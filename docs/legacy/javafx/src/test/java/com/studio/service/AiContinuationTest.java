package com.studio.service;

import com.studio.model.AiConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The continuation loop is now shared by test generation (JSON) and by the planner/sub-agents (prose).
 * These tests pin both shapes: what counts as a complete answer, what a replayed opening does, and how
 * the turn budget and token usage are reported.
 */
class AiContinuationTest {

    private static final List<OpenAiClient.ChatMessage> BASE =
            List.of(new OpenAiClient.ChatMessage("user", "Viết báo cáo chi tiết"));

    private static AiConfig config(String model, int contextWindow) {
        AiConfig config = new AiConfig();
        config.setModel(model);
        config.setMaxTokens(contextWindow);
        return config;
    }

    @Test
    void proseContinuesUntilTheProviderSaysStop() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> log = new ArrayList<>();
        OpenAiClient.Completion first = new OpenAiClient.Completion("Phần thứ nhất.", "length", 100, 50);

        OpenAiClient.Completion result = AiContinuation.continueUntilComplete(
                config("gpt-4o", 200_000), BASE, first, 8192, AiContinuation.DocumentShape.PROSE,
                (messages, budget) -> {
                    calls.incrementAndGet();
                    return new OpenAiClient.Completion(" Phần thứ hai.", "stop", 120, 30);
                }, log);

