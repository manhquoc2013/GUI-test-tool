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

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public OpenAiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(),
             new ObjectMapper());
    }

    public OpenAiClient(HttpClient httpClient, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
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
     * Test connectivity and authentication by pinging the /models endpoint.
     */
    public ConnectionTestResult testConnection(AiConfig config) {
        String apiKey = config.getEffectiveApiKey();
        if (apiKey.isBlank()) {
            return new ConnectionTestResult(false, 0, "Chưa thiết lập OpenAI API Key.");
        }

        String baseUrl = config.getEffectiveBaseUrl();
        String targetUrl = baseUrl + "/models";

        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", "PlaywrightAutomationStudio/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() == 200) {
                return new ConnectionTestResult(true, latency, "Kết nối thành công (" + latency + "ms) tới " + baseUrl);
            } else if (response.statusCode() == 401) {
                return new ConnectionTestResult(false, latency, "Xác thực thất bại (HTTP 401): API Key không chính xác.");
            } else {
                String errorMsg = extractErrorMessage(response.body());
                return new ConnectionTestResult(false, latency, "Lỗi từ server (" + response.statusCode() + "): " + errorMsg);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new ConnectionTestResult(false, latency, "Không thể kết nối đến " + baseUrl + ": " + e.getMessage());
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
                .header("User-Agent", "PlaywrightAutomationStudio/1.0")
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
            throw new IOException("HTTP " + response.statusCode() + ": " + errorMsg);
        }
    }

    /**
     * Call OpenAI Chat Completion API.
     */
    public String chatCompletion(AiConfig config, List<ChatMessage> messages) throws IOException, InterruptedException {
        String apiKey = config.getEffectiveApiKey();
        if (apiKey.isBlank()) {
            throw new IOException("Chưa cấu hình OpenAI API Key. Vui lòng mở Cài đặt để điền API Key hoặc thiết lập biến môi trường OPENAI_API_KEY.");
        }

        String baseUrl = config.getEffectiveBaseUrl();
        String targetUrl = baseUrl + "/chat/completions";

        ObjectNode root = mapper.createObjectNode();
        root.put("model", config.getEffectiveModel());
        root.put("temperature", config.getTemperature());
        root.put("max_tokens", config.getMaxTokens());

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

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", "PlaywrightAutomationStudio/1.0")
                .timeout(Duration.ofMinutes(2))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String err = extractErrorMessage(response.body());
            throw new IOException("OpenAI API Lỗi (" + response.statusCode() + "): " + err);
        }

        JsonNode resJson = mapper.readTree(response.body());
        JsonNode choices = resJson.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode msg = choices.get(0).path("message");
            return msg.path("content").asText("");
        }
        return "";
    }

    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "Không có chi tiết lỗi từ phản hồi.";
        }
        try {
            JsonNode tree = mapper.readTree(responseBody);
            JsonNode error = tree.path("error");
            if (!error.isMissingNode()) {
                String msg = error.path("message").asText();
                if (!msg.isBlank()) return msg;
            }
        } catch (Exception ignored) {
        }
        return responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
    }
}

