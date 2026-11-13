package com.studio.controller;

import com.studio.model.CaseResult;
import com.studio.model.RunReport;
import com.studio.model.StepResult;
import com.studio.model.TestCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A suite report holds one row per case, and only some of them carry step results (an older report,
 * or a case the run never reached). Picking the first row that happens to have steps paints one
 * case's verdicts onto another case's cards, so the row has to be matched by identity.
 */
class SuiteWorkspaceTraceMatchTest {

    @Test
    void theRowOfTheRanCaseWinsEvenWhenAnotherRowCarriesTheSteps() {
        CaseResult other = caseResult("tc-other", "case khác", List.of(
                new StepResult(1, "page.reload();", StepResult.PASSED, 4, null)));
        CaseResult ran = caseResult("tc-ran", "case đang mở", List.of());

        RunReport report = new RunReport();
        report.setCases(List.of(other, ran));

        assertSame(ran, SuiteWorkspacePageController.findCaseResult(report, testCase("tc-ran", "case đang mở")),
                "The running case's own row must be used, not the first row that has steps");
    }

    @Test
    void aRowWithoutAnIdFallsBackToItsName() {
        CaseResult legacy = caseResult(null, "case cũ", List.of());

        RunReport report = new RunReport();
        report.setCases(List.of(legacy));

        assertSame(legacy, SuiteWorkspacePageController.findCaseResult(report, testCase(null, "case cũ")));
    }

    @Test
    void aCaseThatIsNotInTheReportHasNoRow() {
        RunReport report = new RunReport();
        report.setCases(List.of(caseResult("tc-1", "case 1", List.of())));

        assertNull(SuiteWorkspacePageController.findCaseResult(report, testCase("tc-2", "case 2")));
        assertEquals(1, report.getCases().size(), "Matching must not mutate the report");
    }

    private static TestCase testCase(String id, String name) {
        TestCase testCase = new TestCase();
        testCase.setId(id);
        testCase.setName(name);
        return testCase;
    }

    private static CaseResult caseResult(String id, String name, List<StepResult> steps) {
        CaseResult cr = new CaseResult();
        cr.setTestCaseId(id);
        cr.setTestCaseName(name);
        cr.setStatus("PASSED");
        cr.setStepResults(steps);
        return cr;
    }
}
