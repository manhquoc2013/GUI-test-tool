package com.studio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.AiConfig;
import com.studio.model.AiGeneratedCase;
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
        assertTrue(fixed.contains("// Studio manages page/browser lifecycle"));
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
}

