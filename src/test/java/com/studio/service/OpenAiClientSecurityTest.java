package com.studio.service;

import com.studio.model.AiConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiClientSecurityTest {

    private HttpServer server;
    private int port;
    private OpenAiClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        client = new OpenAiClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void testChatCompletionErrorSanitizesApiKeyInExceptionMessage() {
        String secretKey = "sk-proj-superSecretToken998877665544";
        server.createContext("/chat/completions", exchange -> {
            String errorJson = "{\"error\": {\"message\": \"Invalid auth token: " + secretKey + "\"}}";
            byte[] bytes = errorJson.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        AiConfig config = new AiConfig();
        config.setBaseUrl("http://127.0.0.1:" + port);
        config.setApiKey(secretKey);
        config.setModel("gpt-4o");

        IOException ex = assertThrows(IOException.class, () ->
                client.chatCompletion(config, List.of(new OpenAiClient.ChatMessage("user", "Hello"))));

        assertNotNull(ex.getMessage());
        assertFalse(ex.getMessage().contains("superSecretToken998877665544"),
                "Exception message must not leak raw secret key");
        assertTrue(ex.getMessage().contains("••••"), "Secret key must be masked with bullets");
    }

    @Test
    void testFetchModelsErrorSanitizesApiKeyInExceptionMessage() {
        String secretKey = "sk-proj-modelFetchSecret1234567890";
        server.createContext("/models", exchange -> {
            String errorJson = "{\"error\": {\"message\": \"Unauthorized key: " + secretKey + "\"}}";
            byte[] bytes = errorJson.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(403, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();

        AiConfig config = new AiConfig();
        config.setBaseUrl("http://127.0.0.1:" + port);
        config.setApiKey(secretKey);

        IOException ex = assertThrows(IOException.class, () -> client.fetchModels(config));

        assertNotNull(ex.getMessage());
        assertFalse(ex.getMessage().contains("modelFetchSecret1234567890"),
                "Exception message must not leak raw secret key in fetchModels");
        assertTrue(ex.getMessage().contains("••••"), "Secret key must be masked with bullets");
    }
}
