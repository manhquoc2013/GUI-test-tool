package com.studio.db;

import com.studio.model.AiChatMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fast query service for AI Chat history powered by H2 index.
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
     * Fetch all messages for a specific suite ordered chronologically.
     */
    public List<AiChatMessage> getMessages(String suiteKey) {
        List<AiChatMessage> list = new ArrayList<>();
        String sql = "SELECT * FROM ai_chat_messages WHERE suite_key = ? ORDER BY created_at ASC";

        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, suiteKey != null ? suiteKey : "global");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AiChatMessage m = new AiChatMessage();
                    m.setId(rs.getString("id"));
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
                    list.add(m);
                }
            }
        } catch (SQLException ignored) {}

        return list;
    }

    /**
     * Compute total tokens used in a suite session in 1 SQL query.
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
