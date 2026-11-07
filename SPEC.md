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

## 3. Verification & Testing Strategy
- Unit tests for `TestStep` and `TestDataItem` models & JSON serialization.
- Unit tests for `AiTestGeneratorService` multi-stage prompt generation, DOM context formatting, and JSON response parsing.
- Unit tests for `ScriptValidator` / syntax auto-fixing.
- Unit tests for `AiAssistantPageController` tab bindings and live DOM scan integration.
- Regression verification: All 101+ tests must PASS.
