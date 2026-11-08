package com.studio.runtime;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Action-Delta State Verification Engine (inspired by Skyvern).
 * Verifies that a browser interaction actually triggered an expected page state change
 * (navigation, DOM mutation, modal emergence) and provides recovery tactics for "phantom clicks".
 */
public class ActionDeltaVerifier {

    public static class DeltaSnapshot {
        public final String url;
        public final String title;
        public final int interactiveCount;
        public final int domSignature;

        public DeltaSnapshot(String url, String title, int interactiveCount) {
            this(url, title, interactiveCount, 0);
        }

        public DeltaSnapshot(String url, String title, int interactiveCount, int domSignature) {
            this.url = url != null ? url : "";
            this.title = title != null ? title : "";
            this.interactiveCount = interactiveCount;
            this.domSignature = domSignature;
        }
    }

    public static class DeltaResult {
        private final boolean stateChanged;
        private final boolean urlChanged;
        private final boolean domMutated;
        private final boolean recovered;
        private final String recoveryStrategy;

        public DeltaResult(boolean stateChanged, boolean urlChanged, boolean domMutated, boolean recovered, String recoveryStrategy) {
            this.stateChanged = stateChanged;
            this.urlChanged = urlChanged;
            this.domMutated = domMutated;
            this.recovered = recovered;
            this.recoveryStrategy = recoveryStrategy != null ? recoveryStrategy : "NONE";
        }

        public boolean isStateChanged() {
            return stateChanged;
        }

        public boolean isUrlChanged() {
            return urlChanged;
        }

        public boolean isDomMutated() {
            return domMutated;
        }

        public boolean isRecovered() {
            return recovered;
        }

        public String getRecoveryStrategy() {
            return recoveryStrategy;
        }
    }

    /**
     * Captures an ephemeral snapshot of active page state.
     */
    public static DeltaSnapshot captureSnapshot(Page page) {
        if (page == null) {
            return new DeltaSnapshot("", "", 0, 0);
        }
        try {
            String url = page.url();
            String title = page.title();
            int count = 0;
            int signature = 0;
            try {
                // Count primary interactive elements on viewport
                count = page.locator("button, a, input, select, textarea, [role='button'], [role='dialog']").count();
            } catch (Exception ignored) {}
            try {
                Object res = page.evaluate("() => document.body ? (document.body.innerText.length ^ (document.body.children ? document.body.children.length : 0)) : 0");
                if (res instanceof Number num) {
                    signature = num.intValue();
                }
            } catch (Exception ignored) {}
            return new DeltaSnapshot(url, title, count, signature);
        } catch (Exception e) {
            return new DeltaSnapshot("", "", 0, 0);
        }
    }

    /**
     * Executes an interaction and verifies whether a state transition occurred.
     * If no transition is observed, applies resilient recovery tactics (force click, dispatchEvent, scroll).
     *
     * @param page active Playwright page
     * @param targetLocator locator being acted upon
     * @param action the primary action to perform
     * @return DeltaResult indicating transition and whether recovery was required
     */
    public static DeltaResult executeAndVerify(Page page, Locator targetLocator, Consumer<Locator> action) {
        if (page == null || targetLocator == null || action == null) {
            return new DeltaResult(false, false, false, false, "NONE");
        }

        DeltaSnapshot before = captureSnapshot(page);

        // Execute primary action
        action.accept(targetLocator);

        // Brief stabilization window (50ms) for microtasks / animations
        try {
            page.waitForTimeout(50);
        } catch (Exception ignored) {}

        DeltaSnapshot after = captureSnapshot(page);
        boolean urlChanged = !Objects.equals(before.url, after.url);
        boolean domMutated = before.interactiveCount != after.interactiveCount
                || before.domSignature != after.domSignature
                || !Objects.equals(before.title, after.title);
        boolean stateChanged = urlChanged || domMutated;

        if (stateChanged) {
            return new DeltaResult(true, urlChanged, domMutated, false, "NONE");
        }

        // Phantom Click Recovery: Attempt recovery strategies
        // Strategy 1: Scroll into view center & retry
        try {
            targetLocator.scrollIntoViewIfNeeded();
            action.accept(targetLocator);
            page.waitForTimeout(50);
            DeltaSnapshot recovery1 = captureSnapshot(page);
            if (!Objects.equals(before.url, recovery1.url) || before.interactiveCount != recovery1.interactiveCount || before.domSignature != recovery1.domSignature) {
                return new DeltaResult(true, !Objects.equals(before.url, recovery1.url), true, true, "SCROLL_INTO_VIEW");
            }
        } catch (Exception ignored) {}

        // Strategy 2: Dispatch Click Event (bypasses overlays / covered elements)
        try {
            targetLocator.dispatchEvent("click");
            page.waitForTimeout(50);
            DeltaSnapshot recovery2 = captureSnapshot(page);
            if (!Objects.equals(before.url, recovery2.url) || before.interactiveCount != recovery2.interactiveCount || before.domSignature != recovery2.domSignature) {
                return new DeltaResult(true, !Objects.equals(before.url, recovery2.url), true, true, "DISPATCH_EVENT");
            }
        } catch (Exception ignored) {}

        return new DeltaResult(false, false, false, false, "FAILED");
    }
}

