package com.studio.ui;

import com.studio.util.UiIcons;

import java.util.Locale;
import com.studio.util.PlaywrightDescribe;

/**
 * Presentation metadata for every persisted {@code TestStep.action} code.
 *
 * <p>The model keeps the Playwright-facing codes ({@code NAVIGATE}, {@code CLICK}, …, {@code RAW})
 * because they drive script generation and are stored in existing suites. What a person reads on a
 * step card is this enum: a Vietnamese label, an icon, the fields the action actually consumes, and
 * a one-line summary. Adding a UI label here can therefore never change a generated script.</p>
 *
 * <p>Every assertion the builder can author is a {@link Group#ASSERTION} action, so an expected
 * value is edited on the step card itself instead of through the assertion catalog dialog. A
 * {@code //} comment is a {@link #NOTE}: it stays a line of the script but reads as a sentence, not
 * as code the tester has to decode.</p>
 */
public enum StepAction {

    // ── Thao tác ───────────────────────────────────────────────────────────
    NAVIGATE(Group.INTERACTION, "NAVIGATE", "Đi tới trang", UiIcons.GLOBE,
            "Địa chỉ URL", "https://example.com", null, null),

    CLICK(Group.INTERACTION, "CLICK", "Nhấp chuột", UiIcons.CURSOR,
            "Phần tử cần nhấp", "button.submit", null, null),

    DOUBLE_CLICK(Group.INTERACTION, "DOUBLE_CLICK", "Nhấp đúp", UiIcons.CURSOR,
            "Phần tử cần nhấp đúp", "tr.row-item", null, null),

    FILL(Group.INTERACTION, "FILL", "Nhập liệu", UiIcons.EDIT,
            "Ô nhập liệu", "input#email", "Giá trị nhập", "nguyen.van.a@example.com"),

    SELECT(Group.INTERACTION, "SELECT", "Chọn mục", UiIcons.MENU,
            "Danh sách chọn", "select#country", "Giá trị chọn", "VN"),

    CHECK(Group.INTERACTION, "CHECK", "Đánh dấu", UiIcons.CHECK,
            "Ô cần đánh dấu", "input#agree", null, null),

    UNCHECK(Group.INTERACTION, "UNCHECK", "Bỏ đánh dấu", UiIcons.CLOSE,
            "Ô cần bỏ đánh dấu", "input#newsletter", null, null),

    HOVER(Group.INTERACTION, "HOVER", "Rê chuột", UiIcons.MOUSE,
            "Phần tử cần rê chuột", ".menu-item", null, null),

    PRESS_KEY(Group.INTERACTION, "PRESS_KEY", "Nhấn phím", UiIcons.CODE,
            "Phần tử nhận phím", "input#search", "Phím cần nhấn", "Enter"),

    WAIT_FOR_ELEMENT(Group.INTERACTION, "WAIT_FOR_ELEMENT", "Chờ phần tử", UiIcons.TIMER,
            "Phần tử cần chờ", ".spinner", null, null),

    WAIT_FOR_LOAD(Group.INTERACTION, "WAIT_FOR_LOAD", "Chờ trang tải xong", UiIcons.TIMER,
            null, null, null, null),

    WAIT_FOR_URL(Group.INTERACTION, "WAIT_FOR_URL", "Chờ chuyển trang", UiIcons.GLOBE,
            "URL mong đợi (chứa)", "/dashboard", null, null),

    RELOAD(Group.INTERACTION, "RELOAD", "Tải lại trang", UiIcons.REFRESH,
            null, null, null, null),

    GO_BACK(Group.INTERACTION, "GO_BACK", "Quay lại trang trước", UiIcons.BACK,
            null, null, null, null),

    DELAY(Group.INTERACTION, "DELAY", "Tạm dừng", UiIcons.TIMER,
            null, null, "Thời gian chờ (ms)", "1000"),

    // ── Kiểm tra ───────────────────────────────────────────────────────────
    ASSERT_VISIBLE(Group.ASSERTION, "ASSERT_VISIBLE", "Kiểm tra hiển thị", UiIcons.EYE,
            "Phần tử phải hiển thị", ".toast-success", null, null),

    ASSERT_HIDDEN(Group.ASSERTION, "ASSERT_HIDDEN", "Kiểm tra phần tử ẩn", UiIcons.EYE_OFF,
            "Phần tử phải ẩn", ".loading-spinner", null, null),

    ASSERT_TEXT(Group.ASSERTION, "ASSERT_TEXT", "Kiểm tra nội dung", UiIcons.CHECK,
            "Phần tử cần kiểm tra", ".page-title", "Nội dung mong đợi", "Danh sách hồ sơ"),

