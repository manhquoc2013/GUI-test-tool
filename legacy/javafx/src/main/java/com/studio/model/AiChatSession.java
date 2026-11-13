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

    /** How much of each user prompt is indexed for the sidebar search (memory bound). */
    private static final int SEARCH_TEXT_CHARS_PER_MESSAGE = 240;
    /** Maximum number of user prompts indexed per session. */
    private static final int SEARCH_TEXT_MAX_MESSAGES = 20;

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
    /**
     * Target group/suite captured when the conversation starts. Empty means "(Tự động tạo khi lưu)".
     * It lives on the session because it is locked for the whole conversation: changing the picker
     * mid-conversation used to move the transcript to another storage bucket and hide the history.
     */
    private String targetGroup;
    private String targetSuite;
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

    /** Target group chosen when the conversation started; null/blank means "create on save". */
    public String getTargetGroup() {
        return targetGroup;
    }

    public void setTargetGroup(String targetGroup) {
        this.targetGroup = targetGroup;
    }

    /** Target suite chosen when the conversation started; null/blank means "create on save". */
    public String getTargetSuite() {
        return targetSuite;
    }

    public void setTargetSuite(String targetSuite) {
        this.targetSuite = targetSuite;
    }

    /** True once the user has sent at least one prompt, which locks the target pickers. */
    public boolean hasUserMessages() {
        for (AiChatMessage message : messages) {
            if (message != null && "user".equalsIgnoreCase(message.getRole())) {
                return true;
            }
        }
        return false;
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
        meta.setSearchText(buildSearchText());
        return meta;
    }

    /** Title + a bounded slice of user prompts, so the sidebar search can match message text. */
    private String buildSearchText() {
        StringBuilder blob = new StringBuilder(getTitle());
        if (messages == null) {
            return blob.toString();
        }
        int scanned = 0;
        for (AiChatMessage message : messages) {
            if (message == null || !"user".equalsIgnoreCase(message.getRole())) {
                continue;
            }
            String text = message.getRawPrompt();
            if (text == null || text.isBlank()) {
                text = message.getContent();
            }
            if (text == null || text.isBlank()) {
                continue;
            }
            blob.append(' ').append(text, 0, Math.min(text.length(), SEARCH_TEXT_CHARS_PER_MESSAGE));
            if (++scanned >= SEARCH_TEXT_MAX_MESSAGES) {
                break;
            }
        }
        return blob.toString();
    }
}
