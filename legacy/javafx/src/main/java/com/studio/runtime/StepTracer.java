package com.studio.runtime;

import com.studio.model.StepResult;
import com.studio.util.PlaywrightDescribe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Records the outcome of every step while a generated harness runs.
 *
 * <p>The instrumented script calls {@link #enter(int)} immediately before each step's statement, so
 * the tracer learns a step started, and closes the previous one as passed the moment the next
 * begins. Whatever is still open when the case ends is the step that failed. Steps that never
 * produced a statement are reported separately instead of being silently folded into the pass.</p>
 *
 * <p>Every method is synchronized: the harness runs on the runner's worker thread, while the report
 * is read from that same thread after execution, but a caller may also poll {@link #snapshot()} for
 * live progress.</p>
 *
 * <p>Live observers implement {@link Listener}; the terminal log and the step cards are two of them,
 * so neither has to poll and both see the same numbers.</p>
 */
public final class StepTracer {

    /** Extra detail for a step that was skipped, so the report never shows a bare blank. */
    private static final String NO_STATEMENT = "Bước không sinh câu lệnh để thực thi";
    private static final String AFTER_FAILURE = "Không chạy vì bước trước bị lỗi";

    /**
     * Caps on the text stored per step. Every step of every case persists its statement and failure
     * reason into the run report, which is re-read whenever the report list is opened; an unbounded
     * Playwright stack trace would make that listing progressively slower. The caps are far above
     * any statement the builder emits, so only genuinely sprawling text is shortened.
     */
    private static final int MAX_STATEMENT_CHARS = 240;
    private static final int MAX_MESSAGE_CHARS = 400;
    private static final String ELLIPSIS = "…";

    /**
     * Live observer of a run. Both methods are optional so an implementation can care about only one
     * side of the lifecycle — and both are called on the runner's thread, never the FX thread.
     */
    public interface Listener {

        /** The step began executing; {@code running} is {@code RUNNING} with no duration yet. */
        default void onStepStarted(StepResult running) {
        }

        /** The step ended, with its final verdict. */
        default void onStepFinished(StepResult finished) {
        }
    }

    private final List<String> statements;
    private final Map<Integer, StepResult> results = new TreeMap<>();
    private final Listener listener;

    private int currentStep;
    private long currentStartNanos;
    private boolean closed;

    /**
     * @param statements statement text per step, one entry per step, where index 0 is an unused
     *                   placeholder so a step number reads its own position directly
     * @param listener   optional live observer of the run; never called with a null result
     */
    public StepTracer(List<String> statements, Listener listener) {
        this.statements = statements != null ? List.copyOf(statements) : List.of();
        this.listener = listener;
    }

    public StepTracer(List<String> statements) {
        this(statements, null);
    }

    /** @return how many steps this run knows about (index 0 of the statement list is unused). */
    public synchronized int stepCount() {
        return Math.max(0, statements.size() - 1);
    }

    /**
     * Marks {@code stepNumber} as started, closing the previous step as passed.
     *
     * <p>Called from instrumented harness code, so it must never throw: a tracing failure must not
     * turn a working test into a broken one.</p>
     */
    public synchronized void enter(int stepNumber) {
        if (stepNumber <= 0) {
            return;
        }
        try {
            closeCurrent(StepResult.PASSED, null);
            closeGapsBefore(stepNumber);
            currentStep = stepNumber;
            currentStartNanos = System.nanoTime();
            if (listener != null) {
                listener.onStepStarted(result(stepNumber, statement(stepNumber),
                        StepResult.RUNNING, 0, null));
            }
        } catch (RuntimeException ignored) {
            // Tracing is observability, never a precondition for the test itself.
        }
    }

    /**
     * Closes the step that was running as failed. Steps after it are reported as skipped rather than
     * left blank, so the card list shows exactly how far the run got.
     */
    public synchronized void failCurrent(String message) {
        int failedStep = currentStep;
        closeCurrent(StepResult.FAILED, message);
        currentStep = 0;
        if (failedStep <= 0) {
            return;
        }
        for (int step = failedStep + 1; step <= stepCount(); step++) {
            if (!results.containsKey(step)) {
                publish(result(step, statement(step), StepResult.SKIPPED, 0, AFTER_FAILURE));
            }
        }
    }

    /** Closes the trailing step and fills in every step that never produced a statement. */
    public synchronized void finish() {
        closeCurrent(StepResult.PASSED, null);
        currentStep = 0;
        for (int step = 1; step <= stepCount(); step++) {
            if (!results.containsKey(step)) {
                publish(result(step, statement(step), StepResult.NOT_RUN, 0, NO_STATEMENT));
            }
        }
    }

    /**
     * Ends the trace exactly once. A case can leave through several paths (success, exception,
     * cancel), and the first exit is the one that describes what happened; later calls are ignored
     * instead of overwriting a failure with a pass.
     */
    public synchronized void close(boolean failed, String message) {
        if (closed) {
            return;
        }
        closed = true;
        if (failed) {
            failCurrent(message);
        } else {
            finish();
        }
    }

    /** @return true once {@link #close(boolean, String)} has ended the trace. */
    public synchronized boolean isClosed() {
        return closed;
    }

    /** @return every recorded step in step order. */
    public synchronized List<StepResult> snapshot() {
        return new ArrayList<>(results.values());
    }

    /**
     * @return true when the run traced at least one step. Used to decide whether a case is allowed
     *         to clear the badges on the cards, or must leave them untouched (e.g. a compile error
     *         aborted the run before any step ran).
     */
    public synchronized boolean hasAnyResult() {
        return !results.isEmpty();
    }

    /**
     * Fills the steps that sit between the one that just ended and the one about to start.
     *
     * <p>A builder card exists for every script line, but only a line that begins a statement can
     * carry a marker — a wrapped call or {@code } else {} cannot. Those lines did execute, as part of
     * the previous statement, so they are reported as passed with that explanation instead of being
     * left looking unexecuted. A comment occupies a card too but runs nothing, so it stays "not run".</p>
     */
    private void closeGapsBefore(int upcomingStep) {
        // Without a previous step there is nothing these lines could have been carried by; they are
        // left to finish(), which reports them as never reached.
        if (currentStep <= 0) {
            return;
        }
        for (int step = currentStep + 1; step < upcomingStep; step++) {
            if (results.containsKey(step)) {
                continue;
            }
            String text = statement(step);
            if (PlaywrightDescribe.isComment(text) || text.isBlank()) {
                publish(result(step, text, StepResult.NOT_RUN, 0, NO_STATEMENT));
            } else {
                publish(result(step, text, StepResult.PASSED, 0,
                        "Nằm trong câu lệnh của bước " + currentStep));
            }
        }
    }

    private void closeCurrent(String status, String message) {
        if (currentStep <= 0) {
            return;
        }
        long durationMs = Math.max(0, (System.nanoTime() - currentStartNanos) / 1_000_000L);
        publish(result(currentStep, statement(currentStep), status, durationMs, message));
    }

    /**
     * Builds the stored entry, capping the text that will be persisted with the report.
     *
     * <p>The statement keeps its leading characters (the part that identifies the step) and the
     * message keeps its head (the exception type and the locator), which is what a report reader
     * needs; the tail of a stack trace is noise there.</p>
     */
    private static StepResult result(int stepNumber, String statement, String status, long durationMs,
                                     String message) {
        return new StepResult(stepNumber, cap(statement, MAX_STATEMENT_CHARS), status, durationMs,
                cap(message, MAX_MESSAGE_CHARS));
    }

    private static String cap(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + ELLIPSIS;
    }

    /** Stores the verdict and hands it to the live observer, if any. */
    private void publish(StepResult result) {
        results.put(result.getStepNumber(), result);
        if (listener != null) {
            listener.onStepFinished(result);
        }
    }

    private String statement(int stepNumber) {
        // Index 0 is unused so a step number maps directly onto its list position.
        return stepNumber >= 0 && stepNumber < statements.size() ? statements.get(stepNumber) : "";
    }

    /** @return one human line for the terminal, e.g. {@code ✓ Bước 3 (120ms)}. */
    public static String terminalLine(StepResult result) {
        String mark = switch (result.getStatus()) {
            case StepResult.FAILED -> "✗";
            case StepResult.PASSED -> "✓";
            default -> "•";
        };
        StringBuilder line = new StringBuilder("   ")
                .append(mark).append(" Bước ").append(result.getStepNumber());
        if (result.isExecuted()) {
            line.append(" (").append(result.getDurationMs()).append("ms)");
        }
        if (result.getMessage() != null && !result.getMessage().isBlank()) {
            line.append(" — ").append(result.getMessage());
        }
        return line.toString();
    }
}
