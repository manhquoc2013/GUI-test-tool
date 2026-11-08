package com.studio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestRunnerScreenshotFinalizeTest {

    @TempDir
    Path tempDir;

    @Test
    void finalizeScreenshot_renamesHarnessPngToStatusSuffix() throws Exception {
        Path raw = tempDir.resolve("case 1.png");
        Path preferred = tempDir.resolve("case 1_PASSED.png");
        Files.writeString(raw, "png-bytes");

        Method m = TestRunnerService.class.getDeclaredMethod(
                "finalizeScreenshot", Path.class, Path.class);
        m.setAccessible(true);
        String result = (String) m.invoke(null, raw, preferred);

        assertEquals(preferred.toString(), result);
        assertTrue(Files.isRegularFile(preferred));
        assertFalse(Files.exists(raw));
    }

    @Test
    void finalizeScreenshot_fallsBackToRawWhenMoveNotNeeded() throws Exception {
        Path raw = tempDir.resolve("case 1.png");
        Files.writeString(raw, "png-bytes");

        Method m = TestRunnerService.class.getDeclaredMethod(
                "finalizeScreenshot", Path.class, Path.class);
        m.setAccessible(true);
        String result = (String) m.invoke(null, raw, raw);

        assertEquals(raw.toString(), result);
        assertTrue(Files.isRegularFile(raw));
    }

    @Test
    void testSafeCaseNameScreenshotResolutionWithSlashesAndWindowsChars() {
        String testCaseName = "TC 01: Login / Sign-up <Quick>?";
        String safeName = com.studio.util.StringEscapeUtil.safeFileName(testCaseName, "tc_1");

        // Asserts no illegal path chars exist in the resolved filename
        assertFalse(safeName.contains("/"), "Safe filename must not contain forward slash");
        assertFalse(safeName.contains("\\"), "Safe filename must not contain backslash");
        assertFalse(safeName.contains(":"), "Safe filename must not contain colon");
        assertFalse(safeName.contains("<"), "Safe filename must not contain less than");
        assertFalse(safeName.contains(">"), "Safe filename must not contain greater than");
        assertFalse(safeName.contains("?"), "Safe filename must not contain question mark");

        Path ssPassed = tempDir.resolve(safeName + "_PASSED.png");
        assertDoesNotThrow(() -> Files.writeString(ssPassed, "dummy-png"),
                "Filesystem must accept sanitized screenshot path on all OSes");
        assertTrue(Files.exists(ssPassed));
    }

    @Test
    void testRunHarnessClassResolvesPackagedClass() throws Exception {
        // Create compiled dummy class in com/test/
        Path pkgDir = tempDir.resolve("com/test");
        Files.createDirectories(pkgDir);
        Path srcFile = tempDir.resolve("PackagedCase_Run.java");
        Files.writeString(srcFile, """
            package com.test;
            public class PackagedCase_Run {
                public static boolean executed = false;
                public static void main(String[] args) {
                    executed = true;
                }
            }
            """);

        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        int rc = compiler.run(null, null, null, "-d", tempDir.toString(), srcFile.toString());
        assertEquals(0, rc, "Compiling dummy packaged class should succeed");

        TestRunnerService runner = new TestRunnerService(new com.studio.store.ProjectStore(), new com.studio.store.ReportStore());
        Method m = TestRunnerService.class.getDeclaredMethod("runHarnessClass", Path.class, String.class);
        m.setAccessible(true);
        // Invoking with unqualified class name should resolve package and execute main
        assertDoesNotThrow(() -> m.invoke(runner, tempDir, "PackagedCase_Run"));
    }

    @Test
    void testUnwrapCauseAndExtractFailureReason() {
        com.microsoft.playwright.TimeoutError timeoutError = new com.microsoft.playwright.TimeoutError(
                "Timeout 30000ms exceeded.\ncall log:\n  - waiting for locator(\"input#description\")");
        java.lang.reflect.InvocationTargetException ite = new java.lang.reflect.InvocationTargetException(timeoutError);

        Throwable unwrapped = TestRunnerService.unwrapCause(ite);
        assertSame(timeoutError, unwrapped);

        String reason = TestRunnerService.extractFailureReason(ite);
        assertNotNull(reason);
        assertNotEquals("Unknown error", reason);
        assertTrue(reason.contains("Timeout 30000ms exceeded"), "Reason should contain timeout: " + reason);
        assertTrue(reason.contains("input#description"), "Reason should include locator info: " + reason);
    }

    @Test
    void testBuildReproductionLogIncludesStepsAndHints() {
        com.microsoft.playwright.TimeoutError timeoutError = new com.microsoft.playwright.TimeoutError(
                "Timeout 30000ms exceeded waiting for locator(\"input#description\")");
        java.lang.reflect.InvocationTargetException ite = new java.lang.reflect.InvocationTargetException(timeoutError);

        String log = TestRunnerService.buildReproductionLog(
                ite, "case 1", "case 1.java", "https://example.com/login");

        assertNotNull(log);
        assertTrue(log.contains("CÁCH TÁI HIỆN"), "Log should contain reproduction steps");
        assertTrue(log.contains("https://example.com/login"), "Log should contain URL");
        assertTrue(log.contains("case 1.java"), "Log should contain script file");
        assertTrue(log.contains("GỢI Ý XỬ LÝ"), "Log should contain troubleshooting hint");
        assertTrue(log.contains("Đăng nhập"), "Hint should mention login verification");
        assertTrue(log.contains("TimeoutError"), "Log should contain clear error category");
        assertTrue(log.contains("input#description"), "Log should contain extracted target locator");

        // Verify that raw application/framework stack trace dumping is completely omitted
        assertFalse(log.contains("VẾT STACK TRACE ĐẦY ĐỦ"), "Log must not contain raw full stack trace section");
        assertFalse(log.contains("at com.studio.service"), "Log must not contain application internal stack traces");
    }

    @Test
    void testBuildReproductionLogWithTestCaseSteps() {
        com.studio.model.TestCase tc = new com.studio.model.TestCase();
        tc.setId("TC-01");
        tc.setName("Đăng nhập và tạo dự án");
        tc.setStartUrl("https://example.com/dashboard");

        com.studio.model.TestStep step1 = new com.studio.model.TestStep(1, "NAVIGATE", "https://example.com/login", "", "Điều hướng tới trang đăng nhập", "");
        com.studio.model.TestStep step2 = new com.studio.model.TestStep(2, "FILL", "#username", "admin", "Nhập tài khoản quản trị", "");
        com.studio.model.TestStep step3 = new com.studio.model.TestStep(3, "CLICK", "button#submit", "", "Nhấn nút đăng nhập", "");
        tc.getTestSteps().addAll(java.util.List.of(step1, step2, step3));

        AssertionError assertionError = new AssertionError("Expected text 'Dashboard' but found 'Access Denied'");

        String log = TestRunnerService.buildReproductionLog(
                assertionError, tc.getName(), "LoginTest.java", tc.getStartUrl(), tc);

        assertNotNull(log);
        assertTrue(log.contains("AssertionError (Kết quả kiểm chứng không thỏa mãn)"), "Should classify assertion error");
        assertTrue(log.contains("Expected text 'Dashboard' but found 'Access Denied'"), "Should contain clear error message");
        assertTrue(log.contains("Điều hướng tới trang đăng nhập"), "Should list step 1 description");
        assertTrue(log.contains("Nhập tài khoản quản trị"), "Should list step 2 description");
        assertTrue(log.contains("Nhấn nút đăng nhập"), "Should list step 3 description");
        assertFalse(log.contains("VẾT STACK TRACE ĐẦY ĐỦ"), "Must not dump raw stack traces");
    }

    @Test
    void testCategorizeErrorAndSanitization() {
        assertEquals("TimeoutError (Quá thời gian chờ phần tử giao diện)",
                TestRunnerService.categorizeError(new com.microsoft.playwright.TimeoutError("Timeout exceeded")));

        assertEquals("AssertionError (Kết quả kiểm chứng không thỏa mãn)",
                TestRunnerService.categorizeError(new AssertionError("Value mismatch")));

        assertEquals("StrictModeViolation (Bộ chọn trùng lặp nhiều phần tử)",
                TestRunnerService.categorizeError(new RuntimeException("Error: strict mode violation: locator(\"button\") resolved to 2 elements")));

        assertEquals("BrowserClosed (Trình duyệt hoặc tab bị đóng đột ngột)",
                TestRunnerService.categorizeError(new RuntimeException("Target closed while waiting for locator")));

        assertEquals("NavigationError (Lỗi tải trang web hoặc mạng)",
                TestRunnerService.categorizeError(new RuntimeException("net::ERR_CONNECTION_REFUSED at https://bad.url")));

        // Sanitization tests
        String rawWithStack = "Element not found\n\tat com.microsoft.playwright.impl.ChannelOwner.runUntil(ChannelOwner.java:120)\n\tat java.base/jdk.internal.reflect...";
        assertEquals("Element not found", TestRunnerService.sanitizeErrorMessage(rawWithStack));

        String rawLocator = "waiting for locator(\"button.primary-btn\") to be visible";
        assertEquals("button.primary-btn", TestRunnerService.extractTargetLocator(new RuntimeException(rawLocator)));
    }
}
