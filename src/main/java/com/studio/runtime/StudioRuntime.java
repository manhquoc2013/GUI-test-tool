package com.studio.runtime;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.studio.model.HealedStepMeta;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class StudioRuntime {
    private static final ThreadLocal<Page> currentPage = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> currentContext = new ThreadLocal<>();
    private static final ThreadLocal<OptimizedSelfHealingExecutor> currentExecutor = new ThreadLocal<>();

    public static void set(Page page, BrowserContext context) {
        currentPage.set(page);
        currentContext.set(context);
    }

    public static void setExecutor(OptimizedSelfHealingExecutor executor) {
        currentExecutor.set(executor);
    }

    public static OptimizedSelfHealingExecutor executor() {
        return currentExecutor.get();
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

    /**
     * Resolves a locator with runtime self-healing protection.
     * Used by transformed scripts and generated assertions.
     */
    public static Locator healLocator(String selector) {
        return healLocator(selector, "LOCATOR_ACTION");
    }

    public static Locator healLocator(String selector, String actionDescription) {
        OptimizedSelfHealingExecutor exec = currentExecutor.get();
        if (exec != null) {
            return exec.resolveLocator(selector, actionDescription);
        }
        return page().locator(selector);
    }

    /**
     * Executes an action with self-healing protection.
     */
    public static void executeWithHealing(String selector, String actionDescription,
                                         Consumer<String> actionRunnable,
                                         Consumer<String> onRepairedCallback) {
        OptimizedSelfHealingExecutor exec = currentExecutor.get();
        if (exec != null) {
            exec.executeWithHealing(selector, actionDescription, actionRunnable, onRepairedCallback);
        } else {
            actionRunnable.accept(selector);
        }
    }

    public static List<HealedStepMeta> getHealedSteps() {
        OptimizedSelfHealingExecutor exec = currentExecutor.get();
        if (exec != null) {
            return exec.getAuditLogs();
        }
        return Collections.emptyList();
    }

    public static void clear() {
        currentPage.remove();
        currentContext.remove();
        currentExecutor.remove();
    }
}
