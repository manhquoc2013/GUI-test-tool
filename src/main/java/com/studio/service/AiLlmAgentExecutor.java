package com.studio.service;

import com.studio.model.AiAgentRule;
import com.studio.model.AiConfig;
import com.studio.model.AiChatMessage;

import java.util.List;

/**
 * Runs one sub-agent through the real LLM.
 *
 * The prompt is built from that agent's OWN catalogue rule, so the boundary an agent is held to
 * comes from the fixed rule set rather than from whatever the planner wrote. An agent therefore
 * cannot be talked into a wider job by a task brief that contradicts its rule.
 */
public final class AiLlmAgentExecutor implements AiSubAgentOrchestrator.AgentExecutor {

    /** Minimal LLM seam, so the prompt construction is testable without a network call. */
    public interface Completion {
        String complete(AiConfig config, List<AiChatMessage> messages) throws Exception;
    }

    private final AiConfig config;
    private final Completion completion;
    private final String sharedContext;

    public AiLlmAgentExecutor(AiConfig config, Completion completion, String sharedContext) {
        this.config = config;
        this.completion = completion;
        this.sharedContext = sharedContext != null ? sharedContext : "";
    }

    @Override
    public String execute(AiAgentRule rule, String taskBrief) throws Exception {
        List<AiChatMessage> messages = buildMessages(rule, taskBrief);
        String raw = completion.complete(config, messages);
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("Agent " + rule.getName() + " trả về nội dung rỗng");
        }
        return raw.trim();
    }

    /** System prompt = the agent's own rule; user prompt = brief plus shared context. */
    public List<AiChatMessage> buildMessages(AiAgentRule rule, String taskBrief) {
        return List.of(
                new AiChatMessage("system", systemPrompt(rule)),
                new AiChatMessage("user", userPrompt(taskBrief)));
    }

    static String systemPrompt(AiAgentRule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là ").append(rule.getName())
          .append(" — ").append(rule.getRole()).append(".\n\n");
        sb.append("Bạn CHỈ thực hiện nhiệm vụ trong phạm vi nghĩa vụ dưới đây.\n");
        for (String o : rule.getObligations()) {
            sb.append("✓ ").append(o).append('\n');
        }
        if (!rule.getProhibitions().isEmpty()) {
            sb.append("\nTuyệt đối KHÔNG:\n");
            for (String p : rule.getProhibitions()) {
                sb.append("✗ ").append(p).append('\n');
            }
        }
        if (!rule.getRequiredEvidence().isEmpty()) {
            sb.append("\nPhải trả về bằng chứng:\n");
            for (String e : rule.getRequiredEvidence()) {
                sb.append("↩ ").append(e).append('\n');
            }
        }
        sb.append("\nNếu nhiệm vụ đòi hỏi việc ngoài phạm vi, hãy nêu rõ là không thực hiện được và lý do.");
        return sb.toString();
    }

    private String userPrompt(String taskBrief) {
        if (sharedContext.isBlank()) {
            return taskBrief;
        }
        return taskBrief + "\n\n--- Ngữ cảnh chung ---\n" + sharedContext;
    }
}
