package com.studio.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The splitter is what keeps a long specification inside one reply per section, so these tests pin
 * that nothing is dropped, nothing overflows the batch budget, and a document with no headings still
 * makes progress.
 */
class AiSectionBatchesTest {

    private static final int BUDGET = 1_000;

    @Test
    void blankMaterialYieldsNoSections() {
        assertTrue(AiSectionBatches.split(null, BUDGET).isEmpty());
        assertTrue(AiSectionBatches.split("   \n\n ", BUDGET).isEmpty());
    }

    @Test
    void materialThatFitsStaysOneSectionLabelledByItsHeading() {
        List<AiSectionBatches.Section> sections = AiSectionBatches.split("## Màn hình đăng nhập\nnội dung", BUDGET);

        assertEquals(1, sections.size());
        assertEquals("Màn hình đăng nhập", sections.get(0).label());
        assertEquals(1, sections.get(0).total());
        assertEquals(0, sections.get(0).index());
    }

    @Test
    void longMaterialIsSplitIntoOrderedSectionsThatKeepEveryLine() {
        StringBuilder material = new StringBuilder();
        for (int i = 1; i <= 40; i++) {
            material.append("## Màn hình ").append(i).append('\n')
                    .append("MARKER-").append(i).append(" yêu cầu chức năng chi tiết\n");
        }

        List<AiSectionBatches.Section> sections = AiSectionBatches.split(material.toString(), BUDGET);

        assertTrue(sections.size() > 1, "tài liệu dài phải được chia phần");
        String joined = sections.stream().map(AiSectionBatches.Section::body).collect(Collectors.joining("\n"));
        for (int i = 1; i <= 40; i++) {
            assertTrue(joined.contains("MARKER-" + i), "không được mất nội dung của màn hình " + i);
        }
        for (int i = 0; i < sections.size(); i++) {
            assertEquals(i, sections.get(i).index());
            assertEquals(sections.size(), sections.get(i).total());
        }
    }

    @Test
    void noSectionExceedsTheBatchBudget() {
        String material = "một dòng nội dung khá dài ".repeat(500);

        for (AiSectionBatches.Section section : AiSectionBatches.split(material, BUDGET)) {
            assertTrue(section.body().length() <= BUDGET,
                    "phần vượt ngân sách sẽ bị nhà cung cấp cắt: " + section.body().length());
        }
    }

    @Test
    void oneOverLongLineIsCutInsteadOfLoopingForever() {
        String material = "X".repeat(2_500);

        List<AiSectionBatches.Section> sections = AiSectionBatches.split(material, BUDGET);

        assertTrue(sections.size() >= 3, "một dòng dài hơn ngân sách phải bị cắt thành nhiều mảnh");
        assertEquals(2_500, sections.stream().mapToInt(s -> s.body().length()).sum());
        for (AiSectionBatches.Section section : sections) {
            assertTrue(section.body().length() <= BUDGET);
            assertFalse(section.label().isBlank(), "mỗi phần phải có nhãn để merge có tiêu đề");
        }
    }

    @Test
    void aSectionThatContinuesATableIsLabelledByItsHeadingNotByADataRow() {
        StringBuilder material = new StringBuilder("### 3.1 Bảng trường nhập\n");
        for (int i = 1; i <= 60; i++) {
            material.append("| ").append(i).append(" | Trường số ").append(i)
                    .append(" | bắt buộc | mô tả chi tiết của trường |\n");
        }
        material.append("### 4.2 Luồng xử lý\nbước 1 đến bước 15\n");

        List<AiSectionBatches.Section> sections = AiSectionBatches.split(material.toString(), BUDGET);

        assertTrue(sections.size() > 1, "bảng dài phải bị chia phần");
        assertTrue(sections.get(0).label().contains("3.1"),
                "phần đầu lấy nhãn từ heading của nó, got: " + sections.get(0).label());
        String second = sections.get(1).label();
        assertFalse(second.startsWith("|"),
                "nhãn không được là một dòng dữ liệu bảng, got: " + second);
        assertTrue(second.contains("4.2") || second.contains("3.1"),
                "nhãn phải chỉ ra mục mà phần này thuộc về, got: " + second);
    }

    @Test
    void aBudgetBelowTheFloorIsRaisedInsteadOfProducingLineSizedChunks() {
        List<AiSectionBatches.Section> sections = AiSectionBatches.split("a".repeat(5_000), 10);

        assertTrue(sections.get(0).body().length() >= AiSectionBatches.MIN_BATCH_CHARS,
                "ngân sách quá nhỏ sẽ tạo hàng nghìn lượt gọi; sàn phải được áp dụng");
    }
}
