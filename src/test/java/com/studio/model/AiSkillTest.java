package com.studio.model;

import com.studio.service.AiTestGeneratorService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiSkillTest {

    @Test
    void testEnumDefinitionsAndVietnameseDisplayNames() {
        assertEquals("🤖 Tự động nhận diện", AiSkill.AUTO.getDisplayName());
        assertEquals("⚡ Sinh Test Case & Unit Test (Độ bao phủ cao)", AiSkill.EXHAUSTIVE_UNIT_TEST.getDisplayName());
        assertEquals("🎭 Sinh Kịch Bản Giao Diện (Playwright E2E)", AiSkill.PLAYWRIGHT_E2E.getDisplayName());
        assertEquals("🔍 Chuẩn Đoán & Sửa Lỗi (RCA & Fix)", AiSkill.FAILURE_RCA.getDisplayName());
        assertEquals("🛡️ Kiểm Thử Bảo Mật & Đầu Vào (Fuzzing)", AiSkill.SECURITY_FUZZING.getDisplayName());

        for (AiSkill skill : AiSkill.values()) {
            assertNotNull(skill.getId());
            assertNotNull(skill.getDisplayName());
            assertNotNull(skill.getDescription());
            assertEquals(skill.getDisplayName(), skill.toString());
        }
    }

    @Test
    void testSlashCommandsParsingAndStripping() {
        assertEquals(AiSkill.EXHAUSTIVE_UNIT_TEST, AiSkill.fromSlashCommand("/unit int add(int a, int b)"));
        assertEquals(AiSkill.PLAYWRIGHT_E2E, AiSkill.fromSlashCommand("/e2e https://example.com"));
        assertEquals(AiSkill.FAILURE_RCA, AiSkill.fromSlashCommand("/fix error in test"));
        assertEquals(AiSkill.FAILURE_RCA, AiSkill.fromSlashCommand("/rca stacktrace"));
        assertEquals(AiSkill.SECURITY_FUZZING, AiSkill.fromSlashCommand("/fuzz search form"));
        assertNull(AiSkill.fromSlashCommand("plain text without slash command"));

        assertEquals("int add(int a, int b)", AiSkill.stripSlashCommand("/unit int add(int a, int b)"));
        assertEquals("https://example.com", AiSkill.stripSlashCommand("/e2e https://example.com"));
        assertEquals("plain text", AiSkill.stripSlashCommand("plain text"));
    }

    @Test
    void testSemanticIntentDetection() {
        // 1. Failure RCA detection
        assertEquals(AiSkill.FAILURE_RCA, AiSkill.detectSkill("Exception in thread main: java.lang.AssertionError", false, false));
        assertEquals(AiSkill.FAILURE_RCA, AiSkill.detectSkill("Tại sao test case này bị timeout 30000ms?", false, false));
        assertEquals(AiSkill.FAILURE_RCA, AiSkill.detectSkill("Lỗi biên dịch javac không tìm thấy class", false, false));

        // 2. Security Fuzzing detection
        assertEquals(AiSkill.SECURITY_FUZZING, AiSkill.detectSkill("Kiểm tra các lỗ hổng XSS và SQL Injection cho form tìm kiếm", false, false));
        assertEquals(AiSkill.SECURITY_FUZZING, AiSkill.detectSkill("Tạo payload fuzzing kiểm thử bảo mật", false, false));

        // 3. Exhaustive Unit Test detection
        assertEquals(AiSkill.EXHAUSTIVE_UNIT_TEST, AiSkill.detectSkill("Viết unit test đạt branch coverage và boundary value cho hàm sau", false, false));
        assertEquals(AiSkill.EXHAUSTIVE_UNIT_TEST, AiSkill.detectSkill("public class Calculator { public int calculate(int x) {} }", false, false));
        assertEquals(AiSkill.EXHAUSTIVE_UNIT_TEST, AiSkill.detectSkill("def process_order(items, discount): pass", false, false));

        // 4. Playwright E2E default
        assertEquals(AiSkill.PLAYWRIGHT_E2E, AiSkill.detectSkill("Mở trang https://my-app.com và đăng nhập", false, false));
        assertEquals(AiSkill.PLAYWRIGHT_E2E, AiSkill.detectSkill("Tạo kịch bản kiểm thử giỏ hàng trên giao diện", false, false));
    }

    @Test
    void testExtractCasesFromMarkdown() {
        String md = """
                ## 1. PHÂN TÍCH LUỒNG
                Bảng phân tích luồng...
                
                ## 2. UNIT TESTS
                ```csharp
                public class CalculatorTests {
                    [Theory]
                    [InlineData(1, 2, 3)]
                    public void TestAdd(int a, int b, int expected) {
                        Assert.Equal(expected, a + b);
                    }
                }
                ```
                """;

        List<AiGeneratedCase> cases = AiTestGeneratorService.extractCasesFromMarkdown(md);
        assertEquals(1, cases.size());
        assertTrue(cases.get(0).getScriptCode().contains("CalculatorTests"));
        assertTrue(cases.get(0).getScriptCode().contains("[Theory]"));
    }
}

