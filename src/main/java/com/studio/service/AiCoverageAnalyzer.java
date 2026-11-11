package com.studio.service;

import com.studio.model.AiGeneratedCase;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Computes verifiable requirement coverage from traceability links instead of counting test cases.
 *
 * <p>A screen is covered only when a case that sits inside that screen covers the scenario kind
 * (happy path, negative validation, boundary, business rule). Cases without a screen or an SRS
 * reference are reported as untraceable and never inflate the coverage number.</p>
 */
public final class AiCoverageAnalyzer {

    /** Scenario kinds the pipeline asks the model to produce for every screen. */
    public static final List<String> REQUIRED_KINDS = List.of(
            "HappyPath", "NegativeValidation", "BoundaryBVA", "BusinessRule");

    private AiCoverageAnalyzer() {
    }

    public record ScreenCoverage(String screenId,
                                 String screenTitle,
                                 int traceableCases,
                                 Set<String> coveredKinds,
                                 List<String> missingKinds) {
    }

    public record Report(int totalCases,
                         int traceableCases,
                         int screensTotal,
                         int screensCovered,
                         List<ScreenCoverage> screens,
                         List<String> findings) {

        /** Percentage of cases that point back to a screen and an SRS clause. */
        public int traceabilityPercent() {
            return totalCases == 0 ? 0 : Math.round(traceableCases * 100f / totalCases);
        }

        /** Percentage of screens that have at least one traceable case. */
        public int screenCoveragePercent() {
            return screensTotal == 0 ? 0 : Math.round(screensCovered * 100f / screensTotal);
        }

        public boolean hasGaps() {
            return (screensTotal > 0 && screensCovered < screensTotal) || !findings.isEmpty();
        }
    }

    public static Report analyze(List<AiGeneratedCase> cases,
                                 List<AiMultiAgentCoordinator.ScreenSpecSection> screens) {
        List<AiGeneratedCase> usable = cases == null ? List.of() : cases.stream()
                .filter(c -> c != null && !c.isSuggested())
                .toList();
        int traceable = (int) usable.stream().filter(AiGeneratedCase::hasTraceability).count();

        List<String> findings = new ArrayList<>();
        if (!usable.isEmpty() && traceable < usable.size()) {
            findings.add((usable.size() - traceable) + "/" + usable.size()
                    + " case không truy vết được về màn hình + điều khoản SRS");
        }

        List<AiMultiAgentCoordinator.ScreenSpecSection> screenList =
                screens != null ? screens : List.of();
        List<ScreenCoverage> perScreen = new ArrayList<>();
        int coveredScreens = 0;

        for (AiMultiAgentCoordinator.ScreenSpecSection screen : screenList) {
            String token = normalize(screen.featureToken());
            String title = normalize(screen.screenTitle());

            List<AiGeneratedCase> matching = usable.stream()
                    .filter(c -> matchesScreen(c, token, title))
                    .toList();
            Set<String> kinds = new LinkedHashSet<>();
            for (AiGeneratedCase c : matching) {
                kinds.add(detectKind(c));
            }
            List<String> missing = REQUIRED_KINDS.stream()
                    .filter(k -> !kinds.contains(k))
                    .toList();
            if (!matching.isEmpty()) {
                coveredScreens++;
            } else {
                findings.add("Màn hình '" + screen.screenTitle() + "' không có test case nào truy vết tới");
            }
            perScreen.add(new ScreenCoverage(screen.featureToken(), screen.screenTitle(),
                    matching.size(), kinds, missing));
        }

        return new Report(usable.size(), traceable, screenList.size(), coveredScreens,
                List.copyOf(perScreen), List.copyOf(findings));
    }

    /**
     * Scenario kind inferred from the case's own name/description. Only the four kinds the pipeline
     * requires map to a bucket; anything else becomes {@code Other} and does not satisfy a slot.
     */
    static String detectKind(AiGeneratedCase c) {
        String text = ((c.getName() != null ? c.getName() : "") + " "
                + (c.getDescription() != null ? c.getDescription() : "")).toLowerCase(Locale.ROOT);
        if (text.matches(".*(happy|thanhcong|thanh cong|luongchinh|luong chinh|success).*")) {
            return "HappyPath";
        }
        if (text.matches(".*(negative|validation|hopl|hợp lệ|sai|invalid|empty|trong|required|batbuoc).*")) {
            return "NegativeValidation";
        }
        if (text.matches(".*(boundary|bva|bien|biên|gioihan|giới hạn|max|min|edge).*")) {
            return "BoundaryBVA";
        }
        if (text.matches(".*(business|rule|quyen|quyền|phanquyen|phân quyền|nghiepvu|nghiệp vụ|permission).*")) {
            return "BusinessRule";
        }
        return "Other";
    }

    private static boolean matchesScreen(AiGeneratedCase c, String token, String title) {
        if (!c.getScreenId().isBlank()) {
            String caseScreen = normalize(c.getScreenId());
            return caseScreen.equals(token) || caseScreen.contains(token) || token.contains(caseScreen);
        }
        // Older cases may not have screenId; fall back to the case name/description containing the title.
        String haystack = normalize(c.getName() + " " + c.getDescription());
        return !title.isBlank() && haystack.contains(title);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9à-ỹ]+", " ")
                .trim();
    }
}
