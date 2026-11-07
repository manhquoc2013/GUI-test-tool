package com.studio.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * Service to parse requirements and specifications from multiple document formats:
 * - Plaintext, Markdown, JSON, CSV (.txt, .md, .json, .csv, etc.)
 * - Word documents (.docx) via Apache POI
 * - PDF documents (.pdf) via Apache PDFBox
 * - Screenshots and UI mockups (.png, .jpg, .jpeg, .webp, .gif) to Base64 data URLs for Vision
 */
public class DocumentParserService {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "json", "csv", "xml", "html", "yaml", "yml", "java", "js", "ts", "sql", "log"
    );

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "webp", "gif", "bmp"
    );

    public static class ParsedDocument {
        private final String fileName;
        private final Path path;
        private final boolean image;
        private final String content;
        private final String mimeType;
        private final long sizeBytes;

        public ParsedDocument(String fileName, Path path, boolean image, String content, String mimeType, long sizeBytes) {
            this.fileName = fileName;
            this.path = path;
            this.image = image;
            this.content = content;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
        }

        public String getFileName() {
            return fileName;
        }

        public Path getPath() {
            return path;
        }

        public boolean isImage() {
            return image;
        }

        /** Text content if document/text; Base64 data URL if image. */
        public String getContent() {
            return content;
        }

        public String getMimeType() {
            return mimeType;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }
    }

    public ParsedDocument parse(Path file) throws IOException {
        if (file == null || !Files.exists(file)) {
            throw new IOException("File does not exist: " + file);
        }

        String fileName = file.getFileName().toString();
        String ext = getExtension(fileName).toLowerCase(Locale.ROOT);
        long size = Files.size(file);

        if (IMAGE_EXTENSIONS.contains(ext)) {
            String mime = getMimeTypeForImage(ext);
            byte[] bytes = Files.readAllBytes(file);
            String b64 = Base64.getEncoder().encodeToString(bytes);
            String dataUrl = "data:" + mime + ";base64," + b64;
            return new ParsedDocument(fileName, file, true, dataUrl, mime, size);
        }

        if ("docx".equals(ext)) {
            String text = parseDocx(file);
            return new ParsedDocument(fileName, file, false, text, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", size);
        }

        if ("pdf".equals(ext)) {
            String text = parsePdf(file);
            return new ParsedDocument(fileName, file, false, text, "application/pdf", size);
        }

        // Default or text-based files
        String text = parseTextFile(file);
        return new ParsedDocument(fileName, file, false, text, "text/plain", size);
    }

    public String parseTextFile(Path file) throws IOException {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return Files.readString(file, StandardCharsets.ISO_8859_1);
        }
    }

    public String parseDocx(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = new FileInputStream(file.toFile());
             XWPFDocument document = new XWPFDocument(is)) {

            for (XWPFParagraph p : document.getParagraphs()) {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text.trim()).append("\n");
                }
            }

            for (XWPFTable table : document.getTables()) {
                sb.append("\n[Table]\n");
                for (XWPFTableRow row : table.getRows()) {
                    sb.append("| ");
                    row.getTableCells().forEach(cell -> {
                        String cellText = cell.getText() != null ? cell.getText().replace("\n", " ").trim() : "";
                        sb.append(cellText).append(" | ");
                    });
                    sb.append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    public String parsePdf(Path file) throws IOException {
        File f = file.toFile();
        try (PDDocument doc = Loader.loadPDF(f)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc).trim();
        }
    }

    public static String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1) : "";
    }

    public static String getMimeTypeForImage(String ext) {
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> "image/png";
        };
    }

    public static boolean isSupportedExtension(String ext) {
        if (ext == null) return false;
        String e = ext.toLowerCase(Locale.ROOT);
        return TEXT_EXTENSIONS.contains(e) || IMAGE_EXTENSIONS.contains(e) || "docx".equals(e) || "pdf".equals(e);
    }
}
