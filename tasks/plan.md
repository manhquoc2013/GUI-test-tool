# Plan: Multi-Screen SRS Semantic Sectioning & Multi-Batch 4-Agent Orchestration

## Slice 1: Structure-Preserving Document Compression & RAG Expansion
- **Files**:
  - `src/main/java/com/studio/service/AiTokenOptimizer.java`
  - `src/main/java/com/studio/service/AiAgentIndexService.java`
- **Details**:
  - Replace hardcoded `compressDocument(doc, 4000)` in `buildOptimizedMessages()` with dynamic token-budget allocation (`DEFAULT_DOC_CHAR_BUDGET = 48000`) and semantic section-aware compression (`compressDocumentSmart`) that preserves all headings and balanced content from every section of a 60-page SRS.
  - Expand `AiAgentIndexService.retrieveIndexedContextForAgent` chunk limit and scoring so multi-section documents retrieve coverage across sections.

## Slice 2: Agent 1 (Spec Miner) — Semantic Screen & Module Extractor
- **Files**:
  - `src/main/java/com/studio/service/AiMultiAgentCoordinator.java`
- **Details**:
  - Create `ScreenSpecSection` model holding `index`, `screenName`, `featureToken`, `sectionContent`, `extractedFields`, and `extractedRules`.
  - Implement `extractScreenSections(String userPrompt, List<ParsedDocument> attachedDocs)`:
    - Normalize PDF page break markers (`### Trang \d+`, `Page \d+`) so multi-page screens stay contiguous.
    - Detect functional screen/module sections (`Màn hình`, `Screen`, `Trang`, `Chức năng`, `Module`, `Phân hệ`, `Form`, `UC-\d+`, `FR-\d+`, or numbered SRS specification sections) while skipping non-functional frontmatter (`Giới thiệu`, `Mục đích`, `Phạm vi`, `Thuật ngữ`).
    - Parse explicit screen count from prompt (e.g., `"10 màn hình"`, `"5 màn hình"`) and extract/partition matching screens from the SRS or prompt.

## Slice 3: Agent 2 (Test Architect) — Multi-Batch Screen Orchestration
- **Files**:
  - `src/main/java/com/studio/service/AiTestGeneratorService.java`
  - `src/main/java/com/studio/service/AiMultiAgentCoordinator.java`
- **Details**:
  - When Agent 1 identifies multiple screens (`screens.size() >= 2`), orchestrate focused batch generation (2 screens per batch) when appropriate, passing the full untruncated `ScreenSpecSection` content of each screen in the batch and instructing the LLM to generate 4–5 test cases per screen (Happy Path, Negative Validation, Boundary BVA, Business Rule/Permission).
  - Merge batch responses into a unified `GenerationResult` while deduplicating and preserving screen order.

## Slice 4: Agent 3 (Reality Checker) & Agent 4 (Loop Engineer) — Per-Screen Coverage Audit & Expansion
- **Files**:
  - `src/main/java/com/studio/service/AiMultiAgentCoordinator.java`
- **Details**:
  - Replace the naive `if (cases.size() == 1)` check with **Per-Screen / Per-Feature Coverage Auditing**:
    - Group generated test cases by screen / feature token (`TCxx_<Feature>_...` or matched `ScreenSpecSection`).
    - Enforce `minCasesPerScreen` (4 cases per screen for multi-screen SRS: Happy Path [P0], Negative Validation [P1], Boundary BVA [P1], Business Rule & Permission [P2]; minimum 3 cases per feature for standard requests).
    - If the LLM returned only 1 case per screen (e.g., 10 cases for 10 screens) or missed any screen from the SRS inventory, Agent 4 synthesizes the missing screen-specific scenarios grounded in that screen's extracted SRS fields, actions, and business rules.
    - Re-sequence all test case IDs (`TC01_<Screen1>_...` through `TC40_<Screen10>_...`).

## Slice 5: Comprehensive TDD Verification
- **Files**:
  - `src/test/java/com/studio/service/AiMultiAgentLargeSrsMultiScreenTest.java`
  - `src/test/java/com/studio/service/AiTokenOptimizerTest.java`
  - `src/test/java/com/studio/service/AiMultiAgentLoopAndNamingTest.java`
- **Details**:
  - Write unit and integration tests simulating a 60-page SRS with 10 screens, verifying:
    - Agent 1 extracts all 10 screens accurately across 60 PDF/Markdown pages.
    - Agent 2 + Agent 3 + Agent 4 produce 35–40+ detailed test cases (3–5+ cases per screen) even when an LLM returns only 1 case per screen or across multi-batch calls.
    - Every single screen has Happy Path, Negative Validation, Boundary BVA, and Business Rule scenarios with concrete steps, data sets, and assertions.
