package com.studio.service;

import com.studio.model.TestCase;
import com.studio.model.TestStep;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt construction and response normalization for the contextual AI Copilot drawer.
 *
 * <p>Kept free of JavaFX so the prompt contracts can be unit-tested and so the drawer stays a
 * thin view over {@link OpenAiClient}.</p>
 */
public final class AiCopilotService {

    /** Upper bound for the reduced DOM handed to the model (keeps prompts inside the context window). */
    public static final int MAX_DOM_CONTEXT_CHARS = 6000;
    /** The copilot only needs a short answer: one selector or one assertion line. */
    public static final int MAX_OUTPUT_TOKENS = 600;
    /** Defensive bound on a selector coming back from the model. */
    public static final int MAX_SELECTOR_CHARS = 300;

    public static final String SYSTEM_PROMPT = """
            Bạn là AI Copilot chuyên hỗ trợ kiểm thử Playwright (Java).
            Chỉ trả lời đúng thứ được yêu cầu, không giải thích, không markdown.
            Selector phải dùng cú pháp Playwright: css, xpath, text=, role=, hoặc getBy* nếu phù hợp.
            Assertion phải là một dòng Java hợp lệ dùng assertThat(page.locator("..."))....
            """;

    private static final Pattern LOCATOR_CALL = Pattern.compile(
            "locator\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)");
    private static final Pattern CODE_FENCE = Pattern.compile("```[a-zA-Z0-9]*\\s*([\\s\\S]*?)```");

    private AiCopilotService() {
    }

    /** Builds the prompt asking the model to repair a broken selector for {@code step}. */
    public static String buildSelectorFixPrompt(TestCase testCase, TestStep step, String reducedDom, String instruction) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nhiệm vụ: sửa lại selector Playwright đang không tìm thấy phần tử.\n");
        appendCaseContext(sb, testCase, step);
        sb.append("Selector hiện tại: ")
                .append(quote(step != null ? step.getTarget() : ""))
                .append('\n');
        if (instruction != null && !instruction.isBlank()) {
            sb.append("Ghi chú của người dùng: ").append(instruction.trim()).append('\n');
        }
        sb.append("DOM rút gọn của trang (JSON):\n").append(trimDom(reducedDom)).append('\n');
        sb.append("Chỉ trả về DUY NHẤT một selector trên một dòng, không kèm giải thích hay dấu nháy bao ngoài.");
        return sb.toString();
    }

    /** Builds the prompt asking the model for a single Playwright assertion line for {@code step}. */
    public static String buildAssertionPrompt(TestCase testCase, TestStep step, String currentScript,
                                              String reducedDom, String instruction) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nhiệm vụ: sinh MỘT assertion Playwright Java cho bước đang chọn.\n");
        appendCaseContext(sb, testCase, step);
        if (currentScript != null && !currentScript.isBlank()) {
            sb.append("Kịch bản hiện tại (tham khảo):\n")
                    .append(trim(currentScript, MAX_DOM_CONTEXT_CHARS))
                    .append('\n');
        }
        if (instruction != null && !instruction.isBlank()) {
            sb.append("Ghi chú của người dùng: ").append(instruction.trim()).append('\n');
        }
        String dom = trimDom(reducedDom);
        if (!dom.isBlank()) {
            sb.append("DOM rút gọn của trang (JSON):\n").append(dom).append('\n');
        }
        sb.append("Chỉ trả về đúng một dòng, ví dụ: assertThat(page.locator(\".success\")).isVisible();")
                .append(" hoặc assertThat(page.locator(\"#msg\")).hasText(\"Lưu thành công\");");
        return sb.toString();
    }

    private static void appendCaseContext(StringBuilder sb, TestCase testCase, TestStep step) {
        if (testCase != null) {
            sb.append("Test case: ").append(quote(testCase.getName())).append('\n');
            sb.append("URL bắt đầu: ").append(quote(testCase.getStartUrl())).append('\n');
        }
        if (step != null) {
            sb.append("Bước ").append(step.getStepNumber())
                    .append(" | hành động: ").append(quote(step.getAction()))
                    .append(" | selector: ").append(quote(step.getTarget()))
                    .append(" | dữ liệu: ").append(quote(step.getTestData()))
                    .append('\n');
        }
    }

    /** Strips markdown fences and surrounding whitespace from a model reply. */
    public static String cleanModelOutput(String raw) {
        if (raw == null) {
            return "";
        }
        Matcher fence = CODE_FENCE.matcher(raw);
        if (fence.find()) {
            return fence.group(1).trim();
        }
        return raw.trim();
    }

    /**
     * Extracts one selector from a model reply. Prefers a {@code locator("...")} call, otherwise
     * takes the first non-empty line and removes labels/quotes/backticks.
     *
     * @return the selector, or {@code null} when the reply has no usable selector
     */
    public static String extractSelector(String raw) {
        String cleaned = cleanModelOutput(raw);
        if (cleaned.isBlank()) {
            return null;
        }
        Matcher locator = LOCATOR_CALL.matcher(cleaned);
        if (locator.find()) {
            return validateSelector(unescape(locator.group(1)));
        }

        for (String line : cleaned.split("\\R")) {
            String candidate = stripSelectorDecorations(line);
            if (!candidate.isBlank()) {
                return validateSelector(candidate);
            }
        }
        return null;
    }

    private static String stripSelectorDecorations(String line) {
        String candidate = line.trim();
        candidate = candidate.replaceFirst("(?i)^(selector|locator|bộ chọn|bo chon)\\s*[:=]\\s*", "");
        candidate = candidate.replaceAll(";+$", "").trim();
        candidate = candidate.replaceAll("^[`'\"]+|[`'\"]+$", "");
        return candidate.trim();
    }

    private static String validateSelector(String selector) {
        if (selector == null) {
            return null;
        }
        String trimmed = selector.trim();
        if (trimmed.isBlank() || trimmed.length() > MAX_SELECTOR_CHARS) {
            return null;
        }
        // A newline would be pasted into a single-line code statement and break the script.
        if (trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            return null;
        }
        // A URL would silently turn a CLICK step into navigation; reject it for selector repair.
        if (trimmed.matches("(?i)^https?://.*")) {
            return null;
        }
        return trimmed;
    }

    /** Reverses the backslash escapes of a Java string literal returned inside a locator call. */
    private static String unescape(String value) {
        if (value == null || value.indexOf('\\') < 0) {
            return value == null ? "" : value;
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\' || i + 1 >= value.length()) {
                out.append(current);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                default -> out.append('\\').append(next);
            }
        }
        return out.toString();
    }

    /** Caps the DOM context so a huge page cannot crowd out the instruction. */
    public static String trimDom(String dom) {
        if (dom == null || dom.isBlank() || "[]".equals(dom.trim())) {
            return "";
        }
        return trim(dom.trim(), MAX_DOM_CONTEXT_CHARS);
    }

    private static String trim(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }

    private static String quote(String value) {
        return '"' + (value == null ? "" : value) + '"';
    }

    /** Convenience for UI code that wants distinct system/user chat messages. */
    public static List<OpenAiClient.ChatMessage> asMessages(String userPrompt) {
        List<OpenAiClient.ChatMessage> messages = new ArrayList<>(2);
        messages.add(new OpenAiClient.ChatMessage("system", SYSTEM_PROMPT));
        messages.add(new OpenAiClient.ChatMessage("user", userPrompt));
        return messages;
    }
}
