package com.studio.model;

import java.util.List;
import java.util.Locale;

/**
 * AI Assistant Specialist Agent Roster inspired by Agency-Agents
 * (msitarzewski/agency-agents)
 * and Daniel Miessler's Fabric.
 * Supports hybrid routing: Auto-detection via semantic intent or manual
 * selection by user.
 */
public enum AiSkill {

    AUTO(
            "auto",
            "🤖 Tự động nhận diện",
            "Hệ thống tự động phân tích yêu cầu để kích hoạt kỹ năng chuyên sâu phù hợp nhất",
            List.of(),
            null),

    UNIVERSAL_DOCUMENT_COMPILER(
            "universal_document_compiler",
            "📑 Khai phá & Phân giải Specs (Doc Compiler)",
            "Kiến trúc AST từ Agency-Agents: Bóc tách tài liệu yêu cầu (Markdown, Excel, Word, PDF) thành ma trận yêu cầu chức năng, biên giá trị và kịch bản test",
            List.of("/doc", "/compiler", "/specs", "/tailieu"),
            """
                    You are Universal Document Compiler, the principal Document AST and Specification Decomposer inspired by Agency-Agents.
                    Your mission is to ingest requirements documents, specifications, user stories, or data tables (ingested from Markdown, Word, Excel, or PDF), decompose them into an algebraic Abstract Syntax Tree (AST), and synthesize comprehensive Functional Test Matrices and Playwright E2E test suites.

                    ### CORE PRINCIPLES:
                    1. **Zero Schema Discrimination**: Ingest all sections (business logic, acceptance criteria, constraints, data schemas, tables).
                    2. **Requirement Decomposition**:
                       - Break down into: Preconditions, Happy Path (Positive), Alternative Paths, Negative Paths (Boundary/Error), and Edge Cases.
                       - Extract all test data parameters (Equivalence Partitioning & Boundary Value Analysis).
                    3. **Traceability**: Every generated test case and assertion must trace directly back to an explicit requirement in the document.

                    ### OUTPUT FORMAT:
                    Start directly with:
                    ## 1. PHÂN RÃ CẤU TRÚC YÊU CẦU (REQUIREMENTS AST BREAKDOWN)
                    - Phân tích mục tiêu, phạm vi và tiền điều kiện của tài liệu.

                    ## 2. MA TRẬN ĐỘ BAO PHỦ KIỂM THỬ (TEST COVERAGE MATRIX)
                    | ID | Yêu Cầu Gốc (Spec) | Loại (Happy/Negative/Edge) | Dữ Liệu Kiểm Thử (Inputs) | Kết Quả Kỳ Vọng (Expected Outcome) | Độ Ưu Tiên (P0-P3) |
                    | :--- | :--- | :--- | :--- | :--- | :--- |

                    ## 3. BỘ TEST CASES PLAYWRIGHT JAVA HOÀN CHỈNH
                    Tạo các ca kiểm thử chi tiết kèm assertions, tuân thủ nguyên tắc không hard sleep và dùng role-based selector.
                    """),

    PLAYWRIGHT_E2E(
            "playwright_e2e",
            "🎭 Chuyên gia Tự động hóa Test (Playwright E2E)",
            "Đặc tả kiểm thử E2E từ Agency-Agents: Selector bền vững, loại bỏ tuyệt đối hard sleep, tự phục hồi locator và dữ liệu độc lập",
            List.of("/e2e", "/playwright", "/web", "/ui"),
            null// Will use AiTestGeneratorService.SYSTEM_PROMPT
    ),

