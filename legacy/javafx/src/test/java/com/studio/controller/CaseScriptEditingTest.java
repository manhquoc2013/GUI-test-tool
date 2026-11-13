package com.studio.controller;

import com.studio.model.TestStep;
import com.studio.ui.StepScriptBidirectionalSync;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Three states open a case, and each has exactly one right answer for the editor:
 * a script exists, steps exist but no script, or neither. The middle state is the subtle one — the
 * builder is the only source of that case's code, so the buffer must already hold what a save writes.
 */
class CaseScriptEditingTest {

    private static final String SCRIPT = "package com.test;\npublic class Recorded {}\n";

    @Test
    void aRecordedScriptOpensVerbatim() {
        assertEquals(SCRIPT, CaseScriptEditing.openText(SCRIPT, List.of(clickStep()), "", "TC01"),
                "The file on disk is the source of truth; the builder must not rewrite it");
    }

    @Test
    void stepsWithoutAScriptOpenAsTheEnvelopeARunCompiles() {
        String text = CaseScriptEditing.openText(null, List.of(clickStep()), "", "TC02");

        assertTrue(text.contains("Page page = com.studio.runtime.StudioRuntime.page();"),
                "Steps are emitted against the runtime page, so the envelope must declare it: " + text);
        assertTrue(text.contains("#submit"), "The authored step is inside the envelope: " + text);
        assertEquals(1, StepScriptBidirectionalSync.parseDocument(text).getSteps().size(),
                "The rendered script reads back as the same one step: " + text);
    }

    @Test
    void aStartUrlBecomesTheFirstNavigation() {
        String text = CaseScriptEditing.openText(null, List.of(clickStep()), "https://example.com", "TC02");

        assertEquals(2, StepScriptBidirectionalSync.parseDocument(text).getSteps().size(),
                "The start URL is a step the reader can see: " + text);
        assertTrue(text.indexOf("https://example.com") < text.indexOf("#submit"),
                "Navigation precedes the authored step: " + text);
    }

    @Test
    void aCaseWithNeitherScriptNorStepsOpensEmptyForTheBuilderEmptyState() {
        assertEquals("", CaseScriptEditing.openText(null, List.of(), "", "TC03"),
                "Placeholder text in the buffer would be savable script and a phantom step card");
        assertEquals("", CaseScriptEditing.openText(null, null, "", "TC03"),
                "A case whose model has no step list is the same empty state");
    }

    @Test
    void aDisabledStepKeepsItsSlotInsteadOfBeingDropped() {
        TestStep disabled = clickStep();
        disabled.setEnabled(false);

        String text = CaseScriptEditing.openText(null, List.of(disabled), "", "TC04");

        assertTrue(text.contains(StepScriptBidirectionalSync.DISABLED_STEP_PLACEHOLDER),
                "A switched-off step holds its line so the run's step numbers stay aligned: " + text);
        assertFalse(text.contains("#submit"), "A disabled step is not emitted as a call: " + text);
        assertTrue(text.contains("public class TC04"),
                "A steps-only case still opens inside the class a run renames: " + text);
    }

    private static TestStep clickStep() {
        return new TestStep(1, "CLICK", "#submit", "", "Bấm gửi", "");
    }
}
