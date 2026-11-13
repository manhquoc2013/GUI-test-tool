package com.studio.db;

import com.studio.model.AiChatMessage;
import com.studio.model.AiChatSessionMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fast query service for AI Chat history and session management powered by H2 index.
 */
public class AiChatDbService {

    private final DatabaseManager dbManager;

    public AiChatDbService() {
        this(DatabaseManager.getInstance());
    }

    public AiChatDbService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * List all chat sessions for a suiteKey ordered by pinned status (desc) and updated_at (desc).
     */
    public List<AiChatSessionMeta> listSessions(String suiteKey) {
        List<AiChatSessionMeta> list = new ArrayList<>();
        String sql = """
            SELECT s.id, s.suite_key, s.title, s.pinned, s.created_at, s.updated_at,
                   COUNT(m.id) AS msg_count,
                   COALESCE(SUM(m.estimated_tokens), 0) AS total_tokens
            FROM ai_chat_sessions s
            LEFT JOIN ai_chat_messages m ON s.id = m.session_id
            WHERE s.suite_key = ?
            GROUP BY s.id, s.suite_key, s.title, s.pinned, s.created_at, s.updated_at
            ORDER BY s.pinned DESC, s.updated_at DESC
            """;

        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, suiteKey != null ? suiteKey : "global");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AiChatSessionMeta meta = new AiChatSessionMeta();
                    meta.setId(rs.getString("id"));
                    meta.setSuiteKey(rs.getString("suite_key"));
                    meta.setTitle(rs.getString("title"));
                    meta.setPinned(rs.getBoolean("pinned"));
                    meta.setCreatedAt(rs.getLong("created_at"));
                    meta.setUpdatedAt(rs.getLong("updated_at"));
                    meta.setMessageCount(rs.getInt("msg_count"));
                    meta.setTotalTokens(rs.getInt("total_tokens"));
                    list.add(meta);
                }
            }
        } catch (SQLException ignored) {}

        return list;
    }

    /**
     * Upsert a session's metadata.
     */
    public void upsertSession(AiChatSessionMeta meta) {
        if (meta == null || meta.getId() == null) return;
        String sql = """
            MERGE INTO ai_chat_sessions (id, suite_key, title, pinned, created_at, updated_at)
            KEY(id) VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, meta.getId());
            ps.setString(2, meta.getSuiteKey() != null ? meta.getSuiteKey() : "global");
            ps.setString(3, meta.getTitle());
            ps.setBoolean(4, meta.isPinned());
            ps.setLong(5, meta.getCreatedAt());
            ps.setLong(6, meta.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    /**
     * Rename a chat session.
     */
    public void renameSession(String sessionId, String newTitle) {
        if (sessionId == null || newTitle == null || newTitle.isBlank()) return;
        String sql = "UPDATE ai_chat_sessions SET title = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newTitle.trim());
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, sessionId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    /**
     * Toggle pin status for a chat session.
     */
    public void setSessionPinned(String sessionId, boolean pinned) {
        if (sessionId == null) return;
        String sql = "UPDATE ai_chat_sessions SET pinned = ? WHERE id = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, pinned);
            ps.setString(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    /**
     * Delete a chat session and its associated messages.
     */
    public void deleteSession(String sessionId) {
        if (sessionId == null) return;
        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ai_chat_messages WHERE session_id = ?")) {
                ps.setString(1, sessionId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM ai_chat_sessions WHERE id = ?")) {
                ps.setString(1, sessionId);
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {}
    }

    /**
     * Fetch messages for a specific session ordered chronologically.
     */
    public List<AiChatMessage> getMessages(String suiteKey, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return getMessages(suiteKey);
        }
        List<AiChatMessage> list = new ArrayList<>();
        String sql = "SELECT * FROM ai_chat_messages WHERE session_id = ? ORDER BY created_at ASC";

        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AiChatMessage m = mapMessageRow(rs);
                    list.add(m);
                }
            }
        } catch (SQLException ignored) {}

        return list;
    }

    /**
     * Fetch all messages for a specific suite ordered chronologically (legacy fallback).
     */
    public List<AiChatMessage> getMessages(String suiteKey) {
        List<AiChatMessage> list = new ArrayList<>();
        String sql = "SELECT * FROM ai_chat_messages WHERE suite_key = ? ORDER BY created_at ASC";

        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, suiteKey != null ? suiteKey : "global");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AiChatMessage m = mapMessageRow(rs);
                    list.add(m);
                }
            }
        } catch (SQLException ignored) {}

        return list;
    }

    private AiChatMessage mapMessageRow(ResultSet rs) throws SQLException {
        AiChatMessage m = new AiChatMessage();
        m.setId(rs.getString("id"));
        try {
            m.setSessionId(rs.getString("session_id"));
        } catch (Exception ignored) {}
        m.setRole(rs.getString("role"));
        m.setContent(rs.getString("content"));
        m.setEstimatedTokens(rs.getInt("estimated_tokens"));
        m.setSuiteSnapshotJson(rs.getString("suite_snapshot_json"));
        m.setAppliedToSuite(rs.getBoolean("applied_to_suite"));
        m.setTimestamp(rs.getLong("created_at"));

        String attached = rs.getString("attached_files");
        if (attached != null && !attached.isBlank()) {
            m.setAttachedFiles(Arrays.asList(attached.split(";")));
        }
        return m;
    }

    /**
     * Compute total tokens used in a session in 1 SQL query.
     */
    public int calculateTotalTokens(String suiteKey, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return calculateTotalTokens(suiteKey);
        }
        String sql = "SELECT COALESCE(SUM(estimated_tokens), 0) FROM ai_chat_messages WHERE session_id = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ignored) {}
        return 0;
    }

    /**
     * Compute total tokens used in a suite session in 1 SQL query (legacy).
     */
    public int calculateTotalTokens(String suiteKey) {
        String sql = "SELECT COALESCE(SUM(estimated_tokens), 0) FROM ai_chat_messages WHERE suite_key = ?";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, suiteKey != null ? suiteKey : "global");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException ignored) {}
        return 0;
    }
}
