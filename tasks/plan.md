# Implementation Plan: AI Agent Test Case Generation & Direct Download with Conditional Script Omission

## Overview
Implement deep knowledge-driven test case generation from requirements/documents and direct export (Excel, Markdown, JSON) from the AI Assistant, with smart detection to omit test scripts when no test page URL is provided.

---

## Slices

### Slice 1: Backend - Direct Test Case Export in `TestCaseExcelExportService` & Markdown/JSON Exporters
- Add `exportAiGeneratedCases(List<AiGeneratedCase> cases, String summary, Path outputPath, ExportLanguage lang)` in `TestCaseExcelExportService.java`.
- Generates professional 3-sheet Excel workbook (`Tổng quan`, `Danh sách Test Cases`, `Chi tiết từng bước`) with existing standard styles, badges, and metrics.
- Add helper methods to format `List<AiGeneratedCase>` as clean Markdown and JSON.
- Add unit tests in `src/test/java/com/studio/service/TestCaseExcelExportServiceTest.java`.

### Slice 2: Prompt Engineering & Service - Conditional Script Omission in `AiTestGeneratorService`
- Update `AiTestGeneratorService.java` to support a `generateScript` boolean flag (defaulting to auto-detection: true if prompt/docs/dom have URL, false otherwise).
- In system prompt and user context, inject explicit guidance: when `generateScript == false`, strictly do not generate `scriptCode` (`scriptCode = ""`), and focus 100% on detailed test steps, preconditions, BVA test data sets, and assertions.
- Add unit tests in `src/test/java/com/studio/service/AiTestGeneratorServiceTest.java`.

### Slice 3: UI Enhancement - Direct Download Action & Script Toggle in `ai_assistant_page.fxml` and `AiAssistantPageController`
- In `ai_assistant_page.fxml`:
  - Add a "Tạo Test Script" CheckBox in the input controls area.
  - Add "Tải về Test Case" / "Xuất bộ Test Case" MenuButton / SplitMenuButton next to "Nhập vào Suite" in the preview header (with options: Excel .xlsx, Markdown .md, JSON .json).
- In `AiAssistantPageController.java`:
  - Wire up `generateScriptCheck` with auto-toggle when prompt changes or attachment is loaded.
  - Wire up export menu handlers (`exportToExcel`, `exportToMarkdown`, `exportToJson`) using `FileChooser`.
  - When selecting a case with empty/no `scriptCode`, display a clear guidance notice in `previewScriptEditor` and focus by default on `previewStepsTab`.

### Slice 4: Full Verification & Learning Capture
- Run `mvn clean test` across the entire project.
- Verify zero regressions and check generated file outputs.
- Record institutional learnings in `tasks/learnings.md`.
