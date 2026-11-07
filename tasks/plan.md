# Implementation Plan: Post-Run Reporting & Evidence System

## Slices of Delivery

- [x] **Slice 1: AI Assistant & Document Parsing (Completed)**
  - Tích hợp OpenAI model, document parser (docx, pdf, images), dialog cài đặt & workspace AI prompt.

- [x] **Slice 2: Standalone HTML Report Generation Service**
  - Tạo `HtmlReportService.java` sinh báo cáo HTML tự chứa (standalone, single-file HTML).
  - Tích hợp biểu đồ tiến độ, thống kê Pass/Fail/Skip, danh sách case, chi tiết từng Assertion.
  - Nhúng ảnh chụp màn hình bằng chứng dưới dạng Base64 data URLs.
  - Unit tests cho `HtmlReportServiceTest`.

- [x] **Slice 3: Auto-Navigation to Report & Evidence on Test Completion**
  - Cập nhật `SuiteWorkspacePageController.java` (`runCaseTarget`, `runSuite`): tự động mở `ViewNavigator.navigateToReportDetail(report.getRunId())`.
  - Cập nhật `ExplorerPageController.java` (`runTargetItem`, `runAll`): tự động mở `ViewNavigator.navigateToReportDetail(report.getRunId())`.

- [x] **Slice 4: Enhanced Report Detail & Evidence Viewer UI**
  - Cập nhật `report_detail_page.fxml` & `ReportDetailPageController.java`:
    - Thêm nút "🌐 Xuất HTML" (`exportHtmlBtn`).
    - Thêm cột "📸 Bằng chứng" vào `casesTable` cho phép xem nhanh ảnh chụp màn hình.
    - Cải thiện hiển thị ảnh chụp màn hình bằng chứng, hỗ trợ phóng to và mở với trình xem ảnh hệ thống.
    - Hỗ trợ Playwright trace button nếu có file trace.
  - Cập nhật `ReportsPageController.java` (danh sách lịch sử): thêm nút xuất HTML nhanh.

- [x] **Slice 5: Verification & Full Suite Run**
  - Chạy toàn bộ test suites (`mvn test`).
  - Cập nhật tài liệu `README.md` & `tasks/learnings.md`.
