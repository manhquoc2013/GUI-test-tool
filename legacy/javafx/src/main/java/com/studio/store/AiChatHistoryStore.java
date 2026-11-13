package com.studio.store;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final ObjectMapper DEFAULT_MAPPER = ProjectStore.DEFAULT_MAPPER;
    private final ObjectMapper mapper;
    private final com.studio.db.LocalDataIndexService indexService;
    /** The project tree is scanned for suite-scoped chats at most once per store instance. */
    private boolean suiteSessionsMigrated;

    public AiChatHistoryStore() {
        this(DEFAULT_MAPPER);
    }

    public AiChatHistoryStore(ObjectMapper mapper) {
        this.mapper = mapper != null ? mapper : DEFAULT_MAPPER;
        this.indexService = new com.studio.db.LocalDataIndexService();
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
        return resolveGlobalSessionsDir();
    }

    /** The single, workspace-wide location of chat history (independent of the save target). */
    public Path resolveGlobalSessionsDir() {
        return StudioPaths.getDataDir().resolve("ai_chats").resolve("global");
    }

    /**
     * History list for the whole workspace. Choosing a save target must never swap the transcript out
     * from under a running worker, so every conversation lives in one place and the target is only
     * metadata captured on the session.
     */
    public synchronized List<com.studio.model.AiChatSessionMeta> listAllSessions() {
        migrateSuiteSessionsIntoGlobalHistory();
        return listSessions(null);
    }

    /** Load one global session by id; returns null instead of falling back to "latest". */
    public AiChatSession loadSessionById(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        Path path = resolveGlobalSessionsDir().resolve(sessionId + ".json");
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return mapper.readValue(path.toFile(), AiChatSession.class);
        } catch (IOException e) {
            com.studio.service.AiLoggingService.warn("AI-CHAT",
                    "Không đọc được cuộc trò chuyện " + sessionId + ": " + e.getMessage());
            return null;
        }
    }

    public void saveSession(AiChatSession session) throws IOException {
        saveSession(null, session);
    }

    public void renameSession(String sessionId, String newTitle) throws IOException {
        if (sessionId == null || newTitle == null || newTitle.isBlank()) {
            return;
        }
        AiChatSession session = loadSessionById(sessionId);
        if (session != null) {
            session.setTitle(newTitle.trim());
            saveSession(session);
        }
    }

    public void setSessionPinned(String sessionId, boolean pinned) throws IOException {
        AiChatSession session = loadSessionById(sessionId);
        if (session != null) {
            session.setPinned(pinned);
            saveSession(session);
        }
    }

    public void deleteSession(String sessionId) throws IOException {
        deleteSessionFile(resolveGlobalSessionsDir(), sessionId);
    }

    /**
     * Write JSON object atomically using a temporary file and atomic replace.
     */
    public void writeJsonAtomic(Path targetPath, Object value) throws IOException {
        Path parent = targetPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempFile = Files.createTempFile(parent != null ? parent : Path.of("."), targetPath.getFileName().toString(), ".tmp");
        try {
            mapper.writeValue(tempFile.toFile(), value);
            try {
                Files.move(tempFile, targetPath, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tempFile, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (Exception ignored) {}
        }
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
                Path sessionFile = dir.resolve(legacy.getId() + ".json");
                writeJsonAtomic(sessionFile, legacy);

                // Index in H2
                indexService.indexChatSession(legacy.toMeta());
                String suiteKey = suiteDir != null ? suiteDir.getFileName().toString() : "global";
                for (com.studio.model.AiChatMessage msg : legacy.getMessages()) {
                    if (msg.getSessionId() == null || msg.getSessionId().isBlank()) {
                        msg.setSessionId(legacy.getId());
                    }
                }
                indexService.indexChatMessages(legacy.getMessages(), suiteKey);
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
        writeJsonAtomic(path, session);

        // Write-through to local database index
        indexService.indexChatSession(session.toMeta());

        if (session.getMessages() != null && !session.getMessages().isEmpty()) {
            String suiteKey = suiteDir != null ? suiteDir.getFileName().toString() : "global";
            for (com.studio.model.AiChatMessage msg : session.getMessages()) {
                if (msg.getSessionId() == null || msg.getSessionId().isBlank()) {
                    msg.setSessionId(session.getId());
                }
            }
            indexService.indexChatMessages(session.getMessages(), suiteKey);
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
        deleteSessionFile(resolveSessionsDir(suiteDir), sessionId);
    }

    private void deleteSessionFile(Path dir, String sessionId) throws IOException {
        if (sessionId == null) return;
        Path path = dir.resolve(sessionId + ".json");
        Files.deleteIfExists(path);
        indexService.clearChatForSession(sessionId);

        // Clean up empty directory if no more sessions remain
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                if (!stream.iterator().hasNext()) {
                    Files.deleteIfExists(dir);
                }
            } catch (IOException ignored) {}
        }
    }

    /**
     * Moves conversations that older builds stored under {@code <suite>/.ai_chats} into the single
     * workspace history. The target group/suite is recovered from the path, so a migrated session keeps
     * the target it was started with. Empty sessions are dropped instead of being duplicated as clutter.
     */
    private synchronized void migrateSuiteSessionsIntoGlobalHistory() {
        if (suiteSessionsMigrated) {
            return;
        }
        suiteSessionsMigrated = true;

        Path projectDir = StudioPaths.getProjectDir();
        if (!Files.isDirectory(projectDir)) {
            return;
        }
        Path globalDir = resolveGlobalSessionsDir();
        try (DirectoryStream<Path> groups = Files.newDirectoryStream(projectDir)) {
            for (Path groupDir : groups) {
                if (!Files.isDirectory(groupDir)) {
                    continue;
                }
                String groupName = groupDir.getFileName().toString();
                try (DirectoryStream<Path> suites = Files.newDirectoryStream(groupDir)) {
                    for (Path suiteDir : suites) {
                        if (!Files.isDirectory(suiteDir)) {
                            continue;
                        }
                        Path sessionsDir = suiteDir.resolve(CHAT_SESSIONS_DIR);
                        if (Files.isDirectory(sessionsDir)) {
                            migrateSessionsDir(sessionsDir, globalDir, groupName,
                                    suiteDir.getFileName().toString());
                        }
                    }
                } catch (IOException e) {
                    com.studio.service.AiLoggingService.warn("AI-CHAT",
                            "Không quét được chat của nhóm " + groupName + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            com.studio.service.AiLoggingService.warn("AI-CHAT",
                    "Không quét được lịch sử chat theo suite: " + e.getMessage());
        }
    }

    private void migrateSessionsDir(Path sessionsDir, Path globalDir, String groupName, String suiteName)
            throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sessionsDir, "*.json")) {
            for (Path source : stream) {
                AiChatSession session;
                try {
                    session = mapper.readValue(source.toFile(), AiChatSession.class);
                } catch (IOException e) {
                    com.studio.service.AiLoggingService.warn("AI-CHAT",
                            "Bỏ qua file chat hỏng " + source.getFileName() + ": " + e.getMessage());
                    continue;
                }
                if (session == null) {
                    continue;
                }
                if (session.getMessages().isEmpty()) {
                    // An empty conversation holds no history; keeping it would duplicate placeholder rows.
                    Files.deleteIfExists(source);
                    continue;
                }
                if (session.getTargetGroup() == null || session.getTargetGroup().isBlank()) {
                    session.setTargetGroup(groupName);
                }
                if (session.getTargetSuite() == null || session.getTargetSuite().isBlank()) {
                    session.setTargetSuite(suiteName);
                }
                Path target = globalDir.resolve(session.getId() + ".json");
                if (!Files.exists(target)) {
                    // Write directly instead of saveSession so the original ordering is preserved: an
                    // upgrade must not reshuffle every old conversation to the top of the sidebar.
                    for (com.studio.model.AiChatMessage message : session.getMessages()) {
                        if (message.getSessionId() == null || message.getSessionId().isBlank()) {
                            message.setSessionId(session.getId());
                        }
                    }
                    writeJsonAtomic(target, session);
                    indexService.indexChatSession(session.toMeta());
                    indexService.indexChatMessages(session.getMessages(), "global");
                }
                Files.deleteIfExists(source);
            }
        }
        // Remove the now-empty suite chat directory so the tree is not littered with dead folders.
        try (DirectoryStream<Path> remaining = Files.newDirectoryStream(sessionsDir)) {
            if (!remaining.iterator().hasNext()) {
                Files.deleteIfExists(sessionsDir);
            }
        } catch (IOException ignored) {}
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
        indexService.clearChatForSuite(suiteKey);
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

        // 4. Re-index the restored suite in H2 so removed/restored cases are synchronized
        if (Files.exists(suiteJsonPath)) {
            try {
                com.studio.model.TestSuite restoredSuite = mapper.readValue(suiteJsonPath.toFile(), com.studio.model.TestSuite.class);
                String groupName = suiteDir.getParent() != null && suiteDir.getParent().getFileName() != null
                        ? suiteDir.getParent().getFileName().toString() : "";
                indexService.indexSuite(restoredSuite, suiteDir, groupName);
            } catch (Exception ignored) {
            }
        }

        return true;
    }
}
