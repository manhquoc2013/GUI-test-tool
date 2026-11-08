# Technical Specification: Advanced AI Agent Test Generation, Automation & Script Optimization Engine

## 1. Objectives & Overview
Upgrade the AI capabilities in Playwright Automation Studio (`GUI-test-tool`) into a state-of-the-art AI Test Engineering Agent, synthesizing best practices from:
1. **Midscene.js**: Token-optimized interactive DOM tree extraction, multi-candidate resilient locators, visual/semantic intent mapping.
2. **Testsigma**: Dual representation (declarative NLP test steps + runnable code), test data parameterization (equivalence partitioning & boundary analysis), assertion mapping.
3. **Skyvern**: Agentic task decomposition (Planner -> Generator -> Validator), scratchpad reasoning, live DOM context scanning.
4. **Automa**: Resilient wait conditions (`waitForLoadState`, `waitForSelector`), exception recovery, stable element identification.

## 2. Core Architecture & Enhancements

### 2.1 Model Layer
- **`com.studio.model.TestStep`**:
  - `stepNumber`: int
  - `action`: String (e.g. `NAVIGATE`, `CLICK`, `FILL`, `SELECT`, `ASSERT_VISIBLE`, `ASSERT_TEXT`)
  - `target`: String (selector or semantic element name)
  - `testData`: String (input value or parameter)
  - `description`: String (human-readable step explanation)
  - `expectedResult`: String (verification outcome)
- **`com.studio.model.TestDataItem`**:
  - `scenario`: String (e.g. "Valid Inputs", "Missing Required Field", "Boundary Length")
  - `parameters`: Map<String, String> (field-value pairs)
  - `expectedOutcome`: String
- **`com.studio.model.AiGeneratedCase`**:
  - Enhanced with `List<TestStep> testSteps`
  - Enhanced with `List<TestDataItem> testDataSets`
  - Enhanced with `List<String> preconditions`
- **`com.studio.model.TestCase`**:
  - Enhanced with `List<TestStep> testSteps`
  - Enhanced with `List<TestDataItem> testDataSets`

### 2.2 Live DOM-Aware Context Injection
- **`LiveDomScanner` / Integration with `SharedBrowserSession` & `EnhancedDomExtractor`**:
  - Inspects active Playwright `Page` from `SharedBrowserSession` or runs quick headless inspection for a given URL.
  - Passes structured element list (`data-testid`, `role`, `text`, stable `id`, `name`) into AI context so generated scripts use real, accurate locators instead of hallucinated ones.

### 2.3 Multi-Stage AI Agent Architecture (`AiTestGeneratorService`)
- **Stage 1: Test Planning & Scenario Decomposition**:
  - Analyzes PRD / user prompt / live DOM context.
  - Synthesizes positive, negative, and edge cases with test data variations.
- **Stage 2: Resilient Playwright Java Script Generation**:
  - Embeds `StudioRuntime.healLocator("selector", "description")` on element interactions.
  - Interleaves explicit Playwright wait conditions:
    `page.waitForLoadState(LoadState.DOMCONTENTLOADED);`
    `page.waitForLoadState(LoadState.NETWORKIDLE);`
  - Produces structured JSON containing summary, cases, NLP `testSteps`, `testDataSets`, and Java `scriptCode`.
- **Stage 3: Syntax & Best-Practice Validator (Linter & Auto-Fixer)**:
  - Validates class structure, imports, balanced braces, and ensures no prohibited calls (`playwright.chromium().launch()`, `page.close()`).
  - Auto-corrects common formatting flaws before presenting to the user.

### 2.4 Token & Context Optimizer (`AiTokenOptimizer`)
- Compact DOM representation: Formats extracted interactive DOM into a compact markdown table/DSL, saving ~70% tokens compared to raw JSON.
- Context budgeting: Allocates token windows across system prompt, history, attachments, DOM context, and output headroom.

