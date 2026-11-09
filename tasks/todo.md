# Todo List: Agent Indexing, In-App Artifact/MD/Export Persistence, Concise Chat Output & Coverage Optimization

- [x] 1. Extend `StudioPaths.java` (`getConvertedDocsDir`, `getAiArtifactsDir`) and `DatabaseManager.java` (`ai_documents_index`, `ai_document_chunks`, `ai_exports_index`) <!-- id: 1 -->
- [x] 2. Create `AiAgentIndexService.java` for document chunk indexing, RAG context retrieval, in-app export archival, and AI artifact persistence <!-- id: 2 -->
- [x] 3. Integrate `AiAgentIndexService` into `DocumentParserService.java` and `TestCaseExcelExportService.java` so all `.md` conversions and exports are stored in-app and indexed <!-- id: 3 -->
- [x] 4. Optimize `AiMultiAgentCoordinator.java` & `AiTestGeneratorService.java`: concise chat summary (no raw `.md`/code in chat), multi-scenario expansion (Happy/Negative/Boundary), multi-assertion synthesis, 46-assertion alias normalization, pre-compiled regexes, and batch `applyCasesToSuite` <!-- id: 4 -->
- [x] 5. Write comprehensive tests in `AiAgentIndexingAndOptimizationTest.java` and run full test suite (`mvn test` — 313/313 passing) <!-- id: 5 -->
- [x] 6. Update `tasks/learnings.md` <!-- id: 6 -->
