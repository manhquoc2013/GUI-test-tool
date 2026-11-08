package com.studio.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.studio.model.AiChatSession;
import com.studio.util.StudioPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Storage manager for persistent AI chat sessions, suite file snapshots, and
 * rollback operations.
 */
public class AiChatHistoryStore {

    public static final String CHAT_HISTORY_FILE = ".ai_chat.json";
    private final ObjectMapper mapper;

    public AiChatHistoryStore() {
        this(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));
    }

    public AiChatHistoryStore(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Resolve the storage file for chat history.
     */
    public Path resolveHistoryPath(Path suiteDir) {
        if (suiteDir != null && Files.exists(suiteDir) && Files.isDirectory(suiteDir)) {
            return suiteDir.resolve(CHAT_HISTORY_FILE);
        }
        return StudioPaths.getDataDir().resolve("ai_chat_default.json");
    }

    /**
     * Load chat session from disk.
     */
    public AiChatSession loadSession(Path suiteDir) {
        Path path = resolveHistoryPath(suiteDir);
        if (Files.exists(path)) {
            try {
                return mapper.readValue(path.toFile(), AiChatSession.class);
            } catch (Exception ignored) {
                // Return fresh session on parse error
            }
        }
        String key = suiteDir != null ? suiteDir.getFileName().toString() : "global";
        return new AiChatSession(key);
    }

    /**
     * Save chat session to disk and mirror into H2 database.
     */
    public void saveSession(Path suiteDir, AiChatSession session) throws IOException {
        Path path = resolveHistoryPath(suiteDir);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        mapper.writeValue(path.toFile(), session);

        // Write-through to local database index
        if (session != null && session.getMessages() != null) {
            String suiteKey = suiteDir != null ? suiteDir.getFileName().toString() : "global";
            com.studio.db.LocalDataIndexService indexService = new com.studio.db.LocalDataIndexService();
            for (com.studio.model.AiChatMessage msg : session.getMessages()) {
                indexService.indexChatMessage(msg, suiteKey);
            }
        }
    }

    /**
     * Clear chat session for a suite and purge from H2 database.
     */
    public void clearSession(Path suiteDir) throws IOException {
        Path path = resolveHistoryPath(suiteDir);
        if (Files.exists(path)) {
            Files.delete(path);
        }
        String suiteKey = suiteDir != null ? suiteDir.getFileName().toString() : "global";
        new com.studio.db.LocalDataIndexService().clearChatForSuite(suiteKey);
    }

    // ── Suite Snapshot & Rollback Engine ──

    public static class SuiteSnapshot {
        private String suiteJson;
        private Map<String, String> javaFiles = new HashMap<>();

        public SuiteSnapshot() {
        }

        public SuiteSnapshot(String suiteJson, Map<String, String> javaFiles) {
            this.suiteJson = suiteJson;
            this.javaFiles = javaFiles != null ? javaFiles : new HashMap<>();
        }

        public String getSuiteJson() {
            return suiteJson;
        }

        public void setSuiteJson(String suiteJson) {
            this.suiteJson = suiteJson;
        }

        public Map<String, String> getJavaFiles() {
            return javaFiles;
        }

        public void setJavaFiles(Map<String, String> javaFiles) {
            this.javaFiles = javaFiles != null ? javaFiles : new HashMap<>();
        }
    }

    /**
     * Capture snapshot of suite.json and all existing .java test case files.
     */
    public String createSuiteSnapshot(Path suiteDir) throws IOException {
        if (suiteDir == null || !Files.exists(suiteDir) || !Files.isDirectory(suiteDir)) {
            return null;
        }

        String suiteJson = "";
        Path suiteJsonPath = suiteDir.resolve("suite.json");
        if (Files.exists(suiteJsonPath)) {
            suiteJson = Files.readString(suiteJsonPath, StandardCharsets.UTF_8);
        }

        Map<String, String> javaFiles = new HashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(suiteDir, "*.java")) {
            for (Path p : stream) {
                String content = Files.readString(p, StandardCharsets.UTF_8);
                javaFiles.put(p.getFileName().toString(), content);
            }
        }

        SuiteSnapshot snapshot = new SuiteSnapshot(suiteJson, javaFiles);
        return mapper.writeValueAsString(snapshot);
    }

    /**
     * Revert / Rollback suite files to the state captured in the snapshot.
     */
    public boolean rollbackSuiteSnapshot(Path suiteDir, String snapshotJson) throws IOException {
        if (suiteDir == null || !Files.exists(suiteDir) || snapshotJson == null || snapshotJson.isBlank()) {
            return false;
        }

        SuiteSnapshot snapshot = mapper.readValue(snapshotJson, SuiteSnapshot.class);

        // 1. Restore suite.json
        Path suiteJsonPath = suiteDir.resolve("suite.json");
        if (snapshot.getSuiteJson() != null && !snapshot.getSuiteJson().isBlank()) {
            Files.writeString(suiteJsonPath, snapshot.getSuiteJson(), StandardCharsets.UTF_8);
        }

        // 2. Delete any .java files that did not exist in the snapshot
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(suiteDir, "*.java")) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (!snapshot.getJavaFiles().containsKey(name)) {
                    Files.deleteIfExists(p);
                }
            }
        }

        // 3. Restore all .java files from snapshot
        for (Map.Entry<String, String> entry : snapshot.getJavaFiles().entrySet()) {
            Path targetFile = suiteDir.resolve(entry.getKey());
            Files.writeString(targetFile, entry.getValue(), StandardCharsets.UTF_8);
        }

        return true;
    }
}
