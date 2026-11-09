# Release Notes — Test Studio

## Test Studio v2.4.1

**Release date:** 2026-11-09

Phiên bản vá **Test Studio v2.4.1** khắc phục triệt để lỗi xung đột vòng đời DatabaseManager trên môi trường Windows CI, đảm bảo tính toàn vẹn và cô lập dữ liệu giữa các tiến trình kiểm thử tự động, đồng thời nâng cao độ tin cậy của dịch vụ giải phóng tài nguyên hệ thống (ResourceReleaseService).

---

### Các cải tiến & sửa lỗi trong bản vá v2.4.1 (Bug Fixes & Improvements)

#### 1. Khắc phục xung đột vòng đời cơ sở dữ liệu H2 (Database Lifecycle Management)
- **Tự động tái tạo Singleton Instance sau Shutdown**: `DatabaseManager.getInstance()` tự động nhận diện và tái tạo một instance cơ sở dữ liệu đang hoạt động nếu instance trước đó đã bị đóng (`isShutdown`), ngăn chặn hoàn toàn lỗi `SQLException: DatabaseManager has been shut down` trong cùng tiến trình JVM.
- **Dọn dẹp tài nguyên chuẩn xác**: Phương thức `DatabaseManager.shutdown()` tự động giải phóng tham chiếu singleton `instance = null`.

#### 2. Cô lập trạng thái kiểm thử (Test State Isolation & Resets)
- **Bổ sung cơ chế Reset cho ResourceReleaseService**: Cho phép đặt lại cờ `IS_SHUTTING_DOWN` và xóa các hook cleanup trong môi trường kiểm thử đơn vị.
- **Hoàn thiện chu trình dọn dẹp `@AfterEach`**: Bổ sung `@AfterEach` cho `ResourceReleaseServiceTest`, `DataCleanupServiceTest`, và `SettingsDualPersistenceTest` để cô lập hoàn toàn cơ sở dữ liệu nhúng và mock data giữa các test case trên mọi hệ điều hành.
- **Minh bạch hóa lỗi JDBC Indexing**: Bổ sung log cảnh báo chi tiết khi batch indexing chat messages gặp sự cố thay vì nuốt ngoại lệ âm thầm.

---

## Test Studio v2.4.0

**Release date:** 2026-11-08

Phiên bản **Test Studio v2.4.0** là bước tiến vượt bậc trong trải nghiệm AI Testing, mang đến khả năng Quản lý đa phiên hội thoại (Multi-Session Chat Management), Thanh lịch sử thu gọn (Collapsible Sidebar), Bộ tối ưu hóa Token ngữ cảnh lớn (Large Context Token Optimization Engine), và Kiến trúc đồng bộ cơ sở dữ liệu nhúng H2 siêu tốc.

---

### Điểm nổi bật trong phiên bản v2.4.0 (Key Highlights)

#### 1. Quản lý đa phiên hội thoại AI (Multi-Session Chat Management)
- **Độc lập phiên theo Suite / Workspace**: Lưu trữ lịch sử hội thoại độc lập theo từng Suite tại `.ai_chats/<sessionId>.json`. Người dùng có thể tạo không giới hạn các phiên làm việc song song để phục vụ các mục tiêu kiểm thử khác nhau.
- **Thanh bên lịch sử tích hợp (Collapsible Left Sidebar)**:
  - Đóng/mở linh hoạt qua nút `☰ Lịch sử` hoặc phím tắt `Ctrl+H` / `Cmd+H`.
  - Hỗ trợ ô tìm kiếm tức thì theo tiêu đề phiên làm việc.
  - Thao tác một chạm: Tạo phiên mới (`+ Cuộc trò chuyện mới`), Chuyển đổi phiên mượt mà, Ghim phiên lên đầu (Pin to Top), Đổi tên trực tiếp bằng đúp chuột (`double-click`) hoặc qua Menu ngữ cảnh chuột phải.
