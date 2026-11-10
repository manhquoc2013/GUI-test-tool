package com.studio.service;

import com.studio.model.AiClarificationQuestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service to analyze user prompt intent, detect requirement ambiguities,
 * and synthesize structured interactive clarification questions.
 */
public class AiAmbiguityAnalyzer {

    public enum Intent {
        SEARCH_TESTS,
        EVALUATE_TEST,
        GAP_ANALYSIS,
        CHAT_ADVICE,
        GENERATE_SCENARIOS,
        GENERATE_TESTS
    }

    public record AnalysisResult(
            Intent intent,
            boolean needsClarification,
            String targetEntity,
            List<AiClarificationQuestion> questions,
            String initialClarificationMessage) {
    }

    /**
     * Checks if the user prompt explicitly requests test script generation, automation code, or Playwright scripts.
     */
    public static boolean containsScriptOrAutomationRequest(String prompt) {
        if (prompt == null || prompt.isBlank()) return false;
        String p = prompt.toLowerCase(Locale.ROOT);
        return p.contains("test script") || p.contains("test scripts")
                || p.contains("viết script") || p.contains("viet script")
                || p.contains("sinh script") || p.contains("tạo script")
                || p.contains("viết code") || p.contains("viet code")
                || p.contains("sinh code") || p.contains("code test")
                || p.contains("automation") || p.contains("tự động hóa") || p.contains("tu dong hoa")
                || p.contains("playwright");
    }

    /**
     * Checks if the user prompt specifically requests ONLY test scenarios / kịch bản kiểm thử,
     * without automation test scripts or code.
     */
    public static boolean isScenarioOnlyRequest(String prompt) {
        if (prompt == null || prompt.isBlank()) return false;
        String p = prompt.toLowerCase(Locale.ROOT);
        boolean hasScenarioKeyword = p.contains("scenario") || p.contains("scenarios")
                || p.contains("scenrio") || p.contains("kịch bản") || p.contains("kich ban");
        boolean hasCaseOrScriptKeyword = p.contains("test case") || p.contains("test cases")
                || p.contains("testcase") || p.contains("testcases")
                || p.contains("trường hợp kiểm thử") || p.contains("truong hop kiem thu")
                || p.contains("ca kiểm thử") || p.contains("ca kiem thu")
                || containsScriptOrAutomationRequest(p);

        return hasScenarioKeyword && !hasCaseOrScriptKeyword;
    }

    /**
     * Classifies user intent from prompt text.
     */
    public Intent detectIntent(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return Intent.GENERATE_TESTS;
        }
        String p = prompt.trim().toLowerCase(Locale.ROOT);

        // 1. Search intent
        if (p.startsWith("tìm ") || p.contains("tìm test") || p.contains("tìm case")
                || p.contains("search case") || p.contains("danh sách case")
                || p.contains("danh sách test") || p.contains("case nào có")
                || p.contains("tìm kiếm test")) {
            return Intent.SEARCH_TESTS;
        }

        // 2. Evaluation / Review intent
        if (p.startsWith("đánh giá") || p.contains("đánh giá case") || p.contains("đánh giá test")
                || p.contains("review case") || p.contains("chấm điểm")
                || p.contains("audit case") || p.contains("kiểm tra chất lượng case")
                || p.contains("evaluate case")) {
            return Intent.EVALUATE_TEST;
        }

        // 3. Gap analysis intent
        if (p.contains("thiếu test") || p.contains("khoảng trống") || p.contains("gap analysis")
                || p.contains("gợi ý thêm case") || p.contains("còn thiếu gì")
                || p.contains("thiếu kịch bản gì") || p.contains("độ bao phủ")
                || p.contains("độ phủ") || p.contains("coverage")) {
            return Intent.GAP_ANALYSIS;
        }

        // 4. Chat / Advice intent (questions without generation verbs)
        boolean hasGenVerb = p.contains("viết") || p.contains("tạo") || p.contains("sinh")
                || p.contains("generate") || p.contains("build") || p.contains("thêm test");
        if (!hasGenVerb && (p.contains("làm sao") || p.contains("như thế nào") || p.contains("tại sao")
                || p.contains("giải thích") || p.contains("là gì") || p.contains("cách chờ")
                || p.contains("selector nào") || p.contains("best practice") || p.contains("playwright có"))) {
            return Intent.CHAT_ADVICE;
        }

        // 5. Test Scenario only intent (QA Specification without automation code)
        if (isScenarioOnlyRequest(p)) {
            return Intent.GENERATE_SCENARIOS;
        }