### 2.5 UI / UX Polish (`AiAssistantPageController` & FXML)
- Dedicated "Các bước kiểm thử (Steps)" tab in preview pane with formatted NLP steps.
- Dedicated "Dữ liệu kiểm thử (Test Data)" tab in preview pane with boundary/scenario data.
- "Quét DOM Trình duyệt (Scan Live DOM)" toolbar action to inject active browser DOM into prompt context.
- Updated counters and badges (`Steps (N)`, `Assertions (N)`, `Data (N)`).

### 2.6 Security & Storage Encryption (`ApiKeyCipher` & `SecuritySanitizer`)
- **Encryption at Rest**: AES-256-GCM encryption with machine-bound PBKDF2WithHmacSHA256 entropy (deterministic sorted network interfaces and OS metadata) in `settings.json` and H2 database.
- **Leak-Proof Export & Import**: Automatic API key masking (`sk-••••...`) on settings export, and preservation of existing local keys on import.
- **Log & Output Sanitization**: Active redaction of sensitive credentials and tokens in terminal output and UI error notifications.

## 3. Verification & Testing Strategy
- Unit tests for `TestStep` and `TestDataItem` models & JSON serialization.
- Unit tests for `AiTestGeneratorService` multi-stage prompt generation, DOM context formatting, and JSON response parsing.
- Unit tests for `ScriptValidator` / syntax auto-fixing.
- Unit tests for `AiAssistantPageController` tab bindings and live DOM scan integration.
- Unit tests for `ApiKeyCipherTest`, `SettingsSecurityTest`, `SecuritySanitizerTest`.
- Regression verification: All 138+ tests must PASS.

## 4. Full Offline Native Installers (macOS .dmg and Windows .exe Setup)

### 4.1 Objective
Provide turnkey native installer formats (`.dmg` for macOS, `.exe` Setup wizard for Windows) that are 100% self-contained and offline-ready:
1. **Zero Host Java Prerequisite**: Embedded OpenJDK 26 runtime created via `jpackage` with all required JavaFX & compiler modules.
2. **Offline Browsers & Node Runtime**: Bundled Node.js LTS and Playwright Chromium binaries offline.
3. **Data Safety & Clean Permissions**: `StudioPaths` dynamically identifies portable vs installed mode. When installed in system folders (`/Applications` on macOS, `Program Files` on Windows), writable user data (`data/`, `work/`) is safely anchored in standard OS user application data directories (`~/Library/Application Support/PlaywrightAutomationStudio` on macOS, `%APPDATA%\PlaywrightAutomationStudio` on Windows) while static binaries (`browsers`, `tools`) are loaded directly from the installation package.
4. **macOS Installer (`.dmg`)**: Automated script creating a drag-and-drop installer `.dmg` with `/Applications` shortcut and `.app` bundle.
5. **Windows Installer (`.exe`)**: Inno Setup script (`installer.iss`) and build script (`build-installer.bat`) creating a modern Setup wizard with Desktop shortcut, Start Menu shortcut, and Uninstaller.
6. **Continuous Integration**: GitHub Actions workflow to build release installers for both macOS and Windows.


## 5. Multi-Agent AI Test Engineering & Self-Healing Pipeline (ECC-Inspired)

### 5.1 Objectives & Architecture
Incorporate the multi-agent orchestration, test governance, self-healing TDD, and anti-thrashing mechanisms from `docs/knowledge/ECC-main`:
1. **QA Architect & Spec Miner (`qa-planner`)**: Decomposes user stories, PRDs, and live DOM contexts into structured test matrices with durable `TEST-ID`s, Equivalence Partitioning, Boundary Value Analysis (BVA), and negative/security scenarios.
2. **Playwright Test Engineer (`playwright-test-engineer`)**: Generates dual-representation test artifacts: declarative NLP steps (`testSteps`), parameterized test datasets (`testDataSets`), and runnable Playwright Java scripts with resilient locators (`StudioRuntime.healLocator`) and explicit wait conditions.
3. **Sandbox Test Runner & Verifier (`test-runner`)**: Executes generated Playwright Java tests in an isolated headless container, capturing compilation outcomes, runtime traces, DOM snapshots, and execution logs.
4. **Self-Healing Debugger (`test-self-healer`)**:
   - Classifies errors into syntax/compiler errors (minimal diff patching), locator/DOM failures (heuristic locator healing), or business logic assertions (identifying application defects vs test script defects).
   - **Anti-Thrashing Guard**: Halts speculative patching if a script or file is modified more than 3 times without achieving a green test, triggering forensic analysis.
