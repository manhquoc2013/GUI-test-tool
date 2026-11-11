package com.studio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public static final int DEFAULT_MAX_CONTEXT_TOKENS = 32768;
    public static final int DEFAULT_MAX_DOC_CHARS = 48000;
    public static final int DEFAULT_SLIDING_WINDOW_TURNS = 6; // last 6 turns (user+assistant pairs)
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern MULTI_NEWLINES = Pattern.compile("(\\r?\\n){3,}");
    private static final Pattern MULTI_SPACES = Pattern.compile("[ \\t]{3,}");
    private static final Pattern PDF_PAGE_MARKER = Pattern.compile("(?m)^#{1,4}\\s+Trang\\s+\\d+\\s*$\\r?\\n?");
    private static final Pattern SECTION_SPLIT_PATTERN = Pattern.compile("(?m)^(#{1,4}\\s+[^\\r\\n]+|(?:\\*\\*)?(?:Màn\\s+hình|Screen|Chức\\s+năng|Module|Phân\\s+hệ|UC-?\\d+|FR-?\\d+|\\d+\\.\\d+)[^\\r\\n]{2,90})$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```([a-zA-Z0-9_-]*)\\r?\\n([\\s\\S]*?)\\r?\\n```");

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
     * Truncate lengthy code blocks and repetitive output in historical messages to save tokens.
     */
    public String truncateHistoricalContent(String content, int maxChars) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String text = MULTI_NEWLINES.matcher(content).replaceAll("\n\n");

        // Compress large code blocks inside historical turns
        java.util.regex.Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String lang = matcher.group(1);
            String code = matcher.group(2);
            if (code.length() > 140) {
                String[] lines = code.split("\\r?\\n");
                StringBuilder kept = new StringBuilder();
                int linesToKeep = Math.min(3, lines.length);
                for (int i = 0; i < linesToKeep; i++) {
                    kept.append(lines[i]).append("\n");
                }
                String replacement = "```" + lang + "\n" + kept.toString().trim() +
                        "\n    // ... [Đã thu gọn " + (lines.length - linesToKeep) + " dòng mã nguồn cũ để tối ưu context] ...\n```";
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
            } else {
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);
        text = sb.toString();

        if (maxChars > 0 && text.length() > maxChars) {
            text = text.substring(0, maxChars) + "\n... [Đã thu gọn bớt nội dung cũ để tối ưu token]";
        }
        return text;
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
     * Structure-preserving compression for large multi-page specification documents (e.g. 60-page SRS).
     * Strips redundant PDF page-marker headings ("### Trang N") and, when the text exceeds {@code maxChars},
     * preserves a complete document section outline plus balanced slices from every major section/screen
     * instead of blindly truncating at the start of the document.
     */
    public ParsedDocument compressDocumentSmart(ParsedDocument doc, int maxChars) {
        if (doc == null) return null;
        if (doc.isImage()) {
            return doc;
        }
        String raw = doc.getContent();
        if (raw == null || raw.isBlank()) {
            return new ParsedDocument(doc.getFileName(), doc.getPath(), false, "", doc.getMimeType(), 0);
        }

        // Normalize PDF page break markers and excessive whitespace
        String cleaned = PDF_PAGE_MARKER.matcher(raw).replaceAll("");
        cleaned = MULTI_NEWLINES.matcher(cleaned).replaceAll("\n\n");
        cleaned = MULTI_SPACES.matcher(cleaned).replaceAll("  ").trim();

        int effectiveMax = maxChars > 0 ? maxChars : DEFAULT_MAX_DOC_CHARS;
        if (cleaned.length() <= effectiveMax) {
            return new ParsedDocument(doc.getFileName(), doc.getPath(), false, cleaned, doc.getMimeType(), cleaned.length());
        }

        // Split by structural headings / screen markers to preserve every screen across a large SRS
        java.util.regex.Matcher m = SECTION_SPLIT_PATTERN.matcher(cleaned);
        List<Integer> starts = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
            titles.add(m.group(1).replaceAll("^#+\\s*", "").trim());
        }

        if (starts.size() <= 1) {
            String truncated = cleaned.substring(0, effectiveMax) + "\n... [Nội dung đã được lược bớt để tối ưu token]";
            logDocTruncation(doc, raw.length(), truncated.length());
            return new ParsedDocument(doc.getFileName(), doc.getPath(), false, truncated, doc.getMimeType(), truncated.length());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("### Cấu trúc Mục lục Tài liệu (").append(starts.size()).append(" phần/màn hình):\n");
        for (int i = 0; i < Math.min(starts.size(), 60); i++) {
            sb.append("- ").append(titles.get(i)).append("\n");
        }
        sb.append("\n");

        int remainingBudget = Math.max(2000, effectiveMax - sb.length());
        int perSectionBudget = Math.max(350, remainingBudget / starts.size());

        // Include preamble if present
        if (starts.get(0) > 0) {
            String preamble = cleaned.substring(0, Math.min(starts.get(0), Math.min(800, perSectionBudget))).trim();
            if (!preamble.isEmpty()) {
                sb.append(preamble).append("\n\n");
            }
        }

        for (int i = 0; i < starts.size(); i++) {
            int start = starts.get(i);
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : cleaned.length();
            String sec = cleaned.substring(start, end).trim();
            if (sec.length() > perSectionBudget) {
                sec = sec.substring(0, perSectionBudget) + "\n... [Lược bớt chi tiết phụ trong mục này]";
            }
            sb.append(sec).append("\n\n");
            if (sb.length() >= effectiveMax) {
                break;
            }
        }

        String result = sb.toString().trim();
        logDocTruncation(doc, raw.length(), result.length());
        return new ParsedDocument(doc.getFileName(), doc.getPath(), false, result, doc.getMimeType(), result.length());
    }

    /**
     * Logs the character loss when a document is truncated/compressed, so truncation is observable
     * instead of silent. Includes the file name, requested vs delivered characters and percentage kept.
     */
    private static void logDocTruncation(ParsedDocument doc, int charsRequested, int charsDelivered) {
        if (charsDelivered >= charsRequested) {
            return;
        }
        double pctKept = charsRequested > 0 ? (charsDelivered * 100.0 / charsRequested) : 0.0;
        AiLoggingService.warn("DOC-TRUNC",
                "file=" + doc.getFileName()
                        + ", charsRequested=" + charsRequested
                        + ", charsDelivered=" + charsDelivered
                        + ", pctKept=" + Math.round(pctKept) + "%");
    }

    /**
     * Format reduced DOM JSON into a compact Markdown element table for AI ingestion (70% token savings).
     * Inspired by Midscene.js and Skyvern compact DOM representations.
     */
    public String formatDomForPrompt(String domJson, int maxElements) {
        if (domJson == null || domJson.isBlank()) {
            return "";
        }
        int limit = maxElements > 0 ? maxElements : 50;
        try {
            JsonNode root = MAPPER.readTree(domJson);
            if (!root.isArray() || root.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("### Giao diện DOM tương tác thực tế (Live Page Elements):\n");
            sb.append("| # | Tag | Candidate Locator | Role/Type | Text/Placeholder/Label |\n");
            sb.append("|---|---|---|---|---|\n");
            int count = 0;
            for (JsonNode node : root) {
                if (count >= limit) break;
                String tag = node.path("tag").asText("");
                String id = node.path("id").asText("");
                String testId = node.path("testId").asText("");
                String name = node.path("name").asText("");
                String role = node.path("role").asText("");
                String type = node.path("type").asText("");
                String text = node.path("text").asText("");
                String aria = node.path("ariaLabel").asText("");
                String placeholder = node.path("placeholder").asText("");

                String locator;
                if (!testId.isBlank()) {
                    locator = "[data-testid=\"" + testId + "\"]";
                } else if (!id.isBlank() && !id.equals("null")) {
                    locator = "#" + id;
                } else if (!name.isBlank() && !name.equals("null")) {
                    locator = tag + "[name=\"" + name + "\"]";
                } else if (!aria.isBlank() && !aria.equals("null")) {
                    locator = "[aria-label=\"" + aria + "\"]";
                } else if (!role.isBlank() && !role.equals("null") && !text.isBlank()) {
                    locator = "role=" + role + "[name=\"" + text + "\"]";
                } else if (!text.isBlank()) {
                    locator = tag + ":has-text(\"" + text + "\")";
                } else {
                    locator = tag;
                }

                String roleType = !role.isBlank() && !role.equals("null") ? role : (!type.isBlank() ? type : "-");
                String desc = !text.isBlank() ? text : (!placeholder.isBlank() ? "ph: " + placeholder : (!aria.isBlank() ? aria : "-"));
                if (desc.length() > 40) {
                    desc = desc.substring(0, 37) + "...";
                }

                sb.append(String.format("| %d | %s | `%s` | %s | %s |\n", count + 1, tag, locator, roleType, desc));
                count++;
            }
            return sb.toString();
        } catch (Exception e) {
            return compressText(domJson, 2000);
        }
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
        return buildOptimizedMessages(systemPrompt, history, currentUserPrompt, attachments, contextInfo, null, maxTokens, maxTurns);
    }

    /**
     * Construct optimized list with Live DOM context injection.
     */
    public List<OpenAiClient.ChatMessage> buildOptimizedMessages(
            String systemPrompt,
            List<AiChatMessage> history,
            String currentUserPrompt,
            List<ParsedDocument> attachments,
            String contextInfo,
            String domContext,
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

        if (domContext != null && !domContext.isBlank()) {
            userContent.append(domContext.trim()).append("\n\n");
        }

        userContent.append("### Yêu cầu người dùng:\n").append(currentUserPrompt != null ? currentUserPrompt.trim() : "").append("\n\n");

        int contextLimit = maxTokens > 0 ? maxTokens : DEFAULT_MAX_CONTEXT_TOKENS;

        List<String> imageUrls = new ArrayList<>();
        if (attachments != null && !attachments.isEmpty()) {
            List<ParsedDocument> textDocs = attachments.stream()
                    .filter(d -> d != null && !d.isImage())
                    .toList();
            int textDocCount = textDocs.size();
            int totalDocCharBudget = Math.max(8000, Math.min(96000, contextLimit * 2));

            // AG-9: two-pass water-filling document allowance instead of an equal split. A document
            // shorter than its fair share only takes its own length, freeing the remainder for long
            // documents; the total granted never exceeds totalDocCharBudget and no document is ever
            // granted more than its own content length.
            int[] docAllowances = new int[textDocCount];
            if (textDocCount > 0) {
                int fairShare = totalDocCharBudget / textDocCount;
                long granted = 0;
                // Pass 1: every document gets min(contentLength, fairShare).
                for (int i = 0; i < textDocCount; i++) {
                    int len = textDocs.get(i).getContent() != null ? textDocs.get(i).getContent().length() : 0;
                    docAllowances[i] = Math.min(len, fairShare);
                    granted += docAllowances[i];
                }
                // Pass 2: redistribute the unused remainder equally to documents still short of their
                // full length, until the budget is exhausted or every document is fully covered.
                long remaining = totalDocCharBudget - granted;
                while (remaining > 0) {
                    int stillShort = 0;
                    for (int i = 0; i < textDocCount; i++) {
                        int len = textDocs.get(i).getContent() != null ? textDocs.get(i).getContent().length() : 0;
                        if (docAllowances[i] < len) {
                            stillShort++;
                        }
                    }
                    if (stillShort == 0) {
                        break;
                    }
                    int share = (int) Math.max(1, remaining / stillShort);
                    boolean progressed = false;
                    for (int i = 0; i < textDocCount; i++) {
                        int len = textDocs.get(i).getContent() != null ? textDocs.get(i).getContent().length() : 0;
                        if (docAllowances[i] < len) {
                            int grant = (int) Math.min(Math.min(len - docAllowances[i], share), remaining);
                            if (grant > 0) {
                                docAllowances[i] += grant;
                                remaining -= grant;
                                progressed = true;
                            }
                        }
                    }
                    if (!progressed) {
                        break;
                    }
                }
            }

            userContent.append("### Tài liệu đính kèm (đã tối ưu):\n");
            int textIdx = 0;
            for (ParsedDocument doc : attachments) {
                if (doc == null) continue;
                if (doc.isImage()) {
                    imageUrls.add(doc.getContent());
                    userContent.append("- Hình ảnh UI: ").append(doc.getFileName()).append(" (đã đính kèm ảnh)\n");
                } else {
                    int allowance = DEFAULT_MAX_DOC_CHARS;
                    if (textDocCount > 0) {
                        allowance = docAllowances[textIdx++];
                    }
                    ParsedDocument compDoc = compressDocumentSmart(doc, allowance);
                    userContent.append("- Tên file: ").append(compDoc.getFileName()).append("\n");
                    userContent.append("```\n").append(compDoc.getContent()).append("\n```\n\n");
                }
            }
        }

        String userMessageText = userContent.toString();
        int currentUserTokens = estimateTokens(userMessageText) + 4;
        usedTokens += currentUserTokens;

        // 3. Sliding Window over History
        List<OpenAiClient.ChatMessage> historyMessages = new ArrayList<>();
        if (history != null && !history.isEmpty()) {
            int maxMessages = Math.max(2, maxTurns * 2);
            int startIdx = Math.max(0, history.size() - maxMessages);

            // If we have pruned messages older than the sliding window, create progressive summary
            if (startIdx > 0) {
                List<AiChatMessage> pruned = history.subList(0, startIdx);
                String summary = generatePrunedSummary(pruned);
                if (!summary.isBlank()) {
                    historyMessages.add(new OpenAiClient.ChatMessage("system", summary));
                    usedTokens += estimateTokens(summary);
                }
            }

            // Add the messages in sliding window (backwards check to fit within remaining token budget)
            List<OpenAiClient.ChatMessage> windowMessages = new ArrayList<>();
            for (int i = history.size() - 1; i >= startIdx; i--) {
                AiChatMessage msg = history.get(i);
                String content = msg.getContent() != null ? msg.getContent() : "";

                // For historical messages older than the immediate last turn, truncate bulky code & outputs
                if (i < history.size() - 2) {
                    content = truncateHistoricalContent(content, 2000);
                }

                int msgTokens = estimateTokens(content) + 4;
                if (usedTokens + msgTokens > contextLimit && !windowMessages.isEmpty()) {
                    break; // Prune if exceeding budget
                }
                usedTokens += msgTokens;
                windowMessages.add(0, new OpenAiClient.ChatMessage(msg.getRole(), content));
            }

            historyMessages.addAll(windowMessages);
        }

        // Insert history messages between system prompt and current user message
        result.addAll(historyMessages);
        result.add(new OpenAiClient.ChatMessage("user", userMessageText, imageUrls));

        return result;
    }

    /**
     * Create a structured, concise summary of pruned messages to retain long-term context while saving tokens.
     */
    public String generatePrunedSummary(List<AiChatMessage> prunedMessages) {
        if (prunedMessages == null || prunedMessages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Ngữ cảnh tóm tắt các lượt trao đổi trước:\n");

        List<String> userRequests = new ArrayList<>();
        List<String> generatedCases = new ArrayList<>();
        int countUser = 0;

        for (AiChatMessage m : prunedMessages) {
            if ("user".equalsIgnoreCase(m.getRole())) {
                String snippet = m.getContent() != null ? m.getContent().trim() : "";
                if (!snippet.isBlank()) {
                    if (snippet.length() > 100) snippet = snippet.substring(0, 97) + "...";
                    userRequests.add(snippet);
                    countUser++;
                    if (countUser >= 6) break;
                }
            } else if ("assistant".equalsIgnoreCase(m.getRole())) {
                if (m.getGeneratedCasesSnapshot() != null && !m.getGeneratedCasesSnapshot().isEmpty()) {
                    for (com.studio.model.AiGeneratedCase gc : m.getGeneratedCasesSnapshot()) {
                        if (gc.getName() != null && !gc.getName().isBlank()) {
                            generatedCases.add(gc.getName().trim());
                        }
                    }
                } else if (m.getContent() != null && (m.getContent().contains("TC") || m.getContent().contains("Ca kiểm thử"))) {
                    for (String line : m.getContent().split("\\r?\\n")) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("TC") || trimmed.contains("Ca kiểm thử") || trimmed.startsWith("- TC")) {
                            if (trimmed.length() > 80) trimmed = trimmed.substring(0, 77) + "...";
                            generatedCases.add(trimmed);
                            if (generatedCases.size() >= 5) break;
                        }
                    }
                }
            }
        }

        if (!userRequests.isEmpty()) {
            sb.append("• Yêu cầu & Mục tiêu:\n");
            for (String req : userRequests) {
                sb.append("  - ").append(req).append("\n");
            }
        }

        if (!generatedCases.isEmpty()) {
            sb.append("• Ca kiểm thử / Kết quả đã sinh:\n");
            for (String c : generatedCases) {
                sb.append("  - ").append(c).append("\n");
            }
        }

        return sb.toString().trim();
    }
}
