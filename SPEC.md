# Technical Specification: Multi-Tier Self-Healing Engine for Playwright Automation Studio

## 1. Objective
Implement an autonomous, multi-tier Self-Healing Engine integrated directly into Playwright Automation Studio's runtime and runner:
1. **Multi-Tier Resilience & Healing Architecture**:
   - **Tier 0**: Smart wait & element stability checks (400ms buffer, scroll into view).
   - **Tier 1**: In-memory `LocateCache` for zero-overhead repeat step healing across tests.
   - **Tier 2**: Zero-cost Rule-Based `HeuristicHealer` (<30ms, dynamic ID cleanup, text/role/aria-label conversion).
   - **Tier 3**: Multimodal AI / VLM healing via Studio's existing `OpenAiClient` + `EnhancedDomExtractor` (with Shadow DOM, IFrame, and visibility checks).
   - **Tier 4**: Multi-criteria Sandbox Verification (`count() == 1 && isVisible() && isEnabled()`).
2. **Persistent Auto-Mutation & Audit Trail**:
   - Updates `case.json` automatically upon validated healing via `ProjectStore`.
   - Records detailed audit metadata (`HealedStepMeta`) on `CaseResult` and displays `[Auto-Healed]` badges in HTML/Report views.
3. **Seamless Runner & Harness Integration**:
   - `StudioRuntime` provides thread-safe access to the healing executor.
   - `ScriptHarnessTransformer` and `AssertCodeGenerator` generate self-healing wrapped calls for locator interactions and element assertions.

## 2. Components

### 2.1 Models (`com.studio.model.HealedStepMeta`)
- Records:
  - `testCaseId`: String
  - `actionDescription`: String
  - `originalSelector`: String
  - `repairedSelector`: String
  - `method`: String ("HEURISTIC" | "VLM" | "LOCATE_CACHE")
  - `timestamp`: String

### 2.2 Runtime Engine (`com.studio.runtime`)
- `EnhancedDomExtractor`: Evaluates JS to gather interactive elements with Shadow DOM traversal, visibility checks, and dynamic ID filtering.
- `HeuristicHealer`: Fast rule-based repair analyzing failed selectors (id suffix, text, name, aria-label).
- `OptimizedSelfHealingExecutor`: Orchestrates Tiers 0-4, verifies candidates, records audit trail, and persists changes.
- `StudioRuntime`: Adds ThreadLocal holder for `OptimizedSelfHealingExecutor` and step audit records.

### 2.3 Transformers & Generators
- `ScriptHarnessTransformer`: Wraps locator actions or provides `StudioRuntime.healLocator(...)` proxy helper.
- `AssertCodeGenerator`: Embeds healing lookup in element assertion generation.

### 2.4 Test Runner & Reporting
- `TestRunnerService`: Instantiates executor with active `OpenAiClient` + `AiConfig` and attaches to `StudioRuntime`. Collects `healedSteps` into `CaseResult`.
- `HtmlReportService`: Renders auto-healed badges and table in execution reports.

## 3. Testing Strategy
- Unit tests for `HeuristicHealer` (regex, ID patterns, text matching).
- Unit tests for `OptimizedSelfHealingExecutor` (cache hit, heuristic repair, candidate validation).
- Unit tests for `ScriptHarnessTransformer` and `AssertCodeGenerator` with healing.
- Full regression verification (`mvn test`).
