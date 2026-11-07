package com.studio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.AiConfig;
import com.studio.model.AiGeneratedCase;
import com.studio.model.Assertion;
import com.studio.model.TestCase;
import com.studio.model.TestSuite;
import com.studio.service.DocumentParserService.ParsedDocument;
import com.studio.store.ProjectStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service to generate and update Playwright test cases, automation scripts, and assertions
 * using OpenAI models from user prompts and documents.
 */
public class AiTestGeneratorService {

    private final OpenAiClient openAiClient;
    private final ObjectMapper mapper;

    public AiTestGeneratorService() {
        this(new OpenAiClient(), new ObjectMapper());
    }

    public AiTestGeneratorService(OpenAiClient openAiClient, ObjectMapper mapper) {
        this.openAiClient = openAiClient;
        this.mapper = mapper;
    }

    public static class GenerationResult {
        private final String summary;
        private final List<AiGeneratedCase> cases;
        private final String rawResponse;

        public GenerationResult(String summary, List<AiGeneratedCase> cases, String rawResponse) {
            this.summary = summary != null ? summary : "";
            this.cases = cases != null ? cases : new ArrayList<>();
            this.rawResponse = rawResponse != null ? rawResponse : "";
        }

        public String getSummary() {
            return summary;
        }

        public List<AiGeneratedCase> getCases() {
            return cases;
        }

        public String getRawResponse() {
            return rawResponse;
        }
    }

    public static final String SYSTEM_PROMPT = """
        You are an expert QA Automation Engineer specialized in Playwright Java for "Playwright Automation Studio".
        Your task is to analyze user requirements, specification documents (PRD, SRS, user stories, matrices), or UI screenshots, and generate ready-to-run Playwright Java test cases with structured assertions.

        ### Execution Environment Rules:
        1. In this studio, the browser/page lifecycle is managed automatically. Test scripts must follow this Java template:
        ```java
        package com.test;

        import com.microsoft.playwright.*;
        import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

        public class <CaseClassName> {
            public static void main(String[] args) {
                Page page = com.studio.runtime.StudioRuntime.page();
                page.navigate("<StartUrl>");
                // User interactions here (page.locator(...).click(), fill, selectOption, etc.)
            }
        }
        ```
        Do NOT call playwright.chromium().launch(), browser.newContext(), or page.close(). The Studio manages these.

        2. Each test case can have assertions. The supported Assertion types include:
           - PAGE_CONTAINS_TEXT (value: expected text)
           - PAGE_NOT_CONTAINS_TEXT (value: text)
           - PAGE_TITLE_EQUALS (value: title)
           - PAGE_TITLE_CONTAINS (value: text)
           - PAGE_URL_EQUALS (value: url)
           - PAGE_URL_CONTAINS (value: substring)
           - ELEMENT_VISIBLE (locator: css or text locator)
           - ELEMENT_NOT_VISIBLE (locator)
           - ELEMENT_EXISTS (locator)
           - ELEMENT_ENABLED (locator)
           - ELEMENT_DISABLED (locator)
           - ELEMENT_HAS_TEXT (locator, value)
           - ELEMENT_TEXT_CONTAINS (locator, value)
           - INPUT_VALUE_EQUALS (locator, value)
           - CHECKBOX_CHECKED (locator)
           - ELEMENT_HAS_ATTRIBUTE (locator, attribute, expectedValue)
           - ELEMENT_COUNT (locator, value: count as number)
           - CUSTOM_JS_TRUE (value: javascript expression returning boolean)

        3. Response format:
        You MUST respond ONLY with a JSON object (inside ```json ... ``` or raw JSON) matching this exact schema:
        {
          "summary": "Brief summary of generated tests in Vietnamese",
          "cases": [
            {
              "name": "Case_Identifier_Name",
              "description": "Clear description of test scenario",
              "startUrl": "https://example.com/target",
              "scriptCode": "Java code as described above",
              "assertions": [
                {
                  "type": "ELEMENT_VISIBLE",
                  "locator": "selector",
                  "value": "",
                  "status": "ACTIVE"
                }
              ]
            }
          ]
        }
        """;

