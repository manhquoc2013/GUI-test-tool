package com.studio.runtime;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.studio.model.MultiLocatorProfile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MultiLocatorCascadeTest {

    @Test
    void testAutoBuildProfileFromSelector() {
        MultiLocatorProfile profile = MultiLocatorCascade.autoBuildProfile("button#btn-save-9921");
        assertNotNull(profile);
        assertEquals("button#btn-save-9921", profile.getPrimary());
        assertTrue(profile.getFallbacks().size() > 0);
    }

    @Test
    void testEvaluateCascadeReturnsNullWhenInputsNull() {
        assertNull(MultiLocatorCascade.evaluateCascade(null, null));
        assertNull(MultiLocatorCascade.evaluateCascade(null, new MultiLocatorProfile("button")));
    }

    @Test
    void testEvaluateCascadeFindsUniqueMatchViaProxy() {
        Locator mockLocator = (Locator) Proxy.newProxyInstance(
                Locator.class.getClassLoader(),
                new Class<?>[]{Locator.class},
                (proxy, method, args) -> {
                    if ("count".equals(method.getName())) return 1;
                    if ("first".equals(method.getName())) return proxy;
                    if ("isVisible".equals(method.getName())) return true;
                    return null;
                }
        );

        Page mockPage = (Page) Proxy.newProxyInstance(
                Page.class.getClassLoader(),
                new Class<?>[]{Page.class},
                (proxy, method, args) -> {
                    if ("locator".equals(method.getName())) {
                        String sel = (String) args[0];
                        if ("button:has-text('Save')".equals(sel)) {
                            return mockLocator;
                        }
                    }
                    // Failed for other locators
                    return Proxy.newProxyInstance(
                            Locator.class.getClassLoader(),
                            new Class<?>[]{Locator.class},
                            (p, m, a) -> "count".equals(m.getName()) ? 0 : null
                    );
                }
        );

        MultiLocatorProfile profile = new MultiLocatorProfile("#brittle-id", List.of("button:has-text('Save')"));
        String matched = MultiLocatorCascade.evaluateCascade(mockPage, profile);

        assertEquals("button:has-text('Save')", matched);
    }
}
