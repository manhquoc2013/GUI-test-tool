# Implementation Plan: Multi-Agent Skill Pipeline, Loop Engineer & Functional Group/Suite Naming

## Overview
Upgrade the AI Assistant in `GUI-test-tool` to leverage a coordinated **Multi-Agent & Skill Pipeline** inspired by `docs/knowledge` (`ECC-main/agents/spec-miner.md`, `gan-planner.md`, `gan-generator.md`, `gan-evaluator.md`, `loop-operator.md`, `verification-loop/SKILL.md`, and `agency-agents-main/testing/testing-reality-checker.md`, `testing-test-automation-engineer.md`, `testing-workflow-optimizer.md`) and ensure **Group and Suite names** are accurately derived from the functional module and test cases (`suggestedGroup` & `suggestedSuite`).

---

## Architecture & Slices

### Slice 1: Specialized Skills & Multi-Agent Roles (`AiSkill.java`)
- Add and enrich specialized agent skills in `AiSkill`:
  1. `MULTI_AGENT_LOOP` (`"🔄 Đa Tác Nhân & Tự Kiểm Chứng (Multi-Agent Loop)"`, commands: `/loop`, `/agent`, `/pipeline`): Coordinates all 4 specialist agents (Spec Miner -> Test Architect & Script Engineer -> Reality Checker -> Loop Engineer).
  2. `REALITY_CHECKER_REVIEW` (`"🧐 Thẩm định & Đánh giá Test (Reality Checker)"`, commands: `/review`, `/audit`, `/check`): Evaluates test cases and scripts against a strict 5-axis rubric and outputs corrected cases.
  3. `API_CONTRACT_TESTER` (`"🔌 Kiểm thử API & Hợp đồng dữ liệu (API Tester)"`, commands: `/api`, `/rest`, `/graphql`): Specializes in REST/GraphQL/Payload contract & schema validation test cases.
- Enhance `AiSkill.detectSkill(...)` to route user requests accurately to the optimal specialist skill.

### Slice 2: Multi-Agent Pipeline & Autonomous Loop Engineer (`AiMultiAgentCoordinator.java` & `AiTestGeneratorService.java`)
- Introduce a structured 4-Agent orchestration workflow inside `AiTestGeneratorService` (backed by `AiMultiAgentCoordinator`):
  1. **Agent 1 — Spec Miner & Requirement Analyst (`SpecMiner`)**:
     - Decomposes user prompt, attached documents, and DOM into functional domain, feature module, target Group name (`suggestedGroup`), target Suite name (`suggestedSuite`), BVA boundaries, and scenario matrix.
  2. **Agent 2 — Test Case Architect & Playwright Script Engineer (`TestGenerator`)**:
     - Synthesizes ISTQB-compliant test cases (`TC01_<Feature>_HappyPath`, `TC02_<Feature>_NegativeValidation`, `TC03_<Feature>_BoundaryEdgeCase`) with priorities (`P0-P3`), NLP `testSteps`, `testDataSets`, `assertions`, and deterministic Playwright Java `scriptCode` (when URL/DOM is provided and script generation is enabled).
  3. **Agent 3 — Reality Checker & Reviewer (`RealityChecker`)**:
     - Audits generated cases across 5 quality gates:
       - Gate 1: Functional Group & Suite Naming Accuracy (`suggestedGroup` & `suggestedSuite` must reflect the exact functional domain and test suite, never generic `"AI-Tests"` or `"AI-Generated-Suite"`).
       - Gate 2: Scenario Matrix & Priority Completeness (Happy Path, Negative, Boundary/Edge cases, valid `P0-P3`).
       - Gate 3: NLP Test Steps & Precondition Completeness (at least 3 concrete steps with `stepNumber`, `action`, `target`, `testData`, `expectedResult`).
       - Gate 4: Assertion & Test Data Coverage (every case has valid assertions and BVA/EP test data sets).
       - Gate 5: Playwright Script Determinism & Syntax Safety (zero `Thread.sleep`/`waitForTimeout`, valid class wrapper, balanced braces, strict mode safety).
  4. **Agent 4 — Loop Engineer (`LoopEngineer`)**:
     - Executes an autonomous self-healing & refinement loop on any case or metadata failing Reality Checker gates:
       - Infers and normalizes accurate functional `suggestedGroup` and `suggestedSuite` names from prompt, documents, and test case names/descriptions.
       - Synthesizes missing `testSteps`, `assertions`, or `testDataSets` from test case context.
       - Repairs/validates Playwright `scriptCode` or triggers an LLM refinement pass when needed.
       - Attaches a concise Multi-Agent Quality Report (`AgentReviewReport`) to `GenerationResult`.

### Slice 3: Functional Naming for Groups & Suites (`AiAssistantPageController.java` & `AiChatMessage.java`)
- Extend `GenerationResult` and `AiChatMessage` with `suggestedGroup` and `suggestedSuite`.
- Provide `AiMultiAgentCoordinator.resolveFunctionalGroupName(...)` and `resolveFunctionalSuiteName(...)`:
  - Uses AI's `suggestedGroup` / `suggestedSuite` when valid and non-generic.
  - Otherwise extracts the functional domain from test case names (e.g., `TC01_DangNhap_ThanhCong` -> Group: `QuanLyDangNhap`, Suite: `KiemThu_DangNhap`), descriptions, or Vietnamese/English functional keywords in the prompt (e.g., đăng nhập/xác thực, giỏ hàng/thanh toán, đăng ký tài khoản, tìm kiếm sản phẩm, quản lý đơn hàng, phân quyền, báo cáo...).
- Update `AiAssistantPageController.applyCasesToSelectedSuite()` so that when auto-creating a Group or Suite (or when no specific Group/Suite was pre-selected), it names the Group and Suite accurately after the function and test cases instead of `"AI-Tests"` or `"AI-Generated-Suite"`.

### Slice 4: Unit Tests & Full Regression Verification
- Create `src/test/java/com/studio/service/AiMultiAgentLoopAndNamingTest.java` to verify:
  - All 4 agents in the pipeline (Spec Miner, Test Architect, Reality Checker, Loop Engineer).
  - Self-healing of missing steps, assertions, test data, and non-deterministic scripts.
  - Accurate functional Group and Suite naming from AI JSON (`suggestedGroup`, `suggestedSuite`), from test case names (`TC01_<Function>_...`), and from Vietnamese/English prompts.
  - Skill detection for new skills (`/loop`, `/review`, `/api`).
- Update `AiAssistantAutoCreateSuiteTest.java` to verify functional Group & Suite naming on save.
- Run `mvn clean test` and record learnings in `tasks/learnings.md`.