- **Tự động đặt tên phiên thông minh**: Tự động nhận diện nội dung yêu cầu đầu tiên của người dùng để đặt tên phiên hội thoại ngắn gọn, súc tích (tối đa 28 ký tự).
- **Chốt chặn an toàn khi xóa phiên**: Ngăn chặn người dùng vô tình xóa phiên đang trong tiến trình sinh kịch bản ngầm (`generationProgress`), triệt tiêu hoàn toàn race condition.

#### 2. Bộ tối ưu hóa Token ngữ cảnh lớn (Large-Context Token Optimization Engine)
- **Structured Progressive Rolling Summary**: Khi lịch sử hội thoại kéo dài vượt quá cửa sổ trượt, hệ thống tự động chắt lọc và tổng hợp các mục tiêu, kịch bản đã sinh và quyết định kỹ thuật thành một bản tóm tắt súc tích, duy trì trí nhớ ngữ cảnh dài hạn mà không gây tràn token.
- **Adaptive Sliding Window Context**: Mặc định giữ 6 lượt (12 tin nhắn) gần nhất với cơ chế cưỡng chế giới hạn ngân sách token nghiêm ngặt (`Token Budget Enforcement`), tự động thu nhỏ linh hoạt để không bao giờ vượt quá giới hạn của mô hình.
- **Bulky Code & DOM Truncation**: Tự động nén và thu gọn các khối mã Playwright dài và cây DOM cũ ở các lượt trao đổi trước, cắt giảm đến 75% lượng token tiêu thụ.
- **Live DOM Markdown Representation**: Biến đổi JSON DOM trực tiếp thành bảng Markdown tinh gọn (Candidate Locator, Tag, Role, Text/Label) giúp mô hình nắm bắt chính xác cấu trúc trang thực tế.

#### 3. Lưu trữ nguyên tử & Đồng bộ H2 Database tốc độ cao
- **Cơ chế ghi file nguyên tử (`writeJsonAtomic`)**: Sử dụng file tạm `.tmp` và hoán đổi nguyên tử (`ATOMIC_MOVE`), bảo vệ dữ liệu hội thoại an toàn 100% trước nguy cơ cúp điện hoặc tắt máy đột ngột.
- **Tương thích ngược tự động (Zero-Data-Loss Migration)**: Tự động di chuyển lịch sử cũ từ `.ai_chat.json` sang phiên làm việc đầu tiên ngay khi người dùng mở ứng dụng.
- **Batch JDBC Execution**: Đồng bộ hóa tin nhắn vào bảng H2 `ai_chat_messages` thông qua JDBC Batch (`executeBatch()`), loại bỏ hoàn toàn độ trễ I/O trên UI thread.

---

## Test Studio v2.3.1

**Release date:** 2026-11-08

Phiên bản vá **Test Studio v2.3.1** hoàn thiện hệ thống phát hành kép đa nền tảng (Unified Dual Release CI/CD), khắc phục tính toàn vẹn của gói cài đặt ngoại tuyến Linux Debian (.deb), củng cố bộ cài đặt Windows Inno Setup với cơ chế ghi Registry đa đặc quyền (HKA) và bảo đảm an toàn thực thi tiến trình hệ thống.

---

### Điểm nổi bật trong phiên bản v2.3.1 (Key Highlights)

#### 1. Hệ thống phát hành kép tự động trên CI/CD (Unified Dual Release Pipeline)
- **Xuất bản đồng thời 6 gói cài đặt trên 3 nền tảng**: Tự động build và upload cả bản Portable (ZIP) và bản Cài đặt (Windows Inno Setup `.exe`, macOS `.dmg`, Linux Debian `.deb`) trong cùng một GitHub Release.
- **Trích xuất thông tin phát hành mục tiêu**: Tự động bóc tách đúng phần ghi chú của phiên bản hiện tại đưa vào GitHub Release thông qua công cụ `scripts/extract-release-notes.py`, không nhồi nhét hay lặp lại các phiên bản cũ.

