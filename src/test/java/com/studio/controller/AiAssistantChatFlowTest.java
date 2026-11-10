package com.studio.controller;

import com.studio.model.AiChatMessage;
import com.studio.model.AiChatSession;
import com.studio.model.AiGeneratedCase;
import com.studio.model.Assertion;
import com.studio.store.AiChatHistoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AiAssistantChatFlowTest {

    @Test
    public void testUndoMessagePairRemoval() {
        AiChatSession session = new AiChatSession("suite-1");

        AiChatMessage user1 = new AiChatMessage("user", "Yêu cầu 1");
        AiChatMessage ai1 = new AiChatMessage("assistant", "Trả lời 1");

        AiChatMessage user2 = new AiChatMessage("user", "Yêu cầu 2");
        AiChatMessage ai2 = new AiChatMessage("assistant", "Trả lời 2");

        session.addMessage(user1);
        session.addMessage(ai1);
        session.addMessage(user2);
        session.addMessage(ai2);

        assertEquals(4, session.getMessages().size());

        // Simulate undo on assistant 2: both user2 and ai2 should be removed
        List<AiChatMessage> msgs = session.getMessages();
        int idx = msgs.indexOf(ai2);
        assertEquals(3, idx);

        msgs.remove(idx); // removes ai2
        if (idx - 1 >= 0 && "user".equalsIgnoreCase(msgs.get(idx - 1).getRole())) {
            msgs.remove(idx - 1); // removes user2
        }

        assertEquals(2, session.getMessages().size());
        assertEquals("Yêu cầu 1", session.getMessages().get(0).getContent());
        assertEquals("Trả lời 1", session.getMessages().get(1).getContent());
    }

    @Test
    public void testGeneratedCasesSnapshotPreservedInMessage() {
        AiChatMessage msg = new AiChatMessage("assistant", "Đã sinh 2 test cases");

        AiGeneratedCase c1 = new AiGeneratedCase();
        c1.setName("LoginValid");
        c1.setScriptCode("page.navigate(\"https://example.com\");");
        Assertion a1 = new Assertion();
        a1.setType("PAGE_TITLE_CONTAINS");
        a1.setValue("Dashboard");
        c1.setAssertions(List.of(a1));

        msg.setGeneratedCasesSnapshot(List.of(c1));

        assertEquals(1, msg.getGeneratedCasesSnapshot().size());
        assertEquals("LoginValid", msg.getGeneratedCasesSnapshot().get(0).getName());
        assertEquals("PAGE_TITLE_CONTAINS", msg.getGeneratedCasesSnapshot().get(0).getAssertions().get(0).getType());
    }

    @Test
    public void testUndoWithSuiteRollback(@TempDir Path suiteDir) throws IOException {
        AiChatHistoryStore store = new AiChatHistoryStore();

        // 1. Initial Suite on disk
        Path suiteJson = suiteDir.resolve("suite.json");
        Files.writeString(suiteJson, "{\"cases\":[{\"name\":\"ExistingCase\"}]}", StandardCharsets.UTF_8);

        Path existingCase = suiteDir.resolve("ExistingCase.java");
        Files.writeString(existingCase, "// Existing case", StandardCharsets.UTF_8);

        // 2. Snapshot captured before applying
        String snapshot = store.createSuiteSnapshot(suiteDir);

        // 3. AI generated cases applied to suite
        Files.writeString(suiteJson, "{\"cases\":[{\"name\":\"ExistingCase\"},{\"name\":\"NewAiCase\"}]}",
                StandardCharsets.UTF_8);
        Path newAiCase = suiteDir.resolve("NewAiCase.java");
        Files.writeString(newAiCase, "// New Playwright code", StandardCharsets.UTF_8);

        AiChatMessage assistantMsg = new AiChatMessage("assistant", "Đã sinh NewAiCase");
        assistantMsg.setSuiteSnapshotJson(snapshot);
        assistantMsg.setSuiteSnapshotDir(suiteDir.toString());
        assistantMsg.setAppliedToSuite(true);

        assertTrue(assistantMsg.isAppliedToSuite());
        assertTrue(Files.exists(newAiCase));

        // 4. User triggers Undo on assistantMsg
        boolean rolledBack = store.rollbackSuiteSnapshot(suiteDir, assistantMsg.getSuiteSnapshotJson());
        assertTrue(rolledBack);

        // 5. Verify suite returned to pristine original state
        assertFalse(Files.exists(newAiCase), "NewAiCase.java must be deleted on undo");
        assertTrue(Files.exists(existingCase));
        assertEquals("// Existing case", Files.readString(existingCase));
        assertFalse(Files.readString(suiteJson).contains("NewAiCase"));
    }

    @Test
    public void testSessionAutoNamingAndSwitching(@TempDir Path suiteDir) throws IOException {
        AiChatHistoryStore store = new AiChatHistoryStore();

        // 1. New session starts with default title
        AiChatSession session = store.createNewSession(suiteDir, "Cuộc trò chuyện mới");
        assertEquals("Cuộc trò chuyện mới", session.getTitle());

        // 2. User prompt auto-names session
        String firstPrompt = "Viết kịch bản kiểm thử API thanh toán Stripe";
        String autoName = firstPrompt.replace("\n", " ").trim();
        if (autoName.length() > 28) {
            autoName = autoName.substring(0, 28) + "...";
        }
        session.setTitle(autoName);
        session.addMessage(new AiChatMessage("user", firstPrompt));
        store.saveSession(suiteDir, session);

        // 3. Verify title and messages persist
        AiChatSession loaded = store.loadSession(suiteDir, session.getId());
        assertEquals(autoName, loaded.getTitle());
        assertEquals(1, loaded.getMessages().size());

        // 4. Create another session and switch
        AiChatSession session2 = store.createNewSession(suiteDir, "Kiểm thử giỏ hàng");
        session2.addMessage(new AiChatMessage("user", "Kiểm thử thêm sản phẩm vào giỏ"));
        store.saveSession(suiteDir, session2);

        List<com.studio.model.AiChatSessionMeta> list = store.listSessions(suiteDir);
        assertEquals(2, list.size());

        AiChatSession loaded2 = store.loadSession(suiteDir, session2.getId());
        assertEquals("Kiểm thử giỏ hàng", loaded2.getTitle());
        assertEquals(1, loaded2.getMessages().size());
    }

    @Test
    public void testExtractPromptFromUserContent() {
        // Case 1: with skill prefix and attachment suffix
        String content1 = "[Fuzzing Bảo Mật]\nKiểm thử form đăng nhập SQL Injection\n\nĐã đính kèm: spec.docx report.pdf ";
        assertEquals("Kiểm thử form đăng nhập SQL Injection", AiAssistantPageController.extractPromptFromUserContent(content1));

        // Case 2: only attachment suffix
        String content2 = "Kiểm thử responsive mobile\n\nĐã đính kèm: screen.png ";
        assertEquals("Kiểm thử responsive mobile", AiAssistantPageController.extractPromptFromUserContent(content2));

        // Case 3: only skill prefix
        String content3 = "[Kiểm Thử Toàn Diện]\nKiểm tra luồng checkout hoàn chỉnh";
        assertEquals("Kiểm tra luồng checkout hoàn chỉnh", AiAssistantPageController.extractPromptFromUserContent(content3));

        // Case 4: plain prompt
        String content4 = "Tạo 5 test cases cho trang giỏ hàng";
        assertEquals("Tạo 5 test cases cho trang giỏ hàng", AiAssistantPageController.extractPromptFromUserContent(content4));

        // Case 5: empty or null
        assertEquals("", AiAssistantPageController.extractPromptFromUserContent(""));
        assertEquals("", AiAssistantPageController.extractPromptFromUserContent(null));
    }

    @Test
    public void testAttachedDocSnapshotModel() {
        AiChatMessage msg = new AiChatMessage("user", "prompt");
        msg.setRawPrompt("raw prompt");
        msg.setSkill("AUTO");
        msg.setAttachedFilePaths(List.of("/path/to/spec.docx"));

        AiChatMessage.AttachedDocSnapshot snap = new AiChatMessage.AttachedDocSnapshot(
                "spec.docx",
                "/path/to/spec.docx",
                false,
                "# Converted Markdown",
                "text/markdown",
                2048L,
                "/data/converted_docs/spec.md"
        );
        msg.setAttachedDocsSnapshot(List.of(snap));

        assertEquals("raw prompt", msg.getRawPrompt());
        assertEquals("AUTO", msg.getSkill());
        assertEquals(1, msg.getAttachedFilePaths().size());
        assertEquals("/path/to/spec.docx", msg.getAttachedFilePaths().get(0));
        assertEquals(1, msg.getAttachedDocsSnapshot().size());
        assertEquals("spec.docx", msg.getAttachedDocsSnapshot().get(0).getFileName());
        assertEquals("# Converted Markdown", msg.getAttachedDocsSnapshot().get(0).getContent());
        assertEquals(2048L, msg.getAttachedDocsSnapshot().get(0).getSizeBytes());
    }
}
