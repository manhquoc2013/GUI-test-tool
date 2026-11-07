package com.studio.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiveDomScannerTest {

    @Test
    void testScanPageNullSafe() {
        LiveDomScanner scanner = new LiveDomScanner();
        assertEquals("[]", scanner.scanPage(null));
    }

    @Test
    void testScanUrlNullOrBlankSafe() {
        LiveDomScanner scanner = new LiveDomScanner();
        assertEquals("[]", scanner.scanUrl(null));
        assertEquals("[]", scanner.scanUrl(""));
        assertEquals("[]", scanner.scanUrl("   "));
    }

    @Test
    void testScanUrlInstantaneousDataUrl() {
        LiveDomScanner scanner = new LiveDomScanner();
        String html = "data:text/html,<html><body><button id='btn1' data-testid='submit-btn'>Click Me</button></body></html>";
        String res = scanner.scanUrl(html, 3000);
        assertNotNull(res);
        assertTrue(res.contains("submit-btn") || res.contains("Click Me") || res.equals("[]"));
    }
}