#### 2. Khắc phục gói cài đặt ngoại tuyến Linux Debian (.deb)
- **Tích hợp đầy đủ trình duyệt offline**: Điều chỉnh đường dẫn app-image ưu tiên gói đã chuẩn bị đầy đủ Chromium và Node.js runtime (`dist/TestStudio/TestStudio/browsers`), đảm bảo ứng dụng sau khi cài từ file `.deb` chạy ngay lập tức mà không cần kết nối mạng.
- **Kiểm định toàn vẹn trước khi đóng gói**: Tự động kiểm tra sự tồn tại của thư mục `browsers/` trước khi gọi `jpackage --type deb`, ngăn ngừa hoàn toàn các bản phát hành thiếu dependency.

#### 3. Củng cố bộ cài đặt Inno Setup Windows (.exe)
- **Hỗ trợ cài đặt Non-Admin & Admin mượt mà**: Chuyển khóa cấu hình Registry từ `HKLM` sang `HKA` (HKA auto-switch), giúp người dùng cài đặt cho bản thân (Current User) không bị lỗi phân quyền hay xung đột UAC.
- **Thực thi binary hệ thống an toàn tuyệt đối**: Sử dụng `ExpandConstant('{cmd}')` và `ExpandConstant('{sys}\taskkill.exe')` để đóng ứng dụng đang chạy trước khi ghi đè, loại bỏ hoàn toàn rủi ro PATH hijacking.
- **Cập nhật in-place mượt mà**: Giữ nguyên thư mục cài đặt cũ (`UsePreviousAppDir=yes`), tự động đóng ứng dụng với `taskkill` và cập nhật thông tin phiên bản mới trong Windows Add/Remove Programs.

#### 4. Tối ưu hiệu năng Build & Đồng bộ tài nguyên CI
- **Bộ nhớ đệm giải nén Node.js**: Tận dụng thư mục đã giải nén trong `/tmp`, rút ngắn thời gian đóng gói liên tiếp giữa các script.
- **Đồng bộ hóa tài nguyên**: Cập nhật toàn bộ các script đóng gói và workflow mới nhất vào `src/main/resources/ci/`.

---

## Test Studio v2.3.0

**Release date:** 2026-11-08

Phiên bản **Test Studio v2.3.0** nâng cấp toàn diện trải nghiệm trí tuệ nhân tạo tương tác với Thẻ câu hỏi làm rõ yêu cầu (Interactive Clarification Cards), Trợ lý tư vấn & đánh giá chất lượng kịch bản đàm thoại (Conversational Intelligence & Project Advisor), Cơ chế kiểm duyệt thực thi đồng bộ (Harmonized Execution Guard) và Bộ nút xóa phổ quát đa bề mặt.

---

### Điểm nổi bật trong phiên bản v2.3.0 (Key Highlights)

#### 1. Thẻ tương tác làm rõ yêu cầu AI (Interactive Clarification Cards)
- **Phân tích độ mơ hồ (Ambiguity Analyzer)**: Tự động phát hiện khi câu lệnh kiểm thử của người dùng quá ngắn, thiếu tiền đề hoặc tiêu chí xác minh; ngăn chặn hoàn toàn việc AI đoán mò hay sinh kịch bản sơ sài.
- **Thẻ lựa chọn trực quan trong Chat (Clarification Card)**: Hiển thị danh sách câu hỏi đa chiều (Phạm vi Happy/Negative/BVA, Tiền đề tài khoản, Tiêu chí Assertion) với các chip tùy chọn bo tròn và ô nhập tự do.
- **Tăng cường câu lệnh một chạm (Prompt Augmentation)**: Tổng hợp các lựa chọn làm rõ thành câu lệnh đặc tả chuẩn Agency-Agents và kích hoạt sinh bộ test cases chi tiết.

#### 2. Trợ lý đàm thoại & Cố vấn chất lượng dự án (Conversational Project Advisor)
- **Tìm kiếm kịch bản toàn cục (`searchCases`)**: Tìm nhanh test case trên toàn bộ cây thư mục theo tên, suite, nhóm, URL hoặc mã nguồn Java.
- **Đánh giá & chấm điểm kịch bản theo chuẩn Agency-Agents (`evaluateCase`)**: Kiểm toán chất lượng kịch bản Playwright, xếp hạng từ A đến D, phát hiện lỗi `Thread.sleep`/`waitForTimeout`, thiếu assertion, selector mong manh và xuất mã sửa chữa tối ưu.
- **Phân tích khoảng trống kiểm thử (`analyzeSuiteGaps`)**: Đánh giá độ bao phủ của Test Suite, phát hiện các nhánh kịch bản còn thiếu (Happy path, Negative validation, Boundary value, Security auth).
- **Tư vấn kỹ thuật đối thoại (`CHAT_ADVICE`)**: Trả lời câu hỏi kỹ thuật Playwright/QA trực tiếp dạng Markdown đàm thoại tự nhiên, không ép schema JSON.

