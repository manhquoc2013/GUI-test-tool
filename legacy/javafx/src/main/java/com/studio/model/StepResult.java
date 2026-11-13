package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The outcome of one step in a run.
 *
 * <p>A run report only knew pass/fail per case, so a failed case could not say which step broke and
 * a passing case could not show what it actually walked through. Each executed step now leaves one
 * of these: its number (the same number the builder prints on the card), the statement that ran, the
 * status, and — when it failed — the reason.</p>
 *
 * <p>The statement text is stored as code on purpose. The run happens below the UI, and phrasing a
 * statement into a sentence is a presentation concern that {@code PlaywrightDescribe} handles when
 * the report or a step card renders it.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StepResult {

    /** The step is executing right now; only ever a live value, never a stored verdict. */
    public static final String RUNNING = "RUNNING";
    /** The step never produced a statement, so there was nothing to execute. */
    public static final String NOT_RUN = "NOT_RUN";
    /** The step was not reached because an earlier step failed. */
    public static final String SKIPPED = "SKIPPED";
    public static final String PASSED = "PASSED";
    public static final String FAILED = "FAILED";

    private int stepNumber;
    private String statement = "";
    private String status = NOT_RUN;
    private long durationMs;
    private String message;

    public StepResult() {
    }

    public StepResult(int stepNumber, String statement, String status, long durationMs, String message) {
        this.stepNumber = stepNumber;
        this.statement = statement != null ? statement : "";
        this.status = status != null ? status : NOT_RUN;
        this.durationMs = durationMs;
        this.message = message;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public String getStatement() {
        return statement != null ? statement : "";
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public String getStatus() {
        return status != null ? status : NOT_RUN;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isFailed() {
        return FAILED.equals(getStatus());
    }

    /** @return true when the step actually executed (as opposed to skipped or empty). */
    public boolean isExecuted() {
        return PASSED.equals(getStatus()) || FAILED.equals(getStatus());
    }

    /** @return true while the step is running, which only happens during a live run. */
    public boolean isRunning() {
        return RUNNING.equals(getStatus());
    }
}
