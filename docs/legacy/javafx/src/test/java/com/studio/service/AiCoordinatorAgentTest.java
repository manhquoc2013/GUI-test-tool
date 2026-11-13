package com.studio.service;

import com.studio.model.AiAgentRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The planner is an LLM, so its output is untrusted input. These tests pin that a plan which
 * cannot be trusted is rejected WHOLE - never partially executed.
 */
class AiCoordinatorAgentTest {

    private static String planJson(String... ruleIdAndBrief) {
        StringBuilder sb = new StringBuilder("{\"tasks\":[");
        for (int i = 0; i < ruleIdAndBrief.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append("{\"ruleId\":\"").append(ruleIdAndBrief[i])
              .append("\",\"taskBrief\":\"").append(ruleIdAndBrief[i + 1]).append("\"}");
        }
        return sb.append("]}").toString();
    }

    private static AiCoordinatorAgent agentReturning(String raw) {
        return new AiCoordinatorAgent(req -> raw);
    }

    @Test
    void acceptsValidPlanInDeclaredOrder() throws Exception {
        String json = planJson(
                AiAgentRuleCatalog.SPEC_MINER, "bóc tách SRS",
                AiAgentRuleCatalog.TEST_ARCHITECT, "sinh case từ đặc tả");

        List<AiAgentPlanParser.PlannedTask> planned = agentReturning(json).plan("làm bộ test cho SRS");

        assertEquals(2, planned.size());
        assertEquals(AiAgentRuleCatalog.SPEC_MINER, planned.get(0).ruleId());
        assertEquals("sinh case từ đặc tả", planned.get(1).taskBrief());
    }

    @Test
    void rejectsUnknownRuleIdAndExecutesNothing() {
        String json = planJson(
                AiAgentRuleCatalog.SPEC_MINER, "bóc tách",
                "agent-tu-che", "làm mọi thứ");

        assertThrows(AiAgentPlanParser.InvalidPlanException.class,
                () -> agentReturning(json).plan("yêu cầu"));
    }

    @Test
    void rejectsDelegatingToCoordinatorItself() {
        String json = planJson(AiAgentRuleCatalog.COORDINATOR, "tự chia việc");

        AiAgentPlanParser.InvalidPlanException ex = assertThrows(
                AiAgentPlanParser.InvalidPlanException.class,
                () -> agentReturning(json).plan("yêu cầu"));
        assertTrue(ex.getMessage().contains("Coordinator"));
    }

    @Test
    void rejectsDuplicateRuleAssignment() {
        String json = planJson(
                AiAgentRuleCatalog.TEST_ARCHITECT, "việc A",
                AiAgentRuleCatalog.TEST_ARCHITECT, "việc B");

        assertThrows(AiAgentPlanParser.InvalidPlanException.class,
                () -> agentReturning(json).plan("yêu cầu"));
    }

    @Test
    void rejectsEmptyPlanAndBlankRequest() {
        assertThrows(AiAgentPlanParser.InvalidPlanException.class,
                () -> agentReturning("{\"tasks\":[]}").plan("yêu cầu"));
        assertThrows(AiAgentPlanParser.InvalidPlanException.class,
                () -> agentReturning(planJson(AiAgentRuleCatalog.SPEC_MINER, "x")).plan("   "));
    }

    @Test
    void rejectsNonJsonOutput() {
        assertThrows(AiAgentPlanParser.InvalidPlanException.class,
                () -> agentReturning("Tôi nghĩ nên chia thành 3 việc").plan("yêu cầu"));
    }

    @Test
    void toleratesMarkdownFencedPlan() throws Exception {
        String fenced = "```json\n" + planJson(AiAgentRuleCatalog.SPEC_MINER, "bóc tách") + "\n```";
        assertEquals(1, agentReturning(fenced).plan("yêu cầu").size());
    }

    /**
     * Separation of duties is structural: the coordinator carries the work-forbidding rule and
     * its planner prompt repeats those prohibitions, so it cannot silently do specialist work.
     */
    @Test
    void coordinatorRuleForbidsSpecialistWork() {
        AiAgentRule rule = new AiCoordinatorAgent(req -> "{}").rule();
        assertTrue(rule.getProhibitions().contains("tự sinh test case"));
        assertTrue(rule.getProhibitions().contains("tự phân tích tài liệu"));

        String prompt = new AiCoordinatorAgent(req -> "{}").plannerSystemPrompt();
        assertTrue(prompt.contains("KHÔNG tự làm việc chuyên môn"));
        assertTrue(prompt.contains("spec-miner"), "prompt phải nêu ruleId hợp lệ để model không bịa");
        assertTrue(prompt.contains("\"tasks\""));
    }

    @Test
    void convertsPlanToOrchestratorTasksResolvingRules() throws Exception {
        AiCoordinatorAgent agent = agentReturning(
                planJson(AiAgentRuleCatalog.REALITY_CHECKER, "chấm điểm case"));

        List<AiSubAgentOrchestrator.SubAgentTask> tasks = agent.toTasks(agent.plan("kiểm tra"));

        assertEquals(1, tasks.size());
        assertEquals("RealityChecker", tasks.get(0).rule().getName());
        assertEquals("chấm điểm case", tasks.get(0).taskBrief());
    }
}