        assertEquals("Phần thứ nhất. Phần thứ hai.", result.content(),
                "the pieces must be joined in order");
        assertEquals("stop", result.finishReason(), "the provider's stop reason ends the loop");
        assertFalse(result.truncated());
        assertEquals(1, calls.get(), "one continuation turn was enough");
        assertEquals(1, log.size());
        assertEquals(220, result.promptTokens(), "usage of every turn is summed");
        assertEquals(80, result.completionTokens());
    }

    @Test
    void proseStopsAtTheTurnBudgetWhenEveryReplyIsCut() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<String> log = new ArrayList<>();
        OpenAiClient.Completion first = new OpenAiClient.Completion("Mở đầu.", "length");

        OpenAiClient.Completion result = AiContinuation.continueUntilComplete(
                config("gpt-4o", 200_000), BASE, first, 8192, AiContinuation.DocumentShape.PROSE,
                (messages, budget) -> new OpenAiClient.Completion(
                        " Đoạn " + calls.incrementAndGet() + ".", "length"), log);

        assertEquals(AiContinuation.DEFAULT_MAX_TURNS, calls.get(),
                "an endlessly cut answer must stop at the turn budget");
        assertTrue(result.truncated(), "the answer is still incomplete and must say so");
        assertEquals(AiContinuation.DEFAULT_MAX_TURNS, log.size());
        assertTrue(result.content().startsWith("Mở đầu."));
        assertTrue(result.content().contains(" Đoạn 3."));
    }

    @Test
    void replayedOpeningIsNotAppendedTwice() throws Exception {
        String accumulated = "Báo cáo kiểm thử chức năng đăng nhập gồm ba phần chính.";
        OpenAiClient.Completion first = new OpenAiClient.Completion(accumulated, "length");
        AtomicInteger calls = new AtomicInteger();

        OpenAiClient.Completion result = AiContinuation.continueUntilComplete(
                config("gpt-4o", 200_000), BASE, first, 8192, AiContinuation.DocumentShape.PROSE,
                (messages, budget) -> {
                    calls.incrementAndGet();
                    // The model rewrote the opening instead of resuming it.
                    return new OpenAiClient.Completion(accumulated, "stop");
                }, new ArrayList<>());

        assertEquals(accumulated, result.content(), "a replayed opening must not be duplicated");
        assertEquals(2, calls.get(),
                "one refused restart is re-asked exactly once, then the loop stops without appending");
    }

    @Test
    void jsonIsCompleteAsSoonAsTheDocumentClosesEvenIfProviderSaysLength() throws Exception {
        OpenAiClient.Completion first = new OpenAiClient.Completion("{\"cases\": [", "length");
        AtomicInteger calls = new AtomicInteger();

        OpenAiClient.Completion result = AiContinuation.continueUntilComplete(
                config("gpt-4o", 200_000), BASE, first, 8192, AiContinuation.DocumentShape.JSON,
                (messages, budget) -> {
                    calls.incrementAndGet();
                    return new OpenAiClient.Completion("]}", "length");
                }, new ArrayList<>());

        assertEquals("{\"cases\": []}", result.content());
        assertEquals("stop", result.finishReason(),
                "a balanced document is complete whatever the stop reason claimed");
        assertFalse(result.truncated());
        assertTrue(calls.get() <= 2);
    }

    @Test
    void jsonRestartIsReaskedWithTheStrictPromptBeforeGivingUp() throws Exception {
        List<String> prompts = new ArrayList<>();
        OpenAiClient.Completion first = new OpenAiClient.Completion("{\"cases\": [{\"name\":\"TC01", "length");

        OpenAiClient.Completion result = AiContinuation.continueUntilComplete(
                config("gpt-4o", 200_000), BASE, first, 8192, AiContinuation.DocumentShape.JSON,
                (messages, budget) -> {
                    String lastPrompt = messages.get(messages.size() - 1).getTextContent();
                    prompts.add(lastPrompt);
                    if (lastPrompt.startsWith("[CONTINUATION - STRICT]")) {
                        return new OpenAiClient.Completion("_A\"}]}", "stop");
                    }
                    // First attempt rewrites the document from the start: unusable as a continuation.
                    return new OpenAiClient.Completion("```json\n{\"cases\": [", "length");
                }, new ArrayList<>());

        assertEquals(2, prompts.size(), "the restart must be re-asked exactly once");
        assertTrue(prompts.get(1).startsWith("[CONTINUATION - STRICT]"));
        assertEquals("{\"cases\": [{\"name\":\"TC01_A\"}]}", result.content());
        assertEquals("stop", result.finishReason());
    }

    @Test
    void lostConnectionDuringContinuationKeepsWhatAlreadyArrived() throws Exception {
        OpenAiClient.Completion first = new OpenAiClient.Completion("Phần đầu đã nhận được.", "length");

        OpenAiClient.Completion result = AiContinuation.continueUntilComplete(
                config("gpt-4o", 200_000), BASE, first, 8192, AiContinuation.DocumentShape.PROSE,
                (messages, budget) -> {
                    // What a proxy that resets the stream looks like after every retry is spent.
                    throw new IOException("HTTP/1.1 header parser received no bytes");
                }, new ArrayList<>());

        assertEquals("Phần đầu đã nhận được.", result.content(),
                "text received before the connection dropped must never be thrown away");
        assertTrue(result.truncated(), "the missing tail must still be reported as incomplete");
    }

    @Test
    void lostConnectionBeforeAnyContentStillFailsTheCall() {
        OpenAiClient.Completion empty = new OpenAiClient.Completion("", "length");

        assertThrows(IOException.class, () -> AiContinuation.continueUntilComplete(
                        config("gpt-4o", 200_000), BASE, empty, 8192, AiContinuation.DocumentShape.PROSE,
                        (messages, budget) -> {
                            throw new IOException("Connection reset");
                        }, new ArrayList<>()),
                "a call that produced nothing must surface the transport failure");
    }

    @Test
    void continuationTurnDoesNotEchoTheWholeAnswerBack() throws Exception {
        String longAnswer = "Báo cáo rất dài. ".repeat(2_500);
        List<List<OpenAiClient.ChatMessage>> sent = new ArrayList<>();

        AiContinuation.continueUntilComplete(
                config("gpt-4o", 200_000), BASE,
                new OpenAiClient.Completion(longAnswer, "length"), 8192,
                AiContinuation.DocumentShape.PROSE,
                (messages, budget) -> {
                    sent.add(messages);
                    return new OpenAiClient.Completion(" Phần cuối.", "stop");
                }, new ArrayList<>());

        String echo = sent.get(0).stream()
                .filter(m -> "assistant".equals(m.getRole()))
                .findFirst().orElseThrow()
                .getTextContent();
        assertTrue(echo.length() <= AiContinuation.CONTINUATION_ECHO_CHARS,
                "the echoed answer must stay bounded so the prompt does not grow with every turn "
                        + "(actual: " + echo.length() + " chars)");
        assertTrue(longAnswer.endsWith(echo), "what is echoed must be the tail, where the answer stopped");
    }

    @Test
    void clampUsesTheModelCeilingThenTheManualOverride() {
        AiConfig gpt4o = config("gpt-4o", 200_000);
        assertEquals(16384, AiContinuation.clampOutputBudget(gpt4o, List.of(), 100_000, ""));

        gpt4o.setOutputTokenCeiling(32000);
        int raised = AiContinuation.clampOutputBudget(gpt4o, List.of(), 100_000, "");
        assertTrue(raised > 16384 && raised <= 32000,
                "the manual ceiling must raise the clamp (actual: " + raised + ")");

        AiConfig smallWindow = config("gpt-4.1", 20_000);
        assertTrue(AiContinuation.clampOutputBudget(smallWindow, List.of(), 100_000, "") <= 20_000,
                "the context window always bounds the output budget");
    }

    @Test
    void unreportedUsageStaysUnreportedInsteadOfBecomingZero() throws Exception {
        OpenAiClient.Completion result = AiContinuation.continueUntilComplete(
                config("gpt-4o", 200_000), BASE,
                new OpenAiClient.Completion("Nửa đầu", "length"), 8192, AiContinuation.DocumentShape.PROSE,
                (messages, budget) -> new OpenAiClient.Completion(" và nửa sau.", "stop"),
                new ArrayList<>());

        assertEquals(-1, result.promptTokens(), "-1 must survive so the UI never shows a measured 0");
        assertEquals(-1, result.completionTokens());
    }
}
