package com.studio.controller;

import com.studio.model.TestStep;
import com.studio.store.ProjectStore;
import com.studio.ui.StepScriptBidirectionalSync;

import java.util.List;

/**
 * What the workspace editor holds when a case is opened.
 *
 * <p>The rule is one sentence with three outcomes, and getting it wrong costs a case its code: a case
 * can be created from a specification — steps authored, no Java written yet — and the builder is then
 * its only source of code. Opening such a case on an empty buffer would make it unsavable, opening it
 * on placeholder text would let the placeholder be saved as the script.</p>
 */
final class CaseScriptEditing {

    private CaseScriptEditing() {
    }

    /**
     * @param script       the script on disk, or {@code null} when the case has none
     * @param authoredSteps the steps the case owns, merged into the skeleton when there is no script
     * @return the text the editor opens with — empty when the case has neither code nor steps
     */
    static String openText(String script, List<TestStep> authoredSteps, String startUrl, String caseName) {
        if (script != null) {
            return script;
        }
        if (authoredSteps == null || authoredSteps.isEmpty()) {
            // Nothing authored and nothing recorded: the empty state belongs to the step builder.
            return "";
        }
        // The class envelope a run compiles, with this case's steps inside it.
        return StepScriptBidirectionalSync
                .parseDocument(ProjectStore.scriptSkeleton(caseName))
                .render(authoredSteps, startUrl);
    }
}
