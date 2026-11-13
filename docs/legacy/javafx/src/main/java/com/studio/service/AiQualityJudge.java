package com.studio.service;

import com.studio.model.AiGeneratedCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, evidence-only quality judge for generated cases.
 *
 * <p>The producer of a test case never scores it here: this class only inspects artefacts the caller
 * can re-verify (names, traceability links, step/assertion counts, script shape). It cannot be talked
 * into a higher score by prose, and it does not award points for content that the pipeline invented
 * to fill a gap.</p>
 */
public final class AiQualityJudge {

    private static final int MAX_SCORE = 100;

    private AiQualityJudge() {
    }

    /** Score plus human-readable reasons for every point that was not awarded. */
    public record Verdict(int score, List<String> findings) {
        public Verdict {
            findings = findings != null ? List.copyOf(findings) : List.of();
        }
    }

    public static Verdict judge(List<AiGeneratedCase> cases, boolean generateScript) {
        List<String> findings = new ArrayList<>();
        if (cases == null || cases.isEmpty()) {
            return new Verdict(0, List.of("Không có test case nào để thẩm định"));
        }

        List<AiGeneratedCase> judged = cases.stream().filter(c -> c != null && !c.isSuggested()).toList();
        if (judged.isEmpty()) {
            return new Verdict(0, List.of("Toàn bộ case chỉ là đề xuất chưa được kiểm chứng (SUGGESTED)"));
        }

        int score = 0;
        int n = judged.size();

        long wellNamed = judged.stream()
                .filter(c -> c.getName() != null && c.getName().matches("(?i)^TC\\d+_[A-Za-z0-9_]+"))
                .filter(c -> c.getPriority() != null && c.getPriority().matches("P[0-3]"))
                .count();
        score += proportional(wellNamed, n, 15);
        if (wellNamed < n) {
            findings.add((n - wellNamed) + "/" + n + " case chưa đúng chuẩn tên TCxx_<Feature>_<Scenario> hoặc thiếu priority");
        }

        long traceable = judged.stream().filter(AiGeneratedCase::hasTraceability).count();
        score += proportional(traceable, n, 20);
        if (traceable < n) {
            findings.add((n - traceable) + "/" + n + " case thiếu truy vết (testId/screenId/requirementRef)");
        }

        long completeSteps = judged.stream()
                .filter(c -> c.getTestSteps() != null && c.getTestSteps().size() >= 3)
                .count();
        score += proportional(completeSteps, n, 20);
        if (completeSteps < n) {
            findings.add((n - completeSteps) + "/" + n + " case có dưới 3 bước thực thi");
        }

        long verified = judged.stream().filter(AiQualityJudge::hasTwoActiveAssertions).count();
        score += proportional(verified, n, 20);
        if (verified < n) {
            findings.add((n - verified) + "/" + n + " case có dưới 2 assertion ACTIVE");
        }

        long withData = judged.stream()
                .filter(c -> c.getTestDataSets() != null && !c.getTestDataSets().isEmpty())
                .count();
        score += proportional(withData, n, 10);
        if (withData < n) {
            findings.add((n - withData) + "/" + n + " case chưa có bộ dữ liệu kiểm thử");
        }

        if (generateScript) {
            long validScript = judged.stream().filter(AiQualityJudge::hasDeterministicScript).count();
            score += proportional(validScript, n, 15);
            if (validScript < n) {
                findings.add((n - validScript) + "/" + n + " case thiếu script Playwright hợp lệ hoặc còn hard sleep");
            }
        } else {
            long noScript = judged.stream()
                    .filter(c -> c.getScriptCode() == null || c.getScriptCode().isBlank())
                    .count();
            score += proportional(noScript, n, 15);
            if (noScript < n) {
                findings.add((n - noScript) + "/" + n + " case còn script dù chế độ chỉ tạo kịch bản");
            }
        }

        return new Verdict(Math.min(MAX_SCORE, score), findings);
    }

    private static int proportional(long matching, int total, int weight) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.round((double) weight * matching / total);
    }

    private static boolean hasTwoActiveAssertions(AiGeneratedCase c) {
        if (c.getAssertions() == null) {
            return false;
        }
        long active = c.getAssertions().stream()
                .filter(a -> a != null && (a.getStatus() == null || a.getStatus().isBlank()
                        || "ACTIVE".equalsIgnoreCase(a.getStatus())))
                .filter(a -> a.getType() != null && !a.getType().isBlank())
                .count();
        return active >= 2;
    }

    private static boolean hasDeterministicScript(AiGeneratedCase c) {
        String code = c.getScriptCode();
        if (code == null || code.isBlank() || !code.contains("class ")) {
            return false;
        }
        String lower = code.toLowerCase(Locale.ROOT);
        return !lower.contains("thread.sleep") && !lower.contains("waitfortimeout");
    }
}
