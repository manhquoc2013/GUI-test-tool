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
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride) {
                // Simulate LLM returning verbose markdown + code blocks inside summary and a single case
                return new Completion("""
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
                        """, "stop");
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
        assertEquals("TEXT_EXACT", AiTestGeneratorService.normalizeAssertionType("TEXT_EQUALS"));
        assertEquals("ELEMENT_VISIBLE", AiTestGeneratorService.normalizeAssertionType("VISIBLE"));
        assertEquals("ELEMENT_HIDDEN", AiTestGeneratorService.normalizeAssertionType("HIDDEN"));
        assertEquals("ELEMENT_ENABLED", AiTestGeneratorService.normalizeAssertionType("ENABLED"));
        assertEquals("ELEMENT_DISABLED", AiTestGeneratorService.normalizeAssertionType("DISABLED"));
        assertEquals("INPUT_VALUE_EQUALS", AiTestGeneratorService.normalizeAssertionType("VALUE_EQUALS"));
        assertEquals("ATTRIBUTE_EQUALS", AiTestGeneratorService.normalizeAssertionType("HAS_ATTRIBUTE"));
        assertEquals("CSS_CONTAINS", AiTestGeneratorService.normalizeAssertionType("HAS_CLASS"));
        assertEquals("COOKIE_EXISTS", AiTestGeneratorService.normalizeAssertionType("HAS_COOKIE"));

        // Unknown hallucinated LLM assertion types must fallback to supported AssertCodeGenerator types
        String unknownUrlAssert = AiTestGeneratorService.normalizeAssertionType("ASSERT_URL_REDIRECTED");
        assertTrue(AssertCodeGenerator.isSupported(unknownUrlAssert));
        String unknownVisAssert = AiTestGeneratorService.normalizeAssertionType("VERIFY_TOAST_DISPLAYED");
        assertTrue(AssertCodeGenerator.isSupported(unknownVisAssert));

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

        // Verify step-to-script synthesis in AiMultiAgentCoordinator
        AiGeneratedCase stepCase = new AiGeneratedCase("TC01_StepToScript", "Test step translation", "https://example.com/form");
        stepCase.setTestSteps(List.of(
                new com.studio.model.TestStep(1, "FILL", "#email-input", "qa@example.com", "Nhập email", "Email đã nhập"),
                new com.studio.model.TestStep(2, "CLICK", "#submit-btn", "", "Bấm nút gửi", "Gửi biểu mẫu")
        ));
        Assertion vis = new Assertion();
        vis.setType("ELEMENT_VISIBLE");
        vis.setLocator("#success-banner");
        stepCase.setAssertions(List.of(vis));

        String synthesizedScript = service.getMultiAgentCoordinator().synthesizeScenarioScript(stepCase);
        assertTrue(synthesizedScript.contains("healLocator(\"#email-input\""));
        assertTrue(synthesizedScript.contains(".fill(\"qa@example.com\")"));
        assertTrue(synthesizedScript.contains("healLocator(\"#submit-btn\""));
        assertTrue(synthesizedScript.contains(".click()"));
        assertTrue(synthesizedScript.contains("#success-banner"));

        // Verify Markdown heading case extraction when no code fence is present
        String mdOnly = """
                # Tài liệu Test Case
                ## 1. TC01_DangKy_HopLe
                Mô tả kịch bản 1
                ## 2. TC02_DangKy_TrungEmail
                Mô tả kịch bản 2
                """;
        List<AiGeneratedCase> mdCases = AiTestGeneratorService.extractCasesFromMarkdown(mdOnly);
        assertEquals(2, mdCases.size());
        assertEquals("TC01_DangKy_HopLe", mdCases.get(0).getName());
        assertEquals("TC02_DangKy_TrungEmail", mdCases.get(1).getName());
    }

    @Test
    public void testSystemReviewFixes_RenameCopyCase_ReportSync_StepGroups_Cleanup() throws Exception {
        DatabaseManager dbManager = DatabaseManager.getInstance();
        AiAgentIndexService indexService = new AiAgentIndexService(dbManager, new AiTokenOptimizer());
        ProjectStore projectStore = new ProjectStore();
        com.studio.db.ProjectDbService projectDb = new com.studio.db.ProjectDbService(dbManager);

        // 1. Create Group, Suite, and Case with special/Vietnamese characters, then rename and copy
        Path groupDir = projectStore.createGroup(StudioPaths.getProjectDir(), "NhomThanhToan", "Mô tả nhóm").getName() != null
                ? StudioPaths.getProjectDir().resolve("NhomThanhToan") : null;
        assertNotNull(groupDir);
        projectStore.createSuite(groupDir, "SuiteGioHang");
        Path suiteDir = groupDir.resolve("SuiteGioHang");

        TestCase created = projectStore.createCase(suiteDir, "Đăng nhập / Giỏ hàng", "Mô tả case");
        assertNotNull(created.getScriptFile());
        assertTrue(Files.exists(suiteDir.resolve(created.getScriptFile())));

        // Rename case with special characters -> must not throw NoSuchFileException and must update H2
        projectStore.renameCase(suiteDir, created.getId(), "Thanh toán: Thẻ tín dụng");
        List<TestCase> foundRenamed = projectDb.searchCases("Thẻ tín dụng", null);
        assertEquals(1, foundRenamed.size());

        // Copy case with special characters -> must safely copy .java and index in H2
        TestCase copied = projectStore.copyCase(suiteDir, created.getId(), "Thanh toán / Ví điện tử");
        assertNotNull(copied);
        assertTrue(Files.exists(suiteDir.resolve(copied.getScriptFile())));
        assertEquals(2, projectDb.getCasesForSuite("NhomThanhToan", "SuiteGioHang").size());

        // 2. Verify LocalDataIndexService.syncReportsDirectory reads report.json written by ReportStore
        com.studio.store.ReportStore reportStore = new com.studio.store.ReportStore();
        com.studio.model.RunReport r = new com.studio.model.RunReport(
                "run-sync-check", "SUITE", suiteDir.toString(), "SuiteGioHang", "CHROMIUM", true, false, false);
        r.setStartedAt("2026-11-10T00:00:00");
        r.setTotal(2);
        r.setPassed(2);
        ProjectStore.writeJsonAtomic(StudioPaths.getReportsDir().resolve("run-sync-check").resolve("report.json"), r);

        com.studio.db.LocalDataIndexService localIdx = new com.studio.db.LocalDataIndexService(dbManager, new com.fasterxml.jackson.databind.ObjectMapper());
        localIdx.syncReportsDirectory(StudioPaths.getReportsDir());
        com.studio.db.ReportDbService reportDb = new com.studio.db.ReportDbService(dbManager);
        assertNotNull(reportDb.getReport("run-sync-check"), "syncReportsDirectory must index report.json files");

        // 3. Verify TestRunnerService.expandStepGroups compiles steps into executable StudioRuntime calls
        com.studio.store.StepGroupStore sgStore = new com.studio.store.StepGroupStore(tempHome.resolve("step-groups"));
        com.studio.model.StepGroup sg = new com.studio.model.StepGroup("sg-login", "Reusable Login", "Common login");
        sg.setSteps(List.of(
                new com.studio.model.TestStep(1, "NAVIGATE", "https://example.com/login", "", "Mở trang login", ""),
                new com.studio.model.TestStep(2, "FILL", "#user", "admin", "Nhập user", ""),
                new com.studio.model.TestStep(3, "CLICK", "#btn-login", "", "Bấm đăng nhập", "")
        ));
        sgStore.save(sg);

        String scriptWithInclude = "public class Test {\n    public static void main(String[] args) {\n        // @include(\"sg-login\")\n    }\n}";
        String expanded = TestRunnerService.expandStepGroups(scriptWithInclude, sgStore);
        assertTrue(expanded.contains("StudioRuntime.page().navigate(\"https://example.com/login\")"));
        assertTrue(expanded.contains("StudioRuntime.healLocator(\"#user\").first().fill(\"admin\")"));
        assertTrue(expanded.contains("StudioRuntime.healLocator(\"#btn-login\").first().click()"));

        // 4. Verify DataCleanupService cleans .ai_chats sessions and H2 document index tables
        com.studio.store.AiChatHistoryStore chatStore = new com.studio.store.AiChatHistoryStore();
        com.studio.model.AiChatSession chatSession = chatStore.createNewSession(suiteDir, "Phiên test dọn dẹp");
        chatSession.addMessage(new com.studio.model.AiChatMessage("user", "Xin chào"));
        chatStore.saveSession(suiteDir, chatSession);

        DocumentParserService parser = new DocumentParserService(new MarkdownConverterEngine(), indexService);
        Path specFile = tempHome.resolve("cleanup_spec.txt");
        Files.writeString(specFile, "# Tài liệu kiểm thử\nNội dung kiểm thử dọn dẹp", StandardCharsets.UTF_8);
        DocumentParserService.ParsedDocument d1 = parser.parse(specFile);
        DocumentParserService.ParsedDocument d2 = parser.parse(specFile);
        assertEquals(d1.getMarkdownPath(), d2.getMarkdownPath(), "Repeated parse of identical file must reuse deterministic .md path");
        assertFalse(indexService.listIndexedDocuments().isEmpty());

        DataCleanupService cleanup = new DataCleanupService(dbManager, reportStore);
        DataCleanupService.CleanupResult cleanRes = cleanup.executeCleanup(
                new DataCleanupService.CleanupOptions(false, 0, true, true));
        assertTrue(cleanRes.getDeletedChatCount() >= 1, "Multi-session .ai_chats files must be cleaned");
        assertTrue(indexService.listIndexedDocuments().isEmpty(), "Cleaned converted_docs must also purge ai_documents_index");
    }

    @Test
    public void testRound2SystemReviewFixes_GranularIndexSync_GroupCascade_ReportExports() throws Exception {
        DatabaseManager dbManager = DatabaseManager.getInstance();
        AiAgentIndexService indexService = new AiAgentIndexService(dbManager, new AiTokenOptimizer());
        ProjectStore projectStore = new ProjectStore();
        com.studio.db.ProjectDbService projectDb = new com.studio.db.ProjectDbService(dbManager);

        // 1. Create Group -> Suite -> Case and test granular ProjectStore updates syncing to H2
        projectStore.createGroup(StudioPaths.getProjectDir(), "GroupOld", "Mô tả ban đầu");
        Path groupDir = StudioPaths.getProjectDir().resolve("GroupOld");
        projectStore.createSuite(groupDir, "SuiteOrder");
        Path suiteDir = groupDir.resolve("SuiteOrder");
        TestCase tc = projectStore.createCase(suiteDir, "Kiểm thử Đặt hàng #1", "Mô tả cũ");

        projectStore.updateCaseMeta(suiteDir, tc.getId(), "Mô tả mới cập nhật", "Ready", "https://shop.local/order", "CHROMIUM", true);
        projectStore.updateCasePriority(suiteDir, tc.getId(), "P0");
        Assertion urlAssert = new Assertion();
        urlAssert.setType("URL_CONTAINS");
        urlAssert.setValue("/order");
        urlAssert.setStatus("Active");
        projectStore.addAssertion(suiteDir, tc.getId(), urlAssert);

        List<TestCase> suiteCases = projectDb.getCasesForSuite("GroupOld", "SuiteOrder");
        assertEquals(1, suiteCases.size());
        assertEquals("Mô tả mới cập nhật", suiteCases.get(0).getDescription());
        assertEquals("Ready", suiteCases.get(0).getStatus());
        assertEquals("P0", suiteCases.get(0).getPriority());
        assertEquals(1, suiteCases.get(0).getAssertions().size());

        // 2. Test collectCasesForScope("CASE", ...) with Vietnamese / special characters
        List<com.studio.model.CaseResult> singleCaseResults = projectStore.collectCasesForScope(
                "CASE", suiteDir.resolve(tc.getScriptFile()));
        assertEquals(1, singleCaseResults.size());
        assertEquals(tc.getId(), singleCaseResults.get(0).getTestCaseId());

        // 3. Rename Group -> must re-index child suites & cases under GroupNew and purge GroupOld
        projectStore.renameGroup(groupDir, "GroupNew");
        assertTrue(projectDb.getCasesForSuite("GroupOld", "SuiteOrder").isEmpty(),
                "Old group cases must be removed from H2 after renameGroup");
        List<TestCase> renamedGroupCases = projectDb.getCasesForSuite("GroupNew", "SuiteOrder");
        assertEquals(1, renamedGroupCases.size(), "Child suite cases must be re-indexed under GroupNew");

        // 4. Delete Group -> must cascade-delete suites, cases, and assertions in H2
        Path newGroupDir = StudioPaths.getProjectDir().resolve("GroupNew");
        projectStore.deleteGroup(newGroupDir);
        assertTrue(projectDb.getCasesForSuite("GroupNew", "SuiteOrder").isEmpty(),
                "Deleting group must cascade-delete child suites and cases in H2");

        // 5. Export RunReport via ExcelReportService and HtmlReportService -> must archive to data/exports and index in ai_exports_index
        com.studio.model.RunReport report = new com.studio.model.RunReport(
                "run-export-test", "SUITE", suiteDir.toString(), "SuiteOrder", "CHROMIUM", true, false, false);
        report.setTotal(3);
        report.setPassed(3);
        Path externalExcel = tempHome.resolve("external_report.xlsx");
        Path externalHtml = tempHome.resolve("external_report.html");

        ExcelReportService.export(report, externalExcel);
        HtmlReportService.export(report, externalHtml);

        List<AiAgentIndexService.IndexedExportMeta> exports = indexService.listIndexedExports();
        assertTrue(exports.stream().anyMatch(e -> "EXCEL_REPORT".equals(e.exportType()) && Files.exists(Path.of(e.appStoredPath()))),
                "ExcelReportService.export must archive into data/exports and index in ai_exports_index");
        assertTrue(exports.stream().anyMatch(e -> "HTML_REPORT".equals(e.exportType()) && Files.exists(Path.of(e.appStoredPath()))),
                "HtmlReportService.export must archive into data/exports and index in ai_exports_index");

        // 6. Verify deleteSuite cascade-deletes assertions in H2
        projectStore.createGroup(StudioPaths.getProjectDir(), "GroupAssertCascade", "Desc");
        Path cascadeGroupDir = StudioPaths.getProjectDir().resolve("GroupAssertCascade");
        projectStore.createSuite(cascadeGroupDir, "SuiteCascade");
        Path cascadeSuiteDir = cascadeGroupDir.resolve("SuiteCascade");
        TestCase cascadeCase = projectStore.createCase(cascadeSuiteDir, "CaseCascade", "Desc");
        Assertion cascadeAssert = new Assertion();
        cascadeAssert.setType("URL_CONTAINS");
        cascadeAssert.setValue("/cascade");
        cascadeAssert.setStatus("Active");
        projectStore.addAssertion(cascadeSuiteDir, cascadeCase.getId(), cascadeAssert);
        assertEquals(1, projectDb.getAssertionsForCase(cascadeCase.getId()).size());
        projectStore.deleteSuite(cascadeSuiteDir);
        assertTrue(projectDb.getAssertionsForCase(cascadeCase.getId()).isEmpty(),
                "deleteSuite must cascade-delete assertions in test_assertions");

        // 7. Verify Undo message resolution for both User and Assistant bubbles & snapshot dir preservation
        com.studio.model.AiChatMessage userBubble = new com.studio.model.AiChatMessage("user", "Sinh kịch bản");
        com.studio.model.AiChatMessage assistantBubble = new com.studio.model.AiChatMessage("assistant", "Tóm tắt");
        assistantBubble.setAppliedToSuite(true);
        assistantBubble.setSuiteSnapshotJson("{\"suiteJson\":\"{}\"}");
        assistantBubble.setSuiteSnapshotDir(cascadeSuiteDir.toString());
        List<com.studio.model.AiChatMessage> pair = List.of(userBubble, assistantBubble);

        com.studio.model.AiChatMessage resolvedFromUser = com.studio.controller.AiAssistantPageController.resolveAppliedSnapshotMessage(pair, userBubble);
        assertSame(assistantBubble, resolvedFromUser, "Undoing user message must resolve paired applied assistant message");
        assertEquals(cascadeSuiteDir, com.studio.controller.AiAssistantPageController.resolveRollbackSuiteDir(resolvedFromUser, null),
                "Rollback must prefer suiteSnapshotDir stored on the message over current UI selection");
    }

    @Test
    public void testRound4SystemReviewFixes_SingleCaseAiPersistence_FullAssertionCatalog_NestedGroupRename() throws Exception {
        DatabaseManager dbManager = DatabaseManager.getInstance();
        ProjectStore projectStore = new ProjectStore();
        com.studio.db.ProjectDbService projectDb = new com.studio.db.ProjectDbService(dbManager);

        // 1. Verify AiCasePromptDialog.applyGeneratedCaseToStore persists steps, data, priority, assertions, script, and startUrl
        projectStore.createGroup(StudioPaths.getProjectDir(), "ParentGroup", "Parent");
        Path parentGroupDir = StudioPaths.getProjectDir().resolve("ParentGroup");
        projectStore.createSuite(parentGroupDir, "SuiteSingleAi");
        Path suiteDir = parentGroupDir.resolve("SuiteSingleAi");
        TestCase activeCase = projectStore.createCase(suiteDir, "TC01_SingleAi", "Initial description");

        AiGeneratedCase gen = new AiGeneratedCase("TC01_SingleAi", "Updated", "https://app.local/checkout");
        gen.setPriority("P0");
        gen.setScriptCode("package com.test;\npublic class TC01 { public static void main(String[] a) {} }");
        gen.setTestSteps(List.of(
                new com.studio.model.TestStep(1, "FILL", "#coupon", "VIP2026", "Nhập mã giảm giá", "Áp dụng thành công")
        ));
        gen.setTestDataSets(List.of(
                new com.studio.model.TestDataItem("Valid Coupon", java.util.Map.of("coupon", "VIP2026"), "SUCCESS")
        ));
        Assertion attrAssert = new Assertion();
        attrAssert.setType("ATTRIBUTE_EQUALS");
        attrAssert.setLocator("#coupon");
        attrAssert.setAttribute("data-applied");
        attrAssert.setExpectedValue("true");
        gen.setAssertions(List.of(attrAssert));

        com.studio.controller.AiCasePromptDialog.applyGeneratedCaseToStore(projectStore, suiteDir, activeCase, gen);

        assertEquals("P0", activeCase.getPriority());
        assertEquals("https://app.local/checkout", activeCase.getStartUrl());
        assertEquals(1, activeCase.getTestSteps().size());
        assertEquals(1, activeCase.getTestDataSets().size());

        TestSuite reloaded = projectStore.loadAndMigrateSuite(suiteDir);
        TestCase persisted = reloaded.getCases().get(0);
        assertEquals("P0", persisted.getPriority());
        assertEquals(1, persisted.getTestSteps().size());
        assertEquals(1, persisted.getTestDataSets().size());

        // Verify H2 test_assertions stores effectiveValue ("data-applied=true") when value is empty
        List<Assertion> dbAsserts = projectDb.getAssertionsForCase(activeCase.getId());
        assertEquals(1, dbAsserts.size());
        assertEquals("data-applied=true", dbAsserts.get(0).getEffectiveValue());
        assertEquals("data-applied", dbAsserts.get(0).resolveAttribute());
        assertEquals("true", dbAsserts.get(0).resolveExpectedValue());

        // 2. Verify every assertion type in AssertTypeDisplay.ALL_TYPES is supported and generates valid Java code
        for (String type : com.studio.util.AssertTypeDisplay.ALL_TYPES) {
            assertTrue(AssertCodeGenerator.isSupported(type), "Type in ALL_TYPES must be supported: " + type);
            String label = com.studio.util.AssertTypeDisplay.label(type);
            assertNotNull(label);
            assertFalse(label.isBlank(), "Label must not be blank for: " + type);

            Assertion sample = new Assertion();
            sample.setType(type);
            sample.setLocator("#el");
            sample.setValue("key=val");
            sample.setStatus("ACTIVE");
            String block = AssertCodeGenerator.generateAssertBlock(List.of(sample));
            assertFalse(block.contains("Unsupported assert type:"), "Must generate code for: " + type);
            assertFalse(block.contains("Unsupported or unknown assertion type"), "Must not mark supported type as unknown: " + type);
        }

        // 3. Verify nested group rename & delete synchronize recursively in H2
        projectStore.createGroup(parentGroupDir, "ChildGroup", "Nested group");
        Path childGroupDir = parentGroupDir.resolve("ChildGroup");
        projectStore.createSuite(childGroupDir, "NestedSuite");
        Path nestedSuiteDir = childGroupDir.resolve("NestedSuite");
        projectStore.createCase(nestedSuiteDir, "NestedCase", "Desc");

        projectStore.renameGroup(parentGroupDir, "ParentGroupRenamed");
        Path renamedParentDir = StudioPaths.getProjectDir().resolve("ParentGroupRenamed");
        assertTrue(Files.exists(renamedParentDir.resolve("ChildGroup").resolve("NestedSuite")));
        assertEquals(1, projectDb.getCasesForSuite("ParentGroupRenamed", "SuiteSingleAi").size());
        assertFalse(projectDb.getCasesForSuite("ParentGroupRenamed/ChildGroup", "NestedSuite").isEmpty(),
                "Nested sub-group suite cases must be recursively re-indexed under ParentGroupRenamed/ChildGroup");
    }
}
