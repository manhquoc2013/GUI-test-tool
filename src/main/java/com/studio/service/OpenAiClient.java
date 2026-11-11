package com.studio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studio.model.AiConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Native Java HTTP Client for OpenAI API and OpenAI-compatible endpoints.
 * Supports:
 * - Ping / connection verification
 * - Chat completion with text and multimodal/vision input
 */
public class OpenAiClient {

    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper();
    private static volatile HttpClient SHARED_HTTP_CLIENT;

    /** Per-attempt HTTP timeout; transient provider stalls should fail fast and retry, not hang the UI. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);
    private static final int MAX_NETWORK_RETRIES = 2;
    private static final long RETRY_BASE_BACKOFF_MS = 500;
    private static final java.util.Set<Integer> RETRYABLE_STATUS_CODES = java.util.Set.of(
            408, 425, 429, 500, 502, 503, 504);

    private static HttpClient getSharedHttpClient() {
        if (SHARED_HTTP_CLIENT == null) {
            synchronized (OpenAiClient.class) {
                if (SHARED_HTTP_CLIENT == null) {
                    SHARED_HTTP_CLIENT = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(15))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                }
            }
        }
        return SHARED_HTTP_CLIENT;
    }

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public OpenAiClient() {
        this(getSharedHttpClient(), DEFAULT_MAPPER);
    }

    public OpenAiClient(HttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper != null ? mapper : DEFAULT_MAPPER;
    }

    public static class ChatMessage {
        private final String role;
        private final String textContent;
        private final List<String> imageDataUrls;

        public ChatMessage(String role, String textContent) {
            this(role, textContent, List.of());
        }

        public ChatMessage(String role, String textContent, List<String> imageDataUrls) {
            this.role = role;
            this.textContent = textContent != null ? textContent : "";
            this.imageDataUrls = imageDataUrls != null ? imageDataUrls : List.of();
        }

        public String getRole() {
            return role;
        }

        public String getTextContent() {
            return textContent;
        }

        public List<String> getImageDataUrls() {
            return imageDataUrls;
        }
    }

    public static class ConnectionTestResult {
        private final boolean success;
        private final long latencyMs;
        private final String message;

        public ConnectionTestResult(boolean success, long latencyMs, String message) {
            this.success = success;
            this.latencyMs = latencyMs;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public long getLatencyMs() {
            return latencyMs;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Resolve user-friendly provider name from Base URL.
     */
    public static String resolveProviderName(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank())
            return "OpenAI";
        String lower = baseUrl.toLowerCase();
        if (lower.contains("googleapis.com") || lower.contains("gemini"))
            return "Gemini";
        if (lower.contains("openrouter.ai"))
            return "OpenRouter";
        if (lower.contains("deepseek.com"))
            return "DeepSeek";
        if (lower.contains("groq.com"))
            return "Groq";
        if (lower.contains("ollama") || lower.contains("localhost") || lower.contains("127.0.0.1"))
            return "Local/Ollama";
        if (lower.contains("openai.com"))
            return "OpenAI";
        return "AI";
    }

    /**
     * Test connectivity and authentication by pinging the /models endpoint.
     */
    public ConnectionTestResult testConnection(AiConfig config) {
        String baseUrl = config.getEffectiveBaseUrl();
        String provider = resolveProviderName(baseUrl);
        String apiKey = config.getEffectiveApiKey();
        if (apiKey.isBlank()) {
            return new ConnectionTestResult(false, 0, "Chưa thiết lập " + provider + " API Key.");
        }

        String targetUrl = baseUrl + "/models";

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", "TestStudio/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() == 200) {
                return new ConnectionTestResult(true, latency, "Kết nối thành công (" + latency + "ms) tới " + baseUrl);
            } else if (response.statusCode() == 401) {
                return new ConnectionTestResult(false, latency,
                        "Xác thực thất bại (HTTP 401): API Key không chính xác.");
            } else {
                String errorMsg = extractErrorMessage(response.body());
                String safeMsg = com.studio.util.SecuritySanitizer.sanitize("Lỗi từ server (" + response.statusCode() + "): " + errorMsg, apiKey);
                return new ConnectionTestResult(false, latency, safeMsg);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            String safeMsg = com.studio.util.SecuritySanitizer.sanitize("Không thể kết nối đến " + baseUrl + ": " + e.getMessage(), apiKey);
            return new ConnectionTestResult(false, latency, safeMsg);
        }
    }

    /**
     * Fetch list of available model IDs from the endpoint /models.
     */
    public List<String> fetchModels(AiConfig config) throws IOException, InterruptedException {
        String apiKey = config.getEffectiveApiKey();
        String baseUrl = config.getEffectiveBaseUrl();
        String targetUrl = baseUrl + "/models";

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("User-Agent", "TestStudio/1.0")
                .timeout(Duration.ofSeconds(10))
                .GET();
        if (!apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonNode root = mapper.readTree(response.body());
            JsonNode data = root.get("data");
            List<String> models = new ArrayList<>();
            if (data != null && data.isArray()) {
                for (JsonNode node : data) {
                    JsonNode id = node.get("id");
                    if (id != null && !id.asText().isBlank()) {
                        models.add(id.asText());
                    }
                }
            }
            java.util.Collections.sort(models);
            return models;
        } else {
            String errorMsg = extractErrorMessage(response.body());
            String safeMsg = com.studio.util.SecuritySanitizer.sanitize(errorMsg, apiKey);
            throw new IOException("HTTP " + response.statusCode() + ": " + safeMsg);
        }
    }

    /** Hard ceiling for an escalated output budget: most chat models reject anything larger. */
    public static final int MAX_OUTPUT_TOKENS_CEILING = 16384;
    /** Floor used when a reply was cut while no explicit budget was configured. */
    static final int MIN_OUTPUT_TOKENS_FOR_ESCALATION = 4096;
    /** Headroom kept free in the model's context window for token-estimate drift. */
    static final int CONTEXT_SAFETY_MARGIN_TOKENS = 1024;

    /**
     * Default output token budget when no explicit maxOutputTokens is configured.
     * A generated Java script plus four full test cases is the largest single
     * response, hence the larger budget for script generation.
     */
    public static int defaultOutputTokensFor(boolean generateScript) {
        return generateScript ? 8192 : 4096;
    }

    /**
     * One completed model reply plus why it stopped. {@code truncated} is true when the provider
     * stopped on the output token limit — the content may be cut mid-JSON, which must be handled by
     * the caller instead of being treated as a malformed answer.
     *
     * <p>{@code promptTokens}/{@code completionTokens} are the provider's own usage figures. They are
     * -1 when the response carried no usable {@code usage} object, so a caller can always tell a real
     * zero from "not reported" instead of displaying a measured 0 as if it were fact.
     */
    public record Completion(String content, String finishReason, int promptTokens, int completionTokens) {
        /** A reply whose usage was not reported (hand-built in tests, or a provider that omits usage). */
        public Completion(String content, String finishReason) {
            this(content, finishReason, -1, -1);
        }

        public boolean truncated() {
            return "length".equalsIgnoreCase(finishReason);
        }

        public boolean hasUsage() {
            return promptTokens >= 0 || completionTokens >= 0;
        }
    }

    /**
     * Call OpenAI Chat Completion API.
     */
    public String chatCompletion(AiConfig config, List<ChatMessage> messages) throws IOException, InterruptedException {
        return chatCompletion(config, messages, 0);
    }

    /**
     * Same call as {@link #chatCompletion(AiConfig, List)} but also returns the stop reason, so a
     * reply cut off by the output limit can be salvaged or retried rather than dropped.
     */
    public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride)
            throws IOException, InterruptedException {
        return completeChat(config, messages, outputTokenOverride, false);
    }

    /**
     * Same call with an optional JSON-output contract. Providers that reject {@code response_format}
     * are retried once without it, so a compatible endpoint keeps working.
     */
    public Completion completeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride,
                                   boolean jsonMode) throws IOException, InterruptedException {
        return executeChat(config, messages, outputTokenOverride, jsonMode);
    }

    /**
     * Call OpenAI Chat Completion API with an explicit output token budget override.
     * Resolution order: config.maxOutputTokens (>0), then {@code outputTokenOverride} (>0),
     * otherwise max_tokens is omitted (model default).
     */
    public String chatCompletion(AiConfig config, List<ChatMessage> messages, int outputTokenOverride) throws IOException, InterruptedException {
        Completion completion = executeChat(config, messages, outputTokenOverride, false);
        if (completion.truncated()) {
            // The caller asked for a plain string, so it cannot act on the stop reason. Retry once with a
            // larger budget here rather than handing back a half-written script as if it were complete.
            int budget = resolveOutputBudget(config, outputTokenOverride);
            // The budget is bounded by the model's own context window, not by this constant alone: a request whose
            // prompt + max_tokens overflows the window is rejected outright by the provider.
            int contextWindow = config != null && config.getMaxTokens() > 0
                    ? config.getMaxTokens()
                    : 128000;
            int promptChars = 0;
            for (ChatMessage m : messages) {
                promptChars += m.getTextContent() != null ? m.getTextContent().length() : 0;
            }
            int promptEstimate = Math.max(1, promptChars / 3);
            int room = contextWindow - promptEstimate - CONTEXT_SAFETY_MARGIN_TOKENS;
            int escalated = Math.min(MAX_OUTPUT_TOKENS_CEILING,
                    Math.max(budget, MIN_OUTPUT_TOKENS_FOR_ESCALATION) * 2);
            escalated = Math.min(escalated, Math.max(0, room));
            if (escalated > budget) {
                AiLoggingService.warn("AI-RESPONSE", "Phản hồi bị cắt, chạy lại với max_tokens=" + escalated
                        + " (trước: " + (budget > 0 ? String.valueOf(budget) : "mặc định model") + ")");
                Completion retry = executeChat(config, messages, escalated, false);
                if (!retry.content().isBlank() && retry.content().length() >= completion.content().length()) {
                    return retry.content();
                }
            }
        }
        return completion.content();
    }

    /** The output budget a request would actually send, mirroring the resolution in executeChat. */
    static int resolveOutputBudget(AiConfig config, int outputTokenOverride) {
        if (config != null && config.getMaxOutputTokens() > 0) {
            return config.getMaxOutputTokens();
        }
        return Math.max(0, outputTokenOverride);
    }

    private Completion executeChat(AiConfig config, List<ChatMessage> messages, int outputTokenOverride,
                                   boolean jsonMode) throws IOException, InterruptedException {
        String baseUrl = config.getEffectiveBaseUrl();
        String provider = resolveProviderName(baseUrl);
        String apiKey = config.getEffectiveApiKey();
        if (apiKey.isBlank()) {
            throw new IOException("Chưa cấu hình " + provider
                    + " API Key. Vui lòng mở Cài đặt để điền API Key hoặc thiết lập biến môi trường.");
        }

        String targetUrl = baseUrl + "/chat/completions";
        // Output budget is independent from the input context budget (config.maxTokens):
        // explicit maxOutputTokens config > caller override > omit (model default).
        int requestedMaxTokens = config.getMaxOutputTokens();
        if (requestedMaxTokens <= 0) {
            requestedMaxTokens = outputTokenOverride;
        }

        ObjectNode root = buildRequestBody(config, messages, requestedMaxTokens, jsonMode);
        String requestBody = mapper.writeValueAsString(root);
        String maxTokensDesc = requestedMaxTokens > 0 ? String.valueOf(requestedMaxTokens) : "không giới hạn (mặc định model)";
        AiLoggingService.info("AI-REQUEST", "Gửi yêu cầu tới " + config.getEffectiveModel() + " (" + targetUrl
                + ") | max_tokens=" + maxTokensDesc + " | temp=" + config.getTemperature()
                + " | json_mode=" + jsonMode + " | messages=" + messages.size());

        Attempt attempt = sendWithRetry(targetUrl, apiKey, requestBody);
        HttpResponse<String> response = attempt.response();
        long latency = attempt.latencyMs();

        // Some OpenAI-compatible servers reject response_format outright. Losing structured output is
        // better than losing the whole turn, so retry once without it and say so in the log.
        if (jsonMode && (response.statusCode() == 400 || response.statusCode() == 422)
                && looksLikeUnsupportedJsonMode(response.body())) {
            AiLoggingService.warn("AI-REQUEST", provider + " từ chối response_format=json_object; thử lại ở chế độ text");
            root = buildRequestBody(config, messages, requestedMaxTokens, false);
            attempt = sendWithRetry(targetUrl, apiKey, mapper.writeValueAsString(root));
            response = attempt.response();
            latency = attempt.latencyMs();
        }

        if (response.statusCode() != 200) {
            String err = extractErrorMessage(response.body());
            String safeErr = com.studio.util.SecuritySanitizer.sanitize(err, apiKey);
            AiLoggingService.error("AI-RESPONSE", "Lỗi HTTP " + response.statusCode() + ": " + safeErr);
            throw new IOException(provider + " API Lỗi (" + response.statusCode() + "): " + safeErr);
        }

        JsonNode resJson = mapper.readTree(response.body());
        JsonNode choices = resJson.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode choice0 = choices.get(0);
            String finishReason = choice0.path("finish_reason").asText("unknown");
            String content = choice0.path("message").path("content").asText("");

            // Xử lý các mô hình có suy luận (DeepSeek R1/V3/V4, Qwen, v.v.) khi nội dung nằm ở reasoning_content
            if (content.isBlank()) {
                JsonNode reasoningNode = choice0.path("message").path("reasoning_content");
                if (reasoningNode.isMissingNode() || reasoningNode.isNull() || reasoningNode.asText("").isBlank()) {
                    reasoningNode = choice0.path("message").path("reasoning");
                }
                if (!reasoningNode.isMissingNode() && !reasoningNode.isNull()) {
                    String reasoning = reasoningNode.asText("");
                    if (!reasoning.isBlank()) {
                        AiLoggingService.warn("AI-RESPONSE", "Mô hình trả về reasoning_content (" + reasoning.length()
                                + " ký tự) trong khi content chính rỗng. Đang trích xuất nội dung từ reasoning.");
                        content = reasoning;
                    }
                }
            }

            // Token usage: extracted in its own try/catch so a malformed or absent usage
            // object can never fail the call. Only counts are logged — never the API key,
            // prompt body or response body. The counts also travel on the Completion record so the
            // UI can show real usage instead of a heuristic estimate; -1 means "not reported".
            int promptTokens = -1;
            int compTokens = -1;
            try {
                JsonNode usage = resJson.path("usage");
                if (usage.isObject()) {
                    promptTokens = usage.path("prompt_tokens").asInt(-1);
                    compTokens = usage.path("completion_tokens").asInt(-1);
                }
                int totalTokens = usage.path("total_tokens").asInt(0);
                int cachedTokens = usage.path("prompt_tokens_details").path("cached_tokens").asInt(-1);
                String cachedDesc = cachedTokens >= 0 ? String.valueOf(cachedTokens) : "n/a";
                AiLoggingService.info("AI-USAGE",
                        "model=" + config.getEffectiveModel()
                                + " | prompt_tokens=" + promptTokens
                                + " | completion_tokens=" + compTokens
                                + " | cached_tokens=" + cachedDesc
                                + " | total_tokens=" + totalTokens
                                + " | elapsed_ms=" + latency);
            } catch (Exception e) {
                AiLoggingService.debug("AI-USAGE", "Không thể đọc usage từ phản hồi (" + e.getClass().getSimpleName() + ").");
            }

            AiLoggingService.info("AI-RESPONSE", "Thành công HTTP 200 sau " + latency + "ms | finish_reason=" + finishReason
                    + " | prompt_tokens=" + promptTokens + " | completion_tokens=" + compTokens + " | output_chars=" + content.length());

            if ("length".equalsIgnoreCase(finishReason)) {
                AiLoggingService.warn("AI-RESPONSE", "⚠️ Phản hồi bị CẮT NGANG do chạm giới hạn token (finish_reason=length)! Hệ thống sẽ tự động kích hoạt cơ chế vá JSON để phục hồi kịch bản.");
            }
            return new Completion(content, finishReason, promptTokens, compTokens);
        }
        AiLoggingService.warn("AI-RESPONSE", "Phản hồi HTTP 200 nhưng không tìm thấy choices[] hợp lệ.");
        return new Completion("", "missing");
    }

    private ObjectNode buildRequestBody(AiConfig config, List<ChatMessage> messages,
                                        int requestedMaxTokens, boolean jsonMode) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", config.getEffectiveModel());
        root.put("temperature", config.getTemperature());
        if (requestedMaxTokens > 0) {
            root.put("max_tokens", requestedMaxTokens);
        }
        if (jsonMode) {
            root.putObject("response_format").put("type", "json_object");
        }

        ArrayNode messagesNode = root.putArray("messages");
        for (ChatMessage msg : messages) {
            ObjectNode m = messagesNode.addObject();
            m.put("role", msg.getRole());

            if (msg.getImageDataUrls().isEmpty()) {
                m.put("content", msg.getTextContent());
            } else {
                ArrayNode contentArray = m.putArray("content");
                ObjectNode textNode = contentArray.addObject();
                textNode.put("type", "text");
                textNode.put("text", msg.getTextContent());

                for (String imgUrl : msg.getImageDataUrls()) {
                    ObjectNode imgPart = contentArray.addObject();
                    imgPart.put("type", "image_url");
                    ObjectNode urlObj = imgPart.putObject("image_url");
                    urlObj.put("url", imgUrl);
                }
            }
        }
        return root;
    }

    private record Attempt(HttpResponse<String> response, long latencyMs) {
    }

    /**
     * Sends one request, retrying transient transport failures and retryable status codes with linear
     * backoff. A 4xx other than 408/425/429 is returned immediately: retrying a bad request only adds
     * latency without changing the outcome.
     */
    private Attempt sendWithRetry(String targetUrl, String apiKey, String requestBody)
            throws IOException, InterruptedException {
        IOException lastFailure = null;
        long latency = 0;
        for (int attempt = 0; attempt <= MAX_NETWORK_RETRIES; attempt++) {
            if (attempt > 0) {
                long backoff = RETRY_BASE_BACKOFF_MS * attempt;
                AiLoggingService.warn("AI-RETRY", "Thử lại lần " + attempt + "/" + MAX_NETWORK_RETRIES
                        + " sau " + backoff + "ms");
                Thread.sleep(backoff);
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "TestStudio/1.0")
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            long start = System.currentTimeMillis();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                latency = System.currentTimeMillis() - start;
                if (RETRYABLE_STATUS_CODES.contains(response.statusCode()) && attempt < MAX_NETWORK_RETRIES) {
                    AiLoggingService.warn("AI-RETRY", "HTTP " + response.statusCode() + " (tạm thời) sau "
                            + latency + "ms; sẽ thử lại");
                    continue;
                }
                return new Attempt(response, latency);
            } catch (IOException e) {
                lastFailure = e;
                latency = System.currentTimeMillis() - start;
                AiLoggingService.warn("AI-RETRY", "Lỗi transport sau " + latency + "ms: " + e.getMessage());
            }
        }
        throw lastFailure != null ? lastFailure
                : new IOException("Không gửi được yêu cầu tới " + targetUrl + " sau " + MAX_NETWORK_RETRIES + " lần thử");
    }

    private static boolean looksLikeUnsupportedJsonMode(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return false;
        }
        String lower = responseBody.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("response_format")
                || lower.contains("json_object")
                || lower.contains("json mode");
    }

    public static String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "Không có chi tiết lỗi từ phản hồi.";
        }
        try {
            JsonNode tree = DEFAULT_MAPPER.readTree(responseBody);
            // Handle array response (e.g. Google Gemini endpoint: [{ "error": { ... } }])
            if (tree.isArray() && !tree.isEmpty()) {
                tree = tree.get(0);
            }
            JsonNode error = tree.path("error");
            if (!error.isMissingNode()) {
                if (error.isTextual()) {
                    return error.asText();
                }
                String msg = error.path("message").asText();
                if (!msg.isBlank())
                    return msg;
            }
            String directMsg = tree.path("message").asText();
            if (!directMsg.isBlank())
                return directMsg;
        } catch (Exception ignored) {
        }
        String cleaned = responseBody.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 200 ? cleaned.substring(0, 200) + "..." : cleaned;
    }
}
