# Institutional Learnings: OpenAI Assistant Integration

## Architectural Decisions
1. **Lightweight Native HTTP Client**:
   - Sử dụng `java.net.http.HttpClient` kết hợp Jackson `ObjectMapper` (đã có sẵn trong project) để gọi OpenAI API thay vì đưa vào các thư viện bên ngoài nặng nề hoặc không tương thích JDK 26.
   - Cho phép tùy biến endpoint `baseUrl` (tương thích Azure OpenAI, OpenRouter, LocalAI, Ollama, vLLM).
   - Hỗ trợ Vision API bằng cách chuyển đổi hình ảnh đính kèm thành base64 data URLs trong tin nhắn multimodal.

2. **Document Parsing via POI & PDFBox**:
   - Tận dụng `poi-ooxml` đã có sẵn để trích xuất cả paragraphs và bảng từ `.docx`.
   - Thêm `org.apache.pdfbox:pdfbox:3.0.4` để đọc text từ tài liệu `.pdf`.

3. **Two-Tier Integration UX**:
   - **Màn hình AI Assistant chuyên dụng** trên Sidebar (`🤖 Trợ lý AI`): phục vụ sinh test suite mới, phân tích tài liệu toàn diện, đàm thoại nhiều lượt.
   - **Modal nhanh trong Suite Workspace** (`✨ AI Hỗ trợ`): cho phép QA đang xem một test case cụ thể có thể nhờ AI tối ưu hóa kịch bản hoặc bổ sung Assertions ngay lập tức mà không cần chuyển màn hình.

4. **Security & Configuration**:
   - API key được lưu trữ trong `settings.json`, đồng thời hỗ trợ fallback từ biến môi trường `OPENAI_API_KEY`, `OPENAI_BASE_URL`, `OPENAI_MODEL` để thuận tiện trong môi trường CI/CD và bảo mật.

## Reporting & Evidence Learnings
1. **Auto-Navigation Workflow**:
   - Tự động chuyển hướng từ Workspace hoặc Explorer sang `ViewNavigator.navigateToReportDetail(report.getRunId())` giúp QA thấy ngay bằng chứng và kết quả kiểm thử mà không phải vào thanh menu tìm kiếm thủ công.
2. **Self-Contained Standalone HTML Reports**:
   - Nhúng ảnh chụp màn hình trực tiếp bằng Base64 Data URL giúp báo cáo HTML độc lập hoàn toàn, dễ chia sẻ qua email, Slack hoặc CI artifacts mà không lo gãy đường link hình ảnh.
   - Kết hợp bảng điều khiển tương tác (lọc Pass/Fail/Skip bằng inline JS) mà không cần CDN bên ngoài.
3. **Evidence Modal Viewer**:
   - `EvidenceViewerDialog` cung cấp trải nghiệm xem ảnh chụp sắc nét với nút mở trực tiếp trên desktop hoặc sao chép đường dẫn, phục vụ điều tra lỗi nhanh chóng.

## Modern Pro IDE UI/UX Redesign Learnings
1. **Multi-Tab Document Bar System**:
   - `TabManager` cho phép quản lý vòng đời tab độc lập, giữ nguyên trạng thái controller/view khi chuyển đổi qua lại giữa các Suite, Report, Explorer và AI Assistant mà không bị reload lại UI hay mất dữ liệu đang soạn thảo.
   - Trạng thái chưa lưu (dirty state `●`) được đồng bộ hóa tức thì từ Code Editor lên tab item và cảnh báo khi đóng hoặc chuyển tab.
2. **JavaFX Thread Concurrency in Unit Tests**:
   - Khi chạy kiểm thử trong môi trường JavaFX, một khi JavaFX Toolkit đã được khởi tạo bằng `Platform.startup()`, các lệnh gọi `Platform.runLater(listener)` sẽ chạy bất đồng bộ trên FX Application Thread.
   - Do đó, các unit test lắng nghe sự kiện của `RunCancelService` và `RunProgressService` cần có cơ chế polling đợi ngắn (`Thread.sleep(20)`) để đảm bảo không bị race condition giữa thread test và FX application thread.
3. **Ergonomic Workspace Layout (Side-by-Side Split View)**:
   - Việc chuyển từ dạng tab luân phiên (Tabs Code vs Asserts) sang dạng hiển thị song song (Side-by-Side SplitPane với nút `[◫ Chia đôi] / [🗖 Toàn màn hình]`) cho phép QA vừa nhìn thấy mã kịch bản Playwright vừa xem và quản lý danh sách 46 Assertions mà không bị đứt đoạn ngữ cảnh.
   - Thanh tác vụ hợp nhất (Consolidated Action Bar) gom gọn toàn bộ luồng nghiệp vụ chính: URL bắt đầu, Ghi hình Playwright codegen, Chạy case, Chạy suite, Trợ lý AI và Lưu code trên cùng một hàng trực quan.
4. **Explorer Tree Inline Hover Actions**:
   - Cung cấp các nút thao tác nhanh `[▶]`, `[+]`, `[✏]`, `[📋]` hiển thị tự động khi rê chuột (`hover`) vào từng nút trong cây thư mục (Group, Suite, Case) giúp giảm đáng kể số lần nhấp chuột so với việc phải mở context menu chuột phải hoặc chuyển sang panel xem trước.
   - Sử dụng các filter chip trạng thái (`[Tất cả]`, `[🟢 Hoạt động]`, `[📝 Nháp]`, `[⚪ Ngừng]`) kết hợp ô tìm kiếm tức thì mang lại tốc độ lọc và tìm kiếm test case cực kỳ nhanh chóng.
