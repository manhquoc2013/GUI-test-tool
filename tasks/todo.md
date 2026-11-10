# Todo: Multi-Screen SRS Semantic Sectioning & Multi-Batch 4-Agent Orchestration
<!-- Progress: 5/5 tasks (100%) | Status: COMPLETED -->

- [x] **Task 1**: Upgrade `AiTokenOptimizer` to eliminate the 4,000-character document cutoff and implement `compressDocumentSmart` for balanced multi-section SRS preservation. <!-- id: 1 -->
- [x] **Task 2**: Implement Agent 1 (`SpecMiner`) semantic screen/module extraction (`ScreenSpecSection`, `extractScreenSections`, `extractRequestedScreenCount`) in `AiMultiAgentCoordinator`, handling 60-page PDF/DOCX Markdown (`### Trang N` stripping, SRS functional headings, fields/rules extraction). <!-- id: 2 -->
- [x] **Task 3**: Implement Agent 3 (`RealityChecker`) & Agent 4 (`LoopEngineer`) Per-Screen / Per-Feature Coverage Audit & Expansion in `AiMultiAgentCoordinator` so each screen in a multi-screen SRS is guaranteed 3–5+ test cases (Happy Path, Negative Validation, Boundary BVA, Business Rule/Permission) -> 35–45+ cases for 10 screens. <!-- id: 3 -->
- [x] **Task 4**: Implement Agent 2 (`TestArchitect`) Multi-Batch Screen Orchestration in `AiTestGeneratorService` to process multi-screen SRS documents in focused screen batches without hitting single-response output token truncation. <!-- id: 4 -->
- [x] **Task 5**: Add comprehensive unit & integration tests (`AiMultiAgentLargeSrsMultiScreenTest`, `AiTokenOptimizerTest`) and run full `mvn -o test` verification (323/323 tests passed). <!-- id: 5 -->
