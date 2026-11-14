# Tài liệu Đặc tả Thiết kế Lại Giao diện (UI/UX Redesign Specification)
**Dự án:** AI QA Test Studio (`GUI-test-tool`)  
**Công nghệ:** Python 3.10+ & `PySide6-Fluent-Widgets`  
**Mục tiêu:** Thẩm mỹ hiện đại • Thoáng & Dễ quét mắt • Thao tác mượt mà • Responsive đa màn hình

---

## 1. Mục tiêu & Nguyên tắc Thiết kế

Bản thiết kế lại giải quyết triệt để tình trạng chật chội, đè chữ và vỡ bố cục khi mở đồng thời nhiều bảng điều khiển trên giao diện hiện tại, dựa trên 4 nguyên tắc cốt lõi:

1. **Phân tầng thị giác rõ ràng (Visual Hierarchy):** Tách biệt không gian điều hướng, không gian biên tập chính và trợ lý AI bằng độ nâng bề mặt (Surface Elevation) và viền mờ thay vì đường kẻ dày.
2. **Không gian thở chuẩn hóa (8pt Grid System):** Tăng khoảng đệm (padding) và chiều cao dòng bảng để người dùng quét nhanh hàng chục bước kiểm thử mà không mỏi mắt.
3. **Thao tác công thái học (Ergonomics):** Loại bỏ các nút chức năng trùng lặp, đưa thao tác chỉnh sửa trực tiếp (inline action) vào từng dòng và mở rộng vùng nhập prompt AI.
4. **Thích ứng thông minh (Adaptive & Responsive):** Tự động điều phối tỷ lệ 3 cột theo chiều ngang cửa sổ, đảm bảo bảng `Steps` luôn giữ độ rộng tối thiểu $640\text{px}$.

---

## 2. Kiểm toán & Giải pháp Khắc phục Lỗi Hiện Tại

| # | Vấn đề trên giao diện cũ | Nguyên nhân kỹ thuật | Giải pháp thiết kế mới |
| :- | :--- | :--- | :--- |
| **1** | **Đè chữ & label trong `AI Wizard` (Bước 1/3)** | Đặt form nhập liệu trong layout cố định chiều cao, thiếu vùng cuộn trên macOS. | Bọc toàn bộ nội dung Wizard trong `SmoothScrollArea`, tách từng trường nhập thành `CardWidget` độc lập với khoảng cách cố định $12\text{px}$. |
| **2** | **Banner `UNBOUND` bị cắt ngang chữ** | Gán cứng chiều cao banner trong khi đoạn mô tả tự động xuống dòng khi mở Copilot. | Dùng `CardWidget` tự co giãn chiều cao theo nội dung (`Preferred` size policy), đặt nút `Sync ngay` căn giữa theo trục dọc. |
| **3** | **Thanh công cụ Copilot bóp méo các nút pill** | Dùng `QHBoxLayout` cứng cho `Auto`, `@Case`, `@DOM`, `@Error`. | Chuyển sang `FlowLayout` của `qfluentwidgets` để các thẻ ngữ cảnh tự rớt dòng gọn gàng khi thu hẹp cột phải. |
| **4** | **Lặp tiêu đề Test Case 2 lần & tràn ngang** | Hiển thị chuỗi `TC01_... — TC01_...` bằng `QLabel` thường không rút gọn. | Dùng `BreadcrumbBar` hiển thị đường dẫn thư mục và `ElidedTitleLabel` tự rút gọn dấu `...` kèm `Tooltip` toàn văn. |
| **5** | **Bảng `Test Data` chiếm cố định ~28% chiều cao** | Chia layout cứng khiến bảng `Steps` chỉ hiện 4–5 dòng dù chưa cần sửa dữ liệu. | Thiết kế thành `BottomDataDrawer` có nút thu gọn 1 chạm về thanh bar $40\text{px}$, nhường tối đa chiều cao cho `Steps`. |
| **6** | **Cửa sổ `Cấu hình hệ thống` dàn trải** | Dùng 7 tab ngang dễ tràn; `SpinBox` kéo giãn hết chiều ngang dialog. | Chuyển sang bố cục **Master-Detail** (cây danh mục bên trái $175\text{px}$ + `SettingCardGroup` bên phải), cố định độ rộng `SpinBox` ở mức $135\text{px}$. |

---

## 3. Hệ thống Design Tokens

### 3.1. Phân tầng bề mặt (Surface Elevation)
Sử dụng màu bán trong suốt `rgba(...)` để giao diện tự động tương thích hoàn hảo khi chuyển đổi giữa `Theme.DARK` và `Theme.LIGHT`:
* **Canvas nền chính:** Nền mặc định của `FluentWindow` / `QWidget`.
* **Panel Dock (Explorer & Copilot):** `background-color: rgba(255, 255, 255, 0.02);`
* **Card / Bảng dữ liệu:** `background-color: rgba(255, 255, 255, 0.025); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 10px;`

### 3.2. Quy chuẩn Khoảng cách (8pt Grid)
* **Padding vùng Workspace trung tâm:** `20px` (trái/phải), `14px` (trên/dưới).
* **Padding nội bộ Dock & Card:** `12px` – `16px`.
* **Khoảng cách giữa các thành phần (Gap):** `10px` – `12px`.
* **Chiều cao dòng bảng `Steps`:** `42px` (tạo độ thoáng rõ rệt cho Badge và chữ).
* **Chiều cao dòng bảng `Test Data`:** `36px`.

### 3.3. Phân cấp chữ (Typography)
* **Breadcrumb điều hướng:** `12px`, màu phụ (`CaptionLabel`).
* **Tiêu đề Test Case chính:** `18px`, `SemiBold (600)`, tự động elide khi hẹp.
* **Tiêu đề nhóm / Card:** `14px`, `SemiBold (600)` (`StrongBodyLabel`).
* **Nội dung bảng & Form:** `13px`, `Regular (400)`.

