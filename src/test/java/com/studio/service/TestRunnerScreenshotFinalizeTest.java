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
}
