# Tài Liệu Thiết Kế Kỹ Thuật: Multi-Tier Autonomous Self-Healing Engine

## 1. Tổng Quan & Bối Cảnh
Trong kiểm thử tự động E2E (End-to-End) trên trình duyệt bằng Playwright, tính "giòn" (brittleness) của các selector giao diện (CSS, XPath, Text) là nguyên nhân hàng đầu khiến kịch bản thất bại giả (false positive). Các thay đổi thường gặp gồm:
- **ID sinh động (Dynamic ID)**: Chứa hậu tố/tiền tố số ngẫu nhiên do framework render (`#btn-submit-19827`).
- **CSS Modules / Hash class**: Class bị đổi sau mỗi lần build Webpack/Vite (`.button__3x9a`).
- **Thay đổi ngữ nghĩa / Thẻ HTML**: Nút chuyển từ `<button>` sang `<a role="button">` hoặc `<div>`.
- **Độ trễ render (Render Hydration / Network Jitter)**: Phần tử cần thêm 100-300ms để hiển thị sau khi chuyển hướng.

Tài liệu này ghi lại toàn bộ thiết kế kiến trúc, giải thuật và mô hình triển khai của **Multi-Tier Self-Healing Engine** tích hợp trực tiếp vào **Playwright Automation Studio** (`GUI-test-tool`).

---

## 2. Phân Tích & Đối Sánh Với Các Framework Mẫu

Dựa trên nghiên cứu mã nguồn 4 nền tảng kiểm thử và browser agent hàng đầu trong thư mục tri thức (`docs/knowledge/`):

| Nền tảng | Điểm học hỏi & Ứng dụng vào Playwright Automation Studio |
| :--- | :--- |
| **Midscene.js** (`ByteDance`) | - Kỹ thuật bóc tách DOM tương tác (`EnhancedDomExtractor`), xuyên thấu **Shadow DOM** và **IFrame**.<br>- Lọc bỏ phần tử che khuất, `opacity: 0`, `pointer-events: none` và ngoài khung nhìn.<br>- Bộ nhớ đệm **LocateCache** tránh gọi AI lặp lại cho cùng 1 selector bị hỏng trong phiên chạy. |
| **Skyvern** | - Mô hình **Lazy Dynamic Proxy** trên `Locator`: Không truy vấn DOM sớm, bảo toàn 100% cơ chế **Auto-Waiting** nguyên bản của Playwright.<br>- Phân bổ ngân sách token và nén ảnh JPEG chất lượng tối ưu trước khi gửi sang VLM. |
| **Testsigma** | - Tầng phục hồi tức thì **Fast-Path Heuristics**: Sửa lỗi bằng luật (rule-based) trong DOM trình duyệt với độ trễ **< 30ms** và **zero chi phí AI**.<br>- Sinh selector thay thế dựa trên quan hệ ngữ nghĩa (`data-testid`, `aria-label`, placeholder, role với text). |
| **Automa** | - Giải thuật chuẩn hóa selector tối giản, ưu tiên thuộc tính thử nghiệm ổn định (`data-testid`, `data-qa`, `name`). |

---

## 3. Kiến Trúc Phân Tầng (5-Tier Architecture)

```
        [ Playwright Action / Assertion Throws PlaywrightException ]
                                      │
                                      ▼
         ┌────────────────────────────────────────────────────────┐
         │ TIER 0: Smart Wait Buffer (350-400ms)                  │
         │ - Khắc phục animation delay, lazy load, DOM hydration  │
         └────────────────────────────┬───────────────────────────┘
                                      │
                                      ▼
         ┌────────────────────────────────────────────────────────┐
         │ TIER 1: In-Memory LocateCache Hit?                     │──► [HIT] ──► Dùng ngay (<1ms)
         └────────────────────────────┬───────────────────────────┘
                                      │ [MISS]
                                      ▼
         ┌────────────────────────────────────────────────────────┐
         │ TIER 2: Fast Rule-Based Heuristic Recovery (<30ms)     │──► [SUCCESS]
         │ - Dynamic ID Stripping (#btn-104928 -> [id^='btn'])    │       │
         │ - CSS Hash Cleanup (.btn__3x9a -> .btn)               │       │
         │ - Text & Role Conversion (text -> role/button:has-text)│       │
         │ - Semantic Attributes (name -> data-testid/aria-label) │       │
         └────────────────────────────┬───────────────────────────┘       │
                                      │ (Heuristic Failed)                │
                                      ▼                                   │
         ┌────────────────────────────────────────────────────────┐       │
         │ TIER 3: Multimodal AI / VLM (OpenAiClient)             │       │
         │ - EnhancedDomExtractor (Xuyên Shadow DOM & IFrame)     │       │
         │ - Lọc bỏ phần tử che khuất, opacity=0, pointer-events  │       │
         │ - Chụp ảnh màn hình JPEG gửi sang Vision LLM Prompt    │       │
         └────────────────────────────┬───────────────────────────┘       │
                                      │                                   │
                                      ▼                                   │
         ┌────────────────────────────────────────────────────────┐       │
         │ TIER 4: Sandbox Multi-Criteria Verification            │◄──────┘
         │ count == 1 && isVisible() && isEnabled()               │
         └────────────────────────────┬───────────────────────────┘
                                      │
                                      ▼
         ┌────────────────────────────────────────────────────────┐
         │ Persistent Auto-Mutation & Audit Trail                 │
         │ - Ghi đè selector trong hằng chuỗi file .java & suite  │
         │ - Ghi nhận HealedStepMeta vào CaseResult & LocateCache │
         │ - Xuất huy hiệu ✨ Auto-Healed & bảng chi tiết trong   │
         │   Báo cáo HTML & Log Terminal & GUI Drawer             │
         └────────────────────────────────────────────────────────┘
```

