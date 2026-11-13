package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TestGroup {

    private String name;
    private String description;
    @JsonProperty("type")
    private String type = "group";
    private List<TestGroup> children = new ArrayList<>();
    private List<TestSuite> suites = new ArrayList<>();

    public TestGroup() {
    }

    public TestGroup(String name, String description) {
        this.name = name;
        this.description = description;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<TestGroup> getChildren() {
        return children;
    }

    public void setChildren(List<TestGroup> children) {
        this.children = children;
    }

    public List<TestSuite> getSuites() {
        return suites;
    }

    public void setSuites(List<TestSuite> suites) {
        this.suites = suites;
    }
}
