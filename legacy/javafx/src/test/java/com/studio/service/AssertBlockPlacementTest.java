package com.studio.service;

import com.studio.model.Assertion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Studio wraps a recorded script in {@code try { … } finally { screenshot }} and appends its
 * assertion block inside that try, after every authored step. That placement is what made a failing
 * assertion look like a failing last step, so the completion call has to be pinned between the two.
 */
class AssertBlockPlacementTest {

    private static final String HARNESS = """
            public class Recorded {
                public static void main(String[] args) {
                    page.locator("#a").click();
                    page.locator("#b").click();
                    try {
                        page.locator("#c").click();
                    } finally {
                        page.screenshot();
                    }
                }
            }
            """;

    @Test
    void theTraceIsClosedBeforeAnyAssertionCanThrow() {
        String block = AssertCodeGenerator.generateAssertBlock(List.of(activeAssertion()));
        String injected = TestRunnerService.injectAssertBlock(HARNESS, block);

        int completion = injected.indexOf("StudioRuntime.stepTraceComplete();");
        int assertions = injected.indexOf("studio assertions");
        int screenshot = injected.indexOf("page.screenshot();");

        assertTrue(completion > 0, "The harness must close the step trace: " + injected);
        assertTrue(assertions > completion,
                "Assertions run after the trace closes, so no step is blamed for a bad assertion");
        assertTrue(screenshot > completion,
                "The completion call must stay inside the try, before the screenshot finally");
        assertTrue(injected.indexOf("page.locator(\"#c\").click();") < completion,
                "Every authored step runs before the trace is closed");
    }

    @Test
    void aBlankAssertBlockLeavesTheHarnessAlone() {
        assertTrue(TestRunnerService.injectAssertBlock(HARNESS, " ").equals(HARNESS));
    }

    private static Assertion activeAssertion() {
        Assertion assertion = new Assertion();
        assertion.setType("PAGE_URL_CONTAINS");
        assertion.setValue("/dashboard");
        assertion.setStatus("ACTIVE");
        return assertion;
    }
}
