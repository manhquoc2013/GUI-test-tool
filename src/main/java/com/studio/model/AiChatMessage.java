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
    private String sessionId;
    private String role; // "user", "assistant", "system"
    private String content;
    private long timestamp;
    private int estimatedTokens;
    private List<String> attachedFiles = new ArrayList<>();
    private List<String> attachedFilePaths = new ArrayList<>();
    private List<AttachedDocSnapshot> attachedDocsSnapshot = new ArrayList<>();
    private String rawPrompt;
    private String skill;
    private List<AiGeneratedCase> generatedCasesSnapshot = new ArrayList<>();
    private String suiteSnapshotJson;
    private String suiteSnapshotDir;
    private boolean appliedToSuite;
    private List<AiClarificationQuestion> clarificationQuestions = new ArrayList<>();
    private java.util.Map<String, String> clarificationAnswers = new java.util.LinkedHashMap<>();
    private boolean clarificationAnswered;
    private String intent;
    private String suggestedGroup;
    private String suggestedSuite;
    /** Sub-agent that produced this message; null for the parent session's own turns. */
    private String agentId;
    private String agentName;
    private String agentRole;
    /** Set on the single aggregated answer posted back to the parent. */
    private boolean aggregatedResult;

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

    /** True when this message belongs to a sub-agent rather than the parent session. */
    public boolean isAgentMessage() {
        return agentName != null && !agentName.isBlank();
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
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

    public boolean isAggregatedResult() {
        return aggregatedResult;
    }

    public void setAggregatedResult(boolean aggregatedResult) {
        this.aggregatedResult = aggregatedResult;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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

    public List<AiClarificationQuestion> getClarificationQuestions() {
        return clarificationQuestions;
    }

    public void setClarificationQuestions(List<AiClarificationQuestion> clarificationQuestions) {
        this.clarificationQuestions = clarificationQuestions != null ? clarificationQuestions : new ArrayList<>();
    }

    public boolean hasClarificationQuestions() {
        return clarificationQuestions != null && !clarificationQuestions.isEmpty();
    }

    public java.util.Map<String, String> getClarificationAnswers() {
        return clarificationAnswers;
    }

    public void setClarificationAnswers(java.util.Map<String, String> clarificationAnswers) {
        this.clarificationAnswers = clarificationAnswers != null ? clarificationAnswers : new java.util.LinkedHashMap<>();
    }

    public boolean isClarificationAnswered() {
        return clarificationAnswered;
    }

    public void setClarificationAnswered(boolean clarificationAnswered) {
        this.clarificationAnswered = clarificationAnswered;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getSuggestedGroup() {
        return suggestedGroup;
    }

    public void setSuggestedGroup(String suggestedGroup) {
        this.suggestedGroup = suggestedGroup;
    }

    public String getSuggestedSuite() {
        return suggestedSuite;
    }

    public void setSuggestedSuite(String suggestedSuite) {
        this.suggestedSuite = suggestedSuite;
    }

    public String getRawPrompt() {
        return rawPrompt;
    }

    public void setRawPrompt(String rawPrompt) {
        this.rawPrompt = rawPrompt;
    }

    public String getSkill() {
        return skill;
    }

    public void setSkill(String skill) {
        this.skill = skill;
    }

    public List<String> getAttachedFilePaths() {
        return attachedFilePaths;
    }

    public void setAttachedFilePaths(List<String> attachedFilePaths) {
        this.attachedFilePaths = attachedFilePaths != null ? attachedFilePaths : new ArrayList<>();
    }

    public List<AttachedDocSnapshot> getAttachedDocsSnapshot() {
        return attachedDocsSnapshot;
    }

    public void setAttachedDocsSnapshot(List<AttachedDocSnapshot> attachedDocsSnapshot) {
        this.attachedDocsSnapshot = attachedDocsSnapshot != null ? attachedDocsSnapshot : new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttachedDocSnapshot {
        private String fileName;
        private String filePath;
        private boolean image;
        private String content;
        private String mimeType;
        private long sizeBytes;
        private String markdownPath;

        public AttachedDocSnapshot() {}

        public AttachedDocSnapshot(String fileName, String filePath, boolean image, String content, String mimeType,
                long sizeBytes, String markdownPath) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.image = image;
            this.content = content;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.markdownPath = markdownPath;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public boolean isImage() {
            return image;
        }

        public void setImage(boolean image) {
            this.image = image;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getMimeType() {
            return mimeType;
        }

        public void setMimeType(String mimeType) {
            this.mimeType = mimeType;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public void setSizeBytes(long sizeBytes) {
            this.sizeBytes = sizeBytes;
        }

        public String getMarkdownPath() {
            return markdownPath;
        }

        public void setMarkdownPath(String markdownPath) {
            this.markdownPath = markdownPath;
        }
    }
}