#### 3. Bộ nút xóa phổ quát & Cơ chế kiểm duyệt thực thi đồng bộ
- **Bộ nút xóa hoàn chỉnh**: Hỗ trợ xóa Nhóm, Suite và Case tại thanh công cụ Explorer, hàng TreeCell inline, bảng xem trước Preview và Suite Workspace.
- **Đồng bộ điều kiện Runnable**: Đảm bảo cả Explorer Tree và Workspace đều kiểm tra điều kiện ca kiểm thử phải có assertion hoạt động trước khi chạy, tránh tình trạng skip 100% trong runner.
- **Khắc phục đích chọn trên Preview Pane**: Giải quyết dứt điểm lỗi mất tham chiếu mục khi cây thư mục bị xóa lựa chọn.

---

## Test Studio v2.2.0

**Release date:** 2026-11-08

Phiên bản **Test Studio v2.2.0** nâng cấp mạnh mẽ năng lực tự động hóa với Động cơ Tự phục hồi Playwright Locators tối ưu hóa (Optimized Self-Healing Execution Engine), Hộp thoại Trợ lý AI & dịch vụ sinh kịch bản kiểm thử tự động, cùng hệ thống Mô hình Khẳng định (Assertion Models) và Động cơ Xác thực Runtime Playwright bền bỉ.

---

### Điểm nổi bật trong phiên bản v2.2.0 (Key Highlights)

#### 1. Động cơ Tự phục hồi Playwright Locators tối ưu (Optimized Self-Healing Engine)
- **Fast-path Locator Execution**: Tự động chuyển thẳng qua locator gốc khi phần tử còn hợp lệ, loại bỏ hoàn toàn độ trễ và chi phí tính toán khi chạy test bình thường.
- **Đa chiến lược phục hồi Heuristic (<30ms)**: Tự động loại bỏ các định dạng ID động sinh ngẫu nhiên, chuẩn hóa văn bản, ánh xạ ARIA role và đối sánh mờ thuộc tính (attribute fuzzy match).
- **HealedLocatorWrapper & Event Listener**: Bọc trong suốt Playwright Locator, kích hoạt cơ chế tự vá lỗi khi phần tử bị biến đổi giao diện và phát sự kiện phục hồi cho giao diện và nhật ký.
- **Bộ nhớ đệm tự phục hồi (Self-Healing Cache)**: Lưu trữ các chiến lược phục hồi thành công kèm thời gian sống (TTL) và theo dõi tỷ lệ hit rate.

#### 2. Hộp thoại Trợ lý AI & Sinh Kịch bản Tự động (AI Case Prompt Dialog & Test Generation)
- **Hộp thoại sinh test case trực quan (AiCasePromptDialog)**: Tạo ca kiểm thử từ mô tả ngôn ngữ tự nhiên, tùy chọn phong cách viết và đội ngũ chuyên gia AI chuyên sâu.
- **Cơ chế phân tích phản hồi AI 3 tầng siêu bền bỉ (3-Tier Robust Parser)**:
  - *Tier 1*: Phân tích cấu trúc JSON chuẩn.
  - *Tier 2*: Tự động bóc tách từ khối Markdown code block / fenced block.
  - *Tier 3*: Phục hồi bằng biểu thức chính quy (Regex Repair) khi model trả về định dạng khiếm khuyết.
- **Tự động sinh mã Playwright Java & Ma trận dữ liệu**: Sinh trọn gói kịch bản kiểm thử Playwright Java hoàn chỉnh, cấu trúc bộ dữ liệu mẫu (Data Schema) và các bước NLP tương ứng.

