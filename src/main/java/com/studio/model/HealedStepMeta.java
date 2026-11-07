package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Metadata record for a Playwright action or assertion step that was
 * self-healed at runtime (via Heuristic or VLM).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HealedStepMeta {

    private String testCaseId;
    private String actionDescription;
    private String originalSelector;
    private String repairedSelector;
    private String method; // "HEURISTIC", "VLM", or "LOCATE_CACHE"
    private double confidence;
    private String timestamp;

    public HealedStepMeta() {
    }

    public HealedStepMeta(String testCaseId, String actionDescription,
                          String originalSelector, String repairedSelector,
                          String method, double confidence, String timestamp) {
        this.testCaseId = testCaseId;
        this.actionDescription = actionDescription;
        this.originalSelector = originalSelector;
        this.repairedSelector = repairedSelector;
        this.method = method;
        this.confidence = confidence;
        this.timestamp = timestamp;
    }

    public String getTestCaseId() {
        return testCaseId;
    }

    public void setTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
    }

    public String getActionDescription() {
        return actionDescription;
    }

    public void setActionDescription(String actionDescription) {
        this.actionDescription = actionDescription;
    }

    public String getOriginalSelector() {
        return originalSelector;
    }

    public void setOriginalSelector(String originalSelector) {
        this.originalSelector = originalSelector;
    }

    public String getRepairedSelector() {
        return repairedSelector;
    }

    public void setRepairedSelector(String repairedSelector) {
        this.repairedSelector = repairedSelector;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