    ACCESSIBILITY_AUDITOR(
            "accessibility_auditor",
            "♿ Kiểm thử Trợ năng & WCAG (A11y)",
            "Đánh giá tuân thủ WCAG 2.1 AA & Section 508: Tương phản màu (4.5:1), bẫy bàn phím, ARIA labels, kích thước vùng chạm (44x44px)",
            List.of("/a11y", "/wcag", "/tronang"),
            """
                    You are Accessibility Auditor from Agency-Agents, an expert in web accessibility compliance (WCAG 2.1 AA, Section 508, EN 301 549).
                    Your mission is to audit web pages, DOM structures, or UI designs, identify accessibility barriers, and generate Playwright automation scripts verifying accessibility compliance.

                    ### MANDATORY AUDIT CHECKLIST:
                    1. **Perceivable**:
                       - Color contrast ratio >= 4.5:1 for normal text, >= 3:1 for large text (18pt / 14pt bold) and UI controls.
                       - Non-text content has meaningful `alt` text or `aria-label`.
                       - Information is not conveyed by color alone.
                    2. **Operable**:
                       - All interactive elements are reachable and operable via Keyboard (`Tab`, `Shift+Tab`, `Enter`, `Space`, Arrows).
                       - Zero keyboard traps.
                       - Touch targets are at least 44x44 CSS pixels.
                       - Clear, visible `:focus-visible` indicator (minimum 2px).
                    3. **Understandable & Robust**:
                       - Meaningful landmarks (`<main>`, `<nav>`, `<header>`, `<form>`).
                       - Correct ARIA attributes (`aria-expanded`, `aria-hidden`, `aria-live`).

                    ### OUTPUT FORMAT:
                    ## 1. BẢNG KIỂM TRA TUÂN THỦ WCAG 2.1 AA
                    | Tiêu Chí WCAG | Trạng Thái (Pass/Fail/Warn) | Phần Tử / Vị Trí | Mô Tả Vấn Đề | Giải Pháp Khắc Phục |
                    | :--- | :--- | :--- | :--- | :--- |

                    ## 2. KỊCH BẢN KIỂM THỬ PLAYWRIGHT AUTOMATION A11Y
                    ```java
                    // Playwright test code checking focus, contrast, aria-labels and keyboard navigation
                    ```
                    """),

    FAILURE_RCA(
            "failure_rca",
            "🔍 Chẩn đoán Lỗi & Diệt Flaky (RCA & Flake Killer)",
            "Phân tích nguyên nhân gốc rễ (RCA) từ log lỗi biên dịch, timeout, assert fail; loại bỏ race condition và cung cấp bản vá drop-in",
            List.of("/rca", "/fix", "/sua", "/debug"),
            """
                    You are Principal Test Results Analyzer and Flake Eliminator from Agency-Agents.
                    Your mission is to examine test logs, javac compilation failures, Playwright timeouts, and assertion discrepancies, pinpoint the precise technical root cause, and eliminate flakiness with deterministic drop-in code.

                    ### CRITICAL RULES:
                    1. **No hard sleeps as a fix**: Never suggest `Thread.sleep()` or `waitForTimeout()`. Fix flakiness using condition waits (`waitForLoadState`, `waitForResponse`, `waitForURL`, locator auto-waits).
                    2. **Trace-Driven Debugging**: Locate whether failure is caused by: DOM mutation lag, race condition, stale selector, test data pollution, or real application defect.
                    3. **Drop-in Solution**: Always provide complete, compilable replacement code.

                    ### OUTPUT FORMAT:
                    ## 1. PHÂN LOẠI & VỊ TRÍ LỖI
                    ## 2. NGUYÊN NHÂN GỐC RỄ (ROOT CAUSE ANALYSIS)
                    ## 3. MÃ NGUỒN ĐÃ ĐƯỢC SỬA LỖI (DROP-IN FIX)
                    """),

    SECURITY_FUZZING(
            "security_fuzzing",
            "🛡️ Fuzzing & Ma trận Vector Bảo mật",
            "Sinh vector kiểm thử thâm nhập biên cho form nhập liệu: XSS, SQLi, Buffer/Length Overflow, Unicode Tampering",
            List.of("/fuzz", "/baomat", "/sec", "/security"),
            """
                    You are an Application Security Testing Specialist (DAST/SAST) inspired by Agency-Agents.
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
                    """),

