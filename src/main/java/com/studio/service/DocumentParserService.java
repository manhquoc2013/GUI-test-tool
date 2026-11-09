package com.studio.service;

import com.studio.util.StudioPaths;
import com.studio.util.StringEscapeUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * Service to parse requirements and specifications from multiple document
 * formats:
 * - Plaintext, Markdown, JSON, CSV, TSV, XML, HTML (.txt, .md, .json, .csv,
 * etc.)
 * - Word documents (.docx) via Apache POI
 * - Excel spreadsheets (.xlsx, .xls) via Apache POI
 * - PowerPoint presentations (.pptx) via Apache POI
 * - PDF documents (.pdf) via Apache PDFBox
 * - Screenshots and UI mockups (.png, .jpg, .jpeg, .webp, .gif) to Base64 data
 * URLs for Vision
 *
 * Automatically converts documents to normalized Markdown files in
 * data/converted_docs/
 * for token-efficient and AST-structured LLM ingestion.
 */
public class DocumentParserService {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "markdown", "json", "csv", "tsv", "xml", "html", "htm", "yaml", "yml", "java", "js", "ts",
            "sql", "log");

    private static final Set<String> OFFICE_EXTENSIONS = Set.of(
            "docx", "xlsx", "xls", "pptx", "pdf");

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "webp", "gif", "bmp");

    private final MarkdownConverterEngine markdownConverter;
    private final AiAgentIndexService indexService;

    public DocumentParserService() {
        this(new MarkdownConverterEngine(), new AiAgentIndexService());
    }

    public DocumentParserService(MarkdownConverterEngine markdownConverter) {
        this(markdownConverter, new AiAgentIndexService());
    }

    public DocumentParserService(MarkdownConverterEngine markdownConverter, AiAgentIndexService indexService) {
        this.markdownConverter = markdownConverter != null ? markdownConverter : new MarkdownConverterEngine();
        this.indexService = indexService != null ? indexService : new AiAgentIndexService();
    }

    public static class ParsedDocument {
        private final String fileName;
        private final Path path;
        private final boolean image;
        private final String content;
        private final String mimeType;
        private final long sizeBytes;
        private final Path markdownPath;
        private final MarkdownConverterEngine.ConversionResult conversionResult;

        public ParsedDocument(String fileName, Path path, boolean image, String content, String mimeType,
                long sizeBytes) {
            this(fileName, path, image, content, mimeType, sizeBytes, null, null);
        }

        public ParsedDocument(String fileName, Path path, boolean image, String content, String mimeType,
                long sizeBytes,
                Path markdownPath, MarkdownConverterEngine.ConversionResult conversionResult) {
            this.fileName = fileName;
            this.path = path;
            this.image = image;
            this.content = content;
            this.mimeType = mimeType;
            this.sizeBytes = sizeBytes;
            this.markdownPath = markdownPath;
            this.conversionResult = conversionResult;
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

        /**
         * Text content if document/text (in Markdown format); Base64 data URL if image.
         */
        public String getContent() {
            return content;
        }

        public String getMimeType() {
            return mimeType;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public Path getMarkdownPath() {
            return markdownPath;
        }

        public MarkdownConverterEngine.ConversionResult getConversionResult() {
            return conversionResult;
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
            return new ParsedDocument(fileName, file, true, dataUrl, mime, size, null, null);
        }

        // Convert document to high-fidelity Markdown
        MarkdownConverterEngine.ConversionResult convResult = markdownConverter.convert(file);
        String markdownText = convResult.getMarkdown();

        // Automatically persist to data/converted_docs/<sanitized_name>_<timestamp>.md
        Path savedMdPath = saveConvertedMarkdown(fileName, markdownText);

        // Index converted markdown and semantic chunks into H2 for AI Agent RAG
        // retrieval
        try {
            indexService.indexConvertedDocument(fileName, savedMdPath, markdownText, "text/markdown", size);
        } catch (Exception ignored) {
        }

        return new ParsedDocument(fileName, file, false, markdownText, "text/markdown", size, savedMdPath, convResult);
    }

    private Path saveConvertedMarkdown(String originalFileName, String markdownContent) {
        try {
            Path targetDir = StudioPaths.getConvertedDocsDir();
            Files.createDirectories(targetDir);

            String base = originalFileName.contains(".")
                    ? originalFileName.substring(0, originalFileName.lastIndexOf('.'))
                    : originalFileName;
            String safeBase = StringEscapeUtil.safeFileName(base, "doc");
            String timestamp = String.valueOf(System.currentTimeMillis());
            Path targetFile = targetDir.resolve(safeBase + "_" + timestamp + ".md");
            String contentHash = java.util.UUID.nameUUIDFromBytes(
                    (originalFileName + "|" + (markdownContent != null ? markdownContent : ""))
                            .getBytes(StandardCharsets.UTF_8))
                    .toString().substring(0, 8);
            Path targetFile = targetDir.resolve(safeBase + "_" + contentHash + ".md");

            Files.writeString(targetFile, markdownContent, StandardCharsets.UTF_8);
            return targetFile;
        } catch (Exception e) {
            // Non-fatal if disk write fails, in-memory markdown content is preserved
            return null;
        }
    }

    public String parseTextFile(Path file) throws IOException {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return Files.readString(file, StandardCharsets.ISO_8859_1);
        }
    }

    public String parseDocx(Path file) throws IOException {
        return markdownConverter.convertDocx(file);
    }

    public String parsePdf(Path file) throws IOException {
        return markdownConverter.convertPdf(file).markdown();
    }

    public static String getExtension(String fileName) {
        if (fileName == null)
            return "";
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
        if (ext == null)
            return false;
        String e = ext.toLowerCase(Locale.ROOT);
        return TEXT_EXTENSIONS.contains(e) || OFFICE_EXTENSIONS.contains(e) || IMAGE_EXTENSIONS.contains(e);
    }
}
