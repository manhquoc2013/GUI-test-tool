# Hướng dẫn sử dụng — AI QA Test Studio v4.1

> Ứng dụng desktop Python (PySide6) để soạn kịch bản ISO 29119, bind selector qua CDP, chạy Dual-Loop và làm việc với **QA Copilot** (Planner → Composer → Critic).

| Thông số | Giá trị |
|---|---|
| **Phiên bản** | **4.1** (Airy UI) |
| **Nền tảng** | Python ≥3.11 · PySide6 + Fluent Widgets · Playwright · qasync |
| **Khởi chạy** | `uv run python -m ai_qa_test_studio` |
| **Cửa sổ** | Mặc định 1200×720 · tối thiểu 800×600 |

JavaFX User Guide cũ không còn áp dụng cho runtime v4.

---

## Mục lục

1. [Khởi chạy](#1-khởi-chạy)
2. [Giao diện Airy](#2-giao-diện-airy)
3. [Explorer & Editor](#3-explorer--editor)
4. [Đồng bộ URL & Inspect](#4-đồng-bộ-url--inspect)
5. [Chạy test](#5-chạy-test)
6. [QA Copilot](#6-qa-copilot)
7. [System Settings](#7-system-settings)
8. [Xuất báo cáo](#8-xuất-báo-cáo)
9. [Phím tắt](#9-phím-tắt)
10. [FAQ](#10-faq)

---

## 1. Khởi chạy

```bash
uv sync --extra dev
uv run playwright install chromium
uv run python -m ai_qa_test_studio
```

Lần đầu app tạo / tải project dưới `data/v4/`. Có thể import legacy Java `data/project` nếu còn.

---

## 2. Giao diện Airy

Mặc định **2 cột**: Sidebar + Editor. Copilot và Trình duyệt nằm trong **drawer phải** (đóng khi mở app).

```text
Top Bar: ☰ | Tạo bằng AI | URL | Sync(N) | Chạy | Copilot | ⋯
Sidebar | Editor canvas              | [Drawer khi mở]
         Spec · Steps · YAML · Pytest | Copilot | Trình duyệt
```

- Cửa sổ hẹp (`< 960px` rộng): sidebar tự thu; drawer **overlay**.  
- Thanh tiến trình mỏng dưới Top Bar khi Sync / Run bận.  
- Status bar: MCP · Skills · Tokens.

---

## 3. Explorer & Editor

**Explorer Pivot**

| Tab | Nội dung |
|-----|----------|
| Kịch bản | Cây Group → Scenario/Suite → Case |
| Elements | Shared elements đã pin |
| Báo cáo | Placeholder danh sách (sau khi Export HTML) |

**Editor**

- Tiêu đề Subtitle: `CODE — Title`.  
- Pivot: Spec · Steps · YAML · Pytest.  
- Preconditions / Expected: thu gọn mặc định (mở bằng nút caret trên Spec).  
- Banner UNBOUND + Sync nhanh khi còn bước chưa bind.

Test Data nằm drawer dưới (toggle qua palette / logic sẵn có).

---

## 4. Đồng bộ URL & Inspect

1. Nhập Target URL trên Top Bar.  
2. **Sync (N)** — Agent bind bước UNBOUND (cần case đang chọn).  
3. Mở drawer **Trình duyệt** → **Browser** (Launch) → bật **Inspect** → click trên screencast để ghim element vào bước.

---

## 5. Chạy test

1. Chọn case trên Explorer.  
2. **Chạy** (Top Bar) — mở drawer tab Trình duyệt và chạy Dual-Loop.  
3. Nút Primary đổi thành **Dừng** khi đang chạy; `Esc` cũng dừng / đóng drawer.  
4. Feed agent (≤5 dòng gần nhất) nằm dưới screencast.

Skill đang dùng lấy từ danh sách Skills đã load (combo ẩn trên Top Bar; có thể mở rộng qua Settings / palette sau này).

---

## 6. QA Copilot

Mở bằng **Copilot**, **Tạo bằng AI**, `Ctrl+I`, hoặc Command Palette.

| Thành phần | Mô tả |
|------------|--------|
| Model combo | `Auto` (router theo task×complexity) hoặc model đã khai báo |
| `@Case` / `@DOM` / `@Error` | Gắn ngữ cảnh vào prompt |
| Chat \| AI Wizard | Chat tự do vs wizard 3 bước Compose |
| Starters 2×2 | Chỉ hiện khi phiên trống |
| Transcript | Tóm tắt crew ngắn; Copy / Undo trên từng bubble (Undo chỉ tin **user**) |
| Composer | Nhập + **Apply** (khi có artifact) + Gửi / Dừng |

Clarify: popup gợi ý + tự nhập; bỏ qua / hết giờ → dùng mặc định và ghi chú trong chat.

---

## 7. System Settings

Mở qua `⋯` → **System Settings**, hoặc Command Palette (`Ctrl+K` → “System Settings”).

- Kết nối AI (nhiều API) + nhiều model / connection.  
- Routing Auto vs Manual pin.  
- Giới hạn Copilot (debate, clarify timeout, summary chars).  
- Cờ MCP external (Playwright…) — MCP nhúng luôn sẵn khi app start.

---

## 8. Xuất báo cáo

Menu `⋯` hoặc Palette:

| Export | Kết quả |
|--------|---------|
| HTML | Visual Replay (cần đã Run) |
| Excel | IEEE 829 + RTM |
| Pytest | Scaffold project + CI |

File nằm dưới `data/exports/`.

---

## 9. Phím tắt

| Phím | Hành động |
|------|-----------|
| `Ctrl+K` | Command Palette |
| `Ctrl+\` | Toggle sidebar |
| `Ctrl+I` | Toggle Copilot drawer |
| `Ctrl+Enter` | Chạy case |
| `Ctrl+S` | Lưu project |
| `Esc` | Đóng drawer / Dừng |
| Enter (trong ô chat) | Gửi (Shift+Enter xuống dòng) |

---

## 10. FAQ

**Drawer không thấy Copilot?** Bấm Copilot trên Top Bar hoặc `Ctrl+I`. Mặc định drawer đóng để tập trung Editor.

**Không mở được URL?** App chuyển `about:blank` và hiện placeholder trên screencast — sửa URL rồi Launch lại.

**API key?** System Settings → thêm connection; không hardcode trong repo.

**Chạy test CI không GUI?** Dùng `ai-qa-report` / module `reporting/` — không import PySide6.
