package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One delegated sub-agent session: an isolated unit of work that processes exactly one
 * assigned rule/task and reports a structured result back to its parent session.
 *
 * Lifecycle: PENDING -> RUNNING -> COMPLETED | FAILED | CANCELLED.
 * A child never sees another child's transcript, only its own rule and the shared brief.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiSubAgentSession {

    public enum Status { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }

    private String id;
    /** Parent chat session this sub-agent reports to. */
    private String parentSessionId;
    /** Stable agent name shown in the chat (e.g. "SpecMiner"). */
    private String agentName;
    /** Human-readable role label used in the bubble header. */
    private String agentRole;
    /** The single rule this agent must follow; anything outside it is out of scope. */
    private String ruleId;
    private String ruleText;
    /** Task brief given to this agent. */
    private String taskBrief;
    private Status status = Status.PENDING;
    private String resultSummary;
    private List<String> findings = new ArrayList<>();
    private String errorMessage;
    private long createdAt = System.currentTimeMillis();
    private long startedAt;
    private long finishedAt;
    /** Ordered transcript of this agent's own exchanges. */
    private List<AiChatMessage> messages = new ArrayList<>();
    /**
     * Non-secret diagnostic metadata (tokens, durations). Written by the agent worker while the token badge reads them on the FX
     * thread, so it must be concurrent: a plain {@link java.util.LinkedHashMap} could corrupt its
     * buckets and hang the UI thread mid-render.
     */
    private Map<String, String> metrics = new ConcurrentHashMap<>();

    public AiSubAgentSession() {
    }

    public AiSubAgentSession(String parentSessionId, String agentName, String agentRole,
                             String ruleId, String ruleText, String taskBrief) {
        this.parentSessionId = parentSessionId;
        this.agentName = agentName;
        this.agentRole = agentRole;
        this.ruleId = ruleId;
        this.ruleText = ruleText;
        this.taskBrief = taskBrief;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParentSessionId() {
        return parentSessionId;
    }

    public void setParentSessionId(String parentSessionId) {
        this.parentSessionId = parentSessionId;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getAgentRole() {
        return agentRole;
    }

    public void setAgentRole(String agentRole) {
        this.agentRole = agentRole;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleText() {
        return ruleText;
    }

    public void setRuleText(String ruleText) {
        this.ruleText = ruleText;
    }

    public String getTaskBrief() {
        return taskBrief;
    }

    public void setTaskBrief(String taskBrief) {
        this.taskBrief = taskBrief;
    }

    public Status getStatus() {
        return status != null ? status : Status.PENDING;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public List<String> getFindings() {
        return findings;
    }

    public void setFindings(List<String> findings) {
        this.findings = findings != null ? findings : new ArrayList<>();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(long finishedAt) {
        this.finishedAt = finishedAt;
    }

    public List<AiChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AiChatMessage> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
    }

    public Map<String, String> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, String> metrics) {
        // Wrapped, not stored as-is: deserialization (and callers) hand over a plain HashMap.
        this.metrics = metrics != null ? new ConcurrentHashMap<>(metrics) : new ConcurrentHashMap<>();
    }

    /** Duration in ms for a finished agent; 0 while pending. */
    public long getDurationMs() {
        if (startedAt <= 0) return 0;
        long end = finishedAt > 0 ? finishedAt : System.currentTimeMillis();
        return Math.max(0, end - startedAt);
    }
}
