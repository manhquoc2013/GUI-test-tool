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

## Framework Harmonization Learnings (Testsigma, Midscene, Skyvern, Automa)
1. **Java Identifier & Class Name Sanitization (`ProjectStore` + `ScriptHarnessTransformer`)**:
   - Tên test case người dùng đặt tự do thường chứa khoảng trắng hoặc từ khóa Java (`"case 2"`, `"default"`, `"test login"`).
   - Khi tạo stub mã nguồn và khi biên dịch harness runtime, bắt buộc phải lọc bỏ ký tự đặc biệt, kiểm tra `SourceVersion.isKeyword(...)` và tự động tiền tố `"C_"` hoặc `"case_2"` để bảo đảm mã nguồn luôn hợp lệ với trình biên dịch `javac`.
2. **Dialog Auto-Acceptance & Non-Blocking Resilience (Skyvern-inspired)**:
   - Các ứng dụng web hiện đại thường kích hoạt `window.alert()`, `confirm()` khi người dùng để trống thông tin hoặc gặp lỗi validation.
   - Nếu không đăng ký listener `onDialog`, Playwright sẽ bị đóng băng (blocking) khi dialog xuất hiện.
   - Việc tiêm tự động listener `StudioRuntime.page().onDialog(d -> d.accept())` vào đầu khối `try { ... }` của harness giúp kịch bản kiểm thử âm tính (Negative Testing) diễn ra mượt mà, không gây timeout 30s.
3. **Dual Representation & Data-Driven Parameterization (Testsigma-inspired)**:
   - Một kịch bản kiểm thử hoàn chỉnh cần song hành: (1) Khai báo các bước NLP rõ ràng cho Manual QA; (2) Ma trận tham số dữ liệu (`testDataSets`) bao quát Happy Path, Validation Failure, và Boundary Values; (3) Mã nguồn Playwright thực thi với cơ chế tự phục hồi selector.

## Storage Atomicity, DOM Caching & Large-Scale Export Learnings
1. **Atomic File Write Protection (`ProjectStore.writeJsonAtomic`)**:
   - Sử dụng ghi file tạm (`.tmp`) rồi thực hiện `Files.move(..., ATOMIC_MOVE)` giúp ngăn chặn triệt để tình trạng file `suite.json`, `group.json` hay `settings.json` bị mất hoặc rỗng dữ liệu khi ứng dụng bị tắt đột ngột.
2. **In-Memory LRU Cache with TTL for Live DOM Scanning (`LiveDomScanner`)**:
   - Tự động lưu trữ cây DOM đã rút gọn theo URL đích với TTL 3 phút và dung lượng 10 mục. Khi người dùng hỏi nhiều câu hỏi liên tiếp về cùng một website, hệ thống phản hồi tức thì mà không cần khởi tạo lại Chromium headless.
3. **Excel Cell Boundary Safeguard (`safeCellText`)**:
   - Apache POI sẽ ném ngoại lệ `IllegalArgumentException` nếu nội dung ô vượt quá 32,767 ký tự.
   - Cơ chế tự động cắt tỉa an toàn (32,700 ký tự) kèm chú thích `[TRUNCATED]` bảo đảm mọi tài liệu Excel và báo cáo kiểm thử đều xuất thành công dù kịch bản có chứa log lỗi hay payload kiểm thử cực lớn.
4. **Bilingual Export Architecture (`ExportLanguage`)**:
   - Hỗ trợ song ngữ Tiếng Việt (mặc định) và Tiếng Anh chuẩn ISTQB/IEEE 829 cho tất cả định dạng xuất (Excel Test Cases, Excel Run Report, HTML Standalone Report) thông qua hộp thoại chọn ngôn ngữ trực quan.





## Multi-Platform Release CI/CD & Resource-Driven i18n Architecture

### 1. Release-Gated CI/CD Workflow (`release-portable.yml`)
- **Release-Only Execution Gate**:
  - Không chạy lãng phí trên các commit push nhánh `main` thường xuyên.
  - Kích hoạt chuẩn xác khi:
    1. Đẩy Git Tag phiên bản: `git push origin v*`.
    2. Xuất bản phiên bản chính thức trên GitHub Releases UI (`release: types: [published]`).
    3. Hoặc kích hoạt thủ công có chủ đích từ tab Actions (`workflow_dispatch` với input `version` tùy chọn).
- **Matrix Build Strategy**:
  - Biên dịch song song độc lập trên cả 3 nền tảng: `ubuntu-latest` (Linux x86_64), `macos-latest` (Apple Silicon arm64), và `windows-latest` (Windows x64).
  - Tự động cài đặt Xvfb (`xvfb-run`) trên Linux để khởi tạo JavaFX Headless Toolkit cho 162 unit tests mà không cần màn hình X11 vật lý.
- **Unified Release Publishing**:
  - Chạy toàn bộ 162 unit tests trước khi đóng gói portable app-image (`jpackage`). Nếu bất kỳ test nào fail, pipeline dừng ngay lập tức, ngăn ngừa phát hành phiên bản lỗi.
  - Job `publish` gom toàn bộ 3 file ZIP portable từ các jobs matrix và tự động upload vào mục Assets của GitHub Release chính thức với `softprops/action-gh-release@v2`.

### 2. Resource-Driven i18n Architecture
- **Zero-Code Extensibility**:
  - Danh mục ngôn ngữ được quản lý động qua `languages.json`. Thêm ngôn ngữ mới không cần sửa mã nguồn Java.
  - Chuỗi bản dịch lưu trữ trong `export_{code}.properties` (chuẩn UTF-8 không cần unicode escape).
  - `I18nService` tự động fallback về tiếng Việt (mặc định) nếu ngôn ngữ mới chưa hoàn thiện toàn bộ key, đảm bảo an toàn tuyệt đối khi vận hành.

## Proactive Resource Reclamation & OS Memory Release Architecture

### 1. Centralized Resource Management (`ResourceReleaseService.java`)
- **App Lifecycle & Window Management**:
  - `primaryStage.setOnCloseRequest(...)` và `App.stop()` được liên kết chặt chẽ với `ResourceReleaseService.releaseAllOnShutdown()`, đảm bảo khi đóng cửa sổ thì mọi tiến trình con (Playwright node driver, Chromium browser, H2 database connection) đều được đóng sạch sẽ.
  - Khi người dùng thu nhỏ cửa sổ (minimize / iconified), `releaseIdleResources()` tự động kích hoạt để thu dọn cache tạm, thực hiện H2 checkpoint và yêu cầu JVM uncommit các trang bộ nhớ heap rỗng trả về cho OS.
  - Đăng ký `Runtime.getRuntime().addShutdownHook(...)` như một chốt an toàn cuối cùng.

### 2. Elimination of Memory and Disk Leaks
- **Harness Artifacts Cleanup**:
  - `TestRunnerService` tạo các file Java/Class tạm thời tại `work/harness/<runId>`. Trước đây thư mục này không được dọn dẹp, gây phình đĩa cứng. Đã thêm cơ chế `deleteQuietly(harnessDir)` trong khối `finally` ngay khi hoàn tất lưu báo cáo.
  - `releaseAfterTestRun()` và `releaseAfterExport()` giải phóng workbook POI và các đối tượng DOM lớn, sau đó kích hoạt GC để OS thu hồi RAM ngay lập tức.
- **Codegen Child Process Guard**:
  - `SuiteWorkspacePageController` lưu vết `activeCodegenProcess` vào `AtomicReference`.
  - Hàm `abortActiveCodegen()` duyệt qua toàn bộ cây tiến trình (`p.toHandle().descendants().forEach(ProcessHandle::destroyForcibly)`) để dập tắt triệt để các tiến trình node/chromium mồ côi nếu người dùng thoát hoặc đóng ứng dụng đột ngột.
- **Shared Connection & Daemon Thread Pool**:
  - `OpenAiClient` tái sử dụng `HttpClient` singleton thay vì tạo mới selector thread pool trên mỗi request.
  - Toàn bộ background worker threads trong `ThemeService`, `AiCasePromptDialog`, `BusyButtons` đều được đặt cờ `setDaemon(true)` để không cản trở JVM shutdown.
- **Embedded Database Reclamation**:
  - `DatabaseManager.shutdown()` chạy `SHUTDOWN COMPACT` để defrag file H2, flush toàn bộ dirty pages và nhả lock file.

## 8. Full Offline Native Installers (macOS .dmg & Windows .exe Setup)
- **Kiến trúc App Image & Installer**:
  - `jpackage` tạo app-image tự chứa OpenJDK runtime tối giản qua `jlink` (`java.base`, `java.desktop`, `java.sql`, `java.naming`, `java.management`, `java.instrument`, `jdk.compiler`, `jdk.unsupported`).
  - Node.js LTS được nhúng portable dưới `tools/node/` và Playwright Chromium + FFMPEG được nhúng dưới `browsers/` trong bundle giúp ứng dụng 100% offline, mở lên chạy ngay không cần internet hay cài đặt thêm.
- **An toàn dữ liệu người dùng khi cài đặt vào thư mục hệ thống**:
  - Khi ứng dụng được cài vào `/Applications` (macOS) hoặc `C:\Program Files` (Windows), người dùng thường không có quyền ghi trực tiếp vào bundle, và việc ghi vào bundle trên macOS có thể vi phạm Code Signing / SIP, đồng thời nâng cấp phiên bản mới sẽ xóa mất dữ liệu cũ.
  - `StudioPaths` được thiết kế thông minh:
    - Nếu chạy ở chế độ portable hoặc dev (thư mục ghi được): Dữ liệu `data/` và `work/` nằm ngay cạnh app.
    - Nếu cài đặt vào `/Applications` hoặc `Program Files`: Tự động chuyển hướng thư mục ghi (`data/`, `work/`) sang thư mục chuẩn của hệ điều hành (`~/Library/Application Support/PlaywrightAutomationStudio` trên macOS, `%APPDATA%\PlaywrightAutomationStudio` trên Windows), trong khi các tài nguyên nhị phân tĩnh (`browsers/`, `tools/`) vẫn đọc trực tiếp từ app bundle.
- **macOS Drag-and-Drop DMG**:
  - Sử dụng `hdiutil` tạo DMG chuẩn Apple với symlink `Applications -> /Applications` và icon app.
- **Windows Inno Setup Wizard**:
  - Kịch bản `installer.iss` cung cấp wizard cài đặt hiện đại, icon Desktop, Start Menu, và Uninstaller tự động. Có fallback sang `jpackage --type exe/msi` nếu máy build chưa có Inno Setup.

