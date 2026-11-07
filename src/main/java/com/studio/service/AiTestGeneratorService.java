package com.studio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.AiConfig;
import com.studio.model.AiGeneratedCase;
import com.studio.model.Assertion;
import com.studio.model.TestCase;
import com.studio.model.TestDataItem;
import com.studio.model.TestStep;
import com.studio.model.TestSuite;
import com.studio.service.DocumentParserService.ParsedDocument;
import com.studio.store.ProjectStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service to generate and update Playwright test cases, automation scripts, and assertions
 * using OpenAI models from user prompts, documents, and live DOM contexts.
 * Incorporates best practices from Midscene, Testsigma, Skyvern, and Automa.
 */
public class AiTestGeneratorService {

    private final OpenAiClient openAiClient;
    private final ObjectMapper mapper;
    private final AiTokenOptimizer tokenOptimizer;

    public AiTestGeneratorService() {
        this(new OpenAiClient(), new ObjectMapper(), new AiTokenOptimizer());
    }

    public AiTestGeneratorService(OpenAiClient openAiClient, ObjectMapper mapper) {
        this(openAiClient, mapper, new AiTokenOptimizer());
    }

    public AiTestGeneratorService(OpenAiClient openAiClient, ObjectMapper mapper, AiTokenOptimizer tokenOptimizer) {
        this.openAiClient = openAiClient;
        this.mapper = mapper;
        this.tokenOptimizer = tokenOptimizer != null ? tokenOptimizer : new AiTokenOptimizer();
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
        You are an expert QA Automation Architect specialized in Playwright Java for "Playwright Automation Studio".
        Your task is to analyze user requirements, specification documents (PRD, SRS, user stories, matrices), or live UI DOM elements, and generate complete, ready-to-run Playwright Java test cases with dual representations (declarative NLP test steps and runnable script code).

        ### Core Architectural Principles (Inspired by Testsigma, Midscene, Skyvern):
        1. **Dual Representation**:
           - Provide human-readable declarative test steps (`testSteps`): Step #, Action, Target, Test Data, Description, Expected Result.
           - Provide parameterized test data sets (`testDataSets`): Cover Equivalence Partitioning & Boundary Value Analysis (e.g., Valid credentials, Invalid input, Empty fields, Extreme length).
           - Provide executable Java code in `scriptCode`.

        2. **Resilience & Autonomous Self-Healing**:
           - For element interactions, wrap dynamic selectors using the Studio's built-in self-healing proxy:
             `com.studio.runtime.StudioRuntime.healLocator("selector", "Action description").click();`
             or `com.studio.runtime.StudioRuntime.healLocator("selector", "Action description").fill("value");`
           - Interleave explicit wait conditions where page state shifts or navigates:
             `page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);`
           - Prefer semantic selectors (`[data-testid='...']`, `[aria-label='...']`, `role=button[name='...']`, text locator). If live DOM elements are provided in the prompt, prioritize those exact candidate selectors!

        3. **Execution Environment Rules**:
           - Browser and Page lifecycles are managed automatically by the Studio. Scripts must follow this Java template:
        ```java
        package com.test;

        import com.microsoft.playwright.*;
        import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

        public class <CaseClassName> {
            public static void main(String[] args) {
                Page page = com.studio.runtime.StudioRuntime.page();
                page.navigate("<StartUrl>");
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
                // User interactions and assertions using StudioRuntime.healLocator(...)
            }
        }
        ```
           - DO NOT call `playwright.chromium().launch()`, `Playwright.create()`, `browser.newContext()`, or `page.close()`. The Studio manages these.

        4. **Supported Assertion Types**:
           - TITLE_CONTAINS (value: text)
           - TITLE_EQUALS (value: title)
           - URL_CONTAINS (value: substring)
           - URL_EQUALS (value: url)
           - PAGE_HAS_TEXT (value: expected text)
           - PAGE_NOT_CONTAINS_TEXT (value: text)
           - ELEMENT_VISIBLE (locator: css or text locator)
           - ELEMENT_HIDDEN (locator)
           - ELEMENT_ENABLED (locator)
           - ELEMENT_DISABLED (locator)
           - ELEMENT_HAS_TEXT (locator, value)
           - ELEMENT_COUNT (locator, value: count as number)
           - INPUT_VALUE_EQUALS (locator, value)
           - ATTRIBUTE_EQUALS (locator, attribute, expectedValue)
           - CUSTOM_JS (value: javascript expression returning boolean)

        5. **Response Format**:
        You MUST respond ONLY with a JSON object (inside ```json ... ``` or raw JSON) matching this exact schema:
        {
          "summary": "Tóm tắt kịch bản kiểm thử bằng tiếng Việt",
          "cases": [
            {
              "name": "Case_Identifier_Name",
              "description": "Mô tả mục tiêu kịch bản",
              "startUrl": "https://example.com/target",
              "preconditions": [
                "Tài khoản kiểm thử đã được kích hoạt"
              ],
              "testSteps": [
                {
                  "stepNumber": 1,
                  "action": "NAVIGATE",
                  "target": "https://example.com/target",
                  "testData": "",
                  "description": "Mở trang đích",
                  "expectedResult": "Trang tải xong thành công"
                },
                {
                  "stepNumber": 2,
                  "action": "FILL",
                  "target": "[data-testid=\"username\"]",
                  "testData": "user@example.com",
                  "description": "Nhập tên đăng nhập",
                  "expectedResult": "Giá trị hiển thị trong ô nhập"
                }
              ],
              "testDataSets": [
                {
                  "scenario": "Trường hợp chuẩn (Happy Path)",
                  "parameters": {
                    "username": "user@example.com",
                    "password": "Password123!"
                  },
                  "expectedOutcome": "SUCCESS"
                }
              ],
              "scriptCode": "Java code as described above",
              "assertions": [
                {
                  "type": "ELEMENT_VISIBLE",
                  "locator": "[data-testid=\"dashboard\"]",
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
        return generateTestCases(config, userPrompt, attachments, contextInfo, (String) null, List.of());
    }

    /**
     * Generate test cases with token optimization, history sliding window, and attachment compression.
     */
    public GenerationResult generateTestCases(AiConfig config, String userPrompt,
                                             List<ParsedDocument> attachments,
                                             String contextInfo,
                                             List<com.studio.model.AiChatMessage> history) throws IOException, InterruptedException {
        return generateTestCases(config, userPrompt, attachments, contextInfo, (String) null, history);
    }

    /**
     * Generate test cases with Live DOM context injection.
     */
    public GenerationResult generateTestCases(AiConfig config, String userPrompt,
                                             List<ParsedDocument> attachments,
                                             String contextInfo,
                                             String domJson,
                                             List<com.studio.model.AiChatMessage> history) throws IOException, InterruptedException {
        int maxTokens = config.getMaxTokens() > 0 ? config.getMaxTokens() : AiTokenOptimizer.DEFAULT_MAX_CONTEXT_TOKENS;
        String formattedDom = (domJson != null && !domJson.isBlank()) ? tokenOptimizer.formatDomForPrompt(domJson, 50) : null;

        List<OpenAiClient.ChatMessage> messages = tokenOptimizer.buildOptimizedMessages(
                SYSTEM_PROMPT,
                history,
                userPrompt,
                attachments,
                contextInfo,
                formattedDom,
                maxTokens,
                AiTokenOptimizer.DEFAULT_SLIDING_WINDOW_TURNS
        );

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
        return generateTestCases(config, userPrompt, attachments, context.toString(), null, List.of());
    }

    /**
     * Parse OpenAI JSON output into GenerationResult with steps, test data, and validated scripts.
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
                    String rawName = cNode.path("name").asText("GeneratedCase");
                    String safeName = sanitizeCaseName(rawName);
                    ac.setName(safeName);
                    ac.setDescription(cNode.path("description").asText(""));
                    String startUrl = cNode.path("startUrl").asText("");
                    ac.setStartUrl(startUrl);

                    // Preconditions
                    JsonNode preNode = cNode.path("preconditions");
                    if (preNode.isArray()) {
                        List<String> pres = new ArrayList<>();
                        for (JsonNode p : preNode) {
                            if (p.isTextual() && !p.asText().isBlank()) {
                                pres.add(p.asText().trim());
                            }
                        }
                        ac.setPreconditions(pres);
                    }

                    // Test Steps (NLP steps)
                    JsonNode stepsNode = cNode.path("testSteps");
                    if (stepsNode.isArray()) {
                        List<TestStep> steps = new ArrayList<>();
                        int idx = 1;
                        for (JsonNode sNode : stepsNode) {
                            int stepNum = sNode.path("stepNumber").asInt(idx);
                            String action = sNode.path("action").asText("ACTION");
                            String target = sNode.path("target").asText("");
                            String testData = sNode.path("testData").asText("");
                            String desc = sNode.path("description").asText("");
                            String expected = sNode.path("expectedResult").asText("");
                            steps.add(new TestStep(stepNum, action, target, testData, desc, expected));
                            idx++;
                        }
                        ac.setTestSteps(steps);
                    }

                    // Test Data Sets
                    JsonNode dataSetsNode = cNode.path("testDataSets");
                    if (dataSetsNode.isArray()) {
                        List<TestDataItem> dataItems = new ArrayList<>();
                        for (JsonNode dNode : dataSetsNode) {
                            String scenario = dNode.path("scenario").asText("Scenario");
                            String expectedOutcome = dNode.path("expectedOutcome").asText("SUCCESS");
                            Map<String, String> params = new LinkedHashMap<>();
                            JsonNode paramsNode = dNode.path("parameters");
                            if (paramsNode.isObject()) {
                                Iterator<Map.Entry<String, JsonNode>> fields = paramsNode.fields();
                                while (fields.hasNext()) {
                                    Map.Entry<String, JsonNode> field = fields.next();
                                    params.put(field.getKey(), field.getValue().asText(""));
                                }
                            }
                            dataItems.add(new TestDataItem(scenario, params, expectedOutcome));
                        }
                        ac.setTestDataSets(dataItems);
                    }

                    // Validate & Auto-fix script code
                    String rawScript = cNode.path("scriptCode").asText("");
                    String validatedScript = validateAndFixScript(rawScript, safeName, startUrl);
                    ac.setScriptCode(validatedScript);

                    // Assertions
                    JsonNode assertsNode = cNode.path("assertions");
                    if (assertsNode.isArray()) {
                        List<Assertion> asserts = new ArrayList<>();
                        for (JsonNode aNode : assertsNode) {
                            Assertion a = new Assertion();
                            a.setType(normalizeAssertionType(aNode.path("type").asText("PAGE_HAS_TEXT")));
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
     * Validates script structure and auto-corrects missing imports or class wrappers.
     */
    public String validateAndFixScript(String scriptCode, String caseName, String startUrl) {
        if (scriptCode == null || scriptCode.isBlank()) {
            String className = (caseName != null && !caseName.isBlank()) ? caseName : "TestCaseAuto";
            String url = (startUrl != null && !startUrl.isBlank()) ? startUrl : "about:blank";
            return """
                package com.test;

                import com.microsoft.playwright.*;
                import com.microsoft.playwright.options.*;
                import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

                public class %s {
                    public static void main(String[] args) {
                        Page page = com.studio.runtime.StudioRuntime.page();
                        page.navigate("%s");
                        page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
                    }
                }
                """.formatted(className, url);
        }

        String cleaned = scriptCode.trim();

        // Strip illegal Playwright lifecycle calls
        cleaned = cleaned.replaceAll("(?m)^\\s*Playwright\\s+\\w+\\s*=\\s*Playwright\\.create\\(\\);?", "");
        cleaned = cleaned.replaceAll("(?m)^\\s*Browser\\s+\\w+\\s*=\\s*\\w+\\.\\w+\\(\\)\\.launch\\([^)]*\\);?", "");
        cleaned = cleaned.replaceAll("(?m)^\\s*\\w+\\.close\\(\\);?", "// Studio manages page/browser lifecycle");

        // If it's missing standard package or class wrapper
        if (!cleaned.contains("public class") && !cleaned.contains("class ")) {
            String className = (caseName != null && !caseName.isBlank()) ? caseName : "GeneratedTest";
            return """
                package com.test;

                import com.microsoft.playwright.*;
                import com.microsoft.playwright.options.*;
                import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

                public class %s {
                    public static void main(String[] args) {
                        Page page = com.studio.runtime.StudioRuntime.page();
                %s
                    }
                }
                """.formatted(className, indentScript(cleaned, "        "));
        }

        // Ensure package com.test;
        if (!cleaned.contains("package com.test;")) {
            cleaned = "package com.test;\n\n" + cleaned;
        }

        // Ensure standard imports
        if (!cleaned.contains("import com.microsoft.playwright.*;")) {
            cleaned = cleaned.replace("package com.test;\n", "package com.test;\n\nimport com.microsoft.playwright.*;\nimport com.microsoft.playwright.options.*;\nimport static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;\n");
        } else if (!cleaned.contains("import com.microsoft.playwright.options.*;")) {
            cleaned = cleaned.replace("import com.microsoft.playwright.*;\n", "import com.microsoft.playwright.*;\nimport com.microsoft.playwright.options.*;\n");
        }

        return cleaned;
    }

    private String indentScript(String code, String indent) {
        StringBuilder sb = new StringBuilder();
        for (String line : code.split("\\r?\\n")) {
            sb.append(indent).append(line).append("\n");
        }
        return sb.toString();
    }

    /**
     * Apply generated cases into a TestSuite in ProjectStore, updating scripts, assertions,
     * testSteps, and testDataSets.
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

            // Update Steps and Test Data
            if ((genCase.getTestSteps() != null && !genCase.getTestSteps().isEmpty()) ||
                (genCase.getTestDataSets() != null && !genCase.getTestDataSets().isEmpty())) {
                projectStore.updateCaseStepsAndData(suiteDir, targetCase.getId(), genCase.getTestSteps(), genCase.getTestDataSets());
                targetCase.setTestSteps(new ArrayList<>(genCase.getTestSteps()));
                targetCase.setTestDataSets(new ArrayList<>(genCase.getTestDataSets()));
            }

            applied.add(targetCase);
        }

        return applied;
    }

    public static String normalizeAssertionType(String rawType) {
        if (rawType == null || rawType.isBlank()) return "PAGE_HAS_TEXT";
        String t = rawType.trim().toUpperCase();
        return switch (t) {
            case "PAGE_TITLE_CONTAINS" -> "TITLE_CONTAINS";
            case "PAGE_TITLE_EQUALS" -> "TITLE_EQUALS";
            case "PAGE_URL_CONTAINS" -> "URL_CONTAINS";
            case "PAGE_URL_EQUALS" -> "URL_EQUALS";
            case "PAGE_CONTAINS_TEXT" -> "PAGE_HAS_TEXT";
            case "ELEMENT_EXISTS" -> "ELEMENT_VISIBLE";
            case "ELEMENT_NOT_VISIBLE" -> "ELEMENT_HIDDEN";
            case "ELEMENT_TEXT_CONTAINS" -> "ELEMENT_HAS_TEXT";
            case "CHECKBOX_CHECKED" -> "ELEMENT_CHECKED";
            case "ELEMENT_HAS_ATTRIBUTE" -> "ATTRIBUTE_EQUALS";
            case "CUSTOM_JS_TRUE" -> "CUSTOM_JS";
            default -> t;
        };
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
