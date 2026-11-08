package com.studio.service;

import com.studio.model.Assertion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SemanticAssertTest {

    @Test
    void testIsSupportedForSemanticAssert() {
        assertTrue(AssertCodeGenerator.isSupported("AI_SEMANTIC_ASSERT"));
        assertTrue(AssertCodeGenerator.isSupported("SEMANTIC_ASSERT"));
        assertTrue(AssertCodeGenerator.isSupported("AI_ASSERT"));
    }

    @Test
    void testGenerateCodeForSemanticAssert() {
        Assertion a = new Assertion();
        a.setType("AI_SEMANTIC_ASSERT");
        a.setValue("The shopping cart contains 2 items");

        String code = AssertCodeGenerator.generateAssertBlock(java.util.List.of(a));
        assertNotNull(code);
        assertTrue(code.contains("StudioRuntime.assertSemantic"));
        assertTrue(code.contains("The shopping cart contains 2 items"));
    }

    @Test
    void testSemanticAssertPassesWhenTextPresent() {
        com.microsoft.playwright.Page mockPage = (com.microsoft.playwright.Page) java.lang.reflect.Proxy.newProxyInstance(
                com.microsoft.playwright.Page.class.getClassLoader(),
                new Class<?>[]{com.microsoft.playwright.Page.class},
                (proxy, method, args) -> "evaluate".equals(method.getName()) ? "Welcome Alice, Cart (2 items)" : null
        );

        assertDoesNotThrow(() -> com.studio.runtime.StudioRuntime.assertSemantic(mockPage, "Cart (2 items)"));
    }

    @Test
    void testSemanticAssertThrowsWhenTextNotPresent() {
        com.microsoft.playwright.Page mockPage = (com.microsoft.playwright.Page) java.lang.reflect.Proxy.newProxyInstance(
                com.microsoft.playwright.Page.class.getClassLoader(),
                new Class<?>[]{com.microsoft.playwright.Page.class},
                (proxy, method, args) -> "evaluate".equals(method.getName()) ? "Welcome Bob, Cart is empty" : null
        );

        AssertionError err = assertThrows(AssertionError.class,
                () -> com.studio.runtime.StudioRuntime.assertSemantic(mockPage, "Cart (2 items)"));
        assertTrue(err.getMessage().contains("Cart (2 items)"));
    }
}