## 9. Comprehensive System Review Hardening & Autonomous Optimizations

### 1. Robust Self-Healing Selector Escaping (`OptimizedSelfHealingExecutor.java`)
- **Quoted Selector Persistence**:
  - Khi lưu vết selector tự chữa lành vào mã nguồn Java `.java`, các selector chứa dấu nháy kép (`[data-testid="submit"]`, `button:has-text("Xác nhận")`) phải được xử lý cẩn trọng:
    - Trong file `.java`, chuỗi được escape dưới dạng `\"`.
    - `persistMutation` áp dụng `StringEscapeUtil.escapeJava(...)` cho cả `oldSelector` và `newSelector`, đảm bảo tìm đúng chuỗi literal cần thay thế và ghi ra cú pháp Java hợp lệ, không gây lỗi biên dịch ở các lần chạy tiếp theo.

### 2. Deep Chaining Support for Dynamic Proxy Locators
- **Chained Method Interception**:
  - Khi script Playwright gọi chuỗi phương thức trả về `Locator` con (như `.first()`, `.last()`, `.nth(i)`, `.filter(...)`), Dynamic Proxy trước đây trả về đối tượng `Locator` gốc, làm đứt đoạn khả năng tự chữa lành của các thao tác kế tiếp.
  - Giải pháp: Khi phương thức proxy trả về một `Locator`, tự động bọc lại nó vào một proxy chữa lành mới lưu vết `ChainedStep`. Nếu thao tác sau đó quăng lỗi `PlaywrightException`, hệ thống tự sửa selector gốc và gọi `replay(baseLocator)` để tái hiện chuỗi định vị trên phần tử mới được phục hồi.

### 3. Active-Run Guard for Data Maintenance
- **Data Cleanup Concurrency Guard (`DataCleanupService.java`, `DataCleanupDialog.java`)**:
  - `DataCleanupService.executeCleanup` kiểm tra `RunCancelService.isRunActive()`, ngay lập tức chặn yêu cầu với `IllegalStateException` nếu có một phiên kiểm thử đang chạy.
  - Trên giao diện `DataCleanupDialog`, nút "Bắt đầu dọn dẹp" hiển thị cảnh báo lỗi và chặn thực hiện nếu kiểm thử đang diễn ra, ngăn ngừa triệt để nguy cơ xóa nhầm thư mục `work/harness` và file báo cáo đang được ghi.

### 4. Deterministic Physical Hardware Entropy & Backward Compatibility
- **Stable Machine Binding (`ApiKeyCipher.java`)**:
  - Loại bỏ việc dựa đơn thuần vào thứ tự tên interface mạng (dễ bị đảo lộn bởi các interface ảo như `awdl0`, `bridge0`, Docker, VPN).
  - Thuật toán mới lọc bỏ các interface ảo và ưu tiên các interface vật lý chuẩn (`en0`, `eth0`, `wlan0`), đồng thời tích hợp `os.arch`.
  - Hỗ trợ giải mã kép (Dual-Key Fallback): Nếu khóa mới không giải mã được bản mã cũ (được mã hóa từ các phiên bản trước), hàm giải mã tự động thử tiếp với khóa entropy cũ trước khi báo lỗi, bảo toàn 100% API key đã lưu trữ của người dùng.

### 5. Elimination of N+1 Database Roundtrips (`ProjectDbService.java`)
- **Batch Assertion Loading**:
  - Chuyển đổi từ cơ chế duyệt từng case và bắn `SELECT * FROM test_assertions WHERE case_id = ?` (N+1 queries) sang cơ chế phân lô `WHERE case_id IN (?, ?, ...)` (tối đa 500 ID mỗi đợt).
  - Giảm thiểu số lượt truy vấn database từ `1 + N` xuống chỉ còn `1 + 1` query duy nhất, tăng tốc đáng kể thao tác tìm kiếm và nạp cây kiểm thử.

### 6. Unified Local Browser Resolution (`PlaywrightUtil.java`)
- **Direct Integration with `StudioPaths.getBrowsersDir()`**:
  - `PlaywrightUtil.resolveBrowsersPath()` chuyển sang gọi `StudioPaths.getBrowsersDir()`, tự động tìm kiếm Chromium tại cả `APP_HOME/browsers` và `DATA_HOME/browsers`, đồng bộ hoàn toàn giữa chế độ Portable và Installed.

### 7. Vision Capability Pre-flight Check & Quiet i18n Fallback
- **Pre-flight Model Check (`OptimizedSelfHealingExecutor.isVisionSupported`)**:
  - Kiểm tra năng lực hỗ trợ thị giác của mô hình trước khi chụp ảnh màn hình và đính kèm base64 JPEG trong VLM Tier 3. Các mô hình thuần text (`gpt-3.5-turbo`, `deepseek-chat`, `deepseek-reasoner`, embedding models) được bỏ qua êm thuận mà không phải chịu độ trễ của lỗi HTTP 400 Bad Request.
- **Quiet i18n Logging (`I18nService.java`)**:
  - Loại bỏ log cảnh báo gây nhiễu ra `stderr` đối với các ngôn ngữ tùy chọn chưa có file bundle, chỉ log khi file cấu hình của ngôn ngữ mặc định (`vi`) bị thiếu.

## 10. Autonomous System Remediation & Cross-Platform Integrity

