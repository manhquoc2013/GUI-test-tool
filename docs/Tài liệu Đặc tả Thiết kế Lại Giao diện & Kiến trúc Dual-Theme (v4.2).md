# Tài liệu Đặc tả Thiết kế Lại Giao diện & Kiến trúc Dual-Theme (v4.2)

**Dự án:** AI QA Test Studio (`GUI-test-tool`)  
**Công nghệ:** Python 3.10+ & `PySide6-Fluent-Widgets`  
**Phạm vi:** 3-Column Full Cutover • Dual-Theme Engine (Dark & Light) • Responsive Multi-Breakpoint  

---

## 1. Mục tiêu & Nguyên tắc Thiết kế

Bản thiết kế lại v4.2 giải quyết triệt để tình trạng chật chội, đè chữ, vỡ bố cục trên màn hình laptop và lỗi lệch màu khi chuyển đổi giữa giao diện Tối (Dark) và Sáng (Light), dựa trên 4 nguyên tắc cốt lõi:

1. **Phân tầng thị giác 3 cấp độ (3-Tier Surface Elevation):** Tách biệt không gian nền tổng thể (Canvas), cột điều hướng phụ (Side Docks) và vùng thẻ nội dung nổi (Elevated Cards) bằng độ chênh sáng và viền hairline $1\text{px}$ thay vì đường kẻ dày.
2. **Đa chế độ không điểm mù (Zero-Glitch Dual-Theme):** Mọi bề mặt tùy chỉnh, badge ngữ nghĩa, cảnh báo và chữ trong bảng đều dùng token động theo `isDarkTheme()` và tự động cập nhật tức thì qua `qconfig.themeChanged` mà không cần khởi động lại ứng dụng.
3. **Không gian thở chuẩn hóa (8pt Grid System):** Tăng khoảng đệm (`14px`–`20px`) và chiều cao dòng bảng (`42px` cho `Steps`, `36px` cho `Test Data`) để mắt quét nhanh hàng chục bước kiểm thử.
4. **Thích ứng thông minh (Responsive 3 Breakpoints):** Tự động điều phối 3 cột theo chiều rộng cửa sổ ($1440\text{px}$ / $1240\text{px}$ / $1150\text{px}$), bảo vệ vùng biên tập chính không bao giờ bị bóp nghẹt.

---

## 2. Kiến trúc Dual-Theme (Dark & Light) — Chống Lỗi Style 100%

### 2.1. Triết lý phân tầng bề mặt

* **Dark Mode (`Theme.DARK`):** Dùng nền than chì sâu (`#18191C`) kết hợp các lớp phủ trắng bán trong suốt tăng dần độ sáng (`rgba(255, 255, 255, 0.02)` $\rightarrow$ `0.03` $\rightarrow$ `0.05`) và màu chữ/badge tông sáng (mức 400).
* **Light Mode (`Theme.LIGHT`):** Tránh phủ trắng `#FFFFFF` toàn màn hình gây chói và làm phẳng ranh giới giữa các cột. Thay vào đó dùng **3 tầng sáng phân cấp**:
  * **Tầng 0 — Canvas nền (`#F3F4F6`):** Xám lạnh dịu làm nền cho `MainWindow` và vùng đệm của `EditorPane`.
  * **Tầng 1 — Side Docks (`#F8FAFC`):** Nền cho `ExplorerPane` (trái) và `RightDock` (phải), tách biệt khỏi Canvas bằng viền hairline `rgba(15, 23, 42, 0.09)`.
  * **Tầng 2 — Elevated Cards (`#FFFFFF`):** Nền trắng tinh cho khối bảng `Steps`, `BottomDataDrawer`, các thẻ nhập liệu trong `AI Wizard` và khung `Composer` của Copilot, tạo độ nổi khối sắc nét.
  * **Hiệu chỉnh tương phản màu (Contrast Shift):** Toàn bộ chữ màu và badge chuyển sang tông đậm (mức 700, ví dụ `#0369A1`, `#B45309`, `#047857`) trên nền tint mờ $11\%–13\%$ để đạt chuẩn tương phản WCAG AA ($\ge 4.5:1$).

### 2.2. Bảng Design Tokens Hoàn chỉnh

| Token Key | Dark Mode (`Theme.DARK`) | Light Mode (`Theme.LIGHT`) | Thành phần áp dụng |
| :--- | :--- | :--- | :--- |
| `bg_canvas` | `#18191C` | `#F3F4F6` | Nền `MainWindow`, `EditorPane`, `SettingsDialog` |
| `bg_topbar` | `#1E2025` | `#FFFFFF` | Thanh `UnifiedTopBar` ($48\text{px}$) và `StatusBar` ($28\text{px}$) |
| `bg_dock` | `rgba(255, 255, 255, 0.02)` | `#F8FAFC` | Nền `ExplorerPane` (trái) và `RightDock` (phải) |
| `bg_card` | `rgba(255, 255, 255, 0.03)` | `#FFFFFF` | Card `Steps`, `BottomDataDrawer`, thẻ `AI Wizard`, `Composer` |
| `bg_card_subtle` | `rgba(255, 255, 255, 0.05)` | `#F1F5F9` | Vùng phụ, nền tiêu đề nhỏ, hover nhẹ |
| `border_hairline` | `rgba(255, 255, 255, 0.08)` | `rgba(15, 23, 42, 0.09)` | Viền chia cột, viền TopBar/StatusBar, `QSplitter::handle` |
| `border_card` | `rgba(255, 255, 255, 0.11)` | `rgba(15, 23, 42, 0.12)` | Viền bao quanh Card và khung nhập Prompt |
| `text_primary` | `#F8FAFC` | `#0F172A` | Tiêu đề Test Case (`ElidedTitleLabel`), chữ chính |
| `text_secondary` | `#94A3B8` | `#475569` | Mô tả phụ, caption, nhãn trạng thái |
| `text_muted` | `#64748B` | `#64748B` | Token counter, thông tin phụ trên StatusBar |
| `warning_bg` | `rgba(245, 158, 11, 0.12)` | `rgba(245, 158, 11, 0.11)` | Nền `UnboundAlertBanner` |
| `warning_border` | `rgba(245, 158, 11, 0.32)` | `rgba(217, 119, 6, 0.35)` | Viền `UnboundAlertBanner` |
| `warning_fg` | `#FBBF24` | `#B45309` | Tiêu đề cảnh báo `UNBOUND` & badge `UNBOUND` |
| `warning_desc` | `#FDE68A` | `#78350F` | Nội dung mô tả bên trong `UnboundAlertBanner` |
| `success_fg` | `#34D399` | `#047857` | Trạng thái `MCP: Ready`, `BOUND`, `SUCCESS` |

