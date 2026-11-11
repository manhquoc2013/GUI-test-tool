package com.studio.service;

import com.studio.model.AiAgentRule;
import com.studio.model.AiChatMessage;
import com.studio.model.AiChatSession;
import com.studio.model.AiSubAgentSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
        List<String> received = java.util.Collections.synchronizedList(new ArrayList<>());

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(
                (AiAgentRule r, String brief) -> "kết quả " + r.getName(),
                null,
                msg -> received.add(msg.getContent()));

        orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "b1"),
                new AiSubAgentOrchestrator.SubAgentTask(rule("R2", "Beta", "r2", List.of()), "b2")));

        assertTrue(received.contains("kết quả Alpha"), "kết quả của agent phải được đẩy ra transcript");
        assertTrue(received.contains("kết quả Beta"));
        // Each agent's answer is published before the aggregate, not buffered until the pipeline ends.
        int aggregateIndex = received.size() - 1;
        assertTrue(received.get(aggregateIndex).startsWith("Kết quả tổng hợp"),
                "kết quả tổng hợp luôn là message cuối cùng");
        assertEquals(parent.getMessages().size(), received.size(),
                "mọi message vào transcript đều phải được thông báo cho UI");
    }

    @Test
    void runsChildrenConcurrentlyInsteadOfBackToBack() throws Exception {
        AiChatSession parent = new AiChatSession();
        java.util.concurrent.CountDownLatch bothRunning = new java.util.concurrent.CountDownLatch(2);
        List<String> started = java.util.Collections.synchronizedList(new ArrayList<>());

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator((r, brief) -> {
            started.add(r.getName());
            bothRunning.countDown();
            // Sequential orchestration would stall here until the timeout: the second agent cannot start
            // while the first still holds the only worker.
            assertTrue(bothRunning.await(5, java.util.concurrent.TimeUnit.SECONDS),
                    "agent thứ hai phải chạy trước khi agent đầu kết thúc");
            return "ok";
        }, null);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "b1"),
                new AiSubAgentOrchestrator.SubAgentTask(rule("R2", "Beta", "r2", List.of()), "b2")));

        assertTrue(res.allSucceeded());
        assertEquals(2, started.size());
    }

    @Test
    void parallelBoundOfOneKeepsExecutionSerial() {
        AiChatSession parent = new AiChatSession();
        List<String> finished = java.util.Collections.synchronizedList(new ArrayList<>());

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator((r, brief) -> {
            finished.add(r.getName());
            return "ok";
        }, null);

        orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "b1"),
                new AiSubAgentOrchestrator.SubAgentTask(rule("R2", "Beta", "r2", List.of()), "b2")), 1);

        assertEquals(List.of("Alpha", "Beta"), finished, "a bound of 1 preserves plan order");
    }

    @Test
    void assignmentNotesKeepPlanOrderEvenWhenResultsFinishOutOfOrder() {
        AiChatSession parent = new AiChatSession();
        java.util.concurrent.CountDownLatch alphaMayFinish = new java.util.concurrent.CountDownLatch(1);

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator((r, brief) -> {
            if ("R2".equals(r.getId())) {
                alphaMayFinish.countDown();
                return "beta xong";
            }
            try {
                alphaMayFinish.await(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return "alpha xong";
        }, null);

        orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "brief alpha"),
                new AiSubAgentOrchestrator.SubAgentTask(rule("R2", "Beta", "r2", List.of()), "brief beta")));

        List<String> assignments = parent.getMessages().stream()
                .map(AiChatMessage::getContent)
                .filter(content -> content != null && content.startsWith("Được giao nhiệm vụ:"))
                .toList();
        assertEquals(List.of("Được giao nhiệm vụ: brief alpha", "Được giao nhiệm vụ: brief beta"),
                assignments, "the plan stays readable even though Beta finished first");
    }

    @Test
    void downstreamAgentReceivesTheArtifactItIsAskedToReview() {
        AiChatSession parent = new AiChatSession();
        java.util.Map<String, String> promptSeenByRule = new java.util.concurrent.ConcurrentHashMap<>();

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(recordingExecutor(promptSeenByRule), null);

        orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(
                        AiAgentRuleCatalog.require(AiAgentRuleCatalog.SPEC_MINER), "đọc SRS"),
                new AiSubAgentOrchestrator.SubAgentTask(
                        AiAgentRuleCatalog.require(AiAgentRuleCatalog.TEST_ARCHITECT), "thiết kế case"),
                new AiSubAgentOrchestrator.SubAgentTask(
                        AiAgentRuleCatalog.require(AiAgentRuleCatalog.REALITY_CHECKER), "rà soát bộ case"),
                new AiSubAgentOrchestrator.SubAgentTask(
                        AiAgentRuleCatalog.require(AiAgentRuleCatalog.LOOP_ENGINEER), "sửa theo finding")));

        String architectPrompt = promptSeenByRule.get(AiAgentRuleCatalog.TEST_ARCHITECT);
        assertNotNull(architectPrompt);
        assertTrue(architectPrompt.contains("SAN_PHAM_CUA_" + AiAgentRuleCatalog.SPEC_MINER),
                "the architect must design from the extracted spec, not from an empty prompt");

        String checkerPrompt = promptSeenByRule.get(AiAgentRuleCatalog.REALITY_CHECKER);
        assertNotNull(checkerPrompt);
        assertTrue(checkerPrompt.contains("SAN_PHAM_CUA_" + AiAgentRuleCatalog.TEST_ARCHITECT),
                "the checker must actually receive the test suite it was told to review");

        String loopPrompt = promptSeenByRule.get(AiAgentRuleCatalog.LOOP_ENGINEER);
        assertNotNull(loopPrompt);
        assertTrue(loopPrompt.contains("SAN_PHAM_CUA_" + AiAgentRuleCatalog.REALITY_CHECKER),
                "the loop engineer must receive the findings it was told to act on");
        assertTrue(loopPrompt.contains("SAN_PHAM_CUA_" + AiAgentRuleCatalog.TEST_ARCHITECT),
                "and the suite it must patch, not only the findings about it");
    }

    /** Records the full prompt each agent got and answers with a string that names the agent. */
    private static AiSubAgentOrchestrator.UpstreamAwareExecutor recordingExecutor(
            java.util.Map<String, String> sink) {
        return new AiSubAgentOrchestrator.UpstreamAwareExecutor() {
            @Override
            public String execute(AiAgentRule rule, String taskBrief) {
                throw new AssertionError("the measured seam carries the handoff");
            }

            @Override
            public AiLlmAgentExecutor.MeasuredReply executeMeasured(AiAgentRule rule, String taskBrief,
                                                                    String upstreamArtifacts) {
                sink.put(rule.getId(), taskBrief + "\n" + upstreamArtifacts);
                return new AiLlmAgentExecutor.MeasuredReply("SAN_PHAM_CUA_" + rule.getId(),
                        new AiLlmAgentExecutor.AgentUsage(1, 10, 10));
            }
        };
    }

    @Test
    void aFailedUpstreamDoesNotSpendACallOnAnAgentThatCannotStart() {
        AiChatSession parent = new AiChatSession();
        List<String> called = java.util.Collections.synchronizedList(new ArrayList<>());

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(new AiSubAgentOrchestrator.UpstreamAwareExecutor() {
            @Override
            public String execute(AiAgentRule rule, String taskBrief) {
                throw new AssertionError("the measured seam carries the handoff");
            }

            @Override
            public AiLlmAgentExecutor.MeasuredReply executeMeasured(AiAgentRule rule, String taskBrief,
                                                                    String upstreamArtifacts) {
                called.add(rule.getId());
                if (AiAgentRuleCatalog.SPEC_MINER.equals(rule.getId())) {
                    throw new IllegalStateException("hết ngân sách");
                }
                return new AiLlmAgentExecutor.MeasuredReply("ok",
                        new AiLlmAgentExecutor.AgentUsage(1, 10, 10));
            }
        }, null);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(
                        AiAgentRuleCatalog.require(AiAgentRuleCatalog.SPEC_MINER), "đọc SRS"),
                new AiSubAgentOrchestrator.SubAgentTask(
                        AiAgentRuleCatalog.require(AiAgentRuleCatalog.REALITY_CHECKER), "rà soát")));

        assertFalse(called.contains(AiAgentRuleCatalog.REALITY_CHECKER),
                "an agent whose required input never arrived must not burn a model call");
        assertEquals(AiSubAgentSession.Status.CANCELLED, res.sessions().get(1).getStatus());
        assertTrue(res.openFindings().stream().anyMatch(f -> f.contains("SpecMiner")),
                "the reason must name the upstream agent that failed");
    }

    @Test
    void transientAgentFailureIsRetriedBeforeItIsReported() {
        AiChatSession parent = new AiChatSession();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator((r, brief) -> {
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("Connection reset");
            }
            return "kết quả sau khi thử lại";
        }, null, null, 0L);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "b1")));

        assertEquals(2, calls.get(), "một lỗi tạm thời phải được thử lại đúng một lần");
        assertEquals(AiSubAgentSession.Status.COMPLETED, res.sessions().get(0).getStatus());
        assertTrue(res.allSucceeded(), "thử lại thành công thì lượt chạy vẫn trọn vẹn");
        assertEquals("2", res.sessions().get(0).getMetrics().get("attempts"),
                "số lần thử phải được ghi lại để người dùng biết đã tốn thêm một lượt gọi");
        assertTrue(parent.getMessages().stream()
                        .noneMatch(m -> m.getContent() != null && m.getContent().contains("Không hoàn thành")),
                "một lần lỗi đã được cứu không được để lại bong bóng thất bại");
    }

    @Test
    void retryExhaustionReportsExactlyOneFailure() {
        AiChatSession parent = new AiChatSession();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator((r, brief) -> {
            calls.incrementAndGet();
            throw new java.io.UncheckedIOException(new java.io.IOException("request timed out"));
        }, null, null, 0L);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "b1")));

        assertEquals(AiSubAgentOrchestrator.MAX_AGENT_ATTEMPTS, calls.get(),
                "phải dùng hết ngân sách thử lại rồi mới báo lỗi");
        assertEquals(AiSubAgentSession.Status.FAILED, res.sessions().get(0).getStatus());
        assertEquals(1, parent.getMessages().stream()
                        .filter(m -> m.getContent() != null && m.getContent().startsWith("Không hoàn thành"))
                        .count(),
                "mỗi agent chỉ được một bong bóng lỗi, không phải một bong bóng cho mỗi lần thử");
    }

    @Test
    void userStopIsNeverRetried() {
        AiChatSession parent = new AiChatSession();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator((r, brief) -> {
            calls.incrementAndGet();
            throw new AiRunCancelledException();
        }, null, null, 0L);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "b1")));

        assertEquals(1, calls.get(), "người dùng đã dừng thì thử lại chỉ tốn thêm một lượt gọi vô ích");
        assertEquals(AiSubAgentSession.Status.CANCELLED, res.sessions().get(0).getStatus());
    }

    @Test
    void deterministicFailureIsNotRetried() {
        AiChatSession parent = new AiChatSession();
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator((r, brief) -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("rule id không tồn tại");
        }, null, null, 0L);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R1", "Alpha", "r1", List.of()), "b1")));

        assertEquals(1, calls.get(), "lỗi do tham số sai sẽ lặp lại y hệt, thử lại là lãng phí");
        assertEquals(AiSubAgentSession.Status.FAILED, res.sessions().get(0).getStatus());
        assertTrue(res.openFindings().stream().anyMatch(f -> f.contains("rule id không tồn tại")));
    }

    @Test
    void aFailedUpstreamThatRecoversOnRetryStillFeedsItsDependent() {
        AiChatSession parent = new AiChatSession();
        java.util.concurrent.atomic.AtomicInteger minerCalls = new java.util.concurrent.atomic.AtomicInteger();

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(new AiSubAgentOrchestrator.UpstreamAwareExecutor() {
            @Override
            public String execute(AiAgentRule rule, String taskBrief) {
                throw new AssertionError("the measured seam carries the handoff");
            }

            @Override
            public AiLlmAgentExecutor.MeasuredReply executeMeasured(AiAgentRule rule, String taskBrief,
                                                                    String upstreamArtifacts) {
                if (AiAgentRuleCatalog.SPEC_MINER.equals(rule.getId()) && minerCalls.incrementAndGet() == 1) {
                    throw new IllegalStateException("stream closed");
                }
                return new AiLlmAgentExecutor.MeasuredReply("SAN_PHAM_CUA_" + rule.getId(),
                        new AiLlmAgentExecutor.AgentUsage(1, 10, 10));
            }
        }, null, null, 0L);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(
                        AiAgentRuleCatalog.require(AiAgentRuleCatalog.SPEC_MINER), "đọc SRS"),
                new AiSubAgentOrchestrator.SubAgentTask(
                        AiAgentRuleCatalog.require(AiAgentRuleCatalog.TEST_ARCHITECT), "thiết kế case")));

        assertEquals(AiSubAgentSession.Status.COMPLETED, res.sessions().get(0).getStatus());
        assertEquals(AiSubAgentSession.Status.COMPLETED, res.sessions().get(1).getStatus(),
                "thử lại thành công thì agent phụ thuộc phải chạy, không bị huỷ oan");
        assertTrue(res.allSucceeded());
    }

    @Test
    void digestCarriesSpecialistConclusionsToTheGeneratorWithinBudget() {
        AiChatSession parent = new AiChatSession();
        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(new AiSubAgentOrchestrator.UpstreamAwareExecutor() {
            @Override
            public String execute(AiAgentRule rule, String taskBrief) {
                throw new AssertionError("the measured seam carries the handoff");
            }

            @Override
            public AiLlmAgentExecutor.MeasuredReply executeMeasured(AiAgentRule rule, String taskBrief,
                                                                    String upstreamArtifacts) {
                if (AiAgentRuleCatalog.REALITY_CHECKER.equals(rule.getId())) {
                    throw new IllegalStateException("timeout");
                }
                return new AiLlmAgentExecutor.MeasuredReply("KẾT_LUẬN_CỦA_" + rule.getId() + "\n".repeat(50),
                        new AiLlmAgentExecutor.AgentUsage(1, 10, 10));
            }
        }, null);

        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(
                        AiAgentRuleCatalog.require(AiAgentRuleCatalog.SPEC_MINER), "đọc SRS"),
                new AiSubAgentOrchestrator.SubAgentTask(
                        AiAgentRuleCatalog.require(AiAgentRuleCatalog.TEST_ARCHITECT), "thiết kế"),
                new AiSubAgentOrchestrator.SubAgentTask(
                        AiAgentRuleCatalog.require(AiAgentRuleCatalog.REALITY_CHECKER), "rà soát")));

        String digest = res.reviewDigestForGenerator();
        assertTrue(digest.contains("KẾT_LUẬN_CỦA_" + AiAgentRuleCatalog.SPEC_MINER));
        assertFalse(digest.contains(AiAgentRuleCatalog.REALITY_CHECKER),
                "a failed agent contributed no conclusion, so it must not appear in the digest");
        assertTrue(digest.length() <= 3_000 + 120, "the digest must stay inside the prompt budget");
        assertFalse(digest.contains("\n\n"), "one line per agent keeps the prompt compact");
    }

    /**
     * End-to-end wiring check: catalogue → waves → real executor → prompt. The seam test proves the
     * orchestrator hands a block over; this proves the block lands in the prompt the model sees.
     */
    @Test
    void theRealExecutorSeesTheUpstreamArtifactInThePromptItSends() {
        AiChatSession parent = new AiChatSession();
        java.util.Map<String, String> userPromptByAgent = new java.util.concurrent.ConcurrentHashMap<>();

        AiLlmAgentExecutor executor = new AiLlmAgentExecutor(new com.studio.model.AiConfig(),
                new AiLlmAgentExecutor.Completion() {
                    @Override
                    public CharSequence complete(com.studio.model.AiConfig cfg, List<AiChatMessage> messages) {
                        return complete(cfg, messages, "không nhãn");
                    }

                    @Override
                    public CharSequence complete(com.studio.model.AiConfig cfg, List<AiChatMessage> messages,
                                                 String agentName) {
                        userPromptByAgent.put(agentName, messages.get(1).getContent());
                        return "ARTIFACT_OF_" + agentName;
                    }
                }, "SRS: màn hình đăng ký");

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator(executor, null);
        orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(
                        AiAgentRuleCatalog.require(AiAgentRuleCatalog.SPEC_MINER), "đọc SRS"),
                new AiSubAgentOrchestrator.SubAgentTask(
                        AiAgentRuleCatalog.require(AiAgentRuleCatalog.TEST_ARCHITECT), "thiết kế case"),
                new AiSubAgentOrchestrator.SubAgentTask(
                        AiAgentRuleCatalog.require(AiAgentRuleCatalog.REALITY_CHECKER), "rà soát")));

        String checkerPrompt = userPromptByAgent.get("RealityChecker");
        assertNotNull(checkerPrompt);
        assertTrue(checkerPrompt.contains("ARTIFACT_OF_TestArchitect"));
        assertTrue(checkerPrompt.contains("SRS: màn hình đăng ký"),
                "the shared grounding must survive alongside the artefact");
    }

    @Test
    void digestOfAnEmptyRunIsEmpty() {
        AiChatSession parent = new AiChatSession();
        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator((r, brief) -> "ok", null);
        AiSubAgentOrchestrator.OrchestrationResult res = orch.orchestrate(parent, List.of());

        assertTrue(res.reviewDigestForGenerator().isEmpty());
    }

    @Test
    void independentTasksStillRunInTheSameWave() throws Exception {
        AiChatSession parent = new AiChatSession();
        java.util.concurrent.CountDownLatch bothRunning = new java.util.concurrent.CountDownLatch(2);

        AiSubAgentOrchestrator orch = new AiSubAgentOrchestrator((r, brief) -> {
            bothRunning.countDown();
            assertTrue(bothRunning.await(5, java.util.concurrent.TimeUnit.SECONDS),
                    "two agents with no dependency between them must still overlap");
            return "ok";
        }, null);

        // Two rules with no declared dependency: adding a pipeline must not serialise them.
        orch.orchestrate(parent, List.of(
                new AiSubAgentOrchestrator.SubAgentTask(rule("R-A", "Alpha", "phân tích", List.of()), "a"),
                new AiSubAgentOrchestrator.SubAgentTask(rule("R-B", "Beta", "thống kê", List.of()), "b")));

        assertTrue(bothRunning.await(5, java.util.concurrent.TimeUnit.SECONDS));
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
