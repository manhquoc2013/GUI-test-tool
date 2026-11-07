package com.studio.service;

import com.studio.model.AiChatMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AiTokenOptimizerTest {

    @Test
    public void testEstimateTokens() {
        AiTokenOptimizer optimizer = new AiTokenOptimizer();

        assertEquals(0, optimizer.estimateTokens(""));
        assertEquals(0, optimizer.estimateTokens(null));

        int engTokens = optimizer.estimateTokens("Hello world, this is a test script.");
        assertTrue(engTokens >= 6 && engTokens <= 15, "Expected reasonable token range for English, got: " + engTokens);

        int vnTokens = optimizer.estimateTokens("Viết kịch bản kiểm thử cho trang đăng nhập với email hợp lệ.");
        assertTrue(vnTokens >= 10 && vnTokens <= 30, "Expected reasonable token range for Vietnamese, got: " + vnTokens);

        int codeTokens = optimizer.estimateTokens("public static void main(String[] args) { page.locator(\"#login\").click(); }");
        assertTrue(codeTokens >= 12, "Code tokens should account for brackets and syntax, got: " + codeTokens);
    }

    @Test
    public void testCompressText() {
        AiTokenOptimizer optimizer = new AiTokenOptimizer();

        String messy = "Line 1\n\n\n\nLine 2      with    many   spaces\n\n\n\nLine 3";
        String compressed = optimizer.compressText(messy, 1000);

        assertFalse(compressed.contains("\n\n\n"), "Excessive blank lines should be collapsed");
        assertFalse(compressed.contains("      "), "Excessive spaces should be collapsed");

        String longText = "a".repeat(200);
        String truncated = optimizer.compressText(longText, 50);
        assertTrue(truncated.length() < 120);
        assertTrue(truncated.contains("[Nội dung đã được lược bớt để tối ưu token]"));
    }

    @Test
    public void testBuildOptimizedMessagesSlidingWindow() {
        AiTokenOptimizer optimizer = new AiTokenOptimizer();

        List<AiChatMessage> history = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            history.add(new AiChatMessage(i % 2 == 1 ? "user" : "assistant", "Message #" + i));
        }

        // Window size: 4 turns (8 messages)
        List<OpenAiClient.ChatMessage> optimized = optimizer.buildOptimizedMessages(
                "System prompt",
                history,
                "Current prompt",
                List.of(),
                "Context info",
                4000,
                4
        );

        // System prompt (1) + Pruned Summary (1) + 8 window messages + Current User Message (1) = 11 messages
        assertNotNull(optimized);
        assertEquals("system", optimized.get(0).getRole());
        assertEquals("System prompt", optimized.get(0).getTextContent());

        // Second message should be summary of pruned turns
        assertTrue(optimized.get(1).getTextContent().contains("Ngữ cảnh tóm tắt"));

        // Last message should be current user prompt
        OpenAiClient.ChatMessage last = optimized.get(optimized.size() - 1);
        assertEquals("user", last.getRole());
        assertTrue(last.getTextContent().contains("Current prompt"));

        // Check that older message #1 is not directly in the list, but message #20 is
        boolean hasMsg1 = optimized.stream().anyMatch(m -> "Message #1".equals(m.getTextContent()));
        boolean hasMsg20 = optimized.stream().anyMatch(m -> "Message #20".equals(m.getTextContent()));
        assertFalse(hasMsg1, "Message #1 should be pruned into summary");
        assertTrue(hasMsg20, "Message #20 should be in sliding window");
    }
}