    /**
     * Generate one or more test cases from user prompt and attached documents.
     */
    public GenerationResult generateTestCases(AiConfig config, String userPrompt,
                                             List<ParsedDocument> attachments,
                                             String contextInfo) throws IOException, InterruptedException {
        List<OpenAiClient.ChatMessage> messages = new ArrayList<>();
        messages.add(new OpenAiClient.ChatMessage("system", SYSTEM_PROMPT));

        StringBuilder userContent = new StringBuilder();
        if (contextInfo != null && !contextInfo.isBlank()) {
            userContent.append("### Ngữ cảnh Suite hiện tại:\n").append(contextInfo).append("\n\n");
        }

        userContent.append("### Yêu cầu người dùng:\n").append(userPrompt).append("\n\n");

        List<String> imageUrls = new ArrayList<>();
        if (attachments != null && !attachments.isEmpty()) {
            userContent.append("### Tài liệu đính kèm:\n");
            for (ParsedDocument doc : attachments) {
                if (doc.isImage()) {
                    imageUrls.add(doc.getContent());
                    userContent.append("- Hình ảnh UI: ").append(doc.getFileName()).append(" (đã đính kèm ảnh)\n");
                } else {
                    userContent.append("- Tên file: ").append(doc.getFileName()).append("\n");
                    userContent.append("```\n").append(doc.getContent()).append("\n```\n\n");
                }
            }
        }

        messages.add(new OpenAiClient.ChatMessage("user", userContent.toString(), imageUrls));

        String reply = openAiClient.chatCompletion(config, messages);
        return parseResponse(reply);
    }

    /**
     * Generate or refine script and assertions for a single case.
     */
    public GenerationResult generateOrUpdateSingleCase(AiConfig config, String userPrompt,
                                                       List<ParsedDocument> attachments,
                                                       TestCase currentCase, String currentScript) throws IOException, InterruptedException {
        StringBuilder context = new StringBuilder();
        if (currentCase != null) {
            context.append("Case Name: ").append(currentCase.getName()).append("\n");
            context.append("Current URL: ").append(currentCase.getStartUrl()).append("\n");
            context.append("Current Description: ").append(currentCase.getDescription()).append("\n");
            context.append("Current Assertions count: ").append(currentCase.getAssertions().size()).append("\n");
            if (currentScript != null && !currentScript.isBlank()) {
                context.append("Existing Java Script:\n```java\n").append(currentScript).append("\n```\n");
            }
        }
        return generateTestCases(config, userPrompt, attachments, context.toString());
    }

