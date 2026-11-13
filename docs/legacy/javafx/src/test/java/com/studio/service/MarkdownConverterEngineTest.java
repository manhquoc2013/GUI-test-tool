package com.studio.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownConverterEngineTest {

    @TempDir
    Path tempDir;

    private MarkdownConverterEngine engine;

    @BeforeEach
    void setUp() {
        engine = new MarkdownConverterEngine();
    }

    @Test
    void testConvertDocxWithHeadingsAndTables() throws IOException {
        Path docxFile = tempDir.resolve("sample.docx");
        try (XWPFDocument doc = new XWPFDocument();
                FileOutputStream out = new FileOutputStream(docxFile.toFile())) {

            XWPFParagraph h1 = doc.createParagraph();
            h1.setStyle("Heading 1");
            XWPFRun r1 = h1.createRun();
            r1.setText("Chức Năng Đăng Nhập");

            XWPFParagraph p = doc.createParagraph();
            XWPFRun r2 = p.createRun();
            r2.setBold(true);
            r2.setText("Yêu cầu hệ thống");

            XWPFTable table = doc.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Trường");
            table.getRow(0).getCell(1).setText("Quy tắc");
            table.getRow(1).getCell(0).setText("Email");
            table.getRow(1).getCell(1).setText("Đúng định dạng");

            doc.write(out);
        }

        MarkdownConverterEngine.ConversionResult result = engine.convert(docxFile);
        assertNotNull(result);
        assertEquals("docx", result.getSourceType());
        assertTrue(result.getMarkdown().contains("---"));
        assertTrue(result.getMarkdown().contains("source_file: \"sample.docx\""));
        assertTrue(result.getMarkdown().contains("# Chức Năng Đăng Nhập"));
        assertTrue(result.getMarkdown().contains("**Yêu cầu hệ thống**"));
        assertTrue(result.getMarkdown().contains("| Trường | Quy tắc |"));
        assertTrue(result.getMarkdown().contains("| Email | Đúng định dạng |"));
    }

    @Test
    void testConvertExcelWorkbook() throws IOException {
        Path xlsxFile = tempDir.resolve("test_matrix.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook();
                FileOutputStream out = new FileOutputStream(xlsxFile.toFile())) {

            Sheet sheet = wb.createSheet("Test Cases");
            Row r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue("TC ID");
            r0.createCell(1).setCellValue("Tiêu Đề");
            r0.createCell(2).setCellValue("Kết Quả");

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("TC_01");
            r1.createCell(1).setCellValue("Đăng nhập hợp lệ");
            r1.createCell(2).setCellValue("Thành công");

            wb.write(out);
        }

        MarkdownConverterEngine.ConversionResult result = engine.convert(xlsxFile);
        assertNotNull(result);
        assertEquals("xlsx", result.getSourceType());
        assertTrue(result.getMarkdown().contains("## Bảng dữ liệu: Test Cases"));
        assertTrue(result.getMarkdown().contains("| TC ID | Tiêu Đề | Kết Quả |"));
        assertTrue(result.getMarkdown().contains("| TC_01 | Đăng nhập hợp lệ | Thành công |"));
        assertEquals(1, result.getSheetOrPageCount());
    }

    @Test
    void testConvertPowerPoint() throws IOException {
        Path pptxFile = tempDir.resolve("presentation.pptx");
        try (XMLSlideShow ppt = new XMLSlideShow();
                FileOutputStream out = new FileOutputStream(pptxFile.toFile())) {

            XSLFSlide slide = ppt.createSlide();
            XSLFTextBox shape = slide.createTextBox();
            XSLFTextParagraph p = shape.addNewTextParagraph();
            p.addNewTextRun().setText("Kiến trúc Test Automation Studio");

            ppt.write(out);
        }

        MarkdownConverterEngine.ConversionResult result = engine.convert(pptxFile);
        assertNotNull(result);
        assertEquals("pptx", result.getSourceType());
        assertTrue(result.getMarkdown().contains("## Slide 1"));
        assertTrue(result.getMarkdown().contains("- Kiến trúc Test Automation Studio"));
    }

    @Test
    void testConvertPdf() throws IOException {
        Path pdfFile = tempDir.resolve("manual.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Huong dan kiem thu tu dong");
                stream.endText();
            }
            document.save(pdfFile.toFile());
        }

        MarkdownConverterEngine.ConversionResult result = engine.convert(pdfFile);
        assertNotNull(result);
        assertEquals("pdf", result.getSourceType());
        assertTrue(result.getMarkdown().contains("Huong dan kiem thu tu dong"));
    }

    @Test
    void testConvertCsvAndJson() throws IOException {
        Path csvFile = tempDir.resolve("data.csv");
        Files.writeString(csvFile, "username,role\nadmin,ADMIN\nuser1,USER");

        MarkdownConverterEngine.ConversionResult csvResult = engine.convert(csvFile);
        assertTrue(csvResult.getMarkdown().contains("| username | role |"));
        assertTrue(csvResult.getMarkdown().contains("| admin | ADMIN |"));

        Path jsonFile = tempDir.resolve("config.json");
        Files.writeString(jsonFile, "{\"browser\": \"chromium\", \"headless\": true}");

        MarkdownConverterEngine.ConversionResult jsonResult = engine.convert(jsonFile);
        assertTrue(jsonResult.getMarkdown().contains("```json"));
        assertTrue(jsonResult.getMarkdown().contains("\"browser\": \"chromium\""));
    }

    @Test
    void testConvertHtml() throws IOException {
        Path htmlFile = tempDir.resolve("page.html");
        Files.writeString(htmlFile, "<html><body><h1>Trang Đăng Nhập</h1><p>Nhập email và mật khẩu.</p></body></html>");

        MarkdownConverterEngine.ConversionResult htmlResult = engine.convert(htmlFile);
        assertTrue(htmlResult.getMarkdown().contains("# Trang Đăng Nhập"));
        assertTrue(htmlResult.getMarkdown().contains("Nhập email và mật khẩu."));
    }

    @Test
    void testConvertCsvWithUtf8Bom() throws IOException {
        Path csvFile = tempDir.resolve("bom_data.csv");
        // Prepend UTF-8 BOM \uFEFF to the header
        Files.writeString(csvFile, "\uFEFFTestID,Scenario,Result\nTC01,Login,Pass\nTC02,Register,Fail");

        MarkdownConverterEngine.ConversionResult res = engine.convert(csvFile);
        assertNotNull(res);
        // Ensure \uFEFF was cleanly stripped from header
        assertFalse(res.getMarkdown().contains("\uFEFF"));
        assertTrue(res.getMarkdown().contains("| TestID | Scenario | Result |"));
        assertTrue(res.getMarkdown().contains("| TC01 | Login | Pass |"));
    }

    @Test
    void testConvertDocxInterleavedParagraphAndTableOrder() throws IOException {
        Path docxFile = tempDir.resolve("interleaved.docx");
        try (XWPFDocument doc = new XWPFDocument();
                FileOutputStream out = new FileOutputStream(docxFile.toFile())) {

            // 1. Heading 1
            XWPFParagraph h1 = doc.createParagraph();
            h1.setStyle("Heading 1");
            h1.createRun().setText("Section 1 Intro");

            // 2. Table 1
            XWPFTable t1 = doc.createTable(1, 2);
            t1.getRow(0).getCell(0).setText("T1-Col1");
            t1.getRow(0).getCell(1).setText("T1-Col2");

            // 3. Heading 2
            XWPFParagraph h2 = doc.createParagraph();
            h2.setStyle("Heading 2");
            h2.createRun().setText("Section 2 Details");

            // 4. Table 2
            XWPFTable t2 = doc.createTable(1, 2);
            t2.getRow(0).getCell(0).setText("T2-Col1");
            t2.getRow(0).getCell(1).setText("T2-Col2");

            doc.write(out);
        }

        String md = engine.convertDocx(docxFile);
        assertNotNull(md);

        // Verify exact sequential order in generated markdown
        int posH1 = md.indexOf("# Section 1 Intro");
        int posT1 = md.indexOf("T1-Col1");
        int posH2 = md.indexOf("## Section 2 Details");
        int posT2 = md.indexOf("T2-Col1");

        assertTrue(posH1 >= 0 && posT1 > posH1, "Table 1 must follow Section 1");
        assertTrue(posH2 > posT1, "Section 2 must follow Table 1");
        assertTrue(posT2 > posH2, "Table 2 must follow Section 2");
    }

    @Test
    void testCliAvailabilityCaching() {
        MarkdownConverterEngine.resetCliCacheForTests();
        boolean firstCheck = engine.isMarkItDownCliAvailable();
        boolean secondCheck = engine.isMarkItDownCliAvailable();
        assertEquals(firstCheck, secondCheck);
    }
}
