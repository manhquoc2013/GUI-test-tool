package com.studio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.AiConfig;
import com.studio.model.AiGeneratedCase;
import com.studio.model.AiSkill;
import com.studio.model.Assertion;
import com.studio.model.TestCase;
import com.studio.model.TestDataItem;
import com.studio.model.TestGroup;
import com.studio.model.TestStep;
import com.studio.model.TestSuite;
import com.studio.service.DocumentParserService.ParsedDocument;
import com.studio.store.ProjectStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        You are an expert QA Automation Architect specialized in Playwright Java for "Test Studio".
        Your task is to analyze user requirements, specification documents (PRD, SRS, user stories, matrices), or live UI DOM elements, and generate complete, ready-to-run Playwright Java test cases with dual representations (declarative NLP test steps and runnable script code).

        ### Core Architectural Principles (Inspired by Testsigma, Midscene, Skyvern & Agency-Agents):
        1. **Dual Representation**:
           - Provide human-readable declarative test steps (`testSteps`): Step #, Action, Target, Test Data, Description, Expected Result.
           - Provide parameterized test data sets (`testDataSets`): Cover Equivalence Partitioning & Boundary Value Analysis (e.g., Valid credentials, Invalid input, Empty fields, Extreme length).
           - Provide executable Java code in `scriptCode`.

        2. **Deterministic Playwright Engineering (Zero Hard Sleeps - Agency-Agents Standard)**:
           - **Tuyệt đối không dùng hard sleep**: `Thread.sleep(...)` hoặc `page.waitForTimeout(...)` bị cấm hoàn toàn vì gây flake và lãng phí thời gian.
           - Chờ đợi trên điều kiện thực tế: `page.waitForLoadState(LoadState.DOMCONTENTLOADED)`, `page.waitForLoadState(LoadState.NETWORKIDLE)`, `page.waitForURL(...)`, hoặc auto-waiting locators (`assertThat(locator).isVisible()`).

        3. **Authentication & Precondition Handling (CRITICAL)**:
           - **Quy tắc bắt buộc**: Nếu URL đích hoặc danh sách phần tử DOM thực tế (Live Page Elements) chứa form Đăng nhập / Xác thực (chẳng hạn các bộ chọn: `#auth-email`, `#auth-password`, `input[type='email']`, `input[type='password']`, `button[type='submit']`, text "Đăng nhập", "Login", "Sign in"):
             AI BẮT BUỘC phải sinh bước Đăng nhập trước (nhập email/tên đăng nhập, nhập mật khẩu, bấm nút Đăng nhập và chờ trang tải xong) trước khi thực hiện các luồng nghiệp vụ bên trong (như thêm giao dịch, tạo đơn hàng, quản lý danh sách, checkout).
           - Trong kịch bản Playwright `scriptCode`, thực hiện kiểm tra hoặc bọc bước đăng nhập an toàn ngay sau khi điều hướng:
             ```java
             // Bước tiền đề: Đăng nhập nếu trang yêu cầu xác thực
             if (page.locator("#auth-email, input[type='email'], input[name='email']").isVisible()) {
                 page.locator("#auth-email, input[type='email'], input[name='email']").first().fill("user@example.com");
                 page.locator("#auth-password, input[type='password'], input[name='password']").first().fill("password123");
                 page.locator("button[type='submit'], button:has-text('Đăng nhập'), button:has-text('Sign in')").first().click();
                 page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
             }
             ```

        4. **Resilience, Autonomous Self-Healing & Strict Mode Disambiguation**:
           - For element interactions, wrap dynamic selectors using the Studio's built-in self-healing proxy:
             `com.studio.runtime.StudioRuntime.healLocator("selector", "Action description").click();`
             or `com.studio.runtime.StudioRuntime.healLocator("selector", "Action description").fill("value");`
           - Interleave explicit wait conditions where page state shifts or navigates:
             `page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);`
           - **Tuyệt đối phòng tránh Strict Mode Violation trong Playwright**: Playwright mặc định bật Strict Mode cho cả tương tác (`.click()`, `.fill()`) và assertion (`assertThat(loc).isVisible()`). Nếu một bộ chọn khớp với nhiều hơn 1 phần tử (ví dụ: `a:has-text('...')` khớp cả link trên breadcrumbs, header nav lẫn tiêu đề trang), Playwright sẽ dừng với lỗi: `Error: strict mode violation: locator(...) resolved to N elements`.
           - **Quy tắc bắt buộc khi sinh selector**:
             + Ưu tiên bộ chọn định danh duy nhất: `[data-testid='...']`, `[aria-label='...']`, hoặc `#id`.
             + Nếu sử dụng text selector hoặc selector chung chung có nguy cơ trùng lặp (ví dụ: links trên GitHub, navigation tabs, buttons lặp lại): BẮT BUỘC dùng selector có phạm vi cụ thể (ví dụ: `[data-testid='repo-name-link']` hoặc `nav a:has-text('...')`) hoặc bổ sung `.first()` khi khởi tạo locator hoặc assertion, ví dụ:
               `Locator repoTitle = StudioRuntime.healLocator("a:has-text('AI-Kit')", "Repository Title").first();`
               `assertThat(repoTitle).isVisible();`
           - If live DOM elements are provided in the prompt, prioritize those exact candidate selectors!

        5. **Execution Environment Rules**:
           - Browser and Page lifecycles are managed automatically by the Studio. Scripts must follow this Java template:
        ```java
        package com.test;

        import com.microsoft.playwright.*;
        import static com.studio.runtime.StudioAssertions.assertThat;

        public class <CaseClassName> {
            public static void main(String[] args) {
                Page page = com.studio.runtime.StudioRuntime.page();
                page.navigate("<StartUrl>");
                page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
                // User interactions and assertions using StudioRuntime.healLocator(...) & assertThat(...)
            }
        }
        ```
           - DO NOT call `playwright.chromium().launch()`, `Playwright.create()`, `browser.newContext()`, or `page.close()`. The Studio manages these.

        6. **Supported Assertion Types & Syntax Rules**:
           - TITLE_CONTAINS (value: text)
           - TITLE_EQUALS (value: title)
           - URL_CONTAINS (value: substring) -> In scriptCode use: `assertThat(page).hasURL(java.util.regex.Pattern.compile(".*substring.*"));`
           - URL_EQUALS (value: url) -> In scriptCode use: `assertThat(page).hasURL("exact_url_or_glob");`
           - **CRITICAL**: In Playwright Java, NEVER use lambdas like `hasURL(url -> url.contains(...))` or `hasURL((u) -> ...)`. `PageAssertions.hasURL` only accepts String or Pattern, NOT functional interfaces!
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

        7. **Quality & Assertion Integrity (ECC Standard & Localization Handling)**:
           - Every generated case MUST contain specific, verifiable assertions verifying state or content.
           - NEVER include empty try-catch blocks or swallow exceptions in `scriptCode`.
           - Always cover boundary scenarios (empty fields, max length, invalid formats) in `testDataSets`.
           - **Localized / Non-English Apps & Validation Error Assertions**:
             + Khi kiểm thử các trường hợp kiểm tra lỗi (negative tests, ví dụ: đăng nhập sai, bỏ trống trường bắt buộc, sai mật khẩu):
               Nếu trang web hiển thị ngôn ngữ tiếng Việt (hoặc người dùng yêu cầu tiếng Việt): TUYỆT ĐỐI KHÔNG hardcode văn bản tiếng Anh như `"Invalid"`, `"Error"`, `"Please fill in"`.
             + Thay vào đó, hãy kiểm tra sự xuất hiện của thông báo lỗi (Alert/Toast/Notice) bằng `assertThat(errorMsg).isVisible();` với selector bao quát (ví dụ: `[role='alert'], .ant-message, .error-message, .toast, .alert-danger`).
             + Nếu cần kiểm tra văn bản của thông báo lỗi, hãy dùng regex kiểm tra các từ khóa tiếng Việt phổ biến:
               `assertThat(errorMsg).containsText(java.util.regex.Pattern.compile(".*(lỗi|không chính xác|không đúng|bảo vệ|thất bại|vui lòng).*"));`
           - **Form có CAPTCHA / Mã bảo vệ**:
             + Nếu trang đăng nhập chứa trường CAPTCHA (nhãn "Mã bảo vệ", "Captcha", có ảnh mã bảo vệ):
               Đối với kịch bản kiểm tra ngoại lệ/đăng nhập sai, kịch bản cần điền thông tin (có thể nhập sai captcha hoặc để trống) và kiểm tra thông báo cảnh báo/lỗi xuất hiện (`assertThat(alert).isVisible()`).

        8. **Test Case Priority Evaluation (P0 - P3)**:
           - You MUST evaluate and classify each test case into an appropriate priority tier based on business impact:
             - "P0" (Critical / Smoke): Blocker workflows (Authentication, Core Checkout, Payment, Security).
             - "P1" (High / Core Flow): Essential business workflows, primary CRUD, and major validations.
             - "P2" (Medium / Regression): Secondary workflows, search/filtering, pagination, error handling.
             - "P3" (Low / Edge Case): UI polish, formatting, tooltips, rare edge scenarios.

        9. **Response Format**:
        You MUST respond ONLY with a JSON object (inside ```json ... ``` or raw JSON) matching this exact schema:
        {
          "summary": "Tóm tắt kịch bản kiểm thử bằng tiếng Việt",
          "cases": [
            {
              "name": "Case_Identifier_Name",
              "description": "Mô tả mục tiêu kịch bản",
              "priority": "P0 | P1 | P2 | P3",
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
        return generateTestCases(config, userPrompt, attachments, contextInfo, domJson, history, null);
    }

    /**
     * Generate test cases with Live DOM context injection and live progress reporting.
     */
    public GenerationResult generateTestCases(AiConfig config, String userPrompt,
                                             List<ParsedDocument> attachments,
                                             String contextInfo,
                                             String domJson,
                                             List<com.studio.model.AiChatMessage> history,
                                             java.util.function.Consumer<String> progressListener) throws IOException, InterruptedException {
        return generateTestCases(config, userPrompt, attachments, contextInfo, domJson, history, progressListener, AiSkill.AUTO);
    }

    /**
     * Generate test cases with Live DOM context injection, live progress reporting, and specialized AiSkill routing.
     */
    public GenerationResult generateTestCases(AiConfig config, String userPrompt,
                                             List<ParsedDocument> attachments,
                                             String contextInfo,
                                             String domJson,
                                             List<com.studio.model.AiChatMessage> history,
                                             java.util.function.Consumer<String> progressListener,
                                             AiSkill selectedSkill) throws IOException, InterruptedException {
        int maxTokens = config.getMaxTokens() > 0 ? config.getMaxTokens() : AiTokenOptimizer.DEFAULT_MAX_CONTEXT_TOKENS;
        if (progressListener != null) {
            progressListener.accept("Đang tối ưu ngữ cảnh & lịch sử hội thoại...");
        }

        AiSkill effectiveSkill = (selectedSkill == null || selectedSkill == AiSkill.AUTO)
                ? AiSkill.detectSkill(userPrompt, domJson != null && !domJson.isBlank(), attachments != null && !attachments.isEmpty())
                : selectedSkill;

        String effectivePrompt = AiSkill.stripSlashCommand(userPrompt);
        String systemPrompt = (effectiveSkill.getSystemPrompt() != null)
                ? effectiveSkill.getSystemPrompt()
                : SYSTEM_PROMPT;

        String formattedDom = (domJson != null && !domJson.isBlank()) ? tokenOptimizer.formatDomForPrompt(domJson, 50) : null;

        List<OpenAiClient.ChatMessage> messages = tokenOptimizer.buildOptimizedMessages(
                systemPrompt,
                history,
                effectivePrompt,
                attachments,
                contextInfo,
                formattedDom,
                maxTokens,
                AiTokenOptimizer.DEFAULT_SLIDING_WINDOW_TURNS
        );

        if (progressListener != null) {
            progressListener.accept("Đang gửi yêu cầu [" + effectiveSkill.getDisplayName() + "] tới " + config.getEffectiveModel() + "...");
        }

        String reply = openAiClient.chatCompletion(config, messages);

        if (progressListener != null) {
            progressListener.accept("Đang xử lý cấu trúc phản hồi...");
        }

        return parseResponse(reply, effectiveSkill);
    }

    /**
     * Generate or refine script and assertions for a single case.
     */
    public GenerationResult generateOrUpdateSingleCase(AiConfig config, String userPrompt,
                                                       List<ParsedDocument> attachments,
                                                       TestCase currentCase, String currentScript,
                                                       AiSkill selectedSkill) throws IOException, InterruptedException {
        StringBuilder context = new StringBuilder();
        if (currentCase != null) {
            context.append("Target Case Name: ").append(currentCase.getName()).append("\n");
            context.append("Target Start URL: ").append(currentCase.getStartUrl()).append("\n");
            context.append("Current Description: ").append(currentCase.getDescription()).append("\n");
            context.append("Current Assertions count: ").append(currentCase.getAssertions().size()).append("\n");
            if (currentScript != null && !currentScript.isBlank()) {
                context.append("Existing Java Script:\n```java\n").append(currentScript).append("\n```\n");
            }
        }
        context.append("\nINSTRUCTION FOR SINGLE CASE: Focus primarily on writing the complete, runnable `scriptCode` in Playwright Java and accurate `assertions`. Keep `testSteps` concise (at most 4-5 steps) and `testDataSets` minimal (1 entry) to maximize token budget for the Java code.\n");
        String targetUrl = LiveDomScanner.extractFirstUrl(userPrompt);
        if (targetUrl == null && currentCase != null && currentCase.getStartUrl() != null && !currentCase.getStartUrl().isBlank()) {
            targetUrl = LiveDomScanner.extractFirstUrl(currentCase.getStartUrl());
        }

        String domContext = null;
        if (targetUrl != null) {
            try {
                LiveDomScanner scanner = new LiveDomScanner();
                String scanned = scanner.scanUrl(targetUrl, 10000);
                if (scanned != null && !scanned.isBlank() && !"[]".equals(scanned)) {
                    domContext = scanned;
                }
            } catch (Exception ignored) {}
        }

        return generateTestCases(config, userPrompt, attachments, context.toString(), domContext, List.of(), null, selectedSkill != null ? selectedSkill : AiSkill.PLAYWRIGHT_E2E);
    }

    public GenerationResult generateOrUpdateSingleCase(AiConfig config, String userPrompt,
                                                       List<ParsedDocument> attachments,
                                                       TestCase currentCase, String currentScript) throws IOException, InterruptedException {
        return generateOrUpdateSingleCase(config, userPrompt, attachments, currentCase, currentScript, AiSkill.PLAYWRIGHT_E2E);
    }

    /**
     * Parse OpenAI JSON output into GenerationResult with steps, test data, and validated scripts.
     */
    public GenerationResult parseResponse(String rawResponse) {
        return parseResponse(rawResponse, AiSkill.PLAYWRIGHT_E2E);
    }

    /**
     * Parse AI output into GenerationResult according to the activated skill mode.
     */
    public GenerationResult parseResponse(String rawResponse, AiSkill skill) {
        if (rawResponse == null || rawResponse.isBlank()) {
            AiLoggingService.warn("AI-PARSE", "Phản hồi thô từ AI rỗng.");
            return new GenerationResult("Không nhận được phản hồi từ AI.", List.of(), "");
        }

        AiLoggingService.info("AI-PARSE", "Bắt đầu phân tích phản hồi AI (độ dài: " + rawResponse.length()
                + " ký tự, skill=" + (skill != null ? skill.getDisplayName() : "PLAYWRIGHT_E2E") + ")");

        // For non-E2E skills (Exhaustive Unit Test, RCA, Security Fuzzing), AI outputs Markdown matrices & code
        if (skill != null && skill != AiSkill.PLAYWRIGHT_E2E && skill != AiSkill.AUTO) {
            String jsonCandidate = extractJsonBlock(rawResponse);
            if (jsonCandidate.startsWith("{") && jsonCandidate.contains("\"cases\"")) {
                try {
                    return parseJsonCases(jsonCandidate, rawResponse);
                } catch (Exception ignored) {}
            }
            List<AiGeneratedCase> cases = extractCasesFromMarkdown(rawResponse);
            return new GenerationResult(rawResponse.trim(), cases, rawResponse);
        }

        // Default: Playwright E2E JSON extraction with 4-tier resilient recovery
        String jsonText = extractJsonBlock(rawResponse);

        // Tier 1: Parse strict JSON
        try {
            GenerationResult res = parseJsonCases(jsonText, rawResponse);
            if (!res.getCases().isEmpty()) {
                AiLoggingService.info("AI-PARSE", "Tier 1: Phân tích JSON chuẩn thành công. Trích xuất " + res.getCases().size() + " case(s).");
                return res;
            }
        } catch (Exception e1) {
            AiLoggingService.warn("AI-PARSE", "Tier 1 JSON parsing thất bại (" + e1.getMessage() + "). Đang thử Tier 2 (Tự động vá JSON bị cắt ngang)...");
        }

        // Tier 2: Try JSON Auto-repair (handles unclosed strings, arrays, objects due to max_tokens truncation)
        try {
            String repairedJson = autoRepairTruncatedJson(jsonText);
            GenerationResult res = parseJsonCases(repairedJson, rawResponse);
            if (!res.getCases().isEmpty()) {
                AiLoggingService.info("AI-PARSE", "Tier 2: Tự động vá JSON thành công! Đã khôi phục " + res.getCases().size() + " case(s).");
                return res;
            }
        } catch (Exception e2) {
            AiLoggingService.warn("AI-PARSE", "Tier 2 Auto-repair thất bại (" + e2.getMessage() + "). Đang thử Tier 3 (Tolerant extraction)...");
        }

        // Tier 3: Tolerant regex / pattern extraction directly from raw text
        try {
            GenerationResult res = extractDirectlyFromRawResponse(rawResponse);
            if (!res.getCases().isEmpty()) {
                AiLoggingService.info("AI-PARSE", "Tier 3: Trích xuất trực tiếp script & assertions thành công. Đã phục hồi " + res.getCases().size() + " case(s).");
                return res;
            }
        } catch (Exception e3) {
            AiLoggingService.warn("AI-PARSE", "Tier 3 Tolerant extraction thất bại (" + e3.getMessage() + "). Đang thử Tier 4 (Markdown blocks)...");
        }

        // Tier 4: Fallback to Markdown code blocks
        List<AiGeneratedCase> cases = extractCasesFromMarkdown(rawResponse);
        if (!cases.isEmpty() || rawResponse.contains("##")) {
            AiLoggingService.info("AI-PARSE", "Tier 4: Trích xuất thành công " + cases.size() + " case(s) từ khối Markdown.");
            return new GenerationResult(rawResponse.trim(), cases, rawResponse);
        }

        AiLoggingService.error("AI-PARSE", "Tất cả 4 tầng trích xuất đều thất bại cho phản hồi AI. Chi tiết: "
                + (rawResponse.length() > 300 ? rawResponse.substring(0, 300) + "..." : rawResponse));
        return new GenerationResult("Lỗi khi phân tích JSON phản hồi từ AI: Cấu trúc bị cắt ngang hoặc không hợp lệ. Vui lòng xem log chi tiết.", List.of(), rawResponse);
    }

    public static List<AiGeneratedCase> extractCasesFromMarkdown(String text) {
        List<AiGeneratedCase> list = new ArrayList<>();
        if (text == null || text.isBlank()) return list;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("```(?:java|csharp|python|rust|typescript|javascript)?\\s*\\n(.*?)```", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(text);
        int idx = 1;
        while (matcher.find()) {
            String code = matcher.group(1).trim();
            if (code.length() > 30) {
                AiGeneratedCase c = new AiGeneratedCase();
                c.setName("Generated_Test_" + idx);
                c.setDescription("Đoạn mã kiểm thử trích xuất từ phản hồi của AI");
                c.setScriptCode(code);
                list.add(c);
                idx++;
            }
        }
        return list;
    }

    private GenerationResult parseJsonCases(String jsonText, String rawResponse) throws Exception {
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
                ac.setPriority(cNode.path("priority").asText("P1"));
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
                import static com.studio.runtime.StudioAssertions.assertThat;

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
        // Agency-Agents standard: eliminate flaky hard sleeps automatically
        cleaned = cleaned.replaceAll("(?m)^\\s*Thread\\.sleep\\([^)]*\\);?", "// Eliminated hard sleep (Agency-Agents standard)");
        cleaned = cleaned.replaceAll("(?m)^\\s*page\\.waitForTimeout\\([^)]*\\);?", "page.waitForLoadState(LoadState.NETWORKIDLE); // Replaced hard sleep with condition wait");

        // Normalize invalid hasURL lambda to valid Java Pattern
        cleaned = cleaned.replaceAll(
                "hasURL\\s*\\(\\s*\\(?\\s*\\w+\\s*\\)?\\s*->\\s*\\w+\\.contains\\s*\\(\\s*(\"(?:\\\\\"|[^\"])*\")\\s*\\)\\s*\\)",
                "hasURL(java.util.regex.Pattern.compile(\".*\" + java.util.regex.Pattern.quote($1) + \".*\"))");
        cleaned = cleaned.replaceAll(
                "hasURL\\s*\\(\\s*\\(?\\s*\\w+\\s*\\)?\\s*->\\s*\\w+\\.endsWith\\s*\\(\\s*(\"(?:\\\\\"|[^\"])*\")\\s*\\)\\s*\\)",
                "hasURL(java.util.regex.Pattern.compile(\".*\" + java.util.regex.Pattern.quote($1) + \"$\"))");
        cleaned = cleaned.replaceAll(
                "hasURL\\s*\\(\\s*\\(?\\s*\\w+\\s*\\)?\\s*->\\s*\\w+\\.equals\\s*\\(\\s*(\"(?:\\\\\"|[^\"])*\")\\s*\\)\\s*\\)",
                "hasURL($1)");

        // If it's missing standard package or class wrapper
        if (!cleaned.contains("public class") && !cleaned.contains("class ")) {
            String className = (caseName != null && !caseName.isBlank()) ? caseName : "GeneratedTest";
            return """
                package com.test;

                import com.microsoft.playwright.*;
                import com.microsoft.playwright.options.*;
                import static com.studio.runtime.StudioAssertions.assertThat;

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
            cleaned = cleaned.replace("package com.test;\n", "package com.test;\n\nimport com.microsoft.playwright.*;\nimport com.microsoft.playwright.options.*;\nimport static com.studio.runtime.StudioAssertions.assertThat;\n");
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

        Files.createDirectories(suiteDir);
        if (suiteDir.getParent() != null && !Files.exists(suiteDir.getParent().resolve("group.json"))) {
            Files.createDirectories(suiteDir.getParent());
            TestGroup group = new TestGroup(suiteDir.getParent().getFileName().toString(), "");
            group.setType("group");
            projectStore.saveGroup(group, suiteDir.getParent());
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

            if (genCase.getPriority() != null) {
                targetCase.setPriority(genCase.getPriority());
                projectStore.updateCasePriority(suiteDir, targetCase.getId(), genCase.getPriority());
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
        return com.studio.util.StringEscapeUtil.safeFileName(name, "Case_" + System.currentTimeMillis());
    }

    public static String extractJsonBlock(String text) {
        if (text == null) return "{}";
        String s = text.trim();
        int jsonStart = s.indexOf("```json");
        if (jsonStart >= 0) {
            int blockStart = jsonStart + 7;
            int blockEnd = s.indexOf("```", blockStart);
            if (blockEnd > blockStart) {
                return s.substring(blockStart, blockEnd).trim();
            }
            // Truncated: no closing fence -> return everything after ```json
            return s.substring(blockStart).trim();
        }
        int codeStart = s.indexOf("```");
        if (codeStart >= 0) {
            int blockStart = codeStart + 3;
            int blockEnd = s.indexOf("```", blockStart);
            if (blockEnd > blockStart) {
                return s.substring(blockStart, blockEnd).trim();
            }
            return s.substring(blockStart).trim();
        }
        int firstBrace = s.indexOf('{');
        if (firstBrace >= 0) {
            // Find where the root object actually closes by counting depth outside quotes
            int depth = 0;
            boolean inStr = false;
            boolean esc = false;
            int rootEnd = -1;
            for (int i = firstBrace; i < s.length(); i++) {
                char c = s.charAt(i);
                if (inStr) {
                    if (esc) esc = false;
                    else if (c == '\\') esc = true;
                    else if (c == '"') inStr = false;
                } else {
                    if (c == '"') {
                        inStr = true;
                    } else if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            rootEnd = i;
                            break;
                        }
                    }
                }
            }
            if (rootEnd >= 0) {
                return s.substring(firstBrace, rootEnd + 1).trim();
            }
            return s.substring(firstBrace).trim();
        }
        return s;
    }

    /**
     * Automatically fixes truncated JSON strings (e.g. cut off by max_tokens limit) by closing
     * open strings, balancing arrays and objects in LIFO order, and cleaning dangling commas/colons.
     */
    public static String autoRepairTruncatedJson(String text) {
        if (text == null || text.isBlank()) return "{}";
        String trimmed = text.trim();

        int jsonIdx = trimmed.indexOf("```json");
        if (jsonIdx >= 0) {
            trimmed = trimmed.substring(jsonIdx + 7).trim();
            int endFence = trimmed.indexOf("```");
            if (endFence >= 0) {
                trimmed = trimmed.substring(0, endFence).trim();
            }
        } else {
            int codeIdx = trimmed.indexOf("```");
            if (codeIdx >= 0) {
                trimmed = trimmed.substring(codeIdx + 3).trim();
                int endFence = trimmed.indexOf("```");
                if (endFence >= 0) {
                    trimmed = trimmed.substring(0, endFence).trim();
                }
            }
        }

        int firstBrace = trimmed.indexOf('{');
        if (firstBrace < 0) return "{}";
        trimmed = trimmed.substring(firstBrace);

        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (inString) {
                if (c == '\n') {
                    sb.append("\\n");
                } else if (c == '\r') {
                    sb.append("\\r");
                } else if (c == '\t') {
                    sb.append("\\t");
                } else {
                    sb.append(c);
                }

                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                    sb.append(c);
                } else {
                    sb.append(c);
                }
            }
        }

        // If string was truncated mid-quote, close it
        if (inString) {
            if (escape && sb.length() > 0 && sb.charAt(sb.length() - 1) == '\\') {
                sb.deleteCharAt(sb.length() - 1);
            }
            sb.append('"');
        }

        String s = sb.toString().trim();
        // Remove trailing comma or dangling colon before unclosed containers
        while (!s.isEmpty()) {
            char last = s.charAt(s.length() - 1);
            if (last == ',' || Character.isWhitespace(last)) {
                s = s.substring(0, s.length() - 1).trim();
            } else if (last == ':') {
                s = s + "\"\"";
                break;
            } else {
                break;
            }
        }

        // If it ends with a dangling key like: , "fieldName" without colon, strip it
        s = s.replaceAll(",\\s*\"[^\"]*\"\\s*$", "");

        // Count open brackets/braces outside strings
        java.util.Deque<Character> stack = new java.util.ArrayDeque<>();
        boolean str = false;
        boolean esc = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (str) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') str = false;
            } else {
                if (c == '"') {
                    str = true;
                } else if (c == '{' || c == '[') {
                    stack.push(c);
                } else if (c == '}' && !stack.isEmpty() && stack.peek() == '{') {
                    stack.pop();
                } else if (c == ']' && !stack.isEmpty() && stack.peek() == '[') {
                    stack.pop();
                }
            }
        }

        StringBuilder res = new StringBuilder(s);
        while (!stack.isEmpty()) {
            char open = stack.pop();
            if (open == '{') res.append('}');
            else if (open == '[') res.append(']');
        }

        String fixed = res.toString();
        // Clean any trailing commas before } or ] repeatedly
        String prev;
        do {
            prev = fixed;
            fixed = fixed.replaceAll(",\\s*([}\\]])", "$1");
        } while (!fixed.equals(prev));

        return fixed;
    }

    /**
     * Tier 3 Tolerant extraction: Scrapes scriptCode, assertions, name and summary directly
     * from raw text when full JSON parsing and repair both fail.
     */
    public GenerationResult extractDirectlyFromRawResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return new GenerationResult("Phản hồi rỗng", List.of(), rawResponse);
        }

        String summary = "Kịch bản phục hồi từ phản hồi AI";
        java.util.regex.Matcher sumMatcher = java.util.regex.Pattern.compile("\"summary\"\\s*:\\s*\"([^\"]+)\"").matcher(rawResponse);
        if (sumMatcher.find()) {
            summary = sumMatcher.group(1);
        }

        // 1. Try to extract scriptCode from JSON string
        String scriptCode = null;
        java.util.regex.Matcher scriptMatcher = java.util.regex.Pattern.compile("\"scriptCode\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(rawResponse);
        if (scriptMatcher.find()) {
            scriptCode = unescapeJsonString(scriptMatcher.group(1));
        } else {
            // Check if truncated before closing quote: "scriptCode"\s*:\s*"(.*)
            java.util.regex.Matcher truncMatcher = java.util.regex.Pattern.compile("\"scriptCode\"\\s*:\\s*\"(.*)", java.util.regex.Pattern.DOTALL).matcher(rawResponse);
            if (truncMatcher.find()) {
                String potential = truncMatcher.group(1);
                int nextKey = potential.indexOf("\",\n");
                if (nextKey > 0) {
                    potential = potential.substring(0, nextKey);
                }
                scriptCode = unescapeJsonString(potential);
            }
        }

        // If not found in JSON field, search for markdown java code block
        if (scriptCode == null || scriptCode.length() < 30) {
            List<AiGeneratedCase> mdCases = extractCasesFromMarkdown(rawResponse);
            if (!mdCases.isEmpty()) {
                return new GenerationResult(summary, mdCases, rawResponse);
            }
        }

        if (scriptCode == null || scriptCode.length() < 30) {
            return new GenerationResult("Không tìm thấy mã kịch bản Playwright", List.of(), rawResponse);
        }

        AiGeneratedCase genCase = new AiGeneratedCase();
        genCase.setName("Case_Recovered_" + (System.currentTimeMillis() % 10000));

        java.util.regex.Matcher nameMatcher = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(rawResponse);
        if (nameMatcher.find()) {
            genCase.setName(sanitizeCaseName(nameMatcher.group(1)));
        }

        java.util.regex.Matcher descMatcher = java.util.regex.Pattern.compile("\"description\"\\s*:\\s*\"([^\"]+)\"").matcher(rawResponse);
        if (descMatcher.find()) {
            genCase.setDescription(descMatcher.group(1));
        }

        java.util.regex.Matcher urlMatcher = java.util.regex.Pattern.compile("\"startUrl\"\\s*:\\s*\"([^\"]+)\"").matcher(rawResponse);
        if (urlMatcher.find()) {
            genCase.setStartUrl(urlMatcher.group(1));
        }

        String validated = validateAndFixScript(scriptCode, genCase.getName(), genCase.getStartUrl());
        genCase.setScriptCode(validated);

        // Extract assertions
        List<Assertion> assertions = new ArrayList<>();
        java.util.regex.Pattern assertPat = java.util.regex.Pattern.compile(
                "\\{\\s*\"type\"\\s*:\\s*\"([^\"]+)\"(?:\\s*,\\s*\"locator\"\\s*:\\s*\"([^\"]*)\")?(?:\\s*,\\s*\"value\"\\s*:\\s*\"([^\"]*)\")?",
                java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher aMatcher = assertPat.matcher(rawResponse);
        while (aMatcher.find()) {
            String type = aMatcher.group(1);
            String loc = aMatcher.group(2) != null ? aMatcher.group(2) : "";
            String val = aMatcher.group(3) != null ? aMatcher.group(3) : "";
            Assertion a = new Assertion();
            a.setType(normalizeAssertionType(type));
            a.setLocator(loc);
            a.setValue(val);
            a.setStatus("ACTIVE");
            assertions.add(a);
        }
        genCase.setAssertions(assertions);

        return new GenerationResult(summary, List.of(genCase), rawResponse);
    }

    private static String unescapeJsonString(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        boolean escape = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (escape) {
                switch (c) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'u' -> {
                        if (i + 4 < input.length()) {
                            try {
                                int hex = Integer.parseInt(input.substring(i + 1, i + 5), 16);
                                sb.append((char) hex);
                                i += 4;
                            } catch (NumberFormatException ignored) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                    }
                    default -> sb.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
