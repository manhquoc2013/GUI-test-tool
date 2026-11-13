package com.studio.service;

import com.studio.model.CaseResult;
import com.studio.model.ExportLanguage;
import com.studio.model.RunReport;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A run where every case was skipped is a legitimate outcome — a suite of scenario cases whose scripts
 * are not generated yet. The evidence still has to render: a report a QA shares must say "0 đạt,
 * 0 thất bại, 2 bỏ qua" instead of looking like an empty or broken export.
 */
class SkippedOnlyRunExportTest {

    @TempDir
    Path tempDir;

    @Test
    void htmlReportOfAnAllSkippedRunStatesTheSkips() throws Exception {
        RunReport report = skippedOnlyReport();
        Path html = tempDir.resolve("report.html");

        HtmlReportService.export(report, html, ExportLanguage.VIETNAMESE);

        String content = Files.readString(html);
        assertTrue(content.contains("TC01_Chua_Co_Script"), "The skipped case is still listed");
        assertTrue(content.contains("TC02_Chi_Co_Ghi_Chu"), "Every skipped case is listed");
        assertTrue(content.contains("Chưa có script để chạy"),
                "The report says why the case did not run, not that it failed");
        assertTrue(content.contains("Script chưa có mã thực thi"),
                "A notes-only script reports its own reason");
        assertFalse(content.contains(">null<"), "A skipped case has no failure text to render");
    }

    @Test
    void excelReportOfAnAllSkippedRunHasOneRowPerCaseAndNoFailureColumnGarbage() throws Exception {
        RunReport report = skippedOnlyReport();
        Path xlsx = tempDir.resolve("report.xlsx");

        ExcelReportService.export(report, xlsx, ExportLanguage.VIETNAMESE);

        assertTrue(Files.isRegularFile(xlsx), "The export must produce a workbook");
        try (InputStream in = Files.newInputStream(xlsx); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            // Sheet 0 is the summary block; the cases live on their own sheet.
            Sheet sheet = workbook.getSheetAt(1);
            assertNotNull(sheet, "The workbook has a cases sheet");
            List<String> rows = new ArrayList<>();
            for (Row row : sheet) {
                rows.add(rowText(row));
            }
            assertTrue(rows.stream().anyMatch(row -> row.contains("TC01_Chua_Co_Script")),
                    "Every skipped case keeps its row: " + rows);
            assertTrue(rows.stream().anyMatch(row -> row.contains("TC02_Chi_Co_Ghi_Chu")),
                    "Every skipped case keeps its row: " + rows);
            assertTrue(rows.stream().anyMatch(row -> row.contains("Chưa có script để chạy")),
                    "The skip reason is the evidence a reader needs: " + rows);
            assertTrue(rows.stream().anyMatch(row -> row.contains("Script chưa có mã thực thi")),
                    "Each skipped case carries its own reason: " + rows);
            assertTrue(rows.stream().noneMatch(row -> row.contains("null")),
                    "A skipped case has no failure text; 'null' must not leak into the sheet: " + rows);
        }
    }

    private static RunReport skippedOnlyReport() {
        RunReport report = new RunReport("run-skipped-only", "SUITE", "/groups/SuiteA", "SuiteA",
                "CHROMIUM", true, false, false);
        report.setStartedAt("2026-11-13T04:00:00Z");
        report.setFinishedAt("2026-11-13T04:00:01Z");
        report.setTotal(2);
        report.setPassed(0);
        report.setFailed(0);
        report.setSkipped(2);
        report.setCases(List.of(
                new CaseResult(1, "SuiteA", "SuiteA", "tc-1", "TC01_Chua_Co_Script",
                        "", "SKIPPED", "Chưa có script để chạy", 12, null, null, 0, null),
                new CaseResult(2, "SuiteA", "SuiteA", "tc-2", "TC02_Chi_Co_Ghi_Chu",
                        "TC02_Chi_Co_Ghi_Chu.java", "SKIPPED", "Script chưa có mã thực thi",
                        8, null, null, 0, null)));
        return report;
    }

    private static String rowText(Row row) {
        StringBuilder text = new StringBuilder();
        row.forEach(cell -> text.append(cell.toString()).append(" | "));
        return text.toString();
    }
}
