package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Objects;

/**
 * High-level declarative test step (NLP representation inspired by Testsigma & Midscene).
 * Represents an atomic action or verification step in human-readable terms.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestStep {

    private int stepNumber;
    private String action = "";        // NAVIGATE, CLICK, FILL, SELECT, HOVER, ASSERT_VISIBLE, ASSERT_TEXT, etc.
    private String target = "";        // Selector, element label, or target URL
    private String testData = "";      // Input value, parameter, or expected text
    private String description = "";   // Human readable step description
    private String expectedResult = ""; // Verification outcome

    public TestStep() {
    }

    public TestStep(int stepNumber, String action, String target, String testData, String description, String expectedResult) {
        this.stepNumber = stepNumber;
        this.action = action != null ? action : "";
        this.target = target != null ? target : "";
        this.testData = testData != null ? testData : "";
        this.description = description != null ? description : "";
        this.expectedResult = expectedResult != null ? expectedResult : "";
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action != null ? action : "";
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target != null ? target : "";
    }

    public String getTestData() {
        return testData;
    }

    public void setTestData(String testData) {
        this.testData = testData != null ? testData : "";
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    public String getExpectedResult() {
        return expectedResult;
    }

    public void setExpectedResult(String expectedResult) {
        this.expectedResult = expectedResult != null ? expectedResult : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestStep testStep = (TestStep) o;
        return stepNumber == testStep.stepNumber &&
                Objects.equals(action, testStep.action) &&
                Objects.equals(target, testStep.target) &&
                Objects.equals(testData, testStep.testData) &&
                Objects.equals(description, testStep.description) &&
                Objects.equals(expectedResult, testStep.expectedResult);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stepNumber, action, target, testData, description, expectedResult);
    }

    @Override
    public String toString() {
        return String.format("Step %d: [%s] %s %s -> %s",
                stepNumber, action, target,
                (testData != null && !testData.isBlank()) ? "('" + testData + "')" : "",
                (expectedResult != null && !expectedResult.isBlank()) ? expectedResult : "OK");
    }
}

