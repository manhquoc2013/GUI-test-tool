package com.studio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.AiConfig;
import com.studio.model.AiModelCapabilities;
import com.studio.model.AiSkill;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-batch orchestration used to swallow a malformed batch: a prose reply parsed to zero cases and
 * the loop simply moved on, so the suite came back short with no signal. These tests pin per-batch
 * self-correction and the explicit report of a batch that never recovered.
 */
public class AiMultiAgentBatchRecoveryTest {

    private static final String CASE_JSON = """
            ```json
            {
              "summary": "Lô đã xử lý",
              "suggestedGroup": "QuanLyDonHang",
              "suggestedSuite": "KiemThu_DonHang",
              "cases": [
                {"name": "TC01_DonHang_HappyPath", "description": "Happy", "priority": "P0",
                 "testSteps": [{"stepNumber": 1, "action": "NAVIGATE", "target": "Page", "testData": "", "expectedResult": "OK"}],
                 "testDataSets": [{"scenario": "Valid", "parameters": {}, "expectedOutcome": "OK"}],
                 "assertions": [{"type": "ELEMENT_VISIBLE", "locator": "body", "value": "", "status": "ACTIVE"}]}
              ]
            }
            ```
            """;

    private static final String SRS = """
            # SRS
            ## Màn hình 1: Đăng nhập & Xác thực
            ## Màn hình 2: Giỏ hàng
            ## Màn hình 3: Thanh toán
            ## Màn hình 4: Theo dõi Đơn hàng
            """;

    private static DocumentParserService.ParsedDocument srsDoc() {
        return new DocumentParserService.ParsedDocument(
                "SRS_Orders.pdf", Path.of("/docs/SRS_Orders.pdf"), false, SRS, "text/markdown", SRS.length());
    }

    private static AiTestGeneratorService.GenerationResult run(OpenAiClient client) throws Exception {
        AiTestGeneratorService service = new AiTestGeneratorService(client, new ObjectMapper());
        AiConfig config = new AiConfig();
        config.setApiKey("sk-mock-test-key");
        return service.generateTestCases(
                config,
                "[PHẠM VI] Bao quát 4 màn hình [XÁC MINH] Toàn diện",
                List.of(srsDoc()),
                "",
                null,
                List.of(),
                status -> { },
                AiSkill.MULTI_AGENT_LOOP,
                null,
                false);
    }