5. **Quality Gate & Coverage Auditor (`quality-auditor`)**: Audits behavioral coverage, eliminates weak assertions (e.g. `assertTrue(true)`), bans silent error swallowing (`catch (Exception e) {}`), and validates test independence.

### 5.2 Artifacts, Skills & Rules
- **Agents**: `.agents/qa-planner.md`, `.agents/playwright-test-engineer.md`, `.agents/test-self-healer.md`, `.agents/quality-auditor.md`
- **Skills**: `skills/ai-test-suite-generator/SKILL.md`, `skills/playwright-test-healer/SKILL.md`, `skills/test-quality-gate/SKILL.md`
- **Rules**: `rules/playwright-java-testing.md`, `rules/anti-thrashing-healing.md`

## 7. Intelligent Test Case Priority Classification & Targeted Execution (P0-P3)

### 7.1 Priority Matrix & Definition
- **P0 (Critical / Smoke)**: Sống còn (Authentication, Security, Core Checkout, Payment, Blocker workflows). Failure stops deployment immediately.
- **P1 (High / Core Flow)**: Luồng nghiệp vụ cốt lõi, xác thực trường dữ liệu quan trọng, giao dịch chính.
- **P2 (Medium / Regression)**: Luồng phụ, tìm kiếm, lọc dữ liệu, phân trang, thông báo lỗi người dùng.
- **P3 (Low / Edge Case)**: UI polish, định dạng văn bản, tooltip, trạng thái biên hiếm gặp.

### 7.2 AI Autonomous Classification Engine
- AI Prompt (`AiTestGeneratorService.java`) và persona `qa-planner` tự động phân tích ma trận rủi ro nghiệp vụ và gắn nhãn `priority`: `"P0" | "P1" | "P2" | "P3"` vào cấu trúc JSON của mỗi test case.
- Tự động truyền trường `priority` từ `AiGeneratedCase` vào `TestCase` khi nhập vào Suite.

### 7.3 Priority-Driven Execution & Reporting
- **Targeted Runner Filtering**: `TestRunnerService` hỗ trợ chạy theo độ ưu tiên (chỉ chạy P0 cho Smoke Test, hoặc P0+P1 cho Sanity Test). Các test case ngoài bộ lọc được ghi nhận là SKIPPED kèm lý do rõ ràng.
- **Visual Badges & Report Filters**:
  - `HtmlReportService`: Hiển thị huy hiệu độ ưu tiên sắc nét (`P0` đỏ rực, `P1` cam vàng, `P2` xanh dương, `P3` xám) kèm bộ lọc chip theo Priority.
  - `ExcelReportService` & `TestCaseExcelExportService`: Bổ sung cột "Độ ưu tiên (Priority)" vào các bảng biểu tổng hợp.
- **Database & Persistence**: Trường `priority` được lưu trong `suite.json` và đánh chỉ mục trong bảng H2 `test_cases`.

## 8. Data-Driven Variable Profiles & Dynamic Locator Promotion Architecture

### 8.1 Test Data Profile Persistence & Pipeline (`TestDataProfileStore`)
- **Dedicated Store**: Lưu trữ và quản lý tập trung các bộ dữ liệu tham số hóa tại `data/test-data/<id>.json` với cơ chế ghi nguyên tử (`writeJsonAtomic`).
- **Layered Variable Resolution**: Khi thực thi test case, biến được hợp nhất theo thứ tự ưu tiên:
  1. Profile Variables (`TestDataProfile.variables`)
  2. Case-Specific Parameters (`TestCase.testDataSets[0].parameters`)
  3. Context Resolvers (`VariableResolver.resolve` cho script, assertions, testSteps).
