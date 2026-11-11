package com.studio.service;

import com.studio.model.AiChatSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the runtime bookkeeping behind the history badges: a conversation running in the
 * background must stay observable, and its finished result must stay flagged until it is opened.
 */
public class AiChatRunRegistryTest {

    @Test
    public void testRunStartsAndExposesLiveSession() {
        AiChatRunRegistry registry = new AiChatRunRegistry();
        AiChatSession session = new AiChatSession("global");

        AiChatRunRegistry.Run run = registry.startRun(session, "gpt-4o — Test Case");

        assertNotNull(run);
        assertTrue(registry.isRunning(session.getId()));
        assertSame(session, registry.getLiveSession(session.getId()));
        assertEquals("gpt-4o — Test Case", run.getStatus());
        assertFalse(registry.isUnread(session.getId()));
    }

    @Test
    public void testStatusUpdatesOnlyReachTheRunningConversation() {
        AiChatRunRegistry registry = new AiChatRunRegistry();
        AiChatSession running = new AiChatSession("global");
        registry.startRun(running, "Bắt đầu");

        registry.updateStatus(running.getId(), "Đang sinh Test Case");
        registry.updateStatus("không-tồn-tại", "bỏ qua");

        assertEquals("Đang sinh Test Case", registry.getRun(running.getId()).getStatus());
    }

    @Test
    public void testBackgroundRunsExcludeTheConversationOnScreen() {
        AiChatRunRegistry registry = new AiChatRunRegistry();
        AiChatSession first = new AiChatSession("global");
        AiChatSession second = new AiChatSession("global");
        registry.startRun(first, "Bắt đầu");
        registry.startRun(second, "Bắt đầu");

        assertEquals(2, registry.countBackgroundRuns(null));
        assertEquals(1, registry.countBackgroundRuns(first.getId()));
        assertEquals(1, registry.countBackgroundRuns(second.getId()));
        assertEquals(2, registry.countBackgroundRuns("phiên-khác-không-chạy"),
                "Mọi phiên đang chạy đều là chạy ngầm khi phiên đang mở không chạy");
    }

    @Test
    public void testFinishInBackgroundMarksResultUnread() {
        AiChatRunRegistry registry = new AiChatRunRegistry();
        AiChatSession background = new AiChatSession("global");
        AiChatSession viewed = new AiChatSession("global");
        registry.startRun(background, "Bắt đầu");
        registry.startRun(viewed, "Bắt đầu");

        registry.finishRun(background.getId(), viewed.getId());
        registry.finishRun(viewed.getId(), viewed.getId());

        assertFalse(registry.isRunning(background.getId()));
        assertFalse(registry.isRunning(viewed.getId()));
        assertTrue(registry.isUnread(background.getId()),
                "Kết quả của phiên chạy ngầm phải được đánh dấu chưa xem");
        assertFalse(registry.isUnread(viewed.getId()),
                "Phiên đang mở không được đánh dấu chưa xem");
        assertEquals(1, registry.countUnread());
    }

    @Test
    public void testOpeningConversationClearsItsUnreadFlag() {
        AiChatRunRegistry registry = new AiChatRunRegistry();
        AiChatSession background = new AiChatSession("global");
        registry.startRun(background, "Bắt đầu");
        registry.finishRun(background.getId(), "phiên-khác");
        assertTrue(registry.isUnread(background.getId()));

        registry.markRead(background.getId());

        assertFalse(registry.isUnread(background.getId()));
        assertEquals(0, registry.countUnread());
    }

    @Test
    public void testFinishWhileViewedClearsAStaleUnreadFlag() {
        AiChatRunRegistry registry = new AiChatRunRegistry();
        AiChatSession session = new AiChatSession("global");
        registry.startRun(session, "Bắt đầu");
        registry.finishRun(session.getId(), "phiên-khác");
        assertTrue(registry.isUnread(session.getId()));

        // The user opens the conversation and sends another prompt that finishes while still viewed.
        registry.startRun(session, "Lượt hai");
        registry.finishRun(session.getId(), session.getId());

        assertFalse(registry.isUnread(session.getId()));
    }

    @Test
    public void testFinishingAnUnknownRunLeavesNoUnreadFlag() {
        AiChatRunRegistry registry = new AiChatRunRegistry();

        registry.finishRun("chưa-từng-chạy", "phiên-khác");

        assertEquals(0, registry.countUnread());
    }

    @Test
    public void testForgetDropsRunningAndUnreadState() {
        AiChatRunRegistry registry = new AiChatRunRegistry();
        AiChatSession session = new AiChatSession("global");
        registry.startRun(session, "Bắt đầu");
        registry.finishRun(session.getId(), "phiên-khác");
        registry.startRun(session, "Lượt hai");

        registry.forget(session.getId());

        assertFalse(registry.isRunning(session.getId()));
        assertFalse(registry.isUnread(session.getId()));
    }
}
