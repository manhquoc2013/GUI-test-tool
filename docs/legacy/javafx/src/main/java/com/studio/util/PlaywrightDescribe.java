package com.studio.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one Playwright/Java statement and says, in Vietnamese, what it does on the page.
 *
 * <p>A recorded script is mostly things the builder can only keep verbatim (options, {@code getBy…}
 * locators, helper calls). Dumping that line into a step card — {@code page.waitForLoadState(
 * com.microsoft.playwright.opti…} — tells a manual tester nothing, and expanding the card to read
 * the code is exactly what the No-Code view exists to avoid. So the card shows the sentence this
 * class derives and keeps the statement itself as the payload.</p>
 *
 * <p>Nothing here rewrites code: it only classifies. Parsing that does re-model a statement lives in
 * {@link com.studio.ui.StepScriptBidirectionalSync} and stays limited to calls it can regenerate byte for byte.</p>
 */
public final class PlaywrightDescribe {

    /** Extracts a string literal without deciding whether the statement is understandable. */
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** {@code page.locator("…")}; group 1 is the raw (still escaped) selector. */
    private static final Pattern LOCATOR =
            Pattern.compile("^page\\.locator\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)$");

    /** {@code page.getByRole(...)} / {@code getByText} / …, treated as a readable element phrase. */
    private static final Pattern GET_BY = Pattern.compile("^page\\.(getBy\\w+)\\((.*)\\)$");

    private static final Pattern ROLE = Pattern.compile("^\\s*AriaRole\\.(\\w+)");
    private static final Pattern SET_NAME = Pattern.compile("setName\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)");
    private static final Pattern FIRST_SUFFIX = Pattern.compile("\\.first\\(\\)$");
    private static final Pattern LAST_SUFFIX = Pattern.compile("\\.last\\(\\)$");
    private static final Pattern NTH_SUFFIX = Pattern.compile("\\.nth\\(\\s*(\\d+)\\s*\\)$");

    /** ARIA roles rendered as the noun a tester would use. */
    private static final java.util.Map<String, String> ROLE_NOUNS = java.util.Map.ofEntries(
            java.util.Map.entry("BUTTON", "nút"),
            java.util.Map.entry("LINK", "liên kết"),
            java.util.Map.entry("TEXTBOX", "ô nhập"),
            java.util.Map.entry("SEARCHBOX", "ô tìm kiếm"),
            java.util.Map.entry("CHECKBOX", "ô đánh dấu"),
            java.util.Map.entry("RADIO", "nút chọn"),
            java.util.Map.entry("COMBOBOX", "danh sách chọn"),
            java.util.Map.entry("LISTBOX", "danh sách chọn"),
            java.util.Map.entry("OPTION", "lựa chọn"),
            java.util.Map.entry("HEADING", "tiêu đề"),
            java.util.Map.entry("IMAGE", "ảnh"),
            java.util.Map.entry("TAB", "thẻ"),
            java.util.Map.entry("MENUITEM", "mục menu"),
            java.util.Map.entry("MENU", "menu"),
            java.util.Map.entry("DIALOG", "hộp thoại"),
            java.util.Map.entry("FORM", "biểu mẫu"),
            java.util.Map.entry("TABLE", "bảng"),
            java.util.Map.entry("ROW", "dòng bảng"),
            java.util.Map.entry("CELL", "ô bảng"),
            java.util.Map.entry("LISTITEM", "mục danh sách"),
            java.util.Map.entry("NAVIGATION", "thanh điều hướng"),
            java.util.Map.entry("ALERT", "thông báo"),
            java.util.Map.entry("SWITCH", "công tắc"));

    /** Locator methods whose meaning is "type this text" (an empty argument means "clear"). */
    private static final Set<String> TYPING = Set.of("fill", "type", "setinputvalue");

    private PlaywrightDescribe() {
    }

    /** One parsed {@code receiver.method(args)} call; the arguments are still raw source text. */
    public record Call(String receiver, String method, String args) {
    }

    /**
     * Splits {@code statement} into its outermost call without being fooled by parentheses or dots
     * inside string literals or nested constructor calls.
     *
     * @return the parsed call, or {@code null} when the line is not a single method call
     */
    public static Call parseCall(String statement) {
        if (statement == null) {
            return null;
        }
        String source = statement.strip();
        if (source.endsWith(";")) {
            source = source.substring(0, source.length() - 1).strip();
        }
        if (!source.endsWith(")")) {
            return null;
        }
        boolean[] quoted = quotedMask(source);
        int depth = 0;
        int open = -1;
        for (int i = source.length() - 1; i >= 0; i--) {
            if (quoted[i]) {
                continue;
            }
            char c = source.charAt(i);
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                depth--;
                if (depth == 0) {
                    open = i;
                    break;
                }
            }
        }
        if (open <= 0) {
            return null;
        }
        int nameStart = open;
        while (nameStart > 0 && Character.isJavaIdentifierPart(source.charAt(nameStart - 1))
                && !quoted[nameStart - 1]) {
            nameStart--;
        }
        if (nameStart == open) {
            return null;
        }
        String before = source.substring(0, nameStart).stripTrailing();
        String method = source.substring(nameStart, open);
        String args = source.substring(open + 1, source.length() - 1);
        if (before.isEmpty()) {
            // A top-level static call such as assertThat(…): no receiver, but still a call.
            return new Call("", method, args);
        }
        if (!before.endsWith(".")) {
            return null;
        }
        String receiver = before.substring(0, before.length() - 1).strip();
        if (receiver.isEmpty()) {
            return null;
        }
        return new Call(receiver, method, args);
    }

    /** @return true for a single-line {@code //} comment, which the builder keeps as a note. */
    public static boolean isComment(String statement) {
        return statement != null && statement.strip().startsWith("//");
    }

    /** @return the text of a {@code //} comment without its marker, or {@code ""}. */
    public static String commentText(String statement) {
        if (!isComment(statement)) {
            return "";
        }
        return statement.strip().substring(2).strip();
    }

    /**
     * @return the selector inside a bare {@code page.locator("…")} expression, already unescaped, or
     *         {@code null} when the expression is something more complex
     */
    public static String locatorSelector(String expression) {
        if (expression == null) {
            return null;
        }
        Matcher matcher = LOCATOR.matcher(expression.strip());
        return matcher.matches() ? unescape(matcher.group(1)) : null;
    }

    /**
     * Describes the element a locator expression points at.
     *
     * @return e.g. {@code nút «Đăng nhập»} or {@code phần tử «input#email»}, or {@code null} when the
     *         expression uses an API this class cannot phrase
     */
    public static String element(String expression) {
        if (expression == null) {
            return null;
        }
        String expr = expression.strip();
        StringBuilder suffix = new StringBuilder();
        // .first()/.last()/.nth(n) narrow a list: keep it in the sentence instead of failing to parse.
        while (true) {
            if (FIRST_SUFFIX.matcher(expr).find()) {
                expr = FIRST_SUFFIX.matcher(expr).replaceFirst("");
                suffix.insert(0, " đầu tiên");
                continue;
            }
            if (LAST_SUFFIX.matcher(expr).find()) {
                expr = LAST_SUFFIX.matcher(expr).replaceFirst("");
                suffix.insert(0, " cuối cùng");
                continue;
            }
            Matcher nth = NTH_SUFFIX.matcher(expr);
            if (nth.find()) {
                suffix.insert(0, " thứ " + (Integer.parseInt(nth.group(1)) + 1));
                expr = nth.replaceFirst("");
                continue;
            }
            break;
        }

        String selector = locatorSelector(expr);
        if (selector != null) {
            return "phần tử «" + selector + "»" + suffix;
        }
        Matcher getBy = GET_BY.matcher(expr);
        if (!getBy.matches()) {
            return null;
        }
        String kind = getBy.group(1);
        String args = getBy.group(2);
        String first = firstString(args);
        if ("getByRole".equals(kind)) {
            Matcher role = ROLE.matcher(args);
            String noun = role.find()
                    ? ROLE_NOUNS.getOrDefault(role.group(1).toUpperCase(Locale.ROOT),
                            role.group(1).toLowerCase(Locale.ROOT))
                    : "phần tử";
            Matcher name = SET_NAME.matcher(args);
            if (name.find()) {
                return noun + " «" + unescape(name.group(1)) + "»" + suffix;
            }
            return first != null ? noun + " «" + first + "»" + suffix : noun + suffix;
        }
        if (first == null) {
            return null;
        }
        return switch (kind) {
            case "getByText" -> "chữ «" + first + "»" + suffix;
            case "getByLabel" -> "ô «" + first + "»" + suffix;
            case "getByPlaceholder" -> "ô có gợi ý «" + first + "»" + suffix;
            case "getByAltText" -> "ảnh «" + first + "»" + suffix;
            case "getByTestId" -> "phần tử test-id «" + first + "»" + suffix;
            case "getByTitle" -> "phần tử có tiêu đề «" + first + "»" + suffix;
            default -> null;
        };
    }

    /**
     * @return a sentence a manual tester can follow, or {@code null} when the statement uses an API
     *         this class does not phrase (the caller then shows the code itself)
     */
    public static String describe(String statement) {
        Call call = parseCall(statement);
        if (call == null) {
            return null;
        }
        String method = call.method();
        String args = call.args().strip();
        String receiver = call.receiver();

        if ("page".equals(receiver)) {
            return describePage(method, args);
        }
        if ("page.keyboard".equals(receiver) && "press".equals(method)) {
            String key = firstString(args);
            return key == null ? "nhấn phím" : "nhấn phím «" + key + "»";
        }

        Subject subject = subject(receiver);
        if (subject != null) {
            return describeAssertion(subject, method, args);
        }

        String element = element(receiver);
        if (element == null) {
            return null;
        }
        return describeLocator(method, args, element);
    }

    // ── Page-level statements ───────────────────────────────────────────────

    private static String describePage(String method, String args) {
        String value = firstString(args);
        return switch (method) {
            case "navigate" -> value == null ? "mở trang" : "mở " + value;
            case "reload" -> "tải lại trang";
            case "goBack" -> "quay lại trang trước";
            case "goForward" -> "đi tới trang sau";
            case "waitForLoadState" -> "chờ trang tải xong";
            case "waitForURL" -> value == null ? "chờ URL chuyển trang" : "chờ URL chuyển tới " + humanUrl(value);
            case "waitForTimeout" -> args.matches("\\d+") ? args + " ms" : "chờ một lúc";
            case "evaluate" -> "chạy JavaScript trên trang";
            case "setViewportSize" -> "đặt lại kích thước cửa sổ";
            case "screenshot" -> "chụp ảnh màn hình";
            case "close" -> "đóng trang";
            case "waitForSelector" -> value == null ? "chờ phần tử xuất hiện" : "chờ phần tử «" + value + "» xuất hiện";
            default -> null;
        };
    }

    // ── Locator statements ──────────────────────────────────────────────────

    private static String describeLocator(String method, String args, String element) {
        String value = firstString(args);
        return switch (method) {
            case "click" -> "nhấp vào " + element;
            case "dblclick" -> "nhấp đúp vào " + element;
            case "hover" -> "rê chuột lên " + element;
            case "check" -> "đánh dấu " + element;
            case "uncheck" -> "bỏ đánh dấu " + element;
            case "selectOption" -> value == null ? "chọn giá trị tại " + element
                    : "chọn «" + value + "» tại " + element;
            case "press" -> value == null ? "nhấn phím tại " + element
                    : "nhấn «" + value + "» tại " + element;
            case "setInputFiles" -> value == null ? "tải tệp lên " + element
                    : "tải tệp «" + value + "» lên " + element;
            case "waitFor" -> "chờ " + element + " xuất hiện";
            case "scrollIntoViewIfNeeded" -> "cuộn tới " + element;
            case "focus" -> "đưa con trỏ vào " + element;
            case "clear" -> "xoá nội dung " + element;
            case "dragTo" -> "kéo " + element;
            default -> TYPING.contains(method.toLowerCase(Locale.ROOT))
                    ? (value == null || value.isEmpty() ? "xoá nội dung " + element
                            : "gõ «" + value + "» vào " + element)
                    : null;
        };
    }

    // ── Assertions ──────────────────────────────────────────────────────────

    /** The thing an assertion talks about: the page itself or one element. */
    private record Subject(String expression, boolean negated) {

        boolean isPage() {
            return "page".equals(expression.strip());
        }
    }

    /** Unwraps {@code assertThat(x)} / {@code expect(x)} / {@code …not()} into its subject. */
    private static Subject subject(String receiver) {
        Call call = parseCall(receiver);
        if (call == null) {
            return null;
        }
        if ("assertThat".equals(call.method()) || "expect".equals(call.method())) {
            return new Subject(call.args().strip(), false);
        }
        if ("not".equals(call.method())) {
            Subject inner = subject(call.receiver());
            return inner == null ? null : new Subject(inner.expression(), !inner.negated());
        }
        return null;
    }

    private static String describeAssertion(Subject subject, String method, String args) {
        String value = firstString(args);
        State state = state(method, args, value);
        if (state == null) {
            return null;
        }
        boolean negative = state.expectedFalse() != subject.negated();
        String prefix = "kiểm tra " + (subject.isPage() ? "trang" : elementOrFallback(subject.expression()));
        return prefix + (negative ? " không " : " ") + state.phrase();
    }

    /** A state phrase plus whether the method asserts the presence or the absence of it. */
    private record State(String phrase, boolean expectedFalse) {
    }

    private static State state(String method, String args, String value) {
        String name = method.toLowerCase(Locale.ROOT);
        return switch (name) {
            case "isvisible", "tobevisible" -> new State("hiển thị", false);
            case "ishidden", "tobehidden" -> new State("hiển thị", true);
            case "isdisabled", "tobedisabled" -> new State("hoạt động", true);
            case "isenabled", "tobeenabled" -> new State("hoạt động", false);
            case "ischecked", "tobechecked" -> new State("được đánh dấu", false);
            case "iseditable", "tobeeditable" -> new State("nhập được", false);
            case "isfocused", "tobefocused" -> new State("đang được chọn", false);
            case "hastext", "tocontaintext", "hasvalue", "tohavevalue", "tohavetext" ->
                    value == null ? new State("có nội dung mong đợi", false) : new State("chứa «" + value + "»", false);
            case "hastextcontaining" -> value == null ? null : new State("chứa «" + value + "»", false);
            case "hascount", "tohavecount" -> args.matches("\\d+")
                    ? new State("có đúng " + args + " mục", false) : null;
            case "hasurl", "tohaveurl" ->
                    value == null ? null : new State("có URL chứa «" + humanUrl(value) + "»", false);
            case "hastitle", "tohavetitle" ->
                    value == null ? null : new State("có tiêu đề chứa «" + value + "»", false);
            case "exists", "toexist" -> new State("tồn tại", false);
            default -> null;
        };
    }

    /**
     * Drops the glob marker Playwright URL patterns carry: {@code **&#47;dashboard} reads as
     * {@code /dashboard} on a card, while the exact pattern stays in the field and the script.
     */
    private static String humanUrl(String value) {
        String url = value.strip();
        while (url.startsWith("**")) {
            url = url.substring(2).strip();
        }
        return url.isEmpty() ? value : url;
    }

    private static String elementOrFallback(String expression) {
        String described = element(expression);
        return described != null ? described : expression.strip();
    }

    // ── Lexical helpers ─────────────────────────────────────────────────────

    /** @return the first string literal of {@code text}, unescaped, or {@code null}. */
    private static String firstString(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = STRING_LITERAL.matcher(text);
        return matcher.find() ? unescape(matcher.group(1)) : null;
    }

    /** Marks every index that sits inside a string literal, so structure scanning ignores it. */
    private static boolean[] quotedMask(String source) {
        boolean[] mask = new boolean[source.length()];
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (inString) {
                mask[i] = true;
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            }
        }
        return mask;
    }

    private static String unescape(String raw) {
        if (raw == null || raw.indexOf('\\') < 0) {
            return raw == null ? "" : raw;
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) {
                out.append(c);
                continue;
            }
            char next = raw.charAt(++i);
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
}
