# Tài Liệu Đặc Tả Thiết Kế UI/UX: AI QA Test Studio (Airy Edition v4.1)

> **Trạng thái triển khai (2026-11-14):** Đã ship trên runtime Python — xem ADR [`docs/adr/0003-airy-ui-shell.md`](adr/0003-airy-ui-shell.md), `SPEC.md`, `ui/main_window.py`, `ui/widgets/right_drawer.py`, `ui/widgets/chat_pane.py`. Tài liệu này vẫn là **source of truth thiết kế**; mọi lệch so với code cần ADR/SPEC cập nhật.

Tài liệu này quy định chuẩn mực thiết kế giao diện người dùng (UI) và trải nghiệm tương tác (UX) cho **AI QA Test Studio**, sử dụng thư viện **`PySide6-Fluent-Widgets`** trên kiến trúc bất đồng bộ **`qasync`**. Bản thiết kế khắc phục triệt để tình trạng mật độ thông tin quá dày đặc, lặp nút điều khiển và chồng chéo khung viền của các phiên bản thử nghiệm trước.

---

## 1. Triết Lý Thiết Kế & Nguyên Tắc Thị Giác

Thay vì hiển thị đồng thời mọi công cụ trên một màn hình tĩnh, giao diện v4.1 áp dụng **5 nguyên tắc cốt lõi** lấy cảm hứng từ *Linear*, *Arc Browser* và *Playwright UI*:

1. **Mặc định 2 cột thoáng (2-Pane Default + On-Demand Drawer):** Khi mở ứng dụng để đọc hoặc chỉnh sửa kịch bản, màn hình chỉ hiển thị **Sidebar trái (`260px`)** và **Vùng làm việc chính (`~80%` chiều rộng)**. Khung **AI Copilot** và **Trình duyệt trực tiếp (CDP)** chỉ trượt ra từ cạnh phải khi người dùng chủ động gọi hoặc khi bấm chạy kiểm thử.
2. **Tiết lộ lũy tiến (Progressive Disclosure):** Các trường thông tin hành chính ít sửa (Điều kiện tiên quyết, Kết quả mong đợi cuối cùng) được thu gọn trong một ngăn mở rộng 1-click, nhường diện tích trung tâm cho danh sách các bước kiểm thử và dữ liệu test.
3. **Phân tầng bằng sắc độ nền thay vì khung viền (Surface Elevation over Borders):** Loại bỏ các đường viền bao quanh mọi ô nhập liệu. Phân tách các vùng chức năng bằng 3 cấp độ màu nền tối dịu mắt kết hợp bo góc mềm mại (`8px`).
4. **Chữ tiêu đề thay cho ô nhập liệu bị đóng khung:** Tên Test Case hiển thị bằng chữ tiêu đề lớn (`SubtitleLabel`) dễ đọc, chỉ chuyển sang chế độ sửa khi click đúp hoặc bấm nút chỉnh sửa, loại bỏ hoàn toàn hiện tượng cắt cụt chữ (`...`).
5. **Hệ lưới khoảng thở `8pt` (Generous Breathing Room):** Vùng làm việc chính duy trì lề xung quanh `24px - 32px`; các danh sách cây thư mục và bảng dữ liệu khóa chiều cao dòng tối thiểu `34px - 38px` để mắt quét nhanh không bị mỏi.

---

## 2. Hệ Thống Design Tokens & Bảng Màu (Fluent Dark Theme)

### 2.1. Bảng màu phân tầng (Color System)

