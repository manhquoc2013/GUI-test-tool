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
}
