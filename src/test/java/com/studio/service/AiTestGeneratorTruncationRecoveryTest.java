package com.studio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.AiConfig;
import com.studio.model.AiSkill;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A long scriptCode can push a reply past the output token limit. Before this, {@code finish_reason}
 * was logged and then ignored: the half-written JSON parsed to zero cases and the answer was lost.
 * These tests pin the recovery — retry once with a larger, capped budget, keep the better result, and
 * report a reply that is still truncated instead of pretending the suite is complete.
 */
public class AiTestGeneratorTruncationRecoveryTest {

    private static final String TWO_CASES = """
            ```json
            {"summary":"ok","suggestedGroup":"G","suggestedSuite":"S","cases":[
              {"name":"TC01_A","description":"a","testSteps":[{"stepNumber":1,"action":"NAVIGATE","target":"P","testData":"","expectedResult":"OK"}],
               "testDataSets":[{"scenario":"x","parameters":{},"expectedOutcome":"OK"}],
               "assertions":[{"type":"ELEMENT_VISIBLE","locator":"body","value":"","status":"ACTIVE"}]},
              {"name":"TC02_B","description":"b","testSteps":[{"stepNumber":1,"action":"NAVIGATE","target":"P","testData":"","expectedResult":"OK"}],
               "testDataSets":[{"scenario":"x","parameters":{},"expectedOutcome":"OK"}],
               "assertions":[{"type":"ELEMENT_VISIBLE","locator":"body","value":"","status":"ACTIVE"}]}
            ]}
            ```
            """;

    /** Truncated mid-JSON, exactly what a provider returns when scriptCode overruns max_tokens. */
    private static final String CUT_MID_JSON = """
            ```json
            {"summary":"ok","suggestedGroup":"G","suggestedSuite":"S","cases":[
              {"name":"TC01_A","description":"a","testSteps":[{"stepNumber":1,"action":"NAVIGATE","target":"P",
            """;

    private static AiConfig config() {
        AiConfig config = new AiConfig();
        config.setApiKey("sk-mock-test-key");
        return config;
    }

    private static AiTestGeneratorService.GenerationResult run(OpenAiClient client) throws Exception {
        AiTestGeneratorService service = new AiTestGeneratorService(client, new ObjectMapper());
        return service.generateTestCases(config(), "Tạo 3 test case cho màn hình Đăng nhập", List.of(), "",
                null, List.of(), status -> { }, AiSkill.PLAYWRIGHT_E2E, null, true);
    }

    @Test
    public void truncatedReplyIsRetriedWithABiggerBudgetAndRecovered() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<Integer> budgets = new java.util.ArrayList<>();
        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride) {
                budgets.add(outputTokenOverride);
                return calls.getAndIncrement() == 0
                        ? new Completion(CUT_MID_JSON, "length")
                        : new Completion(TWO_CASES, "stop");
            }
        };

        AiTestGeneratorService.GenerationResult result = run(client);

        assertNotNull(result);
        assertEquals(2, result.getCases().size(), "the retried reply must be kept, not the truncated one");
        assertFalse(result.isOutputTruncated(), "a successful retry clears the truncation flag");
        assertEquals("", result.describeOutputTruncation());
        assertTrue(budgets.size() >= 2);
        assertTrue(budgets.get(1) > budgets.get(0), "the retry must ask for a larger output budget");
        assertTrue(budgets.get(1) <= AiTestGeneratorService.MAX_OUTPUT_TOKENS_CEILING,
                "the retry budget must stay under the model ceiling");
    }

    @Test
    public void stillTruncatedReplyIsReportedNotSwallowed() throws Exception {
        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride) {
                return new Completion(CUT_MID_JSON, "length");
            }
        };

        AiTestGeneratorService.GenerationResult result = run(client);

        assertNotNull(result);
        assertTrue(result.isOutputTruncated(), "a reply that is still cut must be flagged");
        assertFalse(result.describeOutputTruncation().isBlank(),
                "the user must be told the generated suite may be missing its tail");
    }

    @Test
    public void partialCasesFromATruncatedReplyAreKeptWhenTheRetryDoesNotHelp() throws Exception {
        // First reply is cut but the first case is complete; the retry returns prose instead.
        String firstCaseComplete = CUT_MID_JSON + "\"testSteps\":[]}]}";
        AtomicInteger calls = new AtomicInteger();
        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride) {
                return calls.getAndIncrement() == 0
                        ? new Completion(firstCaseComplete, "length")
                        : new Completion("Tôi không thể tạo kịch bản lúc này.", "stop");
            }
        };

        AiTestGeneratorService.GenerationResult result = run(client);

        assertNotNull(result);
        assertTrue(result.isOutputTruncated(), "the answer is still short of the full request");
        assertFalse(result.getCases().isEmpty(), "cases readable from the cut reply must not be discarded");
    }

    @Test
    public void truncationFlagIsFalseForANormalReply() throws Exception {
        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride) {
                return new Completion(TWO_CASES, "stop");
            }
        };

        AiTestGeneratorService.GenerationResult result = run(client);

        assertFalse(result.isOutputTruncated());
        assertFalse(result.hasLostBatches());
    }
}
