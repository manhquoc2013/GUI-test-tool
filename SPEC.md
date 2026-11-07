# SPEC.md — AI Assistant for Playwright Automation Studio

## 1. Objective
Tích hợp AI Assistant thông minh sử dụng các model của OpenAI (`gpt-4o`, `gpt-4o-mini`, `o3-mini`, `o1`, hoặc endpoint tương thích OpenAI như Azure OpenAI, OpenRouter, vLLM, Ollama) vào ứng dụng Playwright Automation Studio (JavaFX 26 + Playwright).
AI Assistant hỗ trợ:
1. Đọc và phân tích yêu cầu từ người dùng hoặc từ tài liệu đính kèm:
   - Văn bản thuần túy & Markdown (`.txt`, `.md`, `.json`, `.csv`)
   - Tài liệu Word (`.docx`)
   - Tài liệu PDF (`.pdf`)
   - Hình ảnh / Ảnh chụp màn hình giao diện (Vision - `.png`, `.jpg`, `.jpeg`, `.webp`)
2. Sinh trọn gói Test Case:
   - Metadata: Tên case, mô tả, Start URL
   - Mã kịch bản Playwright Java (`public class ... { public static void main ... }`) tương thích hoàn toàn với `ScriptHarnessTransformer` và `StudioRuntime`
   - Danh sách Assertions có cấu trúc chuẩn theo 46 types của hệ thống (PAGE_CONTAINS_TEXT, ELEMENT_VISIBLE, INPUT_VALUE_EQUALS, v.v.)
3. Cập nhật và bổ sung:
   - Cho phép tạo Suite/Case mới hoặc cập nhật/bổ sung assertions và code vào Case đang chọn.
4. Quản lý cấu hình linh hoạt:
   - Cài đặt OpenAI API Key (hỗ trợ fallback biến môi trường `OPENAI_API_KEY`), Base URL (mặc định `https://api.openai.com/v1`), Model name, Temperature, Max tokens, và nút "Kiểm tra kết nối".

## 2. Architecture & Components

```
com.studio/
├── model/
│   ├── AiConfig.java                # Cấu hình OpenAI (apiKey, baseUrl, model, etc.)
│   └── AiGeneratedCase.java         # DTO kết quả sinh test case từ AI
├── service/
│   ├── DocumentParserService.java   # Parser tài liệu (.txt, .md, .docx, .pdf, images)
│   ├── OpenAiClient.java            # HTTP Client cho OpenAI Chat Completion & Vision
│   └── AiTestGeneratorService.java  # Logic prompt engineering & chuyển đổi test case/script
├── store/
│   └── SettingsStore.java           # Mở rộng Settings để lưu/đọc AiConfig
├── controller/
│   ├── AiAssistantPageController.java  # Màn hình AI Assistant chuyên dụng
│   ├── AiCasePromptDialog.java         # Modal nhanh trong Suite Workspace
│   ├── AiSettingsDialog.java           # Dialog cấu hình OpenAI
│   ├── MainShellController.java        # Tích hợp Sidebar navigation & Settings button
│   ├── SuiteWorkspacePageController.java # Nút "✨ AI Hỗ trợ"
│   └── ViewNavigator.java              # Thêm AI_ASSISTANT Page enum
└── resources/
    ├── fxml/
    │   ├── ai_assistant_page.fxml
    │   └── ai_settings_dialog.fxml
    └── css/studio.css                  # Style Catppuccin Mocha cho AI Assistant
```

## 3. Supported Document Types
- **Text & Code**: `.txt`, `.md`, `.json`, `.csv`, `.xml`, `.html` (Đọc UTF-8 trực tiếp)
- **Word (.docx)**: Đọc paragraphs và tables thông qua Apache POI `poi-ooxml` (đã có sẵn trong pom.xml)
- **PDF (.pdf)**: Đọc văn bản từ các trang qua Apache PDFBox `org.apache.pdfbox:pdfbox:3.0.4`
- **Hình ảnh / UI Vision**: `.png`, `.jpg`, `.jpeg`, `.webp` (Chuyển đổi sang Base64 Data URL gửi trong tin nhắn multimodal)

## 4. UI / UX Design & Workflows
- **Màn hình AI Assistant** (`navAiBtn` trên Sidebar):
  - Khung chat đàm thoại hỏi đáp, nhập prompt yêu cầu
  - Khu vực đính kèm file (File chips với nút xóa, hỗ trợ chọn nhiều file)
  - Chọn đích đến: Nhóm (Group) và Suite đích cần nhập test cases
  - Bảng xem trước (Preview) danh sách Test Cases sinh ra kèm script & assertions
  - Nút "⚡ Áp dụng vào Suite" (Tạo hoặc cập nhật test cases trực tiếp vào `ProjectStore`)
- **Suite Workspace Integration**:
  - Thêm nút "✨ AI Hỗ trợ" trên thanh công cụ của Case Workspace
  - Mở hộp thoại `AiCasePromptDialog` cho phép nhập yêu cầu nhanh (hoặc đính kèm ảnh/tài liệu) để AI tối ưu hóa, sinh kịch bản Playwright hoặc bổ sung Assertions cho Case hiện hành
- **Cấu hình OpenAI Settings**:
  - Hộp thoại trực quan, cho phép kiểm tra API Key với nút "Kiểm tra kết nối" (Ping test)

## 5. Testing & Verification Strategy
- Unit test cho `DocumentParserService`: Kiểm tra đọc `.txt`, `.docx`, `.pdf`, và mã hóa base64 image.
- Unit test cho `AiConfig` & `SettingsStore`: Kiểm tra lưu, tải và fallback biến môi trường.
- Unit test cho `OpenAiClient`: Mock / test serialization, error handling, prompt payload structure.
- Unit test cho `AiTestGeneratorService`: Kiểm tra parse structured JSON từ AI response thành `TestCase`, `Assertion`, và Playwright Java code.
- Verification chạy toàn bộ test suite Maven bằng JDK 26.
