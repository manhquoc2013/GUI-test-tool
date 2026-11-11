package com.studio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Encapsulates the persistent chat history and context for a specific test suite or workspace.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiChatSession {

    private String id;
    private String suiteKey;
    private String title;
    private String summary;
    /**
     * Copy-on-write: the AI worker thread appends while the JavaFX thread iterates to render the
     * transcript and token badge. A plain ArrayList threw ConcurrentModificationException there.
     */
    private List<AiChatMessage> messages = new CopyOnWriteArrayList<>();
    private long createdAt;
    private long lastUpdated;
    private boolean pinned;
    /** Sub-agent sessions spawned from this session, in dispatch order. */
    private List<AiSubAgentSession> subAgents = new CopyOnWriteArrayList<>();

    public AiChatSession() {
        this.id = UUID.randomUUID().toString();
        this.title = "Cuộc trò chuyện mới";
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.lastUpdated = now;
    }

    public AiChatSession(String suiteKey) {
        this();
        this.suiteKey = suiteKey != null ? suiteKey : "";
    }

    public AiChatSession(String id, String suiteKey, String title) {
        this();
        if (id != null && !id.isBlank()) {
            this.id = id;
        }
        this.suiteKey = suiteKey != null ? suiteKey : "";
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
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

    public String getSuiteKey() {
        return suiteKey;
    }

    public void setSuiteKey(String suiteKey) {
        this.suiteKey = suiteKey;
    }

    public String getTitle() {
        if (title == null || title.isBlank()) {
            return "Cuộc trò chuyện mới";
        }
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public List<AiSubAgentSession> getSubAgents() {
        return subAgents;
    }

    public void setSubAgents(List<AiSubAgentSession> subAgents) {
        this.subAgents = subAgents != null ? new CopyOnWriteArrayList<>(subAgents) : new CopyOnWriteArrayList<>();
    }

    public boolean hasSubAgents() {
        return subAgents != null && !subAgents.isEmpty();
    }

    public void setMessages(List<AiChatMessage> messages) {
        this.messages = messages != null ? new CopyOnWriteArrayList<>(messages) : new CopyOnWriteArrayList<>();
    }

    public long getCreatedAt() {
        return createdAt > 0 ? createdAt : lastUpdated;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public void addMessage(AiChatMessage message) {
        if (message != null) {
            if (message.getSessionId() == null || message.getSessionId().isBlank()) {
                message.setSessionId(getId());
            }
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

    public AiChatSessionMeta toMeta() {
        AiChatSessionMeta meta = new AiChatSessionMeta();
        meta.setId(getId());
        meta.setSuiteKey(getSuiteKey());
        meta.setTitle(getTitle());
        meta.setMessageCount(messages != null ? messages.size() : 0);
        meta.setTotalTokens(calculateTotalTokens());
        meta.setCreatedAt(getCreatedAt());
        meta.setUpdatedAt(getLastUpdated());
        meta.setPinned(isPinned());
        return meta;
    }
}