    ASSERT_VALUE(Group.ASSERTION, "ASSERT_VALUE", "Kiểm tra giá trị ô nhập", UiIcons.EDIT,
            "Ô nhập cần kiểm tra", "input#email", "Giá trị mong đợi", "nguyen.van.a@example.com"),

    ASSERT_URL(Group.ASSERTION, "ASSERT_URL", "Kiểm tra URL", UiIcons.GLOBE,
            "URL mong đợi (chứa)", "/dashboard", null, null),

    ASSERT_TITLE(Group.ASSERTION, "ASSERT_TITLE", "Kiểm tra tiêu đề", UiIcons.FILE,
            "Tiêu đề trang mong đợi", "Danh sách hồ sơ", null, null),

    // ── Nâng cao ───────────────────────────────────────────────────────────
    CUSTOM_JS(Group.ADVANCED, "CUSTOM_JS", "Chạy JavaScript", UiIcons.CODE,
            null, null, "Đoạn mã JavaScript", "window.scrollTo(0, document.body.scrollHeight)"),

    /** A {@code //} line: part of the script, but read as a sentence instead of as code. */
    NOTE(Group.ADVANCED, "NOTE", "Ghi chú", UiIcons.COMMENT,
            "Nội dung ghi chú", "Kiểm tra luồng đăng nhập", null, null),

    /** Verbatim Java/Playwright line: comments and hand-written statements the builder cannot model. */
    RAW(Group.ADVANCED, "RAW", "Lệnh tự do", UiIcons.TERMINAL,
            "Câu lệnh Java / Playwright", "page.reload();", null, null);

    /** How the action picker groups its entries, so a long menu stays scannable. */
    public enum Group {
        INTERACTION("Thao tác"),
        ASSERTION("Kiểm tra"),
        ADVANCED("Nâng cao");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** Fallback used for a missing or unknown action code; generated as a locator click. */
    public static final StepAction DEFAULT = CLICK;

    private static final String EMPTY_HINT = "Chưa điền nội dung";

    private final Group group;
    private final String code;
    private final String label;
    private final String iconPath;
    private final String targetLabel;
    private final String targetExample;
    private final String valueLabel;
    private final String valueExample;

    StepAction(Group group, String code, String label, String iconPath,
               String targetLabel, String targetExample,
               String valueLabel, String valueExample) {
        this.group = group;
        this.code = code;
        this.label = label;
        this.iconPath = iconPath;
        this.targetLabel = targetLabel;
        this.targetExample = targetExample;
        this.valueLabel = valueLabel;
        this.valueExample = valueExample;
    }

    /** @return the menu group this action belongs to. */
    public Group group() {
        return group;
    }

    /** @return true when the step verifies something instead of acting on the page. */
    public boolean isAssertion() {
        return group == Group.ASSERTION;
    }

    /** @return true when the step consumes the whole page instead of one element. */
    public boolean isPageLevel() {
        return this == NAVIGATE || this == RELOAD || this == GO_BACK
                || this == WAIT_FOR_LOAD || this == WAIT_FOR_URL;
    }

    /** @return the code persisted in {@code TestStep.action} and consumed by script generation. */
    public String code() {
        return code;
    }

    /** @return the Vietnamese label shown on the step card, never a raw action code. */
    public String label() {
        return label;
    }

    public String iconPath() {
        return iconPath;
    }

    /** @return true when the action reads or writes {@code TestStep.target}. */
    public boolean needsTarget() {
        return targetLabel != null;
    }

    /** @return true when the action consumes {@code TestStep.testData}. */
    public boolean needsValue() {
        return valueLabel != null;
    }

    /** @return caption for the target editor, or {@code null} when the action has none. */
    public String targetLabel() {
        return targetLabel;
    }

    public String targetExample() {
        return targetExample != null ? targetExample : "";
    }

    /** @return caption for the value editor, or {@code null} when the action has none. */
    public String valueLabel() {
        return valueLabel;
    }

    public String valueExample() {
        return valueExample != null ? valueExample : "";
    }

    /**
     * @return false when a field this action needs is still blank, so the card can flag an
     *         unfinished step before it reaches a test run.
     */
    public boolean isConfigured(String target, String value) {
        if (needsTarget() && (target == null || target.isBlank())) {
            return false;
        }
        return !needsValue() || (value != null && !value.isBlank());
    }

    /** @return true for verbatim statements, which are displayed as monospace code. */
    public boolean isRaw() {
        return this == RAW;
    }