### 1. Cross-Platform Filename & Screenshot Path Sanitization (`TestRunnerService`, `StringEscapeUtil`)
- **Vấn đề**: Tên test case người dùng đặt tự do thường chứa các ký tự cấm trên Windows (`:`, `*`, `?`, `"`, `<`, `>`, `|`) hoặc ký tự phân cấp thư mục (`/`, `\`). Khi `TestRunnerService` tạo đường dẫn ảnh chụp màn hình trực tiếp bằng `cr.getTestCaseName() + "_PASSED.png"`, Windows sẽ ném `InvalidPathException` làm sập chu trình test, còn Linux/macOS sẽ giải quyết thành các thư mục con không tồn tại khiến Playwright chụp ảnh thất bại trong im lặng (`ENOENT`).
- **Giải pháp**:
  - Bổ sung `StringEscapeUtil.safeFileName(name, fallback)` loại bỏ triệt để các ký tự cấm của hệ điều hành và chuỗi traversal (`..`), bảo toàn ký tự khoảng trắng tự nhiên.
  - Áp dụng `safeCaseName` khi khởi tạo `ssPassed`, `ssFailed`, và `harnessScreenshotBase`, đảm bảo kịch bản chạy an toàn và 100% bằng chứng ảnh chụp được ghi nhận thành công trên mọi hệ điều hành.

### 2. Recursive Nested Group Traversal & Indexing (`LocalDataIndexService`)
- **Vấn đề**: `syncProjectDirectory` duyệt cố định 2 vòng lặp cấp 1 (`projectDir` -> `groupDir` -> `suiteDir`). Khi người dùng tổ chức các nhóm lồng nhau (Nested Groups, vốn được `ProjectStore` hỗ trợ đệ quy), toàn bộ các suite và case nằm trong các nhóm con (như `Project/Regression/Auth/LoginSuite`) bị bỏ quên, không được nạp vào H2.
- **Giải pháp**:
  - Tách logic thành hàm đệ quy `syncGroupDirectory(Path groupDir, String groupPath)` duyệt mọi tầng thư mục con chứa `group.json` hoặc `suite.json`.
  - Cập nhật đúng đường dẫn cha lồng nhau (`groupPath + "/" + childName`) vào trường `group_name` của bảng `test_suites` và `test_cases`, đảm bảo tìm kiếm FTS và truy vấn theo cây hoạt động hoàn hảo.

### 3. API Key Leak Prevention in AI Gateway Error Responses (`OpenAiClient`)
- **Vấn đề**: Các gateway AI (LiteLLM, Cloudflare AI Gateway, reverse proxy nội bộ) thường vọng lại (echo) các header HTTP hoặc chuỗi query param chứa Bearer token khi trả về lỗi HTTP 400/401/403. Nếu không lọc bí mật ở tầng ngoại lệ, chuỗi lỗi thô chứa API key sẽ bị lưu vào tin nhắn chat và hiển thị lên thanh Toast UI.
- **Giải pháp**:
  - Bọc `SecuritySanitizer.sanitize(err, apiKey)` ngay trong phương thức `OpenAiClient.chatCompletion` và `fetchModels` trước khi ném `IOException`.
  - Kiểm thử tích hợp bằng `HttpServer` nhúng của JDK chứng minh API key luôn được che giấu thành dấu đạn (`••••`) trong mọi thông điệp ngoại lệ.

### 4. Zero-Corruption Atomic File Writes for Java Scripts (`ProjectStore`, `OptimizedSelfHealingExecutor`)
- **Vấn đề**: Việc gọi trực tiếp `Files.writeString(scriptPath, ...)` có nguy cơ làm hỏng hoặc xóa trắng file `.java` thành 0 byte nếu tiến trình bị kill hoặc máy tính sập nguồn giữa lúc ghi đè (khi lưu file hoặc trong chu kỳ Self-Healing tự sửa locator).
- **Giải pháp**:
  - Triển khai `ProjectStore.writeTextAtomic(targetPath, content)` ghi trước nội dung ra file tạm `.tmp` cùng thư mục, sau đó thực hiện hoán đổi nguyên tử (`ATOMIC_MOVE` với fallback `REPLACE_EXISTING`).
  - Đồng bộ cơ chế này cho `ProjectStore.createCase`, `ProjectStore.saveCaseScript`, và `OptimizedSelfHealingExecutor.persistMutation`.

### 5. Defensive Path Resolution in Excel Exports (`ExcelReportService`)
- **Vấn đề**: `Path.of(raw).getFileName().toString()` ném `NullPointerException` nếu đường dẫn ảnh là root path (`/` hoặc `C:\`) do `getFileName()` trả về `null`.
- **Giải pháp**:
  - Kiểm tra `Path fn = p.getFileName()` trước khi gọi `.toString()` kết hợp khối `try-catch` dự phòng chuyển về nhãn gốc an toàn, ngăn ngừa việc gián đoạn xuất báo cáo Excel khi có case mang đường dẫn ảnh bất thường.

## 11. Light Theme Accessibility & High-Contrast Visual Revamp (`studio-light.css`, `DialogTheme`)

### 1. Root Cause of Invisible Text & Dark Controls Leakage
- **CSS Specificity Leakage**: JavaFX stylesheet layering applies `studio.css` as base and overlays `studio-light.css`. High-specificity rules in `studio.css` (e.g. `.project-tree .tree-cell .tree-row-status.status-active` with specificity `0,4,0`, or `.page-toolbar .toolbar-btn-add` with `0,2,0`) take precedence over single-class rules in `studio-light.css` (specificity `0,1,0`).
- **Missing `.check-box` and `.radio-button` Overrides**: `studio-light.css` previously lacked `.check-box` rules completely, causing light mode dialogs to inherit near-white text (`#e6e9ff`) and dark navy boxes (`#24283b`) on pure white backgrounds (`#ffffff`), producing unreadable 1.1:1 contrast.
- **Tree View Fluorescent Status Labels**: The dark theme neon lime (`#b9f27c`) and peach (`#ffd28a`) leaked into the explorer tree, making status badges unreadable on white backgrounds.

### 2. Comprehensive Remediation
- **Explicit CheckBox & Radio Styling**:
  - Added full `.check-box` and `.radio-button` styling in `studio-light.css` with dark slate text (`#0f172a`), clean borders (`#94a3b8`), crisp blue checked fill (`#2563eb`), and pure white checkmarks (`#ffffff`).
  - Added explicit overrides for `.dialog-pane .check-box` and `.settings-tab-pane .check-box`.
- **WCAG AAA Compliant Status Badges**:
  - Overrode tree and case list status classes with high-specificity selectors:
    - Active/Passed: Deep Forest Green (`#15803d` / selected `#14532d`, contrast > 4.9:1)
    - Ready: Deep Ocean Blue (`#0369a1` / selected `#0c4a6e`, contrast > 4.7:1)
    - Draft: Warm Amber Brown (`#b45309` / selected `#78350f`, contrast > 5.1:1)
    - Inactive: Slate Grey (`#64748b`, contrast > 4.6:1)
    - Failed/Error: Deep Crimson (`#b91c1c`, contrast > 5.8:1)
- **Toolbar & Filter Chips High-Contrast Visuals**:
  - Outlined toolbar buttons with dark slate text (`#1e293b`) and `#cbd5e1` borders.
  - Add button (`toolbar-btn-add`) styled as crisp GitHub-style green (`#15803d`) with white text (`#ffffff`).
  - Filter chips styled with white background, `#334155` text, and clear borders.
  - Spinners (`.spinner`, `.number-spinner`) given clean white background and dark text.
- **Dialog Button Text Truncation Prevention**:
  - In `DialogTheme.styleButtons()` and `AiSettingsDialog`, applied `setMinWidth(Region.USE_PREF_SIZE)` and `ButtonBar.setButtonUniformSize(btn, false)` to prevent custom Vietnamese button text (e.g. "Lưu cài đặt") from being truncated.

## 12. AI Truncation Auto-Repair, Persistent Logging & Diagnostic Telemetry

### 1. Root Cause of `Unexpected end-of-input` in Flash Models
- **Token Budget Exhaustion (`finish_reason: length`)**: Khi người dùng yêu cầu sinh test case kèm thông tin đăng nhập và assertions, các mô hình flash/fast (như `deepseek-v4.1-flash`) thường sinh rất chi tiết cấu trúc PRD (preconditions, testSteps, testDataSets, mã Playwright và mảng assertions). Khi chạm ngưỡng `max_tokens`, stream phản hồi bị ngắt đột ngột giữa chừng (ví dụ ở dòng 192, khi đang viết dở mảng `assertions`).
- **Jackson Strict JSON Parser Failure**: Khi gặp chuỗi JSON bị ngắt giữa chừng, Jackson ném ngoại lệ `com.fasterxml.jackson.core.JsonParseException: Unexpected end-of-input: expected close marker for Array...`. Hệ thống trước đây bỏ qua toàn bộ phản hồi (kể cả mã kịch bản Playwright đã sinh hoàn chỉnh) và báo lỗi ra UI.
- **Substring Slicing Bug in `extractJsonBlock`**: Khi JSON chưa đóng, hàm `s.lastIndexOf('}')` vô tình tìm thấy dấu `}` của class Java bên trong trường `scriptCode` (`public class TC {}`), dẫn đến việc cắt bỏ toàn bộ phần sau của JSON (bao gồm toàn bộ mảng `assertions`) trước khi đưa vào bộ phân tích.
- **No Diagnostics Visibility**: Toàn bộ luồng gọi AI (`OpenAiClient`, `AiTestGeneratorService`, `AiCasePromptDialog`) không hề ghi log ra đĩa hay console, khiến người dùng và lập trình viên không có bằng chứng để kiểm tra payload, latency, hay token count.

### 2. Multi-Tier Resilient Recovery Architecture (`AiTestGeneratorService`)
- **Tier 1 (Strict JSON)**: Phân tích chuẩn bằng Jackson. Nếu cú pháp đầy đủ, hoàn thành ngay.
- **Tier 2 (Self-Healing Auto-Repair `autoRepairTruncatedJson`)**:
  - **Depth-aware Block Extraction**: `extractJsonBlock` duyệt đếm độ sâu ngoặc nhọn `{` và `}` bên ngoài chuỗi string. Nếu depth về 0, cắt chính xác root object; nếu depth > 0 (bị cắt cụt), bảo toàn 100% nội dung đến ký tự cuối cùng thay vì cắt cụt tại một dấu `}` ngẫu nhiên.
  - **Mid-quote String Closure**: Tự động phát hiện và đóng các chuỗi string dở dang, chuyển các ký tự xuống dòng thô (`\n`, `\r`, `\t`) thành JSON escape sequences hợp lệ (`\\n`, `\\r`, `\\t`).
  - **Dangling Commas & Keys Cleanup**: Loại bỏ các dấu phẩy lơ lửng `,` và các key dở dang `, "key"` trước ngoặc đóng bằng vòng lặp regex thay thế liên tục.
  - **LIFO Bracket Stack Closure**: Tự động đóng các cặp ngoặc mảng `]` và đối tượng `}` theo đúng thứ tự ngăn xếp LIFO cho đến khi cấu trúc JSON hợp lệ hoàn toàn.
- **Tier 3 (Tolerant Direct Scraping `extractDirectlyFromRawResponse`)**:
  - Nếu JSON bị hỏng nặng không thể parse, dùng regex tolerant bóc tách trực tiếp mã nguồn Java Playwright từ trường `"scriptCode"` hoặc block ` ```java `, kết hợp trích xuất các assertions đã có.
- **Tier 4 (Markdown Code Block Scraping `extractCasesFromMarkdown`)**:
  - Quét tất cả các khối mã Playwright Java trong Markdown và tự động đóng gói thành các đối tượng `AiGeneratedCase`.
- **Targeted Single-Case Prompting**:
  - Tinh chỉnh prompt sinh single case (`generateOrUpdateSingleCase`): yêu cầu AI tập trung tối đa token vào `scriptCode` và `assertions`, giữ `testSteps` (tối đa 4-5 bước) và `testDataSets` (1 bộ) ngắn gọn, giúp tiết kiệm ~60% token và loại bỏ nguy cơ tràn token.

### 3. Centralized AI Logging System (`AiLoggingService`, `data/logs/ai.log`)
- **File Rotation & Thread-Safety**:
  - Tự động ghi nhật ký tuần tự vào `data/logs/ai.log` với cơ chế xoay vòng tự động khi file vượt quá 5MB (`ai.log.1`).
  - Format log chuẩn chuyên nghiệp: `[YYYY-MM-DD HH:mm:ss.SSS] [LEVEL] [CATEGORY] Message`.
  - Bộ đệm vòng trong bộ nhớ (In-memory ring buffer 200 dòng gần nhất) cho phép truy xuất và render tức thì trên giao diện đồ họa.
- **End-to-End Diagnostic Tracing**:
  - `OpenAiClient`: Ghi nhận URL endpoint, model, max_tokens, số lượng message gửi đi, thời gian phản hồi (latency tính bằng mili-giây), token usage (`prompt_tokens`, `completion_tokens`), và cảnh báo đặc biệt khi `finish_reason == "length"`.
  - `AiTestGeneratorService`: Ghi log từng bước phân tích kết quả (Tier 1 -> Tier 2 -> Tier 3 -> Tier 4).
- **1-Click "Xem Log AI" Dialog**:
  - Bổ sung nút "Xem Log AI" (icon `TERMINAL`) ngay trên thanh tác vụ `AiCasePromptDialog`.
  - Hộp thoại hiển thị log thời gian thực với font Monospace, hỗ trợ nút "Sao chép toàn bộ log" để gửi hỗ trợ kỹ thuật hoặc kiểm tra lỗi ngay lập tức.
  - Sửa màu sắc thông báo trạng thái trên dialog thành mã màu tương phản cao theo theme (`#15803d` cho thành công, `#b45309` cho cảnh báo, `#b91c1c` cho lỗi), khắc phục triệt để lỗi chữ màu đào nhạt khó đọc trên nền trắng.

## 13. Cross-Platform Path Handling & CI/CD Test Isolation (Linux & Windows)

### 1. Root Cause of Failures in GitHub Actions Matrix
- **Windows File Separators (`\`) in Path Normalization**:
  - `StudioPaths.isSystemOrReadOnlyLocation(Path path)` trước đây kiểm tra chuỗi với dấu gạch chéo Unix `/` (ví dụ: `contains("/applications/")`, `contains(".app/contents")`).
  - Khi chạy trên Windows runner, `path.toAbsolutePath().normalize().toString()` biến đường dẫn thành `D:\Applications\PlaywrightAutomationStudio.app\Contents` (dùng dấu gạch chéo ngược `\`).
  - Do đó, các điều kiện kiểm tra với `/` đều trả về `false`, khiến `isSystemOrReadOnlyLocation` thất bại trên Windows.
- **Linux XDG Lowercase Directory Name (`playwright-automation-studio`)**:
  - Trên Linux, `StudioPaths.getDefaultUserDataDir()` tuân thủ chuẩn freedesktop.org XDG nên sử dụng tên thư mục dạng kebab-case: `~/.local/share/playwright-automation-studio`.
  - Trong khi đó, trên macOS và Windows, tên thư mục là dạng PascalCase: `PlaywrightAutomationStudio`.
  - Bài test `StudioPathsTest` trước đây kiểm tra cứng `assertTrue(StudioPaths.getDataHome().toString().contains("PlaywrightAutomationStudio"))` (phân biệt hoa thường), dẫn đến test bị fail trên Linux runner.

### 2. Remediation
- **Cross-Platform Separator Normalization**:
  - Trong `StudioPaths.isSystemOrReadOnlyLocation`, chuẩn hóa cả đường dẫn gốc `raw` và đường dẫn tuyệt đối `abs` sang dạng forward slash `/` (`.replace('\\', '/')`) trước khi so khớp bất kỳ mẫu thư mục hệ thống nào (macOS `/applications/`, Windows `program files`, Unix `/usr/`, `/opt/`, `/etc/`, `/windows/`).
- **OS-Agnostic Assertions**:
  - Cập nhật các assertions trong `StudioPathsTest` kiểm tra nhận diện ứng dụng không phân biệt hoa thường (`.toLowerCase(Locale.ROOT).contains("playwright") && contains("studio")`), tương thích hoàn hảo với cả 3 hệ điều hành (macOS, Windows, Linux).
  - Bổ sung test case `testUnixSystemDirectoriesModeRedirectsDataHome` bao phủ đường dẫn cài đặt chuẩn trên Linux (`/opt/...`).

## 14. Brand & Identity Unification: Test Studio

### 1. Unified Naming Architecture
- **Application Display & Branding**: Đổi tên ứng dụng toàn diện thành **Test Studio** trên toàn bộ hệ thống:
  - Header logo bar: `<Label text="Test Studio" styleClass="app-title"/>`.
  - Window title: `primaryStage.setTitle("Test Studio")`.
  - Giới thiệu trợ lý AI & Help drawer: `Xin chào! Tôi là Trợ lý AI của Test Studio.`
  - System Prompt AI: `You are an expert QA Automation Architect specialized in Playwright Java for "Test Studio".`
  - HTML & Excel Reports: Tiêu đề `<h1>🎭 Test Studio</h1>`, subtitle bar và footer `Report automatically generated by Test Studio` / `Báo cáo được xuất tự động bởi Test Studio`.
  - User-Agent HTTP: `TestStudio/1.0`.
  - Maven POM `<name>`: `Test Studio`.
  - Installer & Packages: `TestStudio` (.dmg cho macOS, .exe Setup cho Windows, .zip cho Portable).

### 2. Backwards-Compatible User Data Migration (`StudioPaths`)
- Để đảm bảo người dùng cũ không bị mất dữ liệu khi cập nhật lên Test Studio:
  - `StudioPaths.getDefaultUserDataDir()` ưu tiên nạp thư mục dữ liệu mới (`~/Library/Application Support/TestStudio` trên macOS, `%APPDATA%\TestStudio` trên Windows, `~/.local/share/test-studio` trên Linux).
  - Tự động fallback về thư mục cũ (`PlaywrightAutomationStudio` / `playwright-automation-studio`) nếu thư mục mới chưa tồn tại nhưng thư mục cũ đã có sẵn dữ liệu từ trước.



## 15. Multi-Agent AI Testing & Self-Healing Architecture (ECC Synthesis)

### 1. Key Insights from ECC-main Analysis
- **Decoupled Roles vs Monolithic Prompting**: Thay vì ép một AI Prompt duy nhất thực hiện từ việc hiểu PRD, sinh test steps, viết code Java Playwright, đến tự kiểm toán; mô hình tối ưu cần phân tách thành 5 tác nhân chuyên trách (`qa-planner`, `playwright-test-engineer`, `test-runner`, `test-self-healer`, `quality-auditor`).
- **Anti-Thrashing Safety Guard**: Trong quá trình tự động sửa lỗi (self-healing), việc thử sai mù quáng dẫn đến cạn kiệt token và code bị tha hóa (code rot). Quy tắc giới hạn cứng tối đa 3 lần vá (max 3 patches per file) kèm kiểm toán `git diff forensics` giúp ngăn chặn hiện tượng trôi dạt logic và lặp vô tận.
- **Assertion Quality & Anti-Silent Failure**: Kiểm thử chỉ có ý nghĩa khi kiểm tra được kết quả nghiệp vụ thực tế. Cấm hoàn toàn assertion rỗng hoặc các khối `try-catch` nuốt ngoại lệ mà không fail test.
- **Dual Representation Alignment**: Kết hợp chặt chẽ các bước NLP có cấu trúc (`testSteps`), tập dữ liệu kiểm thử biên (`testDataSets`), và mã nguồn Playwright Java thực thi (`scriptCode`) đồng bộ, cho phép kiểm toán kép bởi cả con người và máy.

## 16. System Review Hardening, Resource Safety & High-Throughput Optimization

### 1. OS File Descriptor Leak Prevention
- Trong Java NIO, `Files.walk(Path)` trả về `Stream<Path>` giữ các File Handle và Directory Stream của hệ điều hành. Nếu không được bọc trong khối `try (var walk = Files.walk(dir))`, các handle này chỉ được đóng khi GC chạy.
- Trên Windows và Linux server tải cao, việc để hở file handle dẫn đến ngoại lệ `AccessDeniedException` khi người dùng xóa hoặc đổi tên group/suite liên tục. Việc chuẩn hóa toàn bộ `Files.walk` sang `try-with-resources` triệt tiêu hoàn toàn rủi ro này.

### 2. Embedded Database Connection Pooling & Zero-Cost Reuse
- Chế độ H2 `AUTO_SERVER=TRUE` rất mạnh mẽ cho phép nhiều tiến trình truy cập, nhưng mỗi lệnh gọi `DriverManager.getConnection()` phải trải qua cơ chế bắt tay socket nội bộ (internal socket handshake) và kiểm tra file lock.
- Bằng cách bổ sung một bộ đệm Connection Pool gọn nhẹ (BlockingQueue 10 kết nối) với Dynamic Proxy tự động thu hồi (`close()` trả về pool thay vì đóng kết nối vật lý), thời gian thực thi các thao tác đọc/ghi hàng loạt trong `LocalDataIndexService` và `ProjectDbService` giảm xuống gần 0ms mà không cần phụ thuộc vào thư viện ngoài.

### 3. Anti-Thrashing Guard in Runtime Self-Healing
- Cài đặt giới hạn cứng `MAX_MUTATIONS_PER_CASE = 3` trong `OptimizedSelfHealingExecutor`.
- Khi một test case bị thay đổi bộ chọn quá 3 lần mà không đạt trạng thái xanh ổn định, hệ thống tự động ngắt việc ghi đè mã nguồn và ghi nhận nhật ký pháp y (`ANTI_THRASHING_HALT`), bảo vệ mã kiểm thử khỏi hiện tượng trôi dạt logic hoặc hỏng tệp khi gặp UI giao diện dao động liên tục.

### 4. Cryptographic Key Derivation Security Hardening
- Loại bỏ hoàn toàn việc fallback về khóa tĩnh cố định `STATIC_SALT` trong `ApiKeyCipher` khi không đọc được card mạng vật lý.
- Thay vào đó, hệ thống tự sinh và lưu trữ một tệp khóa ngẫu nhiên 256-bit an toàn (`.device_entropy`) trong thư mục người dùng với quyền chỉ đọc/ghi riêng (`OWNER_READ | OWNER_WRITE`), duy trì tiêu chuẩn mã hóa AES-256-GCM trong mọi môi trường sandbox hoặc container.

### 5. High-Speed Live DOM Scanning via Reusable Headless Browser
- Trước đây mỗi lần quét URL để nạp ngữ cảnh vào AI Prompt, hệ thống khởi động và đóng một tiến trình Chromium mới (mất 2-4 giây độ trễ).
- Chuyển sang mô hình Singleton Headless Browser được quản lý vòng đời bởi `ResourceReleaseService`: Trình duyệt chỉ khởi động một lần, mỗi lần quét chỉ tạo một `BrowserContext` mới và giải phóng sau khi trích xuất DOM (<50ms độ trễ), giúp Trợ lý AI phản hồi nhanh gấp nhiều lần.

### 6. MTime Cache-Aware Workspace Synchronization
- `LocalDataIndexService.syncAllFromDisk()` tải trước bản đồ `(id, updated_at)` từ cơ sở dữ liệu và so sánh với `Files.getLastModifiedTime(path)`.
- Các tệp `group.json`, `suite.json`, `*.java` và `summary.json` không bị thay đổi sẽ được bỏ qua hoàn toàn, giúp thời gian khởi động ứng dụng đạt tốc độ tức thì bất kể kích thước workspace lên tới hàng nghìn test case.

## 17. 3-Pillar Enterprise Testing Synthesis: Midscene + Skyvern + Testsigma + Automa

### 1. Pillar 1: Multi-Locator Fallback Cascade & Action-Delta Healing (Testsigma + Skyvern)
- **MultiLocatorCascade (Testsigma)**:
  - Thay vì phụ thuộc vào một bộ chọn đơn lẻ dễ gãy hoặc phải lập tức gọi đến mô hình AI VLM tốn kém, mỗi phần tử được quản lý bằng một danh sách chiến lược đa tầng ưu tiên (`MultiLocatorProfile`): Primary (`data-testid`, `#id`) → Fallback CSS → Fallback XPath → Fallback Role/Aria → Fallback Text.
  - Tầng cascade được duyệt tuần tự và kiểm tra trực tiếp trên DOM của trang (`count() == 1 && isVisible()`) với thời gian phản hồi siêu tốc (<15ms).
  - Khi một bộ chọn dự phòng khớp duy nhất, hệ thống tự động thăng cấp nó làm Primary và ghi đè bền vững xuống file kịch bản `.java` mà không cần gọi đến API AI.
- **ActionDeltaVerifier (Skyvern)**:
  - Khắc phục triệt để hiện tượng "Phantom Click" (thao tác click Playwright thành công nhưng không có bất kỳ phản hồi giao diện nào do bị lớp phủ che khuất, nút bị disable logic hoặc chưa gắn sự kiện listener).
  - So sánh snapshot trạng thái trước và sau thao tác (`urlChanged`, `domMutated`, `interactiveCount`). Nếu không ghi nhận biến động trạng thái nào, hệ thống tự động kích hoạt các chiến lược tự phục hồi: cuộn phần tử vào trọng tâm (`scrollIntoViewIfNeeded`), phát sự kiện click trực tiếp (`dispatchEvent('click')`) hoặc thử phím Enter.

### 2. Pillar 2: Interactive Visualizer & Semantic AI Assertions (Midscene.js)
- **AI Semantic Assertions (`aiAssert` / `AI_SEMANTIC_ASSERT`)**:
  - Bổ sung loại kiểm tra xác thực ngữ nghĩa cao cấp cho phép người kiểm thử viết các câu khẳng định tự nhiên: "Giỏ hàng hiển thị đúng 2 sản phẩm", "Trang không hiển thị bất kỳ thông báo lỗi đỏ nào".
  - Tích hợp vào `AssertCodeGenerator` và thực thi tại `StudioRuntime.assertSemantic(...)` kết hợp chụp ảnh màn hình và DOM tinh giản để đánh giá bằng prompt chuẩn Fabric/ECC.
- **Interactive Visualizer Inspector**:
  - Nâng cấp `HtmlReportService` với giao diện Visualizer Inspector tương tác mô phỏng `dump.html` của Midscene: xem ảnh chụp màn hình phóng to, kiểm tra bảng nhật ký tự chữa lành (Self-Healing timeline) và kết quả xác thực ngữ nghĩa AI ngay trên báo cáo HTML độc lập.

### 3. Pillar 3: Reusable Step Groups & Test Data Profiles (Automa + Testsigma)
- **Reusable Step Groups (Testsigma)**:
  - Tạo mô hình `StepGroup` và dịch vụ lưu trữ `StepGroupStore` cho phép định nghĩa các chuỗi hành động lặp đi lặp lại (đăng nhập hệ thống, chấp thuận cookie, tạo đơn hàng) dưới dạng nhóm bước tái sử dụng.
  - Hỗ trợ cú pháp nhúng macro `// @include("group-id")` trong kịch bản test để tự động mở rộng các bước tại thời điểm chạy kịch bản trong `TestRunnerService`.
- **TestDataProfile & Variable Pipeline (Automa)**:
  - Cung cấp khả năng tham số hóa dữ liệu kiểm thử độc lập khỏi logic kịch bản thông qua `TestDataProfile` và `VariableResolver`.
  - Hỗ trợ nội suy biểu thức `{{variable_name}}` và `${variable_name}` linh hoạt cho cả URL, bước kiểm thử, tham số nhập liệu và khẳng định kiểm thử (Assertions), hỗ trợ kết nối với biến môi trường hệ thống.

## 18. Intelligent Test Case Priority Classification & Targeted Execution (P0-P3)

### 1. Risk-Based Priority Matrix (ISTQB & ECC Standard)
- Chuẩn hóa thang đo 4 cấp độ:
  - `P0 (Critical / Smoke)`: Luồng sống còn, blocker (Đăng nhập, Thanh toán, Bảo mật, Đặt hàng cốt lõi). Thất bại sẽ chặn đứng bản build/release ngay lập tức.
  - `P1 (High / Core Flow)`: Luồng nghiệp vụ cốt lõi, xác thực dữ liệu và giao dịch chính.
  - `P2 (Medium / Regression)`: Luồng phụ, tìm kiếm, phân trang, lọc dữ liệu, xử lý lỗi ngoại lệ.
  - `P3 (Low / Edge Case)`: Giao diện chi tiết, định dạng văn bản, tooltip, trạng thái biên hiếm gặp.
- AI Persona `qa-planner` và `AiTestGeneratorService` tự động đánh giá ma trận rủi ro nghiệp vụ và gán nhãn P0-P3 cho từng test case khi sinh kịch bản.

### 2. Database Schema Migration & Storage Integrity
- Để đảm bảo tương thích ngược với các file database H2 đã có sẵn dữ liệu của người dùng, lệnh `ALTER TABLE test_cases ADD COLUMN IF NOT EXISTS priority VARCHAR(10) DEFAULT 'P1'` được thực thi an toàn trước khi tạo index `idx_tc_priority`.
- Bổ sung `ProjectStore.updateCasePriority` để ghi đè trường `priority` xuống `suite.json` độc lập, tránh hiện tượng stale object ghi đè mất các case mới thêm trong các luồng AI batch import.

### 3. Targeted Execution & Reporting
- `TestRunnerService.run(...)` hỗ trợ tham số lọc `targetPriorities`: Cho phép chạy riêng các case P0 cho Smoke Test khi tạo PR, hoặc P0+P1 cho Sanity Test trước khi đưa lên môi trường Staging.
- Báo cáo HTML và Excel (`HtmlReportService`, `ExcelReportService`, `TestCaseExcelExportService`) hiển thị huy hiệu màu sắc trực quan (P0 đỏ, P1 cam, P2 xanh, P3 xám) cùng các nút lọc nhanh theo độ ưu tiên.

## Bài học ngày 2026-11-08: Tối Ưu Hệ Thống Toàn Diện & Dynamic Locator Promotion

### 1. Test Data Profile Persistence & Dynamic Resolution
- Xây dựng `TestDataProfileStore` lưu trữ tập trung các bộ dữ liệu tham số hóa tại `data/test-data/<id>.json` với cơ chế ghi file nguyên tử (`writeJsonAtomic`).
- Tích hợp pipeline nội suy biến phân tầng trong `TestRunnerService`: Profile variables (`TestDataProfile`) nạp làm nền tảng, sau đó các tham số riêng của từng case (`testDataSets[0].parameters`) ghi đè lên trên.
- Cách ly an toàn biến môi trường hệ điều hành: chỉ những token có tiền tố rõ ràng `{{ENV.VAR}}` mới được phép truy xuất `System.getenv()`, loại bỏ hoàn toàn nguy cơ rò rỉ username hay path máy local vào kịch bản test và prompt AI.

### 2. Multi-Locator Promotion on Cascade Match
- Khi `MultiLocatorCascade` tìm thấy một selector fallback ăn khớp độc nhất trên DOM (`count == 1 && isVisible()`), hệ thống tự động thăng hạng selector đó thành primary thông qua `profile.promote(candidate)`.
- Ghi nhận vào `auditLogs` và đồng bộ lưu trực tiếp xuống file kịch bản `.java` cũng như các assertions/steps trong `suite.json`, giúp kịch bản tự học hỏi và bền bỉ hơn qua mỗi lần chạy.

### 3. DOM Text Signature for Action-Delta Verification
- Bổ sung `domSignature` (dựa trên hash độ dài nội dung DOM và cây con) vào `DeltaSnapshot`, giúp nhận biết ngay biến đổi nội dung trang (như text thông báo lỗi, cập nhật giỏ hàng) và triệt tiêu click lặp không mong muốn.

## 19. MarkItDown Universal Ingestion & Agency-Agents Specialist Roster

### 1. Hybrid Document-to-Markdown Architecture (Inspired by Microsoft MarkItDown)
- **Zero-Dependency Native Java Markdown Converter**:
  - Tích hợp `MarkdownConverterEngine` sử dụng Apache POI (`poi-ooxml`) và Apache PDFBox để bóc tách và chuyển đổi định dạng tài liệu bất kỳ (`.docx`, `.xlsx`, `.pptx`, `.pdf`, `.csv`, `.tsv`, `.json`, `.html`, `.xml`) thành cây cú pháp trừu tượng (AST) Markdown chuẩn.
  - Tự động sinh bảng Markdown (`| col | col |`), tiêu đề (`#`, `##`, `###`), danh sách, khối mã và phần siêu dữ liệu (YAML Frontmatter: `source_file`, `source_type`, `converted_at`, `word_count`, `estimated_tokens`, `converter_engine`).
- **MarkItDown Python/CLI Adapter & Fallback**:
  - Kiểm tra tính khả dụng của CLI `markitdown` trên môi trường máy; hỗ trợ gọi thực thi nếu có Python/MarkItDown và tự động fallback sang Native Java Engine nếu môi trường không cài đặt Python.
- **Tự động chuyển đổi & Lưu trữ vòng đời file**:
  - Khi người dùng tải lên bất kỳ tài liệu nào (specs, PRD, ma trận test Excel, slides thuyết trình, tài liệu hướng dẫn PDF), hệ thống tự động convert sang Markdown và lưu trữ tại `data/converted_docs/<sanitized_name>_<timestamp>.md`.
  - Trên giao diện `AiAssistantPageController`, mỗi tài liệu đính kèm được gắn huy hiệu tương tác `[👁️ Xem MD]` mở hộp thoại `MarkdownPreviewDialog`, cho phép người dùng xem trước AST Markdown, thống kê token và sao chép hoặc mở file `.md` trên máy tính.
  - AI Agent nhận trực tiếp ngữ cảnh Markdown đã tối ưu, giúp giảm tới 60-70% lượng token dư thừa so với dữ liệu thô, đồng thời bảo toàn nguyên vẹn cấu trúc bảng biểu và tiêu chí chấp nhận (acceptance criteria).

### 2. Agency-Agents Specialist Roster Integration (msitarzewski/agency-agents)
- Tích hợp toàn diện các Agent chuyên trách từ Agency-Agents vào `AiSkill`:
  - **`UNIVERSAL_DOCUMENT_COMPILER`** (`/doc`, `/compiler`, `/specs`): Đóng vai trò kiến trúc sư phân rã tài liệu AST. Bóc tách yêu cầu nghiệp vụ thành ma trận kiểm thử (Coverage Matrix), phân vùng tương đương, giá trị biên và các vertical slices kịch bản test.
  - **`TEST_AUTOMATION_ENGINEER`** (`/e2e`, `/playwright`): Áp dụng triết lý kỷ luật determinism:
    - **Không bao giờ dùng hard sleep**: Cấm triệt để `Thread.sleep` và `page.waitForTimeout`; tự động thay thế bằng wait conditions (`waitForLoadState`, `waitForResponse`, `waitForURL`).
    - **Chọn selector như người dùng**: Ưu tiên `getByRole`, `getByLabel`, `getByPlaceholder`, `getByText` và `StudioRuntime.healLocator` cascade; không dùng chuỗi CSS selector mong manh.
    - **Độc lập dữ liệu**: Test case tự khởi tạo và làm chủ dữ liệu của mình, không phụ thuộc vào trạng thái thừa của test khác.
  - **`ACCESSIBILITY_AUDITOR`** (`/a11y`, `/wcag`): Chuyên gia kiểm định tiêu chuẩn tiếp cận WCAG 2.1 AA & Section 508 (tỷ lệ tương phản tối thiểu 4.5:1, kích thước vùng chạm >= 44x44px, không bẫy bàn phím, nhãn ARIA).
  - **`FAILURE_RCA`** (`/rca`, `/fix`): Chẩn đoán nguyên nhân gốc rễ và triệt tiêu flaky tests dựa trên trace/log/DOM timing.
  - **`SECURITY_FUZZING`** (`/fuzz`): Ma trận vector fuzzing bảo mật (XSS, SQLi, Buffer overflow, Unicode injection).
- Hỗ trợ định tuyến thông minh theo Slash Commands và phân tích ngữ nghĩa (Semantic Intent Detection) trong chế độ Auto.

## Phase 12 Review Remediation & Autonomous Hardening Learnings

### 1. Process Lifecycle, I/O Pipe Drainage & Subprocess Deadlocks
- **Process Pipe Buffering Deadlock**:
  - Khi gọi CLI ngoại vi (như `markitdown` hoặc CLI ngoài) bằng `ProcessBuilder`, nếu tiến trình ghi nhiều log/lỗi vượt quá kích thước bộ đệm pipe của hệ điều hành (~64KB) mà phía Java không đọc luồng xuất, tiến trình OS sẽ bị nghẽn (block) chờ buffer được đọc, gây ra deadlock vĩnh viễn và timeout 30s.
  - Sử dụng `pb.redirectErrorStream(true)` kết hợp background drain thread (`transferTo(outputStream)`) giải quyết triệt để vấn đề đầy buffer pipe.
- **Zombie Process Prevention**:
  - Khi `process.waitFor(timeout, unit)` trả về `false`, tiến trình vẫn tiếp tục chạy ngầm tiêu hao CPU và RAM. Bắt buộc phải gọi `p.destroyForcibly()` cả trong nhánh timeout lẫn khối `finally` nếu tiến trình còn sống (`p.isAlive()`).
- **Lazy Environment Probe Caching**:
  - Tránh gọi `ProcessBuilder("cli", "--version")` lặp lại trên mỗi thao tác nghiệp vụ; lưu kết quả thăm dò vào biến `volatile Boolean` để tránh lãng phí 30-50ms CPU không cần thiết.

### 2. Playwright Java Thread-Affinity Discipline
- **Thread-Affinity Constraint**:
  - Trong Microsoft Playwright for Java, mọi đối tượng (`Playwright`, `Browser`, `BrowserContext`, `Page`) đều có cơ chế kiểm tra thread affinity (`Thread.currentThread() != thread`). Việc lưu giữ `static Browser` dùng chung giữa các worker threads hoặc giữa UI thread và background thread dẫn đến lỗi `PlaywrightException: Playwright object can only be used from the thread where it was created`.
  - Thay vì dùng static browser chia sẻ giữa các luồng, áp dụng mô hình transient browser theo ngữ cảnh gọi (`try (Playwright pw = PlaywrightUtil.create(); Browser br = pw.chromium().launch(...))`) kết hợp với LRU cache 3 phút ở tầng dữ liệu. Giải pháp này đảm bảo 100% thread safety, ngăn chặn hoàn toàn xung đột luồng và giải phóng ~200MB RAM khi nhàn rỗi.

### 3. Sequential Document AST Fidelity & Byte Order Mark (BOM)
- **POI Sequential BodyElements Traversal**:
  - Trong tài liệu Word (`.docx`), các bảng biểu đặc tả kiểm thử nằm xen kẽ giữa các đề mục và đoạn văn bản. Việc duyệt riêng `doc.getParagraphs()` rồi đến `doc.getTables()` làm đứt gãy tính liên tục của tài liệu, dồn toàn bộ bảng về cuối.
  - Sử dụng `doc.getBodyElements()` để duyệt tuần tự cả `XWPFParagraph` và `XWPFTable` theo đúng thứ tự bố cục trong file gốc, giúp AI Agent nắm bắt chính xác ngữ cảnh của từng phần.
  - Thay thế `run.getText(0)` bằng `run.text()` để không bỏ sót các đoạn text bị phân mảnh hoặc ngắt dòng.
- **UTF-8 BOM Header Sanitization**:
  - File CSV/TSV xuất từ Microsoft Excel trên Windows mặc định chứa ký tự BOM `\uFEFF` ở đầu file. Khi đọc bằng `StandardCharsets.UTF_8`, Java không tự động loại bỏ ký tự này, khiến tiêu đề cột đầu tiên bị hỏng (ví dụ `|\uFEFFID|`). Luôn chủ động cắt bỏ `\uFEFF` ở dòng đầu tiên trước khi phân tách cột.

### 4. Connection Pool Transaction Boundary Isolation
- **Connection Reuse Safeguards**:
  - Khi triển khai Connection Pooling qua Dynamic Proxy, nếu một tác vụ đặt `autoCommit = false` hoặc để dở transaction rồi đóng kết nối, việc hoàn trả nguyên trạng vào pool có thể làm ô nhiễm các thao tác kế tiếp.
  - Luôn kiểm tra và rollback các thay đổi chưa commit, đồng thời thiết lập lại `target.setAutoCommit(true)` trước khi đưa kết nối trở lại hàng đợi `pool.offer(target)`.

### 5. Automated Data Lifecycle & Storage Maintenance
- **Maintenance Scope Completeness**:
  - Mọi thư mục phát sinh dữ liệu tự động (như `data/converted_docs/`) cần được tính vào chỉ số dung lượng đĩa `calculateDiskUsage()` và nằm trong phạm vi dọn dẹp an toàn của `DataCleanupService`, ngăn ngừa phình to ổ đĩa theo thời gian sử dụng.

### 6. Clean Diagnostics & Stack Trace Elimination in Test Reporting
- **High-Signal Error Diagnostics**:
  - Khi báo cáo kết quả kiểm thử bị thất bại (`FAILED` / `ERROR`), việc hiển thị toàn bộ stack trace nội bộ của ứng dụng / test harness runner (`at com.studio.service...`, `at jdk.internal.reflect...`) gây ô nhiễm thông tin và làm giảm khả năng nhận diện lỗi của QA/Developer.
  - Loại bỏ hoàn toàn khối `📋 VẾT STACK TRACE ĐẦY ĐỦ` khỏi `buildReproductionLog` trong `TestRunnerService`.
  - Phân loại lỗi tường minh (`categorizeError`): Chuyển đổi mã ngoại lệ kỹ thuật thành tên lỗi tiếng Việt/Anh thân thiện, dễ hiểu (như `TimeoutError (Quá thời gian chờ phần tử giao diện)`, `AssertionError (Kết quả kiểm chứng không thỏa mãn)`, `StrictModeViolation (Bộ chọn trùng lặp nhiều phần tử)`, `BrowserClosed (Trình duyệt đóng đột ngột)`...).
  - Làm sạch thông điệp lỗi (`sanitizeErrorMessage`): Cắt lọc các đoạn call log Playwright quá dài và các vết Java stack trace bị đính kèm trong exception message.
  - Trích xuất thông minh phần tử gặp sự cố (`extractTargetLocator`): Tự động bắt selector/locator từ thông điệp ngoại lệ để hiển thị ngay tại phần đầu báo cáo.
  - Tái hiện chi tiết theo từng bước (`CÁCH TÁI HIỆN`): Tích hợp trực tiếp các bước NLP tuần tự từ `TestCase.getTestSteps()` kèm dữ liệu kiểm thử, giúp người dùng có thể thực hiện lại chính xác kịch bản thao tác để kiểm chứng lỗi mà không cần phải đọc mã nguồn Java.

### 7. Inactive Entity Execution Guard & Universal Entity Deletion UX
- **Rigorous Execution Status Gating in Multi-Tier Scope (`ProjectStore`)**:
  - Khi người dùng bấm chạy theo Suite (`collectSuiteCases`) hoặc theo Group / All (`enumerateSuiteCases`), việc chỉ kiểm tra trạng thái `"Draft"` dẫn đến lỗi nghiêm trọng: các test case có trạng thái `"Inactive"` ("Không hoạt động") hoặc `"INACTIVE"` bị coi là hợp lệ (`PENDING`) và bị Playwright kích hoạt chạy thực tế.
  - Tương tự, nếu TestSuite có trạng thái `"INACTIVE"` nhưng được gọi trực tiếp qua phạm vi `SUITE`, việc thiếu kiểm tra `suiteActive` khiến toàn bộ case trong suite không hoạt động vẫn bị chạy.
  - Giải pháp kiến trúc: Chuẩn hóa logic kiểm tra qua `applySkipEvaluation(suite, tc, cr)` dùng chung cho cả 3 phạm vi: `SUITE`, `GROUP`/`ALL`, và `CASE`.
    - Suite không hoạt động (`!ACTIVE`): Đánh dấu ngay lập tức toàn bộ case là `SKIPPED` với lý do `Suite status is INACTIVE`.
    - Case không hoạt động (`INACTIVE`/`Inactive`): Đánh dấu ngay `SKIPPED` với lý do `Case is inactive`.
    - Case bản nháp (`Draft`): Đánh dấu `SKIPPED` với lý do `No script (case Draft)`.
    - Case chưa có assertion hợp lệ: Đánh dấu `SKIPPED`.
- **Proactive UI Pre-Check & Action Button State Integrity**:
  - Tại `ExplorerPageController`:
    - Phương thức `countRunnableCasesUnderItem(TreeItem)` đệ quy kiểm tra số lượng case thực sự có thể chạy. Nếu suite con không hoạt động, các case bên dưới không được tính.
    - Cả nút "Chạy" trên Preview, nút inline `runBtn` trên từng hàng TreeCell, menu chuột phải context menu, và lệnh `runSelected()` đều kiểm tra điều kiện này. Nếu một nhóm hoặc suite không có bất kỳ case nào đang hoạt động, hệ thống từ chối chạy ngay lập tức và đưa ra Toast thông báo rõ ràng thay vì khởi động runner rỗng.
  - Tại `SuiteWorkspacePageController`:
    - `runSuite()` kiểm tra `runnableCount == 0` trước khi khởi tạo tiến trình chạy ngầm.
    - `runSuiteBtn` được vô hiệu hóa (`disabled`) khi suite không hoạt động hoặc không có case nào runnable.
- **Universal Deletion Ergonomics Across Workspaces**:
  - Bổ sung nút xóa toàn diện cho cả 3 cấp độ (Nhóm / Suite / Case):
    - **Explorer Toolbar**: Nút `deleteBtn` ("Xóa") màu đỏ nguy hiểm (`.toolbar-btn-danger`), biểu tượng thùng rác `UiIcons.TRASH`, tự động kích hoạt khi chọn Nhóm (khác root), Suite hoặc Case.
    - **Explorer Tree Rows**: Nút `deleteBtn` inline (`.tree-action-delete`) xuất hiện khi di chuột hoặc chọn hàng, cho phép xóa nhanh trực tiếp từ cây dự án.
    - **Explorer Preview Panel**: Nút `previewDeleteBtn` ngay cạnh nút "Sửa" trong form thông tin.
    - **Suite Workspace**: Nút `deleteBtn` (`.case-row-delete-btn`) trên từng hàng của danh sách case `caseListView`, tùy chọn "Xóa Case" trong menu ngữ cảnh chuột phải, nút "Xóa case" trên thanh công cụ Workspace, và nút "Xóa Suite" (`deleteSuiteBtn`) trong bảng thông tin Suite.
  - Tất cả các nút xóa đều tích hợp hộp thoại xác nhận chi tiết số lượng mục con bị ảnh hưởng trước khi thực thi xóa an toàn qua `projectStore.delete*` và dọn sạch file script `.java` liên quan.

### 8. Interactive Clarification Card & Deep Test Generation Standards (Agency-Agents & Test Collaboration)
- **Anti-Superficial Generation Standard**:
  - Dựa trên tri thức kiểm thử thực chiến từ `agency-agents-main/testing/testing-test-automation-engineer.md` và `Fabric-main/data/patterns`, các kịch bản kiểm thử sơ sài (1-2 click đơn giản, 1 assertion cơ bản) được loại bỏ triệt để.
  - Chuẩn tạo mới quy định tối thiểu 2-3 kịch bản toàn diện cho một tính năng:
    1. *Happy Path*: Luồng chuẩn với đầy đủ dữ liệu hợp lệ.
    2. *Negative Validation*: Luồng ngoại lệ với thông báo lỗi rõ ràng.
    3. *Boundary & Edge Case*: Giá trị biên, ký tự đặc biệt hoặc dữ liệu cực đại.
    - Mỗi kịch bản phải có 4-8 bước thao tác rõ ràng (`TestStep`) và 2-4 `Assertion` kiểm chứng web-first (Zero hard sleep, strict-mode disambiguation, resilient role selectors).
- **Interactive In-Chat Clarification Flow**:
  - `AiAmbiguityAnalyzer` phân tích câu lệnh: nếu yêu cầu mơ hồ, quá ngắn (< 140 ký tự) và chưa có tệp đính kèm hoặc Live DOM, AI không đoán mò mà chủ động đưa ra thẻ câu hỏi tương tác (`AiClarificationQuestion`).
  - Thẻ làm rõ (`clarification-card`) được hiển thị trực tiếp trong bong bóng chat của trợ lý AI với các chip tùy chọn bo tròn và ô nhập tự do.
  - Khi người dùng bấm "⚡ Xác nhận & Sinh Test Cases chi tiết", câu lệnh được tăng cường (`buildAugmentedPrompt`) và chuyển thẳng vào engine sinh kịch bản chi tiết, bảo toàn lịch sử và trạng thái đã làm rõ.
- **Conversational Chatbot Intelligence**:
  - Hỗ trợ trò chuyện và điều hướng theo 4 nhóm tác vụ qua `AiProjectAdvisorService`:
    1. *Tìm kiếm test case*: Tìm theo tên, suite, nhóm, từ khóa, trạng thái.
    2. *Đánh giá chất lượng test case*: Chấm điểm theo checklist Agency-Agents (0 hard sleep, web-first assertion, selector resilience) và xếp hạng A/B/C/D kèm mã sửa mẫu.
    3. *Phân tích khoảng trống kiểm thử (Gap Analysis)*: Phát hiện các luồng thiếu (Happy path, Negative, Boundary) trong một Suite hiện có.
    4. *Tư vấn kỹ thuật Playwright*: Giải thích best practices và cách xử lý timeout/flakiness.

### 9. Preview Pane Direct Target Resolution Fix
- **Nguyên nhân lỗi "Không đọc được mục đã chọn" và "Chỉ có thể mở suite / case trong workspace"**:
  - Trong `ExplorerPageController`, các nút trên bảng Preview ("Sửa" `editFromPreview`, "Mở Workspace" `openWorkspace`, "Chạy Case" `runNode`) trước đây phụ thuộc vào `resolveTarget()`.
  - Nếu người dùng bấm vào vùng Preview ngoài cây dự án hoặc cây dự án có hàng root/group đang được chọn, `resolveTarget()` trả về node được chọn trên cây hoặc `null`, khiến thao tác trên bảng Preview bị từ chối hoặc mở sai nhóm (ví dụ: dùng tên suite làm tên nhóm).
- **Khắc phục**:
  - `editFromPreview()` và `openWorkspace()` ưu tiên trích xuất trực tiếp `currentPreviewRef` (thực thể đang hiển thị ngay trên bảng Preview).
  - Chuẩn hóa đường dẫn nhóm trong `openWorkspaceForRef` bằng `suiteDir.getParent().getFileName().toString()`, đảm bảo luôn mở đúng Workspace ngay cả khi đối tượng được chọn từ Case cấp con.

### 10. Review Fixes & Architectural Optimizations (Phase 13)
- **Clarification Question Defaulting**:
  - `AiClarificationQuestion.getDefaultOption()` cung cấp giá trị mặc định ưu tiên từ `selectedAnswer` (nếu đã có), hoặc phần tử đầu tiên trong danh sách `options`, bảo đảm thẻ làm rõ trong UI luôn có trạng thái hợp lệ ngay cả khi dữ liệu từ máy chủ chưa trả câu trả lời trước đó.
- **Root Group Directory Normalization in Project Advisor**:
  - `ProjectStore.loadTree()` tạo ra node gốc mang tên `"Project"` làm container ảo trong bộ nhớ; toàn bộ các Suite và Subgroup cấp 1 thực tế nằm trực tiếp dưới thư mục dự án (`projectDir`).
  - Trong các hàm đệ quy tìm kiếm (`searchInGroup`) và tra cứu toàn cục (`findCaseGlobally`), không bao giờ `currentPath.resolve("Project")` cho node gốc để tránh sinh đường dẫn ma dạng `/path/to/project/Project/suite/case.java`. Thay vào đó, đường dẫn gốc được bảo toàn và chỉ `resolve(child.getName())` khi duyệt xuống các node con thực sự.
- **Conversational Technical QA / Chat Advice Intent**:
  - Khi người dùng hỏi đáp về Playwright/QA kiến trúc (`Intent.CHAT_ADVICE`), `AiTestGeneratorService` chuyển sang system prompt chuyên dụng `CHAT_ADVICE_SYSTEM_PROMPT` và trả lời dạng Markdown đàm thoại tự nhiên, không ép cấu trúc JSON schema kịch bản test nhằm triệt tiêu hoàn toàn nguy cơ lỗi `"Lỗi khi phân tích JSON phản hồi từ AI"`.
- **Harmonized Runnable Execution Guard**:
  - Đồng bộ `ExplorerPageController.countRunnableCasesUnderItem` và các nút chạy trên cây thư mục với điều kiện `tc.hasActiveAssertions()`, bảo đảm trải nghiệm đồng nhất với Suite Workspace và `ProjectStore.applySkipEvaluation`.

### 11. Release Notes & Version Publication Standard
- **Nguyên tắc phát hành phiên bản mới (Current Version Scope)**:
  - Khi phát hành phiên bản mới hoặc cập nhật thông báo phát hành trên GitHub Release/CI, chỉ đưa nội dung thay đổi của riêng phiên bản vừa phát hành.
  - Không nhồi nhét hay lặp lại thông tin của các phiên bản cũ vào release body/thông báo phát hành, vì toàn bộ lịch sử thay đổi đã được quản lý và lưu trữ tập trung tại `docs/RELEASE_NOTES.md` và `CHANGELOG.md`.
  - Tích hợp `scripts/extract-release-notes.py` và cấu hình CI workflow `release-portable.yml` để tự động bóc tách đúng phần ghi chú của phiên bản hiện hành đưa vào GitHub Release.

### 13. Inno Setup Multi-Privilege Registry & System Binary Execution
- **HKA Registry Root for Dual Admin/User Installations**:
  - Khi Inno Setup cho phép người dùng tùy chọn chế độ cài đặt (`PrivilegesRequiredOverridesAllowed=dialog`), tuyệt đối không dùng trực tiếp `Root: HKLM` cho các khóa registry ứng dụng.
  - Sử dụng `Root: HKA` (Inno Setup 6+): tự động trỏ vào `HKLM` nếu người dùng cài đặt ở quyền Administrator, hoặc trỏ vào `HKCU` nếu người dùng cài đặt ở quyền thường (Non-Admin / Current User), loại bỏ hoàn toàn lỗi Access Denied hoặc đòi quyền nâng cấp không cần thiết.
- **Safe System Binary Resolution**:
  - Trong Pascal Script `[Code]`, tránh gọi `Exec('cmd.exe', ...)` hoặc `Exec('taskkill.exe', ...)`.
  - Luôn sử dụng `ExpandConstant('{cmd}')` và `ExpandConstant('{sys}\taskkill.exe')` để đảm bảo lệnh thực thi luôn tìm đúng file binary hệ thống Windows chính thức, ngăn chặn rủi ro PATH hijacking hoặc biến môi trường bị thiếu sót.

### 14. Linux Debian Packaging Offline Dependency Verification
- **App-Image Directory Precedence**:
  - Khi đóng gói file `.deb` bằng `jpackage --type deb --app-image <DIR>`, `jpackage` sẽ sao chép toàn bộ cây thư mục bên trong `<DIR>` vào `/opt/<package_name>/`.
  - Cần bảo đảm `<DIR>` là thư mục đã tích hợp đầy đủ các phụ thuộc ngoại vi (`browsers/`, `tools/node/`). Script đóng gói Linux cần ưu tiên kiểm tra `dist/${APP_NAME}/${APP_NAME}/browsers` thay vì `target/jpackage-out/` vốn chỉ chứa app-image sơ khai chưa có Playwright Chromium.

### 15. AI Assistant Multi-Session Architecture & Context Optimization
- **Multi-Session Storage & Migration Isolation**:
  - Lưu trữ đa phiên theo Suite/Workspace trong thư mục `.ai_chats/<sessionId>.json` giúp các phiên độc lập, dễ dàng sao lưu, di chuyển và xóa bỏ atomic mà không gặp nguy cơ file corruption.
  - Tự động di chuyển (auto-migration) mượt mà từ file `.ai_chat.json` cũ sang session đầu tiên, bảo đảm 100% không mất dữ liệu của người dùng khi nâng cấp.
  - Phản ánh tức thời dữ liệu phiên vào bảng H2 Database `ai_chat_sessions` và cột `session_id` của `ai_chat_messages` để tính toán tổng số token và truy vấn nhanh.
- **Large Context Optimization (Hybrid Rolling Summary + Code/DOM Truncation)**:
  - Khi lịch sử cuộc trò chuyện kéo dài qua hàng chục lượt trao đổi, việc gửi toàn bộ code Playwright sinh ra ở các lượt trước làm bùng nổ token context (dẫn đến vượt quá max_tokens hoặc phản hồi chậm).
  - Áp dụng kỹ thuật kép:
    1. **Historical Bulky Code & DOM Truncation**: Đối với các lượt trao đổi cũ nằm ngoài lượt kề cận (turns older than the immediate last turn), tự động rút gọn thân code Playwright/JSON DOM dài thành chữ ký phương thức ngắn gọn kèm chú thích `// ... [Đã thu gọn mã nguồn cũ để tối ưu context] ...`, giúp tiết kiệm đến 75% lượng token tiêu thụ.
    2. **Structured Progressive Rolling Summary**: Các lượt bị cắt khỏi sliding window được tổng hợp lũy tiến thành một bản tóm tắt cấu trúc (`• Yêu cầu & Mục tiêu`, `• Ca kiểm thử / Kết quả đã sinh`), giữ lại toàn bộ ngữ cảnh quan trọng mà không lãng phí token.
    3. **Token Budget Hard Enforcement**: Kiểm soát ngân sách token nghiêm ngặt, tự động điều chỉnh sliding window thu nhỏ linh hoạt nếu ngữ cảnh quá dài.
### 16. Atomic Multi-Session Storage & Batch JDBC Indexing
- **Atomic File Serialization (`writeJsonAtomic`)**:
  - Ghi file JSON trực tiếp qua `mapper.writeValue(file)` tiềm ẩn nguy cơ làm file bị hỏng hoặc mất dữ liệu khi mất điện hoặc tiến trình bị tắt đột ngột.
  - Sử dụng cơ chế ghi ra file tạm `.tmp` trong cùng thư mục cha và hoán đổi nguyên tử (`Files.move` với `StandardCopyOption.ATOMIC_MOVE` và `REPLACE_EXISTING`, kèm fallback `AtomicMoveNotSupportedException`) bảo đảm file session luôn toàn vẹn 100%.
- **Batch JDBC Execution for UI Thread Responsiveness**:
  - Khi lưu trữ phiên hội thoại dài có hàng chục tin nhắn, việc mở/đóng kết nối tuần tự cho từng message gây ra hiện tượng N+1 JDBC transaction trên UI thread.
  - Sử dụng phương thức batch `indexChatMessages(List<AiChatMessage>, String suiteKey)` với 1 `Connection` và `PreparedStatement.addBatch() / executeBatch()` giúp giảm số lần I/O, thực hiện lưu trữ chỉ trong vài mili-giây và triệt tiêu hoàn toàn hiện tượng micro-stutter trên JavaFX.
- **Race Condition Prevention during Session Deletion**:
  - Thêm chốt chặn bảo vệ không cho phép xóa phiên chat đang trong quá trình sinh kịch bản ngầm (`generationProgress.isVisible()`), ngăn chặn việc luồng nền vô tình hồi sinh session đã bị xóa hoặc ném ngoại lệ `NullPointerException`.

### 17. Knowledge-Driven Test Case Generation & Multi-Format Export Architecture
- **Knowledge-Driven QA Standards (Inspiration from `docs/knowledge`)**:
  - Kế thừa kiến trúc từ **Testsigma** (`testsigma-dev`), **Agency-Agents** (`agency-agents-main/testing`), **Fabric** (`Fabric-main`), và **ECC** (`ECC-main`):
    1. **Dual Representation & Anti-Superficial Generation**: Bộ test case sinh ra phải có cấu trúc phân rã hoàn chỉnh: mã ID, độ ưu tiên (P0-P3), điều kiện tiên quyết (preconditions), danh sách các bước thao tác (Step Number, Action, Target/Field theo tài liệu, Test Data, Description, Expected Result), bộ tham số kiểm thử biên & phân vùng tương đương (Equivalence Partitioning & Boundary Value Analysis), và tiêu chí nghiệm thu (Assertions).
    2. **Strict Rule on Test Script Omission without URL**: Khi người dùng chỉ cung cấp đặc tả/tài liệu yêu cầu mà không có link trang web (hoặc tắt tùy chọn tạo script):
       - AI không được sinh mã Playwright Java giả lập hay bịa đặt URL (`scriptCode = ""`, `startUrl = ""`).
       - Toàn bộ năng lực LLM được dành cho việc mở rộng độ bao phủ kiểm thử (Happy Path, Negative Validation, Boundary/Edge Cases).
       - Trình soạn thảo script hiển thị thông báo hướng dẫn trực quan và tự động chọn tab "Các bước" để QA xem chi tiết các bước kiểm thử.
- **Direct Multi-Format Test Suite Export (`TestCaseExcelExportService`)**:
  - Cho phép người dùng tải về bộ Test Case ngay tại màn hình AI Assistant mà không bắt buộc phải nhập vào Suite trước:
    1. **Excel (.xlsx) chuẩn QA/ISTQB / IEEE 829**: Tạo workbook 3 sheet hoàn chỉnh gồm:
       - *Tổng quan (Overview)*: Tên dự án, phạm vi, ngày xuất, số lượng case, phân loại độ ưu tiên, tỉ lệ hoàn thiện.
       - *Danh sách Test Cases (Cases)*: STT, Mã Case, Tên Case, Nhóm, Độ ưu tiên (P0-P3 có badge màu), Điều kiện tiên quyết, Tóm tắt bước, Dữ liệu kiểm thử, Kết quả mong đợi, Ghi chú kịch bản.
       - *Chi tiết các bước (Steps)*: Bảng ma trận chi tiết từng bước thao tác tuần tự với Action, Target, Dữ liệu nhập, Mô tả và Kết quả kỳ vọng của từng bước.
    2. **Markdown (.md)**: Đặc tả tài liệu kỹ thuật có bảng Markdown, danh sách điều kiện và kịch bản để lưu trữ vào kho mã nguồn Git hoặc chia sẻ tài liệu dự án.
    3. **JSON (.json)**: Định dạng dữ liệu có cấu trúc phục vụ việc tích hợp vào các hệ thống CI/CD hoặc công cụ quản lý test khác.
- **Smart URL Detection & User Control UX**:
  - Tích hợp checkbox "Tạo Test Script" ngay tại thanh điều khiển prompt.
  - Tự động nhận diện URL (`http://`, `https://`) từ prompt, tài liệu đính kèm hoặc Live DOM để bật/tắt checkbox thông minh, đồng thời trao toàn quyền cho người dùng chủ động điều khiển theo nhu cầu kiểm thử.

### 18. JavaFX ComboBox Listener Re-entrancy & AI Chat History Tab-Switch Preservation Architecture
- **Root Cause of Chat History Loss on Tab Switch**:
  - Khi người dùng chuyển tab (giữa Trợ lý AI và Khám phá/Báo cáo/Suite Workspace) và quay lại màn hình AI, cơ chế điều hướng `ViewNavigator.onPageShown(Page.AI_ASSISTANT)` gọi `ai.reloadTargets()` để làm mới danh sách nhóm và suite.
  - Hàm `reloadTargets()` gọi trực tiếp `targetGroupCombo.getItems().clear()` và `targetSuiteCombo.getItems().clear()`.
  - Trong JavaFX, việc gọi `.clear()` trên `ComboBox` làm giá trị `.getValue()` chuyển thành `null` và kích hoạt ngay lập tức các listener `onAction`:
    - `targetGroupCombo` kích hoạt `onGroupSelected()`.
    - `targetSuiteCombo` kích hoạt `loadChatHistoryForSuite()`.
  - Tại thời điểm này, `targetGroupCombo` và `targetSuiteCombo` đều tạm thời là `null`, dẫn đến `getSelectedSuiteDir()` trả về `null` (workspace toàn cục).
  - Kết quả là `currentSession` đang chứa lịch sử trò chuyện của người dùng bị ghi đè bởi một session toàn cục trống, và `chatMessagesBox.getChildren().clear()` xóa sạch bong bóng chat đang hiển thị.
- **Robust Fix & Listener Re-entrancy Guard**:
  1. **Re-entrancy Guard Flag (`isReloadingTargets`)**: Đặt cờ bảo vệ `isReloadingTargets = true` trong suốt quá trình `reloadTargets()`. Bất kỳ sự kiện `onAction` nào từ ComboBox phát sinh trong lúc cập nhật danh sách sẽ lập tức `return` mà không kích hoạt chuỗi sự kiện phá hủy dữ liệu.
  2. **Non-Destructive Target Sync (`setAll` thay vì `clear`)**: Đọc danh sách nhóm và suite từ đĩa vào danh sách trung gian (`List<String>`) và chỉ cập nhật ComboBox thông qua `.getItems().setAll(items)` khi có thay đổi thực tế trên đĩa.
  3. **Preserve Live In-Memory Session**: Nếu suite đích không thay đổi (`suiteSame == true`) và `currentSession` hiện tại đã có tin nhắn, không nạp lại hoặc ghi đè session từ đĩa mà giữ nguyên session đang hoạt động trong bộ nhớ, chỉ làm mới danh sách phiên (`refreshSessionsList(activeSessionId)`) để giữ con trỏ chọn phiên nhất quán.
  4. **Active Session ID Preservation in `refreshSessionsList`**: Cho phép `refreshSessionsList` ghi nhớ và tái chọn đúng `currentSession.getId()` thay vì mặc định rơi về index 0.
  5. **Bidirectional Tab Switch Synchronization**: Đồng bộ `ViewNavigator.onPageShown()` khi người dùng chuyển tab thông qua cả thanh menu bên (Sidebar) lẫn thanh tab tài liệu (Multi-Tab Document Bar `TabManager`), đảm bảo trải nghiệm nhất quán 100%.
