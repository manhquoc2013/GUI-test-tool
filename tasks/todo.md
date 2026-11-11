# Todo — Fix toàn bộ findings của review hệ thống

Mỗi mục phải xanh (test + compile) trước khi sang mục kế tiếp.
Không tạo nhánh, không commit (theo quy ước dự án).

## Nhóm A — Toàn vẹn dữ liệu & bảo mật

- [x] A1. `ProjectStore`: chặn path traversal cho tên group/suite (create/rename/delete)
- [x] A2. `ProjectStore`: `createCase`/`deleteCase` không để lại trạng thái nửa vời
- [x] A3. `SettingsStore`: ghi atomic + cách ly file hỏng + log/toast
- [x] A4. `ApiKeyCipher`: fail-closed khi mã hoá lỗi
- [x] A5. `HtmlReportService`: escape `status`/`priority` + whitelist enum
- [x] A6. `ReportStore`: try/catch từng report + reconcile DB ⇄ disk

## Nhóm B — Runtime & self-healing

- [x] B1. `OptimizedSelfHealingExecutor`: re-read `suite.json` khi persist + reset counter theo run
- [x] B2. `TestRunnerService`: cache settings/suite theo run (hết I/O storm per-case)

## Nhóm C — Threading & UI

- [x] C1. `AiChatSession`: message list an toàn đa luồng
- [x] C2. `AiAssistantPageController`: guard nút Xác nhận clarification + lỗi lưu chat
- [x] C3. `RunProgressService` listener: gỡ khi đóng tab
- [x] C4. `SuiteWorkspacePageController`: filter không xoá editor; bỏ interrupt trên FX thread; xoá assert theo identity
- [x] C5. `ViewNavigator`/`TabManager`: tab Reports luôn hiển thị controller mới

## Nhóm D — Đúng đắn AI

- [x] D1. `AiTestGeneratorService`: `Thread.sleep`/`close()` không phá control flow
- [x] D2. `AiCoverageAnalyzer`: match theo token, coverage chỉ tính case traceable
- [x] D3. `AiContinuation`: ghép mảnh trong cửa sổ giới hạn (hết O(n²))
- [x] D4. `AiMultiAgentCoordinator`: `testId` khớp tên sau rename
- [x] D5. `AiProjectAdvisorService`: đọc script qua `scriptFile`; điền `recommendedFix`
- [x] D6. `LocalDataIndexService`: khoá group theo path (hết đè tên trùng)

## Nhóm E — Dọn dẹp

- [x] E1. Xoá dead code đã xác minh
- [x] E2. `AiTokenOptimizer`: summary không bỏ sót snapshot sau lượt 6
- [x] E3. Đồng bộ `docs/ARCHITECTURE.md` (self-healing tiers thật, H2 2.2.224 + schema)
- [x] E4. Bỏ track `docs/intel/_intake` + `.gradle` lockfile, cập nhật `.gitignore`

## Cổng cuối

- [x] `mvn -o test` xanh toàn bộ — 411 tests, 0 failures, 0 errors
- [x] `mvn clean compile` 0 warning / 0 deprecation
- [x] Cập nhật `tasks/learnings.md`

## Ghi chú kiểm chứng

- Regression test mới: `AiTestGeneratorServiceTest.testValidateAndFixScriptKeepsUnrelatedCloseCalls`
  (chỉ strip `close()` của handle Playwright, không đụng `out.close()`).
- `OptimizedSelfHealingExecutor.persistSuiteMutation` nay khai báo `throws IOException`;
  `persistMutation` bắt và ghi log, không burn mutation budget khi ghi đĩa thất bại.
