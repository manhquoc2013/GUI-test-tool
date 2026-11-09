package com.studio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.*;
import com.studio.store.AiChatHistoryStore;
import com.studio.util.StudioPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * High-performance synchronization service that mirrors file-based project
 * assets,
 * test runs, and chat sessions into the local embedded H2 database.
 */
public class LocalDataIndexService {

    private static final ObjectMapper DEFAULT_MAPPER = com.studio.store.ProjectStore.DEFAULT_MAPPER;

    private final DatabaseManager dbManager;
    private final ObjectMapper mapper;

    public LocalDataIndexService() {
        this(DatabaseManager.getInstance(), DEFAULT_MAPPER);
    }

    public LocalDataIndexService(DatabaseManager dbManager, ObjectMapper mapper) {
        this.dbManager = dbManager;
        this.mapper = mapper != null ? mapper : DEFAULT_MAPPER;
    }

    /**
     * Complete synchronization of workspace files and reports into H2 cache with
     * MTime caching.
     * Files unchanged since their recorded database timestamp are skipped.
     */
    public synchronized void syncAllFromDisk() {
        Path projectDir = StudioPaths.getProjectDir();
        Map<String, Long> groupMtimes = loadGroupMtimes();
        Map<String, Long> suiteMtimes = loadSuiteMtimes();

        if (Files.exists(projectDir)) {
            syncProjectDirectoryInternal(projectDir, groupMtimes, suiteMtimes);
        }

        Path reportsDir = StudioPaths.getDataDir().resolve("reports");
        if (Files.exists(reportsDir)) {
            syncReportsDirectory(reportsDir);
        }
    }

    public void syncProjectDirectory(Path projectDir) {
        if (projectDir == null || !Files.exists(projectDir))
            return;
        Map<String, Long> groupMtimes = loadGroupMtimes();
        Map<String, Long> suiteMtimes = loadSuiteMtimes();
        syncProjectDirectoryInternal(projectDir, groupMtimes, suiteMtimes);
    }

