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
                generateScript
                        ? "CHẾ ĐỘ SINH SCRIPT BẬT: Viết mã Playwright Java hoàn chỉnh trong `scriptCode`, tuyệt đối không dùng Thread.sleep/waitForTimeout."
                        : "CHẾ ĐỘ CHỈ TẠO TEST CASE (KHÔNG SINH SCRIPT): Để trống `scriptCode: \"\"`, tập trung tối đa vào độ chi tiết của `testSteps`, `testDataSets` và `assertions`."
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

            // Multi-Scenario & Multi-Screen Coverage Expansion
            boolean isSingleCaseUpdate = cases.get(0).getTargetCaseId() != null
                    && !cases.get(0).getTargetCaseId().isBlank();
            if (!isSingleCaseUpdate) {
                List<ScreenSpecSection> screens = extractScreenSections(userPrompt, attachedDocs);
                if (screens.size() >= 2) {
                    int minCasesPerScreen = 4; // HappyPath + NegativeValidation + BoundaryBVA + BusinessRule/Permission
                    int beforeCount = cases.size();
                    List<AiGeneratedCase> multiScreenExpanded = expandMultiScreenCoverage(
                            cases, screens, minCasesPerScreen, userPrompt, generateScript);
                    if (multiScreenExpanded.size() > beforeCount) {
                        cases.clear();
                        cases.addAll(multiScreenExpanded);
                        improvements.add("Tự động mở rộng độ phủ đa màn hình (Multi-Screen SRS Coverage): đảm bảo đủ "
                                + minCasesPerScreen + " kịch bản/màn hình trên toàn bộ " + screens.size()
                                + " màn hình (" + beforeCount + " -> " + cases.size() + " Test Cases)");
                    }
                } else {
                    // Check if cases contain multiple distinct screen/feature groups where each only has 1-2 cases
                    Map<String, List<AiGeneratedCase>> featureGroups = groupCasesByFeatureToken(cases, featurePrefix);
                    if (featureGroups.size() >= 2) {
                        int minPerGroup = 4;
                        int beforeCount = cases.size();
                        List<AiGeneratedCase> expandedGroups = expandFeatureGroupCoverage(
                                featureGroups, minPerGroup, userPrompt, generateScript);
                        if (expandedGroups.size() > beforeCount) {
                            cases.clear();
                            cases.addAll(expandedGroups);
                            improvements.add("Tự động mở rộng độ phủ theo từng màn hình/chức năng: nâng từ "
                                    + beforeCount + " lên " + cases.size() + " Test Cases (" + featureGroups.size() + " phân hệ)");
                        }
                    } else if (cases.size() == 1) {
                        AiGeneratedCase primary = cases.get(0);
                        List<AiGeneratedCase> expanded = synthesizeCompanionScenarios(primary, featurePrefix, userPrompt, generateScript);
                        cases.addAll(expanded);
                        improvements.add("Tự động mở rộng độ phủ kịch bản (Multi-Scenario Coverage): bổ sung Negative Validation & Boundary BVA (" + expanded.size() + " kịch bản)");
                    }
                }
            }

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

                // 4. Heal NLP Test Steps if missing or fewer than 3 steps
                List<TestStep> steps = tc.getTestSteps();
                if (steps == null || steps.size() < 3) {
                    List<TestStep> synthesizedSteps = synthesizeTestSteps(tc, userPrompt);
                    tc.setTestSteps(synthesizedSteps);
                    improvements.add("Tự động tổng hợp " + synthesizedSteps.size() + " bước thực thi chi tiết cho " + tc.getName());
                } else {
                    // Ensure step numbers and expected results are populated
                    for (int s = 0; s < steps.size(); s++) {
                        TestStep step = steps.get(s);
                        if (step.getStepNumber() <= 0) {
                            step.setStepNumber(s + 1);
                        }
                        if (step.getAction() == null || step.getAction().isBlank()) {
                            step.setAction("Thực hiện thao tác bước " + (s + 1));
                        }
                        if (step.getExpectedResult() == null || step.getExpectedResult().isBlank()) {
                            step.setExpectedResult("Hệ thống xử lý hợp lệ và phản hồi đúng trạng thái kỳ vọng");
                        }
                    }
                }

                // 5. Heal & Enrich Assertions (normalize types + ensure multi-point verification >= 2 assertions)
                List<Assertion> assertions = tc.getAssertions();
                if (assertions == null || assertions.isEmpty()) {
                    List<Assertion> synthesizedAsserts = synthesizeAssertions(tc);
                    tc.setAssertions(synthesizedAsserts);
                    improvements.add("Bổ sung " + synthesizedAsserts.size() + " tiêu chí nghiệm thu đa điểm (Assertions) cho " + tc.getName());
                } else {
                    for (Assertion a : assertions) {
                        a.setType(AiTestGeneratorService.normalizeAssertionType(a.getType()));
                        if (a.getStatus() == null || a.getStatus().isBlank()) {
                            a.setStatus("ACTIVE");
                        }
                    }
                    if (assertions.size() < 2) {
                        List<Assertion> extraAsserts = synthesizeAssertions(tc);
                        for (Assertion extra : extraAsserts) {
                            boolean duplicate = assertions.stream()
                                    .anyMatch(ex -> ex.getType().equals(extra.getType())
                                            && String.valueOf(ex.getLocator()).equals(String.valueOf(extra.getLocator())));
                            if (!duplicate) {
                                assertions.add(extra);
                                if (assertions.size() >= 2) break;
                            }
                        }
                        tc.setAssertions(assertions);
                    }
                }

                // 6. Heal Test Data Sets (BVA / EP) if empty
                List<TestDataItem> dataSets = tc.getTestDataSets();
                if (dataSets == null || dataSets.isEmpty()) {
                    List<TestDataItem> synthesizedData = synthesizeTestDataSets(tc, caseNum);
                    tc.setTestDataSets(synthesizedData);
                    improvements.add("Bổ sung bộ dữ liệu kiểm thử biên/tương đương cho " + tc.getName());
                }

                // 7. Heal Playwright Script Code according to generateScript mode
                if (!generateScript) {
                    if (tc.getScriptCode() != null && !tc.getScriptCode().isBlank()) {
                        tc.setScriptCode("");
                        improvements.add("Loại bỏ script thừa khi ở chế độ chỉ tạo Test Case cho " + tc.getName());
                    }
                } else {
                    String code = tc.getScriptCode();
                    if ((code == null || code.isBlank()) && tc.getStartUrl() != null && !tc.getStartUrl().isBlank() && generatorService != null) {
                        String synthesizedCode = synthesizeScenarioScript(tc);
                        tc.setScriptCode(generatorService.validateAndFixScript(synthesizedCode, tc.getName(), tc.getStartUrl()));
                    } else if (code != null && !code.isBlank() && generatorService != null) {
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

        int finalScore = evaluateQualityScore(result, generateScript);
        report.setFinalQualityScore(finalScore);
        report.setLoopIterations(loopIterations);
        report.setImprovementsApplied(improvements);

        return report;
    }

    /**
     * Evaluates a 0-100 quality score across 5 axes (20 points each):
     * 1. Functional Group & Suite Naming Accuracy (20 pts)
     * 2. Scenario Matrix & Naming Convention (20 pts)
     * 3. Test Steps Completeness (20 pts)
     * 4. Assertion & Test Data Coverage (20 pts)
     * 5. Playwright Script Determinism / Mode Compliance (20 pts)
     */
    public int evaluateQualityScore(AiTestGeneratorService.GenerationResult result, boolean generateScript) {
        if (result == null) return 0;
        int score = 0;

        // Axis 1: Functional Group & Suite Naming (20 pts)
        if (!isGenericOrBlank(result.getSuggestedGroup())) {
            score += 10;
        }
        if (!isGenericOrBlank(result.getSuggestedSuite())) {
            score += 10;
        }

        List<AiGeneratedCase> cases = result.getCases();
        if (cases == null || cases.isEmpty()) {
            return score;
        }

        // Axis 2: Scenario Matrix & Naming Convention (20 pts)
        long wellNamedCount = cases.stream()
                .filter(c -> c.getName() != null && c.getName().matches("(?i)^TC\\d+_[A-Za-z0-9_]+")
                        && c.getPriority() != null && c.getPriority().matches("P[0-3]"))
                .count();
        score += (int) Math.round(20.0 * wellNamedCount / cases.size());

        // Axis 3: Test Steps Completeness >= 3 steps per case (20 pts)
        long completeStepsCount = cases.stream()
                .filter(c -> c.getTestSteps() != null && c.getTestSteps().size() >= 3)
                .count();
        score += (int) Math.round(20.0 * completeStepsCount / cases.size());

        // Axis 4: Assertions & Test Data Coverage (20 pts)
        long assertAndDataCount = cases.stream()
                .filter(c -> c.getAssertions() != null && !c.getAssertions().isEmpty()
                        && c.getTestDataSets() != null && !c.getTestDataSets().isEmpty())
                .count();
        score += (int) Math.round(20.0 * assertAndDataCount / cases.size());

        // Axis 5: Script Determinism / Mode Compliance (20 pts)
        if (!generateScript) {
            long cleanNoScript = cases.stream()
                    .filter(c -> c.getScriptCode() == null || c.getScriptCode().isBlank())
                    .count();
            score += (int) Math.round(20.0 * cleanNoScript / cases.size());
        } else {
            long validScripts = cases.stream()
                    .filter(c -> c.getScriptCode() != null && !c.getScriptCode().isBlank()
                            && !c.getScriptCode().contains("Thread.sleep")
                            && !c.getScriptCode().contains("waitForTimeout")
                            && c.getScriptCode().contains("class "))
                    .count();
            score += (int) Math.round(20.0 * validScripts / cases.size());
        }

        return Math.min(100, score);
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
     * Synthesizes companion Negative Validation (TC02) and Boundary BVA (TC03) scenarios
     * when the initial generation produced only a single Happy Path test case.
     */
    private List<AiGeneratedCase> synthesizeCompanionScenarios(AiGeneratedCase primary,
                                                               String featurePrefix,
                                                               String userPrompt,
                                                               boolean generateScript) {
        List<AiGeneratedCase> companions = new ArrayList<>();
        String startUrl = primary.getStartUrl() != null ? primary.getStartUrl() : "";
        List<String> preconditions = primary.getPreconditions() != null && !primary.getPreconditions().isEmpty()
                ? new ArrayList<>(primary.getPreconditions())
                : List.of("Hệ thống sẵn sàng và màn hình chức năng đang mở");

        // Companion 1: Negative Validation (TC02)
        AiGeneratedCase negativeCase = new AiGeneratedCase();
        negativeCase.setName(String.format("TC02_%s_KiemTraLoiDuLieuKhongHopLe", featurePrefix));
        negativeCase.setPriority("P1");
        negativeCase.setStartUrl(startUrl);
        negativeCase.setPreconditions(preconditions);
        negativeCase.setDescription("Kiểm thử xử lý ngoại lệ & thông báo lỗi khi bỏ trống trường bắt buộc hoặc nhập dữ liệu sai định dạng");
        negativeCase.setTestSteps(List.of(
                new TestStep(1, "NAVIGATE", startUrl.isBlank() ? "Màn hình chức năng" : startUrl, "",
                        "Truy cập màn hình chức năng cần kiểm thử", "Màn hình hiển thị đầy đủ các trường nhập liệu"),
                new TestStep(2, "FILL", "Các trường bắt buộc / Ô nhập liệu chính", "Bỏ trống (\"\") hoặc nhập sai định dạng (!@#$%)",
                        "Để trống trường bắt buộc hoặc điền dữ liệu không hợp lệ", "Hệ thống nhận diện dữ liệu đầu vào vi phạm quy tắc"),
                new TestStep(3, "CLICK", "Nút Xác nhận / Lưu / Gửi biểu mẫu", "",
                        "Nhấn nút gửi biểu mẫu với dữ liệu không hợp lệ", "Hệ thống chặn gửi request và kích hoạt cơ chế kiểm tra hợp lệ"),
                new TestStep(4, "VERIFY", "[role='alert'], .error-message, .toast", "",
                        "Kiểm tra thông báo lỗi validation hiển thị trên giao diện", "Hiển thị thông báo lỗi rõ ràng và không phát sinh lỗi hệ thống 500")
        ));
        negativeCase.setTestDataSets(synthesizeTestDataSets(negativeCase, 2));
        negativeCase.setAssertions(synthesizeAssertions(negativeCase));
        if (generateScript && !startUrl.isBlank()) {
            negativeCase.setScriptCode(synthesizeScenarioScript(negativeCase));
        } else {
            negativeCase.setScriptCode("");
        }
        companions.add(negativeCase);

        // Companion 2: Boundary Value Analysis / Edge Case (TC03)
        AiGeneratedCase boundaryCase = new AiGeneratedCase();
        boundaryCase.setName(String.format("TC03_%s_GiaTriBienBVA", featurePrefix));
        boundaryCase.setPriority("P2");
        boundaryCase.setStartUrl(startUrl);
        boundaryCase.setPreconditions(preconditions);
        boundaryCase.setDescription("Kiểm thử phân tích giá trị biên (Min, Max, Max+1, ký tự đặc biệt/Unicode) cho chức năng " + featurePrefix);
        boundaryCase.setTestSteps(List.of(
                new TestStep(1, "NAVIGATE", startUrl.isBlank() ? "Màn hình chức năng" : startUrl, "",
                        "Truy cập màn hình chức năng cần kiểm thử biên", "Màn hình tải hoàn tất và sẵn sàng nhập liệu"),
                new TestStep(2, "FILL", "Trường dữ liệu giới hạn độ dài / giá trị", "Chuỗi biên tối thiểu (Min=1) và tối đa (Max=255 / Max+1=256)",
                        "Nhập dữ liệu tại ngưỡng biên dưới (Min), biên trên (Max) và vượt biên (Max+1)", "Trường nhập liệu kiểm soát độ dài hoặc giá trị theo đúng thiết kế"),
                new TestStep(3, "CLICK", "Nút Xác nhận / Thực thi", "",
                        "Thực hiện gửi dữ liệu tại các mốc giá trị biên", "Hệ thống xử lý chính xác tại biên hợp lệ và từ chối/cắt ngắn khi vượt biên"),
                new TestStep(4, "VERIFY", "Trạng thái phản hồi & dữ liệu hiển thị", "",
                        "Đối chiếu kết quả xử lý với đặc tả giá trị biên (BVA)", "Dữ liệu không bị lỗi tràn bộ đệm hoặc lỗi hiển thị Unicode")
        ));
        boundaryCase.setTestDataSets(synthesizeTestDataSets(boundaryCase, 3));
        boundaryCase.setAssertions(synthesizeAssertions(boundaryCase));
        if (generateScript && !startUrl.isBlank()) {
            boundaryCase.setScriptCode(synthesizeScenarioScript(boundaryCase));
        } else {
            boundaryCase.setScriptCode("");
        }
        companions.add(boundaryCase);

        return companions;
    }

    public String synthesizeScenarioScript(AiGeneratedCase tc) {
        String url = (tc.getStartUrl() != null && !tc.getStartUrl().isBlank()) ? tc.getStartUrl() : "https://example.com";
        String className = tc.getName() != null && !tc.getName().isBlank() ? sanitizeIdentifier(tc.getName()) : "GeneratedScenario";
        String nameLower = className.toLowerCase(Locale.ROOT);
        StringBuilder bodySb = new StringBuilder();

        // Translate structured testSteps into deterministic Playwright actions
        if (tc.getTestSteps() != null) {
            for (TestStep step : tc.getTestSteps()) {
                String act = step.getAction() != null ? step.getAction().toUpperCase(Locale.ROOT) : "";
                String target = step.getTarget() != null ? step.getTarget().trim() : "";
                String data = step.getTestData() != null ? step.getTestData() : "";
                String desc = step.getDescription() != null && !step.getDescription().isBlank()
                        ? step.getDescription() : ("Step " + step.getStepNumber());
                boolean isSelector = target.startsWith("#") || target.startsWith(".")
                        || target.startsWith("[") || target.startsWith("//")
                        || target.contains("input") || target.contains("button")
                        || target.contains("select") || target.contains("textarea");
                String escTarget = com.studio.util.StringEscapeUtil.escapeJava(target);
                String escDesc = com.studio.util.StringEscapeUtil.escapeJava(desc);
                String escData = com.studio.util.StringEscapeUtil.escapeJava(data);

                if (isSelector) {
                    if (act.contains("FILL") || act.contains("TYPE") || act.contains("INPUT")) {
                        bodySb.append("        com.studio.runtime.StudioRuntime.healLocator(\"")
                                .append(escTarget).append("\", \"").append(escDesc).append("\").first().fill(\"")
                                .append(escData).append("\");\n");
                    } else if (act.contains("CLICK") || act.contains("SUBMIT")) {
                        bodySb.append("        com.studio.runtime.StudioRuntime.healLocator(\"")
                                .append(escTarget).append("\", \"").append(escDesc).append("\").first().click();\n");
                    } else if (act.contains("SELECT")) {
                        bodySb.append("        com.studio.runtime.StudioRuntime.healLocator(\"")
                                .append(escTarget).append("\", \"").append(escDesc).append("\").first().selectOption(\"")
                                .append(escData).append("\");\n");
                    } else if (act.contains("CHECK")) {
                        bodySb.append("        com.studio.runtime.StudioRuntime.healLocator(\"")
                                .append(escTarget).append("\", \"").append(escDesc).append("\").first().check();\n");
                    }
                }
            }
        }

        if (bodySb.isEmpty() && (nameLower.contains("negative") || nameLower.contains("khonghople") || nameLower.contains("loi") || nameLower.contains("sai"))) {
            bodySb.append("""
                        Locator submitBtn = com.studio.runtime.StudioRuntime.healLocator("button[type='submit'], button, input[type='submit']", "Submit Action").first();
                        if (submitBtn.isVisible()) {
                            submitBtn.click();
                        }
                """);
        }

        // Translate assertions into Playwright verification statements
        boolean addedAssertion = false;
        if (tc.getAssertions() != null) {
            for (Assertion a : tc.getAssertions()) {
                String type = a.getType() != null ? a.getType().toUpperCase(Locale.ROOT) : "";
                String loc = a.getLocator() != null ? com.studio.util.StringEscapeUtil.escapeJava(a.getLocator()) : "";
                String val = a.getValue() != null ? com.studio.util.StringEscapeUtil.escapeJava(a.getValue()) : "";
                if ("ELEMENT_VISIBLE".equals(type) && !loc.isBlank()) {
                    bodySb.append("        assertThat(com.studio.runtime.StudioRuntime.healLocator(\"")
                            .append(loc).append("\", \"Verify Visible\").first()).isVisible();\n");
                    addedAssertion = true;
                } else if ("ELEMENT_HAS_TEXT".equals(type) && !loc.isBlank() && !val.isBlank()) {
                    bodySb.append("        assertThat(com.studio.runtime.StudioRuntime.healLocator(\"")
                            .append(loc).append("\", \"Verify Text\").first()).containsText(\"")
                            .append(val).append("\");\n");
                    addedAssertion = true;
                } else if ("URL_CONTAINS".equals(type) && !val.isBlank()) {
                    bodySb.append("        assertThat(page).hasURL(java.util.regex.Pattern.compile(\".*\" + java.util.regex.Pattern.quote(\"")
                            .append(val).append("\") + \".*\"));\n");
                    addedAssertion = true;
                }
            }
        }
        if (!addedAssertion) {
            bodySb.append("        assertThat(page.locator(\"body\")).isVisible();\n");
        }

        return """
                package com.test;

                import com.microsoft.playwright.*;
                import com.microsoft.playwright.options.*;
                import static com.studio.runtime.StudioAssertions.assertThat;

                public class %s {
                    public static void main(String[] args) {
                        Page page = com.studio.runtime.StudioRuntime.page();
                        page.navigate("%s");
                        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
                %s    }
                }
                """.formatted(className, com.studio.util.StringEscapeUtil.escapeJava(url), bodySb.toString());
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
        sb.append("\n\n");

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

    private List<TestStep> synthesizeTestSteps(AiGeneratedCase tc, String prompt) {
        List<TestStep> existing = tc.getTestSteps() != null ? new ArrayList<>(tc.getTestSteps()) : new ArrayList<>();
        if (existing.size() >= 3) {
            return existing;
        }
        List<TestStep> steps = new ArrayList<>();
        String targetUrl = (tc.getStartUrl() != null && !tc.getStartUrl().isBlank())
                ? tc.getStartUrl()
                : "Màn hình chức năng mục tiêu";
        String desc = (tc.getDescription() != null && !tc.getDescription().isBlank())
                ? tc.getDescription()
                : (prompt != null && !prompt.isBlank() ? prompt : tc.getName());

        steps.add(new TestStep(
                1,
                "NAVIGATE",
                targetUrl,
                "",
                "Truy cập màn hình chức năng kiểm thử",
                "Màn hình hiển thị đầy đủ các thành phần giao diện và sẵn sàng thao tác"
        ));
        steps.add(new TestStep(
                2,
                "INTERACT",
                "Biểu mẫu / Thành phần tương tác chính",
                "Dữ liệu đầu vào theo bộ tham số kiểm thử",
                "Nhập dữ liệu và thực hiện thao tác theo kịch bản: " + desc,
                "Hệ thống tiếp nhận dữ liệu và xử lý sự kiện không phát sinh lỗi hệ thống"
        ));
        steps.add(new TestStep(
                3,
                "VERIFY",
                "Thông báo trạng thái / Nội dung kết quả",
                "",
                "Xác minh trạng thái phản hồi và kết quả đầu ra trên giao diện",
                "Kết quả hiển thị đúng với tiêu chí nghiệm thu của kịch bản " + tc.getName()
        ));
        return steps;
    }

    private List<Assertion> synthesizeAssertions(AiGeneratedCase tc) {
        List<Assertion> list = new ArrayList<>();
        String nameLower = tc.getName() != null ? tc.getName().toLowerCase(Locale.ROOT) : "";
        boolean isNegative = nameLower.contains("negative") || nameLower.contains("khonghople")
                || nameLower.contains("sai") || nameLower.contains("loi") || nameLower.contains("botrong");

        // 1. URL verification if startUrl is available
        if (tc.getStartUrl() != null && !tc.getStartUrl().isBlank()) {
            Assertion urlAssert = new Assertion();
            urlAssert.setType("URL_CONTAINS");
            urlAssert.setLocator("");
            urlAssert.setValue(tc.getStartUrl());
            urlAssert.setStatus("ACTIVE");
            list.add(urlAssert);
        }

        // 2. Step-derived assertions (Input value / Target visibility)
        if (tc.getTestSteps() != null) {
            for (TestStep step : tc.getTestSteps()) {
                String act = step.getAction() != null ? step.getAction().toUpperCase(Locale.ROOT) : "";
                String target = step.getTarget() != null ? step.getTarget().trim() : "";
                boolean isCssOrSelector = target.startsWith("#") || target.startsWith(".")
                        || target.startsWith("[") || target.startsWith("//")
                        || target.contains("input") || target.contains("button");
                if ((act.contains("FILL") || act.contains("TYPE") || act.contains("INPUT"))
                        && isCssOrSelector && step.getTestData() != null && !step.getTestData().isBlank()) {
                    Assertion inputAssert = new Assertion();
                    inputAssert.setType("INPUT_VALUE_EQUALS");
                    inputAssert.setLocator(target);
                    inputAssert.setValue(step.getTestData());
                    inputAssert.setStatus("ACTIVE");
                    list.add(inputAssert);
                    break;
                } else if (act.contains("VERIFY") && isCssOrSelector) {
                    Assertion visAssert = new Assertion();
                    visAssert.setType("ELEMENT_VISIBLE");
                    visAssert.setLocator(target);
                    visAssert.setValue("");
                    visAssert.setStatus("ACTIVE");
                    list.add(visAssert);
                    break;
                }
            }
        }

        // 3. Primary functional outcome assertion
        if (isNegative) {
            Assertion errAssert = new Assertion();
            errAssert.setType("ELEMENT_VISIBLE");
            errAssert.setLocator("[role='alert'], .error-message, .toast, .alert-danger, body");
            errAssert.setValue("");
            errAssert.setStatus("ACTIVE");
            list.add(errAssert);
        } else {
            Assertion bodyVis = new Assertion();
            bodyVis.setType("ELEMENT_VISIBLE");
            bodyVis.setLocator("body");
            bodyVis.setValue("");
            bodyVis.setStatus("ACTIVE");
            list.add(bodyVis);
        }

        // 4. Expected text / state verification from final step
        Assertion textAssert = new Assertion();
        textAssert.setType("PAGE_HAS_TEXT");
        textAssert.setLocator("body");
        String expected = isNegative
                ? "Hiển thị thông báo lỗi kiểm tra hợp lệ"
                : "Thành công";
        if (tc.getTestSteps() != null && !tc.getTestSteps().isEmpty()) {
            TestStep last = tc.getTestSteps().get(tc.getTestSteps().size() - 1);
            if (last.getExpectedResult() != null && !last.getExpectedResult().isBlank()) {
                expected = last.getExpectedResult();
            }
        }
        textAssert.setValue(expected);
        textAssert.setStatus("ACTIVE");
        list.add(textAssert);

        return list;
    }

    // =========================================================================
    // MULTI-SCREEN SRS SEMANTIC SECTIONING & COVERAGE EXPANSION
    // =========================================================================

    /**
     * Extracts functional screen/module sections from user prompt and attached SRS documents.
     */
    public static List<ScreenSpecSection> extractScreenSections(String prompt,
                                                               List<DocumentParserService.ParsedDocument> attachedDocs) {
        List<ScreenSpecSection> screens = new ArrayList<>();
        int requestedCount = extractRequestedScreenCount(prompt);

        // 1. First check attached documents
        if (attachedDocs != null && !attachedDocs.isEmpty()) {
            for (DocumentParserService.ParsedDocument doc : attachedDocs) {
                if (doc == null || doc.isImage()) continue;
                String raw = doc.getContent();
                if (raw == null || raw.isBlank()) continue;

                // Strip YAML frontmatter & page markers
                String text = YAML_FRONTMATTER_PATTERN.matcher(raw).replaceFirst("");
                text = PDF_PAGE_HEADING_PATTERN.matcher(text).replaceAll("");

                List<RawHeadingSection> rawSections = splitByFunctionalHeadings(text);
                int idx = 1;
                for (RawHeadingSection rawSec : rawSections) {
                    String cleanTitle = cleanScreenTitle(rawSec.heading);
                    String token = toAsciiPascalCase(cleanTitle);
                    if (token.isBlank() || token.length() < 3) {
                        token = "ManHinh" + idx;
                    }
                    if (!token.toLowerCase(Locale.ROOT).startsWith("manhinh")
                            && !token.toLowerCase(Locale.ROOT).startsWith("chucnang")
                            && !token.toLowerCase(Locale.ROOT).startsWith("trang")) {
                        token = "ManHinh_" + token;
                    }

                    List<String> fields = extractFieldsFromSection(rawSec.body);
                    List<String> rules = extractRulesFromSection(rawSec.body);
                    String url = extractTargetUrlFromSection(rawSec.body);

                    screens.add(new ScreenSpecSection(idx, cleanTitle, token, rawSec.body, fields, rules, url));
                    idx++;
                    if (requestedCount > 0 && screens.size() >= requestedCount) {
                        break;
                    }
                }
                if (!screens.isEmpty()) {
                    break; // Primary document processed
                }
            }
        }

        // 2. If no screens extracted from docs or docs are empty, check user prompt for screens list
        if (screens.isEmpty() && prompt != null && !prompt.isBlank()) {
            List<String> promptLines = prompt.lines()
                    .map(String::trim)
                    .filter(l -> !l.isBlank())
                    .toList();
            int idx = 1;
            for (String line : promptLines) {
                if (line.matches("(?i)^[-*•\\d.]+\\s+(?:Màn\\s+hình|Trang|Chức\\s+năng|Module|Screen|UC-?\\d+).*")
                        || line.matches("(?i)^(?:Màn\\s+hình|Trang|Chức\\s+năng|Module|Screen|UC-?\\d+)\\s*[:\\-–\\d].*")) {
                    String cleanTitle = cleanScreenTitle(line.replaceFirst("^[-*•\\d.]+\\s*", ""));
                    String token = toAsciiPascalCase(cleanTitle);
                    if (token.isBlank()) token = "ManHinh" + idx;
                    if (!token.toLowerCase(Locale.ROOT).startsWith("manhinh")) {
                        token = "ManHinh_" + token;
                    }
                    screens.add(new ScreenSpecSection(idx, cleanTitle, token, line, List.of(), List.of(), ""));
                    idx++;
                }
            }
        }

        // 3. If requestedCount > screens.size(), synthesize indexed screen placeholders to meet the user's explicit request
        if (requestedCount > 0 && screens.size() < requestedCount) {
            int existing = screens.size();
            for (int i = existing + 1; i <= requestedCount; i++) {
                String title = "Màn hình chức năng " + i;
                String token = "ManHinh" + String.format("%02d", i);
                screens.add(new ScreenSpecSection(i, title, token, title, List.of(), List.of(), ""));
            }
        }

        return screens;
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

    /**
     * Expands test cases across all extracted screens so every screen gets {@code minCasesPerScreen}
     * test cases (Happy Path, Negative Validation, Boundary BVA, Business Rule/Permission).
     */
    public List<AiGeneratedCase> expandMultiScreenCoverage(List<AiGeneratedCase> existingCases,
                                                           List<ScreenSpecSection> screens,
                                                           int minCasesPerScreen,
                                                           String prompt,
                                                           boolean generateScript) {
        List<AiGeneratedCase> result = new ArrayList<>();
        Map<Integer, List<AiGeneratedCase>> screenBuckets = new LinkedHashMap<>();
        for (int i = 0; i < screens.size(); i++) {
            screenBuckets.put(i, new ArrayList<>());
        }

        // Match existing cases into screen buckets
        for (AiGeneratedCase tc : existingCases) {
            int matched = matchCaseToScreenIndex(tc, screens);
            if (matched >= 0 && matched < screens.size()) {
                screenBuckets.get(matched).add(tc);
            } else {
                // If not matched, place in first available under-filled bucket or append
                int under = findUnderfilledBucket(screenBuckets, minCasesPerScreen);
                if (under >= 0) {
                    screenBuckets.get(under).add(tc);
                } else {
                    result.add(tc);
                }
            }
        }

        // Ensure every screen has at least minCasesPerScreen
        for (int i = 0; i < screens.size(); i++) {
            ScreenSpecSection screen = screens.get(i);
            List<AiGeneratedCase> bucket = screenBuckets.get(i);

            if (bucket.isEmpty()) {
                // Synthesize full 4-case suite for this screen
                List<AiGeneratedCase> synthesized = synthesizeCasesForScreen(screen, minCasesPerScreen, prompt, generateScript);
                result.addAll(synthesized);
            } else if (bucket.size() < minCasesPerScreen) {
                result.addAll(bucket);
                AiGeneratedCase base = bucket.get(0);
                List<AiGeneratedCase> companions = synthesizeScreenCompanionCases(
                        base, screen, minCasesPerScreen - bucket.size(), generateScript);
                result.addAll(companions);
            } else {
                result.addAll(bucket);
            }
        }

        return result;
    }

    private static int matchCaseToScreenIndex(AiGeneratedCase tc, List<ScreenSpecSection> screens) {
        if (tc == null) return -1;
        String name = (tc.getName() != null ? tc.getName() : "").toLowerCase(Locale.ROOT);
        String desc = (tc.getDescription() != null ? tc.getDescription() : "").toLowerCase(Locale.ROOT);

        for (int i = 0; i < screens.size(); i++) {
            ScreenSpecSection s = screens.get(i);
            String token = s.featureToken().toLowerCase(Locale.ROOT).replace("manhinh_", "").replace("manhinh", "");
            String title = s.screenTitle().toLowerCase(Locale.ROOT);
            if (!token.isBlank() && (name.contains(token) || desc.contains(token))) {
                return i;
            }
            if (!title.isBlank() && (name.contains(title) || desc.contains(title))) {
                return i;
            }
        }
        return -1;
    }

    private static int findUnderfilledBucket(Map<Integer, List<AiGeneratedCase>> buckets, int target) {
        for (var entry : buckets.entrySet()) {
            if (entry.getValue().size() < target) {
                return entry.getKey();
            }
        }
        return -1;
    }

    private List<AiGeneratedCase> synthesizeCasesForScreen(ScreenSpecSection screen,
                                                          int count,
                                                          String prompt,
                                                          boolean generateScript) {
        List<AiGeneratedCase> list = new ArrayList<>();
        String token = screen.featureToken();
        String title = screen.screenTitle();
        String url = screen.targetUrl();

        // 1. Happy Path (P0)
        AiGeneratedCase happy = new AiGeneratedCase();
        happy.setName("TC01_" + token + "_LuongChinh_HappyPath");
        happy.setPriority("P0");
        happy.setStartUrl(url);
        happy.setDescription("Kiểm thử luồng thành công chính (Happy Path) trên " + title
                + " khi điền đầy đủ dữ liệu hợp lệ");
        happy.setTestSteps(buildScreenTestSteps(screen, 1));
        happy.setTestDataSets(synthesizeTestDataSets(happy, 1));
        happy.setAssertions(synthesizeAssertions(happy));
        if (generateScript && !url.isBlank()) {
            happy.setScriptCode(synthesizeScenarioScript(happy));
        } else {
            happy.setScriptCode("");
        }
        list.add(happy);

        // 2. Negative Validation (P1)
        if (count >= 2) {
            AiGeneratedCase neg = new AiGeneratedCase();
            neg.setName("TC02_" + token + "_KiemTraLoiDuLieuKhongHopLe");
            neg.setPriority("P1");
            neg.setStartUrl(url);
            neg.setDescription("Kiểm thử thông báo lỗi & ngăn chặn submit trên " + title
                    + " khi bỏ trống trường bắt buộc hoặc nhập sai định dạng");
            neg.setTestSteps(buildScreenTestSteps(screen, 2));
            neg.setTestDataSets(synthesizeTestDataSets(neg, 2));
            neg.setAssertions(synthesizeAssertions(neg));
            if (generateScript && !url.isBlank()) {
                neg.setScriptCode(synthesizeScenarioScript(neg));
            } else {
                neg.setScriptCode("");
            }
            list.add(neg);
        }

        // 3. Boundary BVA (P1/P2)
        if (count >= 3) {
            AiGeneratedCase bva = new AiGeneratedCase();
            bva.setName("TC03_" + token + "_GiaTriBien_BoundaryBVA");
            bva.setPriority("P1");
            bva.setStartUrl(url);
            bva.setDescription("Kiểm thử phân tích giá trị biên (Min, Max, Max+1, ký tự Unicode đặc biệt) trên các trường dữ liệu của " + title);
            bva.setTestSteps(buildScreenTestSteps(screen, 3));
            bva.setTestDataSets(synthesizeTestDataSets(bva, 3));
            bva.setAssertions(synthesizeAssertions(bva));
            if (generateScript && !url.isBlank()) {
                bva.setScriptCode(synthesizeScenarioScript(bva));
            } else {
                bva.setScriptCode("");
            }
            list.add(bva);
        }

        // 4. Business Rule / Permission (P2)
        if (count >= 4) {
            AiGeneratedCase ruleCase = new AiGeneratedCase();
            ruleCase.setName("TC04_" + token + "_QuyTacNghiepVuVaPhanQuyen");
            ruleCase.setPriority("P2");
            ruleCase.setStartUrl(url);
            ruleCase.setDescription("Kiểm thử quy tắc nghiệp vụ đặc thù, trùng lặp dữ liệu và phân quyền thao tác trên " + title);
            ruleCase.setTestSteps(buildScreenTestSteps(screen, 4));
            ruleCase.setTestDataSets(List.of(
                    new TestDataItem(
                            "Quy tắc nghiệp vụ & Phân quyền",
                            Map.of("role", "STANDARD_USER", "action", "RESTRICTED"),
                            "Hệ thống áp dụng đúng ràng buộc nghiệp vụ hoặc thông báo quyền hạn"
                    )
            ));
            ruleCase.setAssertions(synthesizeAssertions(ruleCase));
            if (generateScript && !url.isBlank()) {
                ruleCase.setScriptCode(synthesizeScenarioScript(ruleCase));
            } else {
                ruleCase.setScriptCode("");
            }
            list.add(ruleCase);
        }

        return list;
    }

    private List<AiGeneratedCase> synthesizeScreenCompanionCases(AiGeneratedCase base,
                                                                ScreenSpecSection screen,
                                                                int needed,
                                                                boolean generateScript) {
        List<AiGeneratedCase> companions = new ArrayList<>();
        String token = screen != null ? screen.featureToken() : "Screen";
        String url = base.getStartUrl() != null ? base.getStartUrl() : "";

        if (needed >= 1) {
            AiGeneratedCase neg = new AiGeneratedCase();
            neg.setName("TC02_" + token + "_KiemTraLoiValidation");
            neg.setPriority("P1");
            neg.setStartUrl(url);
            neg.setDescription("Kiểm tra xử lý ngoại lệ và hiển thị thông báo lỗi khi dữ liệu nhập vi phạm quy tắc");
            neg.setTestSteps(buildScreenTestSteps(screen, 2));
            neg.setTestDataSets(synthesizeTestDataSets(neg, 2));
            neg.setAssertions(synthesizeAssertions(neg));
            if (generateScript && !url.isBlank()) {
                neg.setScriptCode(synthesizeScenarioScript(neg));
            } else {
                neg.setScriptCode("");
            }
            companions.add(neg);
        }

        if (needed >= 2) {
            AiGeneratedCase bva = new AiGeneratedCase();
            bva.setName("TC03_" + token + "_GiaTriBienBVA");
            bva.setPriority("P1");
            bva.setStartUrl(url);
            bva.setDescription("Kiểm thử giá trị biên độ dài tối đa/tối thiểu và ký tự đặc biệt");
            bva.setTestSteps(buildScreenTestSteps(screen, 3));
            bva.setTestDataSets(synthesizeTestDataSets(bva, 3));
            bva.setAssertions(synthesizeAssertions(bva));
            if (generateScript && !url.isBlank()) {
                bva.setScriptCode(synthesizeScenarioScript(bva));
            } else {
                bva.setScriptCode("");
            }
            companions.add(bva);
        }

        if (needed >= 3) {
            AiGeneratedCase ruleCase = new AiGeneratedCase();
            ruleCase.setName("TC04_" + token + "_QuyTacNghiepVu");
            ruleCase.setPriority("P2");
            ruleCase.setStartUrl(url);
            ruleCase.setDescription("Kiểm tra ràng buộc nghiệp vụ và phân quyền truy cập");
            ruleCase.setTestSteps(buildScreenTestSteps(screen, 4));
            ruleCase.setTestDataSets(List.of(
                    new TestDataItem("Ràng buộc nghiệp vụ", Map.of("constraint", "CHECK"), "Hệ thống bảo đảm tính toàn vẹn dữ liệu")
            ));
            ruleCase.setAssertions(synthesizeAssertions(ruleCase));
            if (generateScript && !url.isBlank()) {
                ruleCase.setScriptCode(synthesizeScenarioScript(ruleCase));
            } else {
                ruleCase.setScriptCode("");
            }
            companions.add(ruleCase);
        }

        return companions;
    }

    private List<TestStep> buildScreenTestSteps(ScreenSpecSection screen, int scenarioType) {
        List<TestStep> steps = new ArrayList<>();
        String url = (screen != null && screen.targetUrl() != null && !screen.targetUrl().isBlank())
                ? screen.targetUrl() : "Màn hình chức năng";
        String title = screen != null ? screen.screenTitle() : "Màn hình";
        List<String> fields = (screen != null && screen.extractedFields() != null) ? screen.extractedFields() : List.of();

        steps.add(new TestStep(1, "NAVIGATE", url, "",
                "Truy cập " + title, "Màn hình hiển thị đầy đủ các trường nhập liệu và nút chức năng"));

        if (!fields.isEmpty()) {
            int stepNum = 2;
            for (int f = 0; f < Math.min(fields.size(), 3); f++) {
                String field = fields.get(f);
                String data = switch (scenarioType) {
                    case 2 -> "Bỏ trống (\"\") hoặc ký tự sai (!@#$)";
                    case 3 -> "Giá trị biên: Min=1, Max=255, Max+1=256";
                    case 4 -> "Dữ liệu kiểm tra ràng buộc nghiệp vụ / phân quyền";
                    default -> "Dữ liệu hợp lệ chuẩn (Valid Data)";
                };
                steps.add(new TestStep(stepNum++, "FILL", field, data,
                        "Nhập dữ liệu vào trường " + field, "Trường tiếp nhận và hiển thị dữ liệu kiểm tra"));
            }
            steps.add(new TestStep(stepNum, "CLICK", "Nút Xác nhận / Lưu / Gửi", "",
                    "Nhấn nút xác nhận thực thi hành động",
                    scenarioType == 2
                            ? "Hệ thống chặn submit và hiển thị thông báo lỗi tương ứng"
                            : "Hệ thống xử lý thành công và cập nhật trạng thái"));
        } else {
            steps.add(new TestStep(2, "INTERACT", "Các trường biểu mẫu",
                    scenarioType == 2 ? "Dữ liệu rỗng / sai" : "Dữ liệu hợp lệ",
                    "Thao tác nhập liệu trên " + title, "Hệ thống tiếp nhận thông tin"));
            steps.add(new TestStep(3, "CLICK", "Nút Xác nhận", "",
                    "Thực hiện gửi yêu cầu", "Hệ thống phản hồi trạng thái đúng kỳ vọng"));
        }
        return steps;
    }

    /**
     * Groups existing test cases by their feature token (e.g. TC01_DangNhap_... -> DangNhap).
     */
    public static Map<String, List<AiGeneratedCase>> groupCasesByFeatureToken(List<AiGeneratedCase> cases, String defaultToken) {
        Map<String, List<AiGeneratedCase>> groups = new LinkedHashMap<>();
        if (cases == null || cases.isEmpty()) return groups;

        for (AiGeneratedCase c : cases) {
            String name = c.getName() != null ? c.getName() : "";
            Matcher m = CASE_FEATURE_PATTERN.matcher(name);
            String token = defaultToken;
            if (m.matches()) {
                String extracted = m.group(1);
                if (extracted != null && extracted.length() >= 3
                        && !extracted.equalsIgnoreCase("Case")
                        && !extracted.equalsIgnoreCase("Test")
                        && !extracted.equalsIgnoreCase("Recovered")) {
                    token = extracted;
                }
            }
            groups.computeIfAbsent(token, k -> new ArrayList<>()).add(c);
        }
        return groups;
    }

    /**
     * Expands under-filled feature groups so each group has at least {@code minPerGroup} test cases.
     */
    public List<AiGeneratedCase> expandFeatureGroupCoverage(Map<String, List<AiGeneratedCase>> featureGroups,
                                                            int minPerGroup,
                                                            String prompt,
                                                            boolean generateScript) {
        List<AiGeneratedCase> result = new ArrayList<>();
        for (var entry : featureGroups.entrySet()) {
            String token = entry.getKey();
            List<AiGeneratedCase> bucket = entry.getValue();
            result.addAll(bucket);
            if (bucket.size() < minPerGroup && !bucket.isEmpty()) {
                AiGeneratedCase base = bucket.get(0);
                List<AiGeneratedCase> companions = synthesizeCompanionScenarios(base, token, prompt, generateScript);
                for (AiGeneratedCase comp : companions) {
                    if (result.size() >= bucket.size() + minPerGroup) break;
                    result.add(comp);
                }
            }
        }
        return result;
    }

    public List<TestDataItem> synthesizeTestDataSets(AiGeneratedCase tc, int caseNum) {
        List<TestDataItem> data = new ArrayList<>();
        String nameLower = tc.getName() != null ? tc.getName().toLowerCase(Locale.ROOT) : "";
        if (nameLower.contains("negative") || nameLower.contains("khonghople") || nameLower.contains("sai") || caseNum == 2) {
            data.add(new TestDataItem(
                    "Phân vùng Không hợp lệ / Bỏ trống (Invalid/Empty)",
                    Map.of("input", "", "specialChars", "!@#$%^&*"),
                    "Hiển thị thông báo lỗi validation tương ứng, từ chối xử lý"
            ));
        } else if (nameLower.contains("boundary") || nameLower.contains("bien") || caseNum == 3) {
            data.add(new TestDataItem(
                    "Giá trị biên Min / Max (Boundary BVA)",
                    Map.of("minLength", "1", "maxLength", "255", "overMax", "256"),
                    "Chấp nhận tại biên hợp lệ (Min/Max) và cảnh báo khi vượt biên (Max+1)"
            ));
        } else {
            data.add(new TestDataItem(
                    "Phân vùng tương đương Hợp lệ (Valid Happy Path)",
                    Map.of("status", "VALID", "input", "StandardData"),
                    "Thực thi thành công và cập nhật đúng trạng thái nghiệp vụ"
            ));
        }
        return data;
    }
}
