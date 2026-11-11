package com.studio.service;

import com.studio.model.AiGeneratedCase;

import java.util.ArrayList;
import java.util.Arrays;
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

            // Coverage may only be credited to cases that carry a full trace (test id, screen and
            // SRS clause). Otherwise an untraceable case would make a screen look covered.
            List<AiGeneratedCase> matching = usable.stream()
                    .filter(AiGeneratedCase::hasTraceability)
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
     *
     * <p>Matching is on whole tokens after diacritics are stripped: substring matching made
     * {@code "admin"} look like boundary value analysis ({@code min}) and {@code "trong"}
     * ("inside") look like an empty-field case.</p>
     */
    static String detectKind(AiGeneratedCase c) {
        String text = normalize((c.getName() != null ? c.getName() : "") + " "
                + (c.getDescription() != null ? c.getDescription() : ""));
        Set<String> tokens = tokenize(text);
        if (matchesKind(text, tokens, HAPPY_TERMS, HAPPY_PHRASES)) {
            return "HappyPath";
        }
        if (matchesKind(text, tokens, NEGATIVE_TERMS, NEGATIVE_PHRASES)) {
            return "NegativeValidation";
        }
        if (matchesKind(text, tokens, BOUNDARY_TERMS, BOUNDARY_PHRASES)) {
            return "BoundaryBVA";
        }
        if (matchesKind(text, tokens, BUSINESS_TERMS, BUSINESS_PHRASES)) {
            return "BusinessRule";
        }
        return "Other";
    }

    /** Terms that appear as a single normalised token. */
    private static final Set<String> HAPPY_TERMS = Set.of(
            "happy", "success", "thanhcong", "successful", "happycase");
    /** Multi-word phrases, matched on the normalised string so word order is preserved. */
    private static final List<String> HAPPY_PHRASES = List.of("happy path", "thanh cong", "luong chinh");

    private static final Set<String> NEGATIVE_TERMS = Set.of(
            "negative", "validation", "invalid", "empty", "required", "sai", "batbuoc");
    private static final List<String> NEGATIVE_PHRASES = List.of(
            "khong hop le", "hop le", "de trong", "bo trong", "khong nhap", "truong bat buoc");

    private static final Set<String> BOUNDARY_TERMS = Set.of(
            "boundary", "bva", "edge", "bien", "max", "min", "toida", "toithieu");
    private static final List<String> BOUNDARY_PHRASES = List.of(
            "gia tri bien", "gioi han", "toi da", "toi thieu", "boundary value");

    private static final Set<String> BUSINESS_TERMS = Set.of(
            "business", "rule", "permission", "quyen", "nghiepvu", "phanquyen");
    private static final List<String> BUSINESS_PHRASES = List.of(
            "business rule", "nghiep vu", "phan quyen", "quy tac nghiep vu");

    private static boolean matchesKind(String text, Set<String> tokens,
                                       Set<String> terms, List<String> phrases) {
        for (String term : terms) {
            if (tokens.contains(term)) {
                return true;
            }
        }
        for (String phrase : phrases) {
            if (containsPhrase(text, phrase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesScreen(AiGeneratedCase c, String token, String title) {
        String caseScreen = normalize(c.getScreenId());
        if (!caseScreen.isBlank() && !token.isBlank()) {
            return caseScreen.equals(token)
                    || containsPhrase(caseScreen, token)
                    || containsPhrase(token, caseScreen);
        }
        // Older cases may not have screenId; fall back to the title appearing in the text.
        String haystack = normalize(c.getName() + " " + c.getDescription());
        return !title.isBlank() && containsPhrase(haystack, title);
    }

    /** Whole-token containment, so "min" does not match "admin". */
    private static boolean containsPhrase(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isBlank()) {
            return false;
        }
        return (" " + haystack + " ").contains(" " + needle + " ");
    }

    /** Lower-cases, strips diacritics and collapses every non-alphanumeric run to one space. */
    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private static Set<String> tokenize(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return Set.of();
        }
        // Set.of(...) rejects repeated values, and model-written descriptions repeat words all the
        // time ("xác định ... xác định"), so dedupe through Set.copyOf instead of the varargs factory.
        return Set.copyOf(Arrays.asList(normalized.split(" ")));
    }
}
