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

## Dual Dark/Light Theme & Vector SVG Icon Architecture
1. **Vector SVG Icon Engine (`UiIcons.java`)**:
   - Khắc phục triệt để lỗi tofu box (`[]`) trên Windows do emoji và Variation Selector `\uFE0F` gây ra trong JavaFX DirectWrite.
   - Sử dụng `javafx.scene.shape.SVGPath` với tọa độ chuẩn 24x24 (Lucide / VS Code Codicons) giúp icon sắc nét ở mọi độ phân giải (100%, 125%, 150%, 200% High-DPI).
   - Màu sắc icon tự động thích ứng với màu chữ của theme (`ui-svg-icon`) hoặc nhận mã màu nhấn trực tiếp (`#f7768e` cho Record, `#9ece6a` cho Run, `#bb9af7` cho AI).

2. **Dual Dark & Light Theme System (`ThemeService.java`, `studio-light.css`)**:
   - `studio.css` đóng vai trò theme tối chủ đạo (Tokyo Night tinh chỉnh).
   - `studio-light.css` cung cấp theme sáng (GitHub/VS Code Light) với độ tương phản cao đạt chuẩn WCAG AAA.
   - Chuyển đổi theme tức thì không cần khởi động lại ứng dụng thông qua nút gạt `Sáng / Tối` trên header bar.
   - Trạng thái theme được lưu bền vững vào `settings.json` thông qua `SettingsStore.Settings` (kèm annotation `@JsonIgnoreProperties(ignoreUnknown = true)` và `@JsonIgnore` trên helper getter để tránh lỗi deserialize của Jackson).

3. **Workspace Toolbar Ergonomic Grouping**:
   - Thay thế việc nhồi nhét 12 nút bấm vào 1 hàng co rúm bằng cấu trúc phân nhóm rõ ràng (Execution Group, Authoring Group, View/Options Group).
   - Ô nhập URL có độ rộng co giãn hợp lý (`prefWidth="260"`, `minWidth="160"`, `maxWidth="340"`), kết hợp spacer `HBox.hgrow="ALWAYS"` giúp các nút chức năng luôn giữ nguyên kích thước và hiển thị đầy đủ nhãn chữ không bị co cụm thành `Ghi ...`, `Chạy ...`.
   - Thiết lập quy tắc CSS `-fx-min-width: -fx-pref-width;` cho mọi nút trên toolbar trong cả `studio.css` và `studio-light.css` nhằm ngăn chặn vĩnh viễn hành vi co ngắn text thành `...` của JavaFX.

4. **Unicode Variation Selector (\uFE0F) & Windows Font Fallback**:
   - Các emoji ký hiệu như `✖️` (`\u2716\uFE0F`), `⚠️` (`\u26A0\uFE0F`), `⏭️` (`\u23ED\uFE0F`) chứa ký tự tàng hình Variation Selector-16 (`\uFE0F`) thường xuyên bị Windows font fallback biến thành ô vuông `[]` (tofu box).
   - Loại bỏ hoàn toàn các emoji này trên toàn bộ hệ thống (Dialog buttons, Toast alerts, Breadcrumbs, Table cells, Tree actions) và thay bằng `UiIcons` vector SVG cùng typography chuẩn giúp giao diện sạch, chuyên nghiệp và không bao giờ lỗi font.

## AI Assistant Chat Management, Copy, Revert/Undo & Token Optimization Architecture
1. **Persistent Chat Sessions per Suite (`AiChatHistoryStore.java`)**:
   - Lưu trữ lịch sử hội thoại độc lập theo từng Suite tại `.ai_chat.json` trong thư mục Suite.
   - Khi QA chuyển đổi giữa các Suite / Group, hệ thống tự động lưu session cũ và nạp session của Suite mới mà không làm mất dòng suy nghĩ hoặc kịch bản đã sinh.
   - Khi không có Suite nào được chọn, tự động fallback về session mặc định tại `StudioPaths.getDataDir()`.

2. **1-Click Message Copy**:
   - Tích hợp nút vector SVG Copy trên từng bong bóng chat (cả người dùng và trợ lý AI).
   - Ghi dữ liệu vào `javafx.scene.input.Clipboard` kết hợp hiển thị thông báo `ToastService.success()` phản hồi tức thì.