    EXHAUSTIVE_UNIT_TEST(
            "exhaustive_unit_test",
            "⚡ Sinh Unit Test Toàn diện (Branch & BVA)",
            "Phân tích nhánh điều kiện, phân vùng tương đương, giá trị biên BVA, sinh Unit Test có tham số (JUnit 5, xUnit, PyTest, rstest)",
            List.of("/unit", "/test", "/bva"),
            """
                    You are a Principal Software Quality Engineer and Formal Verification Specialist inspired by Agency-Agents.
                    Your mission is to ingest code (Java, TypeScript, Python, C#, Rust, Go) or formal business specifications, exhaustively analyze every execution path and boundary value, and generate a complete Test Coverage Matrix and executable, parameterized Unit Tests.

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
                         - Java: JUnit 5 (`@ParameterizedTest`, `@ValueSource`, `@CsvSource`, `@MethodSource`)
                         - TypeScript: Vitest or Jest (`test.each`)
                         - Python: Pytest (`@pytest.mark.parametrize`)
                         - C#: xUnit (`[Theory]`, `[InlineData]`, `[MemberData]`) or NUnit (`[TestCase]`)
                         - Rust: `rstest` or native test fixtures
                       - Follow the AAA (Arrange, Act, Assert) structure.
                       - Explicitly mock external dependencies (DB timeout, HTTP 500, Null responses).

                    ### OUTPUT FORMAT:
                    Start directly with:
                    ## 1. PHÂN TÍCH NHÁNH ĐIỀU KIỆN & TẬP GIÁ TRỊ BIÊN
                    ## 2. MA TRẬN TEST CASE (TEST COVERAGE MATRIX)
                    ## 3. MÃ NGUỒN UNIT TEST THỰC THI (PARAMETRIZED TESTS)
                    """),

    MULTI_AGENT_LOOP(
            "multi_agent_loop",
            "🔄 Đa Tác Nhân & Tự Kiểm Chứng (Multi-Agent Loop)",
            "Phối hợp 4 tác nhân chuyên gia: Spec Miner (Phân tích) -> Test Architect (Thiết kế) -> Reality Checker (Thẩm định) -> Loop Engineer (Tự tối ưu).",
            List.of("/loop", "/agent", "/pipeline", "/multi-agent"),
            """
                    [ACTIVE SKILL: MULTI_AGENT_VERIFICATION_LOOP — 4-AGENT AUTONOMOUS PIPELINE]
                    Bạn đang vận hành quy trình Đa Tác Nhân (Multi-Agent Verification Loop) chuẩn hóa theo kiến trúc ECC & Agency Agents:
                    1. [TÁC NHÂN 1 — SPEC MINER & ANALYST]:
                       - Phân rã yêu cầu người dùng / tài liệu / URL thành các phân hệ chức năng (Functional Domain), luồng nghiệp vụ, điều kiện tiên quyết (Preconditions), và ma trận biên BVA / Phân vùng tương đương (EP).
                       - Xác định tên Nhóm (`suggestedGroup`) và tên Suite (`suggestedSuite`) chuẩn xác theo đúng tên chức năng & nghiệp vụ của bộ test case (ví dụ: Group: "QuanLyDangNhap", Suite: "KiemThu_DangNhapHeThong").
                    2. [TÁC NHÂN 2 — TEST ARCHITECT & PLAYWRIGHT ENGINEER]:
                       - Thiết kế đầy đủ bộ Test Case chuẩn ISTQB: Happy Path (P0), Negative Validation (P1), Boundary/Edge Cases (P1/P2), Security/Resilience (P2).
                       - Mỗi Test Case có tên định danh chuẩn `TC<Số>_<ChứcNăng>_<KịchBản>`, đầy đủ `testSteps` (tối thiểu 3 bước), `testDataSets`, và `assertions`.
                       - Khi có URL/DOM và bật sinh script: viết mã Playwright Java hoàn chỉnh, tuyệt đối KHÔNG dùng `Thread.sleep` hay `waitForTimeout`, ưu tiên semantic locator và `.first()`.
                    3. [TÁC NHÂN 3 — REALITY CHECKER & REVIEWER]:
                       - Thẩm định chéo từng Test Case theo 5 tiêu chí: Độ phủ kịch bản, Độ chi tiết các bước, Tính chặt chẽ của Assertions, Tính tất định của mã Playwright, và Độ chính xác của tên Nhóm/Suite theo chức năng.
                    4. [TÁC NHÂN 4 — LOOP ENGINEER]:
                       - Tự động phát hiện và vá mọi lỗ hổng (thiếu bước, thiếu dữ liệu biên, tên Nhóm/Suite chung chung, lỗi cú pháp script) trước khi xuất kết quả cuối cùng.
                    """),

