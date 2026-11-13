package com.studio.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The No-Code view exists so a manual tester never has to read Playwright. A recorded script is full
 * of statements the builder can only keep verbatim, so these tests pin the sentence each one turns
 * into — and, just as importantly, that an unrecognised call is never guessed at.
 */
class PlaywrightDescribeTest {

    @Test
    void pageLevelStatementsReadAsWhatHappensOnThePage() {
        assertEquals("mở https://example.com", PlaywrightDescribe.describe("page.navigate(\"https://example.com\");"));
        assertEquals("tải lại trang", PlaywrightDescribe.describe("page.reload();"));
        assertEquals("quay lại trang trước", PlaywrightDescribe.describe("page.goBack();"));
        assertEquals("đi tới trang sau", PlaywrightDescribe.describe("page.goForward();"));
        assertEquals("chờ trang tải xong", PlaywrightDescribe.describe("page.waitForLoadState();"));
        // The options form is exactly the statement that used to fill a card with package names.
        assertEquals("chờ trang tải xong",
                PlaywrightDescribe.describe("page.waitForLoadState(LoadState.DOMCONTENTLOADED);"));
        assertEquals("chờ trang tải xong", PlaywrightDescribe.describe(
                "page.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD);"));
        assertEquals("chờ URL chuyển tới /dashboard",
                PlaywrightDescribe.describe("page.waitForURL(\"**/dashboard\");"));
        assertEquals("1000 ms", PlaywrightDescribe.describe("page.waitForTimeout(1000);"));
        assertEquals("chạy JavaScript trên trang",
                PlaywrightDescribe.describe("page.evaluate(\"window.scrollTo(0, 0)\");"));
        assertEquals("nhấn phím «Enter»", PlaywrightDescribe.describe("page.keyboard.press(\"Enter\");"));
    }

    @Test
    void elementStatementsNameTheElementAndTheValue() {
        assertEquals("nhấp vào phần tử «button.submit»",
                PlaywrightDescribe.describe("page.locator(\"button.submit\").click();"));
        assertEquals("gõ «admin» vào phần tử «input#email»",
                PlaywrightDescribe.describe("page.locator(\"input#email\").fill(\"admin\");"));
        assertEquals("xoá nội dung phần tử «input#email»",
                PlaywrightDescribe.describe("page.locator(\"input#email\").fill(\"\");"));
        assertEquals("đánh dấu phần tử «input#agree»",
                PlaywrightDescribe.describe("page.locator(\"input#agree\").check();"));
        assertEquals("bỏ đánh dấu phần tử «input#agree»",
                PlaywrightDescribe.describe("page.locator(\"input#agree\").uncheck();"));
        assertEquals("chọn «VN» tại phần tử «select#country»",
                PlaywrightDescribe.describe("page.locator(\"select#country\").selectOption(\"VN\");"));
        assertEquals("nhấn «Enter» tại phần tử «input#q»",
                PlaywrightDescribe.describe("page.locator(\"input#q\").press(\"Enter\");"));
        assertEquals("chờ phần tử «.spinner» xuất hiện",
                PlaywrightDescribe.describe("page.locator(\".spinner\").waitFor();"));
    }

    @Test
    void statementsWithOptionsStayDescribableEvenWhenTheBuilderCannotRegenerateThem() {
        // A force-click carries an option the generator does not model, so the step stays RAW. The
        // tester still gets a sentence, and the code is preserved elsewhere on the card.
        assertEquals("nhấp vào phần tử «#go»", PlaywrightDescribe.describe(
                "page.locator(\"#go\").click(new Page.ClickOptions().setForce(true));"));
        assertEquals("gõ «a)b» vào phần tử «#q»", PlaywrightDescribe.describe(
                "page.locator(\"#q\").fill(\"a)b\");"));
    }

    @Test
    void assertionsSayWhatIsBeingVerified() {
        assertEquals("kiểm tra phần tử «#toast» hiển thị",
                PlaywrightDescribe.describe("assertThat(page.locator(\"#toast\")).isVisible();"));
        assertEquals("kiểm tra phần tử «#toast» không hiển thị",
                PlaywrightDescribe.describe("assertThat(page.locator(\"#toast\")).isHidden();"));
        assertEquals("kiểm tra phần tử «.title» chứa «Hồ sơ»",
                PlaywrightDescribe.describe("assertThat(page.locator(\".title\")).hasText(\"Hồ sơ\");"));
        assertEquals("kiểm tra phần tử «#email» chứa «a@b.c»",
                PlaywrightDescribe.describe("assertThat(page.locator(\"#email\")).hasValue(\"a@b.c\");"));
        assertEquals("kiểm tra trang có URL chứa «/dashboard»",
                PlaywrightDescribe.describe("assertThat(page).hasURL(\"/dashboard\");"));
        assertEquals("kiểm tra trang có tiêu đề chứa «Hồ sơ»",
                PlaywrightDescribe.describe("assertThat(page).hasTitle(\"Hồ sơ\");"));
        assertEquals("kiểm tra phần tử «#ok» hoạt động",
                PlaywrightDescribe.describe("assertThat(page.locator(\"#ok\")).isEnabled();"));
        assertEquals("kiểm tra phần tử «#ok» không hoạt động",
                PlaywrightDescribe.describe("assertThat(page.locator(\"#ok\")).isDisabled();"));
    }

