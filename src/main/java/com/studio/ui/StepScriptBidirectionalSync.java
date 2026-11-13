package com.studio.ui;

import com.studio.model.TestStep;
import com.studio.util.JavaBodyScanner;
import com.studio.util.PlaywrightDescribe;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bidirectional translator between high-level declarative {@link TestStep} models
 * and executable Playwright Java source code.
 *
 * <p>Two shapes of script are supported:</p>
 * <ul>
 *   <li>Bare statement lists, used by the visual builder and the AI copilot.</li>
 *   <li>Full Java classes recorded by codegen ({@code package}/{@code import}/{@code class}/{@code main}).
 *       Only the statements inside {@code main} are exposed as steps; the envelope is preserved verbatim
 *       by {@link Document#render(List, String)} so the builder can never strip a class wrapper.</li>
 * </ul>
 *
 * <p>Anything inside the editable body that is not a recognised action is kept as a
 * {@link #ACTION_RAW RAW} step, so reading a script and writing it back is lossless.</p>
 */
public final class StepScriptBidirectionalSync {

    /** Default wait used when a DELAY step carries no usable millisecond value. */
    public static final int DEFAULT_DELAY_MS = 1000;
    /** Longest accepted delay, guarding against values that overflow Playwright's timer. */
    public static final int MAX_DELAY_MS = 300_000;
    /** Step action that re-emits its target verbatim (comments, hand-written statements). */
    public static final String ACTION_RAW = "RAW";
    /** Step action for a {@code //} line: still a script line, read as a sentence. */
    public static final String ACTION_NOTE = "NOTE";
    /** Indentation applied to each emitted statement of a full-class script body. */
    public static final String BODY_INDENT = "        ";

    private StepScriptBidirectionalSync() {}

    /** An argument list that is exactly one string literal — the only shape re-modelled losslessly. */
    private static final Pattern ONLY_STRING =
            Pattern.compile("\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*");

    // ── Generation ──────────────────────────────────────────────────────────

    /**
     * Converts a list of {@link TestStep}s into clean Playwright Java statements.
     *
     * <p>A leading {@code page.navigate} is emitted from {@code startUrl} only when no enabled
     * {@code NAVIGATE} step already performs that navigation — otherwise the script navigated twice.</p>
     */
    public static String stepsToScript(List<TestStep> steps, String startUrl) {
        StringBuilder sb = new StringBuilder();
        if (startUrl != null && !startUrl.isBlank() && !containsNavigate(steps)) {
            sb.append("page.navigate(\"").append(escape(startUrl.trim())).append("\");\n");
        }
        if (steps != null) {
            for (TestStep step : steps) {
                String statement = statement(step);
                if (!statement.isBlank()) {
                    sb.append(statement).append('\n');
                }
            }
        }
        return sb.toString();
    }

    /** @return the single Playwright statement for {@code step}, or an empty string when it emits nothing. */
    public static String statement(TestStep step) {
        if (step == null || !step.isEnabled()) {
            return "";
        }
        String action = step.getAction() != null ? step.getAction().toUpperCase() : "CLICK";
        String target = step.getTarget() != null ? step.getTarget() : "";
        String data = step.getTestData() != null ? step.getTestData() : "";

        return switch (action) {
            case "NAVIGATE" -> "page.navigate(\"" + escape(target) + "\");";
            case "CLICK" -> locator(target, "click()");
            case "DOUBLE_CLICK" -> locator(target, "dblclick()");
            case "CHECK" -> locator(target, "check()");
            case "UNCHECK" -> locator(target, "uncheck()");
            case "FILL" -> locator(target, "fill(\"" + escape(data) + "\")");
            case "SELECT" -> locator(target, "selectOption(\"" + escape(data) + "\")");
            case "PRESS_KEY" -> locator(target, "press(\"" + escape(data) + "\")");
            case "HOVER" -> locator(target, "hover()");
            case "WAIT_FOR_ELEMENT" -> locator(target, "waitFor()");
            case "WAIT_FOR_LOAD" -> "page.waitForLoadState();";
            case "WAIT_FOR_URL" -> "page.waitForURL(\"" + escape(target) + "\");";
            case "RELOAD" -> "page.reload();";
            case "GO_BACK" -> "page.goBack();";
            case "ASSERT_VISIBLE" -> "assertThat(page.locator(\"" + escape(target) + "\")).isVisible();";
            case "ASSERT_HIDDEN" -> "assertThat(page.locator(\"" + escape(target) + "\")).isHidden();";
            case "ASSERT_TEXT" -> "assertThat(page.locator(\"" + escape(target) + "\")).hasText(\""
                    + escape(data) + "\");";
            case "ASSERT_VALUE" -> "assertThat(page.locator(\"" + escape(target) + "\")).hasValue(\""
                    + escape(data) + "\");";
            case "ASSERT_URL" -> "assertThat(page).hasURL(\"" + escape(target) + "\");";
            case "ASSERT_TITLE" -> "assertThat(page).hasTitle(\"" + escape(target) + "\");";
            case "DELAY" -> "page.waitForTimeout(" + timeoutMillis(data) + ");";
            case "CUSTOM_JS" -> data.isBlank() ? "" : "page.evaluate(\"" + escape(data) + "\");";
            case "NOTE" -> commentLines(target);
            case ACTION_RAW -> target.isBlank() ? "" : target.strip();
            default -> target.isBlank() ? "" : "page.locator(\"" + escape(target) + "\").click();";
        };
    }

    /** @return {@code page.locator("target").call;} — the shape every element action shares. */
    private static String locator(String target, String call) {
        return "page.locator(\"" + escape(target) + "\")." + call + ";";
    }

    /**
     * A note is a real comment in the generated script. Each line is prefixed so a multi-line note
     * can never leak an uncommented line into the body and break the file.
     */
    private static String commentLines(String text) {
        if (text == null || text.isBlank()) {
            return "//";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\r\n|\r|\n", -1)) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("//");
            if (!line.isBlank()) {
                sb.append(' ').append(line.strip());
            }
        }
        return sb.toString();
    }

    /** @return true when an enabled step already navigates, so {@code startUrl} must not be re-emitted. */
    private static boolean containsNavigate(List<TestStep> steps) {
        if (steps == null) {
            return false;
        }
        for (TestStep step : steps) {
            if (step != null && step.isEnabled() && "NAVIGATE".equalsIgnoreCase(step.getAction())
                    && step.getTarget() != null && !step.getTarget().isBlank()) {
                return true;
            }
        }
        return false;
    }

    /**
     * A delay value is emitted as an unquoted numeric literal. Anything that is not a positive
     * integer would either break the generated script or inject arbitrary code into it, so callers
     * fall back to {@link #DEFAULT_DELAY_MS} instead of trusting the authored value.
     */
    private static String timeoutMillis(String rawData) {
        if (rawData == null) {
            return String.valueOf(DEFAULT_DELAY_MS);
        }
        String trimmed = rawData.trim();
        if (trimmed.matches("\\d{1,9}")) {
            long parsed = Long.parseLong(trimmed);
            if (parsed > 0) {
                return String.valueOf(Math.min(parsed, MAX_DELAY_MS));
            }
        }
        return String.valueOf(DEFAULT_DELAY_MS);
    }

    // ── Parsing ─────────────────────────────────────────────────────────────

    /** Parses every recognised statement of {@code script}, ignoring the Java class envelope. */
    public static List<TestStep> scriptToSteps(String script) {
        return parseDocument(script).getSteps();
    }

    /**
     * Splits {@code script} into a preserved Java envelope and editable body statements.
     * Unknown body lines become {@link #ACTION_RAW} steps, which keeps the round trip lossless.
     */
    public static Document parseDocument(String script) {
        List<String> lines = splitLines(script);
        JavaBodyScanner.Body body = JavaBodyScanner.scan(lines);
        int headerEnd = body.headerEnd();
        int footerStart = body.footerStart();

        List<String> header = new ArrayList<>(lines.subList(0, headerEnd));
        List<String> footer = new ArrayList<>(lines.subList(footerStart, lines.size()));
        List<TestStep> steps = new ArrayList<>();

        int stepNum = 1;
        for (int i = headerEnd; i < footerStart; i++) {
            String raw = lines.get(i);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            TestStep parsed = parseStatement(raw.strip(), stepNum);
            if (parsed != null) {
                steps.add(parsed);
                stepNum++;
            }
        }
        return new Document(header, footer, steps);
    }

    /**
     * Parses one statement line.
     *
     * <p>The builder only re-models a call it can regenerate byte for byte: a bare {@code page}
     * call, a bare {@code page.locator("…")} call, or an AssertJ-style assertion over those. A call
     * carrying options ({@code click(new ClickOptions()…)}) or a {@code getBy…} locator stays
     * {@link #ACTION_RAW} but is still explained by {@link PlaywrightDescribe}, so the card reads as
     * a sentence and the statement survives the round trip untouched.</p>
     *
     * <p>A {@code //} line becomes a {@link #ACTION_NOTE} rather than a raw statement: it is part of
     * the script but nothing about it is code a tester should have to decode.</p>
     */
    private static TestStep parseStatement(String line, int stepNum) {
        if (PlaywrightDescribe.isComment(line)) {
            return new TestStep(stepNum, ACTION_NOTE, PlaywrightDescribe.commentText(line), "", "", "");
        }
        PlaywrightDescribe.Call call = PlaywrightDescribe.parseCall(line);
        if (call == null) {
            return rawStep(line, stepNum);
        }
        TestStep parsed = parsePageCall(call, stepNum);
        if (parsed == null) {
            parsed = parseLocatorCall(call, stepNum);
        }
        if (parsed == null) {
            parsed = parseAssertion(call, stepNum);
        }
        return parsed != null ? parsed : rawStep(line, stepNum);
    }

    private static TestStep rawStep(String line, int stepNum) {
        // No canned description: a raw line is described by PlaywrightDescribe, and a fixed
        // "Bình luận / lệnh tùy chỉnh" on every raw card was pure noise in the note field.
        return new TestStep(stepNum, ACTION_RAW, line, "", "", "");
    }

    /** Page-level calls: navigation, waits, reload, and free JavaScript. */
    private static TestStep parsePageCall(PlaywrightDescribe.Call call, int stepNum) {
        if (!"page".equals(call.receiver())) {
            return null;
        }
        String args = call.args().strip();
        return switch (call.method()) {
            case "navigate" -> {
                String url = onlyString(args);
                yield url == null ? null
                        : new TestStep(stepNum, "NAVIGATE", url, "", "Điều hướng tới " + url, "");
            }
            case "reload" -> args.isEmpty()
                    ? new TestStep(stepNum, "RELOAD", "", "", "Tải lại trang", "") : null;
            case "goBack" -> args.isEmpty()
                    ? new TestStep(stepNum, "GO_BACK", "", "", "Quay lại trang trước", "") : null;
            case "waitForLoadState" -> args.isEmpty()
                    ? new TestStep(stepNum, "WAIT_FOR_LOAD", "", "", "Chờ trang tải xong", "") : null;
            case "waitForURL" -> {
                String url = onlyString(args);
                yield url == null ? null
                        : new TestStep(stepNum, "WAIT_FOR_URL", url, "", "Chờ URL chuyển tới " + url, "");
            }
            case "waitForTimeout" -> args.matches("\\d{1,9}")
                    ? new TestStep(stepNum, "DELAY", "", args, "Chờ " + args + "ms", "") : null;
            case "evaluate" -> {
                String js = onlyString(args);
                yield js == null ? null : new TestStep(stepNum, "CUSTOM_JS", "", js, "Thực thi JavaScript", "");
            }
            default -> null;
        };
    }

    /** Element calls on a plain {@code page.locator("…")} receiver. */
    private static TestStep parseLocatorCall(PlaywrightDescribe.Call call, int stepNum) {
        String selector = PlaywrightDescribe.locatorSelector(call.receiver());
        if (selector == null) {
            return null;
        }
        String args = call.args().strip();
        String value = onlyString(args);
        return switch (call.method()) {
            case "click" -> args.isEmpty()
                    ? new TestStep(stepNum, "CLICK", selector, "", "Click vào " + selector, "") : null;
            case "dblclick" -> args.isEmpty()
                    ? new TestStep(stepNum, "DOUBLE_CLICK", selector, "", "Nhấp đúp vào " + selector, "") : null;
            case "check" -> args.isEmpty()
                    ? new TestStep(stepNum, "CHECK", selector, "", "Đánh dấu " + selector, "") : null;
            case "uncheck" -> args.isEmpty()
                    ? new TestStep(stepNum, "UNCHECK", selector, "", "Bỏ đánh dấu " + selector, "") : null;
            case "hover" -> args.isEmpty()
                    ? new TestStep(stepNum, "HOVER", selector, "", "Rê chuột qua " + selector, "") : null;
            case "waitFor" -> args.isEmpty()
                    ? new TestStep(stepNum, "WAIT_FOR_ELEMENT", selector, "", "Chờ phần tử " + selector, "") : null;
            case "fill", "type" -> value == null ? null
                    : new TestStep(stepNum, "FILL", selector, value,
                            "Nhập '" + value + "' vào " + selector, "");
            case "selectOption" -> value == null ? null
                    : new TestStep(stepNum, "SELECT", selector, value,
                            "Chọn '" + value + "' tại " + selector, "");
            case "press" -> value == null ? null
                    : new TestStep(stepNum, "PRESS_KEY", selector, value,
                            "Nhấn '" + value + "' tại " + selector, "");
            default -> null;
        };
    }

    /** AssertJ assertions over a locator or over the page itself. */
    private static TestStep parseAssertion(PlaywrightDescribe.Call call, int stepNum) {
        PlaywrightDescribe.Call outer = PlaywrightDescribe.parseCall(call.receiver());
        if (outer == null) {
            return null;
        }
        boolean assertJ = "assertThat".equals(outer.method());
        boolean playwrightExpect = "expect".equals(outer.method());
        if (!assertJ && !playwrightExpect) {
            // Handles …not().xxx(), which the builder does not re-model: the negated statement
            // stays verbatim and is still explained on the card.
            return null;
        }
        String subject = outer.args().strip();
        String args = call.args().strip();
        String method = call.method();
        boolean pageSubject = "page".equals(subject);
        if (pageSubject) {
            String expected = onlyString(args);
            return switch (method) {
                case "hasURL", "toHaveURL" -> expected == null ? null
                        : new TestStep(stepNum, "ASSERT_URL", expected, "",
                                "Kiểm tra URL chứa " + expected, "URL phải chứa " + expected);
                case "hasTitle", "toHaveTitle" -> expected == null ? null
                        : new TestStep(stepNum, "ASSERT_TITLE", expected, "",
                                "Kiểm tra tiêu đề trang chứa " + expected,
                                "Tiêu đề phải chứa " + expected);
                default -> null;
            };
        }
        String selector = PlaywrightDescribe.locatorSelector(subject);
        if (selector == null) {
            return null;
        }
        String expected = onlyString(args);
        return switch (method) {
            case "isVisible", "toBeVisible" -> args.isEmpty()
                    ? new TestStep(stepNum, "ASSERT_VISIBLE", selector, "",
                            "Kiểm tra hiển thị: " + selector, "Phần tử phải hiển thị") : null;
            case "isHidden", "toBeHidden" -> args.isEmpty()
                    ? new TestStep(stepNum, "ASSERT_HIDDEN", selector, "",
                            "Kiểm tra phần tử ẩn: " + selector, "Phần tử phải ẩn") : null;
            case "hasText", "toHaveText" -> expected == null ? null
                    : new TestStep(stepNum, "ASSERT_TEXT", selector, expected,
                            "Kiểm tra nội dung text của " + selector, expected);
            case "hasValue", "toHaveValue" -> expected == null ? null
                    : new TestStep(stepNum, "ASSERT_VALUE", selector, expected,
                            "Kiểm tra giá trị của " + selector, expected);
            default -> null;
        };
    }

    /**
     * @return the argument when it is exactly one string literal, so an options-carrying call is
     *         never re-modelled (and its options never silently dropped)
     */
    private static String onlyString(String args) {
        Matcher matcher = ONLY_STRING.matcher(args);
        return matcher.matches() ? unescape(matcher.group(1)) : null;
    }

    private static List<String> splitLines(String script) {
        List<String> lines = new ArrayList<>();
        if (script == null || script.isEmpty()) {
            return lines;
        }
        for (String line : script.split("\r\n|\r|\n", -1)) {
            lines.add(line);
        }
        // A trailing newline produces one empty tail element; drop it so render does not grow a blank line.
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    // ── Escaping ────────────────────────────────────────────────────────────

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private static String unescape(String s) {
        if (s == null || s.indexOf('\\') < 0) {
            return s == null ? "" : s;
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char current = s.charAt(i);
            if (current != '\\' || i + 1 >= s.length()) {
                out.append(current);
                continue;
            }
            char next = s.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case '"' -> out.append('"');
                case '\'' -> out.append('\'');
                case '\\' -> out.append('\\');
                default -> out.append('\\').append(next);
            }
        }
        return out.toString();
    }

    /**
     * A script split into its preserved Java envelope and its editable body steps.
     * Instances are immutable; {@link #render(List, String)} produces the next script text.
     */
    public static final class Document {
        private final List<String> header;
        private final List<String> footer;
        private final List<TestStep> steps;
        private final boolean wrapped;

        private Document(List<String> header, List<String> footer, List<TestStep> steps) {
            this.header = List.copyOf(header);
            this.footer = List.copyOf(footer);
            this.steps = List.copyOf(steps);
            this.wrapped = !header.isEmpty();
        }

        /** @return the body steps parsed from the script (RAW steps included). */
        public List<TestStep> getSteps() {
            return steps;
        }

        /** @return true when the script is a full Java class whose {@code main} body is editable. */
        public boolean isWrappedInClass() {
            return wrapped;
        }

        /**
         * Renders {@code steps} back into the original envelope.
         *
         * @param startUrl navigation used when the steps themselves do not navigate
         */
        public String render(List<TestStep> steps, String startUrl) {
            StringBuilder sb = new StringBuilder();
            for (String line : header) {
                sb.append(line).append('\n');
            }
            boolean navigates = containsNavigate(steps);
            if (!navigates && startUrl != null && !startUrl.isBlank()) {
                sb.append(BODY_INDENT).append("page.navigate(\"").append(escape(startUrl.trim()))
                        .append("\");").append('\n');
            }
            if (steps != null) {
                for (TestStep step : steps) {
                    String statement = StepScriptBidirectionalSync.statement(step);
                    if (statement.isBlank()) {
                        continue;
                    }
                    // A note may emit several lines; every one needs the body indent, not just the first.
                    for (String line : statement.split("\n", -1)) {
                        sb.append(wrapped ? BODY_INDENT : "").append(line).append('\n');
                    }
                }
            }
            for (String line : footer) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }
}
