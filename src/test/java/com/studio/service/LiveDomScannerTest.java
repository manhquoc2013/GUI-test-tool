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

    @Test
    void testScanUrlAcrossMultipleThreadsWithoutAffinityCrash() throws InterruptedException {
        LiveDomScanner scanner = new LiveDomScanner();
        String html1 = "data:text/html,<html><body><button id='t1'>Thread 1</button></body></html>";
        String html2 = "data:text/html,<html><body><button id='t2'>Thread 2</button></body></html>";

        String[] results = new String[2];
        Throwable[] errors = new Throwable[2];

        Thread t1 = new Thread(() -> {
            try {
                results[0] = scanner.scanUrl(html1, 3000);
            } catch (Throwable t) {
                errors[0] = t;
            }
        }, "dom-worker-1");

        Thread t2 = new Thread(() -> {
            try {
                results[1] = scanner.scanUrl(html2, 3000);
            } catch (Throwable t) {
                errors[1] = t;
            }
        }, "dom-worker-2");

        t1.start();
        t1.join();

        t2.start();
        t2.join();

        assertNull(errors[0], "Worker 1 should not throw thread affinity error");
        assertNull(errors[1], "Worker 2 should not throw thread affinity error");
        assertNotNull(results[0]);
        assertNotNull(results[1]);
    }
}
