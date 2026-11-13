package com.studio.ui;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the contract between the persisted action codes and what a person reads in the builder.
 *
 * <p>The model must keep storing {@code NAVIGATE}/{@code CLICK}/{@code RAW}… because script
 * generation and previously saved suites depend on them, but no card may ever show those codes:
 * a manual tester cannot tell "RAW" from "ASSERT_TEXT".</p>
 */
class StepActionTest {

    @Test
    void everyPersistedCodeMapsToAReadableVietnameseLabel() {
        Set<String> codes = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (StepAction action : StepAction.values()) {
            assertTrue(codes.add(action.code()), "Duplicate action code: " + action.code());
            assertTrue(labels.add(action.label()), "Duplicate label: " + action.label());
            assertFalse(action.label().isBlank(), action.code() + " has no label");
            assertFalse(action.label().equals(action.code()),
                    action.code() + " still shows its raw code as the label");
            assertNotNull(action.iconPath(), action.code() + " has no icon");
            assertFalse(action.iconPath().isBlank(), action.code() + " has an empty icon path");
            assertTrue(action.styleClass().startsWith("action-"),
                    "Chip style must stay scoped to the action-* family: " + action.styleClass());
        }
        // Every action documented in the builder spec is present.
        for (String expected : new String[]{"NAVIGATE", "CLICK", "FILL", "SELECT", "HOVER",
                "ASSERT_VISIBLE", "ASSERT_HIDDEN", "ASSERT_TEXT", "ASSERT_VALUE", "ASSERT_URL",
                "ASSERT_TITLE", "WAIT_FOR_ELEMENT", "DELAY", "CUSTOM_JS", "RAW"}) {
            assertTrue(codes.contains(expected), "Missing action mapping for " + expected);
        }
    }

    @Test
    void unknownOrBlankCodesFallBackToTheDefaultAction() {
        assertSame(StepAction.DEFAULT, StepAction.fromCode(null));
        assertSame(StepAction.DEFAULT, StepAction.fromCode("  "));
        assertSame(StepAction.DEFAULT, StepAction.fromCode("NOT_AN_ACTION"));
    }

    @Test
    void codesAreMatchedCaseInsensitivelyAndKeepRawVerbatim() {
        assertSame(StepAction.NAVIGATE, StepAction.fromCode("navigate"));
        assertSame(StepAction.ASSERT_TEXT, StepAction.fromCode(" Assert_Text "));
        assertSame(StepAction.RAW, StepAction.fromCode("raw"),
                "RAW must survive so hand-written statements keep their model slot");
        assertTrue(StepAction.RAW.isRaw());
        assertFalse(StepAction.CLICK.isRaw());
    }

    @Test
    void onlyActionsThatConsumeAValueRenderThatField() {
        assertFalse(StepAction.CLICK.needsValue(), "A click has no value to type");
        assertFalse(StepAction.NAVIGATE.needsValue());
        assertFalse(StepAction.RAW.needsValue());
        assertTrue(StepAction.FILL.needsValue());
        assertTrue(StepAction.SELECT.needsValue());
        assertTrue(StepAction.ASSERT_TEXT.needsValue());
        assertTrue(StepAction.DELAY.needsValue());
        assertTrue(StepAction.CUSTOM_JS.needsValue());
    }

    @Test
    void onlyActionsThatTargetAnElementRenderTheTargetField() {
        assertTrue(StepAction.CLICK.needsTarget());
        assertTrue(StepAction.NAVIGATE.needsTarget());
        assertTrue(StepAction.RAW.needsTarget());
        assertFalse(StepAction.DELAY.needsTarget(), "A pause has no element to point at");
        assertFalse(StepAction.CUSTOM_JS.needsTarget());
    }

    @Test
    void actionCaptionsAreNeverNull() {
        for (StepAction action : StepAction.values()) {
            if (action.needsTarget()) {
                assertNotNull(action.targetLabel());
                assertFalse(action.targetExample().isBlank(), action + " has no target example");
            }
            if (action.needsValue()) {
                assertNotNull(action.valueLabel());
                assertFalse(action.valueExample().isBlank(), action + " has no value example");
            }
        }
    }

