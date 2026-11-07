package com.studio.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.studio.util.PlaywrightUtil;
import com.studio.runtime.EnhancedDomExtractor;

/**
 * Service to capture interactive live DOM snapshots from active browser sessions
 * or remote URLs, inspired by Midscene.js and Skyvern agent inspection.
 */
public class LiveDomScanner {

    private static final int DEFAULT_TIMEOUT_MS = 10000;

    /**
     * Scan the DOM of an active Playwright page.
     */
    public String scanPage(Page page) {
        if (page == null) {
            return "[]";
        }
        try {
            return EnhancedDomExtractor.extractReducedDom(page);
        } catch (Exception e) {
            System.err.println("[LiveDomScanner] Error scanning page: " + e.getMessage());
            return "[]";
        }
    }

    /**
     * Scan a remote or local URL by launching a transient headless browser,
     * waiting for DOMContentLoaded, and extracting interactive elements.
     */
    public String scanUrl(String url, int timeoutMs) {
        if (url == null || url.isBlank()) {
            return "[]";
        }

        int timeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        Playwright playwright = null;
        Browser browser = null;
        Page page = null;

        try {
            playwright = PlaywrightUtil.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            page = browser.newPage();
            page.setDefaultNavigationTimeout(timeout);
            page.setDefaultTimeout(timeout);

            try {
                page.navigate(url, new Page.NavigateOptions().setTimeout((double) timeout));
            } catch (Exception navEx) {
                // If navigation timed out or partial load, still attempt to extract whatever DOM was rendered
                System.err.println("[LiveDomScanner] Navigation partial/timeout warning: " + navEx.getMessage());
            }

            try {
                page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout((double) Math.min(timeout, 3000)));
            } catch (Exception ignored) {
                // If page keeps streaming, proceed with whatever DOM is loaded
            }

            return EnhancedDomExtractor.extractReducedDom(page);
        } catch (Exception e) {
            System.err.println("[LiveDomScanner] Failed to scan URL '" + url + "': " + e.getMessage());
            return "[]";
        } finally {
            if (page != null) {
                try { page.close(); } catch (Exception ignored) {}
            }
            if (browser != null) {
                try { browser.close(); } catch (Exception ignored) {}
            }
            if (playwright != null) {
                try { playwright.close(); } catch (Exception ignored) {}
            }
        }
    }

    public String scanUrl(String url) {
        return scanUrl(url, DEFAULT_TIMEOUT_MS);
    }
}