    private void syncProjectDirectoryInternal(Path projectDir, Map<String, Long> groupMtimes,
            Map<String, Long> suiteMtimes) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(projectDir)) {
            for (Path entry : stream) {
                if (!Files.isDirectory(entry))
                    continue;
                Path groupJson = entry.resolve("group.json");
                Path suiteJson = entry.resolve("suite.json");
                if (Files.exists(groupJson)) {
                    syncGroupDirectoryInternal(entry, entry.getFileName().toString(), groupMtimes, suiteMtimes);
                } else if (Files.exists(suiteJson)) {
                    syncSuiteFileIfModified(entry, "", childSuiteId("", entry.getFileName().toString()), suiteJson,
                            suiteMtimes);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void syncGroupDirectory(Path groupDir, String groupPath) {
        if (groupDir == null || !Files.exists(groupDir))
            return;
        syncGroupDirectoryInternal(groupDir, groupPath, new HashMap<>(), new HashMap<>());
    }

    private void syncGroupDirectoryInternal(Path groupDir, String groupPath, Map<String, Long> groupMtimes,
            Map<String, Long> suiteMtimes) {
        try {
            Path groupJson = groupDir.resolve("group.json");
            if (Files.exists(groupJson)) {
                String groupName = groupDir.getFileName().toString();
                long fileMtime = Files.getLastModifiedTime(groupJson).toMillis();
                Long cached = groupMtimes.get(groupName);
                if (cached == null || fileMtime > cached) {
                    try {
                        TestGroup group = mapper.readValue(groupJson.toFile(), TestGroup.class);
                        indexGroup(group, groupDir);
                        groupMtimes.put(groupName, System.currentTimeMillis());
                    } catch (Exception ignored) {
                    }
                }
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(groupDir)) {
                for (Path entry : stream) {
                    if (!Files.isDirectory(entry))
                        continue;
                    Path childGroupJson = entry.resolve("group.json");
                    Path childSuiteJson = entry.resolve("suite.json");
                    if (Files.exists(childGroupJson)) {
                        String nestedPath = (groupPath != null && !groupPath.isBlank())
                                ? groupPath + "/" + entry.getFileName().toString()
                                : entry.getFileName().toString();
                        syncGroupDirectoryInternal(entry, nestedPath, groupMtimes, suiteMtimes);
                    } else if (Files.exists(childSuiteJson)) {
                        syncSuiteFileIfModified(entry, groupPath,
                                childSuiteId(groupPath, entry.getFileName().toString()), childSuiteJson, suiteMtimes);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static String childSuiteId(String groupPath, String suiteName) {
        return (groupPath != null && !groupPath.isBlank() ? groupPath : "") + "/" + suiteName;
    }

    private void syncSuiteFileIfModified(Path suiteDir, String groupPath, String suiteId, Path suiteJson,
            Map<String, Long> suiteMtimes) {
        try {
            long suiteLastMod = Files.getLastModifiedTime(suiteJson).toMillis();
            Long cachedSuite = suiteMtimes.get(suiteId);
            if (cachedSuite == null || suiteLastMod > cachedSuite || hasModifiedScripts(suiteDir, cachedSuite)) {
                TestSuite suite = mapper.readValue(suiteJson.toFile(), TestSuite.class);
                indexSuite(suite, suiteDir, groupPath);
                suiteMtimes.put(suiteId, System.currentTimeMillis());
            }
        } catch (Exception ignored) {
        }
    }

    private boolean hasModifiedScripts(Path suiteDir, long cachedMtime) {
        try (DirectoryStream<Path> js = Files.newDirectoryStream(suiteDir, "*.java")) {
            for (Path p : js) {
                if (Files.getLastModifiedTime(p).toMillis() > cachedMtime) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public void syncReportsDirectory(Path reportsDir) {
        if (reportsDir == null || !Files.exists(reportsDir))
            return;
        Set<String> indexedReports = loadIndexedReportIds();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(reportsDir)) {
            for (Path runDir : stream) {
                if (!Files.isDirectory(runDir))
                    continue;
                String runId = runDir.getFileName().toString();
                if (indexedReports.contains(runId)) {
                    continue; // Skip already indexed reports
                }
                Path reportJson = runDir.resolve("report.json");
                if (!Files.exists(reportJson)) {
                    reportJson = runDir.resolve("summary.json");
                }
                if (Files.exists(reportJson)) {
                    try {
                        RunReport report = mapper.readValue(reportJson.toFile(), RunReport.class);
                        indexReport(report);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Map<String, Long> loadGroupMtimes() {
        Map<String, Long> map = new HashMap<>();
        try (Connection conn = dbManager.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT name, updated_at FROM test_groups")) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getLong(2));
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    private Map<String, Long> loadSuiteMtimes() {
        Map<String, Long> map = new HashMap<>();
        try (Connection conn = dbManager.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, updated_at FROM test_suites")) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getLong(2));
            }
        } catch (Exception ignored) {
        }
        return map;
    }

    private Set<String> loadIndexedReportIds() {
        Set<String> set = new HashSet<>();
        try (Connection conn = dbManager.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT run_id FROM run_reports")) {
            while (rs.next()) {
                set.add(rs.getString(1));
            }
        } catch (Exception ignored) {
        }
        return set;
    }

    // ── Group Indexing ──

    public void indexGroup(TestGroup group, Path groupPath) {
        if (group == null || group.getName() == null)
            return;
        String sql = "MERGE INTO test_groups (name, description, path, updated_at) KEY(name) VALUES (?, ?, ?, ?)";
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, group.getName());
            ps.setString(2, group.getDescription());
            ps.setString(3, groupPath != null ? groupPath.toString() : "");
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public void deleteGroup(String groupName) {
        if (groupName == null)
            return;
        String nestedPrefix = groupName + "/%";
        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement ps1 = conn.prepareStatement(
                    "DELETE FROM test_assertions WHERE case_id IN (SELECT id FROM test_cases WHERE group_name = ? OR group_name LIKE ?)");
                 PreparedStatement ps2 = conn.prepareStatement("DELETE FROM test_cases WHERE group_name = ? OR group_name LIKE ?");
                 PreparedStatement ps3 = conn.prepareStatement("DELETE FROM test_suites WHERE group_name = ? OR group_name LIKE ?");
                 PreparedStatement ps4 = conn.prepareStatement("DELETE FROM test_groups WHERE name = ?")) {
                ps1.setString(1, groupName);
                ps1.setString(2, nestedPrefix);
                ps1.executeUpdate();
                ps2.setString(1, groupName);
                ps2.setString(2, nestedPrefix);
                ps2.executeUpdate();
                ps3.setString(1, groupName);
                ps3.setString(2, nestedPrefix);
                ps3.executeUpdate();
                ps4.setString(1, groupName);
                ps4.executeUpdate();
            }
        } catch (SQLException ignored) {
        }
    }

    // ── Suite & Case Indexing ──

    public void indexSuite(TestSuite suite, Path suitePath, String groupName) {
        if (suite == null)
            return;
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

            // Index all cases in this suite and purge any removed/rolled-back cases
            Set<String> activeCaseIds = new HashSet<>();
            if (suite.getCases() != null) {
                for (TestCase tc : suite.getCases()) {
                    if (tc.getId() == null || tc.getId().isBlank()) {
                        String fallbackName = tc.getName() != null ? tc.getName() : "case";
                        tc.setId("tc-" + fallbackName.toLowerCase().replaceAll("[^a-z0-9]", "-"));
                    }
                    activeCaseIds.add(tc.getId());
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

            // Purge stale cases in H2 that were removed from this suite
            List<String> staleCaseIds = new java.util.ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM test_cases WHERE suite_id = ?")) {
                ps.setString(1, suiteId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String existingId = rs.getString(1);
                        if (existingId != null && !activeCaseIds.contains(existingId)) {
                            staleCaseIds.add(existingId);
                        }
                    }
                }
            }
            for (String staleId : staleCaseIds) {
                try (PreparedStatement delCase = conn.prepareStatement("DELETE FROM test_cases WHERE id = ?")) {
                    delCase.setString(1, staleId);
                    delCase.executeUpdate();
                }
                try (PreparedStatement delAssert = conn.prepareStatement("DELETE FROM test_assertions WHERE case_id = ?")) {
                    delAssert.setString(1, staleId);
                    delAssert.executeUpdate();
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
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void deleteSuite(String groupName, String suiteName) {
        String suiteId = groupName + "/" + suiteName;
        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement ps0 = conn.prepareStatement(
                    "DELETE FROM test_assertions WHERE case_id IN (SELECT id FROM test_cases WHERE group_name = ? AND suite_name = ?)")) {
                ps0.setString(1, groupName);
                ps0.setString(2, suiteName);
                ps0.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM test_suites WHERE id = ?")) {
                ps.setString(1, suiteId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn
                    .prepareStatement("DELETE FROM test_cases WHERE group_name = ? AND suite_name = ?")) {
                ps.setString(1, groupName);
                ps.setString(2, suiteName);
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {
        }
    }

    public void indexCase(TestCase tc, String groupName, String suiteName, String scriptContent) {
        if (tc == null)
            return;
        String suiteId = (groupName != null ? groupName : "") + "/" + (suiteName != null ? suiteName : "");
        try (Connection conn = dbManager.getConnection()) {
            indexCaseInternal(conn, tc, groupName, suiteName, suiteId, scriptContent);
        } catch (SQLException ignored) {
        }
    }

    private void indexCaseInternal(Connection conn, TestCase tc, String groupName, String suiteName,
            String suiteId, String scriptContent) throws SQLException {
        String sql = """
                MERGE INTO test_cases (id, suite_id, group_name, suite_name, name, description, status,
                                       start_url, script_file, script_content, assertions_count, updated_at, priority)
                KEY(id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            ps.setString(13, tc.getPriority() != null ? tc.getPriority() : "P1");
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
                    ins.setString(5, a.getEffectiveValue());
                    ins.setString(6, a.getStatus());
                    ins.setInt(7, idx++);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        }
    }

    public void deleteCase(String caseId) {
        if (caseId == null)
            return;
        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM test_cases WHERE id = ?")) {
                ps.setString(1, caseId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM test_assertions WHERE case_id = ?")) {
                ps.setString(1, caseId);
                ps.executeUpdate();
            }
        } catch (SQLException ignored) {
        }
    }

    // ── Run Report Indexing ──

    public void indexReport(RunReport report) {
        if (report == null || report.getRunId() == null)
            return;
        String sql = """
                MERGE INTO run_reports (run_id, scope, scope_name, scope_path, browser, started_at,
                                        finished_at, total_cases, passed_cases, failed_cases, skipped_cases)
                KEY(run_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, report.getRunId());
            ps.setString(2, report.getScope() != null ? report.getScope() : "");
            ps.setString(3, report.getScopeName() != null ? report.getScopeName() : "");
            ps.setString(4, report.getScopePath() != null ? report.getScopePath() : "");
            ps.setString(5, report.getBrowser() != null ? report.getBrowser() : "");
            ps.setString(6, report.getStartedAt() != null ? report.getStartedAt() : "");
            ps.setString(7, report.getFinishedAt() != null ? report.getFinishedAt() : "");
            ps.setInt(8, report.getTotal());
            ps.setInt(9, report.getPassed());
            ps.setInt(10, report.getFailed());
            ps.setInt(11, report.getSkipped());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    // ── AI Chat Message Indexing ──

    public void indexChatMessage(AiChatMessage msg, String suiteKey) {
        if (msg == null)
            return;
        try (Connection conn = dbManager.getConnection()) {
            indexChatMessageInternal(conn, msg, suiteKey);
        } catch (SQLException ignored) {
        }
    }

    public void indexChatMessages(java.util.List<AiChatMessage> messages, String suiteKey) {
        if (messages == null || messages.isEmpty())
            return;
        String sql = """
                MERGE INTO ai_chat_messages (id, suite_key, session_id, role, content, estimated_tokens,
                                             attached_files, suite_snapshot_json, applied_to_suite, created_at)
                KEY(id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            for (AiChatMessage msg : messages) {
                if (msg == null) continue;
                String attached = msg.getAttachedFiles() != null ? String.join(";", msg.getAttachedFiles()) : "";
                ps.setString(1, msg.getId());
                ps.setString(2, suiteKey != null ? suiteKey : "global");
                ps.setString(3, msg.getSessionId());
                ps.setString(4, msg.getRole());
                ps.setString(5, msg.getContent());
                ps.setInt(6, msg.getEstimatedTokens());
                ps.setString(7, attached);
                ps.setString(8, msg.getSuiteSnapshotJson());
                ps.setBoolean(9, msg.isAppliedToSuite());
                ps.setLong(10, msg.getTimestamp());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            System.err.println("[LocalDataIndexService] Warning: Failed to batch index chat messages: " + e.getMessage());
        }
    }

    public void indexChatSession(com.studio.model.AiChatSessionMeta session) {
        if (session == null || session.getId() == null) return;
        String sql = """
                MERGE INTO ai_chat_sessions (id, suite_key, title, pinned, created_at, updated_at)
                KEY(id) VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = dbManager.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, session.getId());
            ps.setString(2, session.getSuiteKey() != null ? session.getSuiteKey() : "global");
            ps.setString(3, session.getTitle());
            ps.setBoolean(4, session.isPinned());
            ps.setLong(5, session.getCreatedAt());
            ps.setLong(6, session.getUpdatedAt());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void indexChatMessageInternal(Connection conn, AiChatMessage msg, String suiteKey) throws SQLException {
        String sql = """
                MERGE INTO ai_chat_messages (id, suite_key, session_id, role, content, estimated_tokens,
                                             attached_files, suite_snapshot_json, applied_to_suite, created_at)
                KEY(id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String attached = msg.getAttachedFiles() != null ? String.join(";", msg.getAttachedFiles()) : "";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, msg.getId());
            ps.setString(2, suiteKey != null ? suiteKey : "global");
            ps.setString(3, msg.getSessionId());
            ps.setString(4, msg.getRole());
            ps.setString(5, msg.getContent());
            ps.setInt(6, msg.getEstimatedTokens());
            ps.setString(7, attached);
            ps.setString(8, msg.getSuiteSnapshotJson());
            ps.setBoolean(9, msg.isAppliedToSuite());
            ps.setLong(10, msg.getTimestamp());
            ps.executeUpdate();
        }
    }

    public void clearChatForSession(String sessionId) {
        if (sessionId == null)
            return;
        new AiChatDbService(dbManager).deleteSession(sessionId);
    }

    public void clearChatForSuite(String suiteKey) {
        if (suiteKey == null)
            return;
        try (Connection conn = dbManager.getConnection();
                PreparedStatement ps1 = conn.prepareStatement("DELETE FROM ai_chat_messages WHERE suite_key = ?");
                PreparedStatement ps2 = conn.prepareStatement("DELETE FROM ai_chat_sessions WHERE suite_key = ?")) {
            ps1.setString(1, suiteKey);
            ps1.executeUpdate();
            ps2.setString(1, suiteKey);
            ps2.executeUpdate();
        } catch (SQLException ignored) {
        }
    }
}
