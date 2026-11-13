package com.studio.runtime;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.APIResponseAssertions;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.assertions.PageAssertions;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.studio.model.AssertResult;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Drop-in, safe replacement for {@link com.microsoft.playwright.assertions.PlaywrightAssertions}.
 * Automatically unwraps Studio's self-healing dynamic proxies (jdk.proxy) to the underlying
 * LocatorImpl before passing to PlaywrightAssertions, eliminating ClassCastException,
 * transparently resolves Playwright Strict Mode Violations by auto-disambiguating to .first() / visible match,
 * and records all assertion results dynamically for detailed reporting.
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
     * Asserts conditions on a Page, with automatic reporting.
     */
    public static PageAssertions assertThat(Page page) {
        PageAssertions base = PlaywrightAssertions.assertThat(page);
        return createSafePageAssertions(page, base, false);
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

                        String assertType = mapMethodToAssertType(method.getName(), isNot);
                        String detail = formatDetail(locator, method.getName(), args);

                        try {
                            Object result = method.invoke(target, args);
                            recordSuccess(assertType, detail);
                            return result;
                        } catch (InvocationTargetException ite) {
                            Throwable cause = ite.getCause();
                            String msg = cause != null ? cause.getMessage() : null;
                            if (msg != null && (msg.contains("strict mode violation") || msg.contains("expected to be visible") || msg.contains("not visible") || msg.contains("contains text") || msg.contains("element(s) not found"))) {
                                try {
                                    // Auto-disambiguate: find currently visible element among matches
                                    Locator visibleLoc = StudioRuntime.findFirstVisible(locator);
                                    if (visibleLoc != null) {
                                        LocatorAssertions retryAssertions = PlaywrightAssertions.assertThat(visibleLoc);
                                        if (isNot) {
                                            retryAssertions = retryAssertions.not();
                                        }
                                        System.out.printf("[StudioAssertions Auto-Disambiguation] Resolved assertion to visible matching element for locator%n");
                                        Object retryResult = method.invoke(retryAssertions, args);
                                        recordSuccess(assertType, detail);
                                        return retryResult;
                                    }
                                } catch (InvocationTargetException retryIte) {
                                    cause = retryIte.getCause() != null ? retryIte.getCause() : retryIte;
                                } catch (Throwable ignored) {}
                            }
                            recordFailure(assertType, detail, cause != null ? cause.getMessage() : ite.getMessage());
                            throw cause != null ? cause : ite;
                        }
                    }
                }
        );
    }

    private static PageAssertions createSafePageAssertions(
            Page page,
            PageAssertions target,
            boolean isNot) {
        return (PageAssertions) Proxy.newProxyInstance(
                PageAssertions.class.getClassLoader(),
                new Class<?>[]{PageAssertions.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if ("not".equals(method.getName()) && (args == null || args.length == 0)) {
                            PageAssertions notTarget = target.not();
                            return createSafePageAssertions(page, notTarget, !isNot);
                        }
                        if ("toString".equals(method.getName())) {
                            return "StudioSafePageAssertions[" + page + "]";
                        }
                        if ("equals".equals(method.getName()) && args != null && args.length == 1) {
                            return proxy == args[0];
                        }
                        if ("hashCode".equals(method.getName())) {
                            return page.hashCode();
                        }

                        String assertType = mapPageMethodToAssertType(method.getName(), isNot);
                        String detail = formatDetail(page != null ? page.url() : "page", method.getName(), args);

                        try {
                            Object result = method.invoke(target, args);
                            recordSuccess(assertType, detail);
                            return result;
                        } catch (InvocationTargetException ite) {
                            Throwable cause = ite.getCause();
                            recordFailure(assertType, detail, cause != null ? cause.getMessage() : ite.getMessage());
                            throw cause != null ? cause : ite;
                        }
                    }
                }
        );
    }

    private static String mapMethodToAssertType(String methodName, boolean isNot) {
        if (methodName == null) return "ASSERT";
        return switch (methodName) {
            case "isVisible" -> isNot ? "ELEMENT_NOT_VISIBLE" : "ELEMENT_VISIBLE";
            case "isHidden" -> isNot ? "ELEMENT_VISIBLE" : "ELEMENT_HIDDEN";
            case "isEnabled" -> isNot ? "ELEMENT_DISABLED" : "ELEMENT_ENABLED";
            case "isDisabled" -> isNot ? "ELEMENT_ENABLED" : "ELEMENT_DISABLED";
            case "isEditable" -> "ELEMENT_EDITABLE";
            case "isChecked" -> isNot ? "ELEMENT_UNCHECKED" : "ELEMENT_CHECKED";
            case "containsText", "hasText" -> "ELEMENT_HAS_TEXT";
            case "hasValue" -> "INPUT_VALUE_EQUALS";
            case "hasAttribute" -> "ELEMENT_HAS_ATTRIBUTE";
            case "hasCount" -> "ELEMENT_COUNT";
            default -> methodName.toUpperCase();
        };
    }

    private static String mapPageMethodToAssertType(String methodName, boolean isNot) {
        if (methodName == null) return "PAGE_ASSERT";
        return switch (methodName) {
            case "hasURL" -> "URL_CONTAINS";
            case "hasTitle" -> "TITLE_CONTAINS";
            default -> methodName.toUpperCase();
        };
    }

    private static String formatDetail(Object targetObj, String methodName, Object[] args) {
        StringBuilder sb = new StringBuilder();
        if (targetObj != null) {
            sb.append(targetObj);
        }
        if (args != null && args.length > 0 && args[0] != null) {
            sb.append(" [").append(args[0]).append("]");
        }
        return sb.toString();
    }

    private static void recordSuccess(String assertType, String detail) {
        int idx = StudioRuntime.getRecordedAsserts().size();
        StudioRuntime.recordAssert(new AssertResult(idx, assertType, detail, "ACTIVE", "PASSED", null));
    }

    private static void recordFailure(String assertType, String detail, String message) {
        int idx = StudioRuntime.getRecordedAsserts().size();
        StudioRuntime.recordAssert(new AssertResult(idx, assertType, detail, "ACTIVE", "FAILED", message));
    }
}

