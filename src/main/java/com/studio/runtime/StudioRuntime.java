package com.studio.runtime;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

public class StudioRuntime {
    private static final ThreadLocal<Page> currentPage = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> currentContext = new ThreadLocal<>();

    public static void set(Page page, BrowserContext context) {
        currentPage.set(page);
        currentContext.set(context);
    }
    public static Page page() { 
        Page p = currentPage.get();
        if (p == null) throw new IllegalStateException("StudioRuntime.page() called before browser session initialized");
        return p;
    }
    public static BrowserContext context() { 
        BrowserContext c = currentContext.get();
        if (c == null) throw new IllegalStateException("StudioRuntime.context() called before browser session initialized");
        return c;
    }
    public static void clear() {
        currentPage.remove();
        currentContext.remove();
    }
}
