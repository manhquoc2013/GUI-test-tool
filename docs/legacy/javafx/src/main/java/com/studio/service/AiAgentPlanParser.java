package com.studio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a coordinator plan and rejects anything the catalogue does not authorise.
 *
 * This is the trust boundary of delegation: the planner is an LLM, so its output is treated as
 * untrusted input. A task naming an unknown ruleId, or trying to delegate to the coordinator
 * itself, would let the model invent an agent and bypass every rule — those plans are rejected
 * whole rather than partially executed.
 */
public final class AiAgentPlanParser {

    /** One validated delegation. */
    public record PlannedTask(String ruleId, String taskBrief) {
    }

    /** Thrown when the plan cannot be trusted; the caller must not execute any part of it. */
    public static final class InvalidPlanException extends Exception {

        /** Serialisation is never used; the value only silences the missing-SUID contract warning. */
        private static final long serialVersionUID = 1L;

        public InvalidPlanException(String message) {
            super(message);
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiAgentPlanParser() {
    }

    /**
     * Extracts the plan array from raw model output (tolerating a fenced code block) and
     * validates every entry against the fixed catalogue.
     */
    public static List<PlannedTask> parse(String rawOutput) throws InvalidPlanException {
        if (rawOutput == null || rawOutput.isBlank()) {
            throw new InvalidPlanException("Kế hoạch rỗng");
        }
        String json = extractJson(rawOutput);
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new InvalidPlanException("Kế hoạch không phải JSON hợp lệ: " + e.getMessage());
        }

        JsonNode tasksNode = root.isArray() ? root : root.path("tasks");
        if (!tasksNode.isArray() || tasksNode.isEmpty()) {
            throw new InvalidPlanException("Kế hoạch không chứa nhiệm vụ nào");
        }

        List<PlannedTask> tasks = new ArrayList<>();
        for (JsonNode node : tasksNode) {
            String ruleId = text(node, "ruleId");
            String brief = text(node, "taskBrief");
            if (ruleId == null || ruleId.isBlank()) {
                throw new InvalidPlanException("Nhiệm vụ thiếu ruleId");
            }
            if (brief == null || brief.isBlank()) {
                throw new InvalidPlanException("Nhiệm vụ '" + ruleId + "' thiếu mô tả");
            }
            if (AiAgentRuleCatalog.COORDINATOR.equals(ruleId)) {
                throw new InvalidPlanException(
                        "Không được giao việc cho chính Coordinator: " + ruleId);
            }
            if (!AiAgentRuleCatalog.isDelegable(ruleId)) {
                throw new InvalidPlanException(
                        "ruleId không có trong danh mục: " + ruleId
                                + ". Hợp lệ: " + AiAgentRuleCatalog.delegableRuleIds());
            }
            tasks.add(new PlannedTask(ruleId, brief.trim()));
        }
        return tasks;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** Strips a Markdown fence if the model wrapped its JSON. */
    private static String extractJson(String raw) {
        String trimmed = raw.trim();
        int fence = trimmed.indexOf("```");
        if (fence >= 0) {
            int start = trimmed.indexOf('\n', fence);
            int end = trimmed.indexOf("```", fence + 3);
            if (start > 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }
}
