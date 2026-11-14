---
name: a11y-audit
description: WCAG-oriented accessibility checks on live DOM or Playwright—roles, names, contrast notes, and keyboard flows.
---

# A11y Audit Skill

Dùng skill này khi cần đánh giá khả năng tiếp cận (a11y) trên trang đang chạy hoặc trong suite Playwright, bám hướng WCAG 2.x (ưu tiên Level A/AA cho smoke audit).

## When to Use

- Sau khi UI mới merge, trước release, hoặc khi functional E2E pass nhưng nghi ngờ screen reader / keyboard gaps.
- Kết hợp snapshot DOM (browser MCP, Playwright `page.accessibility.snapshot()` nếu có) và quan sát thủ công trên viewport chính.

## Audit Workflow

1. **Scope & entry points**
   - Liệt kê route/modal/dialog trong phạm vi audit.
   - Ghi `TEST-ID` hoặc requirement ref nếu audit gắn với case cụ thể.

2. **Semantic structure (WCAG 1.x / 4.1)**
   - Mỗi màn hình có đúng một `h1` (hoặc logic heading hierarchy không nhảy cấp).
   - Landmark: `main`, `nav`, `header`, `footer` khi phù hợp; không lạm dụng `div` cho control tương tác.
   - Form: mỗi input có label liên kết (`for`/`id` hoặc `aria-labelledby`); nhóm field dùng `fieldset`/`legend` khi cần.

3. **Roles, names, states (WCAG 4.1.2)**
   - Interactive elements: ưu tiên native (`button`, `a`, `input`); custom control phải có role + accessible name.
   - Playwright: `getByRole` với `name` ổn định; cờ `aria-expanded`, `aria-selected`, `aria-disabled` khớp UI.
   - Icon-only control: bắt buộc `aria-label` hoặc text ẩn cho screen reader.
   - Dynamic region: toast/alert dùng `role="status"` hoặc `alert` phù hợp; không spam live region.

4. **Keyboard & focus (WCAG 2.1.1 / 2.4.3 / 2.4.7)**
   - Tab order hợp lý, không trap focus (trừ modal có pattern đúng).
   - Mọi hành động chuột có tương đương keyboard (Enter/Space trên button, Escape đóng dialog nếu spec yêu cầu).
   - Focus visible trên control đang active; sau đóng modal focus trả về trigger.

5. **Contrast & visual notes (WCAG 1.4.x — ghi chú, không thay thế tool chuyên dụng)**
   - Ghi suspected failures: text nhỏ trên nền xám nhạt, placeholder-only label, state chỉ báo bằng màu.
   - Đề xuất đo bằng axe DevTools / Lighthouse / design token review; không hardcode hex trong case.

6. **Media & motion (WCAG 1.4.2 / 2.3.1)**
   - Video/audio có control; animation lặp > 5s có cơ chế pause nếu product yêu cầu.

## Playwright-Oriented Checks

- Assert presence: `await expect(page.getByRole('heading', { level: 1 })).toBeVisible()` khi spec có tiêu đề.
- Tab simulation: `page.keyboard.press('Tab')` theo checklist focus order trên flow chính.
- Không dùng selector chỉ dựa CSS brittle cho a11y verdict; kết luận dựa trên name/role/state.

## Output Format

| Area | Finding | WCAG hint | Severity | TEST-ID |
|------|---------|-----------|----------|---------|
| Forms | Missing label on search field | 1.3.1, 4.1.2 | High | TC-xxx |

Kết thúc bằng: **pass / conditional pass / fail** cho phạm vi audit và danh sách case follow-up (functional hoặc auto) nếu cần.