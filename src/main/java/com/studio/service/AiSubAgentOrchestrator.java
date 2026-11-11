package com.studio.service;

import com.studio.model.AiAgentRule;
import com.studio.model.AiChatMessage;
import com.studio.model.AiChatSession;
import com.studio.model.AiSubAgentSession;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Coordinates rule-bound sub-agent sessions and merges their outputs into ONE parent result.
 *
 * Design constraints this class enforces:
 * - An agent executes only its assigned rule; work outside that rule is reported as a
 *   finding, never performed. {@link #validateResult} is the gate for that.
 * - The parent receives exactly one aggregated answer, always last, flagged
 *   {@code aggregatedResult}. Individual agent outputs stay visible in the transcript but
 *   are never presented as the final deliverable.
 * - Every child reaches a terminal state; a cancelled or failed child can never be left
 *   RUNNING (see {@link #cancelAll}).
 *
 * The executor is injected so the orchestration can be tested without a live LLM.
 */
public final class AiSubAgentOrchestrator {

    /** Turns a rule plus a task brief into that agent's raw output. */
    public interface AgentExecutor {
        String execute(AiAgentRule rule, String taskBrief) throws Exception;
    }

    /** One dispatchable unit of work: a rule plus the brief for it. */
    public record SubAgentTask(AiAgentRule rule, String taskBrief) {
    }

    /** Outcome of one full orchestration pass. */
    public record OrchestrationResult(
            List<AiSubAgentSession> sessions,
            String aggregatedSummary,
            List<String> openFindings,
            boolean allSucceeded) {
    }

    private final AgentExecutor executor;
    private final Consumer<String> progressListener;

    public AiSubAgentOrchestrator(AgentExecutor executor, Consumer<String> progressListener) {
        this.executor = executor;
        this.progressListener = progressListener;
    }

    /**
     * Runs every task in its own child session, sequentially, recording the exchange on the
     * parent transcript and finally publishing one aggregated answer.
     */
    public OrchestrationResult orchestrate(AiChatSession parent, List<SubAgentTask> tasks) {
        List<AiSubAgentSession> sessions = new ArrayList<>();
        List<String> openFindings = new ArrayList<>();
        boolean allSucceeded = true;

        if (tasks == null || tasks.isEmpty()) {
            String summary = "Không có nhiệm vụ nào được giao cho tác nhân con.";
            publishAggregate(parent, summary, false);
            return new OrchestrationResult(sessions, summary, openFindings, false);
        }

        for (SubAgentTask task : tasks) {
            AiSubAgentSession child = newChild(parent, task);
            sessions.add(child);
            record(parent, child, "assistant", "Được giao nhiệm vụ: " + task.taskBrief());

            if (!runChild(parent, child, task)) {
                allSucceeded = false;
                if (child.getErrorMessage() != null) {
                    openFindings.add(child.getAgentName() + ": " + child.getErrorMessage());
                }
            } else {
                openFindings.addAll(validateResult(child));
            }
        }

        String aggregated = aggregate(sessions);
        publishAggregate(parent, aggregated, allSucceeded);
        parent.setSubAgents(sessions);
        return new OrchestrationResult(sessions, aggregated, openFindings, allSucceeded);
    }

    private AiSubAgentSession newChild(AiChatSession parent, SubAgentTask task) {
        return new AiSubAgentSession(
                parent != null ? parent.getId() : null,
                task.rule().getName(),
                task.rule().getRole(),
                task.rule().getId(),
                describeRule(task.rule()),
                task.taskBrief());
    }

    private boolean runChild(AiChatSession parent, AiSubAgentSession child, SubAgentTask task) {
        child.setStatus(AiSubAgentSession.Status.RUNNING);
        child.setStartedAt(System.currentTimeMillis());
        progress("🤖 [" + child.getAgentName() + "] Đang xử lý…");

        try {
            String output = executor.execute(task.rule(), task.taskBrief());
            child.setResultSummary(output);
            record(parent, child, "assistant", safe(output));
            child.setStatus(AiSubAgentSession.Status.COMPLETED);
            return true;
        } catch (Exception e) {
            child.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.toString());
            child.setStatus(AiSubAgentSession.Status.FAILED);
            record(parent, child, "assistant", "Không hoàn thành nhiệm vụ: " + child.getErrorMessage());
            return false;
        } finally {
            child.setFinishedAt(System.currentTimeMillis());
            child.getMetrics().put("durationMs", String.valueOf(child.getDurationMs()));
        }
    }

    /**
     * Rule-boundary gate: an agent that returned nothing, or claimed work its rule prohibits,
     * contributes findings instead of silently passing. This is what stops a sub-agent from
     * widening its own scope.
     */
    public List<String> validateResult(AiSubAgentSession child) {
        List<String> findings = new ArrayList<>();
        String summary = child.getResultSummary();
        if (summary == null || summary.isBlank()) {
            findings.add(child.getAgentName() + ": không trả về kết quả cho nhiệm vụ được giao");
            return findings;
        }
        String lower = summary.toLowerCase(java.util.Locale.ROOT);
        for (String prohibited : ruleProhibitions(child)) {
            if (!prohibited.isBlank() && lower.contains(prohibited.toLowerCase(java.util.Locale.ROOT))) {
                findings.add(child.getAgentName() + ": vượt phạm vi rule — '" + prohibited + "'");
            }
        }
        return findings;
    }

    private List<String> ruleProhibitions(AiSubAgentSession child) {
        // Prohibitions are carried inside ruleText as bullet lines so they survive persistence.
        List<String> out = new ArrayList<>();
        if (child.getRuleText() == null) return out;
        for (String line : child.getRuleText().split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("✗ ")) {
                out.add(trimmed.substring(2).trim());
            }
        }
        return out;
    }

    private String describeRule(AiAgentRule rule) {
        StringBuilder sb = new StringBuilder(rule.getRole() != null ? rule.getRole() : rule.getName()).append('\n');
        for (String o : rule.getObligations()) {
            sb.append("✓ ").append(o).append('\n');
        }
        for (String p : rule.getProhibitions()) {
            sb.append("✗ ").append(p).append('\n');
        }
        for (String e : rule.getRequiredEvidence()) {
            sb.append("↩ ").append(e).append('\n');
        }
        return sb.toString().trim();
    }

    /** Merges child outputs into exactly one answer. Failed children are named, never hidden. */
    public String aggregate(List<AiSubAgentSession> sessions) {
        Map<String, String> byAgent = new LinkedHashMap<>();
        List<String> failed = new ArrayList<>();
        for (AiSubAgentSession s : sessions) {
            if (s.getStatus() == AiSubAgentSession.Status.COMPLETED) {
                byAgent.put(s.getAgentName(), safe(s.getResultSummary()));
            } else if (s.getStatus() != AiSubAgentSession.Status.CANCELLED) {
                failed.add(s.getAgentName());
            }
        }
        StringBuilder sb = new StringBuilder("Kết quả tổng hợp từ ").append(sessions.size()).append(" tác nhân:\n");
        byAgent.forEach((agent, result) -> sb.append("\n**").append(agent).append("**\n")
                .append(result.length() > 600 ? result.substring(0, 597) + "..." : result).append('\n'));
        if (!failed.isEmpty()) {
            sb.append("\nKhông hoàn thành: ").append(String.join(", ", failed)).append('\n');
        }
        return sb.toString().trim();
    }

    /** Cancels every non-terminal child so none is ever left RUNNING. */
    public void cancelAll(List<AiSubAgentSession> sessions) {
        if (sessions == null) return;
        for (AiSubAgentSession s : sessions) {
            if (!s.isTerminal()) {
                s.setStatus(AiSubAgentSession.Status.CANCELLED);
                s.setFinishedAt(System.currentTimeMillis());
            }
        }
    }

    private void publishAggregate(AiChatSession parent, String summary, boolean allSucceeded) {
        AiChatMessage msg = new AiChatMessage("assistant", summary);
        msg.setAggregatedResult(true);
        if (!allSucceeded) {
            msg.setAgentRole("partial");
        }
        if (parent != null) {
            parent.getMessages().add(msg);
            parent.setLastUpdated(System.currentTimeMillis());
        }
        progress("✅ [Điều phối] Đã tổng hợp kết quả cuối cùng" + (allSucceeded ? "" : " (một phần)"));
    }

    private void record(AiChatSession parent, AiSubAgentSession child, String role, String content) {
        AiChatMessage msg = new AiChatMessage(role, content);
        msg.setAgentId(child.getId());
        msg.setAgentName(child.getAgentName());
        msg.setAgentRole(child.getAgentRole());
        if (parent != null) {
            parent.getMessages().add(msg);
        }
        child.getMessages().add(msg);
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }

    private void progress(String text) {
        if (progressListener != null) {
            progressListener.accept(text);
        }
    }
}
