package com.studio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.studio.db.DatabaseManager;
import com.studio.model.AiGeneratedCase;
import com.studio.util.StringEscapeUtil;
import com.studio.util.StudioPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indexing & In-App Artifact Storage Engine for the AI Agent.
 * <ul>
 *   <li>Persists and indexes all converted {@code .md} documents into {@code data/converted_docs/} and H2 tables
 *       ({@code ai_documents_index}, {@code ai_document_chunks}) with heading-based semantic chunking.</li>
 *   <li>Automatically archives all exported files ({@code .xlsx}, {@code .md}, {@code .json}) inside {@code data/exports/}
 *       and indexes them in {@code ai_exports_index}.</li>
 *   <li>Persists AI-generated test artifacts (Markdown report, JSON bundle, individual {@code .java} test scripts,
 *       and {@code _assertions.json} files) into {@code data/ai_artifacts/<Group>/<Suite>/} so chat bubbles remain
 *       concise without dumping raw Markdown or code blocks.</li>
 *   <li>Provides fast BM25-style keyword & project RAG retrieval across indexed document chunks, existing test cases,
 *       and proven locators to enrich Multi-Agent test generation.</li>
 * </ul>
 */
public class AiAgentIndexService {

    private static final Pattern HEADING_SPLIT_PATTERN = Pattern.compile("(?m)^(#{1,3}\\s+.+)$");
    private static final Pattern WORD_TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}_]{2,}");
    private static final ObjectMapper SHARED_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final Set<String> STOP_WORDS = Set.of(
            "va", "cua", "cho", "tren", "voi", "cac", "nhung", "mot", "trong", "khi", "neu", "thi",
            "la", "duoc", "khong", "co", "theo", "tu", "den", "hay", "hoac", "de", "tai", "nay",
            "the", "and", "for", "with", "from", "that", "this", "are", "was", "were", "will"
    );

    private final DatabaseManager dbManager;
    private final AiTokenOptimizer tokenOptimizer;

    public AiAgentIndexService() {
        this(DatabaseManager.getInstance(), new AiTokenOptimizer());
    }

    public AiAgentIndexService(DatabaseManager dbManager, AiTokenOptimizer tokenOptimizer) {
        this.dbManager = dbManager != null ? dbManager : DatabaseManager.getInstance();
        this.tokenOptimizer = tokenOptimizer != null ? tokenOptimizer : new AiTokenOptimizer();
    }

    public record IndexedDocumentMeta(
            String id,
            String originalName,
            String mdPath,
            String mimeType,
            long sizeBytes,
            int chunkCount,
            long createdAt
    ) {}

    public record DocumentChunk(
            String id,
            String docId,
            int chunkIndex,
            String heading,
            String content,
            String keywords,
            int tokenEstimate
    ) {}

    public record IndexedExportMeta(
            String id,
            String exportType,
            String groupName,
            String suiteName,
            String fileName,
            String appStoredPath,
            String externalPath,
            int caseCount,
            long createdAt
    ) {}

    public record PersistedArtifactBundle(
            Path artifactDir,
            Path markdownFile,
            Path jsonFile,
            List<Path> scriptFiles,
            List<Path> assertionFiles
    ) {}

    /**
     * Indexes a converted Markdown document into {@code ai_documents_index} and {@code ai_document_chunks}.
     */
    public String indexConvertedDocument(String originalName,
                                         Path mdPath,
                                         String markdownContent,
                                         String mimeType,
                                         long sizeBytes) {
        if (markdownContent == null || markdownContent.isBlank()) {
            return null;
        }
        String safeOrig = originalName != null && !originalName.isBlank() ? originalName : "document.md";
        String pathStr = mdPath != null ? mdPath.toAbsolutePath().toString() : "";
        String docId = "doc-" + UUID.nameUUIDFromBytes((safeOrig + "|" + pathStr).getBytes(StandardCharsets.UTF_8))
                .toString().substring(0, 12);

        List<DocumentChunk> chunks = splitIntoSemanticChunks(docId, safeOrig, markdownContent);
        long now = System.currentTimeMillis();

        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "MERGE INTO ai_documents_index (id, original_name, md_path, mime_type, size_bytes, chunk_count, created_at) "
                            + "KEY(id) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, docId);
                ps.setString(2, safeOrig);
                ps.setString(3, pathStr);
                ps.setString(4, mimeType != null ? mimeType : "text/markdown");
                ps.setLong(5, sizeBytes);
                ps.setInt(6, chunks.size());
                ps.setLong(7, now);
                ps.executeUpdate();
            }

            try (PreparedStatement delPs = conn.prepareStatement("DELETE FROM ai_document_chunks WHERE doc_id = ?")) {
                delPs.setString(1, docId);
                delPs.executeUpdate();
            }

            if (!chunks.isEmpty()) {
                try (PreparedStatement chunkPs = conn.prepareStatement(
                        "INSERT INTO ai_document_chunks (id, doc_id, chunk_index, heading, content, keywords, token_estimate) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    for (DocumentChunk chunk : chunks) {
                        chunkPs.setString(1, chunk.id());
                        chunkPs.setString(2, chunk.docId());
                        chunkPs.setInt(3, chunk.chunkIndex());
                        chunkPs.setString(4, chunk.heading());
                        chunkPs.setString(5, chunk.content());
                        chunkPs.setString(6, chunk.keywords());
                        chunkPs.setInt(7, chunk.tokenEstimate());
                        chunkPs.addBatch();
                    }
                    chunkPs.executeBatch();
                }
            }
        } catch (Exception ignored) {
        }
        return docId;
    }

    /**
     * Splits Markdown text by headings (#, ##, ###) and caps chunk length to ~1200 chars for optimal RAG retrieval.
     */
    public List<DocumentChunk> splitIntoSemanticChunks(String docId, String docName, String markdown) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return chunks;
        }

        Matcher m = HEADING_SPLIT_PATTERN.matcher(markdown);
        List<Integer> starts = new ArrayList<>();
        List<String> headings = new ArrayList<>();

        while (m.find()) {
            starts.add(m.start());
            headings.add(m.group(1).replaceAll("^#+\\s*", "").trim());
        }

        if (starts.isEmpty()) {
            addSubChunks(chunks, docId, docName, markdown.trim());
            return chunks;
        }

        if (starts.get(0) > 0) {
            String preamble = markdown.substring(0, starts.get(0)).trim();
            if (!preamble.isEmpty()) {
                addSubChunks(chunks, docId, docName + " - Mở đầu", preamble);
            }
        }

        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : markdown.length();
            String sectionText = markdown.substring(start, end).trim();
            String heading = docName + " > " + headings.get(i);
            addSubChunks(chunks, docId, heading, sectionText);
        }

        return chunks;
    }

    private void addSubChunks(List<DocumentChunk> target, String docId, String heading, String content) {
        if (content == null || content.isBlank()) return;
        int maxLen = 1200;
        if (content.length() <= maxLen) {
            int idx = target.size();
            target.add(new DocumentChunk(
                    docId + "-c" + idx,
                    docId,
                    idx,
                    heading,
                    content,
                    extractKeywords(heading + " " + content),
                    tokenOptimizer.estimateTokens(content)
            ));
            return;
        }

        int pos = 0;
        int part = 1;
        while (pos < content.length()) {
            int end = Math.min(content.length(), pos + maxLen);
            if (end < content.length()) {
                int newline = content.lastIndexOf('\n', end);
                if (newline > pos + 300) {
                    end = newline;
                }
            }
            String slice = content.substring(pos, end).trim();
            if (!slice.isEmpty()) {
                int idx = target.size();
                String subHeading = heading + " (Phần " + part + ")";
                target.add(new DocumentChunk(
                        docId + "-c" + idx,
                        docId,
                        idx,
                        subHeading,
                        slice,
                        extractKeywords(subHeading + " " + slice),
                        tokenOptimizer.estimateTokens(slice)
                ));
                part++;
            }
            pos = end;
        }
    }

    /**
     * Archives an exported file into {@code StudioPaths.getExportsDir()} (if not already inside app storage)
     * and indexes the export in {@code ai_exports_index}.
     */
    public Path archiveAndIndexExport(Path exportedFile,
                                      String exportType,
                                      String groupName,
                                      String suiteName,
                                      int caseCount) {
        if (exportedFile == null || !Files.exists(exportedFile)) {
            return exportedFile;
        }
        Path appStoredPath = exportedFile.toAbsolutePath().normalize();
        try {
            Path exportsDir = StudioPaths.getExportsDir().toAbsolutePath().normalize();
            Path artifactsDir = StudioPaths.getAiArtifactsDir().toAbsolutePath().normalize();
            Files.createDirectories(exportsDir);

            if (!appStoredPath.startsWith(exportsDir) && !appStoredPath.startsWith(artifactsDir)) {
                String fileName = exportedFile.getFileName().toString();
                Path targetInApp = exportsDir.resolve(fileName);
                if (Files.exists(targetInApp)) {
                    String base = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
                    String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
                    targetInApp = exportsDir.resolve(base + "_" + (System.currentTimeMillis() % 100000) + ext);
                }
                Files.copy(exportedFile, targetInApp, StandardCopyOption.REPLACE_EXISTING);
                appStoredPath = targetInApp;
            }
        } catch (Exception ignored) {
        }

        String id = "exp-" + UUID.randomUUID().toString().substring(0, 12);
        long now = System.currentTimeMillis();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "MERGE INTO ai_exports_index (id, export_type, group_name, suite_name, file_name, app_stored_path, external_path, case_count, created_at) "
                             + "KEY(id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, exportType != null ? exportType : "EXPORT");
            ps.setString(3, groupName != null ? groupName : "");
            ps.setString(4, suiteName != null ? suiteName : "");
            ps.setString(5, exportedFile.getFileName().toString());
            ps.setString(6, appStoredPath.toString());
            ps.setString(7, exportedFile.toAbsolutePath().toString());
            ps.setInt(8, caseCount);
            ps.setLong(9, now);
            ps.executeUpdate();
        } catch (Exception ignored) {
        }

        return appStoredPath;
    }

    /**
     * Automatically saves AI-generated test artifacts (.md specification, .json bundle,
     * individual .java scripts, and _assertions.json files) into {@code data/ai_artifacts/<Group>/<Suite>/}
     * and indexes them in the database.
     */
    public PersistedArtifactBundle persistGeneratedArtifacts(AiTestGeneratorService.GenerationResult result,
                                                             String userPrompt) {
        if (result == null) {
            return null;
        }
        String group = (result.getSuggestedGroup() != null && !result.getSuggestedGroup().isBlank())
                ? StringEscapeUtil.safeFileName(result.getSuggestedGroup(), "ChucNang")
                : "ChucNang";
        String suite = (result.getSuggestedSuite() != null && !result.getSuggestedSuite().isBlank())
                ? StringEscapeUtil.safeFileName(result.getSuggestedSuite(), "BoKichBan")
                : "BoKichBan";

        Path artifactDir = StudioPaths.getAiArtifactsDir().resolve(group).resolve(suite);
        List<Path> scriptFiles = new ArrayList<>();
        List<Path> assertionFiles = new ArrayList<>();
        Path mdFile = artifactDir.resolve(suite + "_Spec.md");
        Path jsonFile = artifactDir.resolve(suite + "_Cases.json");

        try {
            Files.createDirectories(artifactDir);
            List<AiGeneratedCase> cases = result.getCases() != null ? result.getCases() : List.of();

            // 1. Save structured Markdown specification
            if (!cases.isEmpty()) {
                TestCaseExcelExportService.exportToMarkdown(cases, result.getSummary(), mdFile);
            } else if (result.getRawResponse() != null && !result.getRawResponse().isBlank()) {
                Files.writeString(mdFile, result.getRawResponse(), StandardCharsets.UTF_8);
            }

            // Index the generated Markdown specification for future RAG queries
            if (Files.exists(mdFile)) {
                String mdContent = Files.readString(mdFile, StandardCharsets.UTF_8);
                indexConvertedDocument(mdFile.getFileName().toString(), mdFile, mdContent, "text/markdown", mdContent.length());
            }

            // 2. Save structured JSON cases bundle
            if (!cases.isEmpty()) {
                TestCaseExcelExportService.exportToJson(cases, result.getSummary(), jsonFile);
            }

            // 3. Save individual .java test scripts and _assertions.json per Test Case
            if (!cases.isEmpty()) {
                Path scriptsDir = artifactDir.resolve("scripts");
                Path assertsDir = artifactDir.resolve("assertions");
                Files.createDirectories(scriptsDir);
                Files.createDirectories(assertsDir);

                for (AiGeneratedCase tc : cases) {
                    String safeCaseName = StringEscapeUtil.safeFileName(tc.getName(), "TC_Auto");
                    if (tc.getScriptCode() != null && !tc.getScriptCode().isBlank()) {
                        Path javaPath = scriptsDir.resolve(safeCaseName + ".java");
                        Files.writeString(javaPath, tc.getScriptCode(), StandardCharsets.UTF_8);
                        scriptFiles.add(javaPath);
                    }
                    if (tc.getAssertions() != null && !tc.getAssertions().isEmpty()) {
                        Path assertPath = assertsDir.resolve(safeCaseName + "_assertions.json");
                        Map<String, Object> assertPayload = new LinkedHashMap<>();
                        assertPayload.put("caseName", tc.getName());
                        assertPayload.put("priority", tc.getPriority());
                        assertPayload.put("assertions", tc.getAssertions());
                        assertPayload.put("testSteps", tc.getTestSteps());
                        assertPayload.put("testDataSets", tc.getTestDataSets());
                        SHARED_MAPPER.writeValue(assertPath.toFile(), assertPayload);
                        assertionFiles.add(assertPath);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return new PersistedArtifactBundle(artifactDir, mdFile, jsonFile, scriptFiles, assertionFiles);
    }

    /**
     * Retrieves relevant indexed context from H2 (converted document chunks, existing test cases in project,
     * and verified locators) within the given token budget to augment the AI Agent.
     */
    public String retrieveIndexedContextForAgent(String userPrompt,
                                                 String groupName,
                                                 String suiteName,
                                                 int maxTokenBudget) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return "";
        }
        int budget = maxTokenBudget > 0 ? maxTokenBudget : 1200;
        StringBuilder sb = new StringBuilder();
        int usedTokens = 0;

        // 1. Retrieve existing test cases & locators in the project matching keywords or target suite
        List<String> queryTerms = tokenizeKeywords(userPrompt);
        try (Connection conn = dbManager.getConnection()) {
            List<String> existingCasesInfo = queryRelevantProjectCases(conn, queryTerms, groupName, suiteName, 8);
            if (!existingCasesInfo.isEmpty()) {
                StringBuilder caseSection = new StringBuilder("### Chỉ mục Test Cases & Locators sẵn có trong Dự án (Indexed Project Knowledge):\n");
                for (String line : existingCasesInfo) {
                    caseSection.append("- ").append(line).append("\n");
                }
                int t = tokenOptimizer.estimateTokens(caseSection.toString());
                if (usedTokens + t <= budget) {
                    sb.append(caseSection).append("\n");
                    usedTokens += t;
                }
            }

            // 2. Retrieve relevant chunks from indexed .md documents in data/converted_docs/
            int topK = Math.max(4, Math.min(12, budget / 120));
            List<DocumentChunk> topChunks = searchTopDocumentChunks(conn, queryTerms, topK);
            if (!topChunks.isEmpty()) {
                StringBuilder docSection = new StringBuilder("### Tri thức Đặc tả liên quan từ Kho Tài liệu .MD đã lập chỉ mục:\n");
                for (DocumentChunk chunk : topChunks) {
                    String entry = "**[" + chunk.heading() + "]**\n" + chunk.content() + "\n\n";
                    int t = tokenOptimizer.estimateTokens(entry);
                    if (usedTokens + t > budget) {
                        break;
                    }
                    docSection.append(entry);
                    usedTokens += t;
                }
                if (docSection.length() > 80) {
                    sb.append(docSection);
                }
            }
        } catch (Exception e) {
            AiLoggingService.warn("AI-RAG", "Không truy hồi được ngữ cảnh index: " + e.getMessage());
        }

        return sb.toString().trim();
    }

    private List<String> queryRelevantProjectCases(Connection conn,
                                                   List<String> queryTerms,
                                                   String groupName,
                                                   String suiteName,
                                                   int limit) {
        List<String> results = new ArrayList<>();
        String sql = "SELECT group_name, suite_name, name, priority, description, start_url FROM test_cases ORDER BY updated_at DESC LIMIT 40";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next() && results.size() < limit) {
                String g = rs.getString("group_name");
                String s = rs.getString("suite_name");
                String name = rs.getString("name");
                String prio = rs.getString("priority");
                String desc = rs.getString("description");
                String url = rs.getString("start_url");

                boolean sameSuite = (suiteName != null && !suiteName.isBlank() && suiteName.equalsIgnoreCase(s))
                        || (groupName != null && !groupName.isBlank() && groupName.equalsIgnoreCase(g));
                int score = sameSuite ? 3 : 0;
                String combined = ((g != null ? g : "") + " " + (s != null ? s : "") + " "
                        + (name != null ? name : "") + " " + (desc != null ? desc : "")).toLowerCase(Locale.ROOT);
                for (String term : queryTerms) {
                    if (combined.contains(term)) {
                        score += 2;
                    }
                }
                if (score > 0) {
                    results.add(String.format("[%s/%s] `%s` (%s)%s: %s",
                            g, s, name, prio != null ? prio : "P1",
                            (url != null && !url.isBlank() ? " <" + url + ">" : ""),
                            (desc != null && desc.length() > 90 ? desc.substring(0, 87) + "..." : (desc != null ? desc : ""))));
                }
            }
        } catch (Exception e) {
            AiLoggingService.warn("AI-RAG", "Bỏ qua test case index không truy vấn được: " + e.getMessage());
        }
        return results;
    }

    private List<DocumentChunk> searchTopDocumentChunks(Connection conn, List<String> queryTerms, int topK) {
        List<ScoredChunk> scored = new ArrayList<>();
        List<String> terms = queryTerms.stream()
                .filter(t -> t != null && t.length() >= 3)
                .distinct()
                .limit(8)
                .toList();
        if (terms.isEmpty() || topK <= 0) {
            return List.of();
        }

        // Score in SQL and only return chunks that actually match a term. The previous version read an
        // arbitrary LIMIT 200 window and scored in Java, so a match beyond the first 200 rows was lost.
        StringBuilder sql = new StringBuilder(
                "SELECT id, doc_id, chunk_index, heading, content, keywords, token_estimate, (");
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) sql.append(" + ");
            sql.append("(CASE WHEN LOWER(heading) LIKE ? THEN 5 ELSE 0 END)");
            sql.append(" + (CASE WHEN LOWER(keywords) LIKE ? THEN 3 ELSE 0 END)");
            sql.append(" + (CASE WHEN LOWER(content) LIKE ? THEN 1 ELSE 0 END)");
        }
        sql.append(") AS relevance FROM ai_document_chunks WHERE ");
        for (int i = 0; i < terms.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("LOWER(heading) LIKE ? OR LOWER(keywords) LIKE ? OR LOWER(content) LIKE ?");
        }
        sql.append(" ORDER BY relevance DESC, chunk_index ASC LIMIT ?");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int param = 1;
            for (String term : terms) {
                String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
                ps.setString(param++, like);
                ps.setString(param++, like);
                ps.setString(param++, like);
            }
            for (String term : terms) {
                String like = "%" + term.toLowerCase(Locale.ROOT) + "%";
                ps.setString(param++, like);
                ps.setString(param++, like);
                ps.setString(param++, like);
            }
            ps.setInt(param, Math.max(1, Math.min(24, topK * 2)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next() && scored.size() < topK) {
                    scored.add(new ScoredChunk(new DocumentChunk(
                            rs.getString("id"),
                            rs.getString("doc_id"),
                            rs.getInt("chunk_index"),
                            rs.getString("heading"),
                            rs.getString("content"),
                            rs.getString("keywords"),
                            rs.getInt("token_estimate")
                    ), rs.getInt("relevance")));
                }
            }
        } catch (Exception e) {
            AiLoggingService.warn("AI-RAG", "Truy vấn chunk thất bại, dùng chấm điểm trong bộ nhớ: " + e.getMessage());
            return searchTopDocumentChunksFallback(conn, terms, topK);
        }
        return scored.stream().map(ScoredChunk::chunk).toList();
    }

    /** Fallback for drivers that reject the CASE/ORDER BY query; still term-filtered, never a blind scan. */
    private List<DocumentChunk> searchTopDocumentChunksFallback(Connection conn, List<String> terms, int topK) {
        List<ScoredChunk> scored = new ArrayList<>();
        String sql = "SELECT id, doc_id, chunk_index, heading, content, keywords, token_estimate FROM ai_document_chunks";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                DocumentChunk chunk = new DocumentChunk(
                        rs.getString("id"), rs.getString("doc_id"), rs.getInt("chunk_index"),
                        rs.getString("heading"), rs.getString("content"),
                        rs.getString("keywords"), rs.getInt("token_estimate"));
                String head = chunk.heading() != null ? chunk.heading().toLowerCase(Locale.ROOT) : "";
                String kw = chunk.keywords() != null ? chunk.keywords().toLowerCase(Locale.ROOT) : "";
                String content = chunk.content() != null ? chunk.content().toLowerCase(Locale.ROOT) : "";
                int score = 0;
                for (String term : terms) {
                    if (head.contains(term)) score += 5;
                    if (kw.contains(term)) score += 3;
                    if (content.contains(term)) score += 1;
                }
                if (score > 0) {
                    scored.add(new ScoredChunk(chunk, score));
                }
            }
        } catch (Exception e) {
            AiLoggingService.warn("AI-RAG", "Fallback truy vấn chunk cũng thất bại: " + e.getMessage());
            return List.of();
        }
        scored.sort(Comparator.comparingInt(ScoredChunk::score).reversed());
        return scored.stream().limit(topK).map(ScoredChunk::chunk).toList();
    }

    private record ScoredChunk(DocumentChunk chunk, int score) {}

    public List<IndexedDocumentMeta> listIndexedDocuments() {
        List<IndexedDocumentMeta> list = new ArrayList<>();
        String sql = "SELECT id, original_name, md_path, mime_type, size_bytes, chunk_count, created_at FROM ai_documents_index ORDER BY created_at DESC";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new IndexedDocumentMeta(
                        rs.getString("id"),
                        rs.getString("original_name"),
                        rs.getString("md_path"),
                        rs.getString("mime_type"),
                        rs.getLong("size_bytes"),
                        rs.getInt("chunk_count"),
                        rs.getLong("created_at")
                ));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public List<IndexedExportMeta> listIndexedExports() {
        List<IndexedExportMeta> list = new ArrayList<>();
        String sql = "SELECT id, export_type, group_name, suite_name, file_name, app_stored_path, external_path, case_count, created_at FROM ai_exports_index ORDER BY created_at DESC";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new IndexedExportMeta(
                        rs.getString("id"),
                        rs.getString("export_type"),
                        rs.getString("group_name"),
                        rs.getString("suite_name"),
                        rs.getString("file_name"),
                        rs.getString("app_stored_path"),
                        rs.getString("external_path"),
                        rs.getInt("case_count"),
                        rs.getLong("created_at")
                ));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static String extractKeywords(String text) {
        List<String> tokens = tokenizeKeywords(text);
        return String.join(" ", tokens);
    }

    public static List<String> tokenizeKeywords(String text) {
        if (text == null || text.isBlank()) return List.of();
        String norm = AiMultiAgentCoordinator.removeVietnameseAccents(text).toLowerCase(Locale.ROOT);
        Matcher m = WORD_TOKEN_PATTERN.matcher(norm);
        Set<String> unique = new LinkedHashSet<>();
        while (m.find()) {
            String w = m.group();
            if (w.length() >= 3 && !STOP_WORDS.contains(w)) {
                unique.add(w);
                if (unique.size() >= 40) break;
            }
        }
        return new ArrayList<>(unique);
    }
}

