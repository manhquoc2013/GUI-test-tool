# Todo — Redesign Toàn Bộ Giao Diện Test Studio

## Nhóm 1: Core Dependencies & AtlantaFX Theme Service
- [x] T1.1. Thêm `atlantafx-base:2.0.1`, `ikonli-javafx:12.3.1`, `ikonli-feather-pack:12.3.1` vào `pom.xml`
- [x] T1.2. Cập nhật `ThemeService.java` áp dụng `PrimerDark`, `PrimerLight` từ AtlantaFX
- [x] T1.3. Nâng cấp `UiIcons.java` hỗ trợ vector icon (SVGPath) + bộ icon GRID/CODE/DRAG
- [x] T1.4. `studio.css` / `studio-light.css` đồng bộ palette token cho theme Sáng/Tối

## Nhóm 2: Khung vỏ Hệ Thống (App Shell Frame)
- [x] T2.1. `main_shell.fxml`: Activity Rail 48px, HBox{Rail+Sidebar}, handler methods
- [x] T2.2. `MainShellController.java` điều khiển Tab Strip, Status Bar, Rail buttons
- [x] T2.3. Footer Status Bar: `engineLabel`, `healedCountLabel`, `testStatsLabel` + `StatusBarService`
- [x] T2.4. Rail buttons dùng vector icon (bỏ emoji 🗂 📊 ✨ ⚙)

## Nhóm 3: No-Code Step Builder & Dual View
- [x] T3.1. `StepCardView.java` — card bước, badge action, icon vector, toggle bật/tắt
- [x] T3.2. `PlaywrightCodeArea.java` — editor native + line gutter đồng bộ scroll
- [x] T3.3. `SuiteWorkspacePageController.java` — 3-way toggle (No-Code / Code / Split)
- [x] T3.4. `StepScriptBidirectionalSync.java` — chuyển đổi 2 chiều step ⇄ Playwright code
- [x] T3.5. Kéo-thả đổi thứ tự bước (`VisualStepFlowPane.moveCard`) + đánh số lại
- [x] T3.6. Nút "Sao chép lệnh Playwright" cho từng bước (thay nút chạy đơn chưa có backend)
- [x] T3.7. Sửa lỗi Split View: reparent node thay vì add trùng vào StackPane + SplitPane
- [x] T3.8. Bước bị tắt vẫn được lưu (`TestStep.enabled`) nhưng không sinh vào script

## Nhóm 4: Explorer Page & AI Copilot Drawer
- [x] T4.1. `explorer_page.fxml` — Segmented filter chips, hover quick actions
- [x] T4.2. AI Copilot Right Drawer: sửa selector từ DOM + sinh assertion, áp dụng 1 click
- [x] T4.3. `AiCopilotService` — prompt builder/normalizer thuần Java, có unit test

## Nhóm 5: Reports Dashboard & Evidence Visualizer
- [x] T5.1. `reports_page.fxml` — 4 KPI Summary Cards (styleClass `kpi-value`)
- [x] T5.2. `report_detail_page.fxml` — Step Execution Timeline với badge PASS/FAIL/HEALED
- [x] T5.3. `EvidenceViewerDialog.java` — zoom/pan controls

## Nhóm 6: Chất lượng & Dọn dẹp
- [x] T6.1. Bỏ inline style/magic color trong `StepCardView`, `PlaywrightCodeArea`, KPI cards
- [x] T6.2. Bỏ CSS RichTextFX không còn dùng; thêm CSS cho step badge, KPI, drawer
- [x] T6.3. Bỏ property `richtextfx.version` không dùng trong `pom.xml`
- [x] T6.4. Thay `catch (Exception ignored) {}` bằng xử lý lỗi có log
- [x] T6.5. Escape/validate dữ liệu sinh code (`DELAY` chỉ nhận số, clamp 300000ms)

