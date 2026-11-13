package com.studio.runtime;

import com.studio.model.StepResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepTracerTest {

    private static List<String> statements(int steps) {
        List<String> list = new ArrayList<>();
        list.add("");
        for (int step = 1; step <= steps; step++) {
            list.add("statement " + step + ";");
        }
        return list;
    }

    @Test
    void aStartedStepIsClosedAsPassedWhenTheNextOneBegins() {
        StepTracer tracer = new StepTracer(statements(3));
        tracer.enter(1);
        tracer.enter(2);
        tracer.close(false, null);

        List<StepResult> results = tracer.snapshot();
        assertEquals(3, results.size());
        assertEquals(StepResult.PASSED, results.get(0).getStatus());
        assertEquals(StepResult.PASSED, results.get(1).getStatus());
        assertEquals(StepResult.NOT_RUN, results.get(2).getStatus(),
                "A step that never emitted a statement must not look like it ran");
        assertNotNull(results.get(2).getMessage());
        assertEquals("statement 2;", results.get(1).getStatement());
    }

    @Test
    void theOpenStepIsTheOneThatFailedAndTheRestAreSkipped() {
        StepTracer tracer = new StepTracer(statements(4));
        tracer.enter(1);
        tracer.enter(2);
        tracer.enter(3);
        tracer.close(true, "TimeoutError: waiting for locator(\"#go\")");

        List<StepResult> results = tracer.snapshot();
        assertEquals(StepResult.PASSED, results.get(0).getStatus());
        assertEquals(StepResult.PASSED, results.get(1).getStatus());
        assertEquals(StepResult.FAILED, results.get(2).getStatus());
        assertEquals("TimeoutError: waiting for locator(\"#go\")", results.get(2).getMessage());
        assertEquals(StepResult.SKIPPED, results.get(3).getStatus(),
                "A step after the failure says why it did not run instead of showing a blank");
    }

    @Test
    void closingTwiceKeepsTheFirstVerdict() {
        StepTracer tracer = new StepTracer(statements(2));
        tracer.enter(1);
        tracer.close(true, "boom");
        tracer.close(false, null);

        assertTrue(tracer.isClosed());
        assertEquals(StepResult.FAILED, tracer.snapshot().get(0).getStatus(),
                "A later exit path must not turn a failure into a pass");
    }

    @Test
    void aStepEnteredOutOfOrderDoesNotLoseItsNumber() {
        StepTracer tracer = new StepTracer(statements(3));
        tracer.enter(3);
        tracer.close(false, null);

        List<StepResult> results = tracer.snapshot();
        assertEquals(3, results.get(2).getStepNumber());
        assertEquals(StepResult.PASSED, results.get(2).getStatus());
        assertEquals(StepResult.NOT_RUN, results.get(0).getStatus());
    }

    @Test
    void everyStepEndsUpReportedToTheListener() {
        List<String> lines = new ArrayList<>();
        StepTracer tracer = new StepTracer(statements(2), new StepTracer.Listener() {
            @Override
            public void onStepFinished(StepResult finished) {
                lines.add(StepTracer.terminalLine(finished));
            }
        });
        tracer.enter(1);
        tracer.close(false, null);

        assertEquals(2, lines.size(), "The run reports the executed step and the one never reached");
        assertTrue(lines.get(0).contains("Bước 1"), lines.get(0));
        assertTrue(lines.get(0).contains("✓"), lines.get(0));
        assertTrue(lines.get(1).contains("Bước 2"), lines.get(1));
        assertTrue(lines.get(1).contains("•"), "A step that never ran is not a pass: " + lines.get(1));

        List<String> failing = new ArrayList<>();
        StepTracer failingTracer = new StepTracer(statements(1), new StepTracer.Listener() {
            @Override
            public void onStepFinished(StepResult finished) {
                failing.add(StepTracer.terminalLine(finished));
            }
        });
        failingTracer.enter(1);
        failingTracer.close(true, "lỗi");
        assertTrue(failing.get(0).contains("✗"), failing.get(0));
        assertTrue(failing.get(0).contains("lỗi"), failing.get(0));
    }

    /**
     * The builder has one card per script line, but only a line starting a statement can hold a
     * marker. The card for a wrapped line must therefore still get a verdict — otherwise it reads
     * "không chạy" although it ran as part of the statement above it.
     */
    @Test
    void aWrappedLineIsReportedAsPartOfTheStepThatCarriedIt() {
        StepTracer tracer = new StepTracer(List.of("",
                "page.locator(\"#a\")", ".click();", "page.reload();"));
        tracer.enter(1);
        tracer.enter(3);
        tracer.close(false, null);

        List<StepResult> steps = tracer.snapshot();
        assertEquals(3, steps.size(), "Every card gets a verdict");
        assertEquals(StepResult.PASSED, steps.get(1).getStatus());
        assertTrue(steps.get(1).getMessage().contains("bước 1"), steps.get(1).getMessage());
        assertEquals(StepResult.PASSED, steps.get(2).getStatus());
    }

    @Test
    void aNoteBetweenStepsIsNotClaimedAsExecuted() {
        StepTracer tracer = new StepTracer(List.of("", "// chỉ là ghi chú", "page.reload();"));
        tracer.enter(2);
        tracer.close(false, null);

        List<StepResult> steps = tracer.snapshot();
        assertEquals(2, steps.size());
        assertEquals(StepResult.NOT_RUN, steps.get(0).getStatus(),
                "A comment executes nothing, so it is not a pass");
        assertEquals(StepResult.PASSED, steps.get(1).getStatus());
    }

    /**
     * A live UI needs to know the step started, not only that the previous one passed — otherwise
     * the card that is executing right now looks identical to the ones not reached yet.
     */
    @Test
    void theListenerSeesTheStepStartBeforeItsVerdict() {
        List<String> events = new ArrayList<>();
        StepTracer tracer = new StepTracer(statements(2), new StepTracer.Listener() {
            @Override
            public void onStepStarted(StepResult running) {
                events.add("start:" + running.getStepNumber() + ":" + running.getStatus());
            }

            @Override
            public void onStepFinished(StepResult finished) {
                events.add("done:" + finished.getStepNumber() + ":" + finished.getStatus());
            }
        });

        tracer.enter(1);
        assertEquals(List.of("start:1:RUNNING"), events,
                "The first step is running, and no verdict exists for it yet");

        tracer.enter(2);
        assertEquals(List.of("start:1:RUNNING", "done:1:PASSED", "start:2:RUNNING"), events);

        tracer.close(true, "hỏng");
        assertEquals(List.of("start:1:RUNNING", "done:1:PASSED", "start:2:RUNNING", "done:2:FAILED"),
                events);
    }

    @Test
    void aLiveRunningEntryCarriesNoDurationAndIsNotAFinishedVerdict() {
        AtomicReference<StepResult> seen = new AtomicReference<>();
        StepTracer tracer = new StepTracer(statements(1), new StepTracer.Listener() {
            @Override
            public void onStepStarted(StepResult running) {
                seen.set(running);
            }
        });
        tracer.enter(1);

        assertTrue(seen.get().isRunning());
        assertFalse(seen.get().isExecuted(), "A still-running step must not count as executed");
        assertEquals(0, seen.get().getDurationMs());
        assertEquals("statement 1;", seen.get().getStatement());
    }

    @Test
    void tracingNeverBecomesAPreconditionOfTheRun() {
        // enter() is called from generated harness code: a bad number or a missing tracer must not
        // turn a passing test into a throwing one.
        StudioRuntime.setStepTracer(null);
        StudioRuntime.stepEnter(4);
        assertNull(StudioRuntime.stepTracer());

        StepTracer tracer = new StepTracer(statements(2));
        StudioRuntime.setStepTracer(tracer);
        try {
            StudioRuntime.stepEnter(1);
            StudioRuntime.stepEnter(-1);
            StudioRuntime.stepEnter(99);
            StudioRuntime.stepEnter(2);
        } finally {
            StudioRuntime.setStepTracer(null);
        }
        tracer.close(false, null);
        assertEquals(StepResult.PASSED, tracer.snapshot().get(1).getStatus());
    }

    @Test
    void anUntracedRunReportsNothingSoTheCardsAreLeftUntouched() {
        StepTracer tracer = new StepTracer(statements(2));
        tracer.close(false, null);

        assertFalse(tracer.hasAnyResult() && tracer.snapshot().get(0).isExecuted(),
                "Nothing ran, so no card may be painted as passed");
    }
}
