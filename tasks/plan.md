# Implementation Plan: Agent Indexing, In-App Artifact/MD/Export Persistence, Concise Chat Output & Coverage Optimization

## Overview
1. **Concise Chat Output & Structured Artifact Storage**:
   - Prevent raw `.md` documents and long code blocks from flooding the chat bubble.
   - Return a clean executive summary in the chat bubble while automatically saving `.md` reports and structured test artifacts into the application directory (`data/ai_artifacts/`, `data/exports/`, `data/converted_docs/`) and populating the structured Test Case / Test Script / Assertions / Steps / Test Data panels.
2. **In-App Storage for Converted `.md` Files & Exported Files**:
   - Ensure every attached document parsed by `DocumentParserService` is converted to `.md`, saved in `StudioPaths.getConvertedDocsDir()` (`data/converted_docs/`), and indexed in H2.
   - Ensure every exported file (Excel `.xlsx`, Markdown `.md`, JSON `.json`) from AI Assistant or Suite/Group export is automatically archived inside `StudioPaths.getExportsDir()` (`data/exports/`) and indexed in H2.
3. **Agent Indexing Engine (`AiAgentIndexService.java`)**:
   - Extend `DatabaseManager` and `LocalDataIndexService` with H2 tables `ai_documents_index`, `ai_document_chunks`, and `ai_exports_index`.
   - Build `AiAgentIndexService` to index converted `.md` documents (chunked by Markdown headings for BM25/keyword RAG retrieval), exported files, and existing project test cases/locators, feeding relevant indexed context directly into `AiMultiAgentCoordinator` and `AiTestGeneratorService`.
4. **Multi-Agent, Skill, Coverage & Speed Optimization**:
   - Upgrade `AiMultiAgentCoordinator` (Spec Miner, Test Architect, Reality Checker, Loop Engineer) to guarantee multi-scenario coverage (Happy Path + Negative Validation + Boundary/Edge Case) and multi-point assertions (`URL_CONTAINS`, `ELEMENT_VISIBLE`, `ELEMENT_HAS_TEXT`, `INPUT_VALUE_EQUALS`, etc.).
   - Expand `normalizeAssertionType` and `parseJsonCases` to support all 46 `AssertCodeGenerator` assertion types, aliases, and `attribute`/`expectedValue` fallbacks.
   - Optimize `applyCasesToSuite` with single-pass batch persistence (eliminating `N × 5` disk & H2 writes) and pre-compile all regex patterns + reuse `ObjectMapper` across hot paths.

---

## Vertical Slices

### Slice 1: `StudioPaths` & H2 Schema Extension (`DatabaseManager.java`, `StudioPaths.java`)
- Add `StudioPaths.getConvertedDocsDir()` (`data/converted_docs`) and `StudioPaths.getAiArtifactsDir()` (`data/ai_artifacts`).
- Add tables in `DatabaseManager`:
  - `ai_documents_index`: stores metadata and path of every converted `.md` document (`id`, `original_name`, `md_path`, `mime_type`, `size_bytes`, `chunk_count`, `created_at`).
  - `ai_document_chunks`: stores heading-segmented chunks (`id`, `doc_id`, `chunk_index`, `heading`, `content`, `keywords`, `token_estimate`) for fast RAG retrieval.
  - `ai_exports_index`: stores every exported file & AI-generated artifact (`id`, `export_type`, `group_name`, `suite_name`, `file_name`, `app_stored_path`, `external_path`, `case_count`, `created_at`).

### Slice 2: `AiAgentIndexService` & Automatic `.md` / Export Archival (`AiAgentIndexService.java`, `DocumentParserService.java`, `TestCaseExcelExportService.java`)
- Implement `AiAgentIndexService`:
  - `indexConvertedDocument(...)`: chunks `.md` content by headings and indexes into `ai_documents_index` + `ai_document_chunks`.
  - `indexExportedArtifact(...)`: copies/archives exported files into `data/exports/` (or `data/ai_artifacts/`) and records them in `ai_exports_index`.
  - `persistGeneratedArtifacts(...)`: automatically saves AI generation results (`.md` specification, `.json` cases, and `.java` scripts) into `data/ai_artifacts/<group>/<suite>/` and indexes them.
  - `retrieveRelevantContext(String query, String groupName, String suiteName, int maxTokens)`: searches indexed `.md` chunks, existing suite test cases, and verified locators in H2 to enrich the Agent's prompt with project-aware RAG context.
- Hook `DocumentParserService.parse(...)` and `TestCaseExcelExportService` (`exportAiGeneratedCases`, `exportToMarkdown`, `exportToJson`, `exportSuites`) into `AiAgentIndexService` so all converted `.md` files and exports are stored in-app and indexed automatically.

### Slice 3: Concise Chat Summary & Multi-Scenario / Multi-Assertion Optimization (`AiMultiAgentCoordinator.java`, `AiTestGeneratorService.java`, `AiAssistantPageController.java`)
- Implement `AiMultiAgentCoordinator.buildConciseChatSummary(...)` so chat bubbles display a clean executive summary (without dumping raw `.md` or ` ```java ` code blocks), while code and assertions are placed into the structured `AiGeneratedCase` list and saved to disk.
- Enhance `extractCasesFromMarkdown` to parse Markdown sections and code blocks into structured `AiGeneratedCase` objects with synthesized steps, assertions, and data sets.
- Upgrade `LoopEngineer` in `AiMultiAgentCoordinator`:
  - Auto-synthesize companion Negative Validation (`TC02`) and Boundary BVA (`TC03`) test cases when a functional generation returns only 1 scenario.
  - Synthesize multi-point assertions (at least 2–3 assertions per case derived from steps, locators, and URLs).
- Fix all 6 items from `/s-review`:
  - Batch `applyCasesToSuite` via `ProjectStore.saveSuiteBatch(...)` (1 disk write + 1 H2 index pass).
  - Full assertion alias mapping & `expectedValue` fallback.
  - Pre-compiled `static final Pattern` and shared `ObjectMapper`.
  - Inline `Thread.sleep` / `waitForTimeout` removal.
  - Preserve `preconditions` in `TestCase` when saving to Suite.

### Slice 4: Unit Testing & Verification
- Create `src/test/java/com/studio/service/AiAgentIndexingAndOptimizationTest.java` testing:
  - In-app `.md` conversion storage & H2 document chunk indexing + RAG retrieval.
  - In-app export archival (`data/exports/`) & H2 export indexing for `.xlsx`, `.md`, `.json`.
  - Automatic `data/ai_artifacts/` persistence and concise chat summary (verifying zero raw code blocks or raw `.md` dumps in chat message content).
  - Multi-scenario expansion (1 case -> 3 cases: Happy, Negative, Boundary) and multi-point assertion synthesis + alias normalization.
  - Batch `applyCasesToSuite` performance and precondition preservation.
- Run `mvn test` to verify 100% green across all tests.
