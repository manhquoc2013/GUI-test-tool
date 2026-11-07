package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the persistent chat history and context for a specific test suite or workspace.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiChatSession {

    private String suiteKey;
    private String summary;
    private List<AiChatMessage> messages = new ArrayList<>();
    private long lastUpdated;

    public AiChatSession() {
        this.lastUpdated = System.currentTimeMillis();
    }

    public AiChatSession(String suiteKey) {
        this();
        this.suiteKey = suiteKey != null ? suiteKey : "";
    }

    public String getSuiteKey() {
        return suiteKey;
    }

    public void setSuiteKey(String suiteKey) {
        this.suiteKey = suiteKey;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<AiChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AiChatMessage> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void addMessage(AiChatMessage message) {
        if (message != null) {
            this.messages.add(message);
            this.lastUpdated = System.currentTimeMillis();
        }
    }

    public boolean removeMessage(String messageId) {
        if (messageId == null) return false;
        boolean removed = this.messages.removeIf(m -> messageId.equals(m.getId()));
        if (removed) {
            this.lastUpdated = System.currentTimeMillis();
        }
        return removed;
    }

    public int calculateTotalTokens() {
        int sum = 0;
        for (AiChatMessage msg : messages) {
            sum += msg.getEstimatedTokens();
        }
        return sum;
    }
}

