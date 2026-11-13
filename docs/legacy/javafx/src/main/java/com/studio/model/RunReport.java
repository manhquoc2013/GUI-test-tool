package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RunReport {

    private String runId;
    private String scope;
    private String scopePath;
    private String scopeName;
    private String browser;
    private boolean headless;
    private boolean shareContext;
    private boolean stopOnFirstFailure;
    private String startedAt;
    private String finishedAt;
    private int total;
    private int passed;
    private int failed;
    private int skipped;
    private List<CaseResult> cases = new ArrayList<>();

    public RunReport() {
    }

    public RunReport(String runId, String scope, String scopePath, String scopeName, String browser,
                     boolean headless, boolean shareContext, boolean stopOnFirstFailure) {
        this.runId = runId;
        this.scope = scope;
        this.scopePath = scopePath;
        this.scopeName = scopeName;
        this.browser = browser;
        this.headless = headless;
        this.shareContext = shareContext;
        this.stopOnFirstFailure = stopOnFirstFailure;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getScopePath() {
        return scopePath;
    }

    public void setScopePath(String scopePath) {
        this.scopePath = scopePath;
    }

    public String getScopeName() {
        return scopeName;
    }

    public void setScopeName(String scopeName) {
        this.scopeName = scopeName;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public boolean isHeadless() {
        return headless;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public boolean isShareContext() {
        return shareContext;
    }

    public void setShareContext(boolean shareContext) {
        this.shareContext = shareContext;
    }

    public boolean isStopOnFirstFailure() {
        return stopOnFirstFailure;
    }

    public void setStopOnFirstFailure(boolean stopOnFirstFailure) {
        this.stopOnFirstFailure = stopOnFirstFailure;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(String finishedAt) {
        this.finishedAt = finishedAt;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getPassed() {
        return passed;
    }

    public void setPassed(int passed) {
        this.passed = passed;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public List<CaseResult> getCases() {
        return cases;
    }

    public void setCases(List<CaseResult> cases) {
        this.cases = cases;
    }
}
