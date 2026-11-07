# Implementation Plan: Multi-Tier Self-Healing Engine

## Phase 1: Models & Core Runtime Engine
- Create `HealedStepMeta.java` in `com.studio.model`.
- Add `healedSteps` property to `CaseResult.java`.
- Create `EnhancedDomExtractor.java` in `com.studio.runtime`.
- Create `HeuristicHealer.java` in `com.studio.runtime`.
- Create `OptimizedSelfHealingExecutor.java` in `com.studio.runtime`.
- Enhance `StudioRuntime.java` with executor holder, `healLocator()`, and audit collection methods.
- Write unit tests for `HeuristicHealer` and `OptimizedSelfHealingExecutor`.

## Phase 2: Harness & Assert Generation Integration
- Update `ScriptHarnessTransformer.java` to support healing wrapper (`StudioRuntime.healLocator(...)` or direct healing transformation).
- Update `AssertCodeGenerator.java` to route element locators through `StudioRuntime.healLocator(...)`.
- Write unit tests for `ScriptHarnessTransformer` and `AssertCodeGenerator` healing behavior.

## Phase 3: Runner & Report Audit Trail Integration
- Update `TestRunnerService.java` to initialize `OptimizedSelfHealingExecutor` with `SettingsStore` / `OpenAiClient` and inject into `StudioRuntime`.
- Collect healed steps into `CaseResult` at end of case execution.
- Update `HtmlReportService.java` to render `[Auto-Healed]` badges and audit details table.

## Phase 4: Full Verification & Learnings
- Run full regression tests (`mvn test`).
- Record learnings in `tasks/learnings.md`.
