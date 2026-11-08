package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lightweight metadata model representing a chat session for list views and history management.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiChatSessionMeta {

    private String id;
    private String suiteKey;
    private String title;
    private int messageCount;
    private int totalTokens;
    private long createdAt;
    private long updatedAt;
    private boolean pinned;

    public AiChatSessionMeta() {
    }

    public AiChatSessionMeta(String id, String suiteKey, String title, int messageCount, int totalTokens, long createdAt, long updatedAt, boolean pinned) {
        this.id = id;
        this.suiteKey = suiteKey;
        this.title = title;
        this.messageCount = messageCount;
        this.totalTokens = totalTokens;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.pinned = pinned;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSuiteKey() {
        return suiteKey;
    }

    public void setSuiteKey(String suiteKey) {
        this.suiteKey = suiteKey;
    }

    public String getTitle() {
        return title != null && !title.isBlank() ? title : "Cuộc trò chuyện mới";
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    @Override
    public String toString() {
        return getTitle();
    }
}

