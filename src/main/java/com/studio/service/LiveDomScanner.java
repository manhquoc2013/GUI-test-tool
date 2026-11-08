package com.studio.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.studio.util.PlaywrightUtil;
import com.studio.runtime.EnhancedDomExtractor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to capture interactive live DOM snapshots from active browser sessions
 * or remote URLs, inspired by Midscene.js and Skyvern agent inspection.
 * Includes an in-memory LRU cache with TTL to accelerate repeated scans of identical URLs.
 */
public class LiveDomScanner {

    private static final int DEFAULT_TIMEOUT_MS = 10000;
    private static final int MAX_CACHE_SIZE = 10;
    private static final long CACHE_TTL_MS = 180_000; // 3 minutes

    private static final Pattern URL_PATTERN =
            Pattern.compile("https?://[a-zA-Z0-9.-]+(?::[0-9]+)?(?:/[^\\s\"'<>]*)?", Pattern.CASE_INSENSITIVE);

    private static volatile Playwright sharedPlaywright = null;
    private static volatile Browser sharedBrowser = null;

    static {
        ResourceReleaseService.registerCleanupTask("dom-scanner-browser", LiveDomScanner::closeSharedBrowser);
    }

    public static synchronized Browser getOrCreateBrowser() {
        if (sharedPlaywright == null) {
            sharedPlaywright = PlaywrightUtil.create();
        }
        if (sharedBrowser == null || !sharedBrowser.isConnected()) {
            sharedBrowser = sharedPlaywright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        }
        return sharedBrowser;
    }

    public static synchronized void closeSharedBrowser() {
        if (sharedBrowser != null) {
            try { sharedBrowser.close(); } catch (Exception ignored) {}
            sharedBrowser = null;
        }
        if (sharedPlaywright != null) {
            try { sharedPlaywright.close(); } catch (Exception ignored) {}
            sharedPlaywright = null;
        }
    }

    private static class CacheEntry {
        final String domJson;
        final long timestamp;

        CacheEntry(String domJson, long timestamp) {
            this.domJson = domJson;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS;
        }
    }

    private final Map<String, CacheEntry> urlDomCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
    );

    /**
     * Extract the first HTTP/HTTPS URL found in natural language text or prompt.
     */
    public static String extractFirstUrl(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = URL_PATTERN.matcher(text);
        if (matcher.find()) {
            String url = matcher.group().trim();
            while (url.endsWith(".") || url.endsWith(",") || url.endsWith(";") || url.endsWith(")") || url.endsWith("]") || url.endsWith(">")) {
                url = url.substring(0, url.length() - 1);
            }
            return url;
        }
        return null;
    }

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
     * Results are cached for 3 minutes to avoid redundant browser launch overhead.
     */
    public String scanUrl(String url, int timeoutMs) {
        if (url == null || url.isBlank()) {
            return "[]";
        }

        String normalizedUrl = url.trim();
        CacheEntry cached = urlDomCache.get(normalizedUrl);
        if (cached != null && !cached.isExpired()) {
            return cached.domJson;
        }

        int timeout = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
        BrowserContext context = null;
        Page page = null;

        try {
            Browser browser = getOrCreateBrowser();
            context = browser.newContext();
            page = context.newPage();
            page.setDefaultNavigationTimeout(timeout);
            page.setDefaultTimeout(timeout);

            try {
                page.navigate(normalizedUrl, new Page.NavigateOptions().setTimeout((double) timeout));
            } catch (Exception navEx) {
                // If navigation timed out or partial load, still attempt to extract whatever DOM was rendered
                System.err.println("[LiveDomScanner] Navigation partial/timeout warning: " + navEx.getMessage());
            }

            try {
                page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout((double) Math.min(timeout, 3000)));
            } catch (Exception ignored) {
                // If page keeps streaming, proceed with whatever DOM is loaded
            }

            String extracted = EnhancedDomExtractor.extractReducedDom(page);
            if (extracted != null && !extracted.isBlank() && !"[]".equals(extracted)) {
                urlDomCache.put(normalizedUrl, new CacheEntry(extracted, System.currentTimeMillis()));
            }
            return extracted;
        } catch (Exception e) {
            System.err.println("[LiveDomScanner] Failed to scan URL '" + normalizedUrl + "': " + e.getMessage());
            closeSharedBrowser();
            return "[]";
        } finally {
            if (page != null) {
                try { page.close(); } catch (Exception ignored) {}
            }
            if (context != null) {
                try { context.close(); } catch (Exception ignored) {}
            }
        }
    }

    public String scanUrl(String url) {
        return scanUrl(url, DEFAULT_TIMEOUT_MS);
    }

    public void clearCache() {
        urlDomCache.clear();
    }

    public void evictExpired() {
        urlDomCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public void invalidateCache(String url) {
        if (url != null) {
            urlDomCache.remove(url.trim());
        }
    }

    public int getCacheSize() {
        return urlDomCache.size();
    }
}
