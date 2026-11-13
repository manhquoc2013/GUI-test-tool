package com.studio.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A proxy can accept the connection and then reset the HTTP/2 stream. That failure costs a full
 * timeout/retry today, so it must be recognised and demoted to HTTP/1.1 exactly once per host.
 */
class OpenAiClientHttp2FallbackTest {

    @Test
    void rstStreamIsRecognisedAsAProtocolProblem() {
        assertTrue(OpenAiClient.isHttp2ProtocolError(
                new IOException("Received RST_STREAM: Protocol error")));
        assertTrue(OpenAiClient.isHttp2ProtocolError(new IOException("stream reset by peer")));
        assertTrue(OpenAiClient.isHttp2ProtocolError(new IOException("REFUSED_STREAM")));
        assertTrue(OpenAiClient.isHttp2ProtocolError(new IOException("HTTP/2 protocol error")));
    }

    @Test
    void aRealNetworkFailureDoesNotDemoteTheHost() {
        assertFalse(OpenAiClient.isHttp2ProtocolError(new IOException("Connection timed out")));
        assertFalse(OpenAiClient.isHttp2ProtocolError(new IOException((String) null)));
        assertFalse(OpenAiClient.isHttp2ProtocolError(null));
        assertFalse(OpenAiClient.switchToHttp11("https://unrelated.example/v1", new IOException("timeout")));
    }

    @Test
    void switchingHappensOncePerHost() {
        String host = "https://rst-once.example/v1";
        IOException reset = new IOException("Received RST_STREAM: Protocol error");

        assertTrue(OpenAiClient.switchToHttp11(host, reset), "the first reset must trigger one retry");
        assertFalse(OpenAiClient.switchToHttp11(host, reset),
                "a later failure must not keep retrying for a decision already made");
    }

    @Test
    void hostParsingNeverThrows() {
        assertEquals("api.example.com", OpenAiClient.hostOf("https://api.example.com/v1"));
        assertEquals("", OpenAiClient.hostOf("not a url"));
        assertEquals("", OpenAiClient.hostOf(null));
    }
}
