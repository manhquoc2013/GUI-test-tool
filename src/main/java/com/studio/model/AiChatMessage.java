package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Model representing a chat message in the AI Assistant, with support for
 * token estimation, snapshots for undo/rollback, and attachment metadata.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiChatMessage {

    private String id;
    private String role; // "user", "assistant", "system"
    private String content;
    private long timestamp;
    private int estimatedTokens;
    private List<String> attachedFiles = new ArrayList<>();
    private List<AiGeneratedCase> generatedCasesSnapshot = new ArrayList<>();
    private String suiteSnapshotJson;
    private String suiteSnapshotDir;
    private boolean appliedToSuite;

    public AiChatMessage() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
    }

    public AiChatMessage(String role, String content) {
        this();
        this.role = role;
        this.content = content != null ? content : "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getEstimatedTokens() {
        return estimatedTokens;
    }

    public void setEstimatedTokens(int estimatedTokens) {
        this.estimatedTokens = estimatedTokens;
    }

    public List<String> getAttachedFiles() {
        return attachedFiles;
    }

    public void setAttachedFiles(List<String> attachedFiles) {
        this.attachedFiles = attachedFiles != null ? attachedFiles : new ArrayList<>();
    }

    public List<AiGeneratedCase> getGeneratedCasesSnapshot() {
        return generatedCasesSnapshot;
    }

    public void setGeneratedCasesSnapshot(List<AiGeneratedCase> generatedCasesSnapshot) {
        this.generatedCasesSnapshot = generatedCasesSnapshot != null ? generatedCasesSnapshot : new ArrayList<>();
    }

    public String getSuiteSnapshotJson() {
        return suiteSnapshotJson;
    }

    public void setSuiteSnapshotJson(String suiteSnapshotJson) {
        this.suiteSnapshotJson = suiteSnapshotJson;
    }

    public String getSuiteSnapshotDir() {
        return suiteSnapshotDir;
    }

    public void setSuiteSnapshotDir(String suiteSnapshotDir) {
        this.suiteSnapshotDir = suiteSnapshotDir;
    }

    public boolean isAppliedToSuite() {
        return appliedToSuite;
    }

    public void setAppliedToSuite(boolean appliedToSuite) {
        this.appliedToSuite = appliedToSuite;
    }
}