3. **Complete Undo / Rollback Engine**:
   - Khác với chat bot thông thường chỉ xóa tin nhắn hiển thị, nút "Hoàn tác" (`Undo`) trong Playwright Automation Studio hỗ trợ khôi phục đa tầng:
     - **Tầng 1 (Session State)**: Xóa cặp lượt hỏi-đáp tương ứng ra khỏi session và lưu bền vững vào `.ai_chat.json`.
     - **Tầng 2 (Preview State)**: Khôi phục danh sách test case sinh ra ở panel xem trước về trạng thái của lượt AI trước đó (sử dụng snapshot `generatedCasesSnapshot` gắn liền với `AiChatMessage`).
     - **Tầng 3 (Suite Files Rollback)**: Khi người dùng đã bấm "Nhập vào Suite", hệ thống tự động ghi lại snapshot trước đó (gồm `suite.json` và toàn bộ mã nguồn file `.java`). Khi bấm Hoàn tác trên tin nhắn đó, hộp thoại xác nhận sẽ hỏi người dùng và tự động rollback: khôi phục `suite.json`, phục hồi nội dung file `.java` đã bị sửa, và xóa bỏ triệt để các file test case `.java` mới được thêm vào.

4. **Comprehensive Token Optimization (`AiTokenOptimizer.java`)**:
   - **Token Estimation Heuristic**: Ước lượng chính xác số lượng token cho đa ngôn ngữ (tiếng Anh ~1.3 tokens/word, tiếng Việt có dấu ~0.4 token overhead/char, và ký tự cú pháp lập trình Java `{`, `}`, `;`).
   - **Live Token Usage Badge**: Hiển thị trực tiếp trên thanh công cụ `Tokens: ~total / 4,000 (Prompt: ~p, Lịch sử: ~h)` cập nhật thời gian thực khi người dùng gõ phím.
   - **Sliding Window Context**: Mặc định chỉ giữ 6 lượt (12 tin nhắn) gần nhất làm ngữ cảnh đưa sang model, ngăn chặn tình trạng tràn context window và bùng nổ chi phí token API.
   - **Automatic Pruned Summary**: Khi các lượt chat cũ bị cắt tỉa khỏi sliding window, bộ tối ưu sẽ tự động trích lọc các ý chính của người dùng thành một bản tóm tắt ngắn gọn đưa vào System Context, giữ vững ngữ cảnh dài hạn với chi phí token tối thiểu.
   - **Document & Attachment Compression**: Làm sạch khoảng trắng thừa, xóa các dòng trống liên tiếp (3+ dòng thu về 1), và cắt gọt an toàn các tài liệu dung lượng lớn trước khi gửi sang API.

## Embedded Local Database (H2) Dual-Layer Cache & Acceleration Architecture
1. **Dual-Layer Read-Through Architecture**:
   - File trên đĩa (`group.json`, `suite.json`, `report.json`, file `.java`) vẫn giữ vai trò lưu trữ bền vững (Single Source of Truth cho Git và người dùng sửa ngoài).
   - Cơ sở dữ liệu nhúng H2 (`com.studio.db.DatabaseManager`) đóng vai trò là tầng Read-Through Cache và Search Index tốc độ cao:
     - Khi ứng dụng khởi động (`App.java`), một daemon thread ngầm `LocalDataIndexService.syncAllFromDisk()` sẽ chỉ mục toàn bộ thư mục dự án và báo cáo vào H2 chỉ trong vài chục mili-giây.
     - Mọi thao tác ghi mới (thêm test case, lưu script, lưu report, lưu tin nhắn AI) đều được đồng bộ tức thì (Write-Through) xuống database.
2. **Sub-millisecond Querying & Analytics**:
   - `ReportStore.listReports()` truy vấn trực tiếp từ bảng `run_reports` trong H2 thay vì phải đệ quy đọc hàng trăm file `report.json` trên ổ cứng.
   - `ReportDbService.getAnalyticsSummary()` tính toán tổng hợp toàn bộ số liệu thống kê (tổng run, tổng case, passed, failed, skipped, tỉ lệ pass %) chỉ bằng 1 câu lệnh SQL `SELECT ... GROUP BY / SUM` tức thì.
   - `ProjectDbService.searchCases()` hỗ trợ tìm kiếm full-text trên cả tên, mô tả và nội dung mã nguồn script Playwright mà không cần mở từng file `.java`.
