package com.studio.service;

import com.studio.model.StepResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The terminal is a progress log, not a transcript of the whole run. A suite of 100 cases × 40 steps
 * appends thousands of lines while the FX thread paints badges, so only the verdict a reader must not
 * miss — a failed step — is echoed; the per-case summary line still reports the outcome.
 */
class TestRunnerTerminalPolicyTest {

    @Test
    void aFailedStepIsTheOnlyStepVerdictEchoedToTheTerminal() {
        assertTrue(TestRunnerService.logsToTerminal(step(StepResult.FAILED)));
    }

    @Test
    void passingSkippedAndNotRunStepsStayOffTheTerminal() {
        assertFalse(TestRunnerService.logsToTerminal(step(StepResult.PASSED)));
        assertFalse(TestRunnerService.logsToTerminal(step(StepResult.SKIPPED)));
        assertFalse(TestRunnerService.logsToTerminal(step(StepResult.NOT_RUN)));
        assertFalse(TestRunnerService.logsToTerminal(step(StepResult.RUNNING)));
        assertFalse(TestRunnerService.logsToTerminal(null), "A missing verdict must not throw");
    }

    private static StepResult step(String status) {
        return new StepResult(1, "page.locator(\"#go\").click();", status, 12, null);
    }
}
