package com.studio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studio.model.AiConfig;
import com.studio.model.AiModelCapabilities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns an output-limit stop into the rest of the answer instead of a lost tail.
 *
 * <p>A reply cut by {@code max_tokens} used to leave the caller with half a document. Retrying the
 * same prompt only reproduces the same prefix, so the model is handed its own partial answer and asked
 * to resume at the exact character where it stopped; the pieces are joined with the repeated overlap
 * removed. This is shared by test-case generation, the coordinator planner and the sub-agents, so
 * every non-streaming consumer of a long answer recovers the same way.</p>
 */
public final class AiContinuation {

    /** Continuation turns appended to one reply before the answer is reported as still truncated. */
    public static final int DEFAULT_MAX_TURNS = 3;
    /** Re-asks allowed for a fragment that restarted the document instead of resuming it. */
    public static final int DEFAULT_MAX_RESTART_RETRIES = 1;
    /** Headroom kept free in the model's context window for token-estimate drift. */
    public static final int CONTEXT_SAFETY_MARGIN_TOKENS = 1024;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** Token estimation is pure arithmetic, so one instance serves every clamp. */
    private static final AiTokenOptimizer SHARED_ESTIMATOR = new AiTokenOptimizer();
    private static final int TAIL_CHARS = 400;
    private static final int STRICT_TAIL_CHARS = 300;
    private static final int RESTART_PROBE_CHARS = 120;
    private static final int RESTART_PROBE_MIN_CHARS = 24;
    private static final int OVERLAP_MIN_CHARS = 16;
    /**
     * How much of the head is compared against the piece's prefix. A model can only replay what it was
     * shown (the last 300–400 characters) plus its own opening, so scanning the full head made the
     * merge O(n·m) per turn — quadratic on a long document.
     */
    private static final int OVERLAP_WINDOW_CHARS = 2048;

    /** What shape the answer has, which decides how "complete" can be judged. */
    public enum DocumentShape {
        /** A single JSON document: complete once its root object parses. */
        JSON,
        /** Free prose/markdown: the only evidence of completeness is the provider saying "stop". */
        PROSE
    }

    /** One model call: the seam that keeps the loop independent of the transport. */
    @FunctionalInterface
    public interface Transport {
        OpenAiClient.Completion call(List<OpenAiClient.ChatMessage> messages, int outputTokenBudget)
                throws IOException, InterruptedException;
    }

    private AiContinuation() {
    }