#### 3. Mô hình Khẳng định & Động cơ Xác thực Runtime Bền bỉ (Assertion Models & StudioRuntime)
- **Mô hình Assertion chuẩn hóa**: Hỗ trợ 46 loại assertion trải rộng trên 8 nhóm kiểm thử, phân định rõ trạng thái hoạt động (DRAFT, ACTIVE, INACTIVE) và quy tắc đối soát.
- **Tầng khẳng định StudioAssertions & StudioRuntime**: Cung cấp hàm kiểm tra runtime thống nhất, hỗ trợ Soft Assertion chấm điểm độ bao phủ, kiểm tra phần tử hiển thị/trạng thái DOM và chuẩn đoán lỗi chi tiết.
- **Trình biên dịch mã Assertions (AssertCodeGenerator)**: Tự động chuyển đổi mô hình assertion thành câu lệnh Playwright Java kiểm thử native.
- **Cơ chế đối soát kết quả (Reconciliation)**: Liên kết trực tiếp kết quả chạy runtime với danh sách assertions trong ca kiểm thử, cập nhật trạng thái Pass/Fail/Skip chính xác lên báo cáo.

---

## Test Studio v2.1.0

**Release date:** 2026-11-08

Phiên bản **Test Studio v2.1.0** tích hợp trình xem Hướng dẫn sử dụng trực tiếp trong Help Drawer, bộ chuyển đổi tài liệu đa định dạng MarkItDown Hybrid Converter, mở rộng đội ngũ chuyên gia AI Specialist Roster, phân loại lỗi kiểm thử có cấu trúc (thay thế raw stacktrace) và hoàn thiện giao diện/CSS theme.

---

### Điểm nổi bật trong phiên bản v2.1.0 (Key Highlights)

#### 1. Trình xem hướng dẫn sử dụng tích hợp (In-App User Guide Viewer)
- **Tích hợp ngăn trợ giúp (Help Drawer)**: Xem toàn bộ cẩm nang hướng dẫn sử dụng Markdown trực tiếp ngay trong ứng dụng mà không cần rời màn hình làm việc.
- **Hỗ trợ song ngữ & điều hướng linh hoạt**: Tự động chuyển đổi giữa Tiếng Việt và English, mục lục (Table of Contents) nhảy nhanh đến từng chương, tích hợp bộ ảnh chụp giao diện thực tế cho cả 2 chủ đề Sáng & Tối (Catppuccin Mocha).
- **Đóng gói ngoại tuyến**: Dữ liệu hướng dẫn và hình ảnh được nhúng trực tiếp vào JAR/bộ cài offline, truy cập không cần kết nối mạng.

#### 2. Bộ chuyển đổi tài liệu hybrid MarkItDown & Đội ngũ chuyên gia AI (Agency-Agents)
- **MarkItDown Hybrid Document Ingestion**: Trích xuất tài liệu đặc tả kiểm thử, tài liệu nghiệp vụ từ định dạng PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx) và HTML sang Markdown chuẩn xác cho bối cảnh AI.
- **Agency-Agents Specialist Roster**: Tích hợp danh mục chuyên gia chuyên sâu (QA Architect, Security Specialist, Edge-case Analyst, Test Data Engineer) tối ưu hóa kỹ nghệ prompt và chiến lược sinh kịch bản tự động.

#### 3. Báo cáo kiểm thử & chuẩn đoán lỗi có cấu trúc (Structured Test Error Reporting)
- **Chuẩn đoán lỗi thân thiện**: Loại bỏ hoàn toàn stack trace kỹ thuật thô khỏi báo cáo kiểm thử người dùng, thay thế bằng phân loại lỗi có cấu trúc (`LocatorNotFound`, `Timeout`, `AssertionFailure`, `NavigationError`, `NetworkError`).
- **Các bước tái hiện & định vị lỗi**: Tự động tổng hợp các bước tái hiện sự cố và chỉ rõ locator/assertion gây lỗi trên cả báo cáo HTML độc lập và báo cáo Excel chuẩn ISTQB.

