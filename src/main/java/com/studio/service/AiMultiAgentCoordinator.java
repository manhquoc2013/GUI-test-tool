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

        return """
                === QUY TRÌNH ĐA TÁC NHÂN (MULTI-AGENT VERIFICATION & LOOP PIPELINE) ===
                Bạn vận hành điều phối 4 Tác nhân AI chuyên biệt (Spec Miner -> Test Architect -> Reality Checker -> Loop Engineer):
                1. [Agent 1 - Spec Miner (Phân tích đặc tả & Định danh Chức năng)]:
                   - Bóc tách yêu cầu thành các module chức năng, điều kiện tiên quyết (Preconditions), phân vùng tương đương (EP) và giá trị biên (BVA).
                   - BẮT BUỘC đặt tên `"suggestedGroup"` (Nhóm chức năng/Phân hệ) và `"suggestedSuite"` (Bộ kịch bản kiểm thử) ĐÚNG THEO TÊN CHỨC NĂNG VÀ TEST CASE.
                   - Gợi ý tham chiếu từ ngữ cảnh hiện tại: suggestedGroup = "%s", suggestedSuite = "%s" (Hãy tinh chỉnh chính xác hơn theo đúng nghiệp vụ thực tế của bộ Test Case).
                   - TUYỆT ĐỐI KHÔNG đặt tên chung chung như "AI-Tests", "AI-Generated-Suite", "Default", "Test Suite".
                2. [Agent 2 - Test Architect & Script Engineer (Thiết kế Test Case & Kịch bản)]:
                   - Áp dụng kỹ năng chuyên sâu [%s] để thiết kế đầy đủ các kịch bản: Happy Path (P0), Negative/Validation (P1), Boundary/Edge Cases (P1/P2).
                   - Tên mỗi Test Case PHẢI tuân thủ chuẩn: `TC<SốThứTự>_<TênChứcNăng>_<KịchBảnCụThể>` (Ví dụ: `TC01_DangNhap_ThanhCong`, `TC02_DangNhap_SaiMatKhau`).
                   - Mỗi Test Case PHẢI có đầy đủ `testSteps` (ít nhất 3 bước rõ ràng), `testDataSets` (bộ dữ liệu biên/tương đương) và `assertions`.
                   - %s
                3. [Agent 3 - Reality Checker (Thẩm định chất lượng khắt khe)]:
                   - Tự rà soát chéo toàn bộ kết quả theo 5 cổng chất lượng: (1) Đặt tên Nhóm/Suite/Test Case chuẩn chức năng, (2) Đủ độ phủ kịch bản âm/dương/biên, (3) Đủ các bước thực thi & kết quả kỳ vọng, (4) Assertions chính xác, (5) Mã kịch bản tất định không dùng sleep cứng.
                4. [Agent 4 - Loop Engineer (Tự hoàn thiện trước khi xuất)]:
                   - Tự động bổ sung và sửa mọi điểm khuyết thiếu phát hiện ở bước 3 và ghi tóm tắt đánh giá vào trường `"agentReview"`.
                """.formatted(
                preInferredGroup,
                preInferredSuite,
                resolvedSkill != null ? resolvedSkill.getDisplayName() : AiSkill.PLAYWRIGHT_E2E.getDisplayName(),
                generateScript
                        ? "CHẾ ĐỘ SINH SCRIPT BẬT: Viết mã Playwright Java hoàn chỉnh trong `scriptCode`, tuyệt đối không dùng Thread.sleep/waitForTimeout."
                        : "CHẾ ĐỘ CHỈ TẠO TEST CASE (KHÔNG SINH SCRIPT): Để trống `scriptCode: \"\"`, tập trung tối đa vào độ chi tiết của `testSteps`, `testDataSets` và `assertions`."
        );
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

                // 5. Heal Assertions if empty
                List<Assertion> assertions = tc.getAssertions();
                if (assertions == null || assertions.isEmpty()) {
                    List<Assertion> synthesizedAsserts = synthesizeAssertions(tc);
                    tc.setAssertions(synthesizedAsserts);
                    improvements.add("Bổ sung " + synthesizedAsserts.size() + " tiêu chí nghiệm thu (Assertions) cho " + tc.getName());
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
                    if (code != null && !code.isBlank() && generatorService != null) {
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
        String clean = noAccent.replaceAll("[^A-Za-z0-9_]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return clean.isBlank() ? "Scenario" : clean;
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
        Assertion a = new Assertion();
        if (tc.getStartUrl() != null && !tc.getStartUrl().isBlank()) {
            a.setType("URL_CONTAINS");
            a.setLocator("");
            a.setValue(tc.getStartUrl());
        } else {
            a.setType("PAGE_HAS_TEXT");
            a.setLocator("body");
            String expected = "Thành công";
            if (tc.getTestSteps() != null && !tc.getTestSteps().isEmpty()) {
                TestStep last = tc.getTestSteps().get(tc.getTestSteps().size() - 1);
                if (last.getExpectedResult() != null && !last.getExpectedResult().isBlank()) {
                    expected = last.getExpectedResult();
                }
            }
            a.setValue(expected);
        }
        a.setStatus("ACTIVE");
        list.add(a);
        return list;
    }

    private List<TestDataItem> synthesizeTestDataSets(AiGeneratedCase tc, int caseNum) {
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
