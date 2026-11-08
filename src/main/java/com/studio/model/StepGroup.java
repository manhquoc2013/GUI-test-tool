package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reusable Step Group (inspired by Testsigma).
 * Represents a modular sequence of test steps and assertions that can be
 * shared and referenced across multiple test cases (e.g. login flows, cookie consent, cart setup).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StepGroup {

    private String id;
    private String name;
    private String description;
    private List<TestStep> steps = new ArrayList<>();
    private List<Assertion> assertions = new ArrayList<>();
    private String createdAt;
    private String updatedAt;

    public StepGroup() {
    }

    public StepGroup(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
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

    public List<TestStep> getSteps() {
        return steps;
    }

    public void setSteps(List<TestStep> steps) {
        this.steps = steps != null ? steps : new ArrayList<>();
    }

    public List<Assertion> getAssertions() {
        return assertions;
    }

    public void setAssertions(List<Assertion> assertions) {
        this.assertions = assertions != null ? assertions : new ArrayList<>();
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StepGroup stepGroup = (StepGroup) o;
        return Objects.equals(id, stepGroup.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "StepGroup{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", steps=" + steps.size() +
                '}';
    }
}

