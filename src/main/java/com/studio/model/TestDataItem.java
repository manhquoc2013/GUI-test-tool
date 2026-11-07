package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Parameterized test data item for data-driven testing, boundary analysis,
 * and equivalence partitioning (inspired by Testsigma & Automa).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestDataItem {

    private String scenario = "";                     // e.g. "Valid Standard Input", "Empty Email", "Exceeded Max Length"
    private Map<String, String> parameters = new LinkedHashMap<>(); // key-value inputs
    private String expectedOutcome = "";              // e.g. "SUCCESS", "ERROR_VALIDATION_TRIGGERED"

    public TestDataItem() {
    }

    public TestDataItem(String scenario, Map<String, String> parameters, String expectedOutcome) {
        this.scenario = scenario != null ? scenario : "";
        this.parameters = parameters != null ? new LinkedHashMap<>(parameters) : new LinkedHashMap<>();
        this.expectedOutcome = expectedOutcome != null ? expectedOutcome : "";
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario != null ? scenario : "";
    }

    public Map<String, String> getParameters() {
        if (parameters == null) {
            parameters = new LinkedHashMap<>();
        }
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters != null ? new LinkedHashMap<>(parameters) : new LinkedHashMap<>();
    }

    public String getExpectedOutcome() {
        return expectedOutcome;
    }

    public void setExpectedOutcome(String expectedOutcome) {
        this.expectedOutcome = expectedOutcome != null ? expectedOutcome : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestDataItem that = (TestDataItem) o;
        return Objects.equals(scenario, that.scenario) &&
                Objects.equals(parameters, that.parameters) &&
                Objects.equals(expectedOutcome, that.expectedOutcome);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scenario, parameters, expectedOutcome);
    }

    @Override
    public String toString() {
        return "TestDataItem{" +
                "scenario='" + scenario + '\'' +
                ", parameters=" + parameters +
                ", expectedOutcome='" + expectedOutcome + '\'' +
                '}';
    }
}

