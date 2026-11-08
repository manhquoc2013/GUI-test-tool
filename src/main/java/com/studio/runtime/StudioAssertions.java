package com.studio.runtime;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.APIResponseAssertions;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.assertions.PageAssertions;
import com.microsoft.playwright.assertions.PlaywrightAssertions;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Drop-in, safe replacement for {@link com.microsoft.playwright.assertions.PlaywrightAssertions}.
 * Automatically unwraps Studio's self-healing dynamic proxies (jdk.proxy) to the underlying
 * LocatorImpl before passing to PlaywrightAssertions, eliminating ClassCastException,
 * and transparently resolves Playwright Strict Mode Violations by auto-disambiguating to .first().
 */
public class StudioAssertions {

    /**
     * Asserts conditions on a Locator. Automatically unwraps self-healing proxies
     * and handles Strict Mode Violations / hidden element collisions (auto-resolving to the visible match).
     */
    public static LocatorAssertions assertThat(Locator locator) {
        Locator unwrapped = StudioRuntime.unwrap(locator);
        if (unwrapped == null) {
            return PlaywrightAssertions.assertThat((Locator) null);
        }
        Locator targetLocator = StudioRuntime.findFirstVisible(unwrapped);
        LocatorAssertions base = PlaywrightAssertions.assertThat(targetLocator);
        return createSafeLocatorAssertions(unwrapped, base, false);
    }

    /**
     * Asserts conditions on a Page.
     */
    public static PageAssertions assertThat(Page page) {
        return PlaywrightAssertions.assertThat(page);
    }

    /**
     * Asserts conditions on an APIResponse.
     */
    public static APIResponseAssertions assertThat(APIResponse response) {
        return PlaywrightAssertions.assertThat(response);
    }

    /**
     * Sets default timeout for assertions in milliseconds.
     */
    public static void setDefaultAssertionTimeout(double timeout) {
        PlaywrightAssertions.setDefaultAssertionTimeout(timeout);
    }

    private static LocatorAssertions createSafeLocatorAssertions(
            Locator locator,
            LocatorAssertions target,
            boolean isNot) {
        return (LocatorAssertions) Proxy.newProxyInstance(
                LocatorAssertions.class.getClassLoader(),
                new Class<?>[]{LocatorAssertions.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("not".equals(method.getName()) && (args == null || args.length == 0)) {
                            LocatorAssertions notTarget = target.not();
                            return createSafeLocatorAssertions(locator, notTarget, !isNot);
                        }
                        if ("toString".equals(method.getName())) {
                            return "StudioSafeLocatorAssertions[" + locator + "]";
                        }
                        if ("equals".equals(method.getName()) && args != null && args.length == 1) {
                            return proxy == args[0];
                        }
                        if ("hashCode".equals(method.getName())) {
                            return locator.hashCode();
                        }

                        try {
                            return method.invoke(target, args);
                        } catch (InvocationTargetException ite) {
                            Throwable cause = ite.getCause();
                            String msg = cause != null ? cause.getMessage() : null;
                            if (msg != null && (msg.contains("strict mode violation") || msg.contains("expected to be visible") || msg.contains("not visible"))) {
                                try {
                                    // Auto-disambiguate: find currently visible element among matches
                                    Locator visibleLoc = StudioRuntime.findFirstVisible(locator);
                                    if (visibleLoc != null) {
                                        LocatorAssertions retryAssertions = PlaywrightAssertions.assertThat(visibleLoc);
                                        if (isNot) {
                                            retryAssertions = retryAssertions.not();
                                        }
                                        System.out.printf("[StudioAssertions Auto-Disambiguation] Resolved assertion to visible matching element for locator%n");
                                        return method.invoke(retryAssertions, args);
                                    }
                                } catch (InvocationTargetException retryIte) {
                                    throw retryIte.getCause() != null ? retryIte.getCause() : retryIte;
                                } catch (Throwable ignored) {}
                            }
                            throw cause != null ? cause : ite;
                        }
                    }
                }
        );
    }
}

