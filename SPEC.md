# Technical Specification: Advanced AI Agent Test Generation, Clarification & Project Intelligence Engine

## 1. Objectives & Overview
Upgrade the AI capabilities in Playwright Automation Studio (`GUI-test-tool`) from a single-shot generator into an autonomous, conversational QA Architect Agent inspired by:
1. **Agency-Agents (`testing-test-automation-engineer`, `testing-reality-checker`)**: Zero hard sleeps, deterministic waits, user-facing role selectors (`getByRole`), strict-mode disambiguation, deep multi-vector assertions.
2. **Fabric (`test-collaboration`, patterns)**: Requirements AST breakdown, boundary value analysis (BVA), equivalence partitioning, comprehensive coverage matrices.
3. **Interactive Ambiguity Clarification**: Proactive detection of unclear requirements, presenting interactive choice cards in chat before generating code.
4. **Conversational Test Intelligence**: Project-aware chatbot capable of searching test cases, auditing existing test scripts for flakiness and quality, analyzing coverage gaps, and offering expert Playwright guidance.

---

## 2. Architecture & Components

### 2.1 Model Layer
- **`com.studio.model.AiClarificationQuestion`**:
  - `id`: Unique identifier (e.g. `q1`)
  - `question`: Question text displayed to the user
  - `options`: List of predefined answers (e.g. `["(Khuyên dùng) Đầy đủ (Happy + Negative + Edge)", "Chỉ luồng chính"]`)
  - `multiSelect`: Boolean (default `false`)
  - `allowCustomInput`: Boolean (default `true`)
  - `selectedAnswer`: User's selected or custom answer
- **`com.studio.model.AiChatMessage`**:
  - `clarificationQuestions`: `List<AiClarificationQuestion>`
  - `clarificationAnswers`: `Map<String, String>`
  - `clarificationAnswered`: `boolean`
  - `intent`: `String` (`GENERATE_TESTS`, `CLARIFICATION`, `SEARCH_TESTS`, `EVALUATE_TEST`, `GAP_ANALYSIS`, `CHAT_ADVICE`)

### 2.2 Ambiguity Analysis & Clarification Engine
- **`AiAmbiguityAnalyzer` / `AiTestGeneratorService`**:
  - Evaluates user prompt completeness (URL, authentication state, scope, assertions, test data).
  - If ambiguous or brief (e.g. "viết test đăng nhập", "test thanh toán"), generates a structured clarification schema (`type: "clarification"`).
  - Automatically enriches subsequent generation prompts with user choices.

### 2.3 Project Intelligence & Chatbot Service (`AiProjectAdvisorService`)
- **`searchTestCases(String query, String suiteFilter, Path projectDir)`**:
  - Scans `ProjectStore` tree, searches cases by name, description, startUrl, or script keywords.
  - Returns structured markdown search results with suite, case name, status, and summary.
- **`evaluateTestCase(String caseName, Path suiteDir)`**:
  - Inspects case script and assertions against Agency-Agents best practices:
    - Hard sleep check (`Thread.sleep`, `waitForTimeout`).
    - Locator resilience check (CSS nth-child vs `getByRole` / `data-testid`).
    - Assertion depth check (web-first `assertThat` vs weak checks).
    - Strict-mode safety (`.first()` on text selectors).
  - Assigns grade (A/B/C/Needs Work) and specific drop-in fixes.
- **`analyzeSuiteGaps(Path suiteDir)`**:
  - Inspects existing cases in suite, classifies covered areas, identifies missing negative/boundary/edge cases, recommends exact test cases to add.

### 2.4 Deep & Exhaustive Test Generation (`AiTestGeneratorService`)
- Enhances `SYSTEM_PROMPT` to mandate generating:
  - Multi-scenario suites: 1 Happy Path, 1 Negative/Validation Path, 1 Boundary/Edge Case.
  - Complete `testSteps` (stepNumber, action, target, testData, description, expectedResult).
  - Parameterized `testDataSets` with Equivalence Partitioning & BVA values.
  - Complete, runnable `scriptCode` with `StudioRuntime.healLocator`, strict-mode guards, and web-first assertions.

### 2.5 UI / UX Polish (`AiAssistantPageController` & FXML)
- **Interactive Clarification Card**:
  - Rendered inline in assistant chat bubble when `clarificationQuestions` is present.
  - Option chips with active selection highlight.
  - Custom input text field per question.
  - Confirm button: "⚡ Xác nhận & Sinh Test Cases chi tiết".
- **Enhanced Chat Capabilities**:
  - Seamless chatbot conversation (search, review, gap analysis) without showing empty case list or disabling controls.
  - Quick action chips for Search, Audit, Gap Analysis, and Clarified Generation.

---

## 3. Testing & Verification Strategy
1. **Unit Tests**:
   - `AiAmbiguityAnalyzerTest`: Test ambiguity detection and question structure.
   - `AiProjectAdvisorServiceTest`: Test case search, case evaluation grading, and gap analysis.
   - `AiClarificationFlowTest`: Test interactive answer serialization and augmented prompt building.
2. **Regression & Suite Verification**:
   - Ensure all 269 existing tests pass without regressions.
   - Verify UI rendering in JavaFX test harnesses.
