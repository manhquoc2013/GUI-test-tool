package com.studio.service;

import com.studio.model.Assertion;
import com.studio.model.ExportLanguage;
import com.studio.model.TestCase;
import com.studio.model.TestStep;
import com.studio.model.TestSuite;
import com.studio.store.ProjectStore;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestCaseExcelExportServiceTest {

    @TempDir
    Path tempDir;

    private TestSuite suite;
    private Path suiteDir;

    @BeforeEach
    void setUp() throws IOException {
        suiteDir = tempDir.resolve("TestGroup").resolve("SuiteTest");
        Files.createDirectories(suiteDir);

        suite = new TestSuite();
        suite.setName("SuiteTest");
        suite.setBrowser("chromium");

        List<TestCase> cases = new ArrayList<>();

        // Case 1: Declarative steps and assertions
        TestCase tc1 = new TestCase();
        tc1.setId("TC_LOGIN_01");
        tc1.setName("Đăng nhập thành công với tài khoản hợp lệ");
        tc1.setStatus("Ready");
        tc1.setStartUrl("https://example.com/login");
        tc1.setDescription("Kiểm tra đăng nhập đúng thông tin điều hướng vào dashboard");

        TestStep s1 = new TestStep();
        s1.setStepNumber(1);
        s1.setAction("NAVIGATE");
        s1.setTarget("https://example.com/login");
        s1.setDescription("Mở trang đăng nhập");

        TestStep s2 = new TestStep();
        s2.setStepNumber(2);
        s2.setAction("INPUT");
        s2.setTarget("#email");
        s2.setTestData("admin@test.com");
        s2.setDescription("Nhập email hợp lệ");

        TestStep s3 = new TestStep();
        s3.setStepNumber(3);
        s3.setAction("CLICK");
        s3.setTarget("#btn-login");
        s3.setDescription("Nhấn nút Đăng nhập");
        s3.setExpectedResult("Hệ thống xử lý đăng nhập");

        tc1.setTestSteps(List.of(s1, s2, s3));

        Assertion a1 = new Assertion();
        a1.setType("PAGE_URL_CONTAINS");
        a1.setValue("/dashboard");
        Assertion a2 = new Assertion();
        a2.setType("ELEMENT_VISIBLE");
        a2.setLocator("#welcome-msg");

        tc1.setAssertions(List.of(a1, a2));
        cases.add(tc1);

        // Case 2: Code-based with comment steps
        TestCase tc2 = new TestCase();
        tc2.setId("TC_LOGIN_02");
        tc2.setName("Đăng nhập thất bại với mật khẩu sai");
        tc2.setStatus("Draft");
        tc2.setScriptFile("TC_LOGIN_02.java");

        String javaCode = """
                // 1. Điều hướng đến trang đăng nhập https://example.com/login
                page.navigate("https://example.com/login");
                // 2. Nhập sai mật khẩu 123456
                page.locator("#password").fill("123456");
                // 3. Nhấp nút Submit
                page.locator("button[type='submit']").click();
                """;
        Files.writeString(suiteDir.resolve("TC_LOGIN_02.java"), javaCode);

        Assertion a3 = new Assertion();
        a3.setType("ELEMENT_TEXT_CONTAINS");
        a3.setLocator(".error-alert");
        a3.setValue("Mật khẩu không chính xác");
        tc2.setAssertions(List.of(a3));

        cases.add(tc2);
        suite.setCases(cases);
    }

    @Test
    void testExportSuiteToExcel() throws Exception {
        Path excelOut = tempDir.resolve("test_cases_export.xlsx");

        TestCaseExcelExportService.exportSuite(suite, "TestGroup", suiteDir, excelOut);

        assertTrue(Files.exists(excelOut), "File Excel phải được tạo thành công");
        assertTrue(Files.size(excelOut) > 0, "File Excel không được rỗng");

        try (Workbook wb = new XSSFWorkbook(new FileInputStream(excelOut.toFile()))) {
            assertEquals(3, wb.getNumberOfSheets(), "Workbook phải có đúng 3 sheets chuẩn QA");

            Sheet sOverview = wb.getSheet("Tổng quan");
            Sheet sCases = wb.getSheet("Danh sách Test Cases");
            Sheet sSteps = wb.getSheet("Chi tiết các bước");

            assertNotNull(sOverview, "Sheet Tổng quan phải tồn tại");
            assertNotNull(sCases, "Sheet Danh sách Test Cases phải tồn tại");
            assertNotNull(sSteps, "Sheet Chi tiết các bước phải tồn tại");

            // Check Overview Sheet Title
            Row bannerRow = sOverview.getRow(1);
            assertNotNull(bannerRow);
            assertTrue(bannerRow.getCell(0).getStringCellValue().contains("SuiteTest"));

            // Check Cases Sheet
            // Row 0 is table header, Row 1 is TC1, Row 2 is TC2
            Row tc1Row = sCases.getRow(1);
            assertNotNull(tc1Row, "Hàng TC1 phải có trong sheet Danh sách Test Cases");
            assertEquals("TC_LOGIN_01", tc1Row.getCell(1).getStringCellValue());
            assertEquals("Đăng nhập thành công với tài khoản hợp lệ", tc1Row.getCell(2).getStringCellValue());
            assertEquals("TestGroup", tc1Row.getCell(3).getStringCellValue());
            assertEquals("Ready", tc1Row.getCell(6).getStringCellValue());

            // Verify steps summary contains steps
            String stepsSummary = tc1Row.getCell(8).getStringCellValue();
            assertTrue(stepsSummary.contains("1. Mở trang đăng nhập"));
            assertTrue(stepsSummary.contains("2. Nhập email hợp lệ"));

            // Verify assertions in expected results
            String expResult = tc1Row.getCell(10).getStringCellValue();
            assertTrue(expResult.contains("URL chứa"));
            assertTrue(expResult.contains("/dashboard"));

            // Check TC2 comment steps fallback
            Row tc2Row = sCases.getRow(2);
            assertNotNull(tc2Row);
            String tc2Steps = tc2Row.getCell(8).getStringCellValue();
            assertTrue(tc2Steps.contains("Điều hướng đến trang đăng nhập"));

            // Check Steps Sheet
            // TC1 has 3 steps, TC2 has 1 script row -> at least 4 rows
            assertTrue(sSteps.getLastRowNum() >= 4, "Sheet Chi tiết các bước phải có dữ liệu bước kiểm thử");
            Row step1Row = sSteps.getRow(1);
            assertEquals("TC_LOGIN_01", step1Row.getCell(1).getStringCellValue());
            assertEquals(1, (int) step1Row.getCell(3).getNumericCellValue());
        }
    }

    @Test
    void testExportGroupToExcel() throws Exception {
        ProjectStore store = new ProjectStore();
        store.saveSuite(suite, suiteDir);

        Path groupDir = tempDir.resolve("TestGroup");
        Path excelOut = tempDir.resolve("group_export.xlsx");

        TestCaseExcelExportService.exportGroup(groupDir, store, excelOut);

        assertTrue(Files.exists(excelOut), "File Excel xuất theo Group phải tồn tại");
        try (Workbook wb = new XSSFWorkbook(new FileInputStream(excelOut.toFile()))) {
            Sheet sOverview = wb.getSheet("Tổng quan");
            assertNotNull(sOverview);
            Row bannerRow = sOverview.getRow(1);
            assertTrue(bannerRow.getCell(0).getStringCellValue().contains("TestGroup"));

            Sheet sCases = wb.getSheet("Danh sách Test Cases");
            assertNotNull(sCases);
            // Must have 2 test cases (Row 0 is header, Row 1 is TC1, Row 2 is TC2)
            assertEquals("TC_LOGIN_01", sCases.getRow(1).getCell(1).getStringCellValue());
            assertEquals("TC_LOGIN_02", sCases.getRow(2).getCell(1).getStringCellValue());
        }
    }

    @Test
    void testExportSuiteToExcelEnglish() throws Exception {
        Path excelOut = tempDir.resolve("test_cases_export_en.xlsx");

        TestCaseExcelExportService.exportSuite(suite, "TestGroup", suiteDir, excelOut, ExportLanguage.ENGLISH);

        assertTrue(Files.exists(excelOut), "Excel file must be created");
        try (Workbook wb = new XSSFWorkbook(new FileInputStream(excelOut.toFile()))) {
            assertEquals(3, wb.getNumberOfSheets());

            Sheet sOverview = wb.getSheet("Overview");
            Sheet sCases = wb.getSheet("Test Cases");
            Sheet sSteps = wb.getSheet("Step Details");

            assertNotNull(sOverview, "Overview sheet must exist in English");
            assertNotNull(sCases, "Test Cases sheet must exist in English");
            assertNotNull(sSteps, "Step Details sheet must exist in English");

            Row bannerRow = sOverview.getRow(1);
            assertNotNull(bannerRow);
            assertTrue(bannerRow.getCell(0).getStringCellValue().contains("TEST SUITE: SuiteTest"));

            // Check English table headers in Test Cases sheet
            Row headerRow = sCases.getRow(0);
            assertNotNull(headerRow);
            assertEquals("TC ID", headerRow.getCell(1).getStringCellValue());
            assertEquals("Test Case Name", headerRow.getCell(2).getStringCellValue());
            assertEquals("Preconditions", headerRow.getCell(7).getStringCellValue());
            assertEquals("Execution Steps Summary", headerRow.getCell(8).getStringCellValue());
            assertEquals("Expected Results", headerRow.getCell(10).getStringCellValue());
        }
    }

    @Test
    void testExportSuiteOversizedTextTruncation() throws Exception {
        // Create a test case with >35,000 characters in steps
        TestCase hugeCase = new TestCase();
        hugeCase.setId("TC_HUGE_01");
        hugeCase.setName("TC With Oversized Content");
        hugeCase.setStatus("Ready");

        TestStep hugeStep = new TestStep();
        hugeStep.setStepNumber(1);
        hugeStep.setAction("INPUT");
        hugeStep.setDescription("A".repeat(35000));
        hugeCase.setTestSteps(List.of(hugeStep));

        TestSuite hugeSuite = new TestSuite();
        hugeSuite.setName("HugeSuite");
        hugeSuite.setCases(List.of(hugeCase));

        Path excelOut = tempDir.resolve("huge_export.xlsx");
        assertDoesNotThrow(() -> {
            TestCaseExcelExportService.exportSuite(hugeSuite, "TestGroup", suiteDir, excelOut);
        }, "Export should not throw IllegalArgumentException when cell text exceeds 32767 chars");

        assertTrue(Files.exists(excelOut));
        try (Workbook wb = new XSSFWorkbook(new FileInputStream(excelOut.toFile()))) {
            Sheet sCases = wb.getSheet("Danh sách Test Cases");
            assertNotNull(sCases);
            Row r1 = sCases.getRow(1);
            // Column 8 is stepsSummary
            String cellVal = r1.getCell(8).getStringCellValue();
            assertTrue(cellVal.contains("TRUNCATED"), "Oversized content should be safely truncated");
            assertTrue(cellVal.length() <= 32767, "Length must be within Excel limit");
        }
    }
}
