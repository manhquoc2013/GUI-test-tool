package com.studio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.AiConfig;
import com.studio.model.AiGeneratedCase;
import com.studio.model.Assertion;
import com.studio.model.TestCase;
import com.studio.model.TestSuite;
import com.studio.store.ProjectStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiTestGeneratorServiceTest {

    @TempDir
    Path tempDir;

    private AiTestGeneratorService service;
    private ProjectStore projectStore;

    @BeforeEach
    void setUp() {
        service = new AiTestGeneratorService(new OpenAiClient(), new ObjectMapper());
        projectStore = new ProjectStore();
    }

    @Test
    void testExtractJsonBlockFromMarkdown() {
        String raw = "Here is the response:\n```json\n{\"summary\": \"Test summary\", \"cases\": []}\n```\nHope it helps!";
        String extracted = AiTestGeneratorService.extractJsonBlock(raw);
        assertEquals("{\"summary\": \"Test summary\", \"cases\": []}", extracted);
    }

    @Test
    void testExtractJsonBlockRaw() {
        String raw = "{\"summary\": \"Direct JSON\", \"cases\": []}";
        String extracted = AiTestGeneratorService.extractJsonBlock(raw);
        assertEquals(raw, extracted);
    }

    @Test
    void testParseResponse() {
        String sampleJson = """
            {
              "summary": "Tạo test case đăng nhập",
              "cases": [
                {
                  "name": "TC01_Login_Success",
                  "description": "Đăng nhập với email hợp lệ",
                  "startUrl": "https://example.com/login",
                  "scriptCode": "package com.test; public class TC01_Login_Success {}",
                  "assertions": [
                    {
                      "type": "ELEMENT_VISIBLE",
                      "locator": "#welcome-msg",
                      "status": "ACTIVE"
                    },
                    {
                      "type": "PAGE_TITLE_CONTAINS",
                      "value": "Dashboard",
                      "status": "ACTIVE"
                    }
                  ]
                }
              ]
            }
            """;

        AiTestGeneratorService.GenerationResult result = service.parseResponse(sampleJson);
        assertNotNull(result);
        assertEquals("Tạo test case đăng nhập", result.getSummary());
        assertEquals(1, result.getCases().size());

        AiGeneratedCase c = result.getCases().get(0);
        assertEquals("TC01_Login_Success", c.getName());
        assertEquals("https://example.com/login", c.getStartUrl());
        assertEquals("package com.test; public class TC01_Login_Success {}", c.getScriptCode());
        assertEquals(2, c.getAssertions().size());
        assertEquals("ELEMENT_VISIBLE", c.getAssertions().get(0).getType());
        assertEquals("#welcome-msg", c.getAssertions().get(0).getLocator());
        assertEquals("TITLE_CONTAINS", c.getAssertions().get(1).getType());
        assertEquals("Dashboard", c.getAssertions().get(1).getValue());
    }

    @Test
    void testApplyCasesToSuite() throws IOException {
        Path suiteDir = tempDir.resolve("AuthSuite");
        Files.createDirectories(suiteDir);
        TestSuite suite = new TestSuite("AuthSuite", "Authentication suite");
        suite.setStatus("ACTIVE");
        new ObjectMapper().writeValue(suiteDir.resolve("suite.json").toFile(), suite);

        AiGeneratedCase genCase = new AiGeneratedCase("TC01_Login_Valid", "Valid login scenario", "https://example.com");
        genCase.setScriptCode("package com.test;\npublic class TC01_Login_Valid {\n    public static void main(String[] args) {}\n}");

        List<TestCase> applied = service.applyCasesToSuite(suiteDir, List.of(genCase), projectStore);
        assertEquals(1, applied.size());
        assertEquals("TC01_Login_Valid", applied.get(0).getName());

        // Verify script exists on disk
        String savedScript = projectStore.readCaseScript(suiteDir, applied.get(0).getId());
        assertTrue(savedScript.contains("public class TC01_Login_Valid"));
    }

    @Test
    void testMissingApiKeyValidation() {
        AiConfig config = new AiConfig();
        config.setApiKey("");
        OpenAiClient client = new OpenAiClient();
        OpenAiClient.ConnectionTestResult res = client.testConnection(config);
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("Chưa thiết lập OpenAI API Key"));
    }

    @Test
    void testExtractErrorMessageStandardAndArray() {
        // Standard OpenAI error
        String openAiErr = "{\"error\": {\"message\": \"Incorrect API key provided\"}}";
        assertEquals("Incorrect API key provided", OpenAiClient.extractErrorMessage(openAiErr));

        // Google Gemini array error format
        String geminiErr = """
                [
                  {
                    "error": {
                      "code": 404,
                      "message": "This model models/gemini-2.5-flash is no longer available to new users. Please update your code to use models/gemini-3.8-flash for the latest features ..."
                    }
                  }
                ]
                """;
        assertEquals("This model models/gemini-2.5-flash is no longer available to new users. Please update your code to use models/gemini-3.8-flash for the latest features ...",
                OpenAiClient.extractErrorMessage(geminiErr));
    }

    @Test
    void testResolveProviderName() {
        assertEquals("Gemini", OpenAiClient.resolveProviderName("https://generativelanguage.googleapis.com/v1beta/openai/"));
        assertEquals("DeepSeek", OpenAiClient.resolveProviderName("https://api.deepseek.com"));
        assertEquals("OpenRouter", OpenAiClient.resolveProviderName("https://openrouter.ai/api/v1"));
        assertEquals("OpenAI", OpenAiClient.resolveProviderName("https://api.openai.com/v1"));
        assertEquals("Local/Ollama", OpenAiClient.resolveProviderName("http://localhost:11434/v1"));
    }

    @Test
    void testParseResponseWithStepsAndDataSets() {
        String json = """
            {
              "summary": "Kịch bản đăng nhập đầy đủ",
              "cases": [
                {
                  "name": "TC_Login_Advanced",
                  "description": "Kiểm thử đăng nhập và phân quyền",
                  "startUrl": "https://example.com/login",
                  "preconditions": [
                    "Đã có tài khoản hoạt động"
                  ],
                  "testSteps": [
                    {
                      "stepNumber": 1,
                      "action": "NAVIGATE",
                      "target": "https://example.com/login",
                      "description": "Điều hướng đến trang đăng nhập",
                      "expectedResult": "Trang mở thành công"
                    },
                    {
                      "stepNumber": 2,
                      "action": "FILL",
                      "target": "#username",
                      "testData": "admin",
                      "description": "Nhập username",
                      "expectedResult": "Giá trị đã điền"
                    }
                  ],
                  "testDataSets": [
                    {
                      "scenario": "Admin Login",
                      "parameters": {
                        "role": "ADMIN",
                        "user": "admin"
                      },
                      "expectedOutcome": "SUCCESS"
                    }
                  ],
                  "scriptCode": "page.locator(\\"#username\\").fill(\\"admin\\");",
                  "assertions": [
                    {
                      "type": "ELEMENT_VISIBLE",
                      "locator": "#dashboard",
                      "status": "ACTIVE"
                    }
                  ]
                }
              ]
            }
            """;

        AiTestGeneratorService.GenerationResult result = service.parseResponse(json);
        assertNotNull(result);
        assertEquals(1, result.getCases().size());
        AiGeneratedCase c = result.getCases().get(0);
        assertEquals("TC_Login_Advanced", c.getName());
        assertEquals(1, c.getPreconditions().size());
        assertEquals(2, c.getTestSteps().size());
        assertEquals("NAVIGATE", c.getTestSteps().get(0).getAction());
        assertEquals(1, c.getTestDataSets().size());
        assertEquals("Admin Login", c.getTestDataSets().get(0).getScenario());
        assertEquals("ADMIN", c.getTestDataSets().get(0).getParameters().get("role"));

        // Script should be auto-wrapped with package and class wrapper
        assertTrue(c.getScriptCode().contains("package com.test;"));
        assertTrue(c.getScriptCode().contains("public class TC_Login_Advanced"));
        assertTrue(c.getScriptCode().contains("StudioRuntime.page()"));
    }

    @Test
    void testValidateAndFixScriptStripIllegalLifecycle() {
        String illegalScript = """
            package com.test;
            import com.microsoft.playwright.*;
            public class TestIllegal {
                public static void main(String[] args) {
                    Playwright pw = Playwright.create();
                    Browser br = pw.chromium().launch();
                    Page page = br.newPage();
                    page.navigate("https://example.com");
                    page.close();
                    br.close();
                }
            }
            """;

        String fixed = service.validateAndFixScript(illegalScript, "TestIllegal", "https://example.com");
        assertFalse(fixed.contains("Playwright.create()"));
        assertFalse(fixed.contains(".launch()"));
        assertTrue(fixed.contains("Studio manages page/browser lifecycle"));
        assertFalse(fixed.contains("page.close();"), "Playwright page.close() must be removed");
        assertFalse(fixed.contains("br.close();"), "Playwright browser.close() must be removed");
    }

    @Test
    void testValidateAndFixScriptKeepsUnrelatedCloseCalls() {
        String script = """
            package com.test;
            import com.microsoft.playwright.*;
            import java.io.StringWriter;
            public class TestUnrelatedClose {
                public static void main(String[] args) {
                    Page page = com.studio.runtime.StudioRuntime.page();
                    StringWriter out = new StringWriter();
                    page.navigate("https://example.com");
                    out.close();
                }
            }
            """;

        String fixed = service.validateAndFixScript(script, "TestUnrelatedClose", "https://example.com");
        assertTrue(fixed.contains("out.close();"), "A non-Playwright close() call must be preserved");
    }

    @Test
    void testValidateAndFixScriptKeepsControlFlowWhenRemovingSleep() {
        String script = """
            package com.test;
            import com.microsoft.playwright.*;
            public class TestSleep {
                public static void main(String[] args) {
                    Page page = com.studio.runtime.StudioRuntime.page();
                    if (page.url().isEmpty()) Thread.sleep(1000);
                    page.navigate("https://example.com");
                }
            }
            """;

        String fixed = service.validateAndFixScript(script, "TestSleep", "https://example.com");
        assertFalse(fixed.contains("Thread.sleep"), "Hard sleep phải bị loại bỏ");
        assertTrue(fixed.contains("if (page.url().isEmpty()) page.waitForLoadState"),
                "Câu lệnh thay thế phải giữ nguyên vị trí thân if, không nuốt dòng kế tiếp");
        assertTrue(fixed.contains("page.navigate(\"https://example.com\");"),
                "Dòng sau khối if không được trở thành thân if");
    }

    @Test
    void testValidateAndFixScriptLeavesUnrelatedCloseCalls() {
        String script = """
            package com.test;
            import com.microsoft.playwright.*;
            public class TestClose {
                public static void main(String[] args) {
                    Page page = com.studio.runtime.StudioRuntime.page();
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    out.close();
                    page.close();
                }
            }
            """;

        String fixed = service.validateAndFixScript(script, "TestClose", "https://example.com");
        assertTrue(fixed.contains("out.close();"), "close() ngoài Playwright phải được giữ nguyên");
        assertFalse(fixed.contains("page.close();"), "Chỉ close() của Playwright mới bị loại bỏ");
    }

    @Test
    void testApplyCasesToSuiteWithStepsAndDataSets() throws IOException {
        Path suiteDir = tempDir.resolve("AdvancedSuite");
        Files.createDirectories(suiteDir);
        TestSuite suite = new TestSuite("AdvancedSuite", "Suite with steps and data");
        suite.setStatus("ACTIVE");
        new ObjectMapper().writeValue(suiteDir.resolve("suite.json").toFile(), suite);

        AiGeneratedCase genCase = new AiGeneratedCase("TC_WithSteps", "Test with steps", "https://example.com");
        genCase.setTestSteps(List.of(
                new com.studio.model.TestStep(1, "CLICK", "#btn", "", "Click button", "Button clicked")
        ));
        genCase.setTestDataSets(List.of(
                new com.studio.model.TestDataItem("Edge 1", Map.of("key", "val"), "OK")
        ));

        List<TestCase> applied = service.applyCasesToSuite(suiteDir, List.of(genCase), projectStore);
        assertEquals(1, applied.size());
        TestCase tc = applied.get(0);
        assertEquals(1, tc.getTestSteps().size());
        assertEquals(1, tc.getTestDataSets().size());

        // Reload suite from disk and verify persistence
        TestSuite reloaded = projectStore.loadAndMigrateSuite(suiteDir);
        TestCase reloadedTc = reloaded.getCases().get(0);
        assertEquals(1, reloadedTc.getTestSteps().size());
        assertEquals("CLICK", reloadedTc.getTestSteps().get(0).getAction());
        assertEquals(1, reloadedTc.getTestDataSets().size());
        assertEquals("Edge 1", reloadedTc.getTestDataSets().get(0).getScenario());
    }

    @Test
    void testApplyCasesToSuiteWhenSuiteJsonDoesNotExist(@TempDir Path tempDir) throws Exception {
        Path groupDir = tempDir.resolve("TestGroup");
        Path suiteDir = groupDir.resolve("SuiteTest");
        Files.createDirectories(suiteDir); // folder exists but NO suite.json

        AiGeneratedCase genCase = new AiGeneratedCase("LoginTest", "Test login flow", "https://app.test");
        genCase.setScriptCode("package com.test;\npublic class LoginTest { public void run() {} }");
        Assertion a = new Assertion();
        a.setType("TITLE_CONTAINS");
        a.setValue("Dashboard");
        genCase.setAssertions(List.of(a));

        // Must not throw FileNotFoundException: suite.json
        List<TestCase> applied = service.applyCasesToSuite(suiteDir, List.of(genCase), projectStore);
        assertEquals(1, applied.size());
        assertTrue(Files.exists(suiteDir.resolve("suite.json")), "suite.json should be automatically created");
        assertTrue(Files.exists(groupDir.resolve("group.json")), "group.json should be automatically created");

        TestSuite reloaded = projectStore.loadAndMigrateSuite(suiteDir);
        assertEquals(1, reloaded.getCases().size());
        assertEquals("LoginTest", reloaded.getCases().get(0).getName());
        assertEquals("https://app.test", reloaded.getCases().get(0).getStartUrl());
        assertEquals(1, reloaded.getCases().get(0).getAssertions().size());
    }

    @Test
    void testExtractJsonBlock_WithoutClosingFence() {
        String truncatedMarkdown = "```json\n{\n  \"summary\": \"Test without closing fence\",\n  \"cases\": []";
        String extracted = AiTestGeneratorService.extractJsonBlock(truncatedMarkdown);
        assertTrue(extracted.startsWith("{"));
        assertTrue(extracted.contains("\"summary\": \"Test without closing fence\""));
    }

    @Test
    void testAutoRepairTruncatedJson_UnclosedArrayAndString() {
        // Truncated at line 192 column 23 (similar to user's real error screenshot)
        String truncatedJson = """
            {
              "summary": "Tạo kịch bản quản lý thu chi",
              "cases": [
                {
                  "name": "TC01_ThuChi",
                  "description": "Đăng nhập và kiểm tra",
                  "startUrl": "https://example.com/thu-chi",
                  "scriptCode": "package com.test; public class TC01_ThuChi {}",
                  "assertions": [
                    {
                      "type": "ELEMENT_VISIBLE",
                      "locator": "input#desc
            """;

        String repaired = AiTestGeneratorService.autoRepairTruncatedJson(truncatedJson);
        assertNotNull(repaired);
        // Must be valid JSON parseable by Jackson
        assertDoesNotThrow(() -> new ObjectMapper().readTree(repaired));

        // When parsed by service, it should recover the case and assertions!
        AiTestGeneratorService.GenerationResult res = service.parseResponse(truncatedJson);
        assertNotNull(res);
        assertFalse(res.getCases().isEmpty(), "Should recover truncated case");
        AiGeneratedCase c = res.getCases().get(0);
        assertEquals("TC01_ThuChi", c.getName());
        assertEquals("https://example.com/thu-chi", c.getStartUrl());
        assertTrue(c.getScriptCode().contains("public class TC01_ThuChi"));
        assertEquals(1, c.getAssertions().size());
        assertEquals("ELEMENT_VISIBLE", c.getAssertions().get(0).getType());
    }

    @Test
    void testAutoRepairTruncatedJson_DanglingCommaAndTrailingKey() {
        String truncatedJson = """
            {
              "summary": "Test dangling comma",
              "cases": [
                {
                  "name": "TC02_Dangling",
                  "scriptCode": "package com.test; public class TC02_Dangling {}",
                  "assertions": [
                    {
                      "type": "PAGE_HAS_TEXT",
                      "value": "Thành công",
                    }
                  ],
            """;

        String repaired = AiTestGeneratorService.autoRepairTruncatedJson(truncatedJson);
        assertNotNull(repaired);
        assertDoesNotThrow(() -> new ObjectMapper().readTree(repaired));

        AiTestGeneratorService.GenerationResult res = service.parseResponse(truncatedJson);
        assertNotNull(res);
        assertFalse(res.getCases().isEmpty());
        assertEquals("TC02_Dangling", res.getCases().get(0).getName());
    }

    @Test
    void testExtractDirectlyFromRawResponse_TolerantFallback() {
        // Completely broken JSON syntax where parser and repair fail, but scriptCode is present
        String brokenJson = """
            MALFORMED PREAMBLE
            "summary": "Phục hồi khẩn cấp",
            "name": "TC_Emergency",
            "startUrl": "https://app.test/login",
            "scriptCode": "package com.test;\\npublic class TC_Emergency {\\n    public static void main(String[] args) {\\n        Page page = com.studio.runtime.StudioRuntime.page();\\n        page.navigate(\\\"https://app.test/login\\\");\\n    }\\n}",
            "assertions": [
              { "type": "TITLE_CONTAINS", "value": "Dashboard" }
            RANDOM GARBAGE TRUNCATION
            """;

        AiTestGeneratorService.GenerationResult res = service.extractDirectlyFromRawResponse(brokenJson);
        assertNotNull(res);
        assertFalse(res.getCases().isEmpty());
        AiGeneratedCase c = res.getCases().get(0);
        assertEquals("TC_Emergency", c.getName());
        assertEquals("https://app.test/login", c.getStartUrl());
        assertTrue(c.getScriptCode().contains("public class TC_Emergency"));
        assertEquals(1, c.getAssertions().size());
        assertEquals("TITLE_CONTAINS", c.getAssertions().get(0).getType());
        assertEquals("Dashboard", c.getAssertions().get(0).getValue());
    }

    @Test
    void testContainsTargetUrl() {
        // Prompt contains URL
        assertTrue(AiTestGeneratorService.containsTargetUrl("Kiểm thử trang https://example.com/checkout", null, null, null));
        assertTrue(AiTestGeneratorService.containsTargetUrl("Mở http://localhost:8080 và đăng nhập", null, null, null));

        // Prompt without URL
        assertFalse(AiTestGeneratorService.containsTargetUrl("Tạo kịch bản kiểm thử tính năng tính lãi suất tiết kiệm", null, null, null));

        // Attachment contains URL
        DocumentParserService.ParsedDocument docWithUrl = new DocumentParserService.ParsedDocument(
                "spec.md", null, false, "Tài liệu yêu cầu kiểm thử hệ thống https://mysite.vn", "text/markdown", 100);
        assertTrue(AiTestGeneratorService.containsTargetUrl("Yêu cầu theo tài liệu", List.of(docWithUrl), null, null));

        DocumentParserService.ParsedDocument docNoUrl = new DocumentParserService.ParsedDocument(
                "spec.md", null, false, "Quy tắc nghiệp vụ: Tiền gửi tối thiểu 1.000.000 VNĐ", "text/markdown", 100);
        assertFalse(AiTestGeneratorService.containsTargetUrl("Yêu cầu theo tài liệu", List.of(docNoUrl), null, null));

        // DOM context contains live elements
        assertTrue(AiTestGeneratorService.containsTargetUrl("Kiểm thử theo DOM", null, "[{\"tag\":\"button\"}]", null));
        assertFalse(AiTestGeneratorService.containsTargetUrl("Kiểm thử theo DOM", null, "[]", null));
    }

    @Test
    void testValidateAndFixScript_NoUrlReturnsBlankScript() {
        // A blank script is a missing deliverable: it must not be replaced with a boilerplate class that
        // navigates nowhere yet passes compilation, because that makes an empty case look runnable.
        assertEquals("", service.validateAndFixScript("", "TestCase_01", ""));
        assertEquals("", service.validateAndFixScript(null, "TestCase_02", "about:blank"));
        assertEquals("", service.validateAndFixScript("", "TestCase_03", "https://example.com"));
    }

    @Test
    void subAgentDocAllowanceScalesWithTheContextWindow() {
        // Floor: a tiny window must not starve a document below the smallest useful slice.
        assertEquals(2500, AiTestGeneratorService.resolveSubAgentDocAllowance(2000, 1));
        // Cap: a huge window must not push one document past the useful extraction limit.
        assertEquals(16000, AiTestGeneratorService.resolveSubAgentDocAllowance(32768, 1));
        // An unknown window falls back to the default instead of returning a zero budget.
        assertEquals(16000, AiTestGeneratorService.resolveSubAgentDocAllowance(0, 1));
        // Several documents share the same budget rather than each claiming the full window.
        assertEquals(14933, AiTestGeneratorService.resolveSubAgentDocAllowance(128000, 6));
        assertEquals(2500, AiTestGeneratorService.resolveSubAgentDocAllowance(4000, 4));
    }

    @Test
    void subAgentGroundingKeepsTheLaterSectionsOfALargeSrs() {
        StringBuilder content = new StringBuilder("# Tài liệu SRS\n\n");
        for (int i = 1; i <= 23; i++) {
            content.append("## 3.").append(i).append(" Màn hình số ").append(i).append('\n');
            content.append("MARKER-").append(i).append(' ')
                    .append("Yêu cầu chức năng chi tiết cho màn hình. ".repeat(20))
                    .append("\n\n");
        }
        DocumentParserService.ParsedDocument srs = new DocumentParserService.ParsedDocument(
                "srs.docx", null, false, content.toString(), "docx", content.length());

        String grounding = service.buildSubAgentGrounding("tạo test case cho tài liệu srs", List.of(srs), null,
                "Suite X", 32768);

        assertTrue(grounding.contains("MARKER-23"),
                "mục cuối của SRS phải tới được tác nhân, không chỉ mục lục");
        assertTrue(grounding.length() > 9000,
                "ngân sách cố định 9.000 ký tự cũ làm mất các mục sau, got: " + grounding.length());
    }
}

