package com.studio.service;

import com.studio.model.AiAgentRule;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The orchestrating agent: it decides WHO does WHAT, and does none of the work itself.
 *
 * Its own rule forbids producing test cases, analysing documents or editing code, so the
 * separation of duties is structural, not a convention: this class has no code path that
 * generates domain output, only one that turns a request into a validated task list.
 *
 * The planner (an LLM) is injected as a function returning raw plan JSON, so the delegation
 * logic is testable without a model call.
 */
public final class AiCoordinatorAgent {

    private final Function<String, String> planner;

    public AiCoordinatorAgent(Function<String, String> planner) {
        this.planner = planner;
    }

    /** The rule this agent must obey, from the fixed catalogue. */
    public AiAgentRule rule() {
        return AiAgentRuleCatalog.require(AiAgentRuleCatalog.COORDINATOR);
    }

    /** System prompt handed to the planner: the coordinator's own rule plus the legal rule ids. */
    public String plannerSystemPrompt() {
        StringBuilder sb = new StringBuilder("Bạn là tác nhân điều phối. Bạn KHÔNG tự làm việc chuyên môn.\n");
        for (String o : rule().getObligations()) {
            sb.append("✓ ").append(o).append('\n');
        }
        for (String p : rule().getProhibitions()) {
            sb.append("✗ ").append(p).append('\n');
        }
        sb.append("\nruleId hợp lệ: ").append(AiAgentRuleCatalog.delegableRuleIds()).append('\n');
        sb.append("Trả về JSON: {\"tasks\":[{\"ruleId\":\"<id>\",\"taskBrief\":\"<mô tả>\"}]}\n");
        return sb.toString();
    }

    /**
     * Plans the delegation for one user request. Returns at most one task per rule so a single
     * rule cannot be split into duplicate specialists that would fight over the same output.
     */
    public List<AiAgentPlanParser.PlannedTask> plan(String userRequest) throws AiAgentPlanParser.InvalidPlanException {
        if (userRequest == null || userRequest.isBlank()) {
            throw new AiAgentPlanParser.InvalidPlanException("Yêu cầu rỗng, không có gì để phân rã");
        }
        List<AiAgentPlanParser.PlannedTask> parsed = AiAgentPlanParser.parse(planner.apply(userRequest));
        List<AiAgentPlanParser.PlannedTask> unique = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (AiAgentPlanParser.PlannedTask t : parsed) {
            if (seen.contains(t.ruleId())) {
                throw new AiAgentPlanParser.InvalidPlanException(
                        "ruleId bị giao trùng nhiệm vụ: " + t.ruleId());
            }
            seen.add(t.ruleId());
            unique.add(t);
        }
        return unique;
    }

    /** Converts a validated plan into orchestrator tasks, resolving rules from the catalogue. */
    public List<AiSubAgentOrchestrator.SubAgentTask> toTasks(List<AiAgentPlanParser.PlannedTask> planned) {
        List<AiSubAgentOrchestrator.SubAgentTask> tasks = new ArrayList<>();
        for (AiAgentPlanParser.PlannedTask p : planned) {
            tasks.add(new AiSubAgentOrchestrator.SubAgentTask(
                    AiAgentRuleCatalog.require(p.ruleId()), p.taskBrief()));
        }
        return tasks;
    }
}
