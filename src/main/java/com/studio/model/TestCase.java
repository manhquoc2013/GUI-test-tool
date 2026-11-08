package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestCase {

    public static final String STATUS_DRAFT = "Draft";
    public static final String STATUS_READY = "Ready";

    private String id;
    private String name;
    private String description;
    private String scriptFile;
    private String status = "Draft";
    private String priority = "P1";
    /** Start URL used for codegen / re-record; persisted in suite.json. */
    private String startUrl = "";
    /** Empty / null = inherit suite then Playwright Chromium. */
    private String browser = "";
    /** Null = inherit suite then Settings. */
    private Boolean headless;
    private String createdAt;
    private String updatedAt;
    private String testDataProfileId;
    private List<Assertion> assertions = new ArrayList<>();
    private List<TestStep> testSteps = new ArrayList<>();
    private List<TestDataItem> testDataSets = new ArrayList<>();

    public TestCase() {
    }

    public TestCase(String id, String name, String description, String scriptFile, String status) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.scriptFile = scriptFile;
        this.status = status;
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

    public String getScriptFile() {
        return scriptFile;
    }

    public void setScriptFile(String scriptFile) {
        this.scriptFile = scriptFile;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority != null ? priority : TestPriority.P1;
    }

    public void setPriority(String priority) {
        this.priority = TestPriority.normalize(priority);
    }

    public String getStartUrl() {
        return startUrl;
    }

    public void setStartUrl(String startUrl) {
        this.startUrl = startUrl != null ? startUrl : "";
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

    public List<Assertion> getAssertions() {
        if (assertions == null) {
            assertions = new ArrayList<>();
        }
        return assertions;
    }

    public void setAssertions(List<Assertion> assertions) {
        this.assertions = assertions != null ? assertions : new ArrayList<>();
    }

    public long countActiveAssertions() {
        return getAssertions().stream().filter(a -> "ACTIVE".equals(a.getStatus())).count();
    }

    public boolean hasActiveAssertions() {
        return countActiveAssertions() > 0;
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

    public String getTestDataProfileId() {
        return testDataProfileId;
    }

    public void setTestDataProfileId(String testDataProfileId) {
        this.testDataProfileId = testDataProfileId;
    }
}