    /**
     * Appends continuation turns until the answer is complete, the turn budget is spent, or the context
     * window has no room left. The returned {@link OpenAiClient.Completion} carries the whole joined text
     * and the summed token usage of every turn that produced it.
     *
     * @param first       the reply that stopped on the output limit
     * @param outputBudget the budget a single turn may ask for (before the context clamp)
     * @param shape       how completeness is judged
     * @param transport   performs one model call
     * @param continuationLog receives each accepted piece; its size is the number of extra calls made
     */
    public static OpenAiClient.Completion continueUntilComplete(AiConfig config,
                                                                List<OpenAiClient.ChatMessage> baseMessages,
                                                                OpenAiClient.Completion first,
                                                                int outputBudget,
                                                                DocumentShape shape,
                                                                Transport transport,
                                                                List<String> continuationLog) throws IOException, InterruptedException {
        String accumulated = first != null && first.content() != null ? first.content() : "";
        OpenAiClient.Completion current = new OpenAiClient.Completion(accumulated,
                first != null ? first.finishReason() : "length",
                first != null ? first.promptTokens() : -1,
                first != null ? first.completionTokens() : -1);
        int restartRetries = 0;

        for (int turn = 1; turn <= DEFAULT_MAX_TURNS && current.truncated(); turn++) {
            int budget = clampOutputBudget(config, baseMessages, outputBudget, accumulated);
            if (budget <= 0) {
                AiLoggingService.warn("AI-CONTINUE", "Không còn chỗ trong context window để nối tiếp; giữ "
                        + accumulated.length() + " ký tự đã có");
                break;
            }
            List<OpenAiClient.ChatMessage> turnMessages = new ArrayList<>(baseMessages);
            turnMessages.add(new OpenAiClient.ChatMessage("assistant", accumulated));
            turnMessages.add(new OpenAiClient.ChatMessage("user", buildContinuationPrompt(accumulated)));
            AiLoggingService.info("AI-CONTINUE", "Lượt nối tiếp " + turn + "/" + DEFAULT_MAX_TURNS
                    + " sau " + accumulated.length() + " ký tự | max_tokens=" + budget
                    + " | shape=" + shape);

            OpenAiClient.Completion next = transport.call(turnMessages, budget);
            String piece = next.content() != null ? next.content() : "";
            if (piece.isBlank()) {
                break;
            }
            if (isDocumentRestart(accumulated, piece, shape)) {
                if (restartRetries >= DEFAULT_MAX_RESTART_RETRIES) {
                    AiLoggingService.warn("AI-CONTINUE", "Mô hình liên tục viết lại từ đầu; dừng nối tiếp và giữ "
                            + accumulated.length() + " ký tự đã có");
                    break;
                }
                restartRetries++;
                AiLoggingService.warn("AI-CONTINUE", "Phát hiện mảnh viết lại thay vì viết tiếp; giữ nguyên tiền tố "
                        + "và yêu cầu lại lượt " + turn + " với chỉ dẫn mạnh hơn");
                turnMessages.set(turnMessages.size() - 1,
                        new OpenAiClient.ChatMessage("user", buildStrictContinuationPrompt(accumulated)));
                OpenAiClient.Completion retyped = transport.call(turnMessages, budget);
                String retryPiece = retyped.content() != null ? retyped.content() : "";
                if (retryPiece.isBlank() || isDocumentRestart(accumulated, retryPiece, shape)) {
                    break;
                }
                piece = retryPiece;
                next = retyped;
            }

            String joined = mergeContinuation(accumulated, piece);
            if (joined.length() <= accumulated.length()) {
                AiLoggingService.warn("AI-CONTINUE", "Mảnh nối tiếp không thêm nội dung mới; dừng để tránh lặp vô hạn");
                break;
            }
            accumulated = joined;
            continuationLog.add(piece);

            String finish = next.finishReason();
            // A provider may report "length" even when the joined document already closes: the parse decides.
            if (next.truncated() && shape == DocumentShape.JSON && containsCompleteJson(accumulated)) {
                finish = "stop";
            }
            current = new OpenAiClient.Completion(accumulated, finish,
                    sumTokens(current.promptTokens(), next.promptTokens()),
                    sumTokens(current.completionTokens(), next.completionTokens()));
        }
        return current;
    }

    /** Adds two usage figures while preserving -1 as "not reported" on both sides. */
    private static int sumTokens(int running, int next) {
        if (running < 0) {
            return next;
        }
        if (next < 0) {
            return running;
        }
        return running + next;
    }

    /**
     * The output budget a request may actually send: the caller's wish, the model's own ceiling, and the
     * room left in the context window after the prompt and any accumulated text.
     */
    public static int clampOutputBudget(AiConfig config, List<OpenAiClient.ChatMessage> messages,
                                        int wished, String accumulated) {
        return clampOutputBudget(config, messages, wished, accumulated, SHARED_ESTIMATOR);
    }

    /** Same clamp with a caller-supplied estimator, so an injected optimizer keeps its own settings. */
    public static int clampOutputBudget(AiConfig config, List<OpenAiClient.ChatMessage> messages,
                                        int wished, String accumulated, AiTokenOptimizer estimator) {
        AiTokenOptimizer tokens = estimator != null ? estimator : SHARED_ESTIMATOR;
        int contextWindow = config != null && config.getMaxTokens() > 0
                ? config.getMaxTokens()
                : AiTokenOptimizer.DEFAULT_MAX_CONTEXT_TOKENS;
        int promptTokens = 0;
        if (messages != null) {
            for (OpenAiClient.ChatMessage message : messages) {
                promptTokens += tokens.estimateTokens(message.getTextContent()) + 4;
            }
        }
        promptTokens += tokens.estimateTokens(accumulated);
        int room = contextWindow - promptTokens - CONTEXT_SAFETY_MARGIN_TOKENS;
        int ceiling = Math.min(AiModelCapabilities.resolveOutputCeiling(config), Math.max(0, room));
        return Math.max(0, Math.min(wished, ceiling));
    }

