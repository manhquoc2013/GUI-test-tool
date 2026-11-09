# Todo List: AI Agent Test Case Generation & Direct Download with Conditional Script Omission

- [x] 1. Implement `exportAiGeneratedCases` in `TestCaseExcelExportService.java` (Excel 3-sheet export for AI cases) and Markdown/JSON export utilities <!-- id: 1 -->
- [x] 2. Add unit tests for `exportAiGeneratedCases` in `TestCaseExcelExportServiceTest.java` and verify with Maven <!-- id: 2 -->
- [x] 3. Update `AiTestGeneratorService.java` with conditional test script generation based on URL detection & user toggle <!-- id: 3 -->
- [x] 4. Add unit tests for conditional script omission in `AiTestGeneratorServiceTest.java` <!-- id: 4 -->
- [x] 5. Enhance `ai_assistant_page.fxml` and `AiAssistantPageController.java` with export menu button, script generation toggle, and empty script placeholder <!-- id: 5 -->
- [x] 6. Run full verification (`mvn clean test`), verify end-to-end flow, and update `tasks/learnings.md` <!-- id: 6 -->