#### Bảng màu ngữ nghĩa cho `ActionBadge`

| Action | Dark (`background` / `color`) | Light (`background` / `color`) |
| :--- | :--- | :--- |
| **`click`** | `rgba(56, 189, 248, 0.16)` / `#38BDF8` | `rgba(2, 132, 199, 0.12)` / `#0369A1` |
| **`fill`** | `rgba(52, 211, 153, 0.16)` / `#34D399` | `rgba(5, 150, 105, 0.12)` / `#047857` |
| **`select`** | `rgba(167, 139, 250, 0.16)` / `#A78BFA` | `rgba(124, 58, 237, 0.12)` / `#6D28D9` |
| **`assert`** | `rgba(251, 191, 36, 0.16)` / `#FBBF24` | `rgba(217, 119, 6, 0.13)` / `#B45309` |

### 2.3. 7 Quy tắc "Vàng" Chống Lỗi Khi Chuyển Đổi Dark $\leftrightarrow$ Light

1. **Zero-Override đối với widget gốc của `qfluentwidgets`:** Tuyệt đối **không** gán `setStyleSheet("background-color: ...")` lên `TableWidget`, `TreeWidget`, `LineEdit`, `ComboBox`, `SpinBox` hay `SegmentedWidget`. Thư viện `qfluentwidgets` tự động cập nhật màu nền, hover, selection và scrollbar của các control này khi gọi `setTheme()`. Chỉ gán QSS lên các container kiến trúc bằng `#ObjectName` (ví dụ: `#TopBar`, `#ExplorerDock`, `#RightDock`, `CardWidget#StepsCard`).
2. **Mọi QSS tùy chỉnh đều phải đi qua `ThemeStyleMixin`:** Bất kỳ widget nào gọi `setStyleSheet()` đều kế thừa `ThemeStyleMixin` và đặt toàn bộ logic QSS trong `_apply_theme_style()`. Không bao giờ viết chuỗi màu tĩnh trong `__init__`.
3. **Luôn khai báo tường minh thuộc tính `color` khi dùng QSS trên `QLabel`:** Khi đặt `setStyleSheet("font-size: 18px; font-weight: 600;")` cho `QLabel` tùy chỉnh (như `ElidedTitleLabel`), Qt có thể giữ lại màu chữ cũ sau khi đổi theme. Luôn kèm `color: {t.text_primary};` bên trong `_apply_theme_style()`.
4. **Cấm dùng `Qt.GlobalColor` hoặc `QColor` tĩnh cho ô bảng (`QTableWidgetItem`):** Màu `Qt.GlobalColor.green` (`#00FF00`) bị chói và khó đọc trên nền trắng Light Mode. Với các cột trạng thái (`Action`, `Binding`, `Expected`), hãy dùng `setCellWidget()` với widget có `ThemeStyleMixin` (`ActionBadge`, `StatusPill`), hoặc cập nhật lại `item.setForeground(QColor(t.success_fg))` bên trong `_apply_theme_style()`.
5. **Đồng bộ màu thanh kéo `QSplitter::handle`:** Nếu không style `QSplitter::handle`, thanh chia cột sẽ giữ màu xám mặc định của hệ điều hành và tạo vệt sáng/tối lạc lõng khi đổi theme. Luôn gán `QSplitter::handle { background-color: {t.border_hairline}; }`.
6. **Xóa nền trắng mặc định của `SmoothScrollArea`:** Trên macOS/Windows, `QScrollArea` và viewport bên trong thường tự tô nền trắng/xám đè lên nền Dock. Luôn đặt `scroll.setStyleSheet("QScrollArea, QWidget#qt_scrollarea_viewport { background: transparent; border: none; }")`.
7. **Ngắt kết nối `qconfig.themeChanged` an toàn bằng `WeakMethod` (`EX2`):** `qconfig.themeChanged` là tín hiệu toàn cục. Dùng `weakref.WeakMethod` kết hợp `destroyed.connect(...)` trong `ThemeStyleMixin` để tránh lỗi `RuntimeError: Internal C++ object already deleted` khi đóng dialog hoặc chạy nhiều test case trong `pytest`.

---

## 3. Kiểm toán & Giải pháp Khắc phục 6 Lỗi UX Hiện Tại

| \# | Vấn đề trên giao diện cũ | Nguyên nhân kỹ thuật | Giải pháp thiết kế v4.2 |
| :--- | :--- | :--- | :--- |
| **1** | **Đè chữ & label trong `AI Wizard` (Bước 1/3)** | Đặt form nhập liệu trong layout cố định chiều cao, thiếu vùng cuộn trên macOS. | Bọc toàn bộ nội dung Wizard trong `SmoothScrollArea` (`setWidgetResizable(True)`), tách từng trường nhập thành `CardWidget` độc lập cách nhau $12\text{px}$ + `addStretch(1)`. |
| **2** | **Banner `UNBOUND` bị cắt ngang chữ** | Gán cứng chiều cao banner trong khi đoạn mô tả tự xuống dòng khi mở Copilot. | Dùng `UnboundAlertBanner(CardWidget, ThemeStyleMixin)` tự co giãn chiều cao theo nội dung (`Preferred` size policy), căn giữa nút `Sync ngay`. |
| **3** | **Thanh công cụ Copilot bóp méo các nút pill** | Dùng `QHBoxLayout` cứng cho `Auto`, `@Case`, `@DOM`, `@Error`. | Chuyển sang `FlowLayout` của `qfluentwidgets` để các thẻ ngữ cảnh tự rớt dòng gọn gàng khi thu hẹp cột phải. |
| **4** | **Lặp tiêu đề Test Case 2 lần & tràn ngang** | Hiển thị chuỗi `TC01_... — TC01_...` bằng `QLabel` thường không rút gọn. | Dùng `BreadcrumbBar` hiển thị `Module > Suite` và `ElidedTitleLabel` hiển thị duy nhất tên Case, tự rút gọn dấu `...` kèm `Tooltip` toàn văn. |
| **5** | **Bảng `Test Data` chiếm cố định ~28% chiều cao** | Chia layout cứng khiến bảng `Steps` chỉ hiện 4–5 dòng dù chưa cần sửa dữ liệu. | Thiết kế `BottomDataDrawer` nằm trong `v_splitter` của cột giữa, có nút thu gọn 1 chạm về thanh bar $42\text{px}$. |
| **6** | **Cửa sổ `Cấu hình hệ thống` dàn trải** | Dùng 7 tab ngang dễ tràn; `SpinBox` kéo giãn hết chiều ngang dialog. | Chuyển sang bố cục **Master-Detail** (`TreeWidget` trái $175\text{px}$ + `SmoothScrollArea` / `SettingCardGroup` phải), cố định độ rộng `SpinBox` ở mức $135\text{px}$. |