    @Test
    public void proseBatchIsRetriedAndRecovered() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<OpenAiClient.ChatMessage> messages, int outputTokenOverride, boolean jsonMode) {
                // First attempt of the very first batch answers with prose, exactly like the production log.
                if (calls.getAndIncrement() == 0) {
                    return new Completion("Tôi đã phân tích yêu cầu chức năng giỏ hàng của bạn.", "stop");
                }
                return new Completion(CASE_JSON, "stop");
            }
        };

        AiTestGeneratorService.GenerationResult result = run(client);

        assertNotNull(result);
        assertFalse(result.getCases().isEmpty(), "recovered batch must contribute cases");
        assertTrue(result.getBatchRetries() >= 1, "the self-correction retry must be recorded");
        assertFalse(result.hasLostBatches(), "a recovered batch is not a lost batch");
    }

    @Test
    public void permanentlyFailingBatchIsReportedInsteadOfSwallowed() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<OpenAiClient.ChatMessage> messages, int outputTokenOverride, boolean jsonMode) {
                // Every reply for the first batch stays prose; later batches answer correctly.
                if (calls.getAndIncrement() < 2) {
                    return new Completion("Tôi sẽ thiết kế kịch bản kiểm thử cho bạn ngay sau đây.", "stop");
                }
                return new Completion(CASE_JSON, "stop");
            }
        };

        AiTestGeneratorService.GenerationResult result = run(client);

        assertNotNull(result);
        assertTrue(result.hasLostBatches(), "a batch that never recovered must be reported");
        assertEquals(List.of(1), result.getLostBatches());
        assertTrue(result.describeLostBatches().contains("CHƯA phủ đủ"),
                "the report must tell the user the suite is incomplete");
        assertFalse(result.getCases().isEmpty(), "healthy batches must still be returned");
    }

    @Test
    public void eachBatchAsksForTheModelsFullOutputCeiling() throws Exception {
        // Production sent defaultOutputTokensFor(false)=4096 first, always came back cut, and paid a
        // second 40s call at 8192 for every batch. The largest reply of the pipeline must start at the
        // ceiling the model actually supports.
        AiConfig config = new AiConfig();
        config.setApiKey("sk-mock-test-key");
        config.setModel("deepseek-chat");
        int ceiling = AiModelCapabilities.resolveOutputCeiling(config);
        List<Integer> budgets = java.util.Collections.synchronizedList(new ArrayList<>());

        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig cfg, List<OpenAiClient.ChatMessage> messages,
                                           int outputTokenOverride, boolean jsonMode) {
                budgets.add(outputTokenOverride);
                return new Completion(CASE_JSON, "stop");
            }
        };

        AiTestGeneratorService service = new AiTestGeneratorService(client, new ObjectMapper());
        service.generateTestCases(config, "[PHẠM VI] Bao quát 4 màn hình [XÁC MINH] Toàn diện",
                List.of(srsDoc()), "", null, List.of(), status -> { },
                AiSkill.MULTI_AGENT_LOOP, null, false);

        assertFalse(budgets.isEmpty(), "the batch pipeline must issue at least one call");
        for (int budget : budgets) {
            assertEquals(ceiling, budget,
                    "every batch call must use the model's own output ceiling instead of a smaller default");
        }
    }

    /** A one-case JSON reply whose case name carries the screen it belongs to. */
    private static String caseFor(String caseName, String description) {
        return """
                ```json
                {"summary":"Lô đã xử lý","suggestedGroup":"QuanLyDonHang","suggestedSuite":"KiemThu_DonHang","cases":[
                  {"name":"%s","description":"%s","priority":"P0",
                   "testSteps":[{"stepNumber":1,"action":"NAVIGATE","target":"Page","testData":"","expectedResult":"OK"}],
                   "testDataSets":[{"scenario":"Valid","parameters":{},"expectedOutcome":"OK"}],
                   "assertions":[{"type":"ELEMENT_VISIBLE","locator":"body","value":"","status":"ACTIVE"}]}
                ]}
                ```
                """.formatted(caseName, description);
    }

    /** The screen token the batch prompt declares, so a fake case can be traced to its screen. */
    private static String firstScreenToken(String prompt) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("Token định danh: `([^`]+)`").matcher(prompt);
        return matcher.find() ? matcher.group(1) : "ManHinh";
    }

    @Test
    public void cutBatchRetriesOnlyTheScreensThatWereNotCovered() throws Exception {
        // Repeating the same oversized batch is what made the turn hang: the retry overflowed again and
        // produced the same cut. The retry must carry only the screens the salvaged cases did not cover.
        List<String> batchPrompts = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean firstBatchAsked = new java.util.concurrent.atomic.AtomicBoolean();

        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<OpenAiClient.ChatMessage> messages,
                                           int outputTokenOverride, boolean jsonMode) {
                String lastUser = messages.get(messages.size() - 1).getTextContent();
                boolean continuation = lastUser != null && lastUser.startsWith("[CONTINUATION");
                if (continuation) {
                    // No progress on purpose: the continuation must give up and hand over to recovery.
                    return new Completion("", "length");
                }
                batchPrompts.add(lastUser);
                String token = firstScreenToken(lastUser);
                if (lastUser != null && lastUser.contains("MÀN HÌNH #1")
                        && firstBatchAsked.compareAndSet(false, true)) {
                    // Batch 1 is cut after the case for screen 1 only.
                    return new Completion(caseFor("TC01_" + token + "_HappyPath", "Màn hình 1"), "length");
                }
                return new Completion(caseFor("TC02_" + token + "_HappyPath", "Màn hình kế tiếp"), "stop");
            }
        };

        AiTestGeneratorService service = new AiTestGeneratorService(client, new ObjectMapper());
        AiConfig config = new AiConfig();
        config.setApiKey("sk-mock-test-key");
        config.setModel("deepseek-chat");
        AiTestGeneratorService.GenerationResult result = service.generateTestCases(
                config,
                "[PHẠM VI] Bao quát 4 màn hình [XÁC MINH] Toàn diện",
                List.of(srsDoc()), "", null, List.of(), status -> { },
                AiSkill.MULTI_AGENT_LOOP, null, false);

        boolean recoveryAskedOnlyUncoveredScreens = batchPrompts.stream()
                .anyMatch(p -> p != null && p.contains("MÀN HÌNH #2") && !p.contains("MÀN HÌNH #1"));
        assertTrue(recoveryAskedOnlyUncoveredScreens,
                "after the cut, the retry must ask for the uncovered screens instead of resending the batch");
        assertTrue(result.getCases().size() >= 3,
                "the salvaged case and the recovered cases must both be kept");
    }

    @Test
    public void retryMessagesAskForTheJsonContractAgain() {
        AiTestGeneratorService service = new AiTestGeneratorService(new OpenAiClient(), new ObjectMapper());
        List<OpenAiClient.ChatMessage> base = List.of(new OpenAiClient.ChatMessage("system", "sys"));

        List<OpenAiClient.ChatMessage> retry = service.buildBatchRetryMessages(base, "Tôi đã phân tích...");

        assertEquals(3, retry.size());
        assertEquals("assistant", retry.get(1).getRole());
        assertEquals("user", retry.get(2).getRole());
        assertTrue(retry.get(2).getTextContent().contains("cases"));
        assertFalse(service.containsExtractableCases("Tôi đã phân tích yêu cầu."));
        assertTrue(service.containsExtractableCases(CASE_JSON));
    }

    private static int batchCount(AiTestGeneratorService.GenerationResult result) {
        return Math.max(1, result.getTotalScreensInRequest() > 0 ? result.getTotalScreensInRequest() : 1);
    }
}