package com.studio.service;

import com.studio.model.AiConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The escalated budget used to be capped by one constant, so a model that supports more was left cut
 * and a small endpoint was over-asked. The ceiling now follows the model (or the user's override), and
 * this test observes the {@code max_tokens} actually sent on the retry.
 */
class OpenAiClientOutputCeilingTest {

    private static final Pattern MAX_TOKENS = Pattern.compile("\"max_tokens\"\\s*:\\s*(\\d+)");

    private HttpServer server;
    private int port;
    private final List<Integer> sentBudgets = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/chat/completions", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** Always cut on the output limit; records the requested budget of every call. */
    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Matcher matcher = MAX_TOKENS.matcher(body);
        if (matcher.find()) {
            sentBudgets.add(Integer.parseInt(matcher.group(1)));
        }
        String json = "{\"choices\":[{\"message\":{\"content\":\"{\\\"cases\\\":[\"},"
                + "\"finish_reason\":\"length\"}]}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private AiConfig config() {
        AiConfig config = new AiConfig();
        config.setBaseUrl("http://127.0.0.1:" + port);
        config.setApiKey("sk-ceiling-test-key");
        config.setMaxTokens(200_000);
        return config;
    }

    @Test
    void retryBudgetFollowsTheModelCapability() throws Exception {
        AiConfig config = config();
        config.setModel("gpt-4.1"); // ceiling 32768

        new OpenAiClient().chatCompletion(config, messages(), 8192);

        assertTrue(sentBudgets.size() >= 2, "a cut reply must be retried with a bigger budget");
        assertEquals(16384, sentBudgets.get(1),
                "the retry doubles the budget but never exceeds the model's own ceiling");
    }

    @Test
    void manualCeilingCapsTheRetryForAnUntabulatedEndpoint() throws Exception {
        AiConfig config = config();
        config.setModel("my-private-endpoint"); // unknown → default ceiling
        config.setOutputTokenCeiling(12000);

        new OpenAiClient().chatCompletion(config, messages(), 8192);

        assertTrue(sentBudgets.size() >= 2);
        assertEquals(12000, sentBudgets.get(1),
                "the user's ceiling is what the endpoint can actually accept");
    }

    private static List<OpenAiClient.ChatMessage> messages() {
        return List.of(new OpenAiClient.ChatMessage("user", "Sinh test case"));
    }
}
