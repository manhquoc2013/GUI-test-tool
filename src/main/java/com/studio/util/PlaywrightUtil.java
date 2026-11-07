package com.studio.util;

import com.microsoft.playwright.Playwright;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating Playwright instances with automatic discovery
 * of bundled portable browsers (Chromium, etc.) so that the portable
 * distribution works offline on client machines with zero extra installation.
 */
public final class PlaywrightUtil {

    private PlaywrightUtil() {}

    /**
     * Resolves the effective browsers directory, giving priority to:
     * 1. System environment variable PLAYWRIGHT_BROWSERS_PATH (if set and valid directory)
     * 2. Bundled browsers directory in APP_HOME/browsers (portable bundle)
     * 3. BrowserDetector.resolvePlaywrightBrowsersPath()
     */
    public static Path resolveBrowsersPath() {
        String env = System.getenv("PLAYWRIGHT_BROWSERS_PATH");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env);
            if (Files.isDirectory(p)) {
                return p;
            }
        }

        Path bundled = StudioPaths.getAppHome().resolve("browsers");
        if (Files.isDirectory(bundled)) {
            return bundled;
        }

        return BrowserDetector.resolvePlaywrightBrowsersPath();
    }

    /**
     * Creates a Playwright instance configured with the bundled browsers directory
     * if present, ensuring it works out-of-the-box on client machines without setup.
     */
    public static Playwright create() {
        Path browsersPath = resolveBrowsersPath();
        Playwright.CreateOptions options = new Playwright.CreateOptions();
        if (browsersPath != null && Files.isDirectory(browsersPath)) {
            Map<String, String> env = new HashMap<>();
            env.put("PLAYWRIGHT_BROWSERS_PATH", browsersPath.toAbsolutePath().toString());
            options.setEnv(env);
        }
        return Playwright.create(options);
    }
}

