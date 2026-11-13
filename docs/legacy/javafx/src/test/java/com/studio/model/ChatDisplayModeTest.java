package com.studio.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the chat bubble excerpt rules. Each case fails if a mode starts clipping the
 * stored text silently or starts hiding less than the user asked for.
 */
class ChatDisplayModeTest {

    private static final String LONG_ANSWER = String.join("\n",
            "# Bộ Test Case — Chức năng hồ sơ",
            "",
            "## Ghi chú phạm vi",
            "Tài liệu chỉ chứa phần mở đầu và mục lục 23 mục.",
            "",
            "```java",
            "String hidden = \"code block must not leak into the excerpt\";",
            "```",
            "",
            "| Chỉ số | Giá trị |",
            "|---|---|",
            "| Mục | 23 |",
            "",
            "Dòng thứ tư của phần thân.",
            "Dòng thứ năm của phần thân.",
            "Dòng thứ sáu của phần thân.",
            "Dòng thứ bảy của phần thân.",
            "Dòng thứ tám của phần thân.",
            "Dòng thứ chín của phần thân.",
            "Dòng thứ mười của phần thân.");

    @Test
    void detailModeReturnsTheStoredContentUnchanged() {
        ChatDisplayMode.Excerpt excerpt = ChatDisplayMode.DETAIL.excerpt(LONG_ANSWER);

        assertEquals(LONG_ANSWER, excerpt.text());
        assertFalse(excerpt.truncated(), "detail mode never hides text");
    }

    @Test
    void compactModeKeepsASingleLineAndFlagsTruncation() {
        ChatDisplayMode.Excerpt excerpt = ChatDisplayMode.COMPACT.excerpt(LONG_ANSWER);

        assertTrue(excerpt.truncated(), "compact mode must offer the toggle");
        assertFalse(excerpt.text().contains("\n"), "compact mode is one line only");
        assertFalse(excerpt.text().contains("#"), "markdown heading marks are stripped");
        assertTrue(excerpt.text().endsWith(ChatDisplayMode.ELLIPSIS));
    }

    @Test
    void summaryModeCapsLinesAndRemovesNoiseButKeepsTheGist() {
        ChatDisplayMode.Excerpt excerpt = ChatDisplayMode.SUMMARY.excerpt(LONG_ANSWER);

        assertTrue(excerpt.truncated());
        assertTrue(excerpt.text().startsWith("Bộ Test Case — Chức năng hồ sơ"), "the first heading is the gist");
        assertTrue(excerpt.text().lines().count() <= 8, "summary mode is capped at 8 lines");
        assertFalse(excerpt.text().contains("code block must not leak"), "fenced code is not an excerpt");
        assertFalse(excerpt.text().contains("|"), "table rows are dropped from the excerpt");
        assertFalse(excerpt.text().contains("---"), "table separators and rules are dropped");
    }

    @Test
    void shortPlainMessageIsNotTruncated() {
        ChatDisplayMode.Excerpt excerpt = ChatDisplayMode.SUMMARY.excerpt("Đã tạo 12 test case.");

        assertEquals("Đã tạo 12 test case.", excerpt.text());
        assertFalse(excerpt.truncated(), "no toggle when nothing is hidden");
    }

    @Test
    void cleanedButCompleteTextStillOffersTheToggle() {
        // Nothing dropped by the line/char budget, but the markdown marks were removed: the rendered
        // text differs from what the user typed, so the full version must stay reachable.
        ChatDisplayMode.Excerpt excerpt = ChatDisplayMode.SUMMARY.excerpt("**Đã xong**");

        assertEquals("Đã xong" + ChatDisplayMode.ELLIPSIS, excerpt.text());
        assertTrue(excerpt.truncated());
    }

    @Test
    void emptyContentProducesNoBubbleTextAndNoToggle() {
        ChatDisplayMode.Excerpt excerpt = ChatDisplayMode.COMPACT.excerpt("   ");

        assertEquals("", excerpt.text());
        assertFalse(excerpt.truncated());
    }

    @Test
    void unknownPersistedKeyFallsBackToSummary() {
        assertEquals(ChatDisplayMode.SUMMARY, ChatDisplayMode.fromKey(null));
        assertEquals(ChatDisplayMode.SUMMARY, ChatDisplayMode.fromKey(" "));
        assertEquals(ChatDisplayMode.SUMMARY, ChatDisplayMode.fromKey("verbose"));
        assertEquals(ChatDisplayMode.COMPACT, ChatDisplayMode.fromKey("COMPACT"));
        assertEquals("summary", ChatDisplayMode.SUMMARY.key());
        assertEquals("Tổng hợp", ChatDisplayMode.SUMMARY.getLabel());
    }
}
