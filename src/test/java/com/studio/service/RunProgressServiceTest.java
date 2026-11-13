package com.studio.service;

import com.studio.model.CaseResult;
import com.studio.model.RunReport;
import com.studio.model.StepResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
            for (int i = 0; i < 50 && n.get() < 3; i++) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ignored) {}
            }
            assertTrue(n.get() >= 3);
        } finally {
            RunProgressService.removeListener(listener);
        }
    }

    /**
     * The workspace paints cards from this trace, so a step must be able to go RUNNING → PASSED in
     * place, stay in step order, and be cleaned up with the run.
     */
    @Test
    void aRunningStepIsReplacedInPlaceByItsVerdict() {
        RunProgressService.beginRun("CASE", null);
        RunProgressService.caseStarted(sampleCase("tc-9", "case 9", "g", "s"));

        RunProgressService.stepUpdated("tc-9", new StepResult(2, "b();", StepResult.RUNNING, 0, null));
        RunProgressService.stepUpdated("tc-9", new StepResult(1, "a();", StepResult.PASSED, 12, null));
        RunProgressService.stepUpdated("tc-9", new StepResult(2, "b();", StepResult.FAILED, 30000, "timeout"));

        java.util.List<StepResult> trace = RunProgressService.stepTraceFor("tc-9");
        assertEquals(2, trace.size(), "A step must not be duplicated when it finishes");
        assertEquals(1, trace.get(0).getStepNumber(), "The trace is in step order");
        assertEquals(StepResult.PASSED, trace.get(0).getStatus());
        assertEquals(StepResult.FAILED, trace.get(1).getStatus());
        assertEquals("timeout", trace.get(1).getMessage());
        assertEquals("tc-9", RunProgressService.activeCaseKey());
    }

    @Test
    void anUnknownCaseOrBlankKeyHasNoTrace() {
        assertTrue(RunProgressService.stepTraceFor("nothing").isEmpty());
        assertTrue(RunProgressService.stepTraceFor(null).isEmpty());
        assertTrue(RunProgressService.stepTraceFor("").isEmpty());

        RunProgressService.stepUpdated(null, new StepResult(1, "x();", StepResult.PASSED, 1, null));
        RunProgressService.stepUpdated("tc-1", null);
        assertTrue(RunProgressService.stepTraceFor("tc-1").isEmpty());
    }

    @Test
    void aNewRunOrAClearDropsThePreviousTrace() {
        RunProgressService.beginRun("CASE", null);
        RunProgressService.stepUpdated("tc-1", new StepResult(1, "x();", StepResult.PASSED, 1, null));
        assertFalse(RunProgressService.stepTraceFor("tc-1").isEmpty());

        RunProgressService.beginRun("CASE", null);
        assertTrue(RunProgressService.stepTraceFor("tc-1").isEmpty(),
                "Steps from the previous run must not leak into the next one");

        RunProgressService.stepUpdated("tc-1", new StepResult(1, "x();", StepResult.PASSED, 1, null));
        RunProgressService.clearNow();
        assertTrue(RunProgressService.stepTraceFor("tc-1").isEmpty());
    }

    /** The workspace only has the authored case, whose id may be missing; the name still has to match. */
    @Test
    void theActiveCaseIsRecognisedByIdAndFallsBackToItsName() {
        RunProgressService.beginRun("CASE", null);
        assertFalse(RunProgressService.isActiveCase("tc-1", "case 1"), "No case has started yet");

        RunProgressService.caseStarted(sampleCase("tc-1", "case 1", "g", "s"));
        assertTrue(RunProgressService.isActiveCase("tc-1", "case 1"));
        assertTrue(RunProgressService.isActiveCase(null, "case 1"), "Id-less authored case still matches");
        assertTrue(RunProgressService.isActiveCase("  tc-1  ", null));
        assertFalse(RunProgressService.isActiveCase("tc-2", "case 2"),
                "A trace of another case must never be painted over the open one");

        RunProgressService.stepUpdated("tc-1", new StepResult(1, "x();", StepResult.PASSED, 1, null));
        assertFalse(RunProgressService.stepTraceFor("tc-2").iterator().hasNext());
        assertEquals(1, RunProgressService.stepTraceFor("tc-1").size());
    }

    /**
     * The workspace paints the last step results from the notification the closing case fires. If the
     * active marker is released first, that paint is skipped and the final step keeps the "running"
     * badge until the case is reloaded — while the run is actually over.
     */
    @Test
    void theClosingNotificationStillSeesItsCaseAsActive() throws Exception {
        CaseResult cr = sampleCase("tc-1", "case 1", "g", "s");
        RunProgressService.beginRun("CASE", null);
        RunProgressService.caseStarted(cr);
        cr.setStatus("PASSED");

        AtomicReference<Boolean> sawActiveCase = new AtomicReference<>();
        CountDownLatch painted = new CountDownLatch(1);
        Runnable listener = () -> {
            if (!"PASSED".equals(RunProgressService.statusForCaseId("tc-1"))) {
                return;
            }
            if (sawActiveCase.compareAndSet(null,
                    RunProgressService.isActiveCase("tc-1", "case 1"))) {
                painted.countDown();
            }
        };
        RunProgressService.addListener(listener);
        try {
            RunProgressService.caseFinished(cr);
            assertTrue(painted.await(5, TimeUnit.SECONDS),
                    "The finished case must publish one notification the workspace can paint");
        } finally {
            RunProgressService.removeListener(listener);
        }
        assertTrue(sawActiveCase.get(),
                "The closing notification must still identify the case, or its trace is never painted");
    }

    /**
     * The live trace exists to paint the workspace while a case runs. Keeping it for every case of a
     * suite would hold tens of thousands of step entries at once, so the next case drops the previous
     * one — after the finished case has had its chance to be painted.
     */
    @Test
    void onlyTheRunningCaseKeepsALiveTrace() {
        RunProgressService.beginRun("SUITE", null);
        CaseResult first = sampleCase("tc-1", "case 1", "g", "s");
        RunProgressService.caseStarted(first);
        RunProgressService.stepUpdated("tc-1", new StepResult(1, "a();", StepResult.PASSED, 1, null));

        first.setStatus("PASSED");
        RunProgressService.caseFinished(first);
        assertFalse(RunProgressService.stepTraceFor("tc-1").isEmpty(),
                "The case that just finished must keep its trace while it is still on screen");

        RunProgressService.caseStarted(sampleCase("tc-2", "case 2", "g", "s"));
        assertTrue(RunProgressService.stepTraceFor("tc-1").isEmpty(),
                "A suite must not accumulate the steps of every case it already ran");
    }

    /**
     * The release of the active marker is deferred until the closing notification is delivered. A
     * retry of the same case reuses the same key, so a queued release from the failed attempt must
     * not clear the marker of the attempt that is running now — otherwise its live trace stops
     * painting mid-case.
     */
    @Test
    void aQueuedReleaseCannotClearARetriedCase() throws Exception {
        RunProgressService.beginRun("CASE", null);
        CaseResult cr = sampleCase("tc-1", "case 1", "g", "s");
        RunProgressService.caseStarted(cr);

        cr.setStatus("FAILED");
        RunProgressService.caseFinished(cr);
        RunProgressService.caseStarted(cr); // retry starts before the FX queue drains

        drainFxQueue();

        assertTrue(RunProgressService.isActiveCase("tc-1", "case 1"),
                "The retried case must still own its live trace");
    }

    /** Lets any task the service queued on the FX thread run, so the assertion sees their effect. */
    private static void drainFxQueue() throws InterruptedException {
        CountDownLatch drained = new CountDownLatch(1);
        try {
            javafx.application.Platform.runLater(drained::countDown);
        } catch (IllegalStateException noToolkit) {
            return; // tasks already ran inline
        }
        assertTrue(drained.await(5, TimeUnit.SECONDS), "The FX queue must drain");
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
