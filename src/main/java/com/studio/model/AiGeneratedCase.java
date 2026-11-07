package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AiGeneratedCase {

    private String name = "";
    private String description = "";
    private String startUrl = "";
    private String scriptCode = "";
    private List<Assertion> assertions = new ArrayList<>();
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

    public String getScriptCode() {
        return scriptCode;
    }

    public void setScriptCode(String scriptCode) {
        this.scriptCode = scriptCode != null ? scriptCode : "";
    }

    public List<Assertion> getAssertions() {
        return assertions;
    }

    public void setAssertions(List<Assertion> assertions) {
        this.assertions = assertions != null ? assertions : new ArrayList<>();
    }

    public String getTargetCaseId() {
        return targetCaseId;
    }

    public void setTargetCaseId(String targetCaseId) {
        this.targetCaseId = targetCaseId;
    }
}

