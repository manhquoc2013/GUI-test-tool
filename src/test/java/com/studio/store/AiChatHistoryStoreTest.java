package com.studio.store;

import com.studio.model.AiChatMessage;
import com.studio.model.AiChatSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AiChatHistoryStoreTest {

    @Test
    public void testSaveAndLoadSession(@TempDir Path tempDir) throws IOException {
        AiChatHistoryStore store = new AiChatHistoryStore();

        AiChatSession session = new AiChatSession("LoginSuite");
        AiChatMessage msg1 = new AiChatMessage("user", "Viết test đăng nhập");
        msg1.setEstimatedTokens(12);
        msg1.setAttachedFiles(List.of("spec.txt"));

        AiChatMessage msg2 = new AiChatMessage("assistant", "Đây là kịch bản");
        msg2.setEstimatedTokens(25);

        session.addMessage(msg1);
        session.addMessage(msg2);

        store.saveSession(tempDir, session);

        AiChatSession loaded = store.loadSession(tempDir);
        assertNotNull(loaded);
        assertEquals(2, loaded.getMessages().size());
        assertEquals("user", loaded.getMessages().get(0).getRole());
        assertEquals("Viết test đăng nhập", loaded.getMessages().get(0).getContent());
        assertEquals(1, loaded.getMessages().get(0).getAttachedFiles().size());
        assertEquals(37, loaded.calculateTotalTokens());

        // Test clear
        store.clearSession(tempDir);
        AiChatSession emptySession = store.loadSession(tempDir);
        assertEquals(0, emptySession.getMessages().size());
    }

    @Test
    public void testSuiteSnapshotAndRollback(@TempDir Path suiteDir) throws IOException {
        AiChatHistoryStore store = new AiChatHistoryStore();

        // 1. Setup initial suite files
        Path suiteJson = suiteDir.resolve("suite.json");
        Files.writeString(suiteJson, "{\"name\":\"InitialSuite\",\"cases\":[{\"name\":\"CaseA\"}]}", StandardCharsets.UTF_8);

        Path caseA = suiteDir.resolve("CaseA.java");
        Files.writeString(caseA, "// Case A original code", StandardCharsets.UTF_8);

        // 2. Capture snapshot
        String snapshot = store.createSuiteSnapshot(suiteDir);
        assertNotNull(snapshot);
        assertTrue(snapshot.contains("InitialSuite"));
        assertTrue(snapshot.contains("CaseA.java"));

        // 3. Mutate suite (simulate applying AI test cases: modify CaseA, add CaseB, update suite.json)
        Files.writeString(suiteJson, "{\"name\":\"InitialSuite\",\"cases\":[{\"name\":\"CaseA\"},{\"name\":\"CaseB\"}]}", StandardCharsets.UTF_8);
        Files.writeString(caseA, "// Case A modified by AI", StandardCharsets.UTF_8);

        Path caseB = suiteDir.resolve("CaseB.java");
        Files.writeString(caseB, "// Case B newly created by AI", StandardCharsets.UTF_8);

        assertTrue(Files.exists(caseB));
        assertEquals("// Case A modified by AI", Files.readString(caseA));

        // 4. Perform rollback
        boolean rollbackOk = store.rollbackSuiteSnapshot(suiteDir, snapshot);
        assertTrue(rollbackOk);

        // 5. Verify rollback results
        assertTrue(Files.readString(suiteJson).contains("CaseA"));
        assertFalse(Files.readString(suiteJson).contains("CaseB"));
        assertEquals("// Case A original code", Files.readString(caseA));
        assertFalse(Files.exists(caseB), "Newly created CaseB.java must be deleted on rollback");
    }

    @Test
    public void testMultiSessionManagement(@TempDir Path suiteDir) throws IOException {
        AiChatHistoryStore store = new AiChatHistoryStore();

        // 1. Create session 1
        AiChatSession s1 = store.createNewSession(suiteDir, "Session 1 - Auth Flow");
        s1.addMessage(new AiChatMessage("user", "Hello 1"));
        store.saveSession(suiteDir, s1);

        // 2. Create session 2
        AiChatSession s2 = store.createNewSession(suiteDir, "Session 2 - Dashboard");
        s2.addMessage(new AiChatMessage("user", "Hello 2"));
        store.saveSession(suiteDir, s2);

        // 3. List sessions
        List<com.studio.model.AiChatSessionMeta> list = store.listSessions(suiteDir);
        assertEquals(2, list.size());

        // 4. Rename session 1
        store.renameSession(suiteDir, s1.getId(), "Session 1 - Renamed");
        AiChatSession loadedS1 = store.loadSession(suiteDir, s1.getId());
        assertEquals("Session 1 - Renamed", loadedS1.getTitle());

        // 5. Delete session 2
        store.deleteSession(suiteDir, s2.getId());
        List<com.studio.model.AiChatSessionMeta> listAfterDelete = store.listSessions(suiteDir);
        assertEquals(1, listAfterDelete.size());
        assertEquals(s1.getId(), listAfterDelete.get(0).getId());

        // 6. Delete last session and verify empty directory cleanup
        store.deleteSession(suiteDir, s1.getId());
        assertFalse(Files.exists(store.resolveSessionsDir(suiteDir)),
                "Empty .ai_chats directory must be cleaned up when all sessions are deleted");
    }

    @Test
    public void testLegacyMigration(@TempDir Path suiteDir) throws IOException {
        AiChatHistoryStore store = new AiChatHistoryStore();

        // Write legacy .ai_chat.json directly
        Path legacyFile = suiteDir.resolve(".ai_chat.json");
        String legacyJson = """
            {
              "suiteKey": "OldSuite",
              "summary": "Old summary",
              "messages": [
                {
                  "id": "msg-old-1",
                  "role": "user",
                  "content": "Old message from v2.3.0",
                  "estimatedTokens": 10
                }
              ]
            }
            """;
        Files.writeString(legacyFile, legacyJson, StandardCharsets.UTF_8);

        // Listing sessions should trigger auto-migration
        List<com.studio.model.AiChatSessionMeta> sessions = store.listSessions(suiteDir);
        assertFalse(sessions.isEmpty());

        // Load the migrated session
        AiChatSession migrated = store.loadSession(suiteDir, sessions.get(0).getId());
        assertNotNull(migrated);
        assertEquals(1, migrated.getMessages().size());
        assertEquals("Old message from v2.3.0", migrated.getMessages().get(0).getContent());
    }
}

