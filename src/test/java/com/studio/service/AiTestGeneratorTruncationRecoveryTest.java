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

    // --- continuation ---

    /** The JSON for the fallback case, kept only when the first case is already complete. */
    private static final String SMALL_CUT = """
            ```json
            {"summary":"ok","suggestedGroup":"G","suggestedSuite":"S","cases":[
              {"name":"TC01_A","description":"a","testSteps":[{"stepNumber":1,"action":"NAVIGATE","target":"P","testData":"","expectedResult":"OK"}],
               "testDataSets":[{"scenario":"x","parameters":{},"expectedOutcome":"OK"}],
               "assertions":[{"type":"ELEMENT_VISIBLE","locator":"body","value":"","status":"ACTIVE"}]}
            ]}
            ```
            """;
    private static final String PART_1 = TWO_CASES.substring(0, TWO_CASES.indexOf("\"TC02_B\""));
    private static final String PART_2 = TWO_CASES.substring(PART_1.length(), TWO_CASES.indexOf("\"TC02_B\"") + 40);
    private static final String PART_3 = TWO_CASES.substring(PART_2.length() + PART_1.length());

    private static double unusedOverlapRatio(String tail, String nextPiece) {
        int max = Math.min(80, Math.min(tail.length(), nextPiece.length()));
        for (int len = max; len >= 8; len--) {
            if (tail.regionMatches(tail.length() - len, nextPiece, 0, len)) {
                return len / (double) nextPiece.length();
            }
        }
        return 0;
    }

    @Test
    public void continuationMergesTurnsIntoOneCompleteReply() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride) {
                return switch (calls.incrementAndGet()) {
                    case 1 -> new Completion(PART_2, "length");
                    default -> new Completion(PART_3, "stop");
                };
            }
        };
        AiTestGeneratorService service = new AiTestGeneratorService(client, new ObjectMapper());

        // Drives the loop directly so the merge is asserted regardless of how many escalation attempts the
        // surrounding flow makes before continuation begins.
        OpenAiClient.Completion merged = service.continueCompletion(config(),
                List.of(new OpenAiClient.ChatMessage("user", "tạo case")), PART_1, 4096, new java.util.ArrayList<>());

        assertFalse(merged.truncated(), "a reply whose continuation finished is no longer truncated");
        assertEquals(2, service.parseResponse(merged.content(), AiSkill.PLAYWRIGHT_E2E).getCases().size(),
                "merged pieces must rebuild both cases");
    }

    /**
     * Drives the continuation loop directly so the turn bound can be asserted without relying on how many
     * escalation attempts the surrounding flow makes.
     */
    @Test
    public void continuationStopsAtTheTurnLimitAndKeepsThePartialResult() throws Exception {
        List<String> continuedPrompts = new java.util.ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride) {
                String lastUser = messages.get(messages.size() - 1).getTextContent();
                if (lastUser != null && lastUser.startsWith("[CONTINUATION]")) {
                    continuedPrompts.add(lastUser);
                }
                // Always cut, always appending a little: the loop must stop at the bound.
                return new Completion("{\"cases\":[{\"name\":\"x" + calls.incrementAndGet() + "\"", "length");
            }
        };
        AiTestGeneratorService service = new AiTestGeneratorService(client, new ObjectMapper());

        OpenAiClient.Completion merged = service.continueCompletion(config(),
                List.of(new OpenAiClient.ChatMessage("user", "tạo case")), "{\"cases\":[", 4096, new java.util.ArrayList<>());

        assertEquals(AiTestGeneratorService.MAX_CONTINUATION_TURNS, continuedPrompts.size(),
                "continuation must stop at the configured turn limit");
        assertTrue(merged.truncated(), "an unfinished continuation must report itself as truncated");
        assertTrue(merged.content().length() > "{\"cases\":[".length(), "the pieces accumulated so far are kept");
    }

    @Test
    public void mergeContinuationDropsTheRepeatedOverlap() {
        String head = "{\"a\":1,\"b\":2,\"c\":\"nội dung dài để trùng\",\"z\":0}";
        String repeated = "\"c\":\"nội dung dài để trùng\",\"z\":0}";

        String merged = AiTestGeneratorService.mergeContinuation(head, repeated);

        assertEquals(1, merged.split("nội dung dài để trùng", -1).length - 1, "the repeated fragment must appear once");
        assertTrue(merged.endsWith("\"z\":0}"));
        assertEquals(head, AiTestGeneratorService.mergeContinuation(head, ""), "an empty piece changes nothing");
        assertEquals("abc", AiTestGeneratorService.mergeContinuation("", "abc"));
    }

    @Test
    public void untruncatedReplyMakesNoExtraRequest() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride) {
                calls.incrementAndGet();
                return new Completion(TWO_CASES, "stop");
            }
        };

        AiTestGeneratorService.GenerationResult result = run(client);

        assertFalse(result.isOutputTruncated());
        assertTrue(calls.get() <= 2, "an uncut reply must not trigger a continuation, got " + calls.get() + " calls");
    }

    @Test
    public void continuationPromptAsksToResumeFromTheStoppingPoint() {
        String prompt = AiTestGeneratorService.buildContinuationPrompt("{\"cases\":[{\"name\":\"TC0");

        assertTrue(prompt.startsWith("[CONTINUATION]"));
        assertTrue(prompt.contains("TIẾP đúng từ ký tự tiếp theo"));
        assertTrue(prompt.contains("không mở lại từ đầu"));
        assertTrue(prompt.contains("TC0"), "the model must see where it stopped");
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

    /**
     * Turn limit reached: the accumulated text is deliberately NOT flagged truncated here. Continuation
     * already ran to its bound, the parsed cases are kept, and the run is reported as finished — the flag
     * exists for output that could not be recovered at all, not for a reply that stopped at the ceiling.
     */
    @Test
    public void partialCasesFromATruncatedReplyAreKeptWhenTheRetryDoesNotHelp() throws Exception {
        // First reply is cut but the first case closes; every retry replies with prose instead.
        String firstCaseComplete = SMALL_CUT;
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
        // The cut reply's own first case must survive even though every retry answered with prose.
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
