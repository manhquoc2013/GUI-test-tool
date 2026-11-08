package com.studio.service;

import com.studio.model.AiChatMessage;
import com.studio.model.AiClarificationQuestion;
import com.studio.model.AiConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiClarificationFlowTest {

    @Test
    void testClarificationQuestionsMessageLifecycle() {
        AiChatMessage msg = new AiChatMessage("assistant", "Cần làm rõ yêu cầu");
        assertFalse(msg.hasClarificationQuestions());
        assertFalse(msg.isClarificationAnswered());

        AiClarificationQuestion q1 = new AiClarificationQuestion("q_scope", "Phạm vi kiểm thử?",
                List.of("Cả Happy path và Negative", "Chỉ Happy path"));
        msg.setClarificationQuestions(List.of(q1));

        assertTrue(msg.hasClarificationQuestions());
        assertEquals(1, msg.getClarificationQuestions().size());

        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("q_scope", "Cả Happy path và Negative");
        msg.setClarificationAnswers(answers);
        msg.setClarificationAnswered(true);

        assertTrue(msg.isClarificationAnswered());
        assertEquals("Cả Happy path và Negative", msg.getClarificationAnswers().get("q_scope"));
    }

    @Test
    void testAugmentedPromptBypassesSubsequentClarification() {
        AiAmbiguityAnalyzer analyzer = new AiAmbiguityAnalyzer();
        String originalPrompt = "viết test giỏ hàng";

        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("q_scope", "Bao quát cả Happy Path, Negative Validation và Biên (BVA)");
        answers.put("q_precondition", "Người dùng đã đăng nhập sẵn");
        answers.put("q_assertions", "Xác minh URL, thông báo Toast và phần tử hiển thị");

        String augmented = analyzer.buildAugmentedPrompt(originalPrompt, answers);
        assertNotNull(augmented);
        assertTrue(augmented.contains("viết test giỏ hàng"));
        assertTrue(augmented.contains("Phạm vi kiểm thử"));
        assertTrue(augmented.contains("Tiền đề & Xác thực"));
        assertTrue(augmented.contains("Tiêu chí kiểm tra"));

        // Passing this augmented prompt back to analyzer should NOT trigger clarification again
        AiAmbiguityAnalyzer.AnalysisResult result = analyzer.analyze(augmented, false, false);
        assertFalse(result.needsClarification());
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_TESTS, result.intent());
    }

    @Test
    void testAiGeneratorReturnsClarificationOnVaguePrompt() throws IOException, InterruptedException {
        AiTestGeneratorService generator = new AiTestGeneratorService();
        AiConfig config = new AiConfig();
        config.setApiKey("test-dummy-key");

        // Short ambiguous prompt without DOM or attachments
        AiTestGeneratorService.GenerationResult result = generator.generateTestCases(
                config,
                "viết test đăng ký",
                List.of(),
                "Nhóm: G1, Suite: S1",
                null,
                List.of(),
                null,
                null,
                null
        );

        assertTrue(result.isClarification());
        assertEquals("CLARIFICATION", result.getIntent());
        assertFalse(result.getQuestions().isEmpty());
        assertTrue(result.getCases().isEmpty());
    }

    @Test
    void testAdvisorChatbotIntentsDirectRouting(@TempDir Path tempDir) throws IOException, InterruptedException {
        // Create sample suite for evaluation
        Path suiteJson = tempDir.resolve("suite.json");
        Files.writeString(suiteJson, """
                {
                  "name": "AuthSuite",
                  "cases": [
                    {
                      "id": "tc1",
                      "name": "Login_Valid",
                      "scriptFile": "Login_Valid.java",
                      "status": "Ready",
                      "assertions": [
                        { "type": "assert_visible", "locator": "#dashboard" }
                      ]
                    }
                  ]
                }
                """);

        AiTestGeneratorService generator = new AiTestGeneratorService();
        AiConfig config = new AiConfig();
        config.setApiKey("test-dummy-key");

        // 1. Search tests
        AiTestGeneratorService.GenerationResult searchRes = generator.generateTestCases(
                config, "tìm test case Login", List.of(), "", null, List.of(), null, null, tempDir
        );
        assertEquals("SEARCH_TESTS", searchRes.getIntent());
        assertFalse(searchRes.isClarification());
        assertTrue(searchRes.getSummary().toLowerCase().contains("kết quả tìm kiếm"));

        // 2. Evaluate test case
        AiTestGeneratorService.GenerationResult evalRes = generator.generateTestCases(
                config, "đánh giá test case Login_Valid", List.of(), "", null, List.of(), null, null, tempDir
        );
        assertEquals("EVALUATE_TEST", evalRes.getIntent());
        assertFalse(evalRes.isClarification());
        assertTrue(evalRes.getSummary().toLowerCase().contains("đánh giá chất lượng test case"));

        // 3. Gap analysis
        AiTestGeneratorService.GenerationResult gapRes = generator.generateTestCases(
                config, "kiểm tra độ bao phủ của suite", List.of(), "", null, List.of(), null, null, tempDir
        );
        assertEquals("GAP_ANALYSIS", gapRes.getIntent());
        assertFalse(gapRes.isClarification());
        assertTrue(gapRes.getSummary().toLowerCase().contains("khoảng trống kiểm thử"));
    }
}