    /**
     * Detects a fragment that restarts the document instead of resuming it: it re-opens the JSON fence or
     * root object while the accumulated text is already inside one, or it replays text that already occurred.
     * Concatenating such a fragment would produce two documents.
     */
    public static boolean isDocumentRestart(String accumulated, String piece, DocumentShape shape) {
        if (accumulated == null || accumulated.isBlank() || piece == null || piece.isBlank()) {
            return false;
        }
        if (shape == DocumentShape.JSON) {
            String head = accumulated.stripTrailing();
            String add = piece.strip();
            if (!head.isEmpty()) {
                char last = head.charAt(head.length() - 1);
                boolean insideDocument = last != '}' && last != ']' && last != '`';
                if (insideDocument && (add.startsWith("```") || add.startsWith("{"))) {
                    return true;
                }
            }
            return isReplayOfEarlierText(accumulated, piece);
        }
        // Prose has no delimiters to inspect; only a replayed opening is evidence of a restart.
        return isReplayOfEarlierText(accumulated, piece);
    }

    /** Whether the piece opens with a run of text that already appears earlier in the answer. */
    private static boolean isReplayOfEarlierText(String accumulated, String piece) {
        String add = piece.strip();
        String probe = add.length() > RESTART_PROBE_CHARS ? add.substring(0, RESTART_PROBE_CHARS) : add;
        if (probe.length() < RESTART_PROBE_MIN_CHARS) {
            return false;
        }
        String marker = probe.substring(0, RESTART_PROBE_MIN_CHARS);
        int earlier = accumulated.indexOf(marker);
        return earlier >= 0 && earlier < accumulated.length() - marker.length();
    }

    /**
     * Prompt for the follow-up turn. The instruction is deliberately about the character where the
     * previous reply stopped: anything vaguer makes the model restart, and a restarted fragment cannot be merged.
     */
    public static String buildContinuationPrompt(String accumulated) {
        String text = accumulated != null ? accumulated : "";
        String tail = text.length() > TAIL_CHARS ? text.substring(text.length() - TAIL_CHARS) : text;
        return "[CONTINUATION]: Phản hồi trước bị cắt do giới hạn output tokens. "
                + "Hãy viết TIẾP đúng từ ký tự tiếp theo của nội dung dưới đây, không lặp lại phần đã có, "
                + "không mở lại từ đầu, không thêm lời dẫn hay markdown."
                + "\n\n--- Cuối nội dung đã nhận (" + text.length() + " ký tự) ---\n" + tail
                + "\n--- Hết ---\nTiếp tục ngay từ đây:";
    }

    /**
     * The re-ask sent after a refused restart: it forbids re-opening the document and pins the character
     * that must follow the last one received, which is the part a vaguer instruction loses.
     */
    public static String buildStrictContinuationPrompt(String accumulated) {
        String text = accumulated != null ? accumulated : "";
        String tail = text.length() > STRICT_TAIL_CHARS ? text.substring(text.length() - STRICT_TAIL_CHARS) : text;
        char last = text.isBlank() ? ' ' : text.charAt(text.length() - 1);
        return "[CONTINUATION - STRICT]: KHÔNG được viết lại tài liệu từ đầu. KHÔNG mở lại ```json hay dấu {. "
                + "Ký tự cuối đã nhận là '" + last + "'. Chỉ xuất phần còn thiếu, bắt đầu ngay sau ký tự đó, "
                + "giữ đúng mảng/chuỗi JSON đang dở và đóng lại cho hợp lệ."
                + "\n\n--- " + STRICT_TAIL_CHARS + " ký tự cuối đã nhận ---\n" + tail + "\n--- Hết ---\nTiếp tục:";
    }

    /**
     * Joins a continuation piece to what came before. A model sometimes repeats the trailing fragment it was
     * shown; that overlap is removed by matching the longest suffix of the head against the piece's prefix, so a
     * single line is not duplicated across the join.
     */
    public static String mergeContinuation(String accumulated, String piece) {
        String head = accumulated != null ? accumulated : "";
        String add = piece != null ? piece : "";
        if (head.isBlank()) {
            return add;
        }
        if (add.isBlank()) {
            return head;
        }
        // Only the tail can overlap: the model was shown a short tail, and a longer replay is caught
        // earlier by isDocumentRestart. Bounding this keeps the join linear in the piece length.
        int maxOverlap = Math.min(Math.min(head.length(), add.length()), OVERLAP_WINDOW_CHARS);
        for (int len = maxOverlap; len >= OVERLAP_MIN_CHARS; len--) {
            if (head.regionMatches(head.length() - len, add, 0, len)) {
                return head + add.substring(len);
            }
        }
        return head + add;
    }

    /** Whether the text already holds a balanced root JSON object, i.e. nothing more needs continuing. */
    public static boolean containsCompleteJson(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            MAPPER.readTree(AiTestGeneratorService.extractJsonBlock(text));
            return true;
        } catch (Exception notComplete) {
            return false;
        }
    }
}
