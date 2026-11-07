package com.studio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.*;
import com.studio.store.AiChatHistoryStore;
import com.studio.util.StudioPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * High-performance synchronization service that mirrors file-based project assets,
 * test runs, and chat sessions into the local embedded H2 database.
 */
public class LocalDataIndexService {

    private final DatabaseManager dbManager;
    private final ObjectMapper mapper;

    public LocalDataIndexService() {
        this(DatabaseManager.getInstance(), new ObjectMapper());
    }

    public LocalDataIndexService(DatabaseManager dbManager, ObjectMapper mapper) {
        this.dbManager = dbManager;
        this.mapper = mapper;
    }

    /**
     * Complete synchronization of workspace files and reports into H2 cache.
     */
    public synchronized void syncAllFromDisk() {
        Path projectDir = StudioPaths.getProjectDir();
        if (Files.exists(projectDir)) {
            syncProjectDirectory(projectDir);
        }

        Path reportsDir = StudioPaths.getDataDir().resolve("reports");
        if (Files.exists(reportsDir)) {
            syncReportsDirectory(reportsDir);
        }
    }

    public void syncProjectDirectory(Path projectDir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(projectDir)) {
            for (Path groupDir : stream) {
                if (!Files.isDirectory(groupDir)) continue;
                Path groupJson = groupDir.resolve("group.json");
                if (Files.exists(groupJson)) {
                    TestGroup group = mapper.readValue(groupJson.toFile(), TestGroup.class);
                    indexGroup(group, groupDir);
                }

                try (DirectoryStream<Path> suiteStream = Files.newDirectoryStream(groupDir)) {
                    for (Path suiteDir : suiteStream) {
                        if (!Files.isDirectory(suiteDir)) continue;
                        Path suiteJson = suiteDir.resolve("suite.json");
                        if (Files.exists(suiteJson)) {
                            TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);
                            indexSuite(suite, suiteDir, groupDir.getFileName().toString());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void syncReportsDirectory(Path reportsDir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(reportsDir)) {
            for (Path runDir : stream) {
                if (!Files.isDirectory(runDir)) continue;
                Path summaryJson = runDir.resolve("summary.json");
                if (Files.exists(summaryJson)) {
                    try {
                        RunReport report = mapper.readValue(summaryJson.toFile(), RunReport.class);
                        indexReport(report);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ── Group Indexing ──

    public void indexGroup(TestGroup group, Path groupPath) {
        if (group == null) return;
        String sql = "MERGE INTO test_groups (id, name, description, parent_id, path, updated_at) KEY(id) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, group.getId() != null ? group.getId() : group.getName());
            ps.setString(2, group.getName());
            ps.setString(3, group.getDescription());
            ps.setString(4, group.getParentId());
            ps.setString(5, groupPath != null ? groupPath.toString() : "");
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    public void deleteGroup(String groupId) {
        if (groupId == null) return;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM test_groups WHERE id = ? OR name = ?")) {
            ps.setString(1, groupId);
            ps.setString(2, groupId);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // ── Suite & Case Indexing ──

    public void indexSuite(TestSuite suite, Path suitePath, String groupName) {
        if (suite == null) return;
        String sql = "MERGE INTO test_suites (id, group_name, name, description, browser, headless, path, updated_at) KEY(id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String suiteId = (groupName != null ? groupName : "") + "/" + suite.getName();

        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, suiteId);
                ps.setString(2, groupName != null ? groupName : "");
                ps.setString(3, suite.getName());
                ps.setString(4, suite.getDescription());
                ps.setString(5, suite.getBrowser());
                ps.setBoolean(6, suite.getHeadless() != null && suite.getHeadless());
                ps.setString(7, suitePath != null ? suitePath.toString() : "");
                ps.setLong(8, System.currentTimeMillis());
                ps.executeUpdate();
            }

            // Index all cases in this suite
            if (suite.getCases() != null) {
                for (TestCase tc : suite.getCases()) {
                    String scriptContent = "";
                    if (suitePath != null && tc.getScriptFile() != null) {
                        Path scriptPath = suitePath.resolve(tc.getScriptFile());
                        if (Files.exists(scriptPath)) {
                            scriptContent = Files.readString(scriptPath, StandardCharsets.UTF_8);
                        }
                    }
                    indexCaseInternal(conn, tc, groupName, suite.getName(), suiteId, scriptContent);
                }
            }

            // Index chat history for suite if present
            if (suitePath != null) {
                Path chatJson = suitePath.resolve(AiChatHistoryStore.CHAT_HISTORY_FILE);
                if (Files.exists(chatJson)) {
                    try {
                        AiChatSession session = mapper.readValue(chatJson.toFile(), AiChatSession.class);
                        if (session.getMessages() != null) {
                            for (AiChatMessage msg : session.getMessages()) {
                                indexChatMessageInternal(conn, msg, suiteId);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    public void deleteSuite(String groupName, String suiteName) {
        String suiteId = groupName + "/" + suiteName;
        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM test_suites WHERE id = ?")) {
                ps.setString(1, suiteId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM test_cases WHERE group_name = ? AND suite_name = ?")) {
                ps.setString(1, groupName);
                ps.setString(2, suiteName);
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {}
    }

    public void indexCase(TestCase tc, String groupName, String suiteName, String scriptContent) {
        if (tc == null) return;
        String suiteId = (groupName != null ? groupName : "") + "/" + (suiteName != null ? suiteName : "");
        try (Connection conn = dbManager.getConnection()) {
            indexCaseInternal(conn, tc, groupName, suiteName, suiteId, scriptContent);
        } catch (SQLException ignored) {}
    }

    private void indexCaseInternal(Connection conn, TestCase tc, String groupName, String suiteName,
                                   String suiteId, String scriptContent) throws SQLException {
        String sql = """
            MERGE INTO test_cases (id, suite_id, group_name, suite_name, name, description, status,
                                   start_url, script_file, script_content, assertions_count, updated_at)
            KEY(id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        int assertCount = tc.getAssertions() != null ? tc.getAssertions().size() : 0;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tc.getId());
            ps.setString(2, suiteId);
            ps.setString(3, groupName != null ? groupName : "");
            ps.setString(4, suiteName != null ? suiteName : "");
            ps.setString(5, tc.getName());
            ps.setString(6, tc.getDescription());
            ps.setString(7, tc.getStatus() != null ? tc.getStatus() : "Draft");
            ps.setString(8, tc.getStartUrl());
            ps.setString(9, tc.getScriptFile());
            ps.setString(10, scriptContent != null ? scriptContent : "");
            ps.setInt(11, assertCount);
            ps.setLong(12, System.currentTimeMillis());
            ps.executeUpdate();
        }

        // Index assertions
        if (tc.getAssertions() != null) {
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM test_assertions WHERE case_id = ?")) {
                del.setString(1, tc.getId());
                del.executeUpdate();
            }

            String assertSql = "INSERT INTO test_assertions (id, case_id, assert_type, locator, expected_val, status, order_index) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ins = conn.prepareStatement(assertSql)) {
                int idx = 0;
                for (Assertion a : tc.getAssertions()) {
                    String aId = tc.getId() + "_a" + idx;
                    ins.setString(1, aId);
                    ins.setString(2, tc.getId());
                    ins.setString(3, a.getType());
                    ins.setString(4, a.getLocator());
                    ins.setString(5, a.getValue());
                    ins.setString(6, a.getStatus());
                    ins.setInt(7, idx++);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        }
    }

    public void deleteCase(String caseId) {
        if (caseId == null) return;
        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM test_cases WHERE id = ?")) {
                ps.setString(1, caseId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM test_assertions WHERE case_id = ?")) {
                ps.setString(1, caseId);
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {}
    }

    // ── Run Report Indexing ──

    public void indexReport(RunReport report) {
        if (report == null || report.getRunId() == null) return;
        String sql = """
            MERGE INTO run_reports (run_id, suite_name, run_target, start_time, duration_ms,
                                    total_cases, passed_cases, failed_cases, skipped_cases, report_path, status)
            KEY(run_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, report.getRunId());
            ps.setString(2, report.getSuiteName() != null ? report.getSuiteName() : "");
            ps.setString(3, report.getRunTarget());
            ps.setLong(4, report.getStartTime());
            ps.setLong(5, report.getDurationMs());
            ps.setInt(6, report.getTotalCases());
            ps.setInt(7, report.getPassedCases());
            ps.setInt(8, report.getFailedCases());
            ps.setInt(9, report.getSkippedCases());
            ps.setString(10, report.getReportPath());
            ps.setString(11, report.getStatus());
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    // ── AI Chat Message Indexing ──

    public void indexChatMessage(AiChatMessage msg, String suiteKey) {
        if (msg == null) return;
        try (Connection conn = dbManager.getConnection()) {
            indexChatMessageInternal(conn, msg, suiteKey);
        } catch (SQLException ignored) {}
    }

    private void indexChatMessageInternal(Connection conn, AiChatMessage msg, String suiteKey) throws SQLException {
        String sql = """
            MERGE INTO ai_chat_messages (id, suite_key, role, content, estimated_tokens,
                                         attached_files, suite_snapshot_json, applied_to_suite, created_at)
            KEY(id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        String attached = msg.getAttachedFiles() != null ? String.join(";", msg.getAttachedFiles()) : "";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, msg.getId());
            ps.setString(2, suiteKey != null ? suiteKey : "global");
            ps.setString(3, msg.getRole());
            ps.setString(4, msg.getContent());
            ps.setInt(5, msg.getEstimatedTokens());
            ps.setString(6, attached);
            ps.setString(7, msg.getSuiteSnapshotJson());
            ps.setBoolean(8, msg.isAppliedToSuite());
            ps.setLong(9, msg.getTimestamp());
            ps.executeUpdate();
        }
    }

    public void clearChatForSuite(String suiteKey) {
        if (suiteKey == null) return;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM ai_chat_messages WHERE suite_key = ?")) {
            ps.setString(1, suiteKey);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }
}
