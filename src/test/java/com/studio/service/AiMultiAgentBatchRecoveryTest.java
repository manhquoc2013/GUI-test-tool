package com.studio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.AiConfig;
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
            ### Trang 1
            ## 1. Đăng nhập & Xác thực
            ### Trang 2
            ## 2. Giỏ hàng
            ### Trang 3
            ## 3. Thanh toán
            ### Trang 4
            ## 4. Theo dõi Đơn hàng
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
            public String chatCompletion(AiConfig config, List<OpenAiClient.ChatMessage> messages) {
                // First attempt of the very first batch answers with prose, exactly like the production log.
                if (calls.getAndIncrement() == 0) {
                    return "Tôi đã phân tích yêu cầu chức năng giỏ hàng của bạn.";
                }
                return CASE_JSON;
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
            public String chatCompletion(AiConfig config, List<OpenAiClient.ChatMessage> messages) {
                // Every reply for the first batch stays prose; later batches answer correctly.
                if (calls.getAndIncrement() < 2) {
                    return "Tôi sẽ thiết kế kịch bản kiểm thử cho bạn ngay sau đây.";
                }
                return CASE_JSON;
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