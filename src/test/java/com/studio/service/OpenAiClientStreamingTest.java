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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A long answer sent as one body can be cut by a proxy or time out. The client now prefers SSE and
 * accumulates the whole stream, so these tests pin that contract against a real local HTTP server:
 * the full text arrives, the provider's {@code finish_reason} survives, and an endpoint without SSE
 * still works through the single-body fallback.
 */
class OpenAiClientStreamingTest {

    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String SSE_CONTENT_TYPE = "text/event-stream; charset=utf-8";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String ANSWER = "Dòng 1 của báo cáo rất dài.\nDòng 2 của báo cáo rất dài.";

    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private AiConfig config() {
        AiConfig config = new AiConfig();
        config.setBaseUrl("http://127.0.0.1:" + port);
        config.setApiKey("sk-stream-test-key");
        config.setModel("gpt-4o");
        config.setMaxOutputTokens(4096);
        return config;
    }

    private List<OpenAiClient.ChatMessage> messages() {
        return List.of(new OpenAiClient.ChatMessage("user", "Viết báo cáo dài"));
    }

    private void respond(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add(CONTENT_TYPE_HEADER, contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void wholeStreamIsAccumulatedAndFinishReasonIsKept() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/chat/completions", exchange -> {
            requests.incrementAndGet();
            assertTrue(readRequestBody(exchange).contains("\"stream\":true"),
                    "the request must ask for SSE");
            exchange.getResponseHeaders().add(CONTENT_TYPE_HEADER, SSE_CONTENT_TYPE);
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                write(out, "data: {\"choices\":[{\"delta\":{\"content\":\"Dòng 1 của báo cáo \"}}]}\n\n");
                write(out, ": heartbeat\n\n");
                write(out, "data: {\"choices\":[{\"delta\":{\"content\":\"rất dài.\"}}]}\n\n");
                write(out, "data: {\"choices\":[{\"delta\":{\"content\":\"\\nDòng 2 của báo cáo rất dài.\"}}]}\n\n");
                write(out, "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":42,\"completion_tokens\":7}}\n\n");
                write(out, "data: [DONE]\n\n");
            }
        });
        server.start();

        OpenAiClient.Completion reply = new OpenAiClient()
                .completeChat(config(), messages(), OpenAiClient.defaultOutputTokensFor(false));

        assertEquals(ANSWER, reply.content(), "every delta must be joined into the full answer");
        assertEquals("stop", reply.finishReason());
        assertFalse(reply.truncated());
        assertEquals(42, reply.promptTokens(), "usage from the final SSE frame must be reported");
        assertEquals(7, reply.completionTokens());
        assertEquals(1, requests.get(), "a healthy stream needs exactly one request");
    }

    @Test
    void streamCutBeforeDoneKeepsWhatArrivedAndReportsTruncation() throws Exception {
        server.createContext("/chat/completions", exchange -> {
            exchange.getResponseHeaders().add(CONTENT_TYPE_HEADER, SSE_CONTENT_TYPE);
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream out = exchange.getResponseBody()) {
                write(out, "data: {\"choices\":[{\"delta\":{\"content\":\"Nửa đầu báo cáo\"}}]}\n\n");
                // The connection closes here: no finish_reason, no [DONE].
            }
        });
        server.start();

        OpenAiClient.Completion reply = new OpenAiClient()
                .completeChat(config(), messages(), OpenAiClient.defaultOutputTokensFor(false));

        assertEquals("Nửa đầu báo cáo", reply.content(), "received tokens must never be discarded");
        assertEquals("length", reply.finishReason(),
                "a stream that ends without [DONE] may be incomplete and must say so");
        assertTrue(reply.truncated());
    }

    @Test
    void endpointWithoutSseFallsBackToASingleBodyRequest() throws Exception {
        AtomicInteger streamAttempts = new AtomicInteger();
        AtomicInteger plainAttempts = new AtomicInteger();
        server.createContext("/chat/completions", exchange -> {
            String body = readRequestBody(exchange);
            if (body.contains("\"stream\":true")) {
                streamAttempts.incrementAndGet();
                respond(exchange, 400, JSON_CONTENT_TYPE,
                        "{\"error\":{\"message\":\"unknown parameter: stream\"}}");
                return;
            }
            plainAttempts.incrementAndGet();
            respond(exchange, 200, JSON_CONTENT_TYPE,
                    "{\"choices\":[{\"message\":{\"content\":\"" + ANSWER.replace("\n", "\\n")
                            + "\"},\"finish_reason\":\"stop\"}]}");
        });
        server.start();

        OpenAiClient.Completion reply = new OpenAiClient()
                .completeChat(config(), messages(), OpenAiClient.defaultOutputTokensFor(false));

        assertEquals(ANSWER, reply.content(), "the fallback must return the complete body");
        assertEquals("stop", reply.finishReason());
        assertEquals(1, streamAttempts.get(), "stream support is probed once");
        assertEquals(1, plainAttempts.get(), "exactly one single-body retry");
    }

    @Test
    void serverThatIgnoresStreamFlagIsParsedAsAPlainBody() throws Exception {
        server.createContext("/chat/completions", exchange -> {
            readRequestBody(exchange);
            respond(exchange, 200, JSON_CONTENT_TYPE,
                    "{\"choices\":[{\"message\":{\"content\":\"Kết quả nguyên khối\"},"
                            + "\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":3}}");
        });
        server.start();

        OpenAiClient.Completion reply = new OpenAiClient()
                .completeChat(config(), messages(), OpenAiClient.defaultOutputTokensFor(false));

        assertEquals("Kết quả nguyên khối", reply.content());
        assertEquals("stop", reply.finishReason());
        assertEquals(5, reply.promptTokens());
    }

    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
