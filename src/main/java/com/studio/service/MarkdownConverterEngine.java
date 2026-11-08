package com.studio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Universal High-Fidelity Document-to-Markdown Engine.
 * Synthesizes Microsoft MarkItDown architecture with pure Java POI/PDFBox runtimes
 * and an optional MarkItDown CLI bridge.
 *
 * Supported formats:
 * - Word (.docx) -> Markdown headings, lists, bold/italics, tables
 * - Excel (.xlsx, .xls) -> Sheet sections with clean Markdown tables
 * - PowerPoint (.pptx) -> Slide-by-slide markdown with titles, bullets, presenter notes
 * - PDF (.pdf) -> Sectioned Markdown with layout preservation
 * - Plaintext, Markdown, CSV, TSV, JSON, HTML, XML -> Normalized Markdown AST
 */
public class MarkdownConverterEngine {

    private static final Pattern MARKDOWN_TABLE_PIPE = Pattern.compile("\\|");
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static class ConversionResult {
        private final String markdown;
        private final String sourceFileName;
        private final String sourceType;
        private final int wordCount;
        private final int estimatedTokens;
        private final int sheetOrPageCount;
        private final String convertedAt;
        private final String engineUsed;

        public ConversionResult(String markdown, String sourceFileName, String sourceType,
                                int wordCount, int estimatedTokens, int sheetOrPageCount,
                                String convertedAt, String engineUsed) {
            this.markdown = markdown;
            this.sourceFileName = sourceFileName;
            this.sourceType = sourceType;
            this.wordCount = wordCount;
            this.estimatedTokens = estimatedTokens;
            this.sheetOrPageCount = sheetOrPageCount;
            this.convertedAt = convertedAt;
            this.engineUsed = engineUsed;
        }

        public String getMarkdown() {
            return markdown;
        }

        public String getSourceFileName() {
            return sourceFileName;
        }

        public String getSourceType() {
            return sourceType;
        }

        public int getWordCount() {
            return wordCount;
        }

        public int getEstimatedTokens() {
            return estimatedTokens;
        }

        public int getSheetOrPageCount() {
            return sheetOrPageCount;
        }

        public String getConvertedAt() {
            return convertedAt;
        }

        public String getEngineUsed() {
            return engineUsed;
        }
    }

    /**
     * Convert an arbitrary document into structured Markdown.
     */
    public ConversionResult convert(Path file) throws IOException {
        if (file == null || !Files.exists(file)) {
            throw new FileNotFoundException("File does not exist: " + file);
        }

        String fileName = file.getFileName().toString();
        String ext = getExtension(fileName).toLowerCase(Locale.ROOT);
        String nowStr = LocalDateTime.now().format(ISO_FMT);

        // Try Python MarkItDown bridge first if available and configured
        if (isMarkItDownCliAvailable()) {
            try {
                ConversionResult cliResult = convertViaMarkItDownCli(file);
                if (cliResult != null && !cliResult.getMarkdown().isBlank()) {
                    return cliResult;
                }
            } catch (Exception ignored) {
                // Graceful fallback to pure Java engine
            }
        }

        // Native Java Conversion
        StringBuilder body = new StringBuilder();
        int sheetOrPages = 1;

        switch (ext) {
            case "docx" -> {
                body.append(convertDocx(file));
                sheetOrPages = 1;
            }
            case "xlsx", "xls" -> {
                SheetConvertResult r = convertExcel(file);
                body.append(r.markdown);
                sheetOrPages = r.sheetCount;
            }
            case "pptx" -> {
                SlideConvertResult r = convertPptx(file);
                body.append(r.markdown);
                sheetOrPages = r.slideCount;
            }
            case "pdf" -> {
                PdfConvertResult r = convertPdf(file);
                body.append(r.markdown);
                sheetOrPages = r.pageCount;
            }
            case "csv", "tsv" -> {
                body.append(convertCsvOrTsv(file, "tsv".equals(ext) ? "\t" : ","));
            }
            case "json" -> {
                body.append("```json\n").append(Files.readString(file, StandardCharsets.UTF_8).trim()).append("\n```\n");
            }
            case "html", "htm" -> {
                body.append(convertHtml(file));
            }
            case "xml" -> {
                body.append("```xml\n").append(Files.readString(file, StandardCharsets.UTF_8).trim()).append("\n```\n");
            }
            default -> {
                // txt, md, log, etc.
                body.append(Files.readString(file, StandardCharsets.UTF_8).trim()).append("\n");
            }
        }

        String rawContent = body.toString();
        int words = countWords(rawContent);
        int tokens = estimateTokens(rawContent);

        // Build YAML Frontmatter (MarkItDown / AST compliant)
        String fullMarkdown = buildFrontmatter(fileName, ext, nowStr, sheetOrPages, words, tokens, "JavaNativeHybrid")
                + rawContent;

        return new ConversionResult(fullMarkdown, fileName, ext, words, tokens, sheetOrPages, nowStr, "JavaNativeHybrid");
    }