| Token / Vai trò | Mã màu Hex | Ứng dụng cụ thể trên giao diện |
| :--- | :--- | :--- |
| `bg.canvas` | `#141518` | Nền chính của `QMainWindow`, Top Bar và Vùng làm việc trung tâm |
| `bg.surface` | `#191B20` | Nền Sidebar trái, Ngăn trượt phải (Drawer) và các thẻ bước kiểm thử (`Step Card`) |
| `bg.elevated` | `#20232A` | Trạng thái Hover của thẻ bước test, dòng cây thư mục và menu thả xuống |
| `border.subtle` | `#24262D` | Đường phân cách `1px` giữa các vùng lớn (`QSplitter::handle`) |
| `accent.primary` | `#6366F1` | Màu chủ đạo Indigo (nút `Chạy Test`, thanh gạch chân `Pivot` đang chọn) |
| `text.primary` | `#F3F4F6` | Tiêu đề Test Case, tên hành động chính của từng bước |
| `text.secondary` | `#9CA3AF` | Mô tả locator, thông số kỹ thuật phụ |
| `text.muted` | `#71717A` | Số thứ tự bước (`01`, `02`), thông tin trên Status Bar |
| `status.bound` | `#10B981` | Badge xanh lá: Bước đã gắn selector thật (`BOUND`) hoặc `PASS` |
| `status.unbound` | `#F59E0B` | Badge hổ phách: Bước nháp Shift-Left chưa gắn URL (`UNBOUND`) |
| `status.healed` | `#818CF8` | Badge tím nhạt: Bước vừa được AI tự phục hồi (`HEALED`) |
| `status.error` | `#EF4444` | Badge đỏ: Bước thực thi thất bại (`FAIL`) hoặc mất kết nối MCP |

### 2.2. Thang kiểu chữ (Typography Scale)

* **Font giao diện chính:** `Inter`, `SF Pro Display` (macOS), `Segoe UI` (Windows) — cỡ chữ chuẩn `13px`.
* **Font mã nguồn & Locator:** `JetBrains Mono`, `Menlo`, `Consolas` — cỡ chữ `12.5px` cho chuỗi locator (`get_by_role(...)`), YAML và Python code.
* **Phân cấp kích thước:**
  * Tiêu đề Test Case (`SubtitleLabel`): `18px`, Semi-Bold (`600`).
  * Tiêu đề thẻ bước test (`StrongBodyLabel`): `13.5px`, Medium (`500`).
  * Nhãn phụ & Breadcrumb (`CaptionLabel`): `12px`, Regular (`400`).

---

## 3. Kiến Trúc Bố Cục Không Gian (Spatial Layout)

### 3.1. Trạng thái mặc định: Chế độ Tập trung Soạn thảo (2-Pane Airy Mode)

```text
+---------------------------------------------------------------------------------------------------+
| [☰]  [🤖 Tạo bằng AI]                 [ https://example.com      ] [🔄 Sync (12)]   [▶ Chạy] [✨] [⋯] | ◀ Top Bar (52px)
+-----------------------+---------------------------------------------------------------------------+
| [Kịch bản] [Elements] |   TC01_ChucNangTaoHoSo_HappyPath   [P0 · Critical]  [12 UNBOUND]   [▾]    |
| [🔍 Lọc nhanh...]     |                                                                           |
|                       |   Kiểm thử chức năng tạo mới hồ sơ người yếu thế — Luồng chuẩn            |
| ▼ 📁 TS_TaoHoSo...    |                                                                           |
|   📄 TC01 · Luồng...  |   Các bước (12)    Dữ liệu Test (3)    YAML    Pytest Code                | ◀ Pivot gạch chân
|   📄 TC02 · Thiếu...  |   ─────────────────────────────────────────────────────────────────────   |
|   📄 TC03 · Phân...   |                                                                           |
|   📄 TC04 · Trùng...  |   ┌───────────────────────────────────────────────────────────────────┐   |
|                       |   │ 01  Mở trang Quản lý danh sách người yếu thế             [BOUND]  │   |
|                       |   │     GOTO  /nguoi-yeu-the                                            │   |
|                       |   └───────────────────────────────────────────────────────────────────┘   |
|                       |   ┌───────────────────────────────────────────────────────────────────┐   |
|                       |   │ 02  Nhấn nút 'Thêm người yếu thế'                      [UNBOUND]  │   |
|                       |   │     CLICK  button: Thêm người yếu thế                               │   |
|                       |   └───────────────────────────────────────────────────────────────────┘   |
+-----------------------+---------------------------------------------------------------------------+
|  QuanLyNguoiYeuThe · MCP Playwright sẵn sàng                            7 Skills · 0/8,000 tokens | ◀ Status Bar (28px)
+---------------------------------------------------------------------------------------------------+
```

### 3.2. Trạng thái mở rộng: Chế độ AI Copilot & Kiểm thử trực tiếp (Slide-Over Drawer)

Khi người dùng nhấn nút **`✨ AI Copilot`** hoặc nhấn **`▶ Chạy Test`**, ngăn trượt bên phải (`380px - 420px`) mở ra mà không làm biến dạng cấu trúc vùng giữa:

