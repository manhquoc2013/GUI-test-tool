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
}