        return Intent.GENERATE_TESTS;
    }

    /**
     * Extracts target entity name (e.g. case name for evaluation or keyword for
     * search).
     */
    public String extractTargetEntity(String prompt, Intent intent) {
        if (prompt == null || prompt.isBlank())
            return "";
        String p = prompt.trim();

        if (intent == Intent.SEARCH_TESTS) {
            return p.replaceAll("^(?i)(tìm kiếm|tìm|search)\\s+(test\\s+case(s)?|case(s)?|kịch bản|scenario(s)?)?\\s*", "").trim();
        }

        if (intent == Intent.EVALUATE_TEST) {
            return p.replaceAll(
                    "^(?i)(đánh giá|review|chấm điểm|audit|kiểm tra)\\s+(chất lượng\\s+)?(test\\s+case(s)?|case(s)?|kịch bản|scenario(s)?)?\\s*",
                    "").trim();
        }

        return p;
    }

    public AnalysisResult analyze(String prompt, boolean hasAttachments, boolean hasLiveDom) {
        Intent intent = detectIntent(prompt);
        if (hasAttachments) {
            // Backward compatibility when parsed document objects are not provided
            return new AnalysisResult(intent, false, extractTargetEntity(prompt, intent), List.of(), "");
        }
        return analyze(prompt, false, hasLiveDom, List.of());
    }

    /**
     * Determines whether test generation requires clarification from user.
     * Analyzes both user prompt and attached documents to detect ambiguities,
     * missing target URLs, multi-screen partitioning needs, and authentication preconditions.
     */
    public AnalysisResult analyze(String prompt,
                                  boolean hasAttachments,
                                  boolean hasLiveDom,
                                  List<DocumentParserService.ParsedDocument> attachedDocs) {
        Intent intent = detectIntent(prompt);
        String target = extractTargetEntity(prompt, intent);

        if (intent != Intent.GENERATE_TESTS && intent != Intent.GENERATE_SCENARIOS) {
            return new AnalysisResult(intent, false, target, List.of(), "");
        }

        String trimmed = prompt != null ? prompt.trim() : "";
        String lower = trimmed.toLowerCase(Locale.ROOT);
        boolean isClarified = lower.contains("[phạm vi]") || lower.contains("[tiền điều kiện]")
                || lower.contains("[xác minh]") || lower.contains("[scope]") || lower.contains("[precondition]")
                || lower.contains("[chiến lược]") || lower.contains("[màn hình]");

        // If user already answered interactive questions in this turn, proceed directly
        if (isClarified) {
            return new AnalysisResult(intent, false, target, List.of(), "");
        }

        // Case A: Large SRS Document Attached -> Detect Multi-Screen Ambiguities & Scope Decisions
        if (hasAttachments && attachedDocs != null && !attachedDocs.isEmpty()) {
            List<AiMultiAgentCoordinator.ScreenSpecSection> screens =
                    AiMultiAgentCoordinator.extractScreenSections(trimmed, attachedDocs);

            // If a multi-screen or large document (>2 screens or >4,000 characters) is detected,
            // ask structured clarification on Screen Scope, Test Depth, and Script Generation
            boolean isLargeSrs = screens.size() >= 2 || attachedDocs.stream().anyMatch(d -> d != null && d.getSizeBytes() > 4000);
            if (isLargeSrs) {
                List<AiClarificationQuestion> srsQuestions = generateLargeSrsClarificationQuestions(screens, trimmed, intent);
                String docName = attachedDocs.get(0).getFileName();
                String screenSummary = screens.size() >= 2
                        ? "phát hiện **" + screens.size() + " màn hình / phân hệ chức năng**"
                        : "phát hiện tài liệu đặc tả chi tiết";

                String taskType = (intent == Intent.GENERATE_SCENARIOS)
                        ? "thiết kế bộ kịch bản kiểm thử (Test Scenarios)"
                        : "thiết kế bộ Test Cases & Test Scripts";

                String summary = "🔍 **[Agent 1 - Spec Miner]** Đã phân tích tài liệu đính kèm **`" + docName + "`** và "
                        + screenSummary + ".\n\n"
                        + "Để đội ngũ 4 Tác nhân AI phối hợp " + taskType + " **đầy đủ, bao quát toàn bộ trường hợp** mà không bị sót hoặc chung chung, vui lòng xác nhận các điểm mờ sau:";

                return new AnalysisResult(intent, true, target, srsQuestions, summary);
            }

            return new AnalysisResult(intent, false, target, List.of(), "");
        }

        // Case B: Live DOM scanned -> High UI context available
        if (hasLiveDom) {
            return new AnalysisResult(intent, false, target, List.of(), "");
        }

        boolean hasUrl = lower.contains("http://") || lower.contains("https://");
        boolean hasSteps = trimmed.contains("\n-") || trimmed.contains("\n1.") || trimmed.contains("bước 1")
                || trimmed.contains("step 1");
        boolean isDetailed = trimmed.length() > 140 && (hasUrl || hasSteps);

        if (isDetailed) {
            return new AnalysisResult(intent, false, target, List.of(), "");
        }

        // Case C: Vague or brief prompt -> Needs clarification
        List<AiClarificationQuestion> questions = generateClarificationQuestions(trimmed);
        String summary = "Tôi đã phân tích yêu cầu **\""
                + (trimmed.length() > 50 ? trimmed.substring(0, 47) + "..." : trimmed)
                + "\"**. Để bộ test case sinh ra đầy đủ, chính xác và không bị sơ sài, vui lòng làm rõ một số điểm mờ dưới đây:";

        return new AnalysisResult(intent, true, target, questions, summary);
    }

    private List<AiClarificationQuestion> generateLargeSrsClarificationQuestions(
            List<AiMultiAgentCoordinator.ScreenSpecSection> screens, String prompt, Intent intent) {
        List<AiClarificationQuestion> list = new ArrayList<>();
        int screenCount = screens.size();

        // Question 1: Screen Coverage & Partitioning Strategy
        String q1Title = screenCount >= 2
                ? "1. Phạm vi bao phủ màn hình (" + screenCount + " màn hình được phát hiện trong SRS):"
                : "1. Chiến lược phân tách phạm vi kiểm thử từ SRS:";
        List<String> q1Options = new ArrayList<>();
        if (screenCount >= 2) {
            q1Options.add("(Khuyên dùng) Bao quát toàn bộ " + screenCount + " màn hình: Phối hợp đa tác nhân thiết kế tối thiểu 3 - 5 Test Cases cho MỖI màn hình (tổng ~" + (screenCount * 4) + "+ cases)");
            q1Options.add("Tập trung sâu vào 3 màn hình nghiệp vụ trọng tâm (Core Flows) trước");
            q1Options.add("Chỉ sinh 1 - 2 kịch bản E2E xuyên suốt luồng người dùng giữa các màn hình");
        } else {
            q1Options.add("(Khuyên dùng) Đầy đủ các luồng: Happy Path + Negative Validation + Giá trị biên (BVA) + Quy tắc nghiệp vụ");
            q1Options.add("Tập trung vào kiểm thử xác thực dữ liệu biên và thông báo lỗi (Negative & Boundary)");
            q1Options.add("Chỉ luồng chính thành công (Happy Path)");
        }
        list.add(new AiClarificationQuestion("q_srs_screens", q1Title, q1Options, false, true));

        // Question 2: Preconditions, Authentication & Test Data
        list.add(new AiClarificationQuestion(
                "q_precondition",
                "2. Trạng thái tiền đề (Preconditions) & Dữ liệu kiểm thử:",
                List.of(
                        "(Khuyên dùng) Tự động bọc bước Đăng nhập tiền đề & chuẩn bị dữ liệu mẫu tương ứng cho từng màn hình",
                        "Hệ thống đã ở trạng thái đã đăng nhập sẵn (Bỏ qua form login, thao tác trực tiếp trên màn hình)",
                        "Kiểm thử cả phân quyền người dùng (User thường vs Admin / Phân quyền chức năng)"),
                false,
                true));

        // Question 3: Output Deliverables (NLP Test Steps vs Runnable Playwright Code)
        if (intent == Intent.GENERATE_SCENARIOS) {
            list.add(new AiClarificationQuestion(
                    "q_output_type",
                    "3. Định dạng tài liệu kịch bản kiểm thử (Test Scenario Deliverable):",
                    List.of(
                            "(Khuyên dùng) Đặc tả Kịch bản kiểm thử (Test Scenario) chuẩn QA/ISTQB - Xuất Excel (.xlsx) / Markdown / JSON (Không sinh mã Playwright)",
                            "Đặc tả Kịch bản chi tiết kèm Ma trận bao phủ chức năng (Traceability Matrix)",
                            "Bộ kịch bản tóm tắt các luồng người dùng (User Journey Flows)"),
                    false,
                    true));
        } else {
            boolean hasUrl = prompt != null && (prompt.contains("http://") || prompt.contains("https://"));
            list.add(new AiClarificationQuestion(
                    "q_output_type",
                    "3. Kết quả đầu ra mong muốn của đợt sinh này:",
                    List.of(
                            hasUrl
                                    ? "(Khuyên dùng) Bộ kịch bản đầy đủ kép: Đặc tả QA chi tiết (Steps, Data, Assertions) + Mã Playwright Java tự động hóa"
                                    : "(Khuyên dùng) Thiết kế bộ Test Case QA/ISTQB chi tiết (Các bước, BVA, Tiêu chí nghiệm thu) - Tối ưu cho tài liệu chưa có URL thật",
                            "Sinh kèm mã Playwright Java mẫu (dùng URL demo / startUrl nội bộ)",
                            "Xuất ma trận kiểm thử (Test Matrix) & danh sách kịch bản ra Excel (.xlsx)"),
                    false,
                    true));
        }

        return list;
    }

    private List<AiClarificationQuestion> generateClarificationQuestions(String prompt) {
        List<AiClarificationQuestion> list = new ArrayList<>();
        String p = prompt.toLowerCase(Locale.ROOT);

        // Question 1: Scope & Coverage
        list.add(new AiClarificationQuestion(
                "q_scope",
                "1. Phạm vi kịch bản bạn muốn bao phủ:",
                List.of(
                        "(Khuyên dùng) Đầy đủ: Luồng thành công (Happy) + Xác thực lỗi/Validate (Negative) + Trường hợp biên (Edge cases)",
                        "Chỉ luồng chính thành công (Happy Path)",
                        "Tập trung sâu vào xác thực dữ liệu biên, lỗi nhập liệu & Fuzzing (Negative Validation & BVA)"),
                false,
                true));

        // Question 2: Preconditions & Authentication
        boolean isAuthFeature = p.contains("login") || p.contains("đăng nhập") || p.contains("auth")
                || p.contains("register") || p.contains("đăng ký");
        if (isAuthFeature) {
            list.add(new AiClarificationQuestion(
                    "q_precondition",
                    "2. Tiền đề kiểm thử & Loại tài khoản:",
                    List.of(
                            "(Khuyên dùng) Tài khoản người dùng tiêu chuẩn đã đăng ký sẵn",
                            "Tài khoản Quản trị viên (Admin / SuperUser)",
                            "Kiểm thử với tài khoản chưa tồn tại hoặc bị khóa"),
                    false,
                    true));
        } else {
            list.add(new AiClarificationQuestion(
                    "q_precondition",
                    "2. Yêu cầu tiền đề / Đăng nhập của tính năng này:",
                    List.of(
                            "(Khuyên dùng) Cần đăng nhập tài khoản trước khi thực hiện luồng nghiệp vụ",
                            "Không cần đăng nhập (Trang công khai / Khách vãng lai)",
                            "Cần có sẵn dữ liệu mẫu trong hệ thống (sản phẩm, đơn hàng, giỏ hàng)"),
                    false,
                    true));
        }

        // Question 3: Assertions & Expected Outcome
        list.add(new AiClarificationQuestion(
                "q_assertions",
                "3. Tiêu chí kiểm tra kết quả (Assertions mong muốn):",
                List.of(
                        "(Khuyên dùng) Kiểm tra toàn diện: Chuyển hướng URL đúng + Hiển thị thông báo Toast/Alert thành công + Cập nhật phần tử UI",
                        "Chỉ kiểm tra phần tử giao diện hiển thị (`toBeVisible`)",
                        "Kiểm tra dữ liệu chính xác trên bảng / danh sách"),
                false,
                true));

        return list;
    }

    /**
     * Synthesizes an augmented prompt containing original prompt and clarified
     * answers.
     */
    public String buildAugmentedPrompt(String originalPrompt, Map<String, String> answers) {
        StringBuilder sb = new StringBuilder();
        sb.append(originalPrompt.trim()).append("\n\n");
        sb.append("### THÔNG SỐ ĐẶC TẢ ĐÃ ĐƯỢC LÀM RÕ TỪ NGƯỜI DÙNG:\n");

        if (answers != null && !answers.isEmpty()) {
            for (Map.Entry<String, String> e : answers.entrySet()) {
                String label = switch (e.getKey()) {
                    case "q_scope" -> "Phạm vi kiểm thử";
                    case "q_precondition" -> "Tiền đề & Xác thực";
                    case "q_assertions" -> "Tiêu chí kiểm tra (Assertions)";
                    default -> e.getKey();
                };
                sb.append("- **").append(label).append("**: ").append(e.getValue()).append("\n");
            }
        }
        sb.append(
                "- **Yêu cầu bổ sung**: Sinh bộ kịch bản Playwright Java hoàn chỉnh, chi tiết, bao quát đầy đủ các tiêu chí trên, áp dụng chuẩn Agency-Agents (Zero hard sleep, web-first assertions, resilient role selectors).\n");

        return sb.toString();
    }
}
