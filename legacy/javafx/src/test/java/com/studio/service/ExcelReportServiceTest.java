package com.studio.service;

import com.studio.model.AssertResult;
import com.studio.model.CaseResult;
import com.studio.model.ExportLanguage;
import com.studio.model.RunReport;
import com.studio.model.StepResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelReportServiceTest {

    @TempDir
    Path tempDir;

    private RunReport report;

    @BeforeEach
    void setUp() {
        report = new RunReport("run-excel-001", "SUITE", "/groups/SuiteA", "SuiteA", "CHROMIUM", true, true, false);
        report.setStartedAt("2026-11-08T10:00:00Z");
        report.setFinishedAt("2026-11-08T10:00:15Z");
        report.setTotal(2);
        report.setPassed(1);
        report.setFailed(1);
        report.setSkipped(0);

        CaseResult c1 = new CaseResult(1, "GroupA", "SuiteA", "tc-1", "TC01_Valid_Auth",
                "TC01.java", "PASSED", null, 1500, null, "All green", 0, null);
        AssertResult a1 = new AssertResult();
        a1.setIndex(1);
        a1.setType("PAGE_TITLE_CONTAINS");
        a1.setDetail("Dashboard");
        a1.setStatus("PASSED");
        c1.setAssertResults(List.of(a1));

        CaseResult c2 = new CaseResult(2, "GroupA", "SuiteA", "tc-2", "TC02_Invalid_Auth",
                "TC02.java", "FAILED", null, 1200, "Element not found", "Error trace...", 1, null);
        report.setCases(List.of(c1, c2));
    }

    @Test
    void testExportVietnameseDefault() throws Exception {
        Path out = tempDir.resolve("report_vi.xlsx");
        ExcelReportService.export(report, out);

        assertTrue(Files.exists(out));
        try (Workbook wb = new XSSFWorkbook(new FileInputStream(out.toFile()))) {
            assertEquals(4, wb.getNumberOfSheets(), "Summary + Cases + Steps + Asserts");
            Sheet sOverview = wb.getSheet("Tổng quan");
            Sheet sCases = wb.getSheet("Danh sách Cases");
            Sheet sSteps = wb.getSheet("Danh sách Bước");
            Sheet sAsserts = wb.getSheet("Danh sách Asserts");

            assertNotNull(sOverview, "Sheet Tổng quan must exist");
            assertNotNull(sCases, "Sheet Danh sách Cases must exist");
            assertNotNull(sSteps, "Sheet Danh sách Bước must exist");
            assertNotNull(sAsserts, "Sheet Danh sách Asserts must exist");

            // Overview title
            Row r0 = sOverview.getRow(0);
            assertEquals("Báo Cáo Thực Thi Kiểm Thử", r0.getCell(0).getStringCellValue());

            // Cases header
            Row hCases = sCases.getRow(0);
            assertEquals("STT", hCases.getCell(0).getStringCellValue());
            assertEquals("Tên Test Case", hCases.getCell(1).getStringCellValue());
            assertEquals("Priority", hCases.getCell(4).getStringCellValue());
            assertEquals("Trạng thái", hCases.getCell(5).getStringCellValue());
        }
    }

    @Test
    void testExportEnglish() throws Exception {
        Path out = tempDir.resolve("report_en.xlsx");
        ExcelReportService.export(report, out, ExportLanguage.ENGLISH);

        assertTrue(Files.exists(out));
        try (Workbook wb = new XSSFWorkbook(new FileInputStream(out.toFile()))) {
            assertEquals(4, wb.getNumberOfSheets(), "Summary + Cases + Steps + Asserts");
            Sheet sOverview = wb.getSheet("Summary");
            Sheet sCases = wb.getSheet("Cases");
            Sheet sSteps = wb.getSheet("Steps");
            Sheet sAsserts = wb.getSheet("Asserts");

            assertNotNull(sOverview, "Sheet Summary must exist in English");
            assertNotNull(sCases, "Sheet Cases must exist in English");
            assertNotNull(sSteps, "Sheet Steps must exist in English");
            assertNotNull(sAsserts, "Sheet Asserts must exist in English");

            // Overview title
            Row r0 = sOverview.getRow(0);
            assertEquals("Test Run Report", r0.getCell(0).getStringCellValue());

            // Cases header
            Row hCases = sCases.getRow(0);
            assertEquals("#", hCases.getCell(0).getStringCellValue());
            assertEquals("Case Name", hCases.getCell(1).getStringCellValue());
            assertEquals("Priority", hCases.getCell(4).getStringCellValue());
            assertEquals("Status", hCases.getCell(5).getStringCellValue());
        }
    }

    /**
     * A spreadsheet a tester opens must say what each step did, and must not imply that a step which
     * never ran took zero milliseconds.
     */
    @Test
    void testStepsSheetListsEveryExecutedStepInReadableWords() throws Exception {
        CaseResult case0 = report.getCases().get(0);
        case0.setStepResults(List.of(
                new StepResult(1, "page.navigate(\"https://example.com\");", StepResult.PASSED, 320, null),
                new StepResult(2, "page.locator(\"input#email\").fill(\"admin\");", StepResult.PASSED, 45, null),
                new StepResult(3, "page.locator(\"#submit\").click();", StepResult.FAILED, 30000,
                        "TimeoutError: waiting for locator(\"#submit\")"),
                new StepResult(4, "helper.setup(page);", StepResult.NOT_RUN, 0,
                        "Bước không sinh câu lệnh để thực thi")));

        Path out = tempDir.resolve("report_steps.xlsx");
        ExcelReportService.export(report, out, ExportLanguage.ENGLISH);

        try (Workbook wb = new XSSFWorkbook(new FileInputStream(out.toFile()))) {
            Sheet sSteps = wb.getSheet("Steps");
            assertNotNull(sSteps);
            Row header = sSteps.getRow(0);
            assertEquals("Case", header.getCell(0).getStringCellValue());
            assertEquals("Step #", header.getCell(1).getStringCellValue());
            assertEquals("Action", header.getCell(2).getStringCellValue());
            assertEquals("Result", header.getCell(3).getStringCellValue());

            Row first = sSteps.getRow(1);
            assertEquals("TC01_Valid_Auth", first.getCell(0).getStringCellValue());
            assertEquals(1, (int) first.getCell(1).getNumericCellValue());
            assertEquals("mở https://example.com", first.getCell(2).getStringCellValue(),
                    "The action column must be the sentence, not the Playwright call");
            assertEquals("PASSED", first.getCell(3).getStringCellValue());

            Row failure = sSteps.getRow(3);
            assertEquals("FAILED", failure.getCell(3).getStringCellValue());
            assertEquals(30000, (int) failure.getCell(4).getNumericCellValue());
            assertTrue(failure.getCell(5).getStringCellValue().contains("TimeoutError"));

            Row notRun = sSteps.getRow(4);
            assertEquals("NOT_RUN", notRun.getCell(3).getStringCellValue());
            assertEquals("", notRun.getCell(4).getStringCellValue(),
                    "A step that never ran must not report a 0 ms duration");

            // A statement the formatter does not understand keeps its code: a report must not hide it.
            assertEquals("helper.setup(page);", notRun.getCell(2).getStringCellValue());
        }
    }

    @Test
    void testExportWithRootOrMalformedScreenshotPathDoesNotThrow() {
        CaseResult cSpecial = new CaseResult(3, "GroupA", "SuiteA", "tc-3", "TC03_Root_Path",
                "TC03.java", "PASSED", null, 100, null, "Log", 0, null);
        // Root path where getFileName() would be null
        cSpecial.setScreenshotPath("/");
        report.setCases(
                new java.util.ArrayList<>(List.of(report.getCases().get(0), report.getCases().get(1), cSpecial)));

        Path out = tempDir.resolve("report_special_path.xlsx");
        assertDoesNotThrow(() -> ExcelReportService.export(report, out));
        assertTrue(Files.exists(out), "Excel export must succeed without NullPointerException on root path");
    }
}
