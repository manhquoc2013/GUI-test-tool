package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * A named rule an agent must follow. The rule text is injected into that agent's system
 * prompt and defines the boundary of its work: an agent that needs something outside its
 * rule must report it back instead of doing it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiAgentRule {

    private String id;
    private String name;
    private String role;
    /** Ordered, concrete obligations. */
    private List<String> obligations = new ArrayList<>();
    /** What this agent must never do. */
    private List<String> prohibitions = new ArrayList<>();
    /** The output contract: what evidence the agent must return. */
    private List<String> requiredEvidence = new ArrayList<>();
    /**
     * Rule ids whose output this agent must read before it can work.
     *
     * <p>Declared here — in the fixed catalogue — rather than taken from the planner, because a
     * model-authored edge could put a review before the artefact it reviews, or hide a handoff.
     * The orchestrator turns these edges into execution waves and passes the upstream output into
     * the dependent agent's prompt; an edge naming a rule that is not part of this plan is simply
     * skipped, so a partial plan still runs.</p>
     */
    private List<String> dependsOn = new ArrayList<>();

    public AiAgentRule() {
    }

    public AiAgentRule(String id, String name, String role) {
        this.id = id;
        this.name = name;
        this.role = role;
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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public List<String> getObligations() {
        return obligations;
    }

    public void setObligations(List<String> obligations) {
        this.obligations = obligations != null ? obligations : new ArrayList<>();
    }

    public List<String> getProhibitions() {
        return prohibitions;
    }

    public void setProhibitions(List<String> prohibitions) {
        this.prohibitions = prohibitions != null ? prohibitions : new ArrayList<>();
    }

    public List<String> getRequiredEvidence() {
        return requiredEvidence;
    }

    public void setRequiredEvidence(List<String> requiredEvidence) {
        this.requiredEvidence = requiredEvidence != null ? requiredEvidence : new ArrayList<>();
    }

    /** Rule ids whose output this agent must read first; empty when the agent starts from the brief. */
    public List<String> getDependsOn() {
        return dependsOn;
    }

    public void setDependsOn(List<String> dependsOn) {
        this.dependsOn = dependsOn != null ? dependsOn : new ArrayList<>();
    }
}
