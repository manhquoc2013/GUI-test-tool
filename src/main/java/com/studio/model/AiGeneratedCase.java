package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AiGeneratedCase {

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
}
