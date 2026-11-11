package com.studio.service;

import com.studio.model.AiChatSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the AI turn in flight for every conversation of the workspace.
 *
 * <p>Runs are keyed by session id, so a conversation that keeps generating while the user reads
 * another one stays observable: the history rail badges it as running, and the finished result is
 * marked unread until that conversation is opened. Nothing here touches the JavaFX thread directly —
 * callers publish changes through {@code Platform.runLater}.</p>
 */
public final class AiChatRunRegistry {

    /** Live state of the single in-flight turn of one conversation. */
    public static final class Run {
        private final AiChatSession session;
        private volatile String status;

        Run(AiChatSession session, String initialStatus) {
            this.session = session;
            this.status = initialStatus;
        }

        public AiChatSession getSession() {
            return session;
        }

        public String getStatus() {
            return status;
        }

        void setStatus(String nextStatus) {
            if (nextStatus != null && !nextStatus.isBlank()) {
                this.status = nextStatus;
            }
        }
    }

    /** Conversations with a worker thread currently generating. */
    private final Map<String, Run> runs = new ConcurrentHashMap<>();
    /** Conversations that produced a result while the user was reading a different one. */
    private final Set<String> unread = ConcurrentHashMap.newKeySet();

    /** Registers the turn that is about to run for this conversation and returns its live state. */
    public Run startRun(AiChatSession session, String initialStatus) {
        if (session == null) {
            return null;
        }
        Run run = new Run(session, initialStatus);
        runs.put(session.getId(), run);
        return run;
    }

    public Run getRun(String sessionId) {
        return sessionId == null ? null : runs.get(sessionId);
    }

    public boolean isRunning(String sessionId) {
        return sessionId != null && runs.containsKey(sessionId);
    }

    /** The in-memory session of a running conversation: the only complete copy until the turn ends. */
    public AiChatSession getLiveSession(String sessionId) {
        Run run = getRun(sessionId);
        return run != null ? run.getSession() : null;
    }

    /** Publishes a progress line to one conversation's run; unknown or finished runs are ignored. */
    public void updateStatus(String sessionId, String status) {
        Run run = getRun(sessionId);
        if (run != null) {
            run.setStatus(status);
        }
    }

    /**
     * Ends one conversation's run. A turn that finishes while the user is reading another
     * conversation leaves its result unread, so the workspace can flag it until it is opened.
     */
    public void finishRun(String sessionId, String viewedSessionId) {
        if (sessionId == null || runs.remove(sessionId) == null) {
            return;
        }
        if (sessionId.equals(viewedSessionId)) {
            unread.remove(sessionId);
        } else {
            unread.add(sessionId);
        }
    }

    public boolean isUnread(String sessionId) {
        return sessionId != null && unread.contains(sessionId);
    }

    public int countUnread() {
        return unread.size();
    }

    /** Opens a conversation: its pending results are considered seen. */
    public void markRead(String sessionId) {
        if (sessionId != null) {
            unread.remove(sessionId);
        }
    }

    /** Running conversations other than the one on screen: the background work the user must notice. */
    public int countBackgroundRuns(String viewedSessionId) {
        int count = 0;
        for (String sessionId : runs.keySet()) {
            if (viewedSessionId == null || !viewedSessionId.equals(sessionId)) {
                count++;
            }
        }
        return count;
    }

    /** Drops every trace of a deleted conversation. */
    public void forget(String sessionId) {
        if (sessionId == null) {
            return;
        }
        runs.remove(sessionId);
        unread.remove(sessionId);
    }
}
