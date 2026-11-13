package com.studio.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A report is the record a failed run is debugged from, so per-step results have to survive the
 * round trip to disk — and an older report, written before the field existed, still has to load.
 */
class CaseResultStepResultsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void stepResultsSurviveASaveAndReload() throws Exception {
        CaseResult cr = new CaseResult();
        cr.setTestCaseName("Đăng nhập");
        cr.setStatus("FAILED");
        cr.setStepResults(List.of(
                new StepResult(1, "page.locator(\"#email\").fill(\"admin\");", StepResult.PASSED, 42, null),
                new StepResult(2, "page.locator(\"#submit\").click();", StepResult.FAILED, 30000,
                        "TimeoutError: waiting for locator(\"#submit\")")));

        CaseResult reloaded = mapper.readValue(mapper.writeValueAsString(cr), CaseResult.class);

        assertEquals(2, reloaded.getStepResults().size());
        StepResult failed = reloaded.getStepResults().get(1);
        assertEquals(2, failed.getStepNumber());
        assertEquals(StepResult.FAILED, failed.getStatus());
        assertEquals(30000, failed.getDurationMs());
        assertTrue(failed.isFailed());
        assertTrue(reloaded.getStepResults().get(0).isExecuted());
    }

    @Test
    void anOlderReportWithoutStepResultsStillLoads() throws Exception {
        String legacy = """
                {"testCaseName":"Cũ","status":"PASSED","assertResults":[]}
                """;

        CaseResult reloaded = mapper.readValue(legacy, CaseResult.class);

        assertTrue(reloaded.getStepResults().isEmpty());
        assertFalse(reloaded.getStepResults() == null);
    }

    @Test
    void unknownFieldsInATracedStepAreIgnoredSoReportsStayForwardCompatible() throws Exception {
        String future = """
                {"stepNumber":3,"statement":"x();","status":"PASSED","durationMs":5,"newField":"ignored"}
                """;

        StepResult result = mapper.readValue(future, StepResult.class);

        assertEquals(3, result.getStepNumber());
        assertEquals(StepResult.PASSED, result.getStatus());
    }
}