    private String buildFrontmatter(String fileName, String ext, String time, int units, int words, int tokens, String engine) {
        return """
                ---
                source_file: "%s"
                source_type: "%s"
                converted_at: "%s"
                units_count: %d
                word_count: %d
                estimated_tokens: %d
                converter_engine: "%s"
                ---

                """.formatted(fileName, ext, time, units, words, tokens, engine);
    }

    // -------------------------------------------------------------
    // DOCX Conversion
    // -------------------------------------------------------------
    public String convertDocx(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = new FileInputStream(file.toFile());
             XWPFDocument doc = new XWPFDocument(is)) {

            for (IBodyElement elem : doc.getBodyElements()) {
                if (elem instanceof XWPFParagraph p) {
                    formatDocxParagraph(p, sb);
                } else if (elem instanceof XWPFTable table) {
                    sb.append(formatDocxTable(table)).append("\n\n");
                }
            }
        }
        return sb.toString().trim();
    }

    private void formatDocxParagraph(XWPFParagraph p, StringBuilder sb) {
        String text = p.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        text = text.trim();

        String style = p.getStyle();
        String prefix = "";
        if (style != null) {
            String s = style.toLowerCase(Locale.ROOT);
            if (s.contains("heading 1") || s.equals("1")) prefix = "# ";
            else if (s.contains("heading 2") || s.equals("2")) prefix = "## ";
            else if (s.contains("heading 3") || s.equals("3")) prefix = "### ";
            else if (s.contains("heading 4") || s.equals("4")) prefix = "#### ";
        }

        if (prefix.isEmpty()) {
            if (p.getNumID() != null) {
                prefix = "- ";
            }
        }

        // Inline formatting check (bold, italic)
        StringBuilder runSb = new StringBuilder();
        for (XWPFRun run : p.getRuns()) {
            String runText = run.text();
            if (runText == null || runText.isEmpty()) {
                runText = run.getText(0);
            }
            if (runText == null) continue;
            if (run.isBold() && !prefix.startsWith("#")) {
                runSb.append("**").append(runText).append("**");
            } else if (run.isItalic()) {
                runSb.append("*").append(runText).append("*");
            } else {
                runSb.append(runText);
            }
        }

        String formattedLine = runSb.length() > 0 ? runSb.toString().trim() : text;
        sb.append(prefix).append(formattedLine).append("\n\n");
    }

    private String formatDocxTable(XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        boolean isHeader = true;

        for (XWPFTableRow row : rows) {
            List<XWPFTableCell> cells = row.getTableCells();
            sb.append("| ");
            for (XWPFTableCell cell : cells) {
                String cellText = cell.getText() != null
                        ? MARKDOWN_TABLE_PIPE.matcher(cell.getText().replace("\n", " ").trim()).replaceAll("\\\\|")
                        : "";
                sb.append(cellText).append(" | ");
            }
            sb.append("\n");

            if (isHeader) {
                sb.append("| ");
                for (int i = 0; i < cells.size(); i++) {
                    sb.append("--- | ");
                }
                sb.append("\n");
                isHeader = false;
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------
    // EXCEL (.xlsx / .xls) Conversion
    // -------------------------------------------------------------
    public record SheetConvertResult(String markdown, int sheetCount) {}

    public SheetConvertResult convertExcel(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        DataFormatter formatter = new DataFormatter();
        int sheetCount = 0;

        try (InputStream is = new FileInputStream(file.toFile());
             Workbook workbook = WorkbookFactory.create(is)) {

            sheetCount = workbook.getNumberOfSheets();
            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                sb.append("## Bảng dữ liệu: ").append(sheetName).append("\n\n");

                List<List<String>> rows = new ArrayList<>();
                int maxCols = 0;

                for (Row row : sheet) {
                    List<String> rowCells = new ArrayList<>();
                    boolean rowHasContent = false;
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        String val = cell != null ? formatter.formatCellValue(cell).trim() : "";
                        if (!val.isEmpty()) rowHasContent = true;
                        rowCells.add(val);
                    }
                    if (rowHasContent) {
                        rows.add(rowCells);
                        if (rowCells.size() > maxCols) {
                            maxCols = rowCells.size();
                        }
                    }
                }

                if (rows.isEmpty() || maxCols == 0) {
                    sb.append("*(Bảng tính trống)*\n\n");
                    continue;
                }

                // Normalize column lengths and format as Markdown table
                boolean firstRow = true;
                for (List<String> row : rows) {
                    sb.append("| ");
                    for (int c = 0; c < maxCols; c++) {
                        String cellVal = c < row.size() ? row.get(c) : "";
                        cellVal = MARKDOWN_TABLE_PIPE.matcher(cellVal.replace("\n", " ").trim()).replaceAll("\\\\|");
                        sb.append(cellVal).append(" | ");
                    }
                    sb.append("\n");

                    if (firstRow) {
                        sb.append("| ");
                        for (int c = 0; c < maxCols; c++) {
                            sb.append("--- | ");
                        }
                        sb.append("\n");
                        firstRow = false;
                    }
                }
                sb.append("\n");
            }
        }
        return new SheetConvertResult(sb.toString().trim(), sheetCount);
    }

    // -------------------------------------------------------------
    // PPTX Conversion
    // -------------------------------------------------------------
    public record SlideConvertResult(String markdown, int slideCount) {}

    public SlideConvertResult convertPptx(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        int slideCount = 0;

        try (InputStream is = new FileInputStream(file.toFile());
             XMLSlideShow ppt = new XMLSlideShow(is)) {

            slideCount = ppt.getSlides().size();
            for (int i = 0; i < slideCount; i++) {
                var slide = ppt.getSlides().get(i);
                sb.append("## Slide ").append(i + 1).append("\n\n");

                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        for (XSLFTextParagraph p : textShape.getTextParagraphs()) {
                            String pText = p.getText();
                            if (pText == null || pText.isBlank()) continue;
                            int indent = p.getIndentLevel();
                            String prefix = indent > 0 ? "  ".repeat(indent) + "- " : "- ";
                            sb.append(prefix).append(pText.trim()).append("\n");
                        }
                    }
                }

                XSLFNotes notes = slide.getNotes();
                if (notes != null) {
                    for (XSLFShape shape : notes.getShapes()) {
                        if (shape instanceof XSLFTextShape textShape) {
                            String notesText = textShape.getText();
                            if (notesText != null && !notesText.isBlank()) {
                                sb.append("\n> **Presenter Notes:** ").append(notesText.trim()).append("\n");
                            }
                        }
                    }
                }
                sb.append("\n---\n\n");
            }
        }
        return new SlideConvertResult(sb.toString().trim(), slideCount);
    }

    // -------------------------------------------------------------
    // PDF Conversion
    // -------------------------------------------------------------
    public record PdfConvertResult(String markdown, int pageCount) {}

    public PdfConvertResult convertPdf(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        int pages = 1;
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            pages = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            for (int p = 1; p <= pages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String pageText = stripper.getText(doc).trim();
                if (!pageText.isEmpty()) {
                    sb.append("### Trang ").append(p).append("\n\n");
                    // Detect possible headings (e.g., short lines followed by newline)
                    String[] lines = pageText.split("\r?\n");
                    for (String line : lines) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) continue;
                        if (trimmed.length() < 60 && Character.isUpperCase(trimmed.charAt(0)) && !trimmed.endsWith(".")) {
                            sb.append("#### ").append(trimmed).append("\n\n");
                        } else {
                            sb.append(trimmed).append("\n\n");
                        }
                    }
                }
            }
        }
        return new PdfConvertResult(sb.toString().trim(), pages);
    }

    // -------------------------------------------------------------
    // CSV / TSV Conversion
    // -------------------------------------------------------------
    public String convertCsvOrTsv(Path file, String delimiter) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        int expectedCols = 0;

        for (String rawLine : lines) {
            String line = rawLine;
            if (first && line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            if (line.isBlank()) continue;
            String[] parts = line.split(Pattern.quote(delimiter), -1);
            if (first) {
                expectedCols = parts.length;
            }

            sb.append("| ");
            for (int i = 0; i < expectedCols; i++) {
                String val = i < parts.length ? parts[i].trim() : "";
                val = MARKDOWN_TABLE_PIPE.matcher(val).replaceAll("\\\\|");
                sb.append(val).append(" | ");
            }
            sb.append("\n");

            if (first) {
                sb.append("| ");
                for (int i = 0; i < expectedCols; i++) {
                    sb.append("--- | ");
                }
                sb.append("\n");
                first = false;
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------
    // HTML Conversion
    // -------------------------------------------------------------
    public String convertHtml(Path file) throws IOException {
        String html = Files.readString(file, StandardCharsets.UTF_8);
        // Clean and simple regex-based conversion for headings, paragraphs, lists
        String res = html.replaceAll("(?i)<script[\\s\\S]*?</script>", "")
                .replaceAll("(?i)<style[\\s\\S]*?</style>", "")
                .replaceAll("(?i)<h1[^>]*>([\\s\\S]*?)</h1>", "\n# $1\n")
                .replaceAll("(?i)<h2[^>]*>([\\s\\S]*?)</h2>", "\n## $1\n")
                .replaceAll("(?i)<h3[^>]*>([\\s\\S]*?)</h3>", "\n### $1\n")
                .replaceAll("(?i)<h4[^>]*>([\\s\\S]*?)</h4>", "\n#### $1\n")
                .replaceAll("(?i)<li[^>]*>([\\s\\S]*?)</li>", "\n- $1")
                .replaceAll("(?i)<p[^>]*>([\\s\\S]*?)</p>", "\n$1\n")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\n{3,}", "\n\n");
        return res.trim();
    }

    // -------------------------------------------------------------
    // MarkItDown CLI Bridge
    // -------------------------------------------------------------
    private static volatile Boolean markItDownCliAvailable = null;

    public static void resetCliCacheForTests() {
        markItDownCliAvailable = null;
    }

    public boolean isMarkItDownCliAvailable() {
        if (markItDownCliAvailable != null) {
            return markItDownCliAvailable;
        }
        synchronized (MarkdownConverterEngine.class) {
            if (markItDownCliAvailable != null) {
                return markItDownCliAvailable;
            }
            Process p = null;
            try {
                ProcessBuilder pb = new ProcessBuilder("markitdown", "--version");
                pb.redirectErrorStream(true);
                p = pb.start();
                p.getInputStream().transferTo(OutputStream.nullOutputStream());
                boolean finished = p.waitFor(2, TimeUnit.SECONDS);
                if (!finished) {
                    p.destroyForcibly();
                    markItDownCliAvailable = false;
                    return false;
                }
                markItDownCliAvailable = (p.exitValue() == 0);
            } catch (Exception e) {
                markItDownCliAvailable = false;
            } finally {
                if (p != null && p.isAlive()) {
                    p.destroyForcibly();
                }
            }
            return markItDownCliAvailable;
        }
    }

    public ConversionResult convertViaMarkItDownCli(Path file) throws IOException, InterruptedException {
        Path tempOutput = Files.createTempFile("markitdown_out_", ".md");
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("markitdown", file.toAbsolutePath().toString(), "-o", tempOutput.toAbsolutePath().toString());
            pb.redirectErrorStream(true);
            p = pb.start();

            ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
            Thread drainThread = new Thread(() -> {
                try {
                    p.getInputStream().transferTo(outputCapture);
                } catch (Exception ignored) {}
            }, "markitdown-drain");
            drainThread.setDaemon(true);
            drainThread.start();

            boolean finished = p.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new IOException("MarkItDown CLI timed out after 30 seconds.");
            }
            if (p.exitValue() != 0) {
                throw new IOException("MarkItDown CLI failed (exit " + p.exitValue() + "): "
                        + outputCapture.toString(StandardCharsets.UTF_8).trim());
            }

            String content = Files.readString(tempOutput, StandardCharsets.UTF_8);
            String fileName = file.getFileName().toString();
            String ext = getExtension(fileName);
            int words = countWords(content);
            int tokens = estimateTokens(content);
            String nowStr = LocalDateTime.now().format(ISO_FMT);
            String fullMd = buildFrontmatter(fileName, ext, nowStr, 1, words, tokens, "PythonMarkItDownCLI") + content;
            return new ConversionResult(fullMd, fileName, ext, words, tokens, 1, nowStr, "PythonMarkItDownCLI");
        } finally {
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
            }
            Files.deleteIfExists(tempOutput);
        }
    }

    public static String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }

    public static int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        String[] w = text.trim().split("\\s+");
        return w.length;
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        // Approximation: ~4 chars per token in English, ~2-3 chars for Vietnamese
        return Math.max(1, (int) Math.ceil(text.length() / 3.5));
    }
}
