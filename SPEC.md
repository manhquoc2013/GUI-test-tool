# SPEC.md — AI Assistant, Reporting & Evidence System for Playwright Automation Studio

## 1. Objective
1. **AI Assistant**: Tích hợp trợ lý AI thông minh sử dụng các model OpenAI (`gpt-4o`, `gpt-4o-mini`, `o3-mini`, `o1`, hoặc endpoint tương thích OpenAI) để đọc tài liệu spec (.txt, .md, .docx, .pdf), ảnh UI vision và sinh trọn gói test cases, kịch bản Playwright runnable và Assertions.
2. **Reporting & Evidence System (Báo cáo & Bằng chứng thực thi)**:
   - Tự động mở trang **Chi tiết Báo cáo & Bằng chứng (Report Detail)** ngay sau khi hoàn thành chạy test (Run All, Run Group, Run Suite, Run Case).
   - Hiển thị trực quan bằng chứng ảnh chụp màn hình (Screenshots - PASSED & FAILED) cho từng test case ngay trong bảng và thư viện ảnh.
   - Hỗ trợ xem chi tiết từng bước kiểm tra (Assertions breakdown), kết quả mong đợi vs thực tế, log lỗi.
   - Bổ sung tính năng **Xuất báo cáo HTML độc lập (Standalone HTML Test Report)** chứa toàn bộ số liệu thống kê, biểu đồ pass/fail, log thực thi và ảnh bằng chứng Base64 nhúng sẵn để có thể mở offline trên bất kỳ trình duyệt nào.
   - Giữ nguyên và nâng cấp xuất báo cáo Excel (.xlsx) đa Sheet (Summary, Cases, Asserts).
   - Tích hợp trình xem bằng chứng Playwright Trace Viewer (`.zip`) cho các trường hợp kiểm tra nâng cao.

## 2. Architecture & Components

```
com.studio/
├── model/
│   ├── AiConfig.java                # Cấu hình OpenAI (apiKey, baseUrl, model, etc.)
│   ├── AiGeneratedCase.java         # DTO kết quả sinh test case từ AI
│   ├── CaseResult.java              # Kết quả thực thi case, screenshot path, trace path
│   └── RunReport.java               # Báo cáo tổng thể đợt chạy test
├── service/
│   ├── DocumentParserService.java   # Parser tài liệu (.txt, .md, .docx, .pdf, images)
│   ├── OpenAiClient.java            # HTTP Client cho OpenAI Chat Completion & Vision
│   ├── AiTestGeneratorService.java  # Logic prompt engineering & chuyển đổi test case/script
│   ├── HtmlReportService.java       # Sinh báo cáo HTML standalone kèm ảnh bằng chứng
│   ├── ExcelReportService.java      # Xuất báo cáo Excel .xlsx 3 sheets
│   └── TestRunnerService.java       # Điều phối chạy test, chụp ảnh & ghi trace
├── controller/
│   ├── ReportDetailPageController.java  # Màn hình chi tiết báo cáo & thư viện bằng chứng
│   ├── ReportsPageController.java       # Danh sách báo cáo lịch sử
│   ├── SuiteWorkspacePageController.java # Điều hướng tự động sang Report Detail khi chạy xong
│   ├── ExplorerPageController.java       # Điều hướng tự động sang Report Detail khi chạy xong
│   ├── EvidenceViewerDialog.java        # Modal phóng to xem ảnh bằng chứng / trace
│   ├── AiAssistantPageController.java   # Màn hình AI Assistant chuyên dụng
│   ├── AiCasePromptDialog.java          # Modal AI trong Workspace
│   └── AiSettingsDialog.java            # Cài đặt OpenAI
└── resources/
    ├── fxml/
    │   ├── report_detail_page.fxml      # Giao diện báo cáo chi tiết + nút Xuất HTML + Evidence
    │   └── reports_page.fxml            # Danh sách báo cáo
    └── css/studio.css                   # Theme Catppuccin Mocha cho Report & Evidence
```

## 3. Evidence & Reporting Features
1. **Tự động điều hướng Báo cáo (Auto-navigate)**:
   - Khi chạy test tại Workspace (Run Suite, Run Case) hoặc Explorer (Run All, Run Group, Run Suite, Run Case), khi runner hoàn tất thành công hoặc thất bại, hệ thống tự động gọi `ViewNavigator.navigateToReportDetail(report.getRunId())` kèm toast thông báo kết quả.
2. **Ảnh chụp bằng chứng trực quan (Visual Screenshots)**:
   - Mỗi test case khi chạy xong đều có ảnh chụp màn hình tương ứng (`<caseName>_PASSED.png` hoặc `<caseName>_FAILED.png`).
   - Cột "📸 Bằng chứng" hiển thị thumbnail hoặc nút xem nhanh trong bảng `casesTable`.
   - Click đúp hoặc bấm nút mở cửa sổ xem ảnh phóng to kích thước đầy đủ ([`EvidenceViewerDialog`](file:///d:/Works/Tools/GUI-test-tool/src/main/java/com/studio/controller/EvidenceViewerDialog.java)) hoặc mở trong trình xem ảnh mặc định của hệ điều hành.
3. **Báo cáo HTML độc lập ([`HtmlReportService`](file:///d:/Works/Tools/GUI-test-tool/src/main/java/com/studio/service/HtmlReportService.java))**:
   - Xuất file `.html` duy nhất (Single-file HTML Report).
   - Tích hợp biểu đồ tiến độ / tỷ lệ Passed, Failed, Skipped.
   - Nhúng trực tiếp ảnh chụp bằng chứng dưới dạng Base64 Data URL, cho phép gửi qua Email, Slack, Teams mà không bị mất hình ảnh.
   - Bảng phân tích chi tiết từng Assertion (loại kiểm tra, selector/locator, giá trị mong đợi, trạng thái thực tế).
4. **Báo cáo Excel (.xlsx)**:
   - Xuất 3 sheet: Summary, Cases, Assertions.
5. **Playwright Trace Viewer**:
   - Nếu có trace zip, hỗ trợ nút mở xem Playwright Trace chi tiết (network, DOM, timeline).
