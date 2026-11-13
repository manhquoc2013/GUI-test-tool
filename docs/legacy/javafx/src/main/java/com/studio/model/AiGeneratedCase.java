package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AiGeneratedCase {

    /** The case was produced by the model (or by a strict parser of model output). */
    public static final String ORIGIN_LLM = "LLM";
    /** A proposal the model did not ground in a source section; never executed, never counted as coverage. */
    public static final String ORIGIN_SUGGESTED = "SUGGESTED";

    private String name = "";
    private String description = "";
    private String startUrl = "";
    private String priority = "P1";
    private String scriptCode = "";
    private List<Assertion> assertions = new ArrayList<>();
    private List<TestStep> testSteps = new ArrayList<>();
    private List<TestDataItem> testDataSets = new ArrayList<>();
    private List<String> preconditions = new ArrayList<>();
    private String targetCaseId;
    /** Stable business identifier (TC01_...) used for traceability and duplicate detection. */
    private String testId = "";
    /** Screen token from the mined {@code ScreenSpecSection}, or empty when the case is not screen-scoped. */
    private String screenId = "";
    /** SRS section / requirement clause this case was derived from; empty when the model did not cite one. */
    private String requirementRef = "";
    private String origin = ORIGIN_LLM;

    public AiGeneratedCase() {
    }

    public AiGeneratedCase(String name, String description, String startUrl) {
        this.name = name;
        this.description = description;
        this.startUrl = startUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "";
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    public String getStartUrl() {
        return startUrl;
    }

    public void setStartUrl(String startUrl) {
        this.startUrl = startUrl != null ? startUrl : "";
    }

    public String getPriority() {
        return priority != null ? priority : TestPriority.P1;
    }

    public void setPriority(String priority) {
        this.priority = TestPriority.normalize(priority);
    }

    public String getScriptCode() {
        return scriptCode;
    }

    public void setScriptCode(String scriptCode) {
        this.scriptCode = scriptCode != null ? scriptCode : "";
    }

    public List<Assertion> getAssertions() {
        if (assertions == null) {
            assertions = new ArrayList<>();
        }
        return assertions;
    }

    public void setAssertions(List<Assertion> assertions) {
        this.assertions = assertions != null ? assertions : new ArrayList<>();
    }

    public List<TestStep> getTestSteps() {
        if (testSteps == null) {
            testSteps = new ArrayList<>();
        }
        return testSteps;
    }

    public void setTestSteps(List<TestStep> testSteps) {
        this.testSteps = testSteps != null ? testSteps : new ArrayList<>();
    }

    public List<TestDataItem> getTestDataSets() {
        if (testDataSets == null) {
            testDataSets = new ArrayList<>();
        }
        return testDataSets;
    }

    public void setTestDataSets(List<TestDataItem> testDataSets) {
        this.testDataSets = testDataSets != null ? testDataSets : new ArrayList<>();
    }

    public List<String> getPreconditions() {
        if (preconditions == null) {
            preconditions = new ArrayList<>();
        }
        return preconditions;
    }

    public void setPreconditions(List<String> preconditions) {
        this.preconditions = preconditions != null ? preconditions : new ArrayList<>();
    }

    public String getTargetCaseId() {
        return targetCaseId;
    }

    public void setTargetCaseId(String targetCaseId) {
        this.targetCaseId = targetCaseId;
    }

    public String getTestId() {
        return testId != null ? testId : "";
    }

    public void setTestId(String testId) {
        this.testId = testId != null ? testId.trim() : "";
    }

    public String getScreenId() {
        return screenId != null ? screenId : "";
    }

    public void setScreenId(String screenId) {
        this.screenId = screenId != null ? screenId.trim() : "";
    }

    public String getRequirementRef() {
        return requirementRef != null ? requirementRef : "";
    }

    public void setRequirementRef(String requirementRef) {
        this.requirementRef = requirementRef != null ? requirementRef.trim() : "";
    }

    public String getOrigin() {
        return origin != null ? origin : ORIGIN_LLM;
    }

    public void setOrigin(String origin) {
        this.origin = ORIGIN_SUGGESTED.equalsIgnoreCase(origin) ? ORIGIN_SUGGESTED : ORIGIN_LLM;
    }

    /** True when this case points back at a screen and an SRS clause. */
    public boolean hasTraceability() {
        return !getTestId().isBlank() && !getScreenId().isBlank() && !getRequirementRef().isBlank();
    }

    /** Proposals are shown to the user but are excluded from coverage, scripts and quality scoring. */
    public boolean isSuggested() {
        return ORIGIN_SUGGESTED.equals(getOrigin());
    }

    /**
     * Canonical duplicate key: screen + requirement + normalised title. Two batches that describe the
     * same scenario collapse to one key, so aggregation cannot silently double the coverage count.
     */
    public String duplicateKey() {
        String title = getName() == null ? "" : getName().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("^tc\\d+[_\\-]?", "")
                .replaceAll("[^a-z0-9à-ỹ]+", " ")
                .trim();
        return getScreenId().toLowerCase(java.util.Locale.ROOT) + "|"
                + getRequirementRef().toLowerCase(java.util.Locale.ROOT) + "|"
                + title;
    }
}
