package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Test Data Profile (inspired by Testsigma & Automa).
 * Stores environment parameters and tabular dataset rows for data-driven testing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestDataProfile {

    private String id;
    private String name;
    private String description;
    private Map<String, String> variables = new LinkedHashMap<>();
    private List<Map<String, String>> dataRows = new ArrayList<>();

    public TestDataProfile() {
    }

    public TestDataProfile(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, String> variables) {
        this.variables = variables != null ? variables : new LinkedHashMap<>();
    }

    public List<Map<String, String>> getDataRows() {
        return dataRows;
    }

    public void setDataRows(List<Map<String, String>> dataRows) {
        this.dataRows = dataRows != null ? dataRows : new ArrayList<>();
    }

    public void addVariable(String key, String value) {
        if (key != null && !key.isBlank()) {
            this.variables.put(key.trim(), value != null ? value : "");
        }
    }

    public void addDataRow(Map<String, String> row) {
        if (row != null && !row.isEmpty()) {
            this.dataRows.add(new LinkedHashMap<>(row));
        }
    }

    public String getVariable(String key) {
        if (key == null) return null;
        return this.variables.get(key.trim());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TestDataProfile that = (TestDataProfile) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TestDataProfile{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", vars=" + variables.size() +
                ", rows=" + dataRows.size() +
                '}';
    }
}