    @Test
    void playwrightExpectIsTranslatedTooAndNegationIsKept() {
        assertEquals("kiểm tra nút «Đăng nhập» hiển thị", PlaywrightDescribe.describe(
                "expect(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(\"Đăng nhập\")))"
                        + ".toBeVisible();"));
        assertEquals("kiểm tra chữ «Đã lưu» hiển thị",
                PlaywrightDescribe.describe("expect(page.getByText(\"Đã lưu\")).toBeVisible();"));
        assertEquals("kiểm tra ô «Email» chứa «a@b.c»",
                PlaywrightDescribe.describe("expect(page.getByLabel(\"Email\")).toHaveValue(\"a@b.c\");"));
        assertEquals("kiểm tra chữ «Lỗi» không hiển thị",
                PlaywrightDescribe.describe("expect(page.getByText(\"Lỗi\")).not().toBeVisible();"));
    }

    @Test
    void getByLocatorsAreReadAsTheThingTheUserSees() {
        assertEquals("nút «Đăng nhập»",
                PlaywrightDescribe.element("page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()"
                        + ".setName(\"Đăng nhập\"))"));
        assertEquals("ô có gợi ý «Tìm kiếm»",
                PlaywrightDescribe.element("page.getByPlaceholder(\"Tìm kiếm\")"));
        assertEquals("phần tử test-id «row-1»", PlaywrightDescribe.element("page.getByTestId(\"row-1\")"));
        assertEquals("phần tử «input#email»", PlaywrightDescribe.element("page.locator(\"input#email\")"));
        assertEquals("phần tử «li.item» đầu tiên",
                PlaywrightDescribe.element("page.locator(\"li.item\").first()"));
        assertEquals("phần tử «li.item» thứ 3",
                PlaywrightDescribe.element("page.locator(\"li.item\").nth(2)"));
        assertNull(PlaywrightDescribe.element("com.studio.Helper.row(\"x\")"),
                "An unknown expression must not be guessed at");
    }

    @Test
    void anUnrecognisedStatementIsReportedAsUnreadableInsteadOfGuessed() {
        assertNull(PlaywrightDescribe.describe("helper.setup(page, cfg);"));
        assertNull(PlaywrightDescribe.describe("int attempts = 3;"));
        assertNull(PlaywrightDescribe.describe("// chỉ là ghi chú"), "A comment is not a statement");
        assertNull(PlaywrightDescribe.describe(""));
        assertNull(PlaywrightDescribe.describe(null));
    }

    @Test
    void commentsAreDetectedWithoutLosingTheirText() {
        assertTrue(PlaywrightDescribe.isComment("// 1. Điều hướng đến trang web"));
        assertTrue(PlaywrightDescribe.isComment("   // thụt lề"));
        assertFalse(PlaywrightDescribe.isComment("page.reload();"));
        assertEquals("1. Điều hướng đến trang web",
                PlaywrightDescribe.commentText("// 1. Điều hướng đến trang web"));
        assertEquals("", PlaywrightDescribe.commentText("//"));
        assertEquals("", PlaywrightDescribe.commentText("page.reload();"));
    }

    @Test
    void aCallIsSplitEvenWithNestedCallsAndParenthesesInsideStrings() {
        PlaywrightDescribe.Call call = PlaywrightDescribe.parseCall(
                "page.locator(\"a(b\").click(new Page.ClickOptions().setForce(true));");
        assertEquals("page.locator(\"a(b\")", call.receiver());
        assertEquals("click", call.method());
        assertEquals("new Page.ClickOptions().setForce(true)", call.args());

        assertNull(PlaywrightDescribe.parseCall("int x = 1;"));
        assertNull(PlaywrightDescribe.parseCall(null));
        // A statement that is not a call at all is the shape the card must not try to read.
        assertNull(PlaywrightDescribe.parseCall("return helper;"));
        assertNull(PlaywrightDescribe.parseCall("for (int i = 0; i < 3; i++)"));
    }
}