```text
+-----------------------+-----------------------------------------------+---------------------------+
| Sidebar Trái (250px)  | Vùng Làm Việc Trung Tâm (Tự động co giãn)     | Ngăn Trượt Phải (400px)   |
|                       |                                               | [AI Copilot] [Trình duyệt]|
|                       |                                               | ───────────────────────── |
|                       |                                               | Model: [Gemini 2.5 Flash] |
|                       |                                               | [@Case] [@DOM] [@Error]   |
|                       |                                               |                           |
|                       |                                               | Khung Chat Streaming /    |
|                       |                                               | Hoặc Live CDP Screencast  |
|                       |                                               |                           |
|                       |                                               | [Nhập yêu cầu cho AI...]  |
+-----------------------+-----------------------------------------------+---------------------------+
```

---

## 4. Đặc Tả Chi Tiết Từng Phân Vùng & Component

### 4.1. Minimalist Top Action Bar (`52px`)
Sử dụng `QHBoxLayout` với các nút không viền (`TransparentToolButton`) cho thao tác phụ, giữ tối đa **1 nút Primary** trên toàn thanh:

* **Cụm Trái:**
  * Nút `☰` (`TransparentToolButton`, `34x34px`): Ẩn/hiện Sidebar trái (`Ctrl+\` / `Cmd+\`).
  * Nút `Tạo bằng AI` (`PushButton` kèm icon `FIF.ROBOT`, `min-width: 115px`): Mở hộp thoại **AI Test Wizard** 3 bước.
* **Cụm Giữa (Căn giữa với `addStretch(1)` hai bên):**
  * Ô nhập `Target URL` (`LineEdit`, cố định chiều rộng `320px - 380px` để không ép các nút khác bị cắt chữ).
  * Nút `Sync (N)` (`PushButton` kèm icon `FIF.SYNC`): Hiển thị trực tiếp số bước `UNBOUND` cần đồng bộ; tự động ẩn số đếm khi toàn bộ bước đã chuyển sang `BOUND`.
* **Cụm Phải:**
  * Nút `Chạy Test` (`PrimaryPushButton` kèm icon `FIF.PLAY`, `min-width: 105px`): Khởi chạy kịch bản và tự động mở tab `Trình duyệt` ở ngăn trượt phải. Khi đang chạy, đổi nhãn thành `Dừng` màu đỏ.
  * Nút `AI Copilot` (`TogglePushButton` kèm icon `FIF.CHAT`, `min-width: 105px`): Bật/tắt ngăn trượt AI Copilot.
  * Nút `⋯` (`TransparentToolButton`, `34x34px`): Mở `RoundMenu` chứa các tác vụ xuất bản (*Xuất báo cáo HTML*, *Xuất Excel IEEE 829*, *Xuất dự án Pytest*, *Cài đặt MCP & API Key*).

### 4.2. Clean Left Sidebar (`260px`)
* **Thanh chuyển ngăn (`Pivot`):** Gồm 3 mục gọn gàng: `Kịch bản` | `Elements (67)` | `Báo cáo`.
* **Ô tìm kiếm (`SearchLineEdit`):** Lọc tức thì theo tên Test Case, mã `TC_*` hoặc tên phần tử `EL_*`.
* **Cây thư mục (`TreeWidget`):**
  * Khóa chiều cao mỗi dòng `34px`, bo góc `6px`, thụt lề `14px`.
  * Hiển thị chấm trạng thái nhỏ ở đầu dòng thay vì chuỗi ký tự dài; di chuột vào dòng hiển thị `ToolTip` chứa tên đầy đủ và đường dẫn suite.

### 4.3. Spacious Main Canvas (Vùng Soạn Thảo Trung Tâm)
Áp dụng lề trong `margins = (32, 24, 32, 20)` để tạo cảm giác như một trang tài liệu sạch sẽ:

1. **Header & Metadata gọn nhẹ:**
   * Dòng 1: Mã định danh kỹ thuật (`TC01_...`) + Badge độ ưu tiên (`P0 · Critical`) + Badge số bước chưa đồng bộ (`12 UNBOUND`) + Nút bật/tắt **`Điều kiện & Kết quả mong đợi`** ở góc phải.
   * Dòng 2: Tiêu đề nghiệp vụ lớn (`SubtitleLabel`) hiển thị đầy đủ trên nhiều dòng nếu dài.
   * Khối `Details Box` (ẩn mặc định): Khi mở ra, cho phép chỉnh sửa `Preconditions`, `Expected Final Result` (dùng làm tiêu chí đối chiếu cho Outer Verification Loop) và `Priority / Type`.
2. **Thanh chuyển chế độ `Pivot` (4 tab đồng bộ hai chiều):**
   * **Tab 1 — `Các bước (N)` (No-Code Visual Cards):**
     * Mỗi bước là một thẻ `QFrame` nền `#191B20`, bo góc `8px`, padding `12px 16px`, khoảng cách giữa các thẻ `10px`.
     * Cấu trúc mỗi thẻ gồm 4 cột: Số thứ tự (`01`) | Hành động tiếng Việt rõ nghĩa + dòng phụ monospace hiển thị locator & biến dữ liệu | Ảnh thumbnail phần tử (nếu đã bind) | Badge trạng thái (`BOUND` / `UNBOUND` / `HEALED`).
     * Nút `+ Thêm bước mới` đặt cuối danh sách; hỗ trợ kéo-thả để đổi thứ tự bước.
   * **Tab 2 — `Dữ liệu Test (M)` (Data-Driven Table):**
     * Sử dụng `TableWidget` với chiều cao dòng khóa cứng **`38px`** (`setWordWrap(False)`) để không bao giờ bị giãn dọc làm vỡ bố cục.
     * Cột `Parameters` hiển thị tóm tắt trên 1 dòng (`loaiHoSo: Cá nhân | hoTen: Hồ Thị Lan...`); click đúp vào dòng mở dialog sửa từng cặp key-value trực quan.
   * **Tab 3 — `YAML`:** Trình soạn thảo cú pháp khai báo gọn nhẹ (chuẩn Midscene), tự động đồng bộ 2 chiều với tab No-Code.
   * **Tab 4 — `Pytest Code`:** Xem trước mã nguồn `pytest-playwright` sạch chuẩn PEP 8 với nút `Copy` nhanh ở góc trên phải.

### 4.4. On-Demand Right Slide-Over Drawer (`380px - 420px`)
Sử dụng `Pivot` trên cùng để chuyển đổi giữa 2 chế độ:

* **Chế độ A — `AI Copilot` (Trợ lý hội thoại & Hành động):**
  * **Thanh ngữ cảnh:** Dropdown chọn Model (`Gemini 2.5 Flash`, `GPT-4o`, `DeepSeek V3`, `Ollama`) + các nút chọn ngữ cảnh dạng viên thuốc (`PillPushButton`): `@Case` (bật mặc định), `@DOM`, `@Error`.
  * **Gợi ý nhanh (lưới `2x2` khi khung chat trống):** *Sinh 5 bộ Test Data biên*, *Giải thích bước lỗi*, *Chuyển sang YAML*, *Bổ sung Assertions*.
  * **Khung hội thoại Streaming (`TextBrowser`):** Hiển thị chữ chạy mượt theo thời gian thực qua `qasync`. Các khối dữ liệu hoặc bước test do AI đề xuất đi kèm nút **`Áp dụng vào Test Case`** để ghi trực tiếp vào dữ liệu đang mở.
* **Chế độ B — `Trình duyệt` (Live CDP Screencast & Visual Inspector):**
  * **Thanh điều khiển trên khung hình:** Nút bật/tắt `Inspect Element` (`TogglePushButton`), nút `Mở trình duyệt`, và nhãn độ phân giải (`1280x720`).
  * **Canvas hiển thị khung hình CDP:** Nhận ảnh JPEG từ `Page.startScreencast` (không dùng `QtWebEngine`). Khi bật `Inspect Element` và click lên canvas ở tọa độ vật lý $(x_{\text{widget}}, y_{\text{widget}})$, hệ thống quy đổi sang tọa độ CSS viewport chuẩn trên mọi màn hình HiDPI/Retina theo công thức:
    $$x_{\text{css}} = (x_{\text{widget}} - \text{offset}_x) \times \frac{W_{\text{viewport}}}{W_{\text{rendered}}}, \quad y_{\text{css}} = (y_{\text{widget}} - \text{offset}_y) \times \frac{H_{\text{viewport}}}{H_{\text{rendered}}}$$
  * **Nhật ký chạy rút gọn (phía dưới khung hình):** Hiển thị tối đa 5 dòng trạng thái bước đang chạy (`[✓] Bước 1: Mở trang (0.3s)`), tự động ngắt dòng theo chiều rộng panel (`WidgetWidth`) để không xuất hiện thanh cuộn ngang.

### 4.5. Modal Dialogs (Tách biệt khỏi màn hình chính)
* **AI Test Wizard (`MessageBoxBase`):** Cửa sổ nổi `680x520px` dẫn dắt 3 bước: *(1) Nhập tài liệu SRS / Link Figma / URL* $\rightarrow$ *(2) Chọn Test Scenarios đề xuất* $\rightarrow$ *(3) Sinh Test Cases & Draft Script*.
* **Command Palette (`Ctrl+K` / `Cmd+K`):** Hộp tìm kiếm nhanh nổi ở giữa màn hình cho phép nhảy tới bất kỳ Test Case nào, chuyển đổi Skill hoặc xuất báo cáo chỉ bằng bàn phím.

---

## 5. Quản Lý Trạng Thái UX & Phím Tắt Toàn Cục

### 5.1. Các trạng thái giao diện bắt buộc (UI States)

| Trạng thái | Cách thể hiện trên giao diện |
| :--- | :--- |
| **Chưa mở Test Case (Empty State)** | Vùng trung tâm hiển thị icon trung tâm kèm 2 nút lớn: `Tạo kịch bản bằng AI` và `Import dự án cũ`. |
| **Đang sinh dữ liệu / Đồng bộ URL** | Hiển thị `IndeterminateProgressBar` thanh mảnh `2px` ngay dưới Top Bar; khóa nút `Chạy Test` và đổi thành `Đang xử lý...`. |
| **Kịch bản có bước `UNBOUND`** | Hiển thị badge màu hổ phách `N UNBOUND` cạnh tiêu đề và trên nút `Sync (N)` ở Top Bar. |
| **Cài đặt Chromium lần đầu** | Khi bấm `Chạy Test` lần đầu mà máy chưa có nhân trình duyệt, mở hộp thoại tiến trình tải Chromium tự động thay vì báo lỗi. |

### 5.2. Bảng phím tắt chuẩn (Keyboard Shortcuts)

* `Ctrl + K` / `Cmd + K`: Mở Command Palette tìm kiếm nhanh.
* `Ctrl + \` / `Cmd + \`: Ẩn/hiện Sidebar trái.
* `Ctrl + I` / `Cmd + I`: Bật/tắt ngăn trượt **AI Copilot**.
* `Ctrl + Enter` / `Cmd + Enter`: Chạy Test Case đang mở (`Chạy Test`).
* `Esc`: Dừng phiên chạy hiện tại hoặc đóng ngăn trượt / hộp thoại.

---

## 6. Quy Tắc Kỹ Thuật Triển Khai Trên PySide6 & qasync

1. **Dùng `QMainWindow` tiêu chuẩn làm cửa sổ gốc:** Không dùng `FramelessWindow` của `qfluentwidgets` cho cửa sổ chính để đảm bảo tương thích tuyệt đối với vòng lặp sự kiện `qasync`, thanh menu hệ thống của macOS và luồng render khung hình CDP.
2. **Xóa widget động an toàn trên macOS:** Khi cập nhật lại danh sách thẻ bước test (`Step Cards`) trong layout, luôn gọi `widget.setParent(None)` và `widget.deleteLater()` để ngăn cảnh báo `Called accessibilityLabel on invalid object` trên macOS.
3. **Ngắt dòng tuyệt đối cho mọi khung log/chat:** Luôn cấu hình `setLineWrapMode(PlainTextEdit.LineWrapMode.WidgetWidth)` và `setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)` để ngăn thanh cuộn ngang phá vỡ bố cục khi thu hẹp cửa sổ.
4. **Kiểm thử giao diện tự động (`pytest-qt`):** Mỗi component UI phải tách biệt logic hiển thị khỏi tầng gọi API/Playwright thông qua Qt `Signal`, đảm bảo có thể chạy kiểm thử giao diện headless trong CI/CD.