---

## 4. Kiến trúc Layout 4 Vùng & State Machine Responsive

### 4.1. Sơ đồ State Machine Responsive (`M0`)

```text
[Desktop Wide: W >= 1440px]  <--->  [Laptop Band: 1150 <= W < 1440px]  <--->  [Compact: W < 1150px]
  • Hiển thị đủ 3 cột:                 • Mở Copilot -> tự ẩn Explorer             • TopBar rút gọn nhãn phụ
    Explorer (260) | Editor | Dock       • Mở Explorer khi W < 1240 -> tự ẩn Dock     ("Tạo AI" -> icon-only)
```

### 4.2. Wireframe Chi tiết Các Trạng thái (`M1` – `M7`)

#### M1 — Desktop Wide ($W \ge 1440\text{px}$) — Hiển thị đủ 3 cột

```text
+------------------------------------------------------------------------------------+
| [☰] [Tạo AI]  [ https://… Target URL ………… ] [Sync] [12 UNBOUND] [▶ Chạy] [🤖] [⚙]  |
+------------------+-----------------------------------------+-----------------------+
| Explorer (260px) | Center Workspace (Flex, min 520px)      | Right Dock (380px)    |
| [Tìm kiếm...   ] | ModuleName > SuiteName (BreadcrumbBar)  | [AI Copilot|Trình duyệt]|
| [Cases|EL|Vars ] | TC01_LongCaseTitle_elided… [+ Thêm bước]| [AI Chat | AI Wizard] |
| ▼ QuanLyNguoi... | [Spec] [Steps (12)] [YAML] [Pytest]     | +-------------------+ |
|   ▼ TS_KiemThu   | +-------------------------------------+ | | FlowLayout Pills: | |
|     ● TC01_...   | | ! Có 12 bước chưa gắn selector      | | | [Auto ▾] [@Case]  | |
|     ● TC02_...   | |   Nhập Target URL...    [Sync ngay] | | | [@DOM] [@Error]   | |
|                  | +-------------------------------------+ | +-------------------+ |
|                  | +-------------------------------------+ | +-------------------+ |
|                  | | # | Action | Intent | Data | Bind   | | | SmoothScrollArea  | |
|                  | | 1 | click  | Mở form...| —   | UNB.   | | | (Wizard Cards /   | |
|                  | | 2 | fill   | Nhập tên..| Lan | UNB.   | | |  Chat History)    | |
|                  | +-------------------------------------+ | |                   | |
|                  | +-------------------------------------+ | | +---------------+ | |
|                  | | [Test Data (3)] [Console Log]   [⌄] | | | | Multi-line    | | |
|                  | | (Thu gọn thành bar 42px khi bấm ⌄)  | | | | Prompt Input  | | |
|                  | +-------------------------------------+ | +-------------------+ |
+------------------+-----------------------------------------+-----------------------+
| Imported Legacy Project • 67 shared elements      ● MCP: Ready • PW: On | 0 / 8,000|
+------------------------------------------------------------------------------------+
```

#### M2 & M3 — Laptop Band ($1150 \le W < 1440\text{px}$) & Mutual Exclusion ($W < 1240\text{px}$)

* **Khi mở `Copilot` tại $W < 1440\text{px}$:** `ExplorerPane` tự động ẩn (`setVisible(False)`), vùng `EditorPane` chiếm toàn bộ phần còn lại bên trái `RightDock`.
* **Khi bấm `☰` mở `Explorer` tại $W < 1240\text{px}$ (`UI_THREE_COL_SAFE`):** `RightDock` tự động ẩn (`setVisible(False)`) để hai cột phụ không bao giờ cộng dồn chiều rộng làm bóp nghẹt bảng `Steps`.

#### M4 — Compact TopBar ($W < 1150\text{px}$)

* Nút `Tạo AI` chuyển sang chế độ icon-only (`setText("")`) để bảo vệ độ rộng tối thiểu cho ô nhập `Target URL`.

#### M7 — Settings Master-Detail

```text
+----------------------------------------------------------+
| Cấu hình hệ thống                                        |
+------------------+---------------------------------------+
| TreeWidget 175px | SmoothScrollArea                      |
| • Chung          | SettingCardGroup: Giới hạn Copilot    |
| • Browser        |  Summary max chars     [SpinBox 135]  |
| • AI APIs        |  Debate max rounds     [SpinBox 135]  |
| • Models         |  Loop repeat limit     [SpinBox 135]  |
| • Routing        |  Clarify timeout (ms)  [SpinBox 135]  |
| • Copilot        |  Lean tail tokens      [SpinBox 135]  |
| • MCP            |                                       |
+------------------+---------------------------------------+
|                              [Hủy]  [Lưu cấu hình]       |
+----------------------------------------------------------+
```

---

## 5. Quy tắc Hardening Kỹ thuật Qt (Binding Rules)

1. **`H1` — Tổng `minWidth` của 3 cột trong `QSplitter`:**
   * Đặt `UI_EDITOR_MIN_WIDTH = 520` để $\text{Explorer } (230) + \text{Editor } (520) + \text{Dock } (340) = 1090\text{px} < 1150\text{px}$. Tránh việc Qt ép cửa sổ phình ngang vượt màn hình laptop khi mở lại Explorer.
   * Đặt `UI_THREE_COL_SAFE = 1240` cho cơ chế loại trừ lẫn nhau (mutual exclusion) giữa `Explorer` và `RightDock`.
2. **`H2` & `EX1` — Tách quyền điều khiển `BottomDataDrawer`:**
   * `BottomDataDrawer` chỉ tự quản lý chiều cao của chính nó (`setMinimumHeight(42)` + `setMaximumHeight(42)` khi thu gọn; `setMinimumHeight(150)` + `setMaximumHeight(QWIDGETSIZE_MAX)` khi mở rộng) và phát signal `toggled(bool expanded)`.
   * `EditorPane` (chủ sở hữu `v_splitter`) lắng nghe `toggled`, lưu `_last_drawer_ratio` và gọi `v_splitter.setSizes(...)`.
