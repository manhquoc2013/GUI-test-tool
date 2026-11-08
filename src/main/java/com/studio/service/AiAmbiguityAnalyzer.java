package com.studio.service;

import com.studio.model.AiClarificationQuestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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
        GENERATE_TESTS
    }

    public record AnalysisResult(
            Intent intent,
            boolean needsClarification,
            String targetEntity,
            List<AiClarificationQuestion> questions,
            String initialClarificationMessage
    ) {}

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

        return Intent.GENERATE_TESTS;
    }

    /**
     * Extracts target entity name (e.g. case name for evaluation or keyword for search).
     */
    public String extractTargetEntity(String prompt, Intent intent) {
        if (prompt == null || prompt.isBlank()) return "";
        String p = prompt.trim();

        if (intent == Intent.SEARCH_TESTS) {
            return p.replaceAll("^(?i)(tìm kiếm|tìm|search)\\s+(test\\s+case(s)?|case(s)?|kịch bản)?\\s*", "").trim();
        }

        if (intent == Intent.EVALUATE_TEST) {
            return p.replaceAll("^(?i)(đánh giá|review|chấm điểm|audit|kiểm tra)\\s+(chất lượng\\s+)?(test\\s+case(s)?|case(s)?|kịch bản)?\\s*", "").trim();
        }

        return p;
    }

    /**
     * Determines whether test generation requires clarification from user.
     * If prompt is brief, lacks URL, assertions, preconditions, or scenario details,
     * it flags needsClarification = true and produces structured questions.
     */
    public AnalysisResult analyze(String prompt, boolean hasAttachments, boolean hasLiveDom) {
        Intent intent = detectIntent(prompt);
        String target = extractTargetEntity(prompt, intent);

        if (intent != Intent.GENERATE_TESTS) {
            return new AnalysisResult(intent, false, target, List.of(), "");
        }

        // If user already attached a PRD document or Live DOM was scanned -> high context available
        if (hasAttachments || hasLiveDom) {
            return new AnalysisResult(Intent.GENERATE_TESTS, false, target, List.of(), "");
        }

        String trimmed = prompt != null ? prompt.trim() : "";

        // Check if prompt is already rich or clarified (e.g. contains clarification tags, URL, step bullets, or long detailed spec > 120 chars)
        String lower = trimmed.toLowerCase(Locale.ROOT);
        boolean isClarified = lower.contains("[phạm vi]") || lower.contains("[tiền điều kiện]")
                || lower.contains("[xác minh]") || lower.contains("[scope]") || lower.contains("[precondition]");
        boolean hasUrl = lower.contains("http://") || lower.contains("https://");
        boolean hasSteps = trimmed.contains("\n-") || trimmed.contains("\n1.") || trimmed.contains("bước 1") || trimmed.contains("step 1");
        boolean isDetailed = isClarified || (trimmed.length() > 140 && (hasUrl || hasSteps));

        if (isDetailed) {
            return new AnalysisResult(Intent.GENERATE_TESTS, false, target, List.of(), "");
        }

        // Vague or brief prompt -> Needs clarification
        List<AiClarificationQuestion> questions = generateClarificationQuestions(trimmed);
        String summary = "Tôi đã phân tích yêu cầu **\"" + (trimmed.length() > 50 ? trimmed.substring(0, 47) + "..." : trimmed)
                + "\"**. Để bộ test case sinh ra đầy đủ, chính xác và không bị sơ sài, vui lòng làm rõ một số điểm mờ dưới đây:";

        return new AnalysisResult(Intent.GENERATE_TESTS, true, target, questions, summary);
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
                        "Tập trung sâu vào xác thực dữ liệu biên, lỗi nhập liệu & Fuzzing (Negative Validation & BVA)"
                ),
                false,
                true
        ));

        // Question 2: Preconditions & Authentication
        boolean isAuthFeature = p.contains("login") || p.contains("đăng nhập") || p.contains("auth") || p.contains("register") || p.contains("đăng ký");
        if (isAuthFeature) {
            list.add(new AiClarificationQuestion(
                    "q_precondition",
                    "2. Tiền đề kiểm thử & Loại tài khoản:",
                    List.of(
                            "(Khuyên dùng) Tài khoản người dùng tiêu chuẩn đã đăng ký sẵn",
                            "Tài khoản Quản trị viên (Admin / SuperUser)",
                            "Kiểm thử với tài khoản chưa tồn tại hoặc bị khóa"
                    ),
                    false,
                    true
            ));
        } else {
            list.add(new AiClarificationQuestion(
                    "q_precondition",
                    "2. Yêu cầu tiền đề / Đăng nhập của tính năng này:",
                    List.of(
                            "(Khuyên dùng) Cần đăng nhập tài khoản trước khi thực hiện luồng nghiệp vụ",
                            "Không cần đăng nhập (Trang công khai / Khách vãng lai)",
                            "Cần có sẵn dữ liệu mẫu trong hệ thống (sản phẩm, đơn hàng, giỏ hàng)"
                    ),
                    false,
                    true
            ));
        }

        // Question 3: Assertions & Expected Outcome
        list.add(new AiClarificationQuestion(
                "q_assertions",
                "3. Tiêu chí kiểm tra kết quả (Assertions mong muốn):",
                List.of(
                        "(Khuyên dùng) Kiểm tra toàn diện: Chuyển hướng URL đúng + Hiển thị thông báo Toast/Alert thành công + Cập nhật phần tử UI",
                        "Chỉ kiểm tra phần tử giao diện hiển thị (`toBeVisible`)",
                        "Kiểm tra dữ liệu chính xác trên bảng / danh sách"
                ),
                false,
                true
        ));

        return list;
    }

    /**
     * Synthesizes an augmented prompt containing original prompt and clarified answers.
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
        sb.append("- **Yêu cầu bổ sung**: Sinh bộ kịch bản Playwright Java hoàn chỉnh, chi tiết, bao quát đầy đủ các tiêu chí trên, áp dụng chuẩn Agency-Agents (Zero hard sleep, web-first assertions, resilient role selectors).\n");

        return sb.toString();
    }
}
