# Implementation Plan: OpenAI Assistant Integration

## Slices of Delivery

- [x] **Slice 1: Dependencies & Data Models**
  - Thêm Apache PDFBox vào `pom.xml`
  - Tạo `AiConfig.java` và `AiGeneratedCase.java`
  - Cập nhật `SettingsStore.java` để lưu/tải cấu hình AI và fallback biến môi trường `OPENAI_API_KEY`
  - Unit tests cho AiConfig & SettingsStore

- [x] **Slice 2: Document & Image Parsing Service**
  - Tạo `DocumentParserService.java` hỗ trợ:
    - Text/Markdown/JSON/CSV
    - Word (.docx) qua Apache POI
    - PDF (.pdf) qua Apache PDFBox
    - Image to Base64 Data URL
  - Unit tests kiểm tra đọc các định dạng tài liệu

- [x] **Slice 3: OpenAI Client & Test Generation Service**
  - Tạo `OpenAiClient.java` (Java native HttpClient, chat completions, test connection, vision messages)
  - Tạo `AiTestGeneratorService.java` (Prompt system chuyên biệt cho Playwright Studio, parser sinh test case + kịch bản Java + assertions)
  - Unit tests kiểm tra prompt synthesis và parsing response

- [x] **Slice 4: UI Components (AI Settings & Workspace Modal)**
  - Tạo `AiSettingsDialog.java`
  - Tạo `AiCasePromptDialog.java` cho Suite Workspace
  - Tích hợp nút "✨ AI Hỗ trợ" trong `SuiteWorkspacePageController`

- [x] **Slice 5: Full AI Assistant Page & Navigation**
  - Tạo `ai_assistant_page.fxml` và `AiAssistantPageController.java`
  - Cập nhật `ViewNavigator.java` (thêm `Page.AI_ASSISTANT`)
  - Cập nhật `MainShellController.java` & `main_shell.fxml` (thêm nút `navAiBtn`, `settingsBtn`)
  - Cập nhật styling `studio.css` chuẩn theme Catppuccin Mocha

- [x] **Slice 6: End-to-End Verification & Documentation**
  - Chạy toàn bộ test suites Maven (`mvn test`)
  - Cập nhật README.md hướng dẫn sử dụng tính năng AI Assistant
  - Verification & Final Review