#### 4. Tinh chỉnh giao diện, bố cục & hệ thống (UI/UX & System Hardening)
- **Sửa lỗi kích thước hộp thoại (Modal Dialogs)**: Khắc phục triệt để hiện tượng hộp thoại kéo dãn chiếm toàn bộ chiều cao cửa sổ (`ExportLanguageDialog`, `MarkdownPreviewDialog`), tự động co dãn theo nội dung.
- **Hoàn thiện biến chủ đề CSS**: Sửa lỗi `ClassCastException` đối với `-fx-border-color` khi chuyển đổi theme Sáng/Tối runtime, đồng bộ bảng màu Catppuccin Mocha.
- **Ổn định vòng đời tiến trình & kết nối**: Hoàn thiện giải phóng tiến trình nền (Process destruction), bảo vệ Thread Affinity, AST parsing an toàn và quản lý connection pool H2 Database bền bỉ.

---

## Test Studio v2.0.0

**Release date:** 2026-11-08

Major release focusing on visual, AI-assisted, and self-healing Playwright test automation with offline installer packaging.

---

### Key Capabilities in v1.0.0

#### 1. Visual Test Tree & Modern IDE Workspace
- **Three-Level Tree Management**: Group → Suite → Case hierarchy with drag-and-drop reordering, copy/duplicate, and live state filters.
- **Multi-Tab Document Workspace**: `TabManager` supporting simultaneous case script editing, DOM inspection, and assertion building.
- **Inline Hover Action Buttons**: Quick-run, edit, and deletion directly on tree items with smooth animations.
- **Dual-Theme Catppuccin Mocha**: Refined dark theme with vector SVG icons (`UiIcons`).

#### 2. Autonomous AI Assistant & Test Generator
- **Multi-Stage Generation**: Natural language prompts produce dual representation (NLP steps + test data matrix + Playwright Java code).
- **Vietnamese AI Skill Suite**: Built-in specialized skills (`/testplan`, `/edgecase`, `/data`, `/healing`, `/summary`) with automatic heuristic routing.
- **Live DOM Scanner**: In-memory LRU cache with TTL for rapid interactive page element extraction.
- **Vision/Multimodal Inspection**: High-fidelity visual analysis for complex web applications.

#### 3. 5-Tier Self-Healing Runtime Engine
- **Zero-Latency Heuristics (<30ms)**: Intelligent automatic recovery from dynamic IDs, text shifts, and ARIA roles.
- **Dynamic Proxy Locator**: Seamless chaining (`.first()`, `.last()`, `.nth()`, `.filter()`) preserving native auto-waiting.
- **VLM Vision Fallback**: Multimodal visual recovery for altered UI components.
- **Atomic Mutation Persistence**: Automatically updates Java source files and assertion metadata without file corruption risks.

#### 4. High-Speed Embedded Database (H2) & Dual Persistence
- **Dual-Layer Cache**: Durable JSON on disk paired with embedded H2 database for sub-millisecond FTS search and analytics.
- **Recursive Directory Sync**: Automatically discovers and indexes deeply nested groups and suites.
- **Batch Querying**: Eliminates N+1 query latency when loading test suites.

#### 5. Enterprise Reporting & Bilingual Export
- **ISTQB / IEEE 829 Compliant Excel Exports**: 3-sheet execution reports and comprehensive test case documentation.
- **Standalone HTML Report**: Self-contained single-file HTML reports styled with Catppuccin dark theme.
- **Screenshot Evidence Viewer**: Full-screen modal evidence viewer with zoom and path resolution.
- **Bilingual Export Selection**: One-click language switching between Tiếng Việt (default) and English.

#### 6. Enterprise Security & Resource Reclamation
- **AES-256-GCM Key Encryption**: Transparent encryption at rest bound to physical hardware entropy with dual-key fallback.
- **Continuous Secret Sanitization**: Redacts tokens from terminal logs, exceptions, and toasts.
- **Automatic Memory Reclamation**: Uncommits unused heap memory to the OS on window minimization or idle.