### 3.4. Bảng màu ngữ nghĩa (Semantic Colors)
* **Action `click`:** Nền `rgba(56, 189, 248, 0.15)` • Chữ `#38BDF8` (Xanh dương)
* **Action `fill`:** Nền `rgba(52, 211, 153, 0.15)` • Chữ `#34D399` (Xanh ngọc)
* **Action `select`:** Nền `rgba(167, 139, 250, 0.15)` • Chữ `#A78BFA` (Tím)
* **Action `assert` / Cảnh báo `UNBOUND`:** Nền `rgba(251, 191, 36, 0.15)` • Chữ `#FBBF24` (Vàng hổ phách)
* **Trạng thái `BOUND` / `SUCCESS` / `MCP Ready`:** `#34D399` (Xanh lá)

---

## 4. Kiến trúc Layout 4 Vùng (Wireframe)

```text
+----------------------------------------------------------------------------------+
| [☰] [Tạo AI]  [ https://example.com                  ] [Sync (12)] [12 UNBOUND]  |
|                                                        [▶ Chạy] [🤖] [⚙]        |
+------------------+---------------------------------------+-----------------------+
| Explorer (260px) | Center Workspace (Flex, min 640px)    | Right Dock (380px)    |
| [Tìm kiếm...   ] | Imported Legacy Project > TS_KiemThu  | [AI Copilot|Trình duyệt]|
| [Cases|EL|Vars ] | TC01_ChucNangTaoHoSo...   [+ Thêm bước] | [AI Chat | AI Wizard ]|
|                  | [Spec] [Steps (12)] [YAML] [Pytest]   | +-------------------+ |
| ▼ QuanLyNguoi... | +-----------------------------------+ | | FlowLayout Pills: | |
|   ▼ TS_KiemThu   | | ⚠ Có 12 bước chưa gắn selector    | | | [Auto ▾] [@Case]  | |
|     ● TC01_...   | |   Nhập Target URL rồi nhấn Sync   | | | [@DOM] [@Error]   | |
|     ● TC02_...   | +-----------------------------------+ | +-------------------+ |
|                  | +-----------------------------------+ | +-------------------+ |
|                  | | # | Action | Intent | Data | Bind | | | SmoothScrollArea  | |
|                  | | 1 | click  | Mở form...| —   | UNB. | | | (Wizard Cards /   | |
|                  | | 2 | fill   | Nhập tên..| Lan | UNB. | | |  Chat History)    | |
|                  | +-----------------------------------+ | |                   | |
|                  | +-----------------------------------+ | | +---------------+ | |
|                  | | [Test Data (3)] [Console Log] [⌄] | | | | Multi-line    | | |
|                  | | (Thu gọn thành bar 40px khi cần)  | | | | Prompt Input  | | |
|                  | +-----------------------------------+ | +-------------------+ |
+------------------+---------------------------------------+-----------------------+
| Imported Legacy Project • 67 shared elements      ● MCP: Ready • PW: On | 0/8k   |
+----------------------------------------------------------------------------------+
```

### Chi tiết các vùng chức năng:
1. **Top Command Bar (`48px`):** Chứa nút bật/tắt Explorer (`☰`), nút mở nhanh `Tạo AI` (trỏ thẳng vào tab `AI Wizard`), thanh địa chỉ `Target URL` giãn linh hoạt, cụm đồng bộ (`Sync`, badge `12 UNBOUND`), nút thực thi chính (`Chạy`), nút bật/tắt `Copilot` và `Cài đặt`.
2. **Left Explorer Dock (`230px` – `320px`):** Ô tìm kiếm tức thì (`SearchLineEdit`), 3 tab ngắn gọn (`Cases`, `EL (67)`, `Vars`) tránh cắt chữ, và cây thư mục dự án (`TreeWidget`).
3. **Center Workspace (Linh hoạt):**
   * Thanh `BreadcrumbBar` + Tiêu đề 1 dòng rút gọn (`ElidedTitleLabel`) + Nút `Thêm bước`.
   * Thanh chuyển chế độ xem: `Spec` | `Steps` | `YAML` | `Pytest`.
   * `UnboundAlertBanner` tự co giãn.
   * `QSplitter` dọc chia tỷ lệ $75\%$ cho bảng `Steps` và $25\%$ cho `BottomDataDrawer`.
4. **Right Copilot & Browser Dock (`340px` – `450px`):** Chuyển đổi nhanh giữa `AI Copilot` (gồm `AI Chat` và `AI Wizard`) và `Trình duyệt` (Playwright Live Viewport).

---

## 5. Cơ chế Responsive & Breakpoints

Hệ thống tự động điều chỉnh bố cục dựa trên chiều rộng cửa sổ ($W$) thông qua `resizeEvent` và các signal bật/tắt panel:

| Chế độ màn hình | Điều kiện chiều rộng ($W$) | Hành vi tự động của giao diện |
| :--- | :--- | :--- |
| **Desktop Wide** | $W \ge 1440\text{px}$ | Hiển thị đồng thời cả 3 cột (`Explorer` + `Workspace` + `Copilot`). |
| **Laptop 13"–14"** | $1150\text{px} \le W < 1440\text{px}$ | Khi người dùng mở `Copilot`, `Explorer` bên trái tự động ẩn (Auto-Drawer) để giữ bảng `Steps` luôn rộng rãi. Người dùng vẫn có thể mở lại `Explorer` bất cứ lúc nào bằng nút `☰`. |
| **Compact / Split-screen** | $W < 1150\text{px}$ | Thu gọn nhãn chữ trên các nút phụ ở TopBar (chỉ giữ icon) để bảo vệ độ rộng ô nhập `Target URL`. |

---

## 6. Ánh xạ Widget `PySide6-Fluent-Widgets`

