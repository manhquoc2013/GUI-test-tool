package com.studio.service;

import com.studio.model.AiAgentRule;
import com.studio.model.AiChatMessage;
import com.studio.model.AiChatSession;
import com.studio.model.AiSubAgentSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for rule-bound sub-agent sessions.
 * Each case fails if the orchestration contract is reverted.
 */
class AiSubAgentOrchestratorTest {

    private static AiAgentRule rule(String id, String name, String role, List<String> prohibitions) {
        AiAgentRule r = new AiAgentRule(id, name, role);
        r.getObligations().add("phân tích tài liệu");
        r.getProhibitions().addAll(prohibitions);
        r.getRequiredEvidence().add("danh sách màn hình");
        return r;
    }

    private static AiSubAgentOrchestrator.AgentExecutor fixedOutput(String out) {
        return (r, brief) -> out;
    }

    @Test
    void spawnsOneChildPerTaskWithTerminalState() {
        AiChatSession parent = new AiChatSession();
        List<AiSubAgentOrchestrator.SubAgentTask> tasks = List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "SpecMiner", "Bóc tách SRS", List.of()), "đọc SRS"),
                new AiSubAgentOrchestrator.SubAgentTask(rule("R2", "TestArchitect", "Sinh test case", List.of()), "sinh case"));

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(fixedOutput("đã xong"), null);
        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, tasks);

        assertEquals(2, res.sessions().size(), "mỗi task phải có đúng một session con");
        for (AiSubAgentSession child : res.sessions()) {
            assertEquals(AiSubAgentSession.Status.COMPLETED, child.getStatus());
            assertTrue(child.getStartedAt() > 0);
            assertTrue(child.getFinishedAt() >= child.getStartedAt());
            assertEquals(parent.getId(), child.getParentSessionId());
        }
        assertEquals(2, parent.getSubAgents().size(), "session cha phải giữ danh sách session con");
    }

    @Test
    void eachChildCarriesItsOwnRule() {
        AiChatSession parent = new AiChatSession();
        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(fixedOutput("ok"), null);

        List<AiSubAgentOrchestrator.SubAgentTask> tasks = List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R-ALPHA", "A1", "vai A", List.of()), "brief A"));

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, tasks);

        AiSubAgentSession child = res.sessions().get(0);
        assertEquals("R-ALPHA", child.getRuleId());
        assertTrue(child.getRuleText().contains("vai A"));
        assertEquals("brief A", child.getTaskBrief());
    }

    @Test
    void producesExactlyOneAggregatedResultLast() {
        AiChatSession parent = new AiChatSession();
        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(fixedOutput("kết quả riêng"), null);

        orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "b1"),
                new AiSubAgentOrchestrator.SubAgentTask(rule("R2", "Beta", "r2", List.of()), "b2")));

        List<AiChatMessage> msgs = parent.getMessages();
        long aggregates = msgs.stream().filter(AiChatMessage::isAggregatedResult).count();
        assertEquals(1, aggregates, "chỉ được có MỘT kết quả tổng hợp");
        assertTrue(msgs.get(msgs.size() - 1).isAggregatedResult(), "kết quả tổng hợp phải ở cuối");
    }

    @Test
    void chatMessageCarriesAgentIdentity() {
        AiChatSession parent = new AiChatSession();
        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(fixedOutput("xong"), null);

        orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "SpecMiner", "Bóc tách", List.of()), "b")));

        AiChatMessage agentMsg = parent.getMessages().stream()
                .filter(AiChatMessage::isAgentMessage)
                .findFirst()
                .orElse(null);
        assertNotNull(agentMsg, "phải có message mang danh tính agent");
        assertEquals("SpecMiner", agentMsg.getAgentName());
        assertEquals("Bóc tách", agentMsg.getAgentRole());
        assertNotNull(agentMsg.getAgentId());

        AiChatMessage aggregate = parent.getMessages().get(parent.getMessages().size() - 1);
        assertFalse(aggregate.isAgentMessage(), "kết quả tổng hợp thuộc session cha, không phải agent con");
    }

    @Test
    void failingChildDoesNotHideFailureAndOthersStillRun() {
        AiChatSession parent = new AiChatSession();
        AtomicBoolean secondRan = new AtomicBoolean(false);

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator((r, brief) -> {
            if ("R1".equals(r.getId())) {
                throw new IllegalStateException("hết ngân sách");
            }
            secondRan.set(true);
            return "ok";
        }, null);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "b1"),
                new AiSubAgentOrchestrator.SubAgentTask(rule("R2", "Beta", "r2", List.of()), "b2")));

        assertEquals(AiSubAgentSession.Status.FAILED, res.sessions().get(0).getStatus());
        assertEquals(AiSubAgentSession.Status.COMPLETED, res.sessions().get(1).getStatus());
        assertTrue(secondRan.get(), "agent sau không được bị chặn bởi agent lỗi");
        assertFalse(res.allSucceeded());
        assertTrue(res.openFindings().stream().anyMatch(f -> f.contains("hết ngân sách")));
        assertTrue(res.aggregatedSummary().contains("Không hoàn thành"));
    }

    @Test
    void outOfRuleWorkBecomesFindingNotSilentPass() {
        AiChatSession parent = new AiChatSession();
        // Agent obeys its rule; the prohibited phrase appears in its output -> flagged.
        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(
                fixedOutput("Tôi đã sửa code và chạy migration xong"), null);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(
                        rule("R1", "SpecMiner", "Bóc tách", List.of("sửa code", "chạy migration")), "b")));

        // Hai cụm vi phạm ("sửa code", "chạy migration") => đúng 2 finding, không phải 1.
        assertEquals(2, res.openFindings().size(), "mỗi vi phạm rule phải sinh một finding");
        assertTrue(res.openFindings().stream().allMatch(f -> f.contains("vượt phạm vi rule")));
    }

    @Test
    void emptyResultIsFindingNotSuccess() {
        AiChatSession parent = new AiChatSession();
        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(fixedOutput("   "), null);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "b")));

        assertEquals(1, res.openFindings().size());
        assertTrue(res.openFindings().get(0).contains("không trả về kết quả"));
    }

    @Test
    void noTasksProducesFailureWithoutChildren() {
        AiChatSession parent = new AiChatSession();
        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(fixedOutput("x"), null);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of());

        assertTrue(res.sessions().isEmpty());
        assertFalse(res.allSucceeded());
        assertTrue(parent.getMessages().get(parent.getMessages().size() - 1).isAggregatedResult());
    }

    @Test
    void measuredUsageLandsOnChildMetricsAndTranscriptMessage() {
        AiChatSession parent = new AiChatSession();
        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(
                (AiAgentRule r, String brief) -> "kết quả " + r.getName(), null);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "b1")));
        AiChatMessage msg = parent.getMessages().stream()
                .filter(m -> m.isAgentMessage() && ("kết quả " + "Alpha").equals(m.getContent()))
                .findFirst()
                .orElseThrow();

        // A fake executor reports one call and nothing about usage: the count is known, the tokens are not.
        assertEquals("1", res.sessions().get(0).getMetrics().get("llmCalls"), "call count is always known");
        assertFalse(msg.hasReportedTokens(), "an executor that cannot report usage must not invent it");
        assertEquals(0, msg.getReportedTotalTokens());
    }

    @Test
    void reportedUsageIsRecordedOnChildAndMessage() {
        AiChatSession parent = new AiChatSession();
        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(new AiSubAgentOrchestrator.AgentExecutor() {
            @Override
            public String execute(AiAgentRule rule, String taskBrief) {
                throw new AssertionError("the orchestrator must prefer executeMeasured");
            }

            @Override
            public AiLlmAgentExecutor.MeasuredReply executeMeasured(AiAgentRule rule, String taskBrief) {
                return new AiLlmAgentExecutor.MeasuredReply("kết quả đo",
                        new AiLlmAgentExecutor.AgentUsage(1, 1200, 340));
            }
        }, null);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "b1")));

        AiSubAgentSession child = res.sessions().get(0);
        assertEquals("1", child.getMetrics().get("llmCalls"));
        assertEquals("1200", child.getMetrics().get("promptTokens"));
        assertEquals("340", child.getMetrics().get("completionTokens"));
        assertEquals("true", child.getMetrics().get("reportedUsage"));

        AiChatMessage msg = parent.getMessages().stream()
                .filter(m -> m.isAgentMessage() && "kết quả đo".equals(m.getContent()))
                .findFirst()
                .orElseThrow();
        assertTrue(msg.hasReportedTokens());
        assertEquals(1200, msg.getPromptTokens());
        assertEquals(340, msg.getCompletionTokens());
        assertEquals(1540, msg.getReportedTotalTokens());
        assertEquals(1540, msg.getEstimatedTokens(), "a reported total supersedes the heuristic");
    }

    @Test
    void childTranscriptIsIsolatedFromSiblings() {
        AiChatSession parent = new AiChatSession();
        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(
                (r, brief) -> "kết quả của " + r.getName(), null);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "b1"),
                new AiSubAgentOrchestrator.SubAgentTask(rule("R2", "Beta", "r2", List.of()), "b2")));

        AiSubAgentSession alpha = res.sessions().get(0);
        AiSubAgentSession beta = res.sessions().get(1);
        // Each child only holds messages tagged with its own agent name.
        assertTrue(alpha.getMessages().stream().allMatch(m -> "Alpha".equals(m.getAgentName())));
        assertTrue(beta.getMessages().stream().allMatch(m -> "Beta".equals(m.getAgentName())));
        assertNull(alpha.getMessages().stream()
                .filter(m -> "Beta".equals(m.getAgentName())).findFirst().orElse(null));
    }

    @Test
    void publishesEachAgentTurnWhileThePipelineIsStillRunning() {
        AiChatSession parent = new AiChatSession();
        AtomicInteger completedAgents = new AtomicInteger();
        List<String> received = new ArrayList<>();
        List<Integer> completedWhenReceived = new ArrayList<>();

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(
                (AiAgentRule r, String brief) -> {
                    completedAgents.incrementAndGet();
                    return "kết quả " + r.getName();
                },
                null,
                msg -> {
                    received.add(msg.getContent());
                    completedWhenReceived.add(completedAgents.get());
                });

        orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "b1"),
                new AiSubAgentOrchestrator.SubAgentTask(rule("R2", "Beta", "r2", List.of()), "b2")));

        int alphaResult = received.indexOf("kết quả Alpha");
        assertTrue(alphaResult >= 0, "kết quả của agent phải được đẩy ra transcript");
        // Beta had not started when Alpha's answer was published: the UI is not waiting for the end.
        assertEquals(1, completedWhenReceived.get(alphaResult),
                "agent xong trước phải hiện ngay, không đợi cả pipeline");
        assertEquals("kết quả Beta", received.get(received.size() - 2),
                "kết quả tổng hợp luôn là message cuối cùng");
        assertTrue(received.get(received.size() - 1).startsWith("Kết quả tổng hợp"));
        assertEquals(parent.getMessages().size(), received.size(),
                "mọi message vào transcript đều phải được thông báo cho UI");
    }

    @Test
    void aggregateSummarisesEachAgentInOneLineInsteadOfCopyingProse() {
        AiChatSession parent = new AiChatSession();
        String alphaProse = "Mở đầu của Alpha\nThân bài rất dài của Alpha\nKết luận của Alpha";
        String betaProse = "Mở đầu của Beta\nThân bài rất dài của Beta";

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(
                (AiAgentRule r, String brief) -> "Alpha".equals(r.getName()) ? alphaProse : betaProse, null);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "b1"),
                new AiSubAgentOrchestrator.SubAgentTask(rule("R2", "Beta", "r2", List.of()), "b2")));

        String aggregate = parent.getMessages().get(parent.getMessages().size() - 1).getContent();
        assertTrue(aggregate.contains("• **Alpha**"), "mỗi agent đóng góp một dòng tổng hợp");
        assertTrue(aggregate.contains("• **Beta**"));
        assertFalse(aggregate.contains("Thân bài rất dài của Alpha"),
                "văn xuôi của agent không được lặp lại trong bản tổng hợp");
        assertFalse(aggregate.contains("Thân bài rất dài của Beta"));
        assertEquals(2, aggregate.lines().filter(l -> l.startsWith("• ")).count());
        assertEquals(res.aggregatedSummary(), aggregate);

        // The full text still exists exactly once, in the agent's own bubble.
        long alphaCopies = parent.getMessages().stream()
                .filter(m -> m.getContent() != null && m.getContent().contains("Thân bài rất dài của Alpha"))
                .count();
        assertEquals(1, alphaCopies, "nội dung dài chỉ được xuất hiện một lần");
    }
}