#### 7. Multi-Platform Offline Installers
- **macOS Drag-and-Drop `.dmg`**: Full offline package with bundled JRE 26, Node.js 20, and offline Chromium.
- **Windows Setup `.exe`**: Professional Inno Setup installer wizard with desktop shortcuts and uninstaller.
- **GitHub Actions CI/CD**: Automated multi-OS releases across macOS, Windows, and Ubuntu.
- **Run Group** — Recursively run all eligible cases within a group.
- **Run Suite** — Run all eligible cases in a single suite (allows DRAFT/INACTIVE suites for debugging).
- **Run Case** — Run a single case.

#### Shared Browser Context

Multi-case runs (Suite / Group / All) share a **single BrowserContext** by default — cookies, localStorage, and login sessions persist across cases. No need to re-login for each test. Toggle off for isolated execution.

#### Soft-Assert with Detailed Reporting

Assertions use a **soft-assert** pattern — all active assertions execute even if earlier ones fail. Each case's report shows exactly which assertions passed, which failed, and why any were skipped. The run summary displays as `xP / yF / zS` (Passed / Failed / Skipped).

#### Screenshot Capture

Every executed case (both PASSED and FAILED) captures a screenshot. View screenshots inline in the **Report Detail** view or open them with your system's default image viewer.

#### Excel Export

Export any run report to Excel with **3 sheets**:

| Sheet | Contents |
|-------|----------|
| **Summary** | Scope, browser, duration, pass/fail/skip counts |
| **Cases** | Per-case status, duration, skip reason, screenshot path |
| **Asserts** | Per-assertion config status, result status, failure message |

#### Toast Notifications

Every user action produces immediate, non-blocking feedback via toasts at the top-right of the shell window — green for success, gray for info, orange for warnings, red for errors.

#### Portable Distribution

A self-contained bundle that runs on any machine **without installing JDK, Node.js, or Maven**. Just extract and double-click. The portable build includes:

- Embedded JRE with JavaFX modules
- Node.js 20.18.0 LTS (for codegen)
- Playwright CLI (local npm install)
- Chromium browser (PLAYWRIGHT_BROWSERS_PATH preset)

#### Dark Theme

The entire UI uses the [Catppuccin Mocha](https://github.com/catppuccin/catppuccin) dark theme for comfortable extended use.

#### CI/CD

GitHub Actions pipelines:

- **ci.yml** — Build verification on version tags (`v*`) only.
- **release-portable.yml** — Matrix portable build (macOS, Windows, Linux) on the same tags, producing `.zip` artifacts.

---

### System Requirements

#### Development Mode

| Requirement | Version |
|-------------|---------|
| JDK (Temurin recommended) | 26 |
| Maven | 3.9+ |
| (Optional) Node.js | 20+ |

#### Portable Mode

**No prerequisites.** Extract the `.zip` and run the launcher.

---

### Known Limitations (v1)

- **No embedded browser** — Playwright's browser runs as a separate process; there is no JavaFX-embedded WebView for Playwright.
- **No video recording** — Screenshots are captured, but video of test runs is not supported.
- **No email report notifications** — Reports are viewable in-app or exportable to Excel; no SMTP integration.
- **No concurrent multi-user access** — The application is single-user, single-instance.
- **No AI-powered test generation** — Tests are created manually (New Case) or via recording (Playwright codegen). No LLM-based step generation.
- **No many-to-many case sharing** — Each test case belongs to exactly one suite. Use Copy/Duplicate to reuse in other suites.
- **No E2E GUI testing on CI** — The CI pipeline verifies compilation and packages portable builds but does not run the application's GUI tests.
- **Assertions are suite-scoped** — Assertions are defined at the suite level, not per-case (all cases in a suite share the same assertion set).

---

### Upgrade Notes

This is the **first release** — no upgrade path is needed.

If migrating from manual Playwright Java scripts:

1. Organize scripts into the `data/project/` tree (create `group.json` and `suite.json` files).
2. Suite assertions are defined in `suite.json` and automatically injected at runtime.
3. Scripts that call `Playwright.create()` / `browser.close()` will be automatically transformed by the harness — no manual edits required for the shared-context path.
