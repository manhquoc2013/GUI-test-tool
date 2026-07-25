package com.studio.service;

import com.studio.model.CaseResult;
import com.studio.model.RunReport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RunProgressServiceTest {

    @BeforeEach
    @AfterEach
    void reset() {
        RunProgressService.resetForTests();
    }

    @Test
    void beginStartFinishEndAndClearImmediate() {
        CaseResult cr = sampleCase("tc-1", "case 1", "nhóm 1", "suite 1");

        RunProgressService.beginRun("SUITE", Path.of("data/project/nhóm 1/suite 1"));
        assertTrue(RunProgressService.isRunActive());
        assertNull(RunProgressService.statusForCaseId("tc-1"));

        RunProgressService.caseStarted(cr);
        assertEquals("RUNNING", RunProgressService.statusForCaseId("tc-1"));
        assertEquals("RUNNING", RunProgressService.overlayForCase("tc-1", "suite 1", "case 1"));

        cr.setStatus("PASSED");
        RunProgressService.caseFinished(cr);
        assertEquals("PASSED", RunProgressService.statusForCaseId("tc-1"));

        RunProgressService.endRun(new RunReport());
        assertFalse(RunProgressService.isRunActive());
        assertTrue(RunProgressService.hasOverlay());

        RunProgressService.scheduleClear(0);
        assertFalse(RunProgressService.hasOverlay());
        assertNull(RunProgressService.statusForCaseId("tc-1"));
    }

    @Test
    void suiteAggregatePrefersRunningThenFailed() {
        CaseResult a = sampleCase("a", "a", "g", "s");
        CaseResult b = sampleCase("b", "b", "g", "s");
        RunProgressService.beginRun("SUITE", null);
        RunProgressService.caseStarted(a);
        b.setStatus("PASSED");
        RunProgressService.caseFinished(b);
        String suiteDir = RunProgressService.entries().iterator().next().suiteDir();
        assertEquals("RUNNING", RunProgressService.overlayForSuiteDir(suiteDir));

        a.setStatus("FAILED");
        RunProgressService.caseFinished(a);
        assertEquals("FAILED", RunProgressService.overlayForSuiteDir(suiteDir));
    }

    @Test
    void listenerNotified() {
        AtomicInteger n = new AtomicInteger();
        Runnable listener = n::incrementAndGet;
        RunProgressService.addListener(listener);
        try {
            RunProgressService.beginRun("CASE", null);
            CaseResult cr = sampleCase("x", "x", "g", "s");
            RunProgressService.caseStarted(cr);
            cr.setStatus("PASSED");
            RunProgressService.caseFinished(cr);
            assertTrue(n.get() >= 3);
        } finally {
            RunProgressService.removeListener(listener);
        }
    }

    private static CaseResult sampleCase(String id, String name, String group, String suite) {
        CaseResult cr = new CaseResult();
        cr.setTestCaseId(id);
        cr.setTestCaseName(name);
        cr.setGroupPath(group);
        cr.setSuiteName(suite);
        cr.setStatus("PENDING");
        return cr;
    }
}
