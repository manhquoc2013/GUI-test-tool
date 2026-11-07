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
    }
}
