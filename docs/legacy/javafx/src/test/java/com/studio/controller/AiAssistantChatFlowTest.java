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
    public void testUndoScopeIsThePromptAndEveryAssistantTurnAfterIt() {
        AiChatSession session = new AiChatSession("suite-1");

        AiChatMessage user1 = new AiChatMessage("user", "Yêu cầu 1");
        AiChatMessage ai1 = new AiChatMessage("assistant", "Trả lời 1");
        AiChatMessage user2 = new AiChatMessage("user", "Yêu cầu 2");
        AiChatMessage ai2 = new AiChatMessage("assistant", "Trả lời 2");
        AiChatMessage ai2b = new AiChatMessage("assistant", "Trả lời bổ sung 2");
        AiChatMessage user3 = new AiChatMessage("user", "Yêu cầu 3");

        session.addMessage(user1);
        session.addMessage(ai1);
        session.addMessage(user2);
        session.addMessage(ai2);
        session.addMessage(ai2b);
        session.addMessage(user3);

        // Undoing "Yêu cầu 2" takes the prompt plus both assistant turns it produced, and stops at the
        // next user prompt.
        List<AiChatMessage> scope = AiAssistantPageController.undoScopeOf(session.getMessages(), user2);
        assertEquals(List.of(user2, ai2, ai2b), scope);

        // Earlier history is outside every later scope.
        List<AiChatMessage> scopeOfLast = AiAssistantPageController.undoScopeOf(session.getMessages(), user3);
        assertEquals(List.of(user3), scopeOfLast);

        // An assistant turn has no undo of its own.
        assertTrue(AiAssistantPageController.undoScopeOf(session.getMessages(), ai1).isEmpty());
    }

    @Test
    public void testUndoingUserMessageKeepsEarlierHistoryAndDropsEveryLaterAssistantTurn() {
        AiChatSession session = new AiChatSession("suite-1");
        AiChatMessage user1 = new AiChatMessage("user", "Yêu cầu 1");
        AiChatMessage ai1 = new AiChatMessage("assistant", "Trả lời 1");
        AiChatMessage user2 = new AiChatMessage("user", "Yêu cầu 2");
        AiChatMessage ai2 = new AiChatMessage("assistant", "Trả lời 2");
        AiChatMessage ai2b = new AiChatMessage("assistant", "Trả lời bổ sung 2");
        session.addMessage(user1);
        session.addMessage(ai1);
        session.addMessage(user2);
        session.addMessage(ai2);
        session.addMessage(ai2b);

        List<AiChatMessage> msgs = session.getMessages();
        msgs.removeAll(AiAssistantPageController.undoScopeOf(msgs, user2));

        assertEquals(2, session.getMessages().size());
        assertEquals("Yêu cầu 1", session.getMessages().get(0).getContent());
        assertEquals("Trả lời 1", session.getMessages().get(1).getContent());
    }

    @Test
    public void testUndoOfUserMessageResolvesLastAppliedReplyInItsScope() {
        AiChatMessage user1 = new AiChatMessage("user", "Sinh kịch bản");
        AiChatMessage ai1 = new AiChatMessage("assistant", "Tóm tắt 1");
        AiChatMessage ai1b = new AiChatMessage("assistant", "Đã áp dụng lần 2");
        AiChatMessage user2 = new AiChatMessage("user", "Yêu cầu tiếp theo");
        AiChatMessage ai2 = new AiChatMessage("assistant", "Đã áp dụng ở lượt sau");

        ai1.setAppliedToSuite(true);
        ai1.setSuiteSnapshotJson("{\"pre\":1}");
        ai1b.setAppliedToSuite(true);
        // The later apply of the same scope captured the state before that apply, which already contains
        // the first apply — rolling it back undoes the whole scope.
        ai1b.setSuiteSnapshotJson("{\"pre\":2}");
        ai2.setAppliedToSuite(true);
        ai2.setSuiteSnapshotJson("{\"pre\":3}");

        List<AiChatMessage> msgs = List.of(user1, ai1, ai1b, user2, ai2);

        assertSame(ai1b, AiAssistantPageController.resolveAppliedSnapshotMessage(msgs, user1),
                "Undo must roll back the last applied reply of the undone prompt's scope");
        assertSame(ai2, AiAssistantPageController.resolveAppliedSnapshotMessage(msgs, user2),
                "A later prompt's apply must not leak into an earlier prompt's undo");
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

    /**
     * The user-facing contracts of undo are proven together: only a user prompt undoes, it removes every
     * assistant turn after it while keeping earlier history, and the suite files those turns wrote are
     * rolled back to the snapshot captured before the scope's first apply.
     */
    @Test
    public void testUndoRejectsAssistantTurnAndRestoresHistoryAndSuite(@TempDir Path suiteDir) throws IOException {
        AiChatHistoryStore store = new AiChatHistoryStore();

        // 1. Earlier conversation, before the prompt that will be undone
        AiChatMessage earlierUser = new AiChatMessage("user", "Yêu cầu trước");
        AiChatMessage earlierAi = new AiChatMessage("assistant", "Trả lời trước");

        // 2. The undone prompt, its two assistant turns, and a later prompt that must survive
        AiChatMessage undoUser = new AiChatMessage("user", "Sinh kịch bản thanh toán");
        AiChatMessage appliedAi = new AiChatMessage("assistant", "Đã sinh NewAiCase");
        AiChatMessage followUpAi = new AiChatMessage("assistant", "Ghi chú bổ sung");
        AiChatMessage laterUser = new AiChatMessage("user", "Yêu cầu sau");
        AiChatMessage laterAi = new AiChatMessage("assistant", "Trả lời sau");

        AiChatSession session = new AiChatSession("suite-1");
        for (AiChatMessage m : List.of(earlierUser, earlierAi, undoUser, appliedAi, followUpAi, laterUser, laterAi)) {
            session.addMessage(m);
        }

        // 3. Suite on disk: snapshot captured before the apply, then the apply wrote a new case file
        Path suiteJson = suiteDir.resolve("suite.json");
        Files.writeString(suiteJson, "{\"cases\":[{\"name\":\"ExistingCase\"}]}", StandardCharsets.UTF_8);
        Path existingCase = suiteDir.resolve("ExistingCase.java");
        Files.writeString(existingCase, "// Existing case", StandardCharsets.UTF_8);

        String snapshot = store.createSuiteSnapshot(suiteDir);
        Files.writeString(suiteJson, "{\"cases\":[{\"name\":\"ExistingCase\"},{\"name\":\"NewAiCase\"}]}",
                StandardCharsets.UTF_8);
        Path newAiCase = suiteDir.resolve("NewAiCase.java");
        Files.writeString(newAiCase, "// New Playwright code", StandardCharsets.UTF_8);
        appliedAi.setAppliedToSuite(true);
        appliedAi.setSuiteSnapshotJson(snapshot);
        appliedAi.setSuiteSnapshotDir(suiteDir.toString());

        // 4. An assistant turn cannot be undone on its own: no scope, no applied reply resolved
        assertTrue(AiAssistantPageController.undoScopeOf(session.getMessages(), appliedAi).isEmpty(),
                "An assistant message must not be an undoable target");
        assertEquals(List.of(undoUser, appliedAi, followUpAi),
                AiAssistantPageController.undoScopeOf(session.getMessages(), undoUser));

        AiChatMessage appliedMsg = AiAssistantPageController.resolveAppliedSnapshotMessage(session.getMessages(), undoUser);
        assertSame(appliedAi, appliedMsg);
        Path rollbackDir = AiAssistantPageController.resolveRollbackSuiteDir(appliedMsg, null);
        assertEquals(suiteDir, rollbackDir);

        // 5. Undo: roll back the files, then drop the scope from the transcript
        assertTrue(store.rollbackSuiteSnapshot(rollbackDir, appliedMsg.getSuiteSnapshotJson()));
        session.getMessages().removeAll(AiAssistantPageController.undoScopeOf(session.getMessages(), undoUser));

        // 6. Suite is back to the state before the apply; earlier and later prompts are untouched
        assertFalse(Files.exists(newAiCase), "NewAiCase.java must be deleted on undo");
        assertEquals("// Existing case", Files.readString(existingCase));
        assertFalse(Files.readString(suiteJson).contains("NewAiCase"));
        assertEquals(List.of("Yêu cầu trước", "Trả lời trước", "Yêu cầu sau", "Trả lời sau"),
                session.getMessages().stream().map(AiChatMessage::getContent).toList());
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
