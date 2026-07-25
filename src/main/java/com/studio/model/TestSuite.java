package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestSuite {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    private String name;
    private String status = "DRAFT";
    private String description;
    /** Empty / null = Playwright Chromium default. */
    private String browser = "";
    /** Null = Settings headlessDefault. */
    private Boolean headless;
    private String createdAt;
    private String updatedAt;
    private List<TestCase> cases = new ArrayList<>();

    /**
     * Legacy suite-level assertions (pre per-case model).
     * Jackson still reads/writes this key for migration; cleared after migrate/save.
     */
    private List<Assertion> assertions = new ArrayList<>();

    public TestSuite() {
    }

    public TestSuite(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser != null ? browser : "";
    }

    public Boolean getHeadless() {
        return headless;
    }

    public void setHeadless(Boolean headless) {
        this.headless = headless;
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

    public List<TestCase> getCases() {
        if (cases == null) {
            cases = new ArrayList<>();
        }
        return cases;
    }

    public void setCases(List<TestCase> cases) {
        this.cases = cases != null ? cases : new ArrayList<>();
    }

    /** Jackson / migration accessor for legacy suite-level assertions. */
    public List<Assertion> getAssertions() {
        if (assertions == null) {
            assertions = new ArrayList<>();
        }
        return assertions;
    }

    /** Jackson / migration accessor for legacy suite-level assertions. */
    public void setAssertions(List<Assertion> assertions) {
        this.assertions = assertions != null ? assertions : new ArrayList<>();
    }

    public void clearLegacyAssertions() {
        if (assertions == null) {
            assertions = new ArrayList<>();
        } else {
            assertions.clear();
        }
    }

    /**
     * Move legacy suite-level assertions onto the first Ready case (or first case).
     *
     * @return true if migration changed data
     */
    public boolean migrateLegacyAssertionsToCases() {
        if (assertions == null || assertions.isEmpty()) {
            return false;
        }
        boolean anyCaseHasAsserts = getCases().stream()
                .anyMatch(c -> c.getAssertions() != null && !c.getAssertions().isEmpty());
        if (anyCaseHasAsserts) {
            clearLegacyAssertions();
            return true;
        }
        TestCase target = getCases().stream()
                .filter(c -> TestCase.STATUS_READY.equalsIgnoreCase(c.getStatus()))
                .findFirst()
                .orElse(getCases().isEmpty() ? null : getCases().get(0));
        if (target == null) {
            return false;
        }
        target.getAssertions().addAll(new ArrayList<>(assertions));
        clearLegacyAssertions();
        return true;
    }

    public boolean hasAnyActiveAssertions() {
        return getCases().stream().anyMatch(TestCase::hasActiveAssertions);
    }
}