3. **Pure Java Zero-Dependency Offline Compatibility**:
   - Sử dụng H2 2.2.224 có sẵn trong local `.m2` repository, tương thích tuyệt đối với JDK 26, chạy 100% offline (`mvn -o`), không cần cài đặt thêm C++ binary driver như SQLite.
   - Hỗ trợ chế độ in-memory `jdbc:h2:mem:...` phục vụ kiểm thử đơn vị độc lập và chạy nhanh gấp nhiều lần.


## System Data Cleanup & Dual-Persistence Settings Protection
1. **Dual-Persistence & Automatic Self-Healing Settings**:
   - Cấu hình hệ thống (`SettingsStore`) áp dụng mô hình lưu trữ kép: ghi đồng thời ra file đĩa `data/config/settings.json` và bảng cơ sở dữ liệu H2 `system_settings`.
   - Khi khởi động hoặc nạp cấu hình, nếu file đĩa `settings.json` bị mất, xóa nhầm hoặc hỏng định dạng JSON, hệ thống sẽ tự động khôi phục dữ liệu từ H2 `system_settings` và tái tạo lại file `settings.json` ngay lập tức mà không gây gián đoạn hoạt động.
   - Bổ sung tính năng Sao lưu (Export) và Phục hồi (Import) cấu hình JSON 1-click có xác thực cấu trúc, ngăn ngừa nhập file sai định dạng.
2. **Surgical Multi-Target Data Cleanup (`DataCleanupService`)**:
   - Cung cấp khả năng tính toán dung lượng đĩa sử dụng theo từng nhóm dữ liệu trước khi dọn dẹp (`DiskUsageStats`).
   - Hỗ trợ chính sách lưu giữ thông minh (Retention Policy) cho Báo cáo kiểm thử: thay vì xóa trắng toàn bộ, người dùng có thể tùy chọn giữ lại N báo cáo gần nhất (ví dụ: giữ 5 hoặc 10 báo cáo mới nhất) để đối chiếu lịch sử regression testing.
   - Khi dọn dẹp, dịch vụ dọn sạch cả file trên đĩa cứng (`data/reports/`, ảnh chụp màn hình, file `.ai_chat.json`, thư mục tạm `work/`) lẫn bản ghi trong cơ sở dữ liệu H2 (`run_reports`, `ai_chat_messages`) để đảm bảo tính nhất quán dữ liệu 100%.
   - Nguyên tắc bất khả xâm phạm: Thư mục cấu hình `data/config/settings.json` và bảng `system_settings` được đưa vào danh sách trắng tuyệt đối không bị ảnh hưởng bởi bất kỳ thao tác dọn dẹp nào.
3. **Safety-First Modal UX (`DataCleanupDialog`)**:
   - Cung cấp banner thông tin bảo đảm an toàn cấu hình hệ thống với nút Sao lưu / Phục hồi trực tiếp.
   - Hiển thị dung lượng ước tính của từng mục dữ liệu có thể dọn dẹp và yêu cầu hộp thoại xác nhận trước khi thực thi.
   - Thực thi tác vụ dọn dẹp trên luồng nền (`Task<CleanupResult>`) kèm thanh chỉ báo tiến trình để không làm đông cứng giao diện JavaFX.

