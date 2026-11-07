package com.studio.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiveDomScannerCacheTest {

    @Test
    void testCacheOperations() {
        LiveDomScanner scanner = new LiveDomScanner();
        scanner.clearCache();
        assertEquals(0, scanner.getCacheSize());

        // Test invalid/empty url does not get cached
        String emptyResult = scanner.scanUrl("", 100);
        assertEquals("[]", emptyResult);
        assertEquals(0, scanner.getCacheSize());

        // Test url extraction utility
        String prompt = "Vui lòng kiểm tra chức năng tại https://demo.playwright.dev/todomvc và báo cáo";
        assertEquals("https://demo.playwright.dev/todomvc", LiveDomScanner.extractFirstUrl(prompt));
    }
}

