package com.studio.runtime;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.studio.model.AssertResult;
import com.studio.model.HealedStepMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class StudioRuntime {
    private static final ThreadLocal<Page> currentPage = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> currentContext = new ThreadLocal<>();
    private static final ThreadLocal<OptimizedSelfHealingExecutor> currentExecutor = new ThreadLocal<>();
    private static final ThreadLocal<List<AssertResult>> recordedAsserts = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<StepTracer> stepTracer = new ThreadLocal<>();

    public static void set(Page page, BrowserContext context) {
        currentPage.set(page);
        currentContext.set(context);
    }

    /**
     * Installs the tracer that instrumented harness statements report into. Set per case, on the
     * thread that runs it, so a shared browser session can never mix two cases' steps.
     */
    public static void setStepTracer(StepTracer tracer) {
        if (tracer == null) {
            stepTracer.remove();
        } else {
            stepTracer.set(tracer);
        }
    }

    /** @return the tracer for this run, or {@code null} when the script is not instrumented. */
    public static StepTracer stepTracer() {
        return stepTracer.get();
    }

    /**
     * Called by generated harness code before each step's statement. A no-op when no tracer is
     * installed, so an uninstrumented script keeps running unchanged.
     */
    public static void stepEnter(int stepNumber) {
        StepTracer tracer = stepTracer.get();
        if (tracer != null) {
            tracer.enter(stepNumber);
        }
    }

    /**
     * Closes the last step as passed, once the authored body is over.
     *
     * <p>Studio appends its assertion block <em>after</em> the last step. Without this call a failing
     * assertion would be attributed to whichever step happened to be still open — the last one — so a
     * healthy step would collect the ✗ and the footer would point the tester at the wrong line. The
     * no-op when no tracer is installed keeps an uninstrumented script unchanged.</p>
     */
    public static void stepTraceComplete() {
        StepTracer tracer = stepTracer.get();
        if (tracer != null) {
            tracer.finish();
        }
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
     * Unwraps any self-healing dynamic proxy to retrieve the underlying concrete Playwright Locator
     * (com.microsoft.playwright.impl.LocatorImpl). This is essential before passing locators to
     * PlaywrightAssertions.assertThat(locator) which internally casts to LocatorImpl.
     */
    public static Locator unwrap(Locator locator) {
        if (locator == null) return null;
        Locator current = locator;
        while (current instanceof HealedLocatorWrapper wrapper) {
            Locator inner = wrapper.unwrap();
            if (inner == null || inner == current) break;
            current = inner;
        }
        while (current != null && java.lang.reflect.Proxy.isProxyClass(current.getClass())) {
            try {
                java.lang.reflect.InvocationHandler ih = java.lang.reflect.Proxy.getInvocationHandler(current);
                java.lang.reflect.Method unwrapMethod = ih.getClass().getMethod("unwrap");
                Object inner = unwrapMethod.invoke(ih);
                if (inner instanceof Locator loc && inner != current) {
                    current = loc;
                    continue;
                }
            } catch (Throwable ignored) {}
            break;
        }
        return current;
    }

    /**
     * Resolves the first currently visible Locator among multiple matches.
     * Prevents false failures where a generic selector (e.g. a:has-text('Code'))
     * matches hidden navigation/dropdown elements before the visible target element.
     */
    public static Locator findFirstVisible(Locator locator) {
        if (locator == null) return null;
        try {
            int count = locator.count();
            if (count <= 1) return locator;
            for (int i = 0; i < count; i++) {
                Locator candidate = locator.nth(i);
                try {
                    if (candidate.isVisible()) {
                        return candidate;
                    }
                } catch (Throwable ignored) {}
            }
            return locator.first();
        } catch (Throwable ignored) {
            return locator;
        }
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

    /**
     * Executes an AI-powered semantic assertion (Midscene-style).
     * Validates screenshot and active DOM context against the requirement statement.
     */
    public static void assertSemantic(Page page, String requirement) {
        Page activePage = page != null ? page : currentPage.get();
        OptimizedSelfHealingExecutor exec = currentExecutor.get();
        if (exec != null) {
            exec.evaluateSemanticAssertion(requirement);
            return;
        }
        if (activePage != null && requirement != null && !requirement.isBlank()) {
            try {
                String text = (String) activePage.evaluate("() => document.body ? document.body.innerText : ''");
                if (text != null && text.contains(requirement)) {
                    return;
                }
            } catch (Exception ignored) {}
            throw new AssertionError("[Semantic Assert Failed] Requirement not satisfied on page: " + requirement);
        }
    }

    public static void recordAssert(AssertResult result) {
        if (result != null) {
            recordedAsserts.get().add(result);
        }
    }

    public static List<AssertResult> getRecordedAsserts() {
        return new ArrayList<>(recordedAsserts.get());
    }

    public static void clearRecordedAsserts() {
        recordedAsserts.get().clear();
    }

    public static void clear() {
        currentPage.remove();
        currentContext.remove();
        currentExecutor.remove();
        recordedAsserts.remove();
        stepTracer.remove();
    }
}
