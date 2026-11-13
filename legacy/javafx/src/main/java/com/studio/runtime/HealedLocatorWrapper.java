package com.studio.runtime;

import com.microsoft.playwright.Locator;

/**
 * Interface implemented by dynamic proxies created by Studio's self-healing engine.
 * Allows unwrapping to the underlying concrete LocatorImpl required by PlaywrightAssertions.
 */
public interface HealedLocatorWrapper {
    /**
     * @return the underlying concrete Playwright Locator (e.g. com.microsoft.playwright.impl.LocatorImpl)
     */
    Locator unwrap();

    /**
     * @return the current active selector for this locator
     */
    String getActiveSelector();
}