- **Environment Variable Isolation**: Ràng buộc tra cứu biến môi trường hệ điều hành qua tiền tố tường minh `ENV.` (e.g. `{{ENV.BASE_URL}}`), ngăn ngừa rò rỉ thông tin máy local.

### 8.2 Dynamic Locator Candidate Promotion
- Khi `MultiLocatorCascade` tìm thấy một selector fallback ăn khớp trên trang sống (`count == 1 && isVisible()`), selector này được tự động thăng hạng (promote) thành `primary` trong `MultiLocatorProfile`.
- Trạng thái thăng hạng được ghi nhận tức thời vào `LOCATE_CACHE` và đồng bộ lưu xuống mã nguồn kịch bản `.java` cũng như các assertions/steps trong `suite.json`.



## 6. Comprehensive 3-Pillar Optimization: Midscene + Skyvern + Testsigma + Automa

### 6.1 Pillar 1: Multi-Locator Fallback Cascade & Action-Delta Healing (Testsigma + Skyvern)
1. **Multi-Locator Fallback Cascade (`MultiLocatorProfile` & `MultiLocatorCascade`)**:
   - Represents elements with an ordered cascade of strategies: Primary (`data-testid`/`id`), Alt CSS, Alt XPath, Alt Role/Aria, Alt Text.
   - Sub-15ms in-browser evaluation of candidate locators before incurring heavy LLM/VLM costs.
   - Automatic promotion & persistence: When a fallback matches uniquely, it updates the primary selector in memory and persists the fix to the test file.
2. **Action-Delta State Verification (`ActionDeltaVerifier`)**:
   - Compares pre-action vs post-action state (URL changes, DOM mutation count, modal emergence, interactive state).
   - Detects "phantom clicks" (actions that succeeded at Playwright level but triggered 0 page state changes due to overlays or unattached handlers).
   - Automatically executes resilient recovery actions: scroll element into view, dismiss obstructing overlays, dispatchEvent click fallback, or keyboard triggers.

### 6.2 Pillar 2: Interactive Run Visualizer & Semantic AI Assertions (Midscene.js)
1. **Semantic AI Assertions (`aiAssert` / `AI_SEMANTIC_ASSERT`)**:
   - Extends assertion catalogue (`AssertionType.AI_SEMANTIC_ASSERT`) and generator (`AssertCodeGenerator`).
   - Allows testers to express high-level visual/semantic verifications (e.g. "Giỏ hàng hiển thị đúng 2 sản phẩm", "Trang không có banner báo lỗi đỏ").
   - Evaluates page screenshot + distilled DOM via strict prompt schema returning structured boolean verdict + rationale.
2. **Interactive Run Visualizer (`visualizer.html` / Dump Inspection)**:
   - Midscene-inspired visual inspector generated in `HtmlReportService` and standalone report exports:
     - Visual overlay of tested elements and bounding boxes on step screenshots.
     - Self-healing decision tree & timeline (failed selector, candidates evaluated, healed match, latency).
     - Semantic assertion breakdown cards with AI explanations.

### 6.3 Pillar 3: Reusable Step Groups & Test Data Profiles (Automa + Testsigma)
1. **Reusable Step Groups (`StepGroup` & `StepGroupStore`)**:
   - Enables defining modular test sequences (e.g. "Standard Login", "Checkout Flow", "Clear Cart") once under `data/step-groups/`.
   - Reusable across multiple TestSuites and TestCases with macro inclusion `@include("login-flow")` and unified execution.
2. **Test Data Profiles (TDP) & Variable Pipeline (`TestDataProfile` & `VariableResolver`)**:
   - Decouples test logic from dynamic test parameters (URLs, credentials, test vectors).
   - Supports CSV / JSON data-driven execution with iteration tracking.
   - Dynamic variable interpolation (`{{varName}}`, `${varName}`) across test steps, scripts, and assertion targets.

