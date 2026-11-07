package com.studio.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * AI Assistant Skill Suite inspired by Fabric (Daniel Miessler) and Testsigma.
 * Supports hybrid routing: Auto-detection via semantic intent or manual selection by user.
 */
public enum AiSkill {

    AUTO(
            "auto",
            "🤖 Tự động nhận diện",
            "Hệ thống tự động phân tích yêu cầu để kích hoạt kỹ năng chuyên sâu phù hợp nhất",
            List.of(),
            null
    ),

    EXHAUSTIVE_UNIT_TEST(
            "exhaustive_unit_test",
            "⚡ Sinh Test Case & Unit Test (Độ bao phủ cao)",
            "Phân tích nhánh điều kiện, phân vùng tương đương, giá trị biên BVA, sinh Unit Test có tham số (xUnit, PyTest, JUnit 5, rstest)",
            List.of("/unit", "/test", "/bva"),
            """
            You are a Principal Software Quality Engineer and Formal Verification Specialist inspired by Daniel Miessler's Fabric patterns.
            Your mission is to ingest code (C#, Rust, Python, Java, TypeScript, Go) or formal business specifications, exhaustively analyze every execution path and boundary value, and generate a complete Test Coverage Matrix and executable, parameterized Unit Tests.

            ### EXECUTION WORKFLOW:
            1. **Control Flow & Decision Node Analysis**:
               - Identify every decision point: `if/else`, ternary expressions, `switch/match`, guard clauses, loops, `try/catch/finally`, async unwrap.
               - Compute formal boundary sets for every input variable:
                 - Numbers: Min-1, Min, Min+1, Nominal, Max-1, Max, Max+1, Zero, Negative, Overflow.
                 - Strings: Null, Empty (""), Whitespace only, Min length, Max length, Unicode/Emoji, Delimiters.
                 - Collections: Null, Empty, Single element, Duplicate elements, Max capacity.

            2. **Test Coverage Matrix (Markdown)**:
               - Output a comprehensive Markdown table with columns:
                 | TC ID | Kịch bản / Mục đích | Loại Coverage (Branch/Path/BVA/Edge/Exception) | Input Data | System State / Mock | Expected Result |

            3. **Executable Parameterized Unit Tests**:
               - Write complete, compilable unit tests matching the language and idiomatic framework:
                 - C#: xUnit (`[Theory]`, `[InlineData]`, `[MemberData]`) or NUnit (`[TestCase]`)
                 - Python: Pytest (`@pytest.mark.parametrize`)
                 - Rust: `rstest` or native test fixtures
                 - Java: JUnit 5 (`@ParameterizedTest`, `@ValueSource`, `@CsvSource`, `@MethodSource`)
                 - TypeScript: Vitest or Jest (`test.each`)
               - Follow the AAA (Arrange, Act, Assert) structure.
               - Explicitly mock external dependencies (DB timeout, HTTP 500, Null responses).

            ### OUTPUT FORMAT:
            Start directly with:
            ## 1. PHÂN TÍCH NHÁNH ĐIỀU KIỆN & TẬP GIÁ TRỊ BIÊN
            ## 2. MA TRẬN TEST CASE (TEST COVERAGE MATRIX)
            ## 3. MÃ NGUỒN UNIT TEST THỰC THI (PARAMETRIZED TESTS)
            """
    ),

    PLAYWRIGHT_E2E(
            "playwright_e2e",
            "🎭 Sinh Kịch Bản Giao Diện (Playwright E2E)",
            "Sinh kịch bản kiểm thử giao diện Playwright Java với mô hình kép (bước NLP, dữ liệu DDT, mã tự phục hồi selector)",
            List.of("/e2e", "/web", "/ui"),
            null // Will use AiTestGeneratorService.SYSTEM_PROMPT
    ),

    FAILURE_RCA(
            "failure_rca",
            "🔍 Chuẩn Đoán & Sửa Lỗi (RCA & Fix)",
            "Phân tích nguyên nhân gốc rễ (RCA) từ log lỗi biên dịch javac, timeout, assert fail và cung cấp mã sửa lỗi ngay",
            List.of("/rca", "/fix", "/sua", "/debug"),
            """
            You are a Principal Test Failure Debugging and Root Cause Analysis (RCA) Specialist inspired by Fabric's analyze_incident pattern.
            Your mission is to examine test logs, javac compilation failures, Playwright timeouts, and assertion discrepancies, pinpoint the precise technical root cause, and provide an immediate drop-in code fix.

            ### EXECUTION WORKFLOW:
            1. **Anchor Failure**:
               - Isolate the failing line, selector, exception class, or timing collision.
               - Categorize into: COMPILATION_ERROR, LOCATOR_MUTATION, TIMING_RACE_CONDITION, DATA_MISMATCH, or APPLICATION_BUG.

            2. **Root Cause Analysis (RCA)**:
               - Explain clearly in Vietnamese why the failure occurred (1-2 precise paragraphs).

            3. **Actionable Remedy**:
               - Provide the exact drop-in corrected Java or script code block.

            ### OUTPUT FORMAT:
            ## 1. PHÂN LOẠI & VỊ TRÍ LỖI
            ## 2. NGUYÊN NHÂN GỐC RỄ (ROOT CAUSE ANALYSIS)
            ## 3. MÃ NGUỒN ĐÃ ĐƯỢC SỬA LỖI (DROP-IN FIX)
            """
    ),

