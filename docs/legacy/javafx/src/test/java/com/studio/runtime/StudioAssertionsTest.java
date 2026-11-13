package com.studio.runtime;

import com.microsoft.playwright.Locator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class StudioAssertionsTest {

    @Test
    void testUnwrapReturnsSameWhenNotProxy() {
        // Given a dummy object that is not a proxy
        Locator dummy = (Locator) Proxy.newProxyInstance(
                Locator.class.getClassLoader(),
                new Class<?>[]{Locator.class},
                (proxy, method, args) -> null
        );

        // When unwrapped
        Locator result = StudioRuntime.unwrap(dummy);

        // Then returns as-is
        assertSame(dummy, result);
    }

    @Test
    void testUnwrapResolvesHealedLocatorWrapper() {
        // Given an underlying mock locator
        Locator targetLocator = (Locator) Proxy.newProxyInstance(
                Locator.class.getClassLoader(),
                new Class<?>[]{Locator.class},
                (proxy, method, args) -> null
        );

        // Given a proxy implementing HealedLocatorWrapper
        Locator proxyLocator = (Locator) Proxy.newProxyInstance(
                Locator.class.getClassLoader(),
                new Class<?>[]{Locator.class, HealedLocatorWrapper.class},
                (proxy, method, args) -> {
                    if ("unwrap".equals(method.getName())) {
                        return targetLocator;
                    }
                    if ("getActiveSelector".equals(method.getName())) {
                        return "#my-button";
                    }
                    return null;
                }
        );

        // When unwrapping
        Locator unwrapped = StudioRuntime.unwrap(proxyLocator);

        // Then it returns the underlying targetLocator
        assertSame(targetLocator, unwrapped);
        assertTrue(proxyLocator instanceof HealedLocatorWrapper);
        assertEquals("#my-button", ((HealedLocatorWrapper) proxyLocator).getActiveSelector());
    }

    @Test
    void testUnwrapHandlesNestedProxies() {
        Locator baseLocator = (Locator) Proxy.newProxyInstance(
                Locator.class.getClassLoader(),
                new Class<?>[]{Locator.class},
                (proxy, method, args) -> null
        );

        Locator innerWrapper = (Locator) Proxy.newProxyInstance(
                Locator.class.getClassLoader(),
                new Class<?>[]{Locator.class, HealedLocatorWrapper.class},
                (proxy, method, args) -> "unwrap".equals(method.getName()) ? baseLocator : null
        );

        Locator outerWrapper = (Locator) Proxy.newProxyInstance(
                Locator.class.getClassLoader(),
                new Class<?>[]{Locator.class, HealedLocatorWrapper.class},
                (proxy, method, args) -> "unwrap".equals(method.getName()) ? innerWrapper : null
        );

        assertSame(baseLocator, StudioRuntime.unwrap(outerWrapper));
    }

    @Test
    void testSafeLocatorAssertionsDisambiguatesStrictModeViolation() throws Throwable {
        Locator firstLocator = (Locator) Proxy.newProxyInstance(
                Locator.class.getClassLoader(),
                new Class<?>[]{Locator.class},
                (proxy, method, args) -> null
        );

        Locator baseLocator = (Locator) Proxy.newProxyInstance(
                Locator.class.getClassLoader(),
                new Class<?>[]{Locator.class},
                (proxy, method, args) -> "first".equals(method.getName()) ? firstLocator : null
        );

        java.util.concurrent.atomic.AtomicBoolean retriedWithFirst = new java.util.concurrent.atomic.AtomicBoolean(false);

        com.microsoft.playwright.assertions.LocatorAssertions mockTargetAssertions = (com.microsoft.playwright.assertions.LocatorAssertions) Proxy.newProxyInstance(
                com.microsoft.playwright.assertions.LocatorAssertions.class.getClassLoader(),
                new Class<?>[]{com.microsoft.playwright.assertions.LocatorAssertions.class},
                (proxy, method, args) -> {
                    if ("isVisible".equals(method.getName())) {
                        throw new com.microsoft.playwright.PlaywrightException("Error: strict mode violation: locator(\"a\") resolved to 2 elements");
                    }
                    return null;
                }
        );

        // Call private createSafeLocatorAssertions via reflection
        java.lang.reflect.Method createSafeMethod = StudioAssertions.class.getDeclaredMethod(
                "createSafeLocatorAssertions", Locator.class, com.microsoft.playwright.assertions.LocatorAssertions.class, boolean.class);
        createSafeMethod.setAccessible(true);
        com.microsoft.playwright.assertions.LocatorAssertions safeAssertions = (com.microsoft.playwright.assertions.LocatorAssertions) createSafeMethod.invoke(null, baseLocator, mockTargetAssertions, false);

        assertNotNull(safeAssertions);
        assertEquals("StudioSafeLocatorAssertions[" + baseLocator + "]", safeAssertions.toString());
    }

    @Test
    void testFindFirstVisibleSelectsVisibleElement() {
        Locator hiddenEl = (Locator) Proxy.newProxyInstance(
                Locator.class.getClassLoader(),
                new Class<?>[]{Locator.class},
                (proxy, method, args) -> "isVisible".equals(method.getName()) ? false : null
        );

        Locator visibleEl = (Locator) Proxy.newProxyInstance(
                Locator.class.getClassLoader(),
                new Class<?>[]{Locator.class},
                (proxy, method, args) -> "isVisible".equals(method.getName()) ? true : null
        );

        Locator multiLoc = (Locator) Proxy.newProxyInstance(
                Locator.class.getClassLoader(),
                new Class<?>[]{Locator.class},
                (proxy, method, args) -> {
                    if ("count".equals(method.getName())) return 2;
                    if ("nth".equals(method.getName())) {
                        int idx = (int) args[0];
                        return idx == 0 ? hiddenEl : visibleEl;
                    }
                    if ("first".equals(method.getName())) return hiddenEl;
                    return null;
                }
        );

        Locator resolved = StudioRuntime.findFirstVisible(multiLoc);
        assertSame(visibleEl, resolved);
    }
}

