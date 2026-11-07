package com.studio.util;

import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlaywrightUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void testResolveBrowsersPathReturnsNonNull() {
        Path path = PlaywrightUtil.resolveBrowsersPath();
        assertNotNull(path, "Browsers path should be resolvable");
    }

    @Test
    void testCreatePlaywrightInstance() {
        try (Playwright pw = PlaywrightUtil.create()) {
            assertNotNull(pw);
            assertNotNull(pw.chromium());
        }
    }

    @Test
    void testBundledBrowsersResolution() throws Exception {
        Path fakeBrowsers = tempDir.resolve("browsers");
        Files.createDirectories(fakeBrowsers);
        Files.createDirectories(fakeBrowsers.resolve("chromium-test"));

        boolean hasChromium = BrowserDetector.hasPrefixedChild(fakeBrowsers, "chromium");
        assertTrue(hasChromium, "Should detect chromium prefixed directory");
    }
}