## Multi-Tier Autonomous Self-Healing Engine
1. **Multi-Tier Resilience (Midscene + Skyvern + Testsigma Architecture)**:
   - **Tier 0 (Smart Wait Buffer)**: Chờ 350-400ms để triệt tiêu hiệu ứng UI animation/hydration trước khi kết luận selector bị hỏng.
   - **Tier 1 (LocateCache)**: Lưu đệm selector đã sửa thành công trong cùng phiên chạy; nếu cùng một selector bị hỏng ở các bước tiếp theo hoặc vòng lặp, hệ thống sử dụng lại kết quả cache trong <1ms.
   - **Tier 2 (Fast Heuristic Repair)**: Khắc phục >80% lỗi selector do dynamic ID (`#btn-18291` ➔ `[id^='btn']`), CSS hash (`.btn__3x9a` ➔ `.btn`), hoặc text change (`text='Submit'` ➔ `button:has-text('Submit')`, `[role='button']:has-text(...)`, `[data-testid=...]`) ngay trong DOM trình duyệt với độ trễ <30ms và zero chi phí AI.
   - **Tier 3 (Multimodal VLM Recovery)**: Chỉ khi Heuristic thất bại mới kích hoạt VLM qua `OpenAiClient` với ảnh JPEG nén và cây DOM đã qua bóc tách chuyên sâu (`EnhancedDomExtractor`) xuyên thấu Shadow DOM và lọc bỏ phần tử vô hình/che khuất.
   - **Tier 4 (Sandbox Verification)**: Chỉ chấp nhận selector thay thế khi thỏa mãn nghiêm ngặt `count() == 1 && isVisible() && isEnabled()`.
2. **Persistent Auto-Mutation & Audit Trail**:
   - Tự động thay thế selector cũ bằng selector mới trong mã nguồn file script `.java` và `suite.json` (cho assertions), giúp các lượt chạy sau không cần tốn thời gian sửa lỗi lại.
   - Lưu vết chi tiết từng bước được phục hồi (`HealedStepMeta`) vào `CaseResult`, xuất bảng thống kê Self-Healing và huy hiệu `[Auto-Healed]` trong báo cáo HTML.

## AI Agent Test Generation, Automation & Script Optimization Engine
1. **Dual Representation Architecture (Testsigma + Midscene Synthesis)**:
   - Thay vì chỉ sinh ra mã Java Playwright thô sơ, AI Agent cung cấp kiến trúc biểu diễn kép (Dual Representation):
     - **Tầng khai báo NLP Test Steps** (`TestStep`): `stepNumber`, `action` (`NAVIGATE`, `CLICK`, `FILL`, `ASSERT_VISIBLE`, ...), `target`, `testData`, `description`, `expectedResult`. Dễ đọc, dễ review cho Product Owner / Manual QA.
     - **Tầng mã nguồn thực thi** (`scriptCode`): Mã nguồn Java Playwright hoàn chỉnh, tuân thủ quy ước Studio Runtime, tích hợp sẵn wait condition và self-healing wrapper.
2. **Autonomous Self-Healing Code Injection**:
   - Mọi tương tác giao diện sinh ra đều được AI bọc bằng cơ chế `StudioRuntime.healLocator("selector", "mô tả hành động")` kết hợp `waitForLoadState(DOMCONTENTLOADED)` để đảm bảo kịch bản tự thích ứng và bền vững trước các thay đổi DOM trong tương lai.
3. **Data-Driven & Boundary Value Analysis (`TestDataItem`)**:
   - AI Agent tự động đề xuất ma trận dữ liệu kiểm thử (Equivalence Partitioning, Boundary Values, Edge Cases) gắn kèm test case: các kịch bản chuẩn (Happy Path), thiếu trường bắt buộc, ký tự đặc biệt, vượt quá độ dài tối đa, v.v.
4. **Live DOM-Aware Context Injection (`LiveDomScanner` + `EnhancedDomExtractor`)**:
   - Học hỏi từ Midscene và Skyvern: Thay vì để AI "đoán mò" (hallucination) selector từ văn bản yêu cầu, người dùng có thể nhấp "Quét Live DOM" từ URL thực tế.
   - `LiveDomScanner` trích xuất danh sách phần tử DOM tương tác thực tế, xuyên thấu Shadow DOM, lọc ID động rác, và nén thành bảng Markdown nhỏ gọn (tiết kiệm ~70% token) nạp trực tiếp vào ngữ cảnh của AI Model.
5. **Pre-Execution Script Linting & Auto-Fixing**:
   - `AiTestGeneratorService.validateAndFixScript` kiểm tra tính hợp lệ cú pháp Java, tự động bọc class/method nếu thiếu, loại bỏ các lệnh tạo browser bị cấm (`Playwright.create()`, `page.close()`), bảo đảm 100% script sinh ra đều sẵn sàng biên dịch và chạy được ngay.


