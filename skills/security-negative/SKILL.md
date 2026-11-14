---
name: security-negative
description: High-level negative security test design for QA—XSS, SQLi, and auth boundaries without exploit PoCs or weaponized payloads.
---

# Security Negative Testing Skill

Thiết kế **test case phủ định** cho QA: kiểm tra ứng dụng **từ chối, làm vô hại, hoặc ghi log** khi gặp input/flow bất thường. **Không** viết exploit PoC, không hướng dẫn bypass production, không dump credential thật.

## Principles

- Test trên **môi trường được phép** (staging, isolated tenant) với account test.
- Payload chỉ ở mức **minh họa an toàn** (ví dụ chuỗi có ký tự đặc biệt, script marker đã escape) — mục tiêu là **hành vi UI/API**, không RCE.
- Mỗi case gắn `TEST-ID` và requirement security / abuse story nếu có.

## Input Boundary — Injection & XSS (design level)

| Vector | Test intent | Expected outcome (assert) |
|--------|-------------|---------------------------|
| Text fields | Special chars `'"<>;&` | Stored/display escaped or rejected; no script execution in DOM |
| Search / URL param | Reflected input in page | No reflection as executable HTML; encoding consistent |
| File upload | Disallowed extension / oversized | Reject with clear error; no execution path |
| API JSON body | Type confusion (string where number) | 4xx validation; no partial persist |

**QA assertions (examples)**:
- Response không chứa unescaped user input trong `innerHTML` context (kiểm qua DOM text / API body snapshot).
- Error message generic, không lộ stack/SQL schema.
- Không tạo bản ghi khi validation fail.

## SQLi (negative — không UNION/tamper DB)

- Chỉ verify **parameterization / validation**: input `' OR '1'='1` (literal test string) không làm thay đổi kết quả trái auth hoặc trả về toàn bộ dataset.
- Assert row count / status code / empty result theo spec; không assert “đã inject thành công”.

## Authentication & Authorization Boundaries

| Scenario | Design |
|----------|--------|
| Unauthenticated access | Protected route/API → redirect 401/403 hoặc login |
| Horizontal privilege | User A token/session → resource ID của User B → deny |
| Vertical privilege | Role thấp gọi admin action → deny |
| Session expiry | Sau logout/expired token → action fail, không silent success |
| IDOR pattern | Thay đổi ID trong URL/body hợp lệ format → không leak object khác |

**Assertions**: status code, body error code, UI không hiển thị dữ liệu nhạy cảm, audit log event (nếu spec yêu cầu).

## CSRF / State (khi applicable)

- State-changing request từ context không có token/session hợp lệ → rejected.
- Không document cách forge token — chỉ mô tả **test đã có / thiếu token**.

## Output per Case

```
TEST-ID: SEC-NEG-###
Title: ...
Preconditions: env, role, data
Steps: (high level)
Negative input / action: (safe descriptor, not weaponized)
Expected: deny / sanitize / log
Risk if missing: ...
```

## Escalation

Nếu phát hiện có khả năng vulnerability thật: **dừng mở rộng exploit**, mở security ticket, attach reproduction tối thiểu theo policy nội bộ — không public chi tiết.