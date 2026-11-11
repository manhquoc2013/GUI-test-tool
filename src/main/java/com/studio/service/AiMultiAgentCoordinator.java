package com.studio.service;

import com.studio.model.AiGeneratedCase;
import com.studio.model.AiSkill;
import com.studio.model.Assertion;
import com.studio.model.TestDataItem;
import com.studio.model.TestStep;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coordinates the 4-Agent Verification & Self-Healing Pipeline inspired by
 * ECC (Spec Miner, GAN Generator/Evaluator, Loop Operator) and Agency Agents
 * (Reality Checker, Test Automation Engineer, Workflow Optimizer):
 *
 * <ol>
 *   <li><b>Agent 1 — Spec Miner &amp; Analyst</b>: Decomposes requirements, documents, and DOM
 *       into functional domain, target Group name ({@code suggestedGroup}), target Suite name
 *       ({@code suggestedSuite}), preconditions, and BVA/EP test matrix.</li>
 *   <li><b>Agent 2 — Test Architect &amp; Playwright Engineer</b>: Generates structured ISTQB
 *       test cases ({@code TC01_<Feature>_<Scenario>}) and deterministic Playwright scripts.</li>
 *   <li><b>Agent 3 — Reality Checker &amp; QA Reviewer</b>: Audits the generated output across
 *       5 strict quality gates (Naming Accuracy, Scenario Coverage, Step Completeness,
 *       Assertion/Data Integrity, and Playwright Script Determinism).</li>
 *   <li><b>Agent 4 — Loop Engineer</b>: Executes autonomous self-healing and refinement loops
 *       to fix any quality deficits before delivering the final result to the user.</li>
 * </ol>
 */
public class AiMultiAgentCoordinator {

    private static final Set<String> GENERIC_NAMES = Set.of(
            "ai-tests", "ai_tests", "aitests",
            "ai-generated-suite", "ai_generated_suite", "aigeneratedsuite",
            "default", "default-group", "default-suite", "group", "suite",
            "test", "tests", "testcase", "testcases", "test-suite", "test_suite",
            "cuộc trò chuyện mới", "cuoc tro chuyen moi", "cuoc_tro_chuyen_moi",
            "new chat", "new-chat", "general", "common", "null", "none", "n/a", "chưa đặt tên"
    );

    private static final Pattern CASE_FEATURE_PATTERN = Pattern.compile(
            "^TC\\d*[_\\-]([A-Za-zÀ-ỹ0-9]+)[_\\-].*", Pattern.CASE_INSENSITIVE);