## Cổng cuối
- [x] C1. `mvn -o clean test` xanh toàn bộ — **573 tests, 0 failures, 0 errors**
- [x] C2. `mvn -o clean compile -DskipTests` — **0 error**, không có warning javac
- [x] C3. Cập nhật `tasks/learnings.md` + `.ai-context.md`

## Nhóm 7: No-Code Builder Usability (Iteration 2)
- [x] T7.1. Thêm icon `CURSOR`, `MOUSE`, `TIMER`, `CHEVRON_DOWN` vào `UiIcons`
- [x] T7.2. `StepAction` enum: mã lưu model → nhãn tiếng Việt + icon + field cần dùng + summary
- [x] T7.3. `StepCardView`: card thu gọn 1 dòng (accordion), body chỉ render field cần thiết, overflow menu copy/xoá
- [x] T7.4. `StepCardView`: xác nhận trước khi xoá bước có nội dung; bật/tắt nhanh vẫn ngoài menu
- [x] T7.5. `VisualStepFlowPane`: empty state mới + CTA, tự mở rộng & focus field khi thêm bước, API selected cho card
- [x] T7.6. `SuiteWorkspacePageController`: header responsive (nút view mode → icon), caption chỉ hiện khi đủ chỗ
- [x] T7.7. `SuiteWorkspacePageController`: tự ẩn panel Asserts khi hẹp + nút toggle trên header + min width 2 panel
- [x] T7.8. CSS cả 2 theme cho card mới, chip hành động, body field, empty state
- [x] T7.9. Test: `StepActionTest`, `StepCardViewUxTest`, `WorkspaceResponsiveLayoutTest`, cập nhật `StepBuilderLayoutTest`
- [x] T7.10. Cổng chất lượng: `mvn -o test` xanh (**619 tests**), `javac -Xlint:all` 0 warning

## Nhóm 8: Vòng review 3 — script & báo cáo (Review follow-ups)
- [x] T8.1. Runner: file chỉ có ghi chú (không có `class`) → `SKIPPED` "Script chưa có mã thực thi", không còn là lỗi biên dịch (`ScriptHarnessTransformer.declaresClass`)
- [x] T8.2. Runner: hằng số lý do skip tập trung (`SKIP_NO_SCRIPT`, `SKIP_NOT_A_SCRIPT`) thay vì chuỗi rải rác
- [x] T8.3. Store: `ProjectStore.scriptFileName` — hai case có tên sanitize trùng nhau không còn chung một file (hậu tố `_<caseId>`), dùng thống nhất ở create/save/read/collect/rename/copy/AI-apply/codegen/heal/Explorer/Advisor
- [x] T8.4. Store: case có `scriptFile` rỗng không còn đọc nhầm script của case bên cạnh (đọc đi qua cùng một phép suy tên)
- [x] T8.5. Test end-to-end không cần trình duyệt: suite chỉ có case chưa có script/ghi chú → 0 đạt, 0 thất bại, 2 bỏ qua (`TestRunnerSkipsScriptlessCaseTest`)
- [x] T8.6. Export: run toàn bỏ qua vẫn ra đủ dòng + lý do ở Excel (sheet Cases) và HTML (`SkippedOnlyRunExportTest`)
- [x] T8.7. Controller: tách luật "editor mở bằng gì" ra `CaseScriptEditing.openText` (có test cho 3 trạng thái + startUrl + step bị tắt)
- [x] T8.8. CSS: `-fx-prompt-text-fill: -color-fg-muted` cho TOÀN BỘ prompt (7 literal ở dark, 4 ở light); test chặn literal + đo tương phản WCAG ở cả 2 theme (`CodeEditorPromptStyleTest`)
- [x] T8.9. Cổng chất lượng: `mvn -o clean test` xanh (**729 tests**), `-Xlint:all` 0 warning, tự review lại diff
- [x] T8.10. Rename/copy case: không còn `move`/`copy` đè lên file của case khác (`CaseScriptNamingTest`, đã kiểm chứng ngược bằng mutation)
