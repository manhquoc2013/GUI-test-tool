package com.studio.service;

import com.studio.model.AiClarificationQuestion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AiAmbiguityAnalyzerTest {

    private AiAmbiguityAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new AiAmbiguityAnalyzer();
    }

    @Test
    void testDetectIntent() {
        assertEquals(AiAmbiguityAnalyzer.Intent.SEARCH_TESTS,
                analyzer.detectIntent("tìm test case đăng nhập"));
        assertEquals(AiAmbiguityAnalyzer.Intent.SEARCH_TESTS,
                analyzer.detectIntent("danh sách case trong suite auth"));

        assertEquals(AiAmbiguityAnalyzer.Intent.EVALUATE_TEST,
                analyzer.detectIntent("đánh giá test case TC01_Login"));
        assertEquals(AiAmbiguityAnalyzer.Intent.EVALUATE_TEST,
                analyzer.detectIntent("chấm điểm case Repo_Navigate"));

        assertEquals(AiAmbiguityAnalyzer.Intent.GAP_ANALYSIS,
                analyzer.detectIntent("suite này còn thiếu test case gì?"));
        assertEquals(AiAmbiguityAnalyzer.Intent.GAP_ANALYSIS,
                analyzer.detectIntent("gợi ý thêm case cho suite hiện tại"));

        assertEquals(AiAmbiguityAnalyzer.Intent.CHAT_ADVICE,
                analyzer.detectIntent("làm sao để chờ element xuất hiện trong playwright?"));

        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_TESTS,
                analyzer.detectIntent("viết test case đăng nhập"));

        // Scenario-only intent
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_SCENARIOS,
                analyzer.detectIntent("tạo test scenario cho chức năng đăng nhập"));
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_SCENARIOS,
                analyzer.detectIntent("kịch bản kiểm thử cho màn hình sản phẩm"));
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_SCENARIOS,
                analyzer.detectIntent("sinh test scenarios từ tài liệu"));
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_SCENARIOS,
                analyzer.detectIntent("tạo test scenrio cho 10 màn hình"));
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_SCENARIOS,
                analyzer.detectIntent("chỉ tạo kịch bản kiểm thử"));

        // Both Test Scenarios and Test Scripts / Automation Code intent
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_BOTH,
                analyzer.detectIntent("tạo cả test scenario và test case"));
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_BOTH,
                analyzer.detectIntent("tạo test scenario và test script"));
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_BOTH,
                analyzer.detectIntent("tạo kịch bản và sinh test script"));
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_BOTH,
                analyzer.detectIntent("kịch bản kiểm thử và viết code test automation"));
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_BOTH,
                analyzer.detectIntent("yêu cầu làm cả 2 kịch bản và script"));

        // Test Cases & Test Scripts / Automation Code intent (without scenario keyword)
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_TESTS,
                analyzer.detectIntent("tạo test case cho chức năng đăng nhập"));
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_TESTS,
                analyzer.detectIntent("tạo trường hợp kiểm thử cho tài liệu"));
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_TESTS,
                analyzer.detectIntent("viết test script cho form login"));
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_TESTS,
                analyzer.detectIntent("viết code test automation"));
    }

    @Test
    void testRequestsScriptOrCaseGeneration_FollowsExplicitWordingOnly() {
        // The three phrasings a user types when they want automation output.
        assertTrue(AiAmbiguityAnalyzer.requestsScriptOrCaseGeneration("tạo test script cho form login"));
        assertTrue(AiAmbiguityAnalyzer.requestsScriptOrCaseGeneration("tạo test case cho chức năng đăng nhập"));
        assertTrue(AiAmbiguityAnalyzer.requestsScriptOrCaseGeneration("sinh trường hợp kiểm thử từ tài liệu"));
        assertTrue(AiAmbiguityAnalyzer.requestsScriptOrCaseGeneration("viết code test automation"));
        assertTrue(AiAmbiguityAnalyzer.requestsScriptOrCaseGeneration("TEST CASE cho giỏ hàng"));

        // A plain URL is not a request for a script.
        assertFalse(AiAmbiguityAnalyzer.requestsScriptOrCaseGeneration("kiểm thử https://example.com/login"));
        assertFalse(AiAmbiguityAnalyzer.requestsScriptOrCaseGeneration("đăng nhập với tài khoản admin"));
        // Scenario-only wording produces a specification, not Playwright code.
        assertFalse(AiAmbiguityAnalyzer.requestsScriptOrCaseGeneration("tạo kịch bản kiểm thử cho màn hình sản phẩm"));
        assertFalse(AiAmbiguityAnalyzer.requestsScriptOrCaseGeneration("chỉ tạo test scenario"));
        assertFalse(AiAmbiguityAnalyzer.requestsScriptOrCaseGeneration(null));
        assertFalse(AiAmbiguityAnalyzer.requestsScriptOrCaseGeneration("   "));
    }

    @Test
    void testVaguePrompt_TriggersClarification() {
        AiAmbiguityAnalyzer.AnalysisResult result = analyzer.analyze("viết test đăng nhập", false, false);
        assertNotNull(result);
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_TESTS, result.intent());
        assertTrue(result.needsClarification(), "Brief vague prompt should trigger clarification");
        assertFalse(result.questions().isEmpty(), "Should generate clarification questions");
        assertTrue(result.initialClarificationMessage().contains("làm rõ"));

        // Verify questions structure
        boolean hasScope = false;
        boolean hasPrecondition = false;
        boolean hasAssertions = false;
        for (AiClarificationQuestion q : result.questions()) {
            assertNotNull(q.getId());
            assertNotNull(q.getQuestion());
            assertFalse(q.getOptions().isEmpty(), "Each question must have options");
            if (q.getId().equals("q_scope")) hasScope = true;
            if (q.getId().equals("q_precondition")) hasPrecondition = true;
            if (q.getId().equals("q_assertions")) hasAssertions = true;
        }
        assertTrue(hasScope && hasPrecondition && hasAssertions);
    }

    @Test
    void testRichPromptOrAttachments_SkipsClarification() {
        // With attachments
        AiAmbiguityAnalyzer.AnalysisResult withAttach = analyzer.analyze("viết test đăng nhập", true, false);
        assertFalse(withAttach.needsClarification(), "With document attachments, should skip clarification");

        // With Live DOM
        AiAmbiguityAnalyzer.AnalysisResult withDom = analyzer.analyze("viết test đăng nhập", false, true);
        assertFalse(withDom.needsClarification(), "With live DOM context, should skip clarification");

        // With long detailed prompt containing steps and URL
        String detailedPrompt = """
                Viết kịch bản Playwright kiểm thử đăng nhập hệ thống:
                URL: https://example.com/login
                Bước 1: Mở trang đăng nhập và kiểm tra tiêu đề Login
                Bước 2: Điền email 'user@example.com' vào ô input#email
                Bước 3: Điền mật khẩu 'Secret123!' vào ô input#password
                Bước 4: Nhấn nút button[type='submit']
                Bước 5: Kiểm tra chuyển hướng sang URL https://example.com/dashboard và hiển thị lời chào.
                """;
        AiAmbiguityAnalyzer.AnalysisResult detailedRes = analyzer.analyze(detailedPrompt, false, false);
        assertFalse(detailedRes.needsClarification(), "Detailed prompt with steps and URL should skip clarification");
    }

    @Test
    void testBuildAugmentedPrompt() {
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("q_scope", "Đầy đủ: Happy + Negative + Edge");
        answers.put("q_precondition", "Cần đăng nhập tài khoản trước");
        answers.put("q_assertions", "Kiểm tra chuyển hướng URL và Toast");

        String augmented = analyzer.buildAugmentedPrompt("viết test case giỏ hàng", answers);
        assertNotNull(augmented);
        assertTrue(augmented.contains("viết test case giỏ hàng"));
        assertTrue(augmented.contains("Đầy đủ: Happy + Negative + Edge"));
        assertTrue(augmented.contains("Cần đăng nhập tài khoản trước"));
        assertTrue(augmented.contains("Agency-Agents"));
    }

    @Test
    void testScenarioOnlyIntent_ClarificationAndDetection() {
        AiAmbiguityAnalyzer.AnalysisResult result = analyzer.analyze("tạo test scenario cho chức năng đăng nhập", false, false);
        assertNotNull(result);
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_SCENARIOS, result.intent());
        assertTrue(result.needsClarification());

        DocumentParserService.ParsedDocument doc = new DocumentParserService.ParsedDocument(
                "srs.docx",
                java.nio.file.Path.of("/docs/srs.docx"),
                false,
                "== Màn hình 1: Quản lý hàng hóa ==\n== Màn hình 2: Báo cáo tồn kho ==",
                "text/markdown",
                10000
        );
        AiAmbiguityAnalyzer.AnalysisResult srsResult = analyzer.analyze("tạo test scenario cho tài liệu", true, false, java.util.List.of(doc));
        assertEquals(AiAmbiguityAnalyzer.Intent.GENERATE_SCENARIOS, srsResult.intent());
        assertTrue(srsResult.needsClarification());
        assertTrue(srsResult.initialClarificationMessage().contains("Test Scenarios"));
    }
}

