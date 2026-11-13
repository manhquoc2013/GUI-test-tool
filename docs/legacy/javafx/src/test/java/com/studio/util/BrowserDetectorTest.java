package com.studio.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BrowserDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void hasPrefixedChild_matchesVersionedDirs() throws Exception {
        Files.createDirectories(tempDir.resolve("chromium-1140"));
        Files.createDirectories(tempDir.resolve("firefox-1489"));
        Files.createDirectories(tempDir.resolve("webkit-2157"));
        Files.writeString(tempDir.resolve("readme.txt"), "not a dir");

        assertTrue(BrowserDetector.hasPrefixedChild(tempDir, "chromium"));
        assertTrue(BrowserDetector.hasPrefixedChild(tempDir, "firefox"));
        assertTrue(BrowserDetector.hasPrefixedChild(tempDir, "webkit"));
        assertFalse(BrowserDetector.hasPrefixedChild(tempDir, "chrome"));
    }

    @Test
    void hasPrefixedChild_falseWhenMissing() {
        assertFalse(BrowserDetector.hasPrefixedChild(tempDir, "chromium"));
        assertFalse(BrowserDetector.hasPrefixedChild(null, "chromium"));
    }
}
