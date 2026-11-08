package com.studio.runtime;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ActionDeltaVerifierTest {

    @Test
    void testStateChangeDetectedOnUrlTransition() {
        AtomicInteger callCount = new AtomicInteger();
        Page mockPage = (Page) Proxy.newProxyInstance(
                Page.class.getClassLoader(),
                new Class<?>[]{Page.class},
                (proxy, method, args) -> {
                    if ("url".equals(method.getName())) {
                        return callCount.getAndIncrement() == 0 ? "https://example.com/login" : "https://example.com/dashboard";
                    }
                    if ("title".equals(method.getName())) return "Title";
                    if ("locator".equals(method.getName())) {
                        return Proxy.newProxyInstance(
                                Locator.class.getClassLoader(),
                                new Class<?>[]{Locator.class},
                                (p, m, a) -> "count".equals(m.getName()) ? 5 : null
                        );
                    }
                    return null;
                }
        );

        Locator mockTarget = (Locator) Proxy.newProxyInstance(
                Locator.class.getClassLoader(),
                new Class<?>[]{Locator.class},
                (proxy, method, args) -> null
        );

        ActionDeltaVerifier.DeltaResult res = ActionDeltaVerifier.executeAndVerify(mockPage, mockTarget, loc -> {});

        assertTrue(res.isStateChanged());
        assertTrue(res.isUrlChanged());
        assertFalse(res.isRecovered());
        assertEquals("NONE", res.getRecoveryStrategy());
    }

    @Test
    void testRecoveryAppliedOnPhantomClick() {
        AtomicInteger countCalls = new AtomicInteger();
        Page mockPage = (Page) Proxy.newProxyInstance(
                Page.class.getClassLoader(),
                new Class<?>[]{Page.class},
                (proxy, method, args) -> {
                    if ("url".equals(method.getName())) return "https://example.com/home";
                    if ("title".equals(method.getName())) return "Home";
                    if ("locator".equals(method.getName())) {
                        return Proxy.newProxyInstance(
                                Locator.class.getClassLoader(),
                                new Class<?>[]{Locator.class},
                                (p, m, a) -> {
                                    if ("count".equals(m.getName())) {
                                        // 1st (before action): 5
                                        // 2nd (after initial action): 5 (phantom click!)
                                        // 3rd (after recovery): 8 (state changed!)
                                        int call = countCalls.getAndIncrement();
                                        return call < 2 ? 5 : 8;
                                    }
                                    return null;
                                }
                        );
                    }
                    return null;
                }
        );

        Locator mockTarget = (Locator) Proxy.newProxyInstance(
                Locator.class.getClassLoader(),
                new Class<?>[]{Locator.class},
                (proxy, method, args) -> null
        );

        ActionDeltaVerifier.DeltaResult res = ActionDeltaVerifier.executeAndVerify(mockPage, mockTarget, loc -> {});

        assertTrue(res.isStateChanged());
        assertTrue(res.isRecovered());
        assertEquals("SCROLL_INTO_VIEW", res.getRecoveryStrategy());
    }
}
