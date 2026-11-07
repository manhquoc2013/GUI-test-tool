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
        assertEquals("PAGE_TITLE_CONTAINS", c.getAssertions().get(1).getType());
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
}
