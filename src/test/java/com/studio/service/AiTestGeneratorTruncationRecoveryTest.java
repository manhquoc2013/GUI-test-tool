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
        // A roomy window: these tests exercise truncation/continuation, not the context clamp.
        config.setMaxTokens(200_000);
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
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride, boolean jsonMode) {
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
        assertTrue(budgets.get(1) <= config().getEffectiveOutputTokenCeiling(),
                "the retry budget must stay under the model's own output ceiling");
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
    /** The cases JSON exactly as a model emits it, split into the pieces a truncated stream would deliver. */
    private static final int PART_1_END = 180;
    private static final int PART_2_END = 330;
    private static final String PART_1 = TWO_CASES.substring(0, PART_1_END);
    private static final String PART_2 = TWO_CASES.substring(PART_1_END, PART_2_END);
    private static final String PART_3 = TWO_CASES.substring(PART_2_END);

    @Test
    public void continuationMergesTurnsIntoOneCompleteReply() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride, boolean jsonMode) {
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
                List.of(new OpenAiClient.ChatMessage("user", "tạo case")), PART_1, 60_000, new java.util.ArrayList<>());

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
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride, boolean jsonMode) {
                String lastUser = messages.get(messages.size() - 1).getTextContent();
                if (lastUser != null && lastUser.startsWith("[CONTINUATION")) {
                    continuedPrompts.add(lastUser);
                }
                // Always cut, always appending a token-free difference: the loop must stop at the bound, and no
                // piece may look like a restart (each new value must differ from every earlier one).
                return new Completion("v" + calls.incrementAndGet() + "\",", "length");
            }
        };
        AiTestGeneratorService service = new AiTestGeneratorService(client, new ObjectMapper());
        AiConfig roomy = new AiConfig();
        roomy.setApiKey("k");
        roomy.setMaxTokens(200_000);

        OpenAiClient.Completion merged = service.continueCompletion(roomy,
                List.of(new OpenAiClient.ChatMessage("user", "tạo case")), "{\"cases\":[\"seed-value-abc",
                60_000, new java.util.ArrayList<>());

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
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride, boolean jsonMode) {
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
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride, boolean jsonMode) {
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
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride, boolean jsonMode) {
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
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride, boolean jsonMode) {
                return new Completion(TWO_CASES, "stop");
            }
        };

        AiTestGeneratorService.GenerationResult result = run(client);

        assertFalse(result.isOutputTruncated());
        assertFalse(result.hasLostBatches());
    }

    // --- restart detection and context-window budget ---

    @Test
    public void restartFragmentIsDetectedAndNotConcatenated() {
        String head = "```json\n{\"summary\":\"ok\",\"cases\":[{\"name\":\"TC01\",\"testSteps\":[";
        String restart = "```json\n{\"summary\":\"Bộ kịch bản kiểm thử\",\"cases\":[{\"name\":\"TC01_A\"";
        String replay = "{\"summary\":\"ok\",\"cases\":[{\"name\":\"TC01_A\"";

        assertTrue(AiTestGeneratorService.isDocumentRestart(head, restart),
                "re-opening the fence inside the document is a restart");
        assertTrue(AiTestGeneratorService.isDocumentRestart(head, replay),
                "replaying an earlier opening is a restart");
        assertFalse(AiTestGeneratorService.isDocumentRestart(head, "\"action\":\"CLICK\"}]}"),
                "a genuine continuation must not be refused");
        assertFalse(AiTestGeneratorService.isDocumentRestart("", "{\"cases\":[]}"),
                "nothing to restart when there is no prefix yet");
    }

    @Test
    public void restartingFragmentIsReaskedWithAStrictPromptAndPrefixKept() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> userPrompts = new java.util.ArrayList<>();
        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride, boolean jsonMode) {
                String lastUser = messages.get(messages.size() - 1).getTextContent();
                userPrompts.add(lastUser);
                int call = calls.incrementAndGet();
                if (call == 1) {
                    return new Completion(PART_1, "length");
                }
                if (call == 2) {
                    // Restarts the document instead of continuing it.
                    return new Completion("```json\n{\"summary\":\"Bộ kịch bản\",\"cases\":[", "length");
                }
                return new Completion(PART_3, "stop");
            }
        };
        AiTestGeneratorService service = new AiTestGeneratorService(client, new ObjectMapper());

        OpenAiClient.Completion merged = service.continueCompletion(config(),
                List.of(new OpenAiClient.ChatMessage("user", "tạo case")), PART_1, 60_000, new java.util.ArrayList<>());

        assertTrue(userPrompts.stream().anyMatch(p -> p.startsWith("[CONTINUATION - STRICT]")),
                "the refused turn must be re-asked with the strict instruction");
        assertTrue(merged.content().startsWith(PART_1), "the pre-continuation prefix must stay intact");
        assertFalse(merged.content().contains("```json\n{\"summary\":\"Bộ kịch bản\""),
                "the restarting fragment must not be concatenated");
    }

    @Test
    public void restartRetriesAreSeparatelyBounded() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride, boolean jsonMode) {
                calls.incrementAndGet();
                return new Completion("```json\n{\"summary\":\"lại từ đầu\",\"cases\":[", "length");
            }
        };
        AiTestGeneratorService service = new AiTestGeneratorService(client, new ObjectMapper());

        OpenAiClient.Completion result = service.continueCompletion(config(),
                List.of(new OpenAiClient.ChatMessage("user", "tạo case")), PART_1, 60_000, new java.util.ArrayList<>());

        assertEquals(PART_1, result.content(), "a permanently rewriting model leaves the prefix untouched");
        assertTrue(calls.get() <= 1 + AiTestGeneratorService.MAX_RESTART_RETRIES,
                "restart retries must be capped independently, got " + calls.get() + " calls");
        assertTrue(result.truncated());
    }

    @Test
    public void outputBudgetIsClampedToTheContextWindow() {
        AiTestGeneratorService service = new AiTestGeneratorService(new OpenAiClient(), new ObjectMapper());
        List<OpenAiClient.ChatMessage> messages = List.of(
                new OpenAiClient.ChatMessage("system", "the quick brown fox jumps over the lazy dog ".repeat(1_000)));

        AiConfig small = new AiConfig();
        small.setApiKey("k");
        small.setMaxTokens(16_000);
        int clamped = service.clampOutputBudget(small, messages, 16_384, "");

        assertTrue(clamped < 16_384, "a small-context model must not be asked for the full 16384");
        assertTrue(clamped >= 0);
        // ~9000 words ⇒ ~11700 tokens by the estimator; window 16k, margin 1k ⇒ ~3.3k of room.
        assertTrue(clamped >= 2_000 && clamped <= 5_000, "budget must track the room left, got " + clamped);

        AiConfig big = new AiConfig();
        big.setApiKey("k");
        big.setMaxTokens(200_000);
        assertEquals(16_384, service.clampOutputBudget(big, messages, 16_384, ""),
                "a large-context model keeps the caller's wish up to the hard ceiling");
    }

    @Test
    public void contextOverflowRefusalFallsBackToTheReducedBudget() throws Exception {
        List<Integer> budgets = new java.util.ArrayList<>();
        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride, boolean jsonMode)
                    throws java.io.IOException {
                budgets.add(outputTokenOverride);
                // The provider's real window is smaller than the configured one, so the first ask is refused.
                // prompt: 1 500 × 9 words ⇒ 13 500 words × 1.3 ≈ 17 550 tokens; window 20 000 − 17 554 − margin
                // 1 024 leaves ≈ 1 422 — under this threshold, and well below the 16 384 of the first request.
                if (outputTokenOverride > 2_000) {
                    throw new java.io.IOException("HTTP 400: This model's maximum context length is 16000 tokens");
                }
                return new Completion(TWO_CASES, "stop");
            }
        };
        AiTestGeneratorService service = new AiTestGeneratorService(client, new ObjectMapper());
        AiConfig contextBound = new AiConfig();
        contextBound.setApiKey("k");
        contextBound.setMaxTokens(20_000);

        OpenAiClient.Completion completion = service.completeWithBudgetFallback(contextBound,
                List.of(new OpenAiClient.ChatMessage("user", "the quick brown fox jumps over the lazy dog ".repeat(1_500))), 16_384);

        assertEquals("stop", completion.finishReason());
        assertTrue(budgets.size() >= 2, "the refused request must be retried");
        assertTrue(budgets.get(budgets.size() - 1) <= 2_000,
                "the fallback must shrink to what the context window allows, got " + budgets.get(budgets.size() - 1));
    }

    @Test
    public void unrelatedErrorIsNotSwallowedByTheFallback() {
        OpenAiClient client = new OpenAiClient() {
            @Override
            public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride, boolean jsonMode) {
                throw new java.io.UncheckedIOException(new java.io.IOException("HTTP 401: bad key"));
            }
        };
        AiTestGeneratorService service = new AiTestGeneratorService(client, new ObjectMapper());

        assertThrows(Exception.class, () -> service.completeWithBudgetFallback(config(),
                List.of(new OpenAiClient.ChatMessage("user", "hi")), 1024));
    }

    @Test
    public void overloadedCeilingConstantIsReadFromTheClient() {
        assertFalse(AiTestGeneratorService.isContextOverflowRefusal("HTTP 401: bad key"));
        assertTrue(AiTestGeneratorService.isContextOverflowRefusal("HTTP 400: context_length_exceeded"));
    }
}