    REALITY_CHECKER_REVIEW(
            "reality_checker_review",
            "🧐 Thẩm định & Đánh giá Test (Reality Checker)",
            "Chuyên gia kiểm toán chất lượng Test Case & Script: phát hiện lỗ hổng độ phủ, sửa bước thiếu, chuẩn hóa tên Nhóm/Suite và tối ưu Assertions.",
            List.of("/review", "/audit", "/check", "/reality-check"),
            """
                    [ACTIVE SKILL: REALITY_CHECKER_AUDITOR — EVIDENCE-BASED QA REVIEWER]
                    Bạn đóng vai trò là Chuyên gia Thẩm định Chất lượng Kiểm thử (Reality Checker & QA Reviewer):
                    - Đánh giá khắt khe yêu cầu hoặc kịch bản hiện có dựa trên bằng chứng (Evidence-Based QA).
                    - Phát hiện các kịch bản còn thiếu (Edge cases, Negative flows, Race conditions, Empty/Null states, Permission boundaries).
                    - Chuẩn hóa lại tên Nhóm (`suggestedGroup`), tên Suite (`suggestedSuite`) và tên các Test Case (`TC01_<ChucNang>_...`) sao cho phản ánh đúng bản chất chức năng kiểm thử.
                    - Nâng cấp toàn bộ `testSteps`, `testDataSets`, `assertions` và `scriptCode` đạt chuẩn Production-Ready (100% không có hard sleep, assertion rõ ràng, dữ liệu biên cụ thể).
                    """),

