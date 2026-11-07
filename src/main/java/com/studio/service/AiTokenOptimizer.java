package com.studio.service;

import com.studio.model.AiChatMessage;
import com.studio.service.DocumentParserService.ParsedDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service to calculate token estimates, prune history via sliding windows,
 * compress documents, and optimize prompts sent to AI models.
 */
public class AiTokenOptimizer {

    public static final int DEFAULT_MAX_CONTEXT_TOKENS = 4096;
    public static final int DEFAULT_SLIDING_WINDOW_TURNS = 6; // last 6 turns (user+assistant pairs)
    private static final Pattern MULTI_NEWLINES = Pattern.compile("(\\r?\\n){3,}");
    private static final Pattern MULTI_SPACES = Pattern.compile("[ \\t]{3,}");

    /**
     * Heuristic token estimation for text (supports English, Vietnamese diacritics, and Code).
     */
    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int length = text.length();
        int words = 0;
        int specialChars = 0;
        int nonAscii = 0;

        boolean inWord = false;
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c > 127) {
                nonAscii++;
            }
            if (Character.isLetterOrDigit(c)) {
                if (!inWord) {
                    words++;
                    inWord = true;
                }
            } else {
                inWord = false;
                if (c == '{' || c == '}' || c == '[' || c == ']' || c == '(' || c == ')' ||
                    c == ';' || c == '=' || c == '<' || c == '>' || c == '"' || c == '/' || c == '\n') {
                    specialChars++;
                }
            }
        }

        // Base word-based token estimate: 1 word ~ 1.3 tokens in English
        // Non-ASCII (Vietnamese accents): each char adds ~0.5 token overhead
        // Code punctuation: ~1 token per 2 special chars
        double estimate = (words * 1.3) + (nonAscii * 0.4) + (specialChars * 0.5);
        return Math.max(1, (int) Math.round(estimate));
    }

    /**
     * Estimate tokens for an AiChatMessage.
     */
    public int estimateMessageTokens(AiChatMessage msg) {
        if (msg == null) return 0;
        int tokens = 4; // role framing overhead
        if (msg.getContent() != null) {
            tokens += estimateTokens(msg.getContent());
        }
        if (msg.getAttachedFiles() != null && !msg.getAttachedFiles().isEmpty()) {
            tokens += msg.getAttachedFiles().size() * 5;
        }
        return tokens;
    }

    /**
     * Compress document text by collapsing repetitive blank lines and excessive whitespace.
     */
    public String compressText(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String cleaned = MULTI_NEWLINES.matcher(text).replaceAll("\n\n");
        cleaned = MULTI_SPACES.matcher(cleaned).replaceAll("  ").trim();

        if (maxChars > 0 && cleaned.length() > maxChars) {
            cleaned = cleaned.substring(0, maxChars) + "\n... [Nội dung đã được lược bớt để tối ưu token]";
        }
        return cleaned;
    }

    /**
     * Compress a parsed document's content for AI context ingestion.
     */
    public ParsedDocument compressDocument(ParsedDocument doc, int maxChars) {
        if (doc == null) return null;
        if (doc.isImage()) {
            return doc; // Do not alter base64 data URLs for vision
        }
        String compressed = compressText(doc.getContent(), maxChars);
        return new ParsedDocument(doc.getFileName(), doc.getPath(), false, compressed, doc.getMimeType(), compressed.length());
    }

    /**
     * Construct optimized list of OpenAiClient.ChatMessage using sliding window,
     * summarized history, compressed attachments, and token budget enforcement.
     */
    public List<OpenAiClient.ChatMessage> buildOptimizedMessages(
            String systemPrompt,
            List<AiChatMessage> history,
            String currentUserPrompt,
            List<ParsedDocument> attachments,
            String contextInfo,
            int maxTokens,
            int maxTurns) {

        List<OpenAiClient.ChatMessage> result = new ArrayList<>();

        // 1. System Prompt (mandatory)
        result.add(new OpenAiClient.ChatMessage("system", systemPrompt != null ? systemPrompt : ""));
        int usedTokens = estimateTokens(systemPrompt);

        // 2. Prepare Current User Message & Attachments
        StringBuilder userContent = new StringBuilder();
        if (contextInfo != null && !contextInfo.isBlank()) {
            userContent.append("### Ngữ cảnh Suite hiện tại:\n").append(contextInfo.trim()).append("\n\n");
        }

        userContent.append("### Yêu cầu người dùng:\n").append(currentUserPrompt != null ? currentUserPrompt.trim() : "").append("\n\n");

        List<String> imageUrls = new ArrayList<>();
        if (attachments != null && !attachments.isEmpty()) {
            userContent.append("### Tài liệu đính kèm (đã tối ưu):\n");
            for (ParsedDocument doc : attachments) {
                if (doc.isImage()) {
                    imageUrls.add(doc.getContent());
                    userContent.append("- Hình ảnh UI: ").append(doc.getFileName()).append(" (đã đính kèm ảnh)\n");
                } else {
                    ParsedDocument compDoc = compressDocument(doc, 4000);
                    userContent.append("- Tên file: ").append(compDoc.getFileName()).append("\n");
                    userContent.append("```\n").append(compDoc.getContent()).append("\n```\n\n");
                }
            }
        }

        String userMessageText = userContent.toString();
        int currentUserTokens = estimateTokens(userMessageText) + 4;
        usedTokens += currentUserTokens;

        int remainingBudget = Math.max(500, maxTokens - usedTokens);

        // 3. Sliding Window over History
        List<OpenAiClient.ChatMessage> historyMessages = new ArrayList<>();
        if (history != null && !history.isEmpty()) {
            int maxMessages = Math.max(2, maxTurns * 2);
            int startIdx = Math.max(0, history.size() - maxMessages);

            // If we have pruned messages older than the sliding window, create a summary
            if (startIdx > 0) {
                List<AiChatMessage> pruned = history.subList(0, startIdx);
                String summary = generatePrunedSummary(pruned);
                if (!summary.isBlank()) {
                    historyMessages.add(new OpenAiClient.ChatMessage("system", "Ngữ cảnh tóm tắt các lượt trao đổi trước:\n" + summary));
                    usedTokens += estimateTokens(summary);
                }
            }

            // Add the messages in sliding window (backwards check to fit within remaining token budget)
            List<OpenAiClient.ChatMessage> windowMessages = new ArrayList<>();
            for (int i = history.size() - 1; i >= startIdx; i--) {
                AiChatMessage msg = history.get(i);
                int msgTokens = estimateMessageTokens(msg);
                if (usedTokens + msgTokens > maxTokens && !windowMessages.isEmpty()) {
                    break; // Prune if exceeding budget
                }
                usedTokens += msgTokens;
                windowMessages.add(0, new OpenAiClient.ChatMessage(msg.getRole(), msg.getContent()));
            }

            historyMessages.addAll(windowMessages);
        }

        // Insert history messages between system prompt and current user message
        result.addAll(historyMessages);
        result.add(new OpenAiClient.ChatMessage("user", userMessageText, imageUrls));

        return result;
    }

    /**
     * Create a concise summary of pruned messages to retain long-term context while saving tokens.
     */
    public String generatePrunedSummary(List<AiChatMessage> prunedMessages) {
        if (prunedMessages == null || prunedMessages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (AiChatMessage m : prunedMessages) {
            if ("user".equalsIgnoreCase(m.getRole())) {
                String snippet = m.getContent();
                if (snippet.length() > 80) snippet = snippet.substring(0, 80) + "...";
                sb.append("- User: ").append(snippet).append("\n");
                count++;
                if (count >= 5) break;
            }
        }
        return sb.toString().trim();
    }
}
