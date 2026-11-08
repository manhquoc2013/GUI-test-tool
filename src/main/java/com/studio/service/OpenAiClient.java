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

    /**
     * Call OpenAI Chat Completion API.
     */
    public String chatCompletion(AiConfig config, List<ChatMessage> messages) throws IOException, InterruptedException {
        String baseUrl = config.getEffectiveBaseUrl();
        String provider = resolveProviderName(baseUrl);
        String apiKey = config.getEffectiveApiKey();
        if (apiKey.isBlank()) {
            throw new IOException("Chưa cấu hình " + provider
                    + " API Key. Vui lòng mở Cài đặt để điền API Key hoặc thiết lập biến môi trường.");
        }

        String targetUrl = baseUrl + "/chat/completions";
        int requestedMaxTokens = config.getMaxTokens() > 0 ? config.getMaxTokens() : 4096;

        ObjectNode root = mapper.createObjectNode();
        root.put("model", config.getEffectiveModel());
        root.put("temperature", config.getTemperature());
        root.put("max_tokens", requestedMaxTokens);

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

        String requestBody = mapper.writeValueAsString(root);
        AiLoggingService.info("AI-REQUEST", "Gửi yêu cầu tới " + config.getEffectiveModel() + " (" + targetUrl
                + ") | max_tokens=" + requestedMaxTokens + " | temp=" + config.getTemperature()
                + " | messages=" + messages.size());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", "TestStudio/1.0")
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        long startTime = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long latency = System.currentTimeMillis() - startTime;

        if (response.statusCode() != 200) {
            String err = extractErrorMessage(response.body());
            String safeErr = com.studio.util.SecuritySanitizer.sanitize(err, apiKey);
            AiLoggingService.error("AI-RESPONSE", "Lỗi HTTP " + response.statusCode() + " sau " + latency + "ms: " + safeErr);
            throw new IOException(provider + " API Lỗi (" + response.statusCode() + "): " + safeErr);
        }

        JsonNode resJson = mapper.readTree(response.body());
        JsonNode choices = resJson.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode choice0 = choices.get(0);
            String finishReason = choice0.path("finish_reason").asText("unknown");
            JsonNode usage = resJson.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int compTokens = usage.path("completion_tokens").asInt(0);
            String content = choice0.path("message").path("content").asText("");

            AiLoggingService.info("AI-RESPONSE", "Thành công HTTP 200 sau " + latency + "ms | finish_reason=" + finishReason
                    + " | prompt_tokens=" + promptTokens + " | completion_tokens=" + compTokens + " | output_chars=" + content.length());

            if ("length".equalsIgnoreCase(finishReason)) {
                AiLoggingService.warn("AI-RESPONSE", "⚠️ Phản hồi bị CẮT NGANG do chạm giới hạn token (finish_reason=length)! Hệ thống sẽ tự động kích hoạt cơ chế vá JSON để phục hồi kịch bản.");
            }
            return content;
        }
        AiLoggingService.warn("AI-RESPONSE", "Phản hồi HTTP 200 nhưng không tìm thấy choices[] hợp lệ.");
        return "";
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