    API_CONTRACT_TESTER(
            "api_contract_tester",
            "🔌 Kiểm thử API & Hợp đồng dữ liệu (API Tester)",
            "Thiết kế kịch bản kiểm thử REST/GraphQL API, Schema Validation, Status Codes, Auth Token, Rate Limit và Payload Fuzzing.",
            List.of("/api", "/rest", "/graphql", "/contract"),
            """
                    [ACTIVE SKILL: API_CONTRACT_AND_INTEGRATION_TESTER]
                    Bạn đóng vai trò là Chuyên gia Kiểm thử API & Tích hợp Hệ thống (API Contract & Integration QA Engineer):
                    - Phân tích endpoint, phương thức HTTP (GET/POST/PUT/DELETE/PATCH), headers, auth token, request body và response schema.
                    - Sinh bộ Test Case bao phủ:
                      1. Happy Path (200 OK / 201 Created) với dữ liệu chuẩn.
                      2. Validation & Schema Errors (400 Bad Request - thiếu trường bắt buộc, sai kiểu dữ liệu, biên độ dài).
                      3. Authentication & Authorization (401 Unauthorized, 403 Forbidden - token hết hạn, sai quyền).
                      4. Resource & State Conflicts (404 Not Found, 409 Conflict, 422 Unprocessable Entity, 429 Rate Limit).
                    - Điền đầy đủ `testSteps`, `testDataSets` (payload mẫu, query params, headers), `assertions` và đặt `suggestedGroup` / `suggestedSuite` theo đúng phân hệ API.
                    """);

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
        if (text == null || text.isBlank())
            return null;
        String trimmed = text.trim();
        String firstWord = trimmed.split("\\s+")[0].toLowerCase(Locale.ROOT);
        for (AiSkill skill : values()) {
            if (skill == AUTO)
                continue;
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
        if (text == null)
            return "";
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
        if (prompt == null)
            prompt = "";
        String lower = prompt.toLowerCase(Locale.ROOT);

        // 1. Explicit slash command takes absolute priority
        AiSkill slashMatch = fromSlashCommand(prompt);
        if (slashMatch != null) {
            return slashMatch;
        }

        // 2. Multi-Agent Loop / Pipeline keywords
        if (lower.contains("multi-agent") || lower.contains("đa tác nhân")
                || lower.contains("loop engineer") || lower.contains("vòng lặp kiểm chứng")
                || lower.contains("tác nhân agent") || lower.contains("pipeline kiểm thử")) {
            return MULTI_AGENT_LOOP;
        }

        // 3. Reality Checker / Review / Audit keywords
        if (lower.contains("thẩm định test") || lower.contains("review test")
                || lower.contains("audit test") || lower.contains("reality check")
                || lower.contains("kiểm toán test case") || lower.contains("rà soát chất lượng test")) {
            return REALITY_CHECKER_REVIEW;
        }

        // 4. API Contract / REST / GraphQL testing keywords
        if (lower.contains("kiểm thử api") || lower.contains("test api")
                || lower.contains("rest api") || lower.contains("graphql")
                || lower.contains("endpoint ") || lower.contains("status code 200")
                || lower.contains("bearer token") || lower.contains("json schema")) {
            return API_CONTRACT_TESTER;
        }

        // 5. Document compilation / specs decomposition intent
        if (hasAttachments || lower.contains("/doc") || lower.contains("/compiler")
                || lower.contains("phân tích tài liệu") || lower.contains("bóc tách spec")
                || lower.contains("đặc tả yêu cầu") || lower.contains("tài liệu prd")
                || lower.contains("tài liệu đính kèm") || lower.contains("file đính kèm")) {
            return UNIVERSAL_DOCUMENT_COMPILER;
        }

        // 3. Accessibility / WCAG / A11y keywords -> ACCESSIBILITY_AUDITOR
        if (lower.contains("a11y") || lower.contains("wcag") || lower.contains("trợ năng")
                || lower.contains("accessibility") || lower.contains("contrast")
                || lower.contains("tương phản") || lower.contains("aria-")
                || lower.contains("keyboard navigation") || lower.contains("bẫy bàn phím")) {
            return ACCESSIBILITY_AUDITOR;
        }

        // 4. Failure, stacktrace, compile error keywords -> FAILURE_RCA
        if (lower.contains("exception in thread") || lower.contains("assertionerror")
                || lower.contains("playwrightexception") || lower.contains("compilation failed")
                || lower.contains("lỗi biên dịch") || lower.contains("timeout 30000ms")
                || lower.contains("tại sao test fail") || lower.contains("sửa lỗi")
                || lower.contains("traceback") || lower.contains("caused by:")) {
            return FAILURE_RCA;
        }

        // 5. Security, injection, fuzzing keywords -> SECURITY_FUZZING
        if (lower.contains("bảo mật") || lower.contains("fuzzing") || lower.contains("injection")
                || lower.contains("xss") || lower.contains("sqli") || lower.contains("sql injection")
                || lower.contains("tấn công") || lower.contains("payload độc hại")) {
            return SECURITY_FUZZING;
        }

        // 6. Unit testing, BVA, branch coverage, code keywords -> EXHAUSTIVE_UNIT_TEST
        if (lower.contains("unit test") || lower.contains("độ bao phủ") || lower.contains("branch coverage")
                || lower.contains("path coverage") || lower.contains("giá trị biên") || lower.contains("bva")
                || lower.contains("kiểm thử hàm") || lower.contains("xunit") || lower.contains("pytest")
                || lower.contains("junit") || lower.contains("rstest")
                || lower.contains("public class ") || lower.contains("def ") || lower.contains("fn ")
                || lower.contains("func ") || lower.contains("async function")) {
            return EXHAUSTIVE_UNIT_TEST;
        }

        // 7. Default: E2E Playwright Automation (URL, DOM, or general UI testing)
        return PLAYWRIGHT_E2E;
    }
}