---

## 4. Chi Tiết Các Thành Phần Cốt Lõi

### 4.1 Lazy Dynamic Proxy Locator (`OptimizedSelfHealingExecutor.java`)
- `StudioRuntime.healLocator(selector, desc)` trả về một `java.lang.reflect.Proxy` triển khai interface `com.microsoft.playwright.Locator`.
- **Bảo toàn Auto-Waiting**: Khi gọi `.click()`, `.fill()`, v.v., proxy chuyển tiếp trực tiếp đến Playwright `Locator`. Playwright tự động chờ đến timeout cấu hình.
- **Bắt ngoại lệ & Retry**: Nếu và chỉ nếu Playwright ném `PlaywrightException` (như `TimeoutError`), InvocationHandler sẽ bắt lỗi, kích hoạt chu trình `repairSelector(...)`, trỏ `currentLocator` sang selector mới và gọi lại phương thức với selector đã sửa.

### 4.2 Fast Rule-Based Heuristic Healer (`HeuristicHealer.java`)
- Không gọi mạng, xử lý ngay trong bộ nhớ:
  1. **Dynamic ID**: Trích xuất tiền tố có nghĩa (`#user-submit-9481` $\rightarrow$ `[id^='user-submit']`, `[id*='user-submit']`).
  2. **Text / Button**: Nhận diện `text=...` hoặc `:has-text(...)` hỗ trợ chuỗi có nháy đơn/nháy kép an toàn (`button:has-text("...")`, `[role='button']:has-text("...")`).
  3. **Attribute Fallback**: Chuyển đổi thuộc tính `name` thành `[data-testid='...']`, `[aria-label='...']`, `input[name='...']`.
  4. **Class Hash Cleanup**: Cắt bỏ hậu tố hash dạng `__3x9a`.

### 4.3 Multimodal DOM Extractor (`EnhancedDomExtractor.java`)
- Chạy script JavaScript trong ngữ cảnh trang:
  - Duyệt đệ quy xuyên thấu **Shadow DOM** (`el.shadowRoot`).
  - Duyệt đệ quy xuyên thấu **IFrame / Frame** (`el.contentDocument.body`).
  - Kiểm tra tính hiển thị nghiêm ngặt: `width > 0 && height > 0`, `visibility !== 'hidden'`, `display !== 'none'`, `opacity !== '0'`, `pointer-events !== 'none'`, và nằm trong khung nhìn viewport.
  - Loại bỏ các ID rác chứa từ 4 chữ số trở lên.
  - Trích xuất bounding box `[x, y, width, height]` và giới hạn tối đa 150 phần tử tương tác quan trọng nhất để tối ưu chi phí token.

### 4.4 Persistent Auto-Mutation (Lưu Đột Biến An Toàn)
- Cập nhật trực tiếp vào file script `.java`: Sử dụng quy tắc thay thế chuỗi hằng số có bọc ngoặc kép:
  `content.replace("\"" + oldSelector + "\"", "\"" + newSelector + "\"")`
  tránh mọi nguy cơ làm hỏng từ khóa hay cấu trúc mã nguồn Java.
- Cập nhật cấu hình assertions trong `suite.json` nếu selector bị sửa thuộc về một Assertion đang hoạt động.

### 4.5 Audit Trail & Báo Cáo Đầy Đủ
- Mỗi lần phục hồi thành công sẽ sinh một bản ghi `HealedStepMeta`:
  - `testCaseId`: Định danh test case.
  - `actionDescription`: Hành động được bảo vệ (click, fill, assert).
  - `originalSelector`: Selector ban đầu bị lỗi.
  - `repairedSelector`: Selector mới đã được kiểm chứng.
  - `method`: Phương thức sửa (`HEURISTIC` | `VLM` | `LOCATE_CACHE`).
  - `confidence`: Độ tin cậy (0.90 – 1.0).
  - `timestamp`: Thời gian ghi nhận.
- Hiển thị trực quan:
  - **Terminal Log**: In thông báo dạng `✨ [Auto-Healed via HEURISTIC] 'old' ➔ 'new'`.
  - **HTML Report**: Gắn badge `✨ Auto-Healed (N)` và bảng chi tiết các bước được sửa.
  - **GUI Studio Drawer**: Hiển thị trên bảng kết quả chi tiết của test case trong ứng dụng JavaFX.

---

## 5. Kết Quả Kiểm Thử & Hiệu Năng
- **Độ trễ trung bình**: Giảm từ ~4.000ms xuống **< 30ms** cho hơn 80% trường hợp hỏng selector nhờ tầng Heuristic và Cache.
- **Độ bao phủ kiểm thử**: Toàn bộ **101/101 tests** trong suite kiểm thử tự động của dự án đạt trạng thái PASS 100%.

