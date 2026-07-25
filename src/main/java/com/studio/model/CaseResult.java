package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CaseResult {

    private int index;
    private String groupPath;
    private String suiteName;
    /** Absolute suite directory; runtime only — not persisted in reports. */
    @JsonIgnore
    private String suiteDir;
    private String testCaseId;
    private String testCaseName;
    private String scriptFile;
    private String status;
    private String skipReason;
    private long durationMs;
    private String failureReason;
    private String logExcerpt;
    private int exitCode;
    private String screenshotPath;
    private List<AssertResult> assertResults = new ArrayList<>();

    public CaseResult() {
    }

    public CaseResult(int index, String groupPath, String suiteName, String testCaseId,
                      String testCaseName, String scriptFile, String status, String skipReason,
                      long durationMs, String failureReason, String logExcerpt, int exitCode,
                      String screenshotPath) {
        this.index = index;
        this.groupPath = groupPath;
        this.suiteName = suiteName;
        this.testCaseId = testCaseId;
        this.testCaseName = testCaseName;
        this.scriptFile = scriptFile;
        this.status = status;
        this.skipReason = skipReason;
        this.durationMs = durationMs;
        this.failureReason = failureReason;
        this.logExcerpt = logExcerpt;
        this.exitCode = exitCode;
        this.screenshotPath = screenshotPath;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getGroupPath() {
        return groupPath;
    }

    public void setGroupPath(String groupPath) {
        this.groupPath = groupPath;
    }

    public String getSuiteName() {
        return suiteName;
    }

    public void setSuiteName(String suiteName) {
        this.suiteName = suiteName;
    }

    public String getSuiteDir() {
        return suiteDir;
    }

    public void setSuiteDir(String suiteDir) {
        this.suiteDir = suiteDir;
    }

    public String getTestCaseId() {
        return testCaseId;
    }

    public void setTestCaseId(String testCaseId) {
        this.testCaseId = testCaseId;
    }

    public String getTestCaseName() {
        return testCaseName;
    }

    public void setTestCaseName(String testCaseName) {
        this.testCaseName = testCaseName;
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

    public String getSkipReason() {
        return skipReason;
    }

    public void setSkipReason(String skipReason) {
        this.skipReason = skipReason;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getLogExcerpt() {
        return logExcerpt;
    }

    public void setLogExcerpt(String logExcerpt) {
        this.logExcerpt = logExcerpt;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public String getScreenshotPath() {
        return screenshotPath;
    }

    public void setScreenshotPath(String screenshotPath) {
        this.screenshotPath = screenshotPath;
    }

    public List<AssertResult> getAssertResults() {
        return assertResults;
    }

    public void setAssertResults(List<AssertResult> assertResults) {
        this.assertResults = assertResults;
    }
}
