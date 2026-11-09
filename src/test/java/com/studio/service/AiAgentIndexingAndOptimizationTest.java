package com.studio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.db.DatabaseManager;
import com.studio.model.AiConfig;
import com.studio.model.AiGeneratedCase;
import com.studio.model.AiSkill;
import com.studio.model.Assertion;
import com.studio.model.TestCase;
import com.studio.model.TestSuite;
import com.studio.store.ProjectStore;
import com.studio.util.StudioPaths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class AiAgentIndexingAndOptimizationTest {

    @TempDir
    Path tempHome;

    @BeforeEach
    void setUp() {
        DatabaseManager.getInstance().shutdown();
        StudioPaths.setPathsForTesting(tempHome, null);
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.getInstance().shutdown();
        StudioPaths.resetForTesting();
    }

    @Test
    public void testConvertedMarkdownPersistenceAndRagIndexing() throws Exception {
        // Create a sample requirement document outside the app directory
        Path externalDoc = tempHome.resolve("external_srs_thanh_toan.txt");
        String docContent = """
                # Đặc tả Chức năng Thanh toán Đơn hàng
                ## 1. Quy tắc nghiệp vụ
                - Đơn hàng tối thiểu để áp dụng mã giảm giá VOUCHER50 là 200.000 VNĐ.
                - Hỗ trợ thanh toán qua cổng VNPay, MoMo và thẻ tín dụng quốc tế.
                ## 2. Xử lý ngoại lệ
                - Khi thẻ hết hạn hoặc số dư không đủ, hiển thị cảnh báo tại #payment-error-toast.
                """;
        Files.writeString(externalDoc, docContent, StandardCharsets.UTF_8);

        DocumentParserService parser = new DocumentParserService();
        DocumentParserService.ParsedDocument parsed = parser.parse(externalDoc);

        // 1. Verify converted .md file is saved inside StudioPaths.getConvertedDocsDir()
        assertNotNull(parsed.getMarkdownPath(), "Converted Markdown path must not be null");
        assertTrue(Files.exists(parsed.getMarkdownPath()), "Converted .md file must exist on disk");
        assertTrue(parsed.getMarkdownPath().startsWith(StudioPaths.getConvertedDocsDir()),
                "Converted .md file must be stored in StudioPaths.getConvertedDocsDir()");

        // 2. Verify document and chunks are indexed in H2 via AiAgentIndexService
        AiAgentIndexService indexService = new AiAgentIndexService();
        List<AiAgentIndexService.IndexedDocumentMeta> docs = indexService.listIndexedDocuments();
        assertFalse(docs.isEmpty(), "Converted document must be indexed in ai_documents_index");
        assertEquals("external_srs_thanh_toan.txt", docs.get(0).originalName());
        assertTrue(docs.get(0).chunkCount() >= 2, "Document should be split into semantic heading chunks");

        // 3. Verify RAG retrieval finds relevant chunks by keyword query
        String ragContext = indexService.retrieveIndexedContextForAgent(
                "Kiểm thử mã giảm giá VOUCHER50 và lỗi thanh toán #payment-error-toast",
                null,
                null,
                1000
        );
        assertFalse(ragContext.isBlank(), "RAG context must return matching chunks");
        assertTrue(ragContext.contains("VOUCHER50"), "RAG context should include matched business rule VOUCHER50");
    }

    @Test
    public void testExportedFilesArchivedAndIndexedInApplication() throws Exception {
        AiGeneratedCase c1 = new AiGeneratedCase("TC01_DangNhap_ThanhCong", "Đăng nhập hợp lệ", "https://app.example.com/login");
        c1.setPriority("P0");

        // Export to an external user folder outside StudioPaths.getExportsDir()
        Path externalExportDir = tempHome.resolve("user_desktop_exports");
        Files.createDirectories(externalExportDir);

        Path xlsxOut = externalExportDir.resolve("BoTestCase_DangNhap.xlsx");
        Path mdOut = externalExportDir.resolve("BoTestCase_DangNhap.md");
        Path jsonOut = externalExportDir.resolve("BoTestCase_DangNhap.json");

        TestCaseExcelExportService.exportAiGeneratedCases(List.of(c1), "Kiểm thử đăng nhập", xlsxOut);
        TestCaseExcelExportService.exportToMarkdown(List.of(c1), "Kiểm thử đăng nhập", mdOut);
        TestCaseExcelExportService.exportToJson(List.of(c1), "Kiểm thử đăng nhập", jsonOut);

        // Verify all 3 exported files are archived inside StudioPaths.getExportsDir() and indexed in H2
        AiAgentIndexService indexService = new AiAgentIndexService();
        List<AiAgentIndexService.IndexedExportMeta> exports = indexService.listIndexedExports();
        assertEquals(3, exports.size(), "All 3 exports (.xlsx, .md, .json) must be indexed in ai_exports_index");

        for (AiAgentIndexService.IndexedExportMeta meta : exports) {
            Path archivedPath = Path.of(meta.appStoredPath());
            assertTrue(Files.exists(archivedPath), "Archived export file must exist in app: " + archivedPath);
            assertTrue(archivedPath.startsWith(StudioPaths.getExportsDir()),
                    "Archived export file must be stored inside StudioPaths.getExportsDir(): " + archivedPath);
        }
    }

    @Test
    public void testConciseChatSummaryWithoutRawCodeAndArtifactPersistence() throws Exception {
        OpenAiClient mockClient = new OpenAiClient() {
            @Override
            public String chatCompletion(AiConfig config, List<ChatMessage> messages) {
                // Simulate LLM returning verbose markdown + code blocks inside summary and a single case
                return """
                        ```json
                        {
                          "summary": "# Báo cáo chi tiết\\nĐây là bản phân tích chức năng Đặt phòng khách sạn.\\n```java\\npublic class RawDumpInChat {}\\n```\\n| Cột 1 | Cột 2 |\\n|---|---|\\n| A | B |",
                          "suggestedGroup": "QuanLyDatPhong",
                          "suggestedSuite": "KiemThu_DatPhongKhachSan",
                          "cases": [
                            {
                              "name": "TC01_DatPhong_ThanhCong",
                              "description": "Đặt phòng tiêu chuẩn thành công",
                              "priority": "P0",
                              "startUrl": "https://hotel.example.com/booking",
                              "preconditions": ["Khách hàng đã đăng nhập", "Phòng còn trống"],
                              "scriptCode": "page.navigate(\\"https://hotel.example.com/booking\\");\\ntry { Thread.sleep(1500); } catch (Exception e) {}\\npage.click(\\"#book-now\\");",
                              "assertions": [
                                {"type": "TEXT_CONTAINS", "locator": "#booking-status", "value": "Đặt phòng thành công"}
                              ]
                            }
                          ]
                        }
                        ```
                        """;
            }
        };

        AiTestGeneratorService generator = new AiTestGeneratorService(mockClient, new ObjectMapper());
        AiConfig config = new AiConfig();
        config.setApiKey("sk-test-key");

        AiTestGeneratorService.GenerationResult result = generator.generateTestCases(
                config,
                "Tạo kịch bản kiểm thử chức năng đặt phòng khách sạn tại https://hotel.example.com/booking",
                List.of(),
                "",
                null,
                List.of(),
                null,
                AiSkill.PLAYWRIGHT_E2E,
                null,
                true
        );

        assertNotNull(result);
        // 1. Chat summary must NOT contain raw code fences (```) or raw markdown tables
        String chatSummary = result.getSummary();
        assertFalse(chatSummary.contains("```"), "Chat summary must NOT dump raw code blocks into chat: " + chatSummary);
        assertFalse(chatSummary.contains("public class RawDumpInChat"), "Chat summary must strip raw code dumps");
        assertTrue(chatSummary.contains("QuanLyDatPhong"), "Chat summary should show target Group");
        assertTrue(chatSummary.contains("KiemThu_DatPhongKhachSan"), "Chat summary should show target Suite");

        // 2. Multi-Scenario Coverage Expansion: 1 case -> 3 cases (Happy Path, Negative Validation, Boundary BVA)
        assertEquals(3, result.getCases().size(), "Single case must be expanded to 3 scenarios (Happy Path, Negative, Boundary)");
        assertEquals("TC01_DatPhong_ThanhCong", result.getCases().get(0).getName());
        assertTrue(result.getCases().get(1).getName().startsWith("TC02_"));
        assertTrue(result.getCases().get(2).getName().startsWith("TC03_"));

        // 3. Inline Thread.sleep inside try/catch must be stripped, and assertion alias TEXT_CONTAINS normalized
        AiGeneratedCase tc1 = result.getCases().get(0);
        assertFalse(tc1.getScriptCode().contains("Thread.sleep("), "Inline Thread.sleep inside try/catch must be stripped");
        assertTrue(tc1.getAssertions().size() >= 2, "Case must have multi-point assertions (>= 2)");
        assertEquals("ELEMENT_HAS_TEXT", tc1.getAssertions().get(0).getType(), "TEXT_CONTAINS must normalize to ELEMENT_HAS_TEXT");

        // 4. Verify artifacts (.md report, .json bundle, scripts/*.java, assertions/*_assertions.json) saved in data/ai_artifacts/
        Path artifactDir = result.getArtifactDir();
        assertNotNull(artifactDir, "Artifact directory must be set on GenerationResult");
        assertTrue(Files.exists(artifactDir), "Artifact directory must exist on disk");
        assertTrue(artifactDir.startsWith(StudioPaths.getAiArtifactsDir()), "Artifact directory must be inside StudioPaths.getAiArtifactsDir()");

        Path scriptsDir = artifactDir.resolve("scripts");
        Path assertionsDir = artifactDir.resolve("assertions");
        assertTrue(Files.exists(scriptsDir), "scripts/ sub-folder must exist in artifact directory");
        assertTrue(Files.exists(assertionsDir), "assertions/ sub-folder must exist in artifact directory");

        try (Stream<Path> scriptFiles = Files.list(scriptsDir)) {
            assertEquals(3, scriptFiles.filter(p -> p.toString().endsWith(".java")).count(),
                    "All 3 test scripts (.java) must be saved in scripts/ directory");
        }
        try (Stream<Path> assertFiles = Files.list(assertionsDir)) {
            assertEquals(3, assertFiles.filter(p -> p.toString().endsWith("_assertions.json")).count(),
                    "All 3 assertion files (*_assertions.json) must be saved in assertions/ directory");
        }

        // 5. Verify fast batch applyCasesToSuite preserves preconditions and writes all cases atomically
        Path suiteDir = StudioPaths.getProjectDir().resolve("QuanLyDatPhong").resolve("KiemThu_DatPhongKhachSan");
        ProjectStore store = new ProjectStore();
        List<TestCase> applied = generator.applyCasesToSuite(suiteDir, result.getCases(), store);
        assertEquals(3, applied.size());
        assertTrue(applied.get(0).getDescription().contains("Điều kiện tiên quyết: Khách hàng đã đăng nhập"),
                "Preconditions must be preserved in TestCase description");

        TestSuite reloadedSuite = store.loadAndMigrateSuite(suiteDir);
        assertEquals(3, reloadedSuite.getCases().size());
    }

    @Test
    public void testAssertionAliasNormalizationAndExpectedValueFallback() {
        assertEquals("ELEMENT_HAS_TEXT", AiTestGeneratorService.normalizeAssertionType("TEXT_CONTAINS"));
        assertEquals("ELEMENT_HAS_EXACT_TEXT", AiTestGeneratorService.normalizeAssertionType("TEXT_EQUALS"));
        assertEquals("ELEMENT_VISIBLE", AiTestGeneratorService.normalizeAssertionType("VISIBLE"));
        assertEquals("ELEMENT_HIDDEN", AiTestGeneratorService.normalizeAssertionType("HIDDEN"));
        assertEquals("ELEMENT_ENABLED", AiTestGeneratorService.normalizeAssertionType("ENABLED"));
        assertEquals("ELEMENT_DISABLED", AiTestGeneratorService.normalizeAssertionType("DISABLED"));
        assertEquals("INPUT_VALUE_EQUALS", AiTestGeneratorService.normalizeAssertionType("VALUE_EQUALS"));
        assertEquals("ATTRIBUTE_EQUALS", AiTestGeneratorService.normalizeAssertionType("HAS_ATTRIBUTE"));
        assertEquals("RESPONSE_STATUS_EQUALS", AiTestGeneratorService.normalizeAssertionType("STATUS_CODE_EQUALS"));

        // Verify expectedValue fallback from value when attribute is present
        String json = """
                {
                  "summary": "Kiểm tra attribute fallback",
                  "cases": [
                    {
                      "name": "TC01_Attr",
                      "assertions": [
                        {
                          "type": "HAS_ATTRIBUTE",
                          "locator": "#btn-submit",
                          "attribute": "aria-disabled",
                          "value": "true"
                        }
                      ]
                    }
                  ]
                }
                """;
        AiTestGeneratorService service = new AiTestGeneratorService();
        AiTestGeneratorService.GenerationResult res = service.parseResponse(json);
        Assertion a = res.getCases().get(0).getAssertions().get(0);
        assertEquals("ATTRIBUTE_EQUALS", a.getType());
        assertEquals("aria-disabled", a.getAttribute());
        assertEquals("true", a.getExpectedValue(), "expectedValue should fallback to value when attribute is specified");
        assertTrue(AssertCodeGenerator.isSupported(a.getType()));
    }
}
