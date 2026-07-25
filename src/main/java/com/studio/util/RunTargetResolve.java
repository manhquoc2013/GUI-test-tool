package com.studio.util;

import com.studio.model.TestCase;
import com.studio.model.TestSuite;
import com.studio.store.SettingsStore;

/**
 * Resolve browser / headless: case → suite → defaults.
 * Empty browser → Playwright Chromium ({@code CHROMIUM}).
 */
public final class RunTargetResolve {

    public static final String DEFAULT_BROWSER = "CHROMIUM";
    public static final String BROWSER_DEFAULT_LABEL = "Mặc định (Playwright Chromium)";

    private RunTargetResolve() {}

    public static boolean isDefaultBrowser(String browser) {
        return browser == null || browser.isBlank()
                || BROWSER_DEFAULT_LABEL.equals(browser)
                || "Mặc định".equalsIgnoreCase(browser.trim());
    }

    /** Persist value for «Mặc định» choice. */
    public static String persistBrowser(String comboValue) {
        if (isDefaultBrowser(comboValue)) {
            return "";
        }
        return normalizeBrowserId(comboValue);
    }

    public static String normalizeBrowserId(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_BROWSER;
        }
        String u = raw.toUpperCase();
        if (u.contains("FIREFOX")) return "FIREFOX";
        if (u.contains("WEBKIT")) return "WEBKIT";
        if (u.contains("EDGE") || u.contains("MSEDGE")) return "EDGE";
        if (u.contains("CHROME") && !u.contains("CHROMIUM")) return "CHROME";
        if (u.contains("CHROMIUM") || u.equals("CHROMIUM")) return "CHROMIUM";
        if (u.equals("CHROME")) return "CHROME";
        if (u.equals("FIREFOX") || u.equals("WEBKIT") || u.equals("EDGE")) return u;
        return DEFAULT_BROWSER;
    }

    public static String resolveBrowser(TestCase testCase, TestSuite suite) {
        if (testCase != null && !isDefaultBrowser(testCase.getBrowser())) {
            return normalizeBrowserId(testCase.getBrowser());
        }
        if (suite != null && !isDefaultBrowser(suite.getBrowser())) {
            return normalizeBrowserId(suite.getBrowser());
        }
        return DEFAULT_BROWSER;
    }

    public static boolean resolveHeadless(TestCase testCase, TestSuite suite, boolean settingsDefault) {
        if (testCase != null && testCase.getHeadless() != null) {
            return testCase.getHeadless();
        }
        if (suite != null && suite.getHeadless() != null) {
            return suite.getHeadless();
        }
        return settingsDefault;
    }

    public static boolean resolveHeadless(TestCase testCase, TestSuite suite) {
        boolean settingsDefault = false;
        try {
            settingsDefault = new SettingsStore().load().isHeadlessDefault();
        } catch (Exception ignored) {
            // keep false
        }
        return resolveHeadless(testCase, suite, settingsDefault);
    }
}