    /** CSS modifier applied to the action chip, e.g. {@code action-navigate}. */
    public String styleClass() {
        return "action-" + code.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Builds the one-line summary shown while the card is collapsed.
     *
     * <p>It must read as an instruction a manual tester can follow, so it names the value and the
     * element in plain language instead of echoing a raw statement. A verbatim statement is
     * translated by {@link PlaywrightDescribe} whenever that class recognises the call; only a
     * statement nobody can phrase falls back to showing the code itself.</p>
     */
    public String describe(String target, String value) {
        String t = target == null ? "" : target.trim();
        String v = value == null ? "" : value.trim();
        return switch (this) {
            case NAVIGATE -> t.isEmpty() ? EMPTY_HINT : "mở " + t;
            case CLICK, HOVER -> t.isEmpty() ? EMPTY_HINT : t;
            case DOUBLE_CLICK -> t.isEmpty() ? EMPTY_HINT : "nhấp đúp vào " + t;
            case CHECK -> t.isEmpty() ? EMPTY_HINT : "đánh dấu " + t;
            case UNCHECK -> t.isEmpty() ? EMPTY_HINT : "bỏ đánh dấu " + t;
            case FILL -> t.isEmpty() ? EMPTY_HINT
                    : v.isEmpty() ? "nhập giá trị vào " + t : "nhập «" + v + "» vào " + t;
            case SELECT -> t.isEmpty() ? EMPTY_HINT
                    : v.isEmpty() ? "chọn giá trị tại " + t : "chọn «" + v + "» tại " + t;
            case PRESS_KEY -> t.isEmpty() ? EMPTY_HINT
                    : v.isEmpty() ? "nhấn phím tại " + t : "nhấn «" + v + "» tại " + t;
            case ASSERT_VISIBLE -> t.isEmpty() ? EMPTY_HINT : t + " phải hiển thị";
            case ASSERT_HIDDEN -> t.isEmpty() ? EMPTY_HINT : t + " phải ẩn";
            case ASSERT_TEXT -> t.isEmpty() ? EMPTY_HINT
                    : v.isEmpty() ? t + " phải có nội dung" : t + " chứa «" + v + "»";
            case ASSERT_VALUE -> t.isEmpty() ? EMPTY_HINT
                    : v.isEmpty() ? t + " phải có giá trị" : t + " có giá trị «" + v + "»";
            case ASSERT_URL -> t.isEmpty() ? EMPTY_HINT : "URL chứa " + t;
            case ASSERT_TITLE -> t.isEmpty() ? EMPTY_HINT : "tiêu đề chứa «" + t + "»";
            case WAIT_FOR_ELEMENT -> t.isEmpty() ? EMPTY_HINT : "chờ " + t;
            case WAIT_FOR_LOAD -> "chờ trang tải xong";
            case WAIT_FOR_URL -> t.isEmpty() ? EMPTY_HINT : "chờ URL chuyển tới " + t;
            case RELOAD -> "tải lại trang";
            case GO_BACK -> "quay lại trang trước";
            case DELAY -> v.isEmpty() ? EMPTY_HINT : v + " ms";
            case CUSTOM_JS -> v.isEmpty() ? EMPTY_HINT : "chạy JavaScript: " + v;
            case NOTE -> t.isEmpty() ? EMPTY_HINT : t;
            case RAW -> describeRaw(t);
        };
    }

    /**
     * A raw statement is described in words when {@link PlaywrightDescribe} understands it. An
     * unreadable line keeps its code, so nothing is ever hidden from the tester.
     */
    private static String describeRaw(String statement) {
        if (statement.isEmpty()) {
            return EMPTY_HINT;
        }
        if (PlaywrightDescribe.isComment(statement)) {
            String text = PlaywrightDescribe.commentText(statement);
            return text.isEmpty() ? EMPTY_HINT : text;
        }
        String described = PlaywrightDescribe.describe(statement);
        return described != null ? described : statement;
    }

    /** @return true when {@link #describe} returned code instead of a sentence. */
    public boolean describeReturnsCode(String target, String value) {
        if (this != RAW) {
            return false;
        }
        String statement = target == null ? "" : target.trim();
        return !statement.isEmpty() && !PlaywrightDescribe.isComment(statement)
                && PlaywrightDescribe.describe(statement) == null;
    }

    /** @return the action for {@code code}, falling back to {@link #DEFAULT} for unknown values. */
    public static StepAction fromCode(String code) {
        if (code == null || code.isBlank()) {
            return DEFAULT;
        }
        String upper = code.trim().toUpperCase(Locale.ROOT);
        for (StepAction action : values()) {
            if (action.code.equals(upper)) {
                return action;
            }
        }
        return DEFAULT;
    }
}