    private static final Pattern VIETNAMESE_FEATURE_PHRASE = Pattern.compile(
            "(?:chức năng|tính năng|màn hình|trang|phân hệ|module|luồng|quy trình|form|biểu mẫu)\\s+([^,.;:\\n\\r()\\[\\]\"'?!]{3,45})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern CODE_FENCE_STRIP_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern NON_WORD_OR_UNDERSCORE = Pattern.compile("[^A-Za-z0-9_]+");
    private static final Pattern MULTI_UNDERSCORE = Pattern.compile("_+");
    private static final Pattern EDGE_UNDERSCORE = Pattern.compile("^_+|_+$");

    private static final Pattern YAML_FRONTMATTER_PATTERN = Pattern.compile("(?s)^---\\r?\\n.*?\\r?\\n---\\r?\\n*");
    private static final Pattern PDF_PAGE_HEADING_PATTERN = Pattern.compile("(?m)^#{1,4}\\s+(?:Trang|Page)\\s+\\d+\\s*$\\r?\\n?", Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUESTED_SCREEN_COUNT_PATTERN = Pattern.compile(
            "(?:cho|với|trên|của|gồm|tạo|sinh|viết|kiểm\\s+thử|test\\s+scenario[s]?\\s+cho|test\\s+case[s]?\\s+cho)?\\s*(\\d{1,2})\\s*(?:màn\\s+hình|man\\s+hinh|screen[s]?|trang\\s+giao\\s+diện|phân\\s+hệ|module[s]?)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern HEADING_LINE_PATTERN = Pattern.compile(
            "(?m)^(?:#{1,4}\\s+|\\*\\*(?=\\d|\\p{L}))([^\\r\\n*]{3,110})(?:\\*\\*)?\\s*$");
    private static final Pattern TABLE_FIELD_ROW_PATTERN = Pattern.compile(
            "(?m)^\\|\\s*(?:\\d+\\s*\\|\\s*)?([^|\\r\\n]{2,45})\\s*\\|([^|\\r\\n]*)\\|");
    private static final Pattern BULLET_FIELD_PATTERN = Pattern.compile(
            "(?m)^\\s*[-*•+]\\s*(?:\\*\\*)?(?:Trường|Ô\\s+nhập|Nút|Button|Field|Input|Dropdown|Combobox|Checkbox| Cột)?\\s*([^:\\r\\n*]{2,40})(?:\\*\\*)?\\s*[:\\-–]([^\\r\\n]{3,120})$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern URL_EXTRACT_PATTERN = Pattern.compile("(https?://[^\\s)\"'<>]+)");

    /**
     * Represents a single functional Screen or Module mined by Agent 1 (Spec Miner) from an SRS document or prompt.
     */
    public record ScreenSpecSection(
            int screenIndex,
            String screenTitle,
            String featureToken,
            String sectionContent,
            List<String> extractedFields,
            List<String> extractedRules,
            String targetUrl
    ) {}

    /**
     * Structured report produced by the Reality Checker &amp; Loop Engineer agents.
     */
    public static class AgentReviewReport {
        private String suggestedGroup;
        private String suggestedSuite;
        private int initialQualityScore;
        private int finalQualityScore;
        private int loopIterations;
        private List<String> agentStagesExecuted = new ArrayList<>();
        private List<String> improvementsApplied = new ArrayList<>();
        /** Deficits found by the independent judge; each one is visible to the user instead of silently filled. */
        private List<String> gaps = new ArrayList<>();
        private List<String> finalFindings = new ArrayList<>();
        private AiCoverageAnalyzer.Report coverage;

        public String getSuggestedGroup() {
            return suggestedGroup;
        }

        public void setSuggestedGroup(String suggestedGroup) {
            this.suggestedGroup = suggestedGroup;
        }

        public String getSuggestedSuite() {
            return suggestedSuite;
        }

        public void setSuggestedSuite(String suggestedSuite) {
            this.suggestedSuite = suggestedSuite;
        }

        public int getInitialQualityScore() {
            return initialQualityScore;
        }

        public void setInitialQualityScore(int initialQualityScore) {
            this.initialQualityScore = initialQualityScore;
        }

        public int getFinalQualityScore() {
            return finalQualityScore;
        }

        public void setFinalQualityScore(int finalQualityScore) {
            this.finalQualityScore = finalQualityScore;
        }

        public int getLoopIterations() {
            return loopIterations;
        }

        public void setLoopIterations(int loopIterations) {
            this.loopIterations = loopIterations;
        }

        public List<String> getAgentStagesExecuted() {
            return agentStagesExecuted;
        }

        public void setAgentStagesExecuted(List<String> agentStagesExecuted) {
            this.agentStagesExecuted = agentStagesExecuted != null ? agentStagesExecuted : new ArrayList<>();
        }

        public List<String> getImprovementsApplied() {
            return improvementsApplied;
        }

        public void setImprovementsApplied(List<String> improvementsApplied) {
            this.improvementsApplied = improvementsApplied != null ? improvementsApplied : new ArrayList<>();
        }

        public List<String> getGaps() {
            return gaps;
        }

        public void addGap(String gap) {
            if (gap != null && !gap.isBlank() && !gaps.contains(gap)) {
                gaps.add(gap);
            }
        }

        public List<String> getFinalFindings() {
            return finalFindings;
        }

        public void setFinalFindings(List<String> finalFindings) {
            this.finalFindings = finalFindings != null ? new ArrayList<>(finalFindings) : new ArrayList<>();
        }

        public AiCoverageAnalyzer.Report getCoverage() {
            return coverage;
        }

        public void setCoverage(AiCoverageAnalyzer.Report coverage) {
            this.coverage = coverage;
        }
    }

    /**
     * Agent 1 (Spec Miner): Analyzes the user request, active skill, and attached documents
     * to build the multi-agent orchestration directive injected into the system prompt.
     */
    public String buildMultiAgentPipelineDirective(String userPrompt,
                                                   AiSkill resolvedSkill,
                                                   List<DocumentParserService.ParsedDocument> attachedDocs,
                                                   boolean generateScript) {
        String preInferredGroup = resolveFunctionalGroupName(userPrompt, attachedDocs, List.of(), null);
        String preInferredSuite = resolveFunctionalSuiteName(userPrompt, null, attachedDocs, List.of(), null);
        List<ScreenSpecSection> screens = extractScreenSections(userPrompt, attachedDocs);

        StringBuilder screenInventoryDirective = new StringBuilder();
        if (screens.size() >= 2) {
            int minTotalCases = screens.size() * 4;
            screenInventoryDirective.append("\n   - PHÁT HIỆN ĐẶC TẢ ĐA MÀN HÌNH (").append(screens.size())
                    .append(" màn hình/module chức năng). BẮT BUỘC sinh tối thiểu 4 - 5 Test Cases CHO MỖI MÀN HÌNH (tổng cộng tối thiểu ")
                    .append(minTotalCases).append(" Test Cases), TUYỆT ĐỐI KHÔNG chỉ sinh 1 Test Case đại diện cho mỗi màn hình:\n");
            for (ScreenSpecSection s : screens) {
                screenInventoryDirective.append("     + Màn hình ").append(s.screenIndex())
                        .append(" [").append(s.featureToken()).append("]: ").append(s.screenTitle())
                        .append(" -> Bắt buộc đủ: (1) HappyPath [P0], (2) NegativeValidation [P1], (3) BoundaryBVA [P1], (4) BusinessRule/PhanQuyen [P2].\n");
            }
        }

        return """
                === QUY TRÌNH ĐA TÁC NHÂN (MULTI-AGENT VERIFICATION & LOOP PIPELINE) ===
                Bạn vận hành điều phối 4 Tác nhân AI chuyên biệt (Spec Miner -> Test Architect -> Reality Checker -> Loop Engineer):
                1. [Agent 1 - Spec Miner (Phân tích đặc tả & Định danh Chức năng)]:
                   - Bóc tách yêu cầu thành các module chức năng, điều kiện tiên quyết (Preconditions), phân vùng tương đương (EP) và giá trị biên (BVA).
                   - BẮT BUỘC đặt tên `"suggestedGroup"` (Nhóm chức năng/Phân hệ) và `"suggestedSuite"` (Bộ kịch bản kiểm thử) ĐÚNG THEO TÊN CHỨC NĂNG VÀ TEST CASE.
                   - Gợi ý tham chiếu từ ngữ cảnh hiện tại: suggestedGroup = "%s", suggestedSuite = "%s" (Hãy tinh chỉnh chính xác hơn theo đúng nghiệp vụ thực tế của bộ Test Case).
                   - TUYỆT ĐỐI KHÔNG đặt tên chung chung như "AI-Tests", "AI-Generated-Suite", "Default", "Test Suite".%s
                2. [Agent 2 - Test Architect & Script Engineer (Thiết kế Test Case & Kịch bản)]:
                   - Áp dụng kỹ năng chuyên sâu [%s] để thiết kế đầy đủ các kịch bản cho TỪNG màn hình/chức năng: Happy Path (P0), Negative/Validation (P1), Boundary/Edge Cases (P1), Business Rules & Phân quyền (P2).
                   - Tên mỗi Test Case PHẢI tuân thủ chuẩn: `TC<SốThứTự>_<TênMànHìnhHoặcChứcNăng>_<KịchBảnCụThể>` (Ví dụ: `TC01_ManHinhDangNhap_ThanhCong`, `TC02_ManHinhDangNhap_SaiMatKhau`).
                   - Mỗi Test Case PHẢI có đầy đủ `testSteps` (ít nhất 4 bước rõ ràng bám sát các trường dữ liệu trong SRS), `testDataSets` (bộ dữ liệu biên/tương đương) và `assertions`.
                   - %s
                3. [Agent 3 - Reality Checker (Thẩm định chất lượng khắt khe)]:
                   - Tự rà soát chéo toàn bộ kết quả theo 5 cổng chất lượng: (1) Đặt tên Nhóm/Suite/Test Case chuẩn chức năng, (2) Đủ độ phủ tối thiểu 3-5 kịch bản/màn hình (âm/dương/biên/nghiệp vụ) cho TẤT CẢ các màn hình, (3) Đủ các bước thực thi & kết quả kỳ vọng, (4) Assertions chính xác, (5) Mã kịch bản tất định không dùng sleep cứng.
                4. [Agent 4 - Loop Engineer (Tự hoàn thiện trước khi xuất)]:
                   - Tự động bổ sung và sửa mọi điểm khuyết thiếu phát hiện ở bước 3 và ghi tóm tắt đánh giá vào trường `"agentReview"`.
                """.formatted(
                preInferredGroup,
                preInferredSuite,
                screenInventoryDirective.toString(),
                resolvedSkill != null ? resolvedSkill.getDisplayName() : AiSkill.PLAYWRIGHT_E2E.getDisplayName(),
                AiAmbiguityAnalyzer.isBothScenariosAndScriptsRequest(userPrompt)
                        ? "CHẾ ĐỘ TẠO CẢ HAI (TEST SCENARIO QA + TEST SCRIPT PLAYWRIGHT): Thiết kế đầy đủ bộ kịch bản kiểm thử (Test Scenarios) chuẩn QA/ISTQB và VIẾT MÃ PLAYWRIGHT JAVA TỰ ĐỘNG HÓA hoàn chỉnh trong `scriptCode`. Sẵn sàng xuất CẢ HAI (tài liệu kịch bản Excel/Markdown + mã nguồn Java .zip)."
                        : (generateScript
                                ? "CHẾ ĐỘ SINH TEST CASE & TEST SCRIPT TỰ ĐỘNG HÓA: Sinh đầy đủ các test case cụ thể theo kịch bản và VIẾT MÃ PLAYWRIGHT JAVA TỰ ĐỘNG HÓA hoàn chỉnh trong `scriptCode`, tuyệt đối không dùng Thread.sleep/waitForTimeout."
                                : "CHẾ ĐỘ TẠO TEST SCENARIO / KỊCH BẢN KIỂM THỬ: Chỉ tạo kịch bản, để trống `scriptCode: \"\"`. Tập trung tối đa vào việc thiết kế bộ kịch bản kiểm thử (Test Scenarios) chuẩn QA/ISTQB, đặc tả luồng người dùng, điều kiện tiên quyết (preconditions), các bước thực hiện tuần tự (testSteps), bộ dữ liệu kiểm thử (testDataSets) và tiêu chí nghiệm thu (assertions). Sẵn sàng xuất tài liệu kịch bản chuẩn (Excel .xlsx, Markdown, JSON).")
        );
    }

    /**
     * Builds a focused prompt for a specific batch of screens during Multi-Batch Orchestration (Agent 2).
     */
    public String buildScreenBatchPrompt(String baseUserPrompt,
                                         List<ScreenSpecSection> batchScreens,
                                         int batchIndex,
                                         int totalBatches,
                                         int totalScreens,
                                         int minCasesPerScreen) {
        StringBuilder sb = new StringBuilder();
        sb.append(baseUserPrompt != null ? baseUserPrompt.trim() : "Thiết kế bộ kịch bản kiểm thử toàn diện theo tài liệu SRS.").append("\n\n");
        sb.append("=== YÊU CẦU ĐIỀU PHỐI LÔ MÀN HÌNH (MULTI-BATCH ORCHESTRATION - LÔ ")
                .append(batchIndex).append("/").append(totalBatches).append(") ===\n");
        sb.append("Trong lượt này, hãy tập trung thiết kế đầy đủ và chuyên sâu bộ Test Case cho **")
                .append(batchScreens.size()).append(" màn hình** dưới đây (thuộc tổng số ").append(totalScreens).append(" màn hình của hệ thống).\n");
        sb.append("⚠️ QUY TẮC BẮT BUỘC: Với MỖI màn hình trong lô này, bạn PHẢI sinh tối thiểu **")
                .append(minCasesPerScreen).append(" Test Cases** (tổng cộng tối thiểu **")
                .append(batchScreens.size() * minCasesPerScreen).append(" Test Cases** trong JSON phản hồi này), bao gồm:\n");
        sb.append("  1. `TCxx_<ScreenToken>_LuongChinh_HappyPath` [P0]: Luồng thành công chuẩn với đầy đủ các trường dữ liệu trên màn hình.\n");
        sb.append("  2. `TCxx_<ScreenToken>_KiemTraHopLe_NegativeValidation` [P1]: Bỏ trống trường bắt buộc, nhập sai định dạng, kiểm tra thông báo lỗi.\n");
        sb.append("  3. `TCxx_<ScreenToken>_GiaTriBien_BoundaryBVA` [P1]: Kiểm thử giá trị biên (Min, Max, Max+1, ký tự đặc biệt/Unicode).\n");
        sb.append("  4. `TCxx_<ScreenToken>_QuyTacNghiepVu_PhanQuyen` [P2]: Kiểm thử quy tắc nghiệp vụ đặc thù, dữ liệu trùng lặp, phân quyền hoặc luồng phụ.\n\n");

        for (ScreenSpecSection screen : batchScreens) {
            sb.append("--- ĐẶC TẢ CHI TIẾT MÀN HÌNH #").append(screen.screenIndex())
                    .append(": ").append(screen.screenTitle())
                    .append(" (Token định danh: `").append(screen.featureToken()).append("`) ---\n");
            if (!screen.extractedFields().isEmpty()) {
                sb.append("• Các trường / thành phần giao diện chính: ")
                        .append(String.join(", ", screen.extractedFields())).append("\n");
            }
            if (!screen.extractedRules().isEmpty()) {
                sb.append("• Quy tắc nghiệp vụ & Validation trọng tâm:\n");
                for (String rule : screen.extractedRules()) {
                    sb.append("  - ").append(rule).append("\n");
                }
            }
            String content = screen.sectionContent() != null ? screen.sectionContent().trim() : "";
            if (!content.isEmpty()) {
                int cap = Math.min(content.length(), 14000);
                sb.append("• Nội dung đặc tả SRS của màn hình:\n```\n")
                        .append(content, 0, cap)
                        .append("\n```\n\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * Agent 3 (Reality Checker) + Agent 4 (Loop Engineer):
     * Audits a {@link AiTestGeneratorService.GenerationResult}, evaluates its quality score,
     * and autonomously repairs/enriches Group/Suite functional names, test case names,
     * steps, assertions, test data sets, and Playwright scripts.
     */
    public AgentReviewReport reviewAndHealResult(AiTestGeneratorService.GenerationResult result,
                                                 String userPrompt,
                                                 List<DocumentParserService.ParsedDocument> attachedDocs,
                                                 boolean generateScript,
                                                 AiTestGeneratorService generatorService,
                                                 Consumer<String> progressListener) {
        AgentReviewReport report = new AgentReviewReport();
        report.getAgentStagesExecuted().add("SpecMiner: Phân tích đặc tả & trích xuất phân hệ chức năng");
        report.getAgentStagesExecuted().add("TestArchitect: Thiết kế ma trận Test Case & Kịch bản");
        report.getAgentStagesExecuted().add("RealityChecker: Thẩm định 5 cổng chất lượng (5-Gate QA Audit)");

        if (result == null) {
            return report;
        }

        if (progressListener != null) {
            progressListener.accept("🧐 [Agent 3/4 - Reality Checker] Đang thẩm định chất lượng Test Case & định danh Nhóm/Suite...");
        }

        List<AiGeneratedCase> cases = result.getCases();
        // A batch lost to a non-JSON reply means part of the requested suite is simply missing; the
        // Reality Checker must see that, or a short suite scores as a complete one.
        if (result.hasLostBatches()) {
            report.getAgentStagesExecuted().add("TestArchitect: " + result.getLostBatches().size()
                    + " lô màn hình mất sau tự hiệu chỉnh");
        }
        int initialScore = evaluateQualityScore(result, generateScript);
        report.setInitialQualityScore(initialScore);

        List<String> improvements = new ArrayList<>();
        int loopIterations = 0;

        // Gate 1: Functional Group & Suite Naming Resolution (Always enforced for GENERATE_TESTS or non-empty cases)
        String rawGroup = result.getSuggestedGroup();
        String rawSuite = result.getSuggestedSuite();

        String resolvedGroup = resolveFunctionalGroupName(userPrompt, attachedDocs, cases, rawGroup);
        String resolvedSuite = resolveFunctionalSuiteName(userPrompt, null, attachedDocs, cases, rawSuite);

        if (isGenericOrBlank(rawGroup) || !resolvedGroup.equals(rawGroup)) {
            result.setSuggestedGroup(resolvedGroup);
            improvements.add("Đặt tên Nhóm chuẩn theo chức năng: '" + resolvedGroup + "'");
        } else {
            result.setSuggestedGroup(resolvedGroup);
        }

        if (isGenericOrBlank(rawSuite) || !resolvedSuite.equals(rawSuite)) {
            result.setSuggestedSuite(resolvedSuite);
            improvements.add("Đặt tên Suite chuẩn theo kịch bản kiểm thử: '" + resolvedSuite + "'");
        } else {
            result.setSuggestedSuite(resolvedSuite);
        }

        report.setSuggestedGroup(resolvedGroup);
        report.setSuggestedSuite(resolvedSuite);

        // If there are generated test cases, run Agent 4 (Loop Engineer) self-healing on each case
        if (cases != null && !cases.isEmpty()) {
            if (progressListener != null) {
                progressListener.accept("🔄 [Agent 4/4 - Loop Engineer] Đang tự động tối ưu hóa & hoàn thiện bộ Test Case...");
            }
            loopIterations = 1;
            report.getAgentStagesExecuted().add("LoopEngineer: Tự động chuẩn hóa & bù đắp lỗ hổng chất lượng");

            String featurePrefix = extractCleanFeatureToken(resolvedGroup, resolvedSuite, userPrompt);

            // Coverage is measured, not manufactured: missing scenarios are reported as gaps below.
            // The previous template-based expansion invented cases the SRS never asked for, which made
            // every suite look complete while adding unverified assertions.
            for (int idx = 0; idx < cases.size(); idx++) {
                AiGeneratedCase tc = cases.get(idx);
                int caseNum = idx + 1;

                // 1. Heal generic or malformed Test Case Name -> TC01_<Feature>_<Scenario>
                String origName = tc.getName();
                if (origName == null || origName.isBlank()
                        || origName.matches("(?i)^(Case|Test|TestCase|KichBan)[_\\-]?\\d*$")
                        || origName.startsWith("Case_Recovered_")) {
                    String scenarioSuffix = inferScenarioSuffix(tc.getDescription(), caseNum);
                    String healedName = String.format("TC%02d_%s_%s", caseNum, featurePrefix, scenarioSuffix);
                    tc.setName(healedName);
                    improvements.add("Chuẩn hóa tên Test Case #" + caseNum + " -> " + healedName);
                } else if (!origName.matches("(?i)^TC\\d+.*")) {
                    String cleanOrig = sanitizeIdentifier(origName);
                    if (!cleanOrig.toLowerCase(Locale.ROOT).contains(featurePrefix.toLowerCase(Locale.ROOT))
                            && cleanOrig.length() < 18) {
                        String healedName = String.format("TC%02d_%s_%s", caseNum, featurePrefix, cleanOrig);
                        tc.setName(healedName);
                        improvements.add("Bổ sung tiền tố chuẩn cho Test Case #" + caseNum + " -> " + healedName);
                    } else {
                        String healedName = String.format("TC%02d_%s", caseNum, cleanOrig);
                        tc.setName(healedName);
                    }
                } else {
                    // Re-sequence TC prefix number so multi-batch / multi-screen cases are numbered TC01..TCNN in order
                    String rest = origName.replaceFirst("(?i)^TC\\d+[_\\-]?", "");
                    if (!rest.isBlank()) {
                        tc.setName(String.format("TC%02d_%s", caseNum, sanitizeIdentifier(rest)));
                    }
                }

                // 1b. Keep the traceability id in step with the (possibly renamed) case. bindTraceability
                // runs before this pass, so a renamed case used to keep pointing at its old generic name,
                // and every report/manifest referenced a case that no longer exists.
                String healedName = tc.getName();
                String testId = tc.getTestId();
                if (healedName != null && !healedName.isBlank()
                        && (testId == null || testId.isBlank() || testId.equals(origName))) {
                    tc.setTestId(healedName);
                }

                // 2. Heal Priority if missing or invalid
                String prio = tc.getPriority();
                if (prio == null || !prio.matches("P[0-3]")) {
                    String inferredPrio = (caseNum == 1) ? "P0" : (caseNum <= 3 ? "P1" : "P2");
                    tc.setPriority(inferredPrio);
                }

                // 3. Heal Description if missing
                if (tc.getDescription() == null || tc.getDescription().isBlank()) {
                    tc.setDescription("[Điều kiện tiên quyết: Hệ thống sẵn sàng] Kiểm thử kịch bản "
                            + tc.getName().replace('_', ' '));
                }

                // 4. Structural normalisation only. Missing steps are a reported gap, not a reason to
                // invent four generic steps the model never wrote.
                List<TestStep> steps = tc.getTestSteps();
                if (steps == null || steps.size() < 3) {
                    report.addGap(tc.getName() + ": chỉ có " + (steps == null ? 0 : steps.size())
                            + " bước thực thi (cần >= 3)");
                } else {
                    for (int s = 0; s < steps.size(); s++) {
                        TestStep step = steps.get(s);
                        if (step.getStepNumber() <= 0) {
                            step.setStepNumber(s + 1);
                        }
                    }
                }

                // 5. Normalise assertion types/status only; missing assertions stay missing so the
                // quality score reflects the real deliverable.
                List<Assertion> assertions = tc.getAssertions();
                if (assertions == null || assertions.isEmpty()) {
                    report.addGap(tc.getName() + ": không có assertion nào");
                } else {
                    for (Assertion a : assertions) {
                        a.setType(AiTestGeneratorService.normalizeAssertionType(a.getType()));
                        if (a.getStatus() == null || a.getStatus().isBlank()) {
                            a.setStatus("ACTIVE");
                        }
                    }
                    long active = assertions.stream()
                            .filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus()))
                            .count();
                    if (active < 2) {
                        report.addGap(tc.getName() + ": chỉ có " + active + " assertion ACTIVE (cần >= 2)");
                    }
                }

                // 6. Test data is the model's BVA/EP work; an empty set is a gap to show, not to fill.
                List<TestDataItem> dataSets = tc.getTestDataSets();
                if (dataSets == null || dataSets.isEmpty()) {
                    report.addGap(tc.getName() + ": chưa có bộ dữ liệu kiểm thử");
                }

                // 7. Playwright script: clean what the model produced, never synthesise a replacement.
                if (!generateScript) {
                    if (tc.getScriptCode() != null && !tc.getScriptCode().isBlank()) {
                        tc.setScriptCode("");
                        improvements.add("Loại bỏ script thừa khi ở chế độ chỉ tạo Test Case cho " + tc.getName());
                    }
                } else {
                    String code = tc.getScriptCode();
                    if (code == null || code.isBlank()) {
                        report.addGap(tc.getName() + ": thiếu script Playwright");
                    } else if (generatorService != null) {
                        boolean hadSleep = code.contains("Thread.sleep") || code.contains("waitForTimeout");
                        String fixedCode = generatorService.validateAndFixScript(code, tc.getName(), tc.getStartUrl());
                        tc.setScriptCode(fixedCode);
                        if (hadSleep) {
                            improvements.add("Loại bỏ hard sleep (Thread.sleep/waitForTimeout) trong script của " + tc.getName());
                        }
                    }
                }
            }
        }

        AiQualityJudge.Verdict verdict = AiQualityJudge.judge(cases, generateScript);
        report.setFinalQualityScore(verdict.score());
        report.setFinalFindings(verdict.findings());
        report.setLoopIterations(loopIterations);
        report.setImprovementsApplied(improvements);
        report.setCoverage(AiCoverageAnalyzer.analyze(cases, extractScreenSections(userPrompt, attachedDocs)));

        return report;
    }

    /**
     * Independent, evidence-only score (see {@link AiQualityJudge}). The producer of the cases cannot
     * award itself points for content it never wrote: gaps are reported by the judge instead.
     */
    public int evaluateQualityScore(AiTestGeneratorService.GenerationResult result, boolean generateScript) {
        if (result == null) return 0;
        return AiQualityJudge.judge(result.getCases(), generateScript).score();
    }

    /**
     * Links each case to the screen section it was actually generated from.
     *
     * <p>The evidence is the case's own identity (name/description) and the batch it came from: every
     * multi-screen batch only contains the sections listed in {@code screens}. Nothing is invented —
     * a case that cannot be matched stays untraceable and lowers {@link AiCoverageAnalyzer} coverage.</p>
     *
     * @return number of cases that ended up with testId + screenId + requirementRef
     */
    public static int bindTraceability(List<AiGeneratedCase> cases,
                                       List<ScreenSpecSection> screens) {
        if (cases == null || cases.isEmpty() || screens == null || screens.isEmpty()) {
            return 0;
        }
        int traced = 0;
        for (AiGeneratedCase c : cases) {
            if (c == null) continue;
            if (c.getTestId().isBlank() && c.getName() != null && !c.getName().isBlank()) {
                c.setTestId(c.getName());
            }
            ScreenSpecSection matched = findScreenForCase(c, screens);
            if (matched != null) {
                if (c.getScreenId().isBlank()) {
                    c.setScreenId(matched.featureToken());
                }
                if (c.getRequirementRef().isBlank()) {
                    c.setRequirementRef("SRS: " + matched.screenTitle());
                }
            }
            if (c.hasTraceability()) {
                traced++;
            }
        }
        return traced;
    }

    /**
     * The screens of {@code screens} that no case can be traced to. Used after a truncated reply: the
     * cases that survived cover some screens, and only the untouched ones are worth asking for again.
     */
    public static List<ScreenSpecSection> uncoveredScreens(List<AiGeneratedCase> cases,
                                                           List<ScreenSpecSection> screens) {
        if (screens == null || screens.isEmpty()) {
            return List.of();
        }
        List<ScreenSpecSection> uncovered = new java.util.ArrayList<>();
        for (ScreenSpecSection screen : screens) {
            boolean covered = false;
            if (cases != null) {
                for (AiGeneratedCase c : cases) {
                    if (c != null && findScreenForCase(c, List.of(screen)) != null) {
                        covered = true;
                        break;
                    }
                }
            }
            if (!covered) {
                uncovered.add(screen);
            }
        }
        return uncovered;
    }

    private static ScreenSpecSection findScreenForCase(AiGeneratedCase c,
                                                       List<ScreenSpecSection> screens) {
        String declared = normalizeToken(c.getScreenId());
        String haystack = normalizeToken(c.getName() + " " + c.getDescription() + " " + c.getRequirementRef());
        ScreenSpecSection best = null;
        int bestLength = 0;
        for (ScreenSpecSection screen : screens) {
            String token = normalizeToken(screen.featureToken());
            String title = normalizeToken(screen.screenTitle());
            boolean matched = (!declared.isBlank() && (declared.equals(token) || token.contains(declared)))
                    || (!token.isBlank() && haystack.contains(token))
                    || (!title.isBlank() && title.length() >= 4 && haystack.contains(title));
            if (!matched) continue;
            int weight = Math.max(token.length(), title.length());
            if (weight > bestLength) {
                best = screen;
                bestLength = weight;
            }
        }
        return best;
    }

    private static String normalizeToken(String value) {
        if (value == null) {
            return "";
        }
        return removeVietnameseAccents(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    /**
     * Resolves an accurate functional Group name based on:
     * 1. AI's suggestedGroup (if non-generic)
     * 2. Functional module extracted from generated test case names (e.g. TC01_QuanLyDonHang_...)
     * 3. Semantic functional domain from user prompt or attached document filenames/content
     */
    public static String resolveFunctionalGroupName(String prompt,
                                                    List<DocumentParserService.ParsedDocument> attachedDocs,
                                                    List<AiGeneratedCase> cases,
                                                    String aiSuggestedGroup) {
        // 1. Use AI suggestedGroup if valid and non-generic
        if (!isGenericOrBlank(aiSuggestedGroup)) {
            String cleaned = sanitizeGroupOrSuiteName(aiSuggestedGroup);
            if (!isGenericOrBlank(cleaned)) {
                return cleaned;
            }
        }

        // 2. Check functional domain keywords from prompt + case descriptions + doc names
        String combinedContext = buildCombinedText(prompt, attachedDocs, cases);
        DomainMapping domain = detectFunctionalDomain(combinedContext);
        if (domain != null) {
            return domain.groupName;
        }

        // 3. Extract feature module from test case names (e.g. TC01_<Feature>_<Scenario>)
        String caseFeature = extractFeatureFromCaseNames(cases);
        if (caseFeature != null && !caseFeature.isBlank()) {
            if (caseFeature.toLowerCase(Locale.ROOT).startsWith("quanly")
                    || caseFeature.toLowerCase(Locale.ROOT).startsWith("kiemthu")
                    || caseFeature.toLowerCase(Locale.ROOT).startsWith("phanhe")) {
                return sanitizeGroupOrSuiteName(caseFeature);
            }
            return sanitizeGroupOrSuiteName("ChucNang_" + caseFeature);
        }

        // 4. Extract explicit Vietnamese functional phrase ("chức năng ...", "màn hình ...", "quản lý ...")
        String phrase = extractVietnameseFunctionalPhrase(prompt);
        if (phrase != null && !phrase.isBlank()) {
            String pascal = toAsciiPascalCase(phrase);
            if (!pascal.isBlank() && pascal.length() >= 3) {
                return pascal.startsWith("QuanLy") ? pascal : "ChucNang_" + pascal;
            }
        }

        // 5. Check attached document file name
        if (attachedDocs != null && !attachedDocs.isEmpty()) {
            for (DocumentParserService.ParsedDocument doc : attachedDocs) {
                if (doc.getFileName() != null && !doc.getFileName().isBlank()) {
                    String baseName = doc.getFileName().replaceFirst("\\.[^.]+$", "");
                    String pascal = toAsciiPascalCase(baseName);
                    if (!pascal.isBlank() && pascal.length() >= 3 && !isGenericOrBlank(pascal)) {
                        return "PhanHe_" + pascal;
                    }
                }
            }
        }

        // 6. Fallback from meaningful words in prompt
        String promptSlug = extractKeywordsPascalCase(prompt, 4);
        if (!promptSlug.isBlank() && !isGenericOrBlank(promptSlug)) {
            return "ChucNang_" + promptSlug;
        }

        return "KiemThuChucNang";
    }

    /**
     * Resolves an accurate functional Suite name based on:
     * 1. AI's suggestedSuite (if non-generic)
     * 2. Specific feature extracted from generated test case names (e.g. TC01_DangNhap_... -> KiemThu_DangNhap)
     * 3. Explicit Vietnamese functional phrase from prompt ("chức năng ..." -> KiemThu_<Phrase>)
     * 4. Semantic functional domain mapping
     * 5. Session title (if non-generic)
     */
    public static String resolveFunctionalSuiteName(String prompt,
                                                    String sessionTitle,
                                                    List<DocumentParserService.ParsedDocument> attachedDocs,
                                                    List<AiGeneratedCase> cases,
                                                    String aiSuggestedSuite) {
        // 1. Use AI suggestedSuite if valid and non-generic
        if (!isGenericOrBlank(aiSuggestedSuite)) {
            String cleaned = sanitizeGroupOrSuiteName(aiSuggestedSuite);
            if (!isGenericOrBlank(cleaned)) {
                return cleaned;
            }
        }

        // 2. Extract specific feature from test case names (e.g. TC01_DangNhap_ThanhCong -> KiemThu_DangNhap)
        String caseFeature = extractFeatureFromCaseNames(cases);
        if (caseFeature != null && !caseFeature.isBlank() && !isGenericOrBlank(caseFeature)) {
            if (caseFeature.toLowerCase(Locale.ROOT).startsWith("kiemthu")
                    || caseFeature.toLowerCase(Locale.ROOT).endsWith("suite")) {
                return sanitizeGroupOrSuiteName(caseFeature);
            }
            return sanitizeGroupOrSuiteName("KiemThu_" + caseFeature);
        }

        // 3. Extract explicit Vietnamese functional phrase ("chức năng ...", "màn hình ...")
        String phrase = extractVietnameseFunctionalPhrase(prompt);
        if (phrase != null && !phrase.isBlank()) {
            String pascal = toAsciiPascalCase(phrase);
            if (!pascal.isBlank() && pascal.length() >= 3) {
                return pascal.startsWith("KiemThu") ? pascal : "KiemThu_" + pascal;
            }
        }

        // 4. Semantic functional domain from prompt + cases + docs
        String combinedContext = buildCombinedText(prompt, attachedDocs, cases);
        DomainMapping domain = detectFunctionalDomain(combinedContext);
        if (domain != null) {
            return domain.suiteName;
        }

        // 5. Attached document file name
        if (attachedDocs != null && !attachedDocs.isEmpty()) {
            for (DocumentParserService.ParsedDocument doc : attachedDocs) {
                if (doc.getFileName() != null && !doc.getFileName().isBlank()) {
                    String baseName = doc.getFileName().replaceFirst("\\.[^.]+$", "");
                    String pascal = toAsciiPascalCase(baseName);
                    if (!pascal.isBlank() && pascal.length() >= 3 && !isGenericOrBlank(pascal)) {
                        return "KiemThu_" + pascal;
                    }
                }
            }
        }

        // 6. Session title if meaningful
        if (!isGenericOrBlank(sessionTitle)) {
            String pascal = extractKeywordsPascalCase(sessionTitle, 5);
            if (!pascal.isBlank() && !isGenericOrBlank(pascal)) {
                return pascal.startsWith("KiemThu") ? pascal : "KiemThu_" + pascal;
            }
        }

        // 7. Prompt keywords
        String promptSlug = extractKeywordsPascalCase(prompt, 5);
        if (!promptSlug.isBlank() && !isGenericOrBlank(promptSlug)) {
            return promptSlug.startsWith("KiemThu") ? promptSlug : "KiemThu_" + promptSlug;
        }

        return "BoKichBan_KiemThuChucNang";
    }

    public static boolean isGenericOrBlank(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String norm = name.trim().toLowerCase(Locale.ROOT);
        if (GENERIC_NAMES.contains(norm)) {
            return true;
        }
        return norm.equals("ai tests") || norm.equals("ai generated suite")
                || norm.startsWith("(tự động") || norm.startsWith("tự động tạo");
    }

    public static String sanitizeGroupOrSuiteName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String safe = raw.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (safe.length() > 45) {
            safe = safe.substring(0, 45).replaceAll("_+$", "");
        }
        return safe;
    }

    private record DomainMapping(String groupName, String suiteName) {}

    private static DomainMapping detectFunctionalDomain(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);

        // Password recovery / change password
        if (lower.contains("quên mật khẩu") || lower.contains("quen mat khau")
                || lower.contains("forgot password") || lower.contains("reset password")
                || lower.contains("đặt lại mật khẩu") || lower.contains("đổi mật khẩu")) {
            return new DomainMapping("XacThuc_TaiKhoan", "KiemThu_KhoiPhucMatKhau");
        }
        // Registration / Sign up
        if (lower.contains("đăng ký") || lower.contains("dang ky")
                || lower.contains("register") || lower.contains("sign up") || lower.contains("signup")
                || lower.contains("tạo tài khoản")) {
            return new DomainMapping("XacThuc_TaiKhoan", "KiemThu_DangKyTaiKhoan");
        }
        // Login / Authentication
        if (lower.contains("đăng nhập") || lower.contains("dang nhap")
                || lower.contains("login") || lower.contains("sign in") || lower.contains("signin")
                || lower.contains("xác thực") || lower.contains("authentication") || lower.contains("auth")
                || lower.contains("otp") || lower.contains("2fa") || lower.contains("mfa")) {
            return new DomainMapping("XacThuc_TaiKhoan", "KiemThu_DangNhapHeThong");
        }
        // Checkout / Payment
        if (lower.contains("thanh toán") || lower.contains("thanh toan")
                || lower.contains("checkout") || lower.contains("payment")
                || lower.contains("mã giảm giá") || lower.contains("voucher") || lower.contains("coupon")) {
            return new DomainMapping("MuaSam_ThanhToan", "KiemThu_ThanhToanDonHang");
        }
        // Shopping Cart
        if (lower.contains("giỏ hàng") || lower.contains("gio hang")
                || lower.contains("shopping cart") || lower.contains("add to cart")
                || lower.contains("thêm vào giỏ") || lower.contains("mua hàng")) {
            return new DomainMapping("MuaSam_GioHang", "KiemThu_GioHang");
        }
        // Order Management
        if (lower.contains("đơn hàng") || lower.contains("don hang")
                || lower.contains("order management") || lower.contains("quản lý đơn")
                || lower.contains("vận chuyển") || lower.contains("giao hàng")) {
            return new DomainMapping("QuanLyDonHang", "KiemThu_XuLyDonHang");
        }
        // Search & Filter
        if (lower.contains("tìm kiếm") || lower.contains("tim kiem")
                || lower.contains("search") || lower.contains("bộ lọc") || lower.contains("filter")
                || lower.contains("tra cứu")) {
            return new DomainMapping("TimKiem_TraCuu", "KiemThu_TimKiemVaLoc");
        }
        // Product / Catalog / Inventory
        if (lower.contains("sản phẩm") || lower.contains("san pham")
                || lower.contains("danh mục") || lower.contains("kho hàng")
                || lower.contains("product") || lower.contains("inventory") || lower.contains("catalog")) {
            return new DomainMapping("QuanLySanPham", "KiemThu_DanhMucSanPham");
        }
        // User Profile / Account / Customer / Employee Management
        if (lower.contains("hồ sơ") || lower.contains("thông tin cá nhân")
                || lower.contains("quản lý người dùng") || lower.contains("quản lý nhân viên")
                || lower.contains("quản lý khách hàng") || lower.contains("user profile")
                || lower.contains("user management")) {
            return new DomainMapping("QuanLyNguoiDung", "KiemThu_HoSoNguoiDung");
        }
        // Role / Permission / RBAC
        if (lower.contains("phân quyền") || lower.contains("phan quyen")
                || lower.contains("vai trò") || lower.contains("rbac") || lower.contains("permission")) {
            return new DomainMapping("PhanQuyen_BaoMat", "KiemThu_PhanQuyenHeThong");
        }
        // Reporting / Analytics / Dashboard
        if (lower.contains("báo cáo") || lower.contains("bao cao")
                || lower.contains("thống kê") || lower.contains("dashboard") || lower.contains("analytics")) {
            return new DomainMapping("BaoCao_ThongKe", "KiemThu_BaoCaoThongKe");
        }
        // Security / Fuzzing / XSS / SQLi
        if (lower.contains("xss") || lower.contains("sqli") || lower.contains("sql injection")
                || lower.contains("fuzzing") || lower.contains("bảo mật") || lower.contains("security")) {
            return new DomainMapping("KiemThuBaoMat", "KiemThu_AnToanBaoMat");
        }
        // Accessibility / WCAG
        if (lower.contains("wcag") || lower.contains("a11y") || lower.contains("trợ năng")
                || lower.contains("accessibility")) {
            return new DomainMapping("KiemThuTroNang", "KiemThu_WCAG_Accessibility");
        }
        // API / Contract
        if (lower.contains("rest api") || lower.contains("graphql") || lower.contains("endpoint")
                || lower.contains("kiểm thử api")) {
            return new DomainMapping("TichHop_API", "KiemThu_HopDongAPI");
        }

        return null;
    }

    private static String extractFeatureFromCaseNames(List<AiGeneratedCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return null;
        }
        Map<String, Integer> freq = new LinkedHashMap<>();
        for (AiGeneratedCase c : cases) {
            if (c == null || c.getName() == null) continue;
            String name = c.getName().trim();
            Matcher m = CASE_FEATURE_PATTERN.matcher(name);
            if (m.matches()) {
                String feature = m.group(1);
                if (feature != null && feature.length() >= 3
                        && !feature.equalsIgnoreCase("Test")
                        && !feature.equalsIgnoreCase("Case")
                        && !feature.equalsIgnoreCase("Recovered")) {
                    freq.put(feature, freq.getOrDefault(feature, 0) + 1);
                }
            }
        }
        String best = null;
        int max = 0;
        for (var entry : freq.entrySet()) {
            if (entry.getValue() > max) {
                max = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    private static String extractVietnameseFunctionalPhrase(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        Matcher m = VIETNAMESE_FEATURE_PHRASE.matcher(prompt);
        if (m.find()) {
            String phrase = m.group(1).trim();
            // Strip trailing filler words like "của hệ thống", "với", "khi", "cho"
            phrase = phrase.replaceAll("(?i)\\s+(khi|với|trên|tại|để|nhằm|bao gồm|theo)\\s+.*$", "").trim();
            if (phrase.length() >= 3) {
                return phrase;
            }
        }
        return null;
    }

    private static String buildCombinedText(String prompt,
                                            List<DocumentParserService.ParsedDocument> docs,
                                            List<AiGeneratedCase> cases) {
        StringBuilder sb = new StringBuilder();
        if (prompt != null) {
            sb.append(prompt).append(" ");
        }
        if (cases != null) {
            for (AiGeneratedCase c : cases) {
                if (c != null) {
                    if (c.getName() != null) sb.append(c.getName()).append(" ");
                    if (c.getDescription() != null) sb.append(c.getDescription()).append(" ");
                }
            }
        }
        if (docs != null) {
            for (DocumentParserService.ParsedDocument d : docs) {
                if (d != null && d.getFileName() != null) {
                    sb.append(d.getFileName()).append(" ");
                }
            }
        }
        return sb.toString();
    }

    public static String toAsciiPascalCase(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String noAccent = removeVietnameseAccents(input);
        String[] words = noAccent.split("[^A-Za-z0-9]+");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String w : words) {
            if (w.isBlank()) continue;
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) {
                sb.append(w.substring(1).toLowerCase(Locale.ROOT));
            }
            count++;
            if (count >= 10) break;
        }
        return sb.toString();
    }

    private static String extractKeywordsPascalCase(String input, int maxWords) {
        if (input == null || input.isBlank()) {
            return "";
        }
        Set<String> stopWords = Set.of(
                "tao", "sinh", "viet", "bo", "test", "case", "cases", "kich", "ban",
                "kiem", "thu", "cho", "cua", "tren", "voi", "va", "hoac", "cac", "nhung",
                "mot", "chi", "tiet", "day", "du", "theo", "yeu", "cau", "tai", "lieu",
                "hay", "giup", "toi", "em", "anh", "he", "thong", "website", "web", "app",
                "trang", "link", "url", "http", "https", "www", "com", "vn"
        );
        String noAccent = removeVietnameseAccents(input);
        String[] tokens = noAccent.split("[^A-Za-z0-9]+");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String t : tokens) {
            if (t.length() < 2) continue;
            String lower = t.toLowerCase(Locale.ROOT);
            if (stopWords.contains(lower)) continue;
            sb.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) {
                sb.append(lower.substring(1));
            }
            count++;
            if (count >= maxWords) break;
        }
        return sb.toString();
    }

    public static String removeVietnameseAccents(String text) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        String withoutMarks = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return withoutMarks.replace('đ', 'd').replace('Đ', 'D');
    }

    private String extractCleanFeatureToken(String resolvedGroup, String resolvedSuite, String prompt) {
        if (resolvedSuite != null && !resolvedSuite.isBlank()) {
            String token = resolvedSuite
                    .replaceFirst("(?i)^KiemThu_?", "")
                    .replaceFirst("(?i)^BoKichBan_?", "")
                    .replaceAll("[^A-Za-z0-9]", "");
            if (token.length() >= 3) {
                return token;
            }
        }
        if (resolvedGroup != null && !resolvedGroup.isBlank()) {
            String token = resolvedGroup
                    .replaceFirst("(?i)^ChucNang_?", "")
                    .replaceFirst("(?i)^PhanHe_?", "")
                    .replaceAll("[^A-Za-z0-9]", "");
            if (token.length() >= 3) {
                return token;
            }
        }
        String kw = extractKeywordsPascalCase(prompt, 3);
        return kw.isBlank() ? "ChucNang" : kw;
    }

    private String inferScenarioSuffix(String description, int caseNum) {
        if (description != null && !description.isBlank()) {
            String lower = description.toLowerCase(Locale.ROOT);
            if (lower.contains("hợp lệ") || lower.contains("thành công") || lower.contains("happy path")) {
                return "ThanhCong_HappyPath";
            }
            if (lower.contains("bỏ trống") || lower.contains("rỗng") || lower.contains("empty")) {
                return "BoTrongTruongBatBuoc";
            }
            if (lower.contains("sai ") || lower.contains("không hợp lệ") || lower.contains("invalid") || lower.contains("lỗi")) {
                return "DuLieuKhongHopLe";
            }
            if (lower.contains("biên") || lower.contains("boundary") || lower.contains("tối đa") || lower.contains("tối thiểu")) {
                return "GiaTriBien_Boundary";
            }
            if (lower.contains("bảo mật") || lower.contains("xss") || lower.contains("sql")) {
                return "BaoMat_Injection";
            }
        }
        return switch (caseNum) {
            case 1 -> "LuongChinh_HappyPath";
            case 2 -> "DuLieuKhongHopLe_Negative";
            case 3 -> "GiaTriBien_Boundary";
            default -> "KichBanMoRong_" + caseNum;
        };
    }

    private String sanitizeIdentifier(String raw) {
        if (raw == null) return "Scenario";
        String noAccent = removeVietnameseAccents(raw);
        String clean = NON_WORD_OR_UNDERSCORE.matcher(noAccent).replaceAll("_");
        clean = MULTI_UNDERSCORE.matcher(clean).replaceAll("_");
        clean = EDGE_UNDERSCORE.matcher(clean).replaceAll("");
        return clean.isBlank() ? "Scenario" : clean;
    }





    /**
     * Formats a concise, structured executive summary for the chat bubble without dumping raw .md
     * documents or raw Java code blocks into the conversation view.
     */
    public String buildConciseChatSummary(AiTestGeneratorService.GenerationResult result,
                                          AiAgentIndexService.PersistedArtifactBundle bundle) {
        if (result == null) {
            return "Đã xử lý yêu cầu.";
        }
        String rawSummary = result.getSummary() != null ? result.getSummary().trim() : "";
        // Strip all fenced code blocks (```java ... ```, ```json ... ```, etc.)
        String cleanOverview = CODE_FENCE_STRIP_PATTERN.matcher(rawSummary).replaceAll("").trim();

        // Strip markdown tables and long multi-heading dumps so chat stays concise
        StringBuilder briefLines = new StringBuilder();
        int keptLines = 0;
        for (String line : cleanOverview.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("|") && t.endsWith("|")) continue; // skip markdown table rows
            if (t.startsWith("---") || t.startsWith("===")) continue;
            if (t.startsWith("#")) {
                t = t.replaceFirst("^#+\\s*", "");
            }
            if (t.length() > 220) {
                t = t.substring(0, 217) + "...";
            }
            briefLines.append(t).append("\n");
            keptLines++;
            if (keptLines >= 3) break;
        }
        String overviewText = briefLines.toString().trim();
        if (overviewText.isBlank()) {
            overviewText = "Đã phân tích yêu cầu và khởi tạo bộ kịch bản kiểm thử tự động.";
        }

        List<AiGeneratedCase> cases = result.getCases() != null ? result.getCases() : List.of();
        if (cases.isEmpty()) {
            return overviewText;
        }

        int totalSteps = cases.stream().mapToInt(c -> c.getTestSteps() != null ? c.getTestSteps().size() : 0).sum();
        int totalAssertions = cases.stream().mapToInt(c -> c.getAssertions() != null ? c.getAssertions().size() : 0).sum();
        long scriptCount = cases.stream().filter(c -> c.getScriptCode() != null && !c.getScriptCode().isBlank()).count();

        StringBuilder sb = new StringBuilder();
        sb.append(overviewText).append("\n\n");
        sb.append("📂 **Phân hệ / Nhóm:** `").append(result.getSuggestedGroup() != null ? result.getSuggestedGroup() : "KiemThuChucNang").append("`")
                .append("  |  **Suite:** `").append(result.getSuggestedSuite() != null ? result.getSuggestedSuite() : "BoKichBan_KiemThu").append("`\n");
        sb.append("📊 **Tổng hợp:** **").append(cases.size()).append("** Test Cases  •  **")
                .append(totalSteps).append("** Test Steps  •  **")
                .append(totalAssertions).append("** Assertions");
        if (scriptCount > 0) {
            sb.append("  •  **").append(scriptCount).append("** Test Scripts");
        }
        if (result.getAgentReviewReport() != null) {
            sb.append("  •  Điểm chất lượng: **").append(result.getAgentReviewReport().getFinalQualityScore()).append("/100**");
        }
        sb.append("\n");
        AiMultiAgentCoordinator.AgentReviewReport review = result.getAgentReviewReport();
        if (review != null && review.getCoverage() != null) {
            AiCoverageAnalyzer.Report coverage = review.getCoverage();
            if (coverage.screensTotal() > 0) {
                sb.append("🎯 **Độ phủ màn hình:** ").append(coverage.screensCovered()).append("/")
                        .append(coverage.screensTotal()).append(" (").append(coverage.screenCoveragePercent()).append("%)")
                        .append("  •  **Truy vết SRS:** ").append(coverage.traceableCases()).append("/")
                        .append(coverage.totalCases()).append(" case (").append(coverage.traceabilityPercent()).append("%)\n");
            }
        }
        if (review != null && !review.getGaps().isEmpty()) {
            sb.append("⚠️ **Khoảng trống chưa được bù (không tự bịa):** ").append(review.getGaps().size())
                    .append(" mục — xem chi tiết trong báo cáo.\n");
        }
        sb.append("\n");

        sb.append("📋 **Danh sách Test Case & Kịch bản (xem chi tiết tại bảng bên phải):**\n");
        int idx = 1;
        for (AiGeneratedCase c : cases) {
            int sCount = c.getTestSteps() != null ? c.getTestSteps().size() : 0;
            int aCount = c.getAssertions() != null ? c.getAssertions().size() : 0;
            boolean hasCode = c.getScriptCode() != null && !c.getScriptCode().isBlank();
            sb.append(idx++).append(". `").append(c.getName()).append("` [").append(c.getPriority() != null ? c.getPriority() : "P1").append("]")
                    .append(" — ").append(sCount).append(" bước, ").append(aCount).append(" assertions")
                    .append(hasCode ? ", có Test Script (.java)" : ", đặc tả Test Case")
                    .append("\n");
        }

        if (bundle != null && bundle.artifactDir() != null) {
            sb.append("\n💾 **Đã lưu trữ trong ứng dụng:** `").append(bundle.artifactDir().getFileName()).append("`")
                    .append(" (Báo cáo `.md`, `.json`, `scripts/*.java`, `assertions/*_assertions.json`).");
        }

        return sb.toString().trim();
    }





    // =========================================================================
    // MULTI-SCREEN SRS SEMANTIC SECTIONING & COVERAGE EXPANSION
    // =========================================================================

    /**
     * Extracts functional screen/module sections from every attached document and, when present, an
     * explicit screen list in the prompt.
     *
     * <p>All documents are scanned (the previous implementation stopped after the first one, so a
     * three-file SRS silently lost two thirds of its screens) and duplicate headings are collapsed.
     * Placeholder screens are never created: a requested count larger than the evidence is reported
     * as a gap, not padded with synthetic "Màn hình N" entries.</p>
     */
    public static List<ScreenSpecSection> extractScreenSections(String prompt,
                                                               List<DocumentParserService.ParsedDocument> attachedDocs) {
        List<ScreenSpecSection> screens = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        int requestedCount = extractRequestedScreenCount(prompt);

        // 1. Every attached document, in order, de-duplicated by title + section body.
        if (attachedDocs != null && !attachedDocs.isEmpty()) {
            for (DocumentParserService.ParsedDocument doc : attachedDocs) {
                if (doc == null || doc.isImage()) continue;
                String raw = doc.getContent();
                if (raw == null || raw.isBlank()) continue;

                // Strip YAML frontmatter & page markers
                String text = YAML_FRONTMATTER_PATTERN.matcher(raw).replaceFirst("");
                text = PDF_PAGE_HEADING_PATTERN.matcher(text).replaceAll("");

                List<RawHeadingSection> rawSections = splitByFunctionalHeadings(text);
                for (RawHeadingSection rawSec : rawSections) {
                    String cleanTitle = cleanScreenTitle(rawSec.heading);
                    if (!seen.add(dedupeKey(cleanTitle, rawSec.body))) {
                        continue;
                    }
                    String token = toAsciiPascalCase(cleanTitle);
                    if (token.isBlank() || token.length() < 3) {
                        token = "ManHinh" + (screens.size() + 1);
                    }
                    if (!token.toLowerCase(Locale.ROOT).startsWith("manhinh")
                            && !token.toLowerCase(Locale.ROOT).startsWith("chucnang")
                            && !token.toLowerCase(Locale.ROOT).startsWith("trang")) {
                        token = "ManHinh_" + token;
                    }

                    List<String> fields = extractFieldsFromSection(rawSec.body);
                    List<String> rules = extractRulesFromSection(rawSec.body);
                    String url = extractTargetUrlFromSection(rawSec.body);

                    screens.add(new ScreenSpecSection(screens.size() + 1, cleanTitle, token, rawSec.body, fields, rules, url));
                    if (requestedCount > 0 && screens.size() >= requestedCount) {
                        break;
                    }
                }
                if (requestedCount > 0 && screens.size() >= requestedCount) {
                    break;
                }
            }
        }

        // 2. Explicit screen list in the prompt (union, de-duplicated). Only lines that actually name a
        // screen are accepted; a bare "10 màn hình" is a count, not a specification.
        if (prompt != null && !prompt.isBlank() && (requestedCount == 0 || screens.size() < requestedCount)) {
            List<String> promptLines = prompt.lines()
                    .map(String::trim)
                    .filter(l -> !l.isBlank())
                    .toList();
            for (String line : promptLines) {
                if (line.matches("(?i)^[-*•\\d.]+\\s+(?:Màn\\s+hình|Trang|Chức\\s+năng|Module|Screen|UC-?\\d+).*")
                        || line.matches("(?i)^(?:Màn\\s+hình|Trang|Chức\\s+năng|Module|Screen|UC-?\\d+)\\s*[:\\-–\\d].*")) {
                    String cleanTitle = cleanScreenTitle(line.replaceFirst("^[-*•\\d.]+\\s*", ""));
                    if (!seen.add(dedupeKey(cleanTitle, line))) {
                        continue;
                    }
                    String token = toAsciiPascalCase(cleanTitle);
                    if (token.isBlank()) token = "ManHinh" + (screens.size() + 1);
                    if (!token.toLowerCase(Locale.ROOT).startsWith("manhinh")) {
                        token = "ManHinh_" + token;
                    }
                    screens.add(new ScreenSpecSection(screens.size() + 1, cleanTitle, token, line, List.of(), List.of(), ""));
                    if (requestedCount > 0 && screens.size() >= requestedCount) {
                        break;
                    }
                }
            }
        }

        if (requestedCount > screens.size()) {
            AiLoggingService.warn("SPEC-MINER", "Yêu cầu " + requestedCount + " màn hình nhưng tài liệu chỉ chứng minh được "
                    + screens.size() + "; không tạo màn hình giả để đủ số lượng");
        }

        return screens;
    }

    /**
     * Short, shared preamble taken from every attached document: enough to keep global constraints
     * (roles, base URLs, mandatory preconditions) in a per-screen batch without re-sending the SRS.
     */
    public static String buildDocumentPreamble(List<DocumentParserService.ParsedDocument> docs,
                                               int charsPerDoc) {
        if (docs == null || docs.isEmpty()) {
            return "";
        }
        int cap = charsPerDoc > 0 ? charsPerDoc : 800;
        StringBuilder sb = new StringBuilder();
        for (DocumentParserService.ParsedDocument doc : docs) {
            if (doc == null || doc.isImage() || doc.getContent() == null || doc.getContent().isBlank()) {
                continue;
            }
            String body = doc.getContent().replaceAll("\\s+", " ").trim();
            if (body.length() > cap) {
                body = body.substring(0, cap) + "…";
            }
            sb.append("<untrusted_document name=\"").append(doc.getFileName()).append("\">\n")
                    .append(body).append("\n</untrusted_document>\n");
        }
        return sb.toString().trim();
    }

    private static String dedupeKey(String title, String body) {        String normalizedTitle = normalizeToken(title);
        String normalizedBody = normalizeToken(body);
        if (normalizedBody.length() > 120) {
            normalizedBody = normalizedBody.substring(0, 120);
        }
        return normalizedTitle + "|" + normalizedBody;
    }

    public static int extractRequestedScreenCount(String prompt) {
        if (prompt == null || prompt.isBlank()) return 0;
        Matcher m = REQUESTED_SCREEN_COUNT_PATTERN.matcher(prompt);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private record RawHeadingSection(String heading, String body) {}

    private static List<RawHeadingSection> splitByFunctionalHeadings(String markdown) {
        List<RawHeadingSection> result = new ArrayList<>();
        Matcher m = HEADING_LINE_PATTERN.matcher(markdown);
        List<Integer> starts = new ArrayList<>();
        List<String> headings = new ArrayList<>();

        while (m.find()) {
            String h = m.group(1).trim();
            if (isFunctionalScreenHeading(h)) {
                starts.add(m.start());
                headings.add(h);
            }
        }

        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : markdown.length();
            String body = markdown.substring(start, end).trim();
            result.add(new RawHeadingSection(headings.get(i), body));
        }

        return result;
    }

    private static boolean isFunctionalScreenHeading(String heading) {
        if (heading == null || heading.isBlank()) return false;
        String lower = heading.toLowerCase(Locale.ROOT);

        // Filter out boilerplate document headings
        if (lower.contains("mục đích") || lower.contains("phạm vi") || lower.contains("thuật ngữ")
                || lower.contains("tài liệu tham khảo") || lower.contains("lịch sử") || lower.contains("phiên bản")
                || lower.contains("giới thiệu") || lower.contains("tổng quan dự án") || lower.contains("table of content")
                || lower.contains("mục lục") || lower.contains("kiến trúc tổng thể") || lower.contains("môi trường")
                || lower.contains("quy ước") || lower.contains("tiêu chuẩn")) {
            return false;
        }

        // Match functional keywords
        return lower.contains("màn hình") || lower.contains("screen") || lower.contains("trang")
                || lower.contains("chức năng") || lower.contains("module") || lower.contains("phân hệ")
                || lower.contains("form") || lower.contains("biểu mẫu")
                || lower.matches(".*(?:uc|fr|req|f)[-_\\s]?\\d+.*")
                || lower.matches("^\\d+\\.\\d+(?:\\.\\d+)?\\s+.*");
    }

    private static String cleanScreenTitle(String raw) {
        if (raw == null) return "ManHinhChucNang";
        return raw.replaceAll("(?i)^[#*\\-\\d.]+\\s*", "")
                .replaceAll("(?i)^màn\\s+hình\\s*[:\\-–]?\\s*", "")
                .replaceAll("(?i)^screen\\s*[:\\-–]?\\s*", "")
                .replaceAll("(?i)^trang\\s*[:\\-–]?\\s*", "")
                .replaceAll("[*`_]", "")
                .trim();
    }

    private static List<String> extractFieldsFromSection(String body) {
        Set<String> fields = new LinkedHashSet<>();
        if (body == null || body.isBlank()) return List.of();

        Matcher tm = TABLE_FIELD_ROW_PATTERN.matcher(body);
        while (tm.find()) {
            String col1 = tm.group(1).trim().replaceAll("[*`]", "");
            if (col1.length() >= 2 && col1.length() <= 35
                    && !col1.equalsIgnoreCase("stt") && !col1.equalsIgnoreCase("tên trường")
                    && !col1.equalsIgnoreCase("field") && !col1.equalsIgnoreCase("tên cột")
                    && !col1.equalsIgnoreCase("tham số") && !col1.contains("---")) {
                fields.add(col1);
                if (fields.size() >= 12) break;
            }
        }

        Matcher bm = BULLET_FIELD_PATTERN.matcher(body);
        while (bm.find()) {
            String name = bm.group(1).trim().replaceAll("[*`]", "");
            if (name.length() >= 2 && name.length() <= 35) {
                fields.add(name);
                if (fields.size() >= 12) break;
            }
        }
        return new ArrayList<>(fields);
    }

    private static List<String> extractRulesFromSection(String body) {
        List<String> rules = new ArrayList<>();
        if (body == null || body.isBlank()) return rules;
        for (String line : body.split("\\r?\\n")) {
            String t = line.trim();
            String lower = t.toLowerCase(Locale.ROOT);
            if (lower.contains("bắt buộc") || lower.contains("tối đa") || lower.contains("tối thiểu")
                    || lower.contains("định dạng") || lower.contains("trùng lặp") || lower.contains("thông báo lỗi")
                    || lower.contains("phân quyền") || lower.contains("chỉ cho phép") || lower.contains("xác thực")
                    || lower.contains("required") || lower.contains("validation") || lower.contains("unique")) {
                String clean = t.replaceAll("^[-*•#\\d.]+\\s*", "").trim();
                if (clean.length() >= 8 && clean.length() <= 160) {
                    rules.add(clean);
                    if (rules.size() >= 6) break;
                }
            }
        }
        return rules;
    }

    private static String extractTargetUrlFromSection(String body) {
        if (body == null || body.isBlank()) return "";
        Matcher m = URL_EXTRACT_PATTERN.matcher(body);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }


















}
