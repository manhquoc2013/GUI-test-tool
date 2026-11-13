package com.studio.util;

import com.studio.model.Assertion;
import com.studio.model.TestStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VariableResolverTest {

    @Test
    void testResolveSingleAndMultipleVariables() {
        Map<String, String> ctx = Map.of(
                "BASE_URL", "https://staging.example.com",
                "USERNAME", "admin@studio.dev"
        );

        String template = "page.navigate(\"{{BASE_URL}}/login\"); fill(\"#user\", \"${USERNAME}\");";
        String resolved = VariableResolver.resolve(template, ctx);

        assertEquals("page.navigate(\"https://staging.example.com/login\"); fill(\"#user\", \"admin@studio.dev\");", resolved);
    }

    @Test
    void testUnresolvedKeepsOriginalToken() {
        Map<String, String> ctx = Map.of("EXISTS", "value");
        String template = "Welcome {{EXISTS}}, id: {{NOT_FOUND}}, home: {{PATH}}";
        String resolved = VariableResolver.resolve(template, ctx);

        assertEquals("Welcome value, id: {{NOT_FOUND}}, home: {{PATH}}", resolved);
    }

    @Test
    void testEnvPrefixResolvesEnvironmentVariable() {
        String template = "Running with PATH: {{ENV.PATH}}";
        String resolved = VariableResolver.resolve(template, Map.of());
        assertFalse(resolved.contains("{{ENV.PATH}}"));
    }

    @Test
    void testResolveStepsAndAssertions() {
        Map<String, String> ctx = Map.of("ITEM_NAME", "MacBook Pro M4");

        TestStep step = new TestStep(1, "FILL", "#search", "{{ITEM_NAME}}", "Search for {{ITEM_NAME}}", "Shows {{ITEM_NAME}}");
        List<TestStep> resolvedSteps = VariableResolver.resolveSteps(List.of(step), ctx);

        assertEquals("MacBook Pro M4", resolvedSteps.get(0).getTestData());
        assertEquals("Search for MacBook Pro M4", resolvedSteps.get(0).getDescription());

        Assertion assertItem = new Assertion();
        assertItem.setType("TEXT_VISIBLE");
        assertItem.setValue("{{ITEM_NAME}}");
        List<Assertion> resolvedAsserts = VariableResolver.resolveAssertions(List.of(assertItem), ctx);

        assertEquals("MacBook Pro M4", resolvedAsserts.get(0).getValue());
    }
}