3. **`H3` & `SG1` — Ghi nhớ độ rộng cột khi Toggle:**
   * Lắng nghe `h_splitter.splitterMoved`, lưu `_saved_explorer_w` và `_saved_dock_w`.
   * **Bảo vệ chống ghi đè số `0` (`SG1`):** Khi ẩn cột, `sizes()` trả về `0`. Chỉ lưu khi cột đang hiển thị và đạt kích thước tối thiểu:
     ```python
     if explorer.isVisible() and sizes[0] >= UI_EXPLORER_MIN_WIDTH:
         self._saved_explorer_w = sizes[0]
     if right_dock.isVisible() and sizes[2] >= UI_DOCK_MIN_WIDTH:
         self._saved_dock_w = sizes[2]
     ```
4. **`SG2` — Chống lặp tín hiệu vòng tròn trên `BreadcrumbBar`:**
   * Khi nạp Test Case mới từ `ExplorerPane`, bọc `breadcrumb.clear()` và `breadcrumb.addItem(...)` trong `breadcrumb.blockSignals(True)` / `False` để không kích hoạt ngược lại sự kiện chọn node trên cây thư mục.
5. **`H4`, `SG3` & `EX2` — Live Theme & Safe Disconnect:**
   * Mọi widget dùng QSS bề mặt kế thừa `ThemeStyleMixin`, gọi `self.init_live_theme()` trong `__init__`, sử dụng `weakref.WeakMethod` và ngắt kết nối ở `self.destroyed`.

---

## 6. Cấu trúc Thư mục Đề xuất cho `GUI-test-tool`

```text
src/ai_qa_test_studio/
├── constants.py                    # Breakpoints (1440, 1240, 1150), min/max sizes, row heights
└── ui/
    ├── theme.py                    # ThemeTokens (DARK_TOKENS, LIGHT_TOKENS), ThemeStyleMixin
    ├── main_window.py              # QMainWindow + h_splitter (3 cột) + StatusBar
    ├── controllers/
    │   └── layout_controller.py    # Điều phối 3 cột, H1 mutual exclusion, H3 saved widths
    └── widgets/
        ├── badges.py               # ElidedTitleLabel, ActionBadge, StatusBadge, UnboundAlertBanner
        ├── top_toolbar.py          # UnifiedTopBar (48px) + nút chuyển nhanh Dark/Light Theme
        ├── explorer_pane.py        # ExplorerPane (230–320px): Search + Cases | EL | Vars
        ├── editor_pane.py          # Breadcrumb + ElidedTitle + Steps (42px) + v_splitter
        ├── data_drawer.py          # BottomDataDrawer (collapse 42px, Test Data | Console)
        ├── right_drawer.py         # RightDock (340–450px): giữ nguyên API open/close/is_open
        ├── chat_pane.py            # FlowLayout context pills + Multi-line Composer Card
        ├── compose_wizard_panel.py # SmoothScrollArea + Field Cards (gap 12px) + addStretch(1)
        └── system_settings_dialog.py # Master-Detail (TreeWidget 175px + SettingCardGroup)
```

---

## 7. Mã nguồn Chuẩn Hóa (Dual-Theme Live Switching Preview)

Đoạn mã dưới đây tích hợp đầy đủ **Dark/Light Theme Engine** (`ThemeTokens` + `ThemeStyleMixin`), kiến trúc 3 cột và nút chuyển đổi giao diện Sáng/Tối ngay trên thanh công cụ để kiểm thử trực tiếp:

```python
import sys
import weakref
from dataclasses import dataclass
from typing import Callable

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QColor, QFontMetrics
from PySide6.QtWidgets import (
    QApplication, QDialog, QFrame, QHBoxLayout, QHeaderView,
    QLabel, QSizePolicy, QSplitter, QStackedWidget,
    QTableWidgetItem, QTreeWidgetItem, QVBoxLayout, QWidget,
    QWIDGETSIZE_MAX,
)
from qfluentwidgets import (
    BreadcrumbBar, CaptionLabel, CardWidget, ComboBox,
    FlowLayout, FluentIcon as FIF, LineEdit, PillPushButton,
    PrimaryPushButton, PushButton, SearchLineEdit, SegmentedWidget,
    SettingCard, SettingCardGroup, SmoothScrollArea, SpinBox,
    StrongBodyLabel, TableWidget, TextEdit, Theme,
    TransparentToggleToolButton, TransparentToolButton, TreeWidget,
    isDarkTheme, qconfig, setTheme,
)

# =====================================================================
# 1. CONSTANTS & BREAKPOINTS
# =====================================================================

UI_BREAKPOINT_WIDE = 1440
UI_THREE_COL_SAFE = 1240
UI_BREAKPOINT_MEDIUM = 1150

UI_EXPLORER_MIN_WIDTH = 230
UI_EXPLORER_MAX_WIDTH = 320
UI_EDITOR_MIN_WIDTH = 520
UI_DOCK_MIN_WIDTH = 340
UI_DOCK_MAX_WIDTH = 450

UI_TABLE_ROW_HEIGHT = 42
UI_DATA_ROW_HEIGHT = 36
UI_DRAWER_COLLAPSED_HEIGHT = 42
UI_DRAWER_EXPANDED_MIN = 150


# =====================================================================
# 2. DUAL-THEME TOKENS & LIVE THEME MIXIN (H4, SG3, EX2)
# =====================================================================

@dataclass(frozen=True)
class ThemeTokens:
    bg_canvas: str
    bg_topbar: str
    bg_dock: str
    bg_card: str
    bg_card_subtle: str
    border_hairline: str
    border_card: str
    text_primary: str
    text_secondary: str
    text_muted: str
    warning_bg: str
    warning_border: str
    warning_fg: str
    warning_desc: str
    success_bg: str
    success_fg: str
    action_styles: dict[str, tuple[str, str]]


DARK_TOKENS = ThemeTokens(
    bg_canvas="#18191C",
    bg_topbar="#1E2025",
    bg_dock="rgba(255, 255, 255, 0.02)",
    bg_card="rgba(255, 255, 255, 0.03)",
    bg_card_subtle="rgba(255, 255, 255, 0.05)",
    border_hairline="rgba(255, 255, 255, 0.08)",
    border_card="rgba(255, 255, 255, 0.11)",
    text_primary="#F8FAFC",
    text_secondary="#94A3B8",
    text_muted="#64748B",
    warning_bg="rgba(245, 158, 11, 0.12)",
    warning_border="rgba(245, 158, 11, 0.32)",
    warning_fg="#FBBF24",
    warning_desc="#FDE68A",
    success_bg="rgba(16, 185, 129, 0.14)",
    success_fg="#34D399",
    action_styles={
        "click": ("rgba(56, 189, 248, 0.16)", "#38BDF8"),
        "fill": ("rgba(52, 211, 153, 0.16)", "#34D399"),
        "select": ("rgba(167, 139, 250, 0.16)", "#A78BFA"),
        "assert": ("rgba(251, 191, 36, 0.16)", "#FBBF24"),
    },
)

LIGHT_TOKENS = ThemeTokens(
    bg_canvas="#F3F4F6",
    bg_topbar="#FFFFFF",
    bg_dock="#F8FAFC",
    bg_card="#FFFFFF",
    bg_card_subtle="#F1F5F9",
    border_hairline="rgba(15, 23, 42, 0.09)",
    border_card="rgba(15, 23, 42, 0.12)",
    text_primary="#0F172A",
    text_secondary="#475569",
    text_muted="#64748B",
    warning_bg="rgba(245, 158, 11, 0.11)",
    warning_border="rgba(217, 119, 6, 0.35)",
    warning_fg="#B45309",
    warning_desc="#78350F",
    success_bg="rgba(16, 185, 129, 0.13)",
    success_fg="#047857",
    action_styles={
        "click": ("rgba(2, 132, 199, 0.12)", "#0369A1"),
        "fill": ("rgba(5, 150, 105, 0.12)", "#047857"),
        "select": ("rgba(124, 58, 237, 0.12)", "#6D28D9"),
        "assert": ("rgba(217, 119, 6, 0.13)", "#B45309"),
    },
)


def get_theme_tokens() -> ThemeTokens:
    return DARK_TOKENS if isDarkTheme() else LIGHT_TOKENS


class ThemeStyleMixin:
    """Tự động cập nhật style khi đổi Dark/Light theme và ngắt kết nối an toàn khi hủy."""

    _theme_slot: Callable[..., None] | None = None

    def init_live_theme(self) -> None:
        self._apply_theme_style()
        weak_method = weakref.WeakMethod(self._apply_theme_style)

        def _on_theme_changed(*_args: object) -> None:
            callback = weak_method()
            if callback is None:
                return
            try:
                callback()
            except RuntimeError:
                pass

        self._theme_slot = _on_theme_changed
        qconfig.themeChanged.connect(_on_theme_changed)

        destroyed_sig = getattr(self, "destroyed", None)
        if destroyed_sig is not None:
            destroyed_sig.connect(self._disconnect_live_theme)

    def _disconnect_live_theme(self) -> None:
        if self._theme_slot is not None:
            try:
                qconfig.themeChanged.disconnect(self._theme_slot)
            except (RuntimeError, TypeError):
                pass
            self._theme_slot = None

    def _apply_theme_style(self) -> None:
        raise NotImplementedError


# =====================================================================
# 3. SHARED BADGES & BANNERS (ui/widgets/badges.py)
# =====================================================================

class ElidedTitleLabel(QLabel, ThemeStyleMixin):
    def __init__(self, text: str = "", parent: QWidget | None = None):
        super().__init__(text, parent)
        self._full_text = text
        self.setToolTip(text)
        self.setMinimumWidth(120)
        self.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Preferred)
        self.init_live_theme()

    def _apply_theme_style(self) -> None:
        t = get_theme_tokens()
        self.setStyleSheet(f"font-size: 18px; font-weight: 600; color: {t.text_primary};")

    def setText(self, text: str) -> None:
        self._full_text = text
        self.setToolTip(text)
        self._update_elided()

    def resizeEvent(self, event) -> None:
        super().resizeEvent(event)
        self._update_elided()

    def _update_elided(self) -> None:
        fm = QFontMetrics(self.font())
        elided = fm.elidedText(
            self._full_text, Qt.TextElideMode.ElideRight, max(120, self.width())
        )
        super().setText(elided)


class ActionBadge(QLabel, ThemeStyleMixin):
    def __init__(self, action: str, parent: QWidget | None = None):
        super().__init__(action, parent)
        self._action_key = action.lower()
        self.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.setFixedSize(64, 24)
        self.init_live_theme()

    def _apply_theme_style(self) -> None:
        t = get_theme_tokens()
        bg, fg = t.action_styles.get(self._action_key, (t.bg_card_subtle, t.text_secondary))
        self.setStyleSheet(f"""
            background-color: {bg};
            color: {fg};
            border-radius: 6px;
            font-size: 12px;
            font-weight: 600;
        """)


class StatusBadge(QLabel, ThemeStyleMixin):
    """Badge trạng thái (UNBOUND / BOUND / SUCCESS) tự đổi màu chuẩn tương phản Dark/Light."""
    def __init__(self, text: str, kind: str = "warning", parent: QWidget | None = None):
        super().__init__(text, parent)
        self._kind = kind
        self.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.setFixedHeight(24)
        self.setMinimumWidth(78)
        self.init_live_theme()

    def _apply_theme_style(self) -> None:
        t = get_theme_tokens()
        if self._kind == "success":
            bg, fg = t.success_bg, t.success_fg
        else:
            bg, fg = t.warning_bg, t.warning_fg
        self.setStyleSheet(f"""
            background-color: {bg};
            color: {fg};
            border-radius: 12px;
            padding: 0 10px;
            font-size: 11px;
            font-weight: 600;
        """)


class UnboundAlertBanner(CardWidget, ThemeStyleMixin):
    def __init__(self, count: int = 12, parent: QWidget | None = None):
        super().__init__(parent)
        row = QHBoxLayout(self)
        row.setContentsMargins(16, 12, 16, 12)
        row.setSpacing(14)

        self.icon_lbl = QLabel("!", self)
        self.icon_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.icon_lbl.setFixedSize(22, 22)
        row.addWidget(self.icon_lbl, 0, Qt.AlignmentFlag.AlignVCenter)

        col = QVBoxLayout()
        col.setSpacing(3)
        self.title_lbl = StrongBodyLabel(f"Có {count} bước chưa gắn selector (UNBOUND)", self)
        self.desc_lbl = CaptionLabel(
            "Nhập Target URL trên thanh công cụ rồi nhấn Sync để Agent tự động ánh xạ selector từ DOM.",
            self,
        )
        self.desc_lbl.setWordWrap(True)
        col.addWidget(self.title_lbl)
        col.addWidget(self.desc_lbl)
        row.addLayout(col, 1)

        self.sync_btn = PrimaryPushButton(FIF.SYNC, "Sync ngay", self)
        self.sync_btn.setFixedHeight(32)
        close_btn = TransparentToolButton(FIF.CLOSE, self)
        close_btn.setFixedSize(30, 30)
        close_btn.clicked.connect(self.hide)

        row.addWidget(self.sync_btn, 0, Qt.AlignmentFlag.AlignVCenter)
        row.addWidget(close_btn, 0, Qt.AlignmentFlag.AlignVCenter)
        self.init_live_theme()

    def _apply_theme_style(self) -> None:
        t = get_theme_tokens()
        self.setStyleSheet(f"""
            UnboundAlertBanner {{
                background-color: {t.warning_bg};
                border: 1px solid {t.warning_border};
                border-radius: 10px;
            }}
        """)
        self.icon_lbl.setStyleSheet(f"""
            background-color: {t.warning_fg};
            color: #FFFFFF;
            border-radius: 11px;
            font-weight: 700;
        """)
        self.title_lbl.setStyleSheet(f"color: {t.warning_fg}; font-weight: 600;")
        self.desc_lbl.setStyleSheet(f"color: {t.warning_desc};")


# =====================================================================
# 4. BOTTOM DATA DRAWER (EX1: Signal-driven, Theme-aware)
# =====================================================================

class BottomDataDrawer(CardWidget, ThemeStyleMixin):
    toggled = Signal(bool)

    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self.setObjectName("BottomDataDrawer")
        self._expanded = True

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
        self.init_live_theme()

    def _apply_theme_style(self) -> None:
        t = get_theme_tokens()
        self.setStyleSheet(f"""
            CardWidget#BottomDataDrawer {{
                background-color: {t.bg_card};
                border: 1px solid {t.border_card};
                border-radius: 10px;
            }}
        """)

    def _build_data_table(self) -> TableWidget:
        table = TableWidget(self)
        table.setColumnCount(3)
        table.setHorizontalHeaderLabels(["Scenario / Case", "Parameters (Tóm tắt)", "Expected"])
        table.verticalHeader().setVisible(False)
        table.verticalHeader().setDefaultSectionSize(UI_DATA_ROW_HEIGHT)

        hdr = table.horizontalHeader()
        hdr.setSectionResizeMode(0, QHeaderView.ResizeMode.Interactive)
        table.setColumnWidth(0, 210)
        hdr.setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        hdr.setSectionResizeMode(2, QHeaderView.ResizeMode.Fixed)
        table.setColumnWidth(2, 110)

        rows = [
            ("Trường hợp chuẩn (HappyPath)", "cccd: 040085999999 | diaBan: Cam Lộ – Quảng Trị | hoTen: Hồ Thị Lan", "SUCCESS"),
            ("Tạo hồ sơ Hộ gia đình", "cccd: 001060012345 | diaBan: Cam Lộ – Quảng Trị | hoTen: Đặng Văn Hùng", "SUCCESS"),
            ("Tạo hồ sơ Cộng đồng", "hoTen: Mái ấm Hồng Đức | loaiHoSo: Cộng đồng | nhomDoiTuong: Trẻ mồ côi", "SUCCESS"),
        ]
        table.setRowCount(len(rows))
        for r, (sc, param, exp) in enumerate(rows):
            table.setItem(r, 0, QTableWidgetItem(sc))
            table.setItem(r, 1, QTableWidgetItem(param))
            wrap = QWidget()
            wl = QHBoxLayout(wrap)
            wl.setContentsMargins(0, 0, 0, 0)
            wl.setAlignment(Qt.AlignmentFlag.AlignCenter)
            wl.addWidget(StatusBadge(exp, kind="success"))
            table.setCellWidget(r, 2, wrap)
        return table

    def _build_console_view(self) -> TextEdit:
        log = TextEdit(self)
        log.setReadOnly(True)
        log.setPlainText("[10:00:00] MCP Server: bundled ready • Playwright Chromium standby.\n")
        return log

    def toggle_drawer(self) -> None:
        self._expanded = not self._expanded
        self.stack.setVisible(self._expanded)
        self.add_row_btn.setVisible(self._expanded)
        self.toggle_btn.setIcon(FIF.CHEVRON_DOWN if self._expanded else FIF.CHEVRON_RIGHT)
        if self._expanded:
            self.setMinimumHeight(UI_DRAWER_EXPANDED_MIN)
            self.setMaximumHeight(QWIDGETSIZE_MAX)
        else:
            self.setMinimumHeight(UI_DRAWER_COLLAPSED_HEIGHT)
            self.setMaximumHeight(UI_DRAWER_COLLAPSED_HEIGHT)
        self.toggled.emit(self._expanded)


# =====================================================================
# 5. RIGHT COPILOT & BROWSER DOCK (Theme-aware, Zero-Overlap Wizard)
# =====================================================================

class CopilotBrowserDock(QFrame, ThemeStyleMixin):
    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self.setObjectName("RightDock")
        self.setMinimumWidth(UI_DOCK_MIN_WIDTH)
        self.setMaximumWidth(UI_DOCK_MAX_WIDTH)

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
        self.init_live_theme()

    def _apply_theme_style(self) -> None:
        t = get_theme_tokens()
        self.setStyleSheet(f"""
            QFrame#RightDock {{
                background-color: {t.bg_dock};
                border-left: 1px solid {t.border_hairline};
            }}
        """)
        for card in self._themed_cards:
            card.setStyleSheet(f"""
                CardWidget {{
                    background-color: {t.bg_card};
                    border: 1px solid {t.border_card};
                    border-radius: 10px;
                }}
            """)

    def _build_copilot_area(self) -> QWidget:
        self._themed_cards: list[CardWidget] = []
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
        self._themed_cards.append(composer)
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
        scroll.setStyleSheet(
            "QScrollArea, QWidget#qt_scrollarea_viewport { background: transparent; border: none; }"
        )

        container = QWidget()
        container.setStyleSheet("background: transparent;")
        form = QVBoxLayout(container)
        form.setContentsMargins(2, 4, 6, 4)
        form.setSpacing(12)

        def add_input_card(title: str, hint: str, widget: QWidget) -> None:
            card = CardWidget(container)
            self._themed_cards.append(card)
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
        self._themed_cards.append(card)
        v = QVBoxLayout(card)
        v.setAlignment(Qt.AlignmentFlag.AlignCenter)
        v.addWidget(StrongBodyLabel("Live Playwright Viewport & Element Picker"))
        v.addWidget(CaptionLabel("Nhấn 'Sync' hoặc 'Chạy' để hiển thị luồng trình duyệt trực tiếp."))
        return card


# =====================================================================
# 6. SETTINGS DIALOG (Master-Detail, Theme-aware)
# =====================================================================

class SettingsDialog(QDialog, ThemeStyleMixin):
    def __init__(self, parent: QWidget | None = None):
        super().__init__(parent)
        self.setObjectName("SystemSettingsDialog")
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
        scroll.setStyleSheet(
            "QScrollArea, QWidget#qt_scrollarea_viewport { background: transparent; border: none; }"
        )

        content = QWidget()
        content.setStyleSheet("background: transparent;")
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
        self.init_live_theme()

    def _apply_theme_style(self) -> None:
        t = get_theme_tokens()
        self.setStyleSheet(f"QDialog#SystemSettingsDialog {{ background-color: {t.bg_canvas}; }}")


# =====================================================================
# 7. MAIN STUDIO WINDOW (3-Column Full Cutover + Live Theme Switcher)
# =====================================================================

class AIQATestStudioWindow(QWidget, ThemeStyleMixin):
    def __init__(self):
        super().__init__()
        self.setObjectName("StudioMainWindow")
        self.setWindowTitle("AI QA Test Studio v4.2")
        self.resize(1440, 880)
        self.setMinimumSize(980, 640)

        self._saved_explorer_w = 260
        self._saved_dock_w = 380
        self._last_drawer_ratio = 0.25
        self._initial_sized = False

        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        # 1. Top Command Bar (48px)
        self.top_bar = QFrame(self)
        self.top_bar.setObjectName("TopBar")
        self.top_bar.setFixedHeight(48)
        tb = QHBoxLayout(self.top_bar)
        tb.setContentsMargins(12, 6, 12, 6)
        tb.setSpacing(10)

        self.nav_toggle = TransparentToggleToolButton(FIF.MENU, self.top_bar)
        self.nav_toggle.setChecked(True)
        self.nav_toggle.setToolTip("Ẩn/Hiện cây thư mục kịch bản")
        self.nav_toggle.toggled.connect(self._toggle_explorer)

        self.ai_create_btn = PushButton(FIF.IOT, "Tạo AI", self.top_bar)
        self.ai_create_btn.setToolTip("Mở AI Wizard tạo kịch bản tự động")
        self.ai_create_btn.clicked.connect(self._open_ai_wizard)

        self.url_bar = LineEdit(self.top_bar)
        self.url_bar.setText("https://example.com")
        self.url_bar.setPlaceholderText("Nhập Target URL ứng dụng cần kiểm thử...")

        self.sync_btn = PushButton(FIF.SYNC, "Sync (12)", self.top_bar)
        self.unbound_badge = StatusBadge("12 UNBOUND", kind="warning", parent=self.top_bar)

        self.run_btn = PrimaryPushButton(FIF.PLAY, "Chạy", self.top_bar)
        self.copilot_toggle = TransparentToggleToolButton(FIF.ROBOT, self.top_bar)
        self.copilot_toggle.setChecked(True)
        self.copilot_toggle.setToolTip("Ẩn/Hiện AI Copilot")
        self.copilot_toggle.toggled.connect(self._toggle_copilot)

        self.theme_btn = TransparentToolButton(FIF.CONSTRACT, self.top_bar)
        self.theme_btn.setToolTip("Chuyển đổi giao diện Sáng / Tối (Dark / Light)")
        self.theme_btn.clicked.connect(self._toggle_theme)

        self.settings_btn = TransparentToolButton(FIF.SETTING, self.top_bar)
        self.settings_btn.setToolTip("Cấu hình hệ thống")
        self.settings_btn.clicked.connect(lambda: SettingsDialog(self).exec())

        tb.addWidget(self.nav_toggle)
        tb.addWidget(self.ai_create_btn)
        tb.addWidget(self.url_bar, 1)
        tb.addWidget(self.sync_btn)
        tb.addWidget(self.unbound_badge)
        tb.addWidget(self.run_btn)
        tb.addWidget(self.copilot_toggle)
        tb.addWidget(self.theme_btn)
        tb.addWidget(self.settings_btn)
        root.addWidget(self.top_bar)

        # 2. Main 3-Column Splitter
        self.h_splitter = QSplitter(Qt.Orientation.Horizontal, self)
        self.h_splitter.setHandleWidth(1)
        self.h_splitter.splitterMoved.connect(self._on_h_splitter_moved)

        self.explorer_dock = self._build_explorer_dock()
        self.center_workspace = self._build_center_workspace()
        self.right_dock = CopilotBrowserDock(self)

        self.h_splitter.addWidget(self.explorer_dock)
        self.h_splitter.addWidget(self.center_workspace)
        self.h_splitter.addWidget(self.right_dock)
        root.addWidget(self.h_splitter, 1)

        # 3. Compact Status Bar (28px)
        self.status_bar = QFrame(self)
        self.status_bar.setObjectName("StatusBar")
        self.status_bar.setFixedHeight(28)
        sb = QHBoxLayout(self.status_bar)
        sb.setContentsMargins(14, 0, 14, 0)
        sb.setSpacing(12)
        sb.addWidget(CaptionLabel("Imported Legacy Project • 67 shared elements"))
        sb.addStretch(1)
        self.mcp_lbl = CaptionLabel("● MCP: Ready • PW: On")
        sb.addWidget(self.mcp_lbl)
        sb.addWidget(CaptionLabel("Skills: 7 Active"))
        sb.addWidget(CaptionLabel("Tokens: 0 / 8,000"))
        root.addWidget(self.status_bar)

        self.init_live_theme()

    def _apply_theme_style(self) -> None:
        t = get_theme_tokens()
        self.setStyleSheet(f"""
            QWidget#StudioMainWindow {{
                background-color: {t.bg_canvas};
            }}
            QFrame#TopBar {{
                background-color: {t.bg_topbar};
                border-bottom: 1px solid {t.border_hairline};
            }}
            QFrame#StatusBar {{
                background-color: {t.bg_topbar};
                border-top: 1px solid {t.border_hairline};
            }}
            QFrame#ExplorerDock {{
                background-color: {t.bg_dock};
                border-right: 1px solid {t.border_hairline};
            }}
            CardWidget#StepsCard {{
                background-color: {t.bg_card};
                border: 1px solid {t.border_card};
                border-radius: 10px;
            }}
            QSplitter::handle {{
                background-color: {t.border_hairline};
            }}
        """)
        self.mcp_lbl.setStyleSheet(f"color: {t.success_fg}; font-weight: 600;")

    def _toggle_theme(self) -> None:
        setTheme(Theme.LIGHT if isDarkTheme() else Theme.DARK)

    def showEvent(self, event) -> None:
        super().showEvent(event)
        if not self._initial_sized:
            self._initial_sized = True
            self._apply_h_splitter_sizes()

    def _build_explorer_dock(self) -> QFrame:
        dock = QFrame(self)
        dock.setObjectName("ExplorerDock")
        dock.setMinimumWidth(UI_EXPLORER_MIN_WIDTH)
        dock.setMaximumWidth(UI_EXPLORER_MAX_WIDTH)
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
        breadcrumb.blockSignals(True)
        breadcrumb.addItem("proj", "Imported Legacy Project")
        breadcrumb.addItem("mod", "QuanLyNguoiYeuThe")
        breadcrumb.addItem("suite", "TS_KiemThu_TaoHoSo")
        breadcrumb.blockSignals(False)
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

        self.v_splitter = QSplitter(Qt.Orientation.Vertical, ws)
        self.v_splitter.setHandleWidth(6)

        steps_card = CardWidget(ws)
        steps_card.setObjectName("StepsCard")
        sc_layout = QVBoxLayout(steps_card)
        sc_layout.setContentsMargins(8, 8, 8, 8)

        table = TableWidget(steps_card)
        table.setColumnCount(5)
        table.setHorizontalHeaderLabels(["#", "Action", "Intent (Mô tả hành động)", "Data / Input", "Binding"])
        table.verticalHeader().setVisible(False)
        table.verticalHeader().setDefaultSectionSize(UI_TABLE_ROW_HEIGHT)

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
            bwl.setContentsMargins(0, 0, 0, 0)
            bwl.setAlignment(Qt.AlignmentFlag.AlignCenter)
            bwl.addWidget(StatusBadge("UNBOUND", kind="warning"))
            table.setCellWidget(r, 4, bind_wrap)

        sc_layout.addWidget(table)

        self.bottom_drawer = BottomDataDrawer(ws)
        self.bottom_drawer.toggled.connect(self._on_drawer_toggled)

        self.v_splitter.addWidget(steps_card)
        self.v_splitter.addWidget(self.bottom_drawer)
        self.v_splitter.setSizes([540, 190])
        v.addWidget(self.v_splitter, 1)
        return ws

    # --- SPLITTER & RESPONSIVE ORCHESTRATOR (H1, H2, H3, SG1) ---

    def _on_h_splitter_moved(self, _pos: int, _index: int) -> None:
        sizes = self.h_splitter.sizes()
        if len(sizes) != 3:
            return
        if self.explorer_dock.isVisible() and sizes[0] >= UI_EXPLORER_MIN_WIDTH:
            self._saved_explorer_w = sizes[0]
        if self.right_dock.isVisible() and sizes[2] >= UI_DOCK_MIN_WIDTH:
            self._saved_dock_w = sizes[2]

    def _apply_h_splitter_sizes(self) -> None:
        total = max(self.width(), 980)
        exp_w = self._saved_explorer_w if self.explorer_dock.isVisible() else 0
        dock_w = self._saved_dock_w if self.right_dock.isVisible() else 0
        center_w = max(UI_EDITOR_MIN_WIDTH, total - exp_w - dock_w)
        self.h_splitter.setSizes([exp_w, center_w, dock_w])

    def _on_drawer_toggled(self, expanded: bool) -> None:
        sizes = self.v_splitter.sizes()
        total_h = sum(sizes) or 700
        if not expanded:
            if sizes[1] > UI_DRAWER_COLLAPSED_HEIGHT:
                self._last_drawer_ratio = max(0.18, min(0.5, sizes[1] / total_h))
            self.v_splitter.setSizes([total_h - UI_DRAWER_COLLAPSED_HEIGHT, UI_DRAWER_COLLAPSED_HEIGHT])
        else:
            drawer_h = max(UI_DRAWER_EXPANDED_MIN, int(total_h * self._last_drawer_ratio))
            self.v_splitter.setSizes([total_h - drawer_h, drawer_h])

    def _toggle_explorer(self, visible: bool) -> None:
        if visible and self.width() < UI_THREE_COL_SAFE and self.right_dock.isVisible():
            self.copilot_toggle.setChecked(False)
        self.explorer_dock.setVisible(visible)
        self._apply_h_splitter_sizes()

    def _toggle_copilot(self, visible: bool) -> None:
        if visible and self.width() < UI_BREAKPOINT_WIDE and self.explorer_dock.isVisible():
            self.nav_toggle.setChecked(False)
        self.right_dock.setVisible(visible)
        self._apply_h_splitter_sizes()

    def _open_ai_wizard(self) -> None:
        if not self.copilot_toggle.isChecked():
            self.copilot_toggle.setChecked(True)
        self.right_dock.dock_tabs.setCurrentItem("copilot")
        self.right_dock.sub_tabs.setCurrentItem("wizard")

    def resizeEvent(self, event) -> None:
        super().resizeEvent(event)
        w = self.width()
        if w < UI_BREAKPOINT_WIDE and self.right_dock.isVisible() and self.explorer_dock.isVisible():
            self.nav_toggle.setChecked(False)
        self.ai_create_btn.setText("" if w < UI_BREAKPOINT_MEDIUM else "Tạo AI")


if __name__ == "__main__":
    setTheme(Theme.DARK)
    app = QApplication(sys.argv)
    window = AIQATestStudioWindow()
    window.show()
    sys.exit(app.exec())
```