    SECURITY_FUZZING(
            "security_fuzzing",
            "🛡️ Kiểm Thử Bảo Mật & Đầu Vào (Fuzzing)",
            "Sinh vector kiểm thử thâm nhập biên cho form nhập liệu: XSS, SQLi, Buffer/Length Overflow, Unicode Tampering",
            List.of("/fuzz", "/baomat", "/sec", "/security"),
            """
            You are an Application Security Testing Specialist (DAST/SAST) inspired by Fabric's threat modeling patterns.
            Your mission is to analyze web input forms, authentication flows, and API payloads, generating an exhaustive fuzzing test suite to verify input boundaries against malicious vectors.

            ### VECTORS TO COVER:
            - Reflected and Stored XSS (`<script>`, SVG onload, attribute event handlers)
            - SQL and NoSQL Injection payloads
            - Format string and Null-byte injection (`\\0`, `%00`)
            - Boundary Length / Buffer Overflows (1,000 to 10,000 chars)
            - Unicode BiDi Override and Homoglyph attacks
            - Number boundaries (negative, 0, 64-bit max, floating precision)

            ### OUTPUT FORMAT:
            ## 1. MA TRẬN VECTOR FUZZING & KIỂM THỬ BẢO MẬT
            | Vector ID | Phân Loại Lỗ Hổng | Payload Input | Trường Mục Tiêu | Hành Vi An Toàn Mong Đợi | Dấu Hiệu Lỗ Hổng (Vulnerability Indicator) |
            | :--- | :--- | :--- | :--- | :--- | :--- |

            ## 2. KỊCH BẢN PLAYWRIGHT / UNIT TEST BẢO MẬT
            ```java
            // Parameterized test automating the fuzzing validation
            ```
            """
    );

    private final String id;
    private final String displayName;
    private final String description;
    private final List<String> slashCommands;
    private final String systemPrompt;

    AiSkill(String id, String displayName, String description, List<String> slashCommands, String systemPrompt) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.slashCommands = slashCommands;
        this.systemPrompt = systemPrompt;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getSlashCommands() {
        return slashCommands;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Parse slash command from prompt if present. Returns matching skill or null.
     */
    public static AiSkill fromSlashCommand(String text) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.trim();
        String firstWord = trimmed.split("\\s+")[0].toLowerCase(Locale.ROOT);
        for (AiSkill skill : values()) {
            if (skill == AUTO) continue;
            for (String cmd : skill.getSlashCommands()) {
                if (firstWord.equalsIgnoreCase(cmd)) {
                    return skill;
                }
            }
        }
        return null;
    }

    /**
     * Strips leading slash command (e.g., "/unit ") from the prompt.
     */
    public static String stripSlashCommand(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        for (AiSkill skill : values()) {
            for (String cmd : skill.getSlashCommands()) {
                if (trimmed.toLowerCase(Locale.ROOT).startsWith(cmd)) {
                    String remaining = trimmed.substring(cmd.length()).trim();
                    return remaining;
                }
            }
        }
        return trimmed;
    }

    /**
     * Semantic intent router: detects the most suitable skill when in AUTO mode.
     */
    public static AiSkill detectSkill(String prompt, boolean hasDomContext, boolean hasAttachments) {
        if (prompt == null) prompt = "";
        String lower = prompt.toLowerCase(Locale.ROOT);

        // 1. Explicit slash command takes absolute priority
        AiSkill slashMatch = fromSlashCommand(prompt);
        if (slashMatch != null) {
            return slashMatch;
        }

        // 2. Failure, stacktrace, compile error keywords -> FAILURE_RCA
        if (lower.contains("exception in thread") || lower.contains("assertionerror")
                || lower.contains("playwrightexception") || lower.contains("compilation failed")
                || lower.contains("lỗi biên dịch") || lower.contains("timeout 30000ms")
                || lower.contains("tại sao test fail") || lower.contains("sửa lỗi")
                || lower.contains("traceback") || lower.contains("caused by:")) {
            return FAILURE_RCA;
        }

        // 3. Security, injection, fuzzing keywords -> SECURITY_FUZZING
        if (lower.contains("bảo mật") || lower.contains("fuzzing") || lower.contains("injection")
                || lower.contains("xss") || lower.contains("sqli") || lower.contains("sql injection")
                || lower.contains("tấn công") || lower.contains("payload độc hại")) {
            return SECURITY_FUZZING;
        }

        // 4. Unit testing, BVA, branch coverage, code keywords -> EXHAUSTIVE_UNIT_TEST
        if (lower.contains("unit test") || lower.contains("độ bao phủ") || lower.contains("branch coverage")
                || lower.contains("path coverage") || lower.contains("giá trị biên") || lower.contains("bva")
                || lower.contains("kiểm thử hàm") || lower.contains("xunit") || lower.contains("pytest")
                || lower.contains("junit") || lower.contains("rstest")
                || lower.contains("public class ") || lower.contains("def ") || lower.contains("fn ")
                || lower.contains("func ") || lower.contains("async function")) {
            return EXHAUSTIVE_UNIT_TEST;
        }

        // 5. Default: E2E Playwright Automation (URL, DOM, or general UI testing)
        return PLAYWRIGHT_E2E;
    }
}
