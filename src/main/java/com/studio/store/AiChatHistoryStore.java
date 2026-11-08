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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Storage manager for persistent AI chat sessions, suite file snapshots, and
 * rollback operations.
 */
public class AiChatHistoryStore {

    public static final String CHAT_HISTORY_FILE = ".ai_chat.json";
    public static final String CHAT_SESSIONS_DIR = ".ai_chats";
    private final ObjectMapper mapper;

    public AiChatHistoryStore() {
        this(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));
    }

    public AiChatHistoryStore(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Resolve the storage file for legacy chat history.
     */
    public Path resolveHistoryPath(Path suiteDir) {
        if (suiteDir != null && Files.exists(suiteDir) && Files.isDirectory(suiteDir)) {
            return suiteDir.resolve(CHAT_HISTORY_FILE);
        }
        return StudioPaths.getDataDir().resolve("ai_chat_default.json");
    }

    /**
     * Resolve directory containing all chat session JSON files for a suite or global workspace.
     */
    public Path resolveSessionsDir(Path suiteDir) {
        if (suiteDir != null && Files.exists(suiteDir) && Files.isDirectory(suiteDir)) {
            return suiteDir.resolve(CHAT_SESSIONS_DIR);
        }
        return StudioPaths.getDataDir().resolve("ai_chats").resolve("global");
    }

    /**
     * Migrate legacy .ai_chat.json if found.
     */
    public synchronized void migrateLegacyIfPresent(Path suiteDir) {
        Path legacyFile = resolveHistoryPath(suiteDir);
        if (legacyFile == null || !Files.exists(legacyFile)) {
            return;
        }

        try {
            AiChatSession legacy = mapper.readValue(legacyFile.toFile(), AiChatSession.class);
            if (legacy != null && legacy.getMessages() != null && !legacy.getMessages().isEmpty()) {
                if (legacy.getTitle() == null || legacy.getTitle().isBlank()) {
                    legacy.setTitle("Cuộc trò chuyện trước");
                }
                Path dir = resolveSessionsDir(suiteDir);
                Files.createDirectories(dir);
                Path sessionFile = dir.resolve(legacy.getId() + ".json");
                mapper.writeValue(sessionFile.toFile(), legacy);

                // Index in H2
                com.studio.db.LocalDataIndexService indexService = new com.studio.db.LocalDataIndexService();
                indexService.indexChatSession(legacy.toMeta());
                String suiteKey = suiteDir != null ? suiteDir.getFileName().toString() : "global";
                for (com.studio.model.AiChatMessage msg : legacy.getMessages()) {
                    if (msg.getSessionId() == null || msg.getSessionId().isBlank()) {
                        msg.setSessionId(legacy.getId());
                    }
                    indexService.indexChatMessage(msg, suiteKey);
                }
            }
            Files.deleteIfExists(legacyFile);
        } catch (Exception ignored) {}
    }

    /**
     * List all chat sessions for a suite or global workspace.
     */
    public List<com.studio.model.AiChatSessionMeta> listSessions(Path suiteDir) {
        migrateLegacyIfPresent(suiteDir);
        Path dir = resolveSessionsDir(suiteDir);
        List<com.studio.model.AiChatSessionMeta> list = new ArrayList<>();

        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                for (Path p : stream) {
                    try {
                        AiChatSession s = mapper.readValue(p.toFile(), AiChatSession.class);
                        if (s != null) {
                            list.add(s.toMeta());
                        }
                    } catch (Exception ignored) {}
                }
            } catch (IOException ignored) {}
        }

        if (list.isEmpty()) {
            AiChatSession defaultSession = createNewSession(suiteDir, "Cuộc trò chuyện mới");
            list.add(defaultSession.toMeta());
        }

        list.sort((a, b) -> {
            if (a.isPinned() != b.isPinned()) {
                return a.isPinned() ? -1 : 1;
            }
            return Long.compare(b.getUpdatedAt(), a.getUpdatedAt());
        });

        return list;
    }

    /**
     * Create a new chat session for a suite.
     */
    public AiChatSession createNewSession(Path suiteDir, String title) {
        migrateLegacyIfPresent(suiteDir);
        String key = suiteDir != null ? suiteDir.getFileName().toString() : "global";
        AiChatSession session = new AiChatSession(key);
        if (title != null && !title.isBlank()) {
            session.setTitle(title.trim());
        }
        try {
            saveSession(suiteDir, session);
        } catch (IOException ignored) {}
        return session;
    }

    /**
     * Load a specific chat session by id.
     */
    public AiChatSession loadSession(Path suiteDir, String sessionId) {
        migrateLegacyIfPresent(suiteDir);
        Path dir = resolveSessionsDir(suiteDir);

        if (sessionId != null && !sessionId.isBlank() && Files.exists(dir)) {
            Path path = dir.resolve(sessionId + ".json");
            if (Files.exists(path)) {
                try {
                    return mapper.readValue(path.toFile(), AiChatSession.class);
                } catch (Exception ignored) {}
            }
        }

        // Fallback: pick the latest session from listSessions
        List<com.studio.model.AiChatSessionMeta> sessions = listSessions(suiteDir);
        if (!sessions.isEmpty()) {
            Path path = dir.resolve(sessions.get(0).getId() + ".json");
            if (Files.exists(path)) {
                try {
                    return mapper.readValue(path.toFile(), AiChatSession.class);
                } catch (Exception ignored) {}
            }
        }

        String key = suiteDir != null ? suiteDir.getFileName().toString() : "global";
        return new AiChatSession(key);
    }

    /**
     * Load default/active chat session from disk.
     */
    public AiChatSession loadSession(Path suiteDir) {
        return loadSession(suiteDir, null);
    }

    /**
     * Save chat session to disk and mirror into H2 database.
     */
    public void saveSession(Path suiteDir, AiChatSession session) throws IOException {
        if (session == null) return;
        Path dir = resolveSessionsDir(suiteDir);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        session.setLastUpdated(System.currentTimeMillis());
        Path path = dir.resolve(session.getId() + ".json");
        mapper.writeValue(path.toFile(), session);

        // Write-through to local database index
        com.studio.db.LocalDataIndexService indexService = new com.studio.db.LocalDataIndexService();
        indexService.indexChatSession(session.toMeta());

        if (session.getMessages() != null) {
            String suiteKey = suiteDir != null ? suiteDir.getFileName().toString() : "global";
            for (com.studio.model.AiChatMessage msg : session.getMessages()) {
                if (msg.getSessionId() == null || msg.getSessionId().isBlank()) {
                    msg.setSessionId(session.getId());
                }
                indexService.indexChatMessage(msg, suiteKey);
            }
        }
    }

    /**
     * Rename an existing session.
     */
    public void renameSession(Path suiteDir, String sessionId, String newTitle) throws IOException {
        if (sessionId == null || newTitle == null || newTitle.isBlank()) return;
        AiChatSession session = loadSession(suiteDir, sessionId);
        if (session != null) {
            session.setTitle(newTitle.trim());
            saveSession(suiteDir, session);
        }
    }

    /**
     * Pin/unpin a session.
     */
    public void setSessionPinned(Path suiteDir, String sessionId, boolean pinned) throws IOException {
        if (sessionId == null) return;
        AiChatSession session = loadSession(suiteDir, sessionId);
        if (session != null) {
            session.setPinned(pinned);
            saveSession(suiteDir, session);
        }
    }

    /**
     * Delete a session and its storage file.
     */
    public void deleteSession(Path suiteDir, String sessionId) throws IOException {
        if (sessionId == null) return;
        Path dir = resolveSessionsDir(suiteDir);
        Path path = dir.resolve(sessionId + ".json");
        Files.deleteIfExists(path);
        new com.studio.db.LocalDataIndexService().clearChatForSession(sessionId);
    }

    /**
     * Clear chat session for a suite and purge from H2 database.
     */
    public void clearSession(Path suiteDir) throws IOException {
        AiChatSession active = loadSession(suiteDir);
        if (active != null) {
            active.getMessages().clear();
            saveSession(suiteDir, active);
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
