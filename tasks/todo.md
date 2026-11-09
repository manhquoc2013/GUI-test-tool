# Todo List: System-Wide Review Round 1–4 Fixes & Optimizations
<!-- Progress: 12/12 tasks (100%) | Status: COMPLETED -->

- [x] 1. Extend `StudioPaths.java` (`getConvertedDocsDir`, `getAiArtifactsDir`) and `DatabaseManager.java` (`ai_documents_index`, `ai_document_chunks`, `ai_exports_index`) <!-- id: 1 -->
- [x] 2. Create `AiAgentIndexService.java` for document chunk indexing, RAG context retrieval, in-app export archival, and AI artifact persistence <!-- id: 2 -->
- [x] 3. Integrate `AiAgentIndexService` into `DocumentParserService.java` and `TestCaseExcelExportService.java` so all `.md` conversions and exports are stored in-app and indexed <!-- id: 3 -->
- [x] 4. Optimize `AiMultiAgentCoordinator.java` & `AiTestGeneratorService.java`: concise chat summary (no raw `.md`/code in chat), multi-scenario expansion (Happy/Negative/Boundary), multi-assertion synthesis, 46-assertion alias normalization, pre-compiled regexes, and batch `applyCasesToSuite` <!-- id: 4 -->
- [x] 5. Resolve Round 1 system review findings across `AssertCodeGenerator`, `ProjectStore`, `DataCleanupService`, `LocalDataIndexService`, `AiChatHistoryStore`, `LiveDomScanner`, `TestRunnerService`, and `DocumentParserService` <!-- id: 5 -->
- [x] 6. Fix `ProjectStore` granular update methods to synchronize with H2 `LocalDataIndexService` via a shared `syncSuiteIndex` helper, and improve `collectSingleCase` matching <!-- id: 6 -->
- [x] 7. Fix `LocalDataIndexService.deleteGroup` cascade cleanup and `ProjectStore.renameGroup` recursive suite/case re-indexing <!-- id: 7 -->
- [x] 8. Integrate `AiAgentIndexService.archiveAndIndexExport` into `ExcelReportService.export` and `HtmlReportService.export`, and set default `StudioPaths.getExportsDir()` in export dialogs <!-- id: 8 -->
- [x] 9. Optimize `SettingsStore.load()` and unify `ProjectStore.DEFAULT_MAPPER` across `ReportStore`, `SettingsStore`, and `OptimizedSelfHealingExecutor` <!-- id: 9 -->
- [x] 10. Resolve Round 3 system review findings: `AiAssistantPageController.handleUndoMessage` user-bubble & `suiteSnapshotDir` rollback resolution + `configureExportChooserDirectory`, and `LocalDataIndexService.deleteSuite` / `clearChatForSuite` cascade cleanup <!-- id: 10 -->
- [x] 11. Resolve Round 4 findings: `AiCasePromptDialog` steps/data/priority persistence, full assertion catalog & `Assertion.getEffectiveValue()` across `AssertTypeDisplay` / `AssertCodeGenerator` / `AssertBuilderPopupController` / `SuiteWorkspacePageController` / `LocalDataIndexService`, safe script file resolution in Workspace/Explorer, recursive `syncGroupDirectory` in `ProjectStore.renameGroup`, and shared `ProjectStore.DEFAULT_MAPPER` <!-- id: 11 -->
- [x] 12. Add Round 4 regression unit tests in `AiAgentIndexingAndOptimizationTest.java` and update `tasks/learnings.md` <!-- id: 12 -->
