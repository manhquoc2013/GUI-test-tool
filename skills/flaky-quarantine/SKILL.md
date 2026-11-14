---
name: flaky-quarantine
description: Capture failure artifacts, isolate intermittent E2E failures, and apply a quarantine policy without masking real defects.
---

# Flaky Quarantine Skill

Dùng khi case E2E đôi khi pass đôi khi fail trên cùng commit, hoặc khi CI báo instability rate vượt ngưỡng. Mục tiêu: **chứng cứ đầy đủ**, **tách flake khỏi regression**, **quarantine có kiểm soát** — không vô hạn `@Retry` che bug.

## Phase 1 — Artifact Capture (bắt buộc trước khi sửa test hoặc quarantine)

1. **Run metadata**: branch, commit SHA, env, shard, worker index, browser/version, timezone.
2. **Failure bundle** (giữ ít nhất một lần fail):
   - Stack trace + step index
   - Screenshot at failure
   - Video/trace nếu Playwright bật `trace: on-first-retry` hoặc `retain-on-failure`
   - HAR hoặc network log cho step liên quan
   - Console errors từ trang
3. **Correlation**: cùng `TEST-ID`, cùng data seed, cùng thứ tự suite; ghi pass/fail history 10–20 run gần nhất.

## Phase 2 — Isolate Root Cause Class

| Signal | Likely class | Next action |
|--------|--------------|-------------|
| Locator timeout, element detached | Timing / DOM race | Stabilize wait; assert state before action |
| Intermittent 5xx/timeout API | Env / backend flake | Mock or contract test; mark dependency |
| Pass only on retry | Order pollution | Fix isolation; unique test data |
| Fails specific shard/worker | Parallelism / shared state | Serial quarantine for investigation |
| Fails only nightly | Clock, cron, locale | Freeze time/locale in test |

Chạy **bisect**: single test file → single test → same test N lần locally và trên CI (`--repeat-each`) để xác nhận flake rate.

## Phase 3 — Quarantine Policy

Quarantine chỉ khi **đủ bằng chứng flake** và **ticket theo dõi** tồn tại.

1. **Eligibility**
   - ≥ 3 failures không có assertion/logic change trên app trong window 7 ngày, hoặc flake rate > 15% trên 20 run.
   - Không quarantine test mới chưa qua ít nhất 5 run ổn định trên main.

2. **Mechanism** (chọn một, ghi trong ticket)
   - Tag `@quarantine` / suite exclude khỏi blocking pipeline
   - Chạy ở job non-blocking nightly với repeat cao hơn
   - Tạm `test.fixme` **chỉ** kèm ticket ID và expiry date — không `fixme` vĩnh viễn im lặng

3. **SLA**
   - Max quarantine: 14 ngày (hoặc theo team policy)
   - Owner assign; weekly review: heal, delete, hoặc chuyển thành app bug

4. **Exit criteria**
   - 20 consecutive green trên main sau fix, hoặc root cause app fix verified
   - Remove tag; bật lại trong required check

## Anti-Patterns (reject)

- Tăng timeout đại trà không có artifact phân tích
- Quarantine cả file vì một case flake
- Retry vô hạn trên PR gate che regression

## Handoff

Xuất bản tóm tắt: flake rate, artifact paths, hypothesis, quarantine decision (yes/no), ticket link, expiry.