    @Test
    void aStepIsOnlyReadyWhenItsRequiredFieldsAreFilled() {
        assertFalse(StepAction.CLICK.isConfigured("", ""), "A click without a selector does nothing");
        assertTrue(StepAction.CLICK.isConfigured("button.submit", ""));
        assertFalse(StepAction.FILL.isConfigured("input#email", " "),
                "A fill without a value would type nothing");
        assertTrue(StepAction.FILL.isConfigured("input#email", "admin"));
        assertFalse(StepAction.DELAY.isConfigured("", ""), "A pause needs a duration");
        assertFalse(StepAction.NAVIGATE.isConfigured(null, null), "A navigation needs a URL");
    }

    @Test
    void summariesReadAsInstructions() {
        assertEquals("mở https://example.com/login",
                StepAction.NAVIGATE.describe("https://example.com/login", ""));
        assertEquals("nhập «admin» vào input#email",
                StepAction.FILL.describe("input#email", "admin"));
        assertEquals("input#email phải hiển thị",
                StepAction.ASSERT_VISIBLE.describe("input#email", ""));
        assertEquals("500 ms", StepAction.DELAY.describe("", "500"));
        // A raw line is read out in words; only a statement nobody can phrase keeps its code.
        assertEquals("tải lại trang", StepAction.RAW.describe("page.reload();", ""));
        assertEquals("chờ trang tải xong",
                StepAction.RAW.describe("page.waitForLoadState(LoadState.LOAD);", ""));
        assertEquals("kiểm tra nút «Đăng nhập» hiển thị", StepAction.RAW.describe(
                "expect(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(\"Đăng nhập\")))"
                        + ".toBeVisible();", ""));
        assertEquals("mysteryCall(1, 2);", StepAction.RAW.describe("mysteryCall(1, 2);", ""));
        assertTrue(StepAction.RAW.describeReturnsCode("mysteryCall(1, 2);", ""));
        assertFalse(StepAction.RAW.describeReturnsCode("page.reload();", ""));
        assertEquals("chờ trang tải xong", StepAction.WAIT_FOR_LOAD.describe("", ""));
        assertEquals("tải lại trang", StepAction.RELOAD.describe("", ""));
        assertEquals("quay lại trang trước", StepAction.GO_BACK.describe("", ""));
    }

    @Test
    void emptyStepsSaySoInsteadOfShowingAnEmptyLine() {
        for (StepAction action : StepAction.values()) {
            String summary = action.describe("", "");
            assertFalse(summary.isBlank(), action + " must never render an empty summary row");
            assertEquals(summary, action.describe(null, null),
                    action + " must tolerate null model values");
            // Only an action with a required field may report it as still empty; a page-level action
            // such as RELOAD is complete the moment it is chosen.
            boolean needsInput = action.needsTarget() || action.needsValue();
            assertEquals(needsInput ? "Chưa điền nội dung" : summary, summary,
                    action + " reports the wrong emptiness");
        }
    }

    @Test
    void everyAssertionIsEditableOnTheCardUnderItsOwnGroup() {
        for (StepAction action : new StepAction[]{StepAction.ASSERT_VISIBLE, StepAction.ASSERT_HIDDEN,
                StepAction.ASSERT_TEXT, StepAction.ASSERT_VALUE, StepAction.ASSERT_URL,
                StepAction.ASSERT_TITLE}) {
            assertTrue(action.isAssertion(), action + " must be grouped as an assertion");
            assertTrue(action.needsTarget(), action + " must ask what to check");
        }
        assertFalse(StepAction.CLICK.isAssertion(), "A click verifies nothing");
        assertFalse(StepAction.RAW.isAssertion());

        for (StepAction action : StepAction.values()) {
            assertNotNull(action.group(), action + " has no menu group");
            assertFalse(action.group().label().isBlank(), action.group() + " has no heading");
        }
    }

    @Test
    void assertionSummariesNameWhatMustHold() {
        assertEquals("URL chứa /dashboard", StepAction.ASSERT_URL.describe("/dashboard", ""));
        assertEquals("tiêu đề chứa «Hồ sơ»", StepAction.ASSERT_TITLE.describe("Hồ sơ", ""));
        assertEquals("input#email có giá trị «a@b.c»",
                StepAction.ASSERT_VALUE.describe("input#email", "a@b.c"));
        assertEquals(".spinner phải ẩn", StepAction.ASSERT_HIDDEN.describe(".spinner", ""));
    }
}