Để giao diện đồng bộ chuẩn Fluent Design trên cả macOS và Windows, sử dụng đúng bộ widget sau:

* **Chuyển đổi tab / chế độ:** `SegmentedWidget` (thay cho `QTabWidget` truyền thống).
* **Danh sách thẻ ngữ cảnh (`@Case`, `@DOM`, Quick Prompts):** `FlowLayout` + `PillPushButton`.
* **Vùng nhập liệu dài (Wizard, Settings):** `SmoothScrollArea` kết hợp `CardWidget` hoặc `SettingCardGroup`.
* **Bảng dữ liệu (`Steps`, `Test Data`):** `TableWidget` tắt `verticalHeader`, bật `Stretch` cho cột nội dung chính (`Intent`, `Data`, `Parameters`).
* **Hộp thoại cấu hình:** `TreeWidget` (cột danh mục trái) + `SettingCard` / `SpinBox` (cột nội dung phải).

---

## 7. Cấu trúc Thư mục Đề xuất cho `GUI-test-tool`

```text
src/
└── ui/
    ├── __init__.py
    ├── main_window.py              # AIQATestStudioWindow & Responsive Orchestrator
    ├── components/
    │   ├── __init__.py
    │   ├── badges.py               # ElidedTitleLabel, ActionBadge, UnboundAlertBanner
    │   └── bottom_drawer.py        # BottomDataDrawer (Test Data Matrix + Console Log)
    ├── docks/
    │   ├── __init__.py
    │   ├── explorer_dock.py        # Cây thư mục Cases, EL (67), Vars
    │   └── copilot_dock.py         # AI Chat, AI Wizard (3 bước) & Live Browser Tab
    └── dialogs/
        ├── __init__.py
        └── settings_dialog.py      # Cấu hình hệ thống dạng Master-Detail
```

---

## 8. Mã nguồn Tham chiếu Hoàn chỉnh (`main_window_preview.py`)

Đoạn mã dưới đây tích hợp toàn bộ kiến trúc trên vào một file chạy độc lập để kiểm thử trực tiếp trước khi tách module:

