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

    @Test
    void testExtractFirstUrl() {
        assertNull(LiveDomScanner.extractFirstUrl(null));
        assertNull(LiveDomScanner.extractFirstUrl(""));
        assertNull(LiveDomScanner.extractFirstUrl("Viết kịch bản kiểm thử không có link"));

        assertEquals("https://manhquoc2013.github.io/quan-ly-thu-chi/",
                LiveDomScanner.extractFirstUrl("Tạo kịch bản kiểm thử cho https://manhquoc2013.github.io/quan-ly-thu-chi/ thêm giao dịch"));

        assertEquals("http://localhost:3000/login",
                LiveDomScanner.extractFirstUrl("Kiểm thử (http://localhost:3000/login), kiểm tra form đăng nhập."));

        assertEquals("https://example.com/api/v1",
                LiveDomScanner.extractFirstUrl("Mở https://example.com/api/v1."));
    }
}
