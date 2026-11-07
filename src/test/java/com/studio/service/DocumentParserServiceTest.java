package com.studio.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DocumentParserServiceTest {

    @TempDir
    Path tempDir;

    private DocumentParserService parser;

    @BeforeEach
    void setUp() {
        parser = new DocumentParserService();
    }

    @Test
    void testParseTextFile() throws IOException {
        Path textFile = tempDir.resolve("requirements.txt");
        Files.writeString(textFile, "User Story: As a customer, I want to login with email and password.");

        DocumentParserService.ParsedDocument doc = parser.parse(textFile);
        assertFalse(doc.isImage());
        assertEquals("requirements.txt", doc.getFileName());
        assertTrue(doc.getContent().contains("User Story: As a customer"));
    }

    @Test
    void testParseDocx() throws IOException {
        Path docxFile = tempDir.resolve("specs.docx");
        try (XWPFDocument document = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(docxFile.toFile())) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("Specification: Check checkout total calculation");
            document.write(out);
        }

        DocumentParserService.ParsedDocument doc = parser.parse(docxFile);
        assertFalse(doc.isImage());
        assertEquals("specs.docx", doc.getFileName());
        assertTrue(doc.getContent().contains("Specification: Check checkout total calculation"));
    }

    @Test
    void testParsePdf() throws IOException {
        Path pdfFile = tempDir.resolve("manual.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Playwright Test Guidelines");
                stream.endText();
            }
            document.save(pdfFile.toFile());
        }

        DocumentParserService.ParsedDocument doc = parser.parse(pdfFile);
        assertFalse(doc.isImage());
        assertEquals("manual.pdf", doc.getFileName());
        assertTrue(doc.getContent().contains("Playwright Test Guidelines"));
    }

    @Test
    void testParseImage() throws IOException {
        Path imgFile = tempDir.resolve("screen.png");
        byte[] fakePng = new byte[]{1, 2, 3, 4, 5};
        Files.write(imgFile, fakePng);

        DocumentParserService.ParsedDocument doc = parser.parse(imgFile);
        assertTrue(doc.isImage());
        assertEquals("image/png", doc.getMimeType());
        assertTrue(doc.getContent().startsWith("data:image/png;base64,"));
    }
}