```python
import sys
from PySide6.QtCore import Qt
from PySide6.QtGui import QFontMetrics
from PySide6.QtWidgets import (
    QApplication, QWidget, QVBoxLayout, QHBoxLayout,
    QSplitter, QStackedWidget, QHeaderView, QTableWidgetItem,
    QDialog, QFrame, QSizePolicy, QTreeWidgetItem, QLabel
)
from qfluentwidgets import (
    setTheme, Theme, FluentIcon as FIF,
    SegmentedWidget, SearchLineEdit, LineEdit, TextEdit,
    PrimaryPushButton, PushButton, TransparentToolButton,
    TransparentToggleToolButton, PillPushButton, ComboBox,
    SpinBox, TreeWidget, TableWidget, CardWidget,
    SmoothScrollArea, FlowLayout, CaptionLabel,
    StrongBodyLabel, BreadcrumbBar,
    SettingCardGroup, SettingCard
)


class ElidedTitleLabel(QLabel):
    """Tiêu đề tự rút gọn dấu '...' khi cửa sổ hẹp, không đẩy tràn layout."""
    def __init__(self, text: str = "", parent=None):
        super().__init__(text, parent)
        self._full_text = text
        self.setToolTip(text)
        self.setMinimumWidth(120)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Preferred)
        self.setStyleSheet("font-size: 18px; font-weight: 600;")

    def setText(self, text: str):
        self._full_text = text
        self.setToolTip(text)
        self._update_elided()

    def resizeEvent(self, event):
        super().resizeEvent(event)
        self._update_elided()

    def _update_elided(self):
        fm = QFontMetrics(self.font())
        elided = fm.elidedText(self._full_text, Qt.TextElideMode.ElideRight, max(120, self.width()))
        super().setText(elided)


class ActionBadge(QLabel):
    """Badge màu phân loại hành động giúp mắt quét nhanh từng bước."""
    STYLES = {
        "click":  ("rgba(56, 189, 248, 0.15)", "#38BDF8"),
        "fill":   ("rgba(52, 211, 153, 0.15)", "#34D399"),
        "select": ("rgba(167, 139, 250, 0.15)", "#A78BFA"),
        "assert": ("rgba(251, 191, 36, 0.15)", "#FBBF24"),
    }

    def __init__(self, action: str, parent=None):
        super().__init__(action, parent)
        bg, fg = self.STYLES.get(action.lower(), ("rgba(148, 163, 184, 0.15)", "#94A3B8"))
        self.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.setFixedSize(64, 24)
        self.setStyleSheet(f"""
            background-color: {bg};
            color: {fg};
            border-radius: 6px;
            font-size: 12px;
            font-weight: 600;
        """)


class UnboundAlertBanner(CardWidget):
    """Banner cảnh báo tự giãn chiều cao theo nội dung."""
    def __init__(self, count: int = 12, parent=None):
        super().__init__(parent)
        self.setStyleSheet("""
            UnboundAlertBanner {
                background-color: rgba(245, 158, 11, 0.10);
                border: 1px solid rgba(245, 158, 11, 0.30);
                border-radius: 10px;
            }
        """)
        row = QHBoxLayout(self)
        row.setContentsMargins(16, 12, 16, 12)
        row.setSpacing(14)

        icon_lbl = QLabel("!", self)
        icon_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        icon_lbl.setFixedSize(22, 22)
        icon_lbl.setStyleSheet("""
            background-color: #FBBF24;
            color: #111827;
            border-radius: 11px;
            font-weight: 700;
        """)
        row.addWidget(icon_lbl, 0, Qt.AlignmentFlag.AlignVCenter)

        col = QVBoxLayout()
        col.setSpacing(3)
        title = StrongBodyLabel(f"Có {count} bước chưa gắn selector (UNBOUND)", self)
        title.setStyleSheet("color: #FBBF24;")
        desc = CaptionLabel(
            "Nhập Target URL trên thanh công cụ rồi nhấn Sync để Agent tự động ánh xạ selector từ DOM.",
            self
        )
        desc.setWordWrap(True)
        col.addWidget(title)
        col.addWidget(desc)
        row.addLayout(col, 1)

        self.sync_btn = PrimaryPushButton(FIF.SYNC, "Sync ngay", self)
        self.sync_btn.setFixedHeight(32)
        close_btn = TransparentToolButton(FIF.CLOSE, self)
        close_btn.setFixedSize(30, 30)
        close_btn.clicked.connect(self.hide)

        row.addWidget(self.sync_btn, 0, Qt.AlignmentFlag.AlignVCenter)
        row.addWidget(close_btn, 0, Qt.AlignmentFlag.AlignVCenter)


class BottomDataDrawer(CardWidget):
    """Ngăn kéo dưới cùng có thể thu gọn thành thanh 42px."""
    def __init__(self, parent=None):
        super().__init__(parent)
        self._expanded = True
        self.setStyleSheet("""
            BottomDataDrawer {
                background-color: rgba(255, 255, 255, 0.025);
                border: 1px solid rgba(255, 255, 255, 0.08);
                border-radius: 10px;
            }
        """)
        root = QVBoxLayout(self)
        root.setContentsMargins(12, 6, 12, 10)
        root.setSpacing(8)

        header = QHBoxLayout()
        header.setSpacing(8)

        self.tabs = SegmentedWidget(self)
        self.tabs.addItem("data", "Test Data (3)")
        self.tabs.addItem("console", "Console Log")
        self.tabs.setCurrentItem("data")

        self.add_row_btn = PushButton(FIF.ADD, "Thêm bộ dữ liệu", self)
        self.add_row_btn.setFixedHeight(28)

        self.toggle_btn = TransparentToolButton(FIF.CHEVRON_DOWN, self)
        self.toggle_btn.setFixedSize(28, 28)
        self.toggle_btn.setToolTip("Thu gọn / Mở rộng bảng dữ liệu")
        self.toggle_btn.clicked.connect(self.toggle_drawer)

        header.addWidget(self.tabs)
        header.addWidget(self.add_row_btn)
        header.addStretch(1)
        header.addWidget(self.toggle_btn)
        root.addLayout(header)

        self.stack = QStackedWidget(self)
        self.stack.addWidget(self._build_data_table())
        self.stack.addWidget(self._build_console_view())
        self.tabs.currentItemChanged.connect(
            lambda k: self.stack.setCurrentIndex(0 if k == "data" else 1)
        )
        root.addWidget(self.stack, 1)

    def _build_data_table(self) -> TableWidget:
        table = TableWidget(self)
        table.setColumnCount(3)
        table.setHorizontalHeaderLabels(["Scenario / Case", "Parameters (Tóm tắt)", "Expected"])
        table.verticalHeader().setVisible(False)
        table.verticalHeader().setDefaultSectionSize(36)
        hdr = table.horizontalHeader()
        hdr.setSectionResizeMode(0, QHeaderView.ResizeMode.Interactive)
        table.setColumnWidth(0, 210)
        hdr.setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        hdr.setSectionResizeMode(2, QHeaderView.ResizeMode.Fixed)
        table.setColumnWidth(2, 105)

        rows = [
            ("Trường hợp chuẩn (HappyPath)", "cccd: 040085999999 | diaBan: Cam Lộ – Quảng Trị | hoTen: Hồ Thị Lan", "SUCCESS"),
            ("Tạo hồ sơ Hộ gia đình", "cccd: 001060012345 | diaBan: Cam Lộ – Quảng Trị | hoTen: Đặng Văn Hùng", "SUCCESS"),
            ("Tạo hồ sơ Cộng đồng", "hoTen: Mái ấm Hồng Đức | loaiHoSo: Cộng đồng | nhomDoiTuong: Trẻ mồ côi", "SUCCESS"),
        ]
        table.setRowCount(len(rows))
        for r, (sc, param, exp) in enumerate(rows):
            table.setItem(r, 0, QTableWidgetItem(sc))
            table.setItem(r, 1, QTableWidgetItem(param))
            exp_item = QTableWidgetItem(exp)
            exp_item.setForeground(Qt.GlobalColor.green)
            table.setItem(r, 2, exp_item)
        return table

    def _build_console_view(self) -> TextEdit:
        log = TextEdit(self)
        log.setReadOnly(True)
        log.setPlainText("[10:00:00] MCP Server: bundled ready • Playwright Chromium standby.\n")
        return log

    def toggle_drawer(self):
        self._expanded = not self._expanded
        self.stack.setVisible(self._expanded)
        self.add_row_btn.setVisible(self._expanded)
        self.toggle_btn.setIcon(FIF.CHEVRON_DOWN if self._expanded else FIF.CHEVRON_RIGHT)
        if self._expanded:
            self.setMinimumHeight(170)
            self.setMaximumHeight(420)
        else:
            self.setFixedHeight(42)


class CopilotBrowserDock(QFrame):
    """Cột phải tích hợp AI Chat, AI Wizard và Live Browser."""
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setMinimumWidth(340)
        self.setMaximumWidth(450)
        self.setObjectName("RightDock")
        self.setStyleSheet("""
            #RightDock {
                background-color: rgba(255, 255, 255, 0.02);
                border-left: 1px solid rgba(255, 255, 255, 0.08);
            }
        """)
        root = QVBoxLayout(self)
        root.setContentsMargins(14, 12, 14, 12)
        root.setSpacing(12)

        self.dock_tabs = SegmentedWidget(self)
        self.dock_tabs.addItem("copilot", "AI Copilot", icon=FIF.ROBOT)
        self.dock_tabs.addItem("browser", "Trình duyệt", icon=FIF.GLOBE)
        self.dock_tabs.setCurrentItem("copilot")
        root.addWidget(self.dock_tabs)

        self.main_stack = QStackedWidget(self)
        self.main_stack.addWidget(self._build_copilot_area())
        self.main_stack.addWidget(self._build_browser_preview())
        self.dock_tabs.currentItemChanged.connect(
            lambda k: self.main_stack.setCurrentIndex(0 if k == "copilot" else 1)
        )
        root.addWidget(self.main_stack, 1)

    def _build_copilot_area(self) -> QWidget:
        wrap = QWidget()
        v = QVBoxLayout(wrap)
        v.setContentsMargins(0, 0, 0, 0)
        v.setSpacing(10)

        self.sub_tabs = SegmentedWidget(wrap)
        self.sub_tabs.addItem("chat", "AI Chat", icon=FIF.CHAT)
        self.sub_tabs.addItem("wizard", "AI Wizard (3 bước)", icon=FIF.IOT)
        self.sub_tabs.setCurrentItem("wizard")
        v.addWidget(self.sub_tabs)

        pill_box = QWidget(wrap)
        flow = FlowLayout(pill_box, needAni=False)
        flow.setContentsMargins(0, 0, 0, 0)
        flow.setHorizontalSpacing(6)
        flow.setVerticalSpacing(6)

        model_cb = ComboBox(wrap)
        model_cb.addItems(["Auto (Thinking)", "Qwen2.5-Coder", "Claude / GPT"])
        model_cb.setFixedHeight(28)
        flow.addWidget(model_cb)

        for tag in ["@Case", "@DOM", "@Error"]:
            p = PillPushButton(tag, wrap)
            p.setFixedHeight(28)
            flow.addWidget(p)
        v.addWidget(pill_box)

        self.copilot_stack = QStackedWidget(wrap)
        self.copilot_stack.addWidget(self._build_chat_view())
        self.copilot_stack.addWidget(self._build_wizard_view())
        self.copilot_stack.setCurrentIndex(1)
        self.sub_tabs.currentItemChanged.connect(
            lambda k: self.copilot_stack.setCurrentIndex(0 if k == "chat" else 1)
        )
        v.addWidget(self.copilot_stack, 1)
        return wrap

    def _build_chat_view(self) -> QWidget:
        page = QWidget()
        v = QVBoxLayout(page)
        v.setContentsMargins(0, 4, 0, 0)
        v.setSpacing(10)

        quick_wrap = QWidget()
        qf = FlowLayout(quick_wrap, needAni=False)
        qf.setContentsMargins(0, 0, 0, 0)
        qf.setHorizontalSpacing(6)
        qf.setVerticalSpacing(6)
        for text in ["Sinh Test Data biên", "Giải thích lỗi bước", "Chuyển YAML", "Thêm Assertions"]:
            btn = PushButton(text, page)
            btn.setFixedHeight(28)
            qf.addWidget(btn)
        v.addWidget(quick_wrap)

        history = TextEdit(page)
        history.setReadOnly(True)
        history.setPlaceholderText("Sẵn sàng hỗ trợ sinh kịch bản, tự sửa selector (Self-Heal)...")
        v.addWidget(history, 1)

        composer = CardWidget(page)
        cv = QVBoxLayout(composer)
        cv.setContentsMargins(10, 8, 10, 8)
        cv.setSpacing(6)
        prompt = TextEdit(composer)
        prompt.setFixedHeight(64)
        prompt.setPlaceholderText("Nhập yêu cầu cho Copilot (dùng @Case, @DOM)...")
        prompt.setStyleSheet("border: none; background: transparent;")
        cv.addWidget(prompt)

        btn_row = QHBoxLayout()
        btn_row.addStretch(1)
        btn_row.addWidget(PrimaryPushButton(FIF.SEND, "Gửi yêu cầu", composer))
        cv.addLayout(btn_row)
        v.addWidget(composer)
        return page

    def _build_wizard_view(self) -> QWidget:
        page = QWidget()
        v = QVBoxLayout(page)
        v.setContentsMargins(0, 4, 0, 0)
        v.setSpacing(10)

        steps_bar = SegmentedWidget(page)
        steps_bar.addItem("1", "1. Nhập liệu")
        steps_bar.addItem("2", "2. Scenario")
        steps_bar.addItem("3", "3. Duyệt")
        steps_bar.setCurrentItem("1")
        v.addWidget(steps_bar)

        scroll = SmoothScrollArea(page)
        scroll.setWidgetResizable(True)
        scroll.setStyleSheet("background: transparent; border: none;")

        container = QWidget()
        form = QVBoxLayout(container)
        form.setContentsMargins(2, 4, 6, 4)
        form.setSpacing(12)

        def add_input_card(title: str, hint: str, widget: QWidget):
            card = CardWidget(container)
            cl = QVBoxLayout(card)
            cl.setContentsMargins(12, 10, 12, 10)
            cl.setSpacing(6)
            cl.addWidget(StrongBodyLabel(title, card))
            if hint:
                h = CaptionLabel(hint, card)
                h.setWordWrap(True)
                cl.addWidget(h)
            cl.addWidget(widget)
            form.addWidget(card)

        url_in = LineEdit()
        url_in.setPlaceholderText("https://example.com")
        add_input_card("URL đích (tùy chọn)", "Dùng để Agent quét DOM trực tiếp khi tạo draft.", url_in)

        figma_in = LineEdit()
        figma_in.setPlaceholderText("https://www.figma.com/file/...")
        add_input_card("Figma Design Link", "", figma_in)

        srs_row = QHBoxLayout()
        srs_in = LineEdit()
        srs_in.setPlaceholderText("Chọn file .pdf, .docx, .md...")
        srs_in.setReadOnly(True)
        srs_btn = PushButton(FIF.FOLDER, "Chọn tệp")
        srs_row.addWidget(srs_in, 1)
        srs_row.addWidget(srs_btn)
        srs_wrap = QWidget()
        srs_wrap.setLayout(srs_row)
        add_input_card("Tài liệu đặc tả (SRS)", "", srs_wrap)

        md_in = TextEdit()
        md_in.setMinimumHeight(100)
        md_in.setPlaceholderText("Mô tả luồng nghiệp vụ hoặc dán User Story...")
        add_input_card("Mô tả tính năng / Markdown", "", md_in)

        form.addStretch(1)
        scroll.setWidget(container)
        v.addWidget(scroll, 1)

        footer = QHBoxLayout()
        footer.addWidget(PushButton("Về Chat"))
        footer.addStretch(1)
        footer.addWidget(PrimaryPushButton(FIF.RIGHT_ARROW, "Tiếp theo"))
        v.addLayout(footer)
        return page

    def _build_browser_preview(self) -> QWidget:
        card = CardWidget(self)
        v = QVBoxLayout(card)
        v.setAlignment(Qt.AlignmentFlag.AlignCenter)
        v.addWidget(StrongBodyLabel("Live Playwright Viewport & Element Picker"))
        v.addWidget(CaptionLabel("Nhấn 'Sync' hoặc 'Chạy' để hiển thị luồng trình duyệt trực tiếp."))
        return card


class SettingsDialog(QDialog):
    """Cấu hình hệ thống dạng Master-Detail chuẩn Fluent."""
    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Cấu hình hệ thống")
        self.resize(800, 540)

        root = QVBoxLayout(self)
        root.setContentsMargins(18, 18, 18, 18)
        root.setSpacing(14)

        body = QHBoxLayout()
        body.setSpacing(16)

        nav = TreeWidget(self)
        nav.setHeaderHidden(True)
        nav.setFixedWidth(175)
        for item in ["Chung", "Browser", "AI APIs", "Models", "Routing", "Copilot", "MCP"]:
            QTreeWidgetItem(nav, [item])
        body.addWidget(nav)

        scroll = SmoothScrollArea(self)
        scroll.setWidgetResizable(True)
        scroll.setStyleSheet("background: transparent; border: none;")

        content = QWidget()
        cv = QVBoxLayout(content)
        cv.setContentsMargins(0, 0, 8, 0)
        cv.setSpacing(12)

        group = SettingCardGroup("Giới hạn runtime Copilot", content)
        specs = [
            ("Summary max chars", "Số ký tự tối đa khi nén ngữ cảnh", 4000, 1000, 32000),
            ("Debate max rounds", "Số vòng tự phản biện tối đa của Agent", 3, 1, 10),
            ("Loop repeat limit", "Ngưỡng lặp hành động chống vòng lặp vô hạn", 5, 1, 20),
            ("Clarify timeout (ms)", "Thời gian chờ xác nhận từ người dùng", 15000, 1000, 60000),
            ("Lean tail tokens", "Token dự phòng cho bước tổng hợp cuối", 1024, 256, 8192),
        ]
        for title, desc, val, lo, hi in specs:
            card = SettingCard(FIF.SETTING, title, desc, group)
            spin = SpinBox(card)
            spin.setRange(lo, hi)
            spin.setValue(val)
            spin.setFixedWidth(135)
            card.hBoxLayout.addWidget(spin, 0, Qt.AlignmentFlag.AlignRight)
            card.hBoxLayout.addSpacing(14)
            group.addSettingCard(card)

        cv.addWidget(group)
        cv.addStretch(1)
        scroll.setWidget(content)
        body.addWidget(scroll, 1)
        root.addLayout(body, 1)

        footer = QHBoxLayout()
        footer.addStretch(1)
        cancel = PushButton("Hủy", self)
        cancel.clicked.connect(self.reject)
        save = PrimaryPushButton("Lưu cấu hình", self)
        save.clicked.connect(self.accept)
        footer.addWidget(cancel)
        footer.addWidget(save)
        root.addLayout(footer)


class AIQATestStudioWindow(QWidget):
    """Cửa sổ chính AI QA Test Studio v4.2 - Responsive 4 vùng."""
    def __init__(self):
        super().__init__()
        self.setWindowTitle("AI QA Test Studio v4.2")
        self.resize(1440, 880)
        self.setMinimumSize(980, 640)

        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        # 1. Top Command Bar
        top_bar = QFrame(self)
        top_bar.setFixedHeight(48)
        top_bar.setStyleSheet("border-bottom: 1px solid rgba(255, 255, 255, 0.08);")
        tb = QHBoxLayout(top_bar)
        tb.setContentsMargins(12, 6, 12, 6)
        tb.setSpacing(10)

        self.nav_toggle = TransparentToggleToolButton(FIF.MENU, self)
        self.nav_toggle.setChecked(True)
        self.nav_toggle.setToolTip("Ẩn/Hiện cây thư mục kịch bản")
        self.nav_toggle.toggled.connect(self._toggle_explorer)

        self.ai_create_btn = PushButton(FIF.IOT, "Tạo AI", self)
        self.ai_create_btn.setToolTip("Mở AI Wizard tạo kịch bản tự động")
        self.ai_create_btn.clicked.connect(self._open_ai_wizard)

        self.url_bar = LineEdit(self)
        self.url_bar.setText("https://example.com")
        self.url_bar.setPlaceholderText("Nhập Target URL ứng dụng cần kiểm thử...")

        self.sync_btn = PushButton(FIF.SYNC, "Sync (12)", self)
        self.unbound_pill = PillPushButton("12 UNBOUND", self)
        self.unbound_pill.setStyleSheet("color: #FBBF24; font-weight: 600;")

        self.run_btn = PrimaryPushButton(FIF.PLAY, "Chạy", self)
        self.copilot_toggle = TransparentToggleToolButton(FIF.ROBOT, self)
        self.copilot_toggle.setChecked(True)
        self.copilot_toggle.setToolTip("Ẩn/Hiện AI Copilot")
        self.copilot_toggle.toggled.connect(self._toggle_copilot)

        self.settings_btn = TransparentToolButton(FIF.SETTING, self)
        self.settings_btn.setToolTip("Cấu hình hệ thống")
        self.settings_btn.clicked.connect(lambda: SettingsDialog(self).exec())

        tb.addWidget(self.nav_toggle)
        tb.addWidget(self.ai_create_btn)
        tb.addWidget(self.url_bar, 1)
        tb.addWidget(self.sync_btn)
        tb.addWidget(self.unbound_pill)
        tb.addWidget(self.run_btn)
        tb.addWidget(self.copilot_toggle)
        tb.addWidget(self.settings_btn)
        root.addWidget(top_bar)

        # 2. Main 3-Column Splitter
        self.h_splitter = QSplitter(Qt.Orientation.Horizontal, self)
        self.h_splitter.setHandleWidth(2)

        self.explorer_dock = self._build_explorer_dock()
        self.center_workspace = self._build_center_workspace()
        self.right_dock = CopilotBrowserDock(self)

        self.h_splitter.addWidget(self.explorer_dock)
        self.h_splitter.addWidget(self.center_workspace)
        self.h_splitter.addWidget(self.right_dock)
        self.h_splitter.setSizes([260, 800, 380])
        root.addWidget(self.h_splitter, 1)

        # 3. Compact Status Bar
        status_bar = QFrame(self)
        status_bar.setFixedHeight(28)
        status_bar.setStyleSheet("border-top: 1px solid rgba(255, 255, 255, 0.08);")
        sb = QHBoxLayout(status_bar)
        sb.setContentsMargins(14, 0, 14, 0)
        sb.setSpacing(12)
        sb.addWidget(CaptionLabel("Imported Legacy Project • 67 shared elements"))
        sb.addStretch(1)
        mcp_lbl = CaptionLabel("● MCP: Ready • PW: On")
        mcp_lbl.setStyleSheet("color: #34D399; font-weight: 600;")
        sb.addWidget(mcp_lbl)
        sb.addWidget(CaptionLabel("Skills: 7 Active"))
        sb.addWidget(CaptionLabel("Tokens: 0 / 8,000"))
        root.addWidget(status_bar)

    def _build_explorer_dock(self) -> QFrame:
        dock = QFrame(self)
        dock.setMinimumWidth(230)
        dock.setMaximumWidth(320)
        dock.setStyleSheet("border-right: 1px solid rgba(255, 255, 255, 0.08);")
        v = QVBoxLayout(dock)
        v.setContentsMargins(10, 12, 10, 12)
        v.setSpacing(10)

        search = SearchLineEdit(dock)
        search.setPlaceholderText("Tìm kịch bản, element...")
        v.addWidget(search)

        tabs = SegmentedWidget(dock)
        tabs.addItem("cases", "Cases")
        tabs.addItem("el", "EL (67)")
        tabs.addItem("vars", "Vars")
        tabs.setCurrentItem("cases")
        v.addWidget(tabs)

        tree = TreeWidget(dock)
        tree.setHeaderHidden(True)
        proj = QTreeWidgetItem(tree, ["Imported Legacy Project"])
        mod = QTreeWidgetItem(proj, ["QuanLyNguoiYeuThe"])
        suite = QTreeWidgetItem(mod, ["TS_KiemThu_TaoHoSo"])
        cases = [
            "TC01_TaoHoSo_LuongChinh",
            "TC02_TaoHoSo_HoGiaDinh",
            "TC03_TaoHoSo_CongDong",
            "TC04_KiemTraValidateCCCD",
            "TC05_DinhKemMinhChung",
        ]
        for idx, c in enumerate(cases):
            item = QTreeWidgetItem(suite, [f"● {c}"])
            if idx == 0:
                item.setSelected(True)
        tree.expandAll()
        v.addWidget(tree, 1)
        return dock

    def _build_center_workspace(self) -> QWidget:
        ws = QWidget(self)
        v = QVBoxLayout(ws)
        v.setContentsMargins(20, 14, 20, 14)
        v.setSpacing(12)

        breadcrumb = BreadcrumbBar(ws)
        breadcrumb.addItem("proj", "Imported Legacy Project")
        breadcrumb.addItem("mod", "QuanLyNguoiYeuThe")
        breadcrumb.addItem("suite", "TS_KiemThu_TaoHoSo")
        v.addWidget(breadcrumb)

        header_row = QHBoxLayout()
        header_row.setSpacing(12)
        self.case_title = ElidedTitleLabel("TC01_ChucNangTaoHoSoNguoiYeuThe_LuongChinh_HappyPath", ws)
        header_row.addWidget(self.case_title, 1)

        add_step_btn = PushButton(FIF.ADD, "Thêm bước", ws)
        add_step_btn.setFixedHeight(32)
        header_row.addWidget(add_step_btn)
        v.addLayout(header_row)

        view_tabs = SegmentedWidget(ws)
        for k, text in [("spec", "Spec"), ("steps", "Steps (12)"), ("yaml", "YAML"), ("pytest", "Pytest")]:
            view_tabs.addItem(k, text)
        view_tabs.setCurrentItem("steps")
        v.addWidget(view_tabs, 0, Qt.AlignmentFlag.AlignLeft)

        v.addWidget(UnboundAlertBanner(12, ws))

        v_splitter = QSplitter(Qt.Orientation.Vertical, ws)
        v_splitter.setHandleWidth(6)

        steps_card = CardWidget(ws)
        steps_card.setStyleSheet("""
            CardWidget {
                background-color: rgba(255, 255, 255, 0.025);
                border: 1px solid rgba(255, 255, 255, 0.08);
                border-radius: 10px;
            }
        """)
        sc_layout = QVBoxLayout(steps_card)
        sc_layout.setContentsMargins(8, 8, 8, 8)

        table = TableWidget(steps_card)
        table.setColumnCount(5)
        table.setHorizontalHeaderLabels(["#", "Action", "Intent (Mô tả hành động)", "Data / Input", "Binding"])
        table.verticalHeader().setVisible(False)
        table.verticalHeader().setDefaultSectionSize(42)

        hdr = table.horizontalHeader()
        hdr.setSectionResizeMode(0, QHeaderView.ResizeMode.Fixed)
        table.setColumnWidth(0, 44)
        hdr.setSectionResizeMode(1, QHeaderView.ResizeMode.Fixed)
        table.setColumnWidth(1, 88)
        hdr.setSectionResizeMode(2, QHeaderView.ResizeMode.Stretch)
        hdr.setSectionResizeMode(3, QHeaderView.ResizeMode.Stretch)
        hdr.setSectionResizeMode(4, QHeaderView.ResizeMode.Fixed)
        table.setColumnWidth(4, 118)

        steps_data = [
            ("1", "click", "Mở biểu mẫu tạo hồ sơ người yếu thế", "—"),
            ("2", "assert", "Kiểm tra thông tin người đăng ký tự động điền", "Theo tài khoản đăng nhập"),
            ("3", "select", "Chọn loại hồ sơ Cá nhân", "Cá nhân"),
            ("4", "fill", "Nhập thông tin người yếu thế đại diện", "Hồ Thị Lan | 040085999999 | Nữ"),
            ("5", "select", "Chọn nhóm đối tượng và địa bàn", "Hộ nghèo | Cam Lộ – Quảng Trị"),
            ("6", "fill", "Mô tả hoàn cảnh và nhu cầu trợ giúp", "Gia đình thuộc hộ nghèo, nhà dột nát..."),
            ("7", "click", "Tải lên hình ảnh minh chứng", "anh_minh_chung.jpg"),
        ]
        table.setRowCount(len(steps_data))
        for r, (num, act, intent, data) in enumerate(steps_data):
            num_item = QTableWidgetItem(num)
            num_item.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            table.setItem(r, 0, num_item)

            badge_wrap = QWidget()
            bl = QHBoxLayout(badge_wrap)
            bl.setContentsMargins(0, 0, 0, 0)
            bl.setAlignment(Qt.AlignmentFlag.AlignCenter)
            bl.addWidget(ActionBadge(act))
            table.setCellWidget(r, 1, badge_wrap)

            table.setItem(r, 2, QTableWidgetItem(intent))
            table.setItem(r, 3, QTableWidgetItem(data))

            bind_wrap = QWidget()
            bwl = QHBoxLayout(bind_wrap)
            bwl.setContentsMargins(4, 0, 4, 0)
            bwl.setAlignment(Qt.AlignmentFlag.AlignCenter)
            bind_btn = PillPushButton("UNBOUND")
            bind_btn.setFixedHeight(26)
            bind_btn.setStyleSheet("color: #FBBF24; font-size: 11px; font-weight: 600;")
            bind_btn.setToolTip("Nhấn để chọn Selector từ danh sách EL (67) hoặc Pick trên trình duyệt")
            bwl.addWidget(bind_btn)
            table.setCellWidget(r, 4, bind_wrap)

        sc_layout.addWidget(table)
        self.bottom_drawer = BottomDataDrawer(ws)

        v_splitter.addWidget(steps_card)
        v_splitter.addWidget(self.bottom_drawer)
        v_splitter.setSizes([540, 190])
        v.addWidget(v_splitter, 1)
        return ws

    def _toggle_explorer(self, visible: bool):
        self.explorer_dock.setVisible(visible)

    def _toggle_copilot(self, visible: bool):
        self.right_dock.setVisible(visible)
        if visible and self.width() < 1440 and self.explorer_dock.isVisible():
            self.nav_toggle.setChecked(False)

    def _open_ai_wizard(self):
        if not self.copilot_toggle.isChecked():
            self.copilot_toggle.setChecked(True)
        self.right_dock.dock_tabs.setCurrentItem("copilot")
        self.right_dock.sub_tabs.setCurrentItem("wizard")

    def resizeEvent(self, event):
        super().resizeEvent(event)
        w = self.width()
        if w < 1440 and self.right_dock.isVisible() and self.explorer_dock.isVisible():
            self.nav_toggle.setChecked(False)
        self.ai_create_btn.setText("" if w < 1150 else "Tạo AI")


if __name__ == "__main__":
    setTheme(Theme.DARK)
    app = QApplication(sys.argv)
    window = AIQATestStudioWindow()
    window.show()
    sys.exit(app.exec())
```

---

## 9. Checklist Kiểm thử UI/UX Trước Khi Release

- [ ] **Kiểm tra độ phân giải Laptop ($1280 \times 800$):** Mở đồng thời `AI Wizard` và bảng `Steps`; xác nhận không có label hay input nào đè lên nhau.
- [ ] **Kiểm tra tiêu đề dài:** Mở Test Case có tên $\ge 80$ ký tự; xác nhận tiêu đề hiển thị dấu `...` gọn gàng và hiện đầy đủ khi di chuột (hover).
- [ ] **Kiểm tra thu gọn Drawer:** Bấm nút `⌄` trên `BottomDataDrawer`; xác nhận thanh bar thu về đúng $42\text{px}$ và bảng `Steps` tự động giãn thêm chiều cao.
- [ ] **Kiểm tra chuyển đổi Theme:** Chuyển qua lại giữa `Theme.DARK` và `Theme.LIGHT`; xác nhận các Card, Viền và Badge màu vẫn giữ độ tương phản rõ nét.