- **Rules**: `rules/playwright-java-testing.md`, `rules/anti-thrashing-healing.md`

## 7. Intelligent Test Case Priority Classification & Targeted Execution (P0-P3)

### 7.1 Priority Matrix & Definition
- **P0 (Critical / Smoke)**: Sống còn (Authentication, Security, Core Checkout, Payment, Blocker workflows). Failure stops deployment immediately.
- **P1 (High / Core Flow)**: Luồng nghiệp vụ cốt lõi, xác thực trường dữ liệu quan trọng, giao dịch chính.
- **P2 (Medium / Regression)**: Luồng phụ, tìm kiếm, lọc dữ liệu, phân trang, thông báo lỗi người dùng.
- **P3 (Low / Edge Case)**: UI polish, định dạng văn bản, tooltip, trạng thái biên hiếm gặp.

### 7.2 AI Autonomous Classification Engine
- AI Prompt (`AiTestGeneratorService.java`) và persona `qa-planner` tự động phân tích ma trận rủi ro nghiệp vụ và gắn nhãn `priority`: `"P0" | "P1" | "P2" | "P3"` vào cấu trúc JSON của mỗi test case.
- Tự động truyền trường `priority` từ `AiGeneratedCase` vào `TestCase` khi nhập vào Suite.

### 7.3 Priority-Driven Execution & Reporting
- **Targeted Runner Filtering**: `TestRunnerService` hỗ trợ chạy theo độ ưu tiên (chỉ chạy P0 cho Smoke Test, hoặc P0+P1 cho Sanity Test). Các test case ngoài bộ lọc được ghi nhận là SKIPPED kèm lý do rõ ràng.
- **Visual Badges & Report Filters**:
  - `HtmlReportService`: Hiển thị huy hiệu độ ưu tiên sắc nét (`P0` đỏ rực, `P1` cam vàng, `P2` xanh dương, `P3` xám) kèm bộ lọc chip theo Priority.
  - `ExcelReportService` & `TestCaseExcelExportService`: Bổ sung cột "Độ ưu tiên (Priority)" vào các bảng biểu tổng hợp.
- **Database & Persistence**: Trường `priority` được lưu trong `suite.json` và đánh chỉ mục trong bảng H2 `test_cases`.

## 8. Data-Driven Variable Profiles & Dynamic Locator Promotion Architecture

### 8.1 Test Data Profile Persistence & Pipeline (`TestDataProfileStore`)
- **Dedicated Store**: Lưu trữ và quản lý tập trung các bộ dữ liệu tham số hóa tại `data/test-data/<id>.json` với cơ chế ghi nguyên tử (`writeJsonAtomic`).
- **Layered Variable Resolution**: Khi thực thi test case, biến được hợp nhất theo thứ tự ưu tiên:
  1. Profile Variables (`TestDataProfile.variables`)
  2. Case-Specific Parameters (`TestCase.testDataSets[0].parameters`)
  3. Context Resolvers (`VariableResolver.resolve` cho script, assertions, testSteps).
- **Environment Variable Isolation**: Ràng buộc tra cứu biến môi trường hệ điều hành qua tiền tố tường minh `ENV.` (e.g. `{{ENV.BASE_URL}}`), ngăn ngừa rò rỉ thông tin máy local.

### 8.2 Dynamic Locator Candidate Promotion
- Khi `MultiLocatorCascade` tìm thấy một selector fallback ăn khớp trên trang sống (`count == 1 && isVisible()`), selector này được tự động thăng hạng (promote) thành `primary` trong `MultiLocatorProfile`.
- Trạng thái thăng hạng được ghi nhận tức thời vào `LOCATE_CACHE` và đồng bộ lưu xuống mã nguồn kịch bản `.java` cũng như các assertions/steps trong `suite.json`.