    /**
     * Parse OpenAI JSON output into GenerationResult.
     */
    public GenerationResult parseResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return new GenerationResult("Không nhận được phản hồi từ AI.", List.of(), "");
        }

        String jsonText = extractJsonBlock(rawResponse);
        try {
            JsonNode root = mapper.readTree(jsonText);
            String summary = root.path("summary").asText("Đã tạo test case thành công");
            List<AiGeneratedCase> cases = new ArrayList<>();

            JsonNode casesArray = root.path("cases");
            if (casesArray.isArray()) {
                for (JsonNode cNode : casesArray) {
                    AiGeneratedCase ac = new AiGeneratedCase();
                    ac.setName(sanitizeCaseName(cNode.path("name").asText("GeneratedCase")));
                    ac.setDescription(cNode.path("description").asText(""));
                    ac.setStartUrl(cNode.path("startUrl").asText(""));
                    ac.setScriptCode(cNode.path("scriptCode").asText(""));

                    JsonNode assertsNode = cNode.path("assertions");
                    if (assertsNode.isArray()) {
                        List<Assertion> asserts = new ArrayList<>();
                        for (JsonNode aNode : assertsNode) {
                            Assertion a = new Assertion();
                            a.setType(aNode.path("type").asText("PAGE_CONTAINS_TEXT"));
                            a.setValue(aNode.path("value").asText(""));
                            a.setLocator(aNode.path("locator").asText(""));
                            a.setAttribute(aNode.path("attribute").asText(""));
                            a.setExpectedValue(aNode.path("expectedValue").asText(""));
                            a.setStatus(aNode.path("status").asText("ACTIVE"));
                            a.setExact(aNode.path("exact").asBoolean(false));
                            asserts.add(a);
                        }
                        ac.setAssertions(asserts);
                    }
                    cases.add(ac);
                }
            }

            return new GenerationResult(summary, cases, rawResponse);
        } catch (Exception e) {
            return new GenerationResult("Lỗi khi phân tích JSON phản hồi từ AI: " + e.getMessage(), List.of(), rawResponse);
        }
    }

    /**
     * Apply generated cases into a TestSuite in ProjectStore.
     */
    public List<TestCase> applyCasesToSuite(Path suiteDir, List<AiGeneratedCase> generatedCases,
                                           ProjectStore projectStore) throws IOException {
        List<TestCase> applied = new ArrayList<>();
        if (suiteDir == null || generatedCases == null || projectStore == null) {
            return applied;
        }

        TestSuite suite = projectStore.loadAndMigrateSuite(suiteDir);

        for (AiGeneratedCase genCase : generatedCases) {
            TestCase targetCase = null;

            // 1. If targetCaseId is set, find by id
            if (genCase.getTargetCaseId() != null) {
                targetCase = suite.getCases().stream()
                        .filter(c -> c.getId().equals(genCase.getTargetCaseId()))
                        .findFirst()
                        .orElse(null);
            }

            // 2. Otherwise find by name
            if (targetCase == null) {
                targetCase = suite.getCases().stream()
                        .filter(c -> c.getName().equalsIgnoreCase(genCase.getName()))
                        .findFirst()
                        .orElse(null);
            }

            if (targetCase == null) {
                // Create new case
                String caseName = genCase.getName();
                targetCase = projectStore.createCase(suiteDir, caseName, genCase.getDescription(),
                        "Ready", genCase.getStartUrl(), suite.getBrowser(), suite.getHeadless());
            } else {
                // Update meta
                projectStore.updateCaseMeta(suiteDir, targetCase.getId(), genCase.getDescription(),
                        "Ready", genCase.getStartUrl(), targetCase.getBrowser(), targetCase.getHeadless());
            }

            // Save script code if present
            if (genCase.getScriptCode() != null && !genCase.getScriptCode().isBlank()) {
                projectStore.saveCaseScript(suiteDir, targetCase.getId(), genCase.getScriptCode());
            }

            // Replace assertions if present
            if (genCase.getAssertions() != null && !genCase.getAssertions().isEmpty()) {
                projectStore.replaceAssertions(suiteDir, targetCase.getId(), genCase.getAssertions());
            }

            applied.add(targetCase);
        }

        return applied;
    }

    private String sanitizeCaseName(String name) {
        if (name == null || name.isBlank()) return "Case_" + System.currentTimeMillis();
        String safe = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (safe.isBlank()) return "Case_" + System.currentTimeMillis();
        return safe;
    }

    public static String extractJsonBlock(String text) {
        if (text == null) return "{}";
        int jsonStart = text.indexOf("```json");
        if (jsonStart >= 0) {
            int blockStart = jsonStart + 7;
            int blockEnd = text.indexOf("```", blockStart);
            if (blockEnd > blockStart) {
                return text.substring(blockStart, blockEnd).trim();
            }
        }
        int codeStart = text.indexOf("```");
        if (codeStart >= 0) {
            int blockStart = codeStart + 3;
            int blockEnd = text.indexOf("```", blockStart);
            if (blockEnd > blockStart) {
                return text.substring(blockStart, blockEnd).trim();
            }
        }
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1).trim();
        }
        return text.trim();
    }
}
