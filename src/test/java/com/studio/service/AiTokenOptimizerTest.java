package com.studio.service;

import com.studio.model.AiChatMessage;
import com.studio.model.AiGeneratedCase;
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

        // The user's current prompt is the last user message; retained conversation state (if any)
        // follows it as a trailing system note so trimming can never drop it.
        OpenAiClient.ChatMessage lastUser = optimized.stream()
                .filter(m -> "user".equals(m.getRole()))
                .reduce((a, b) -> b)
                .orElseThrow();
        assertTrue(lastUser.getTextContent().contains("Current prompt"));

        // Check that older message #1 is not directly in the list, but message #20 is
        boolean hasMsg1 = optimized.stream().anyMatch(m -> "Message #1".equals(m.getTextContent()));
        boolean hasMsg20 = optimized.stream().anyMatch(m -> "Message #20".equals(m.getTextContent()));
        assertFalse(hasMsg1, "Message #1 should be pruned into summary");
        assertTrue(hasMsg20, "Message #20 should be in sliding window");
    }

    @Test
    public void testFormatDomForPrompt() {
        AiTokenOptimizer optimizer = new AiTokenOptimizer();

        assertEquals("", optimizer.formatDomForPrompt("", 50));
        assertEquals("", optimizer.formatDomForPrompt("[]", 50));

        String domJson = """
            [
              {
                "tag": "button",
                "id": "btn-submit",
                "testId": "login-submit-btn",
                "role": "button",
                "text": "Sign In"
              },
              {
                "tag": "input",
                "name": "username",
                "type": "text",
                "placeholder": "Enter username"
              }
            ]
            """;

        String formatted = optimizer.formatDomForPrompt(domJson, 10);
        assertNotNull(formatted);
        assertTrue(formatted.contains("Live Page Elements"));
        assertTrue(formatted.contains("[data-testid=\"login-submit-btn\"]"));
        assertTrue(formatted.contains("input[name=\"username\"]"));
        assertTrue(formatted.contains("Sign In"));
        assertTrue(formatted.contains("Enter username"));
    }

    @Test
    public void testTruncateHistoricalContent() {
        AiTokenOptimizer optimizer = new AiTokenOptimizer();

        String codeBlock = """
            Here is the test code:
            ```java
            @Test
            public void testLogin() {
                page.navigate("https://example.com");
                page.locator("#username").fill("admin");
                page.locator("#password").fill("secret");
                page.locator("#login").click();
                assertThat(page.locator(".welcome")).isVisible();
                // 50 more lines of code here...
            }
            ```
            Hope this helps!
            """;

        String truncated = optimizer.truncateHistoricalContent(codeBlock, 120);
        assertNotNull(truncated);
        assertTrue(truncated.contains("[Đã thu gọn"));
        assertTrue(truncated.length() < codeBlock.length(), "Code block should be truncated in historical turns");
    }

    @Test
    public void testRollingSummaryGeneration() {
        AiTokenOptimizer optimizer = new AiTokenOptimizer();

        List<AiChatMessage> history = new ArrayList<>();
        AiChatMessage u1 = new AiChatMessage("user", "Hãy tạo kịch bản kiểm thử đăng ký tài khoản");
        AiChatMessage a1 = new AiChatMessage("assistant", "Đã tạo ca kiểm thử: TC01 - Đăng ký thành công");
        a1.setGeneratedCasesSnapshot(List.of(new com.studio.model.AiGeneratedCase("TC01 - Đăng ký thành công", "", "")));

        AiChatMessage u2 = new AiChatMessage("user", "Thêm kiểm tra mật khẩu yếu");
        AiChatMessage a2 = new AiChatMessage("assistant", "Đã tạo ca kiểm thử: TC02 - Kiểm tra mật khẩu yếu");

        history.add(u1);
        history.add(a1);
        history.add(u2);
        history.add(a2);

        String summary = optimizer.generatePrunedSummary(history);
        assertNotNull(summary);
        assertTrue(summary.contains("Yêu cầu"));
        assertTrue(summary.contains("đăng ký"));
    }

    @Test
    public void testLargeContextTokenOptimizationBudgetEnforcement() {
        AiTokenOptimizer optimizer = new AiTokenOptimizer();

        List<AiChatMessage> largeHistory = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            AiChatMessage msg = new AiChatMessage(i % 2 == 0 ? "user" : "assistant",
                    "Message number " + i + " with large payload " + "x".repeat(300));
            largeHistory.add(msg);
        }

        // Enforce strict max tokens budget of 2000 tokens
        int maxTokensBudget = 2000;
        List<OpenAiClient.ChatMessage> messages = optimizer.buildOptimizedMessages(
                "You are an expert QA tester.",
                largeHistory,
                "Write another test case",
                List.of(),
                "Target Suite Info",
                null,
                maxTokensBudget,
                6
        );

        assertNotNull(messages);
        assertTrue(messages.size() > 2);

        // Calculate total estimated tokens of the resulting context
        int totalTokens = 0;
        for (OpenAiClient.ChatMessage m : messages) {
            totalTokens += optimizer.estimateTokens(m.getTextContent()) + 4;
        }

        assertTrue(totalTokens <= maxTokensBudget + 500,
                "Optimized context must strictly respect token budget. Expected <= " + (maxTokensBudget + 500) + ", got: " + totalTokens);
    }

    /**
     * A follow-up turn in the same conversation must still know what earlier turns established:
     * the screens already covered, the generated case names (so nothing is duplicated) and the
     * business group/suite being refined. These facts used to vanish with the sliding window.
     */
    @Test
    public void retainedConversationStateSurvivesTheSlidingWindow() {
        AiTokenOptimizer optimizer = new AiTokenOptimizer();
        List<AiChatMessage> history = new ArrayList<>();

        AiChatMessage first = new AiChatMessage("user",
                "Thiết kế test case cho 2 màn hình Đăng nhập và Giỏ hàng");
        first.setRawPrompt("Thiết kế test case cho 2 màn hình Đăng nhập và Giỏ hàng");
        history.add(first);

        AiChatMessage answer = new AiChatMessage("assistant", "Đã tạo 2 test case.");
        answer.setSuggestedGroup("MuaHang");
        answer.setSuggestedSuite("KiemThu_MuaHang");
        AiGeneratedCase existing = new AiGeneratedCase();
        existing.setName("TC01_DangNhap_HappyPath");
        answer.setGeneratedCasesSnapshot(List.of(existing));
        history.add(answer);

        // Push the first turn well past the sliding window with filler turns.
        for (int i = 0; i < 20; i++) {
            history.add(new AiChatMessage(i % 2 == 0 ? "user" : "assistant", "lượt trung gian #" + i));
        }

        List<OpenAiClient.ChatMessage> messages = optimizer.buildOptimizedMessages(
                "You are an expert QA tester.",
                history,
                "Thêm case cho màn Giỏ hàng",
                List.of(),
                "Suite Info",
                null,
                20000,
                2);

        String retained = messages.stream()
                .filter(m -> "system".equals(m.getRole()))
                .map(OpenAiClient.ChatMessage::getTextContent)
                .reduce("", String::concat);

        assertTrue(retained.contains("MuaHang"), "business group must survive trimming");
        assertTrue(retained.contains("KiemThu_MuaHang"), "suite name must survive trimming");
        assertTrue(retained.contains("TC01_DangNhap_HappyPath"),
                "already-generated case names must survive so new cases do not duplicate them");
        assertTrue(retained.contains("Giỏ hàng"), "screens named in earlier turns must survive trimming");
        assertEquals("system", messages.get(messages.size() - 1).getRole(),
                "retained state must be the last thing the model reads before answering");
    }

    @Test
    public void retainedStateIsEmptyForTheFirstTurn() {
        AiTokenOptimizer optimizer = new AiTokenOptimizer();

        String retained = optimizer.buildRetainedConversationState(List.of(), "Tạo test case", List.of());

        assertEquals("", retained);
    }
}