---

## 8. Checklist Kiểm thử UI/UX & Dual-Theme Trước Khi Release

* [ ] **Chuyển đổi Live Dark $\leftrightarrow$ Light:** Bấm chuyển đổi qua lại nhiều lần giữa `Theme.DARK` và `Theme.LIGHT` khi đang mở đồng thời `Explorer`, `Steps`, `BottomDataDrawer`, `AI Wizard` và `SettingsDialog`; xác nhận không còn vệt nền tối sót lại hay chữ mờ khó đọc.
* [ ] **Kiểm tra độ tương phản Light Mode:** Các badge `click`, `fill`, `select`, `assert`, `UNBOUND`, `SUCCESS` và `UnboundAlertBanner` hiển thị rõ nét trên nền `#F8FAFC` / `#FFFFFF`.
* [ ] **Kiểm tra vòng đời `pytest` (`EX2`):** Khởi tạo và đóng nhiều instance `AIQATestStudioWindow` liên tiếp rồi gọi `setTheme(Theme.LIGHT)` / `setTheme(Theme.DARK)` để đảm bảo không phát sinh `RuntimeError: Internal C++ object already deleted`.
* [ ] **Kiểm tra độ phân giải Laptop ($1280 \times 800$):** Mở đồng thời `AI Wizard` và bảng `Steps`; xác nhận không có label hay input nào đè lên nhau và hai cột phụ tự động loại trừ lẫn nhau dưới $1240\text{px}$.
* [ ] **Kiểm tra thu gọn Drawer (`H2`):** Bấm nút `⌄` trên `BottomDataDrawer`; xác nhận thanh bar thu về đúng $42\text{px}$ và bảng `Steps` tự động giãn thêm chiều cao.