"""Browser launch/sync/run/stop and viewport inspect path."""

from __future__ import annotations

import asyncio
import uuid
from datetime import UTC, datetime
from pathlib import Path
from typing import TYPE_CHECKING

from PySide6.QtGui import QImage

from ai_qa_test_studio.agent.case_binding import case_binding_summary
from ai_qa_test_studio.browser.visual_inspector import element_from_point
from ai_qa_test_studio.constants import (
    BATCH_CANCELLED_MESSAGE,
    DEFAULT_SKILLS_DIRNAME,
    DEFAULT_TARGET_URL,
    FALLBACK_BLANK_URL,
    RUN_PARTIAL_BIND_BODY,
    RUN_PARTIAL_BIND_TITLE,
    RUN_TASK_KEY_PREFIX,
    RUN_UNBOUND_BODY,
    RUN_UNBOUND_TITLE,
    RUN_URL_EMPTY_BODY,
    RUN_URL_EMPTY_TITLE,
    RUN_URL_INVALID_BODY,
    RUN_URL_INVALID_TITLE,
    SCREENCAST_STATUS_NAV_FAILED,
    SCREENCAST_STATUS_WAITING,
    TOKEN_CONTEXT_BUDGET,
    UI_AUTH_EXPIRED_BODY,
    UI_AUTH_EXPIRED_TITLE,
    UI_EVIDENCE_LOADING,
    UI_LABEL_AUTH_SAVE,
    UI_LABEL_AUTH_SESSION,
)
from ai_qa_test_studio.core.async_task_supervisor import get_async_task_supervisor
from ai_qa_test_studio.core.auth_session import looks_like_login_url
from ai_qa_test_studio.core.enums import StepStatus
from ai_qa_test_studio.core.models import (
    CaseRunResult,
    CaseStatus,
    RunReport,
    StepAuditEntry,
    TestCase,
)
from ai_qa_test_studio.core.secret_masker import get_secret_masker
from ai_qa_test_studio.core.url_validation import is_valid_target_url
from ai_qa_test_studio.reporting.report_service import ReportService
from ai_qa_test_studio.ui.session_controller import sync_preflight_error

if TYPE_CHECKING:
    from ai_qa_test_studio.ui.main_window import MainWindow

_CANCELLED: CaseStatus = "CANCELLED"


class RunController:
    def __init__(self, window: MainWindow) -> None:
        self._w = window
        self._active_run_key: str | None = None

    def _set_run_mutation_lock(self, locked: bool) -> None:
        w = self._w
        w.explorer.set_mutation_enabled(not locked)
        if hasattr(w.top_bar, "set_batch_actions_enabled"):
            w.top_bar.set_batch_actions_enabled(not locked)

    def _run_task_key(self, case: TestCase) -> str:
        return f"{RUN_TASK_KEY_PREFIX}{case.id}"

    async def launch_browser(self) -> None:
        w = self._w
        if w._busy:
            return
        w._busy = True
        try:
            url = w.url_edit.text().strip() or DEFAULT_TARGET_URL
            w.viewport.set_placeholder(SCREENCAST_STATUS_WAITING)
            opened = await w.session.launch_url(url, log=w._append_log)
            w._set_pill(w._pill_mcp, "MCP: Playwright Ready")
            if opened == FALLBACK_BLANK_URL and url not in {"", FALLBACK_BLANK_URL}:
                w.viewport.set_placeholder(f"{SCREENCAST_STATUS_NAV_FAILED}\n«{url}»")
                w._toast_warn(
                    "URL không mở được",
                    f"Không resolve được «{url}». Đã mở trang trống — nhập URL thật rồi Launch lại.",
                )
            else:
                await self.refresh_viewport()
        except Exception as exc:  # noqa: BLE001
            w._append_log(f"Không mở được trình duyệt: {exc}")
            w._toast_error("Launch Browser", str(exc))
        finally:
            w._busy = False

    async def sync_url(self) -> None:
        """Unified Sync entry — toolbar Sync (N) and editor «Sync ngay» both land here."""
        w = self._w
        if w.project is None:
            return
        case = w._require_case()
        if case is None:
            return
        url = w.url_edit.text().strip()
        preflight = sync_preflight_error(case, url)
        if preflight is not None:
            w._toast_warn("Sync", preflight)
            return

        w.session.set_project_root(w.storage.root)
        task = w.session.register_sync(self._execute_sync(case, url), case_id=case.id)
        try:
            await task
        except asyncio.CancelledError:
            get_async_task_supervisor().emit_if_alive(w, w.sync_progress, "Sync đã hủy")
            w._append_log("Sync đã hủy")
            w.top_bar.set_sync_loading(False)
            w._busy = False
            w._set_busy_chrome(False)
            w._status_caption.setText("Sẵn sàng")

    async def auth_session(self) -> None:
        """Toggle: open headed login capture, or save storageState as «Lưu phiên»."""
        w = self._w
        w.session.set_project_root(w.storage.root)
        if w.session.auth_capture_active:
            try:
                path = await w.session.save_auth_capture(log=w._append_log)
                w.top_bar.set_auth_capturing(False)
                w._toast_info(UI_LABEL_AUTH_SAVE, f"Đã lưu {path.name}")
            except Exception as exc:  # noqa: BLE001
                w._toast_error(UI_LABEL_AUTH_SAVE, str(exc))
            return
        url = w.url_edit.text().strip() or DEFAULT_TARGET_URL
        if not is_valid_target_url(url):
            w._toast_warn(RUN_URL_INVALID_TITLE, RUN_URL_INVALID_BODY)
            return
        try:
            await w.session.start_auth_capture(url, w.storage.root, log=w._append_log)
            w.top_bar.set_auth_capturing(True)
            w._toast_info(
                UI_LABEL_AUTH_SESSION,
                "Đăng nhập trên trình duyệt vừa mở, rồi bấm «Lưu phiên».",
            )
        except Exception as exc:  # noqa: BLE001
            w.top_bar.set_auth_capturing(False)
            w._toast_error(UI_LABEL_AUTH_SESSION, str(exc))

    async def _execute_sync(self, case: TestCase, url: str) -> None:
        w = self._w
        if w.project is None:
            return
        supervisor = get_async_task_supervisor()
        w._busy = True
        w._set_busy_chrome(True)
        w.top_bar.set_sync_loading(True)
        w._status_caption.setText("Đang đồng bộ…")
        supervisor.emit_if_alive(w, w.sync_progress, "Bắt đầu Sync…")

        def _log(msg: str) -> None:
            supervisor.emit_if_alive(w, w.sync_progress, msg)

        try:
            case.start_url = url
            before_dump = [el.model_dump() for el in w.project.shared_elements]
            result = await w.session.sync_bind(w.project, case, url, log=_log)
            updated = result.case
            w._merge_case(updated)
            after_dump = [el.model_dump() for el in w.project.shared_elements]
            should_write_yaml = result.yaml_persisted or before_dump != after_dump
            if should_write_yaml:
                await asyncio.to_thread(w.storage.save, w.project)
            w.builder.load_case(updated)
            if w.project is not None:
                w.builder.set_element_lookup(
                    {el.element_id: el for el in w.project.shared_elements}
                )
            w.explorer.load_project(w.project)
            w._update_unbound_badge(updated)
            summary = case_binding_summary(updated)
            w.builder.set_deterministic_export_enabled(summary.is_fully_bound)
            if result.warnings:
                w._toast_warn("Sync — độ tin cậy", result.warnings[0])
            try:
                page_url = str(w.session.engine.page.url or "")
            except Exception:  # noqa: BLE001
                page_url = ""
            if looks_like_login_url(page_url):
                w._toast_warn(UI_AUTH_EXPIRED_TITLE, UI_AUTH_EXPIRED_BODY)
            await self.refresh_viewport()
            endpoint = w.session.engine.cdp_http_for_mcp() if w.session.started else "n/a"
            w._set_pill(w._pill_mcp, f"MCP: Playwright ({endpoint})")
            supervisor.emit_if_alive(w, w.sync_progress, "Đồng bộ xong")
        except asyncio.CancelledError:
            raise
        except Exception as exc:  # noqa: BLE001
            w._append_log(f"Sync thất bại: {exc}")
            w._toast_error("Sync URL", str(exc))
        finally:
            w.top_bar.set_sync_loading(False)
            w._busy = False
            w._set_busy_chrome(False)
            w._status_caption.setText("Sẵn sàng")

    def _validate_run_preflight(self, case: TestCase) -> bool:
        w = self._w
        url = w.url_edit.text().strip() or (case.start_url or "").strip()
        if not url:
            w._toast_warn(RUN_URL_EMPTY_TITLE, RUN_URL_EMPTY_BODY)
            return False
        if not is_valid_target_url(url):
            w._toast_warn(RUN_URL_INVALID_TITLE, RUN_URL_INVALID_BODY)
            return False
        if not case.start_url:
            case.start_url = url
        summary = case_binding_summary(case)
        sync_before = bool(
            w.project.settings.sync_before_run if w.project is not None else False
        )
        if summary.is_unbound:
            if sync_before:
                return True
            w._toast_warn(RUN_UNBOUND_TITLE, RUN_UNBOUND_BODY)
            return False
        if summary.is_partial:
            w._toast_warn(RUN_PARTIAL_BIND_TITLE, RUN_PARTIAL_BIND_BODY)
        return True

    async def run_current(self) -> None:
        w = self._w
        if w._busy or w.project is None:
            return
        case = w._require_case()
        if case is None:
            return
        if not self._validate_run_preflight(case):
            return

        summary = case_binding_summary(case)
        if summary.is_unbound and w.project.settings.sync_before_run:
            await self.sync_url()
            case = w._require_case() or case
            if case_binding_summary(case).is_unbound:
                w._toast_warn(RUN_UNBOUND_TITLE, RUN_UNBOUND_BODY)
                return

        key = self._run_task_key(case)
        self._active_run_key = key
        supervisor = get_async_task_supervisor()
        task = supervisor.register(
            self._execute_run_current(case),
            key=key,
            replace=True,
        )
        try:
            await task
        except asyncio.CancelledError:
            w._append_log("Phiên chạy đã bị hủy (Stop).")
            raise

    async def _execute_run_current(self, case: TestCase) -> None:
        w = self._w
        if w.project is None:
            return
        w.right_drawer.show_browser()
        w.top_bar.set_copilot_checked(True)
        w._sync_drawer_geometry()
        w._busy = True
        self._set_run_mutation_lock(True)
        w._set_busy_chrome(True)
        w._status_caption.setText("Đang chạy…")
        w._run_logs.clear()
        if hasattr(w.builder, "clear_step_run_status"):
            w.builder.clear_step_run_status()
        try:
            skill = w.skill_combo.currentText()
            report = await w.session.run_case(
                w.project,
                case,
                log=w._append_log,
                on_step=self._on_step_progress,
                skill_name=skill,
                skills_dir=Path(DEFAULT_SKILLS_DIRNAME),
                midscene_enabled=w.env.settings.mcp.external.midscene,
                bundled_mcp=w._bundled_mcp,
                reset_cancel=True,
                emit_report=True,
                project_root=w.storage.root,
            )
            w._last_report = report
            if hasattr(w.right_drawer, "evidence"):
                w.right_drawer.evidence.set_report(report)
                w.right_drawer.show_evidence()
            w.builder.load_case(case)
            w._merge_case(case)
            await asyncio.to_thread(w.storage.save, w.project)
            state = w.explorer.snapshot_tree_state()
            w.explorer.load_project(w.project, restore_state=state)
            await self.refresh_viewport()
            bundle = await ReportService(w.storage.root).export_all(
                w.project, report, write_excel=False
            )
            out = bundle.html_path or (bundle.evidence_dir / "report.html")
            w._append_log(f"Báo cáo HTML: {out} · evidence={bundle.evidence_dir}")
            status = report.cases[0].status if report.cases else "?"
            w._set_pill(w._pill_mcp, f"MCP: Playwright ({status})")
            used = w._token_budget.count(w._run_logs.to_plain_text())
            w._set_pill(
                w._pill_tokens,
                f"Tokens: {used / 1000:.1f}k / {TOKEN_CONTEXT_BUDGET / 1000:.0f}k",
            )
        except asyncio.CancelledError:
            w._append_log("Đã dừng dual-loop.")
            raise
        except Exception as exc:  # noqa: BLE001
            w._append_log(f"Chạy thất bại: {get_secret_masker().mask_text(str(exc))}")
            w._toast_error("Run", str(exc))
        finally:
            w._busy = False
            self._set_run_mutation_lock(False)
            w._set_busy_chrome(False)
            self._active_run_key = None
            if hasattr(w.builder, "clear_step_run_status"):
                w.builder.clear_step_run_status()
            w._status_caption.setText("Sẵn sàng")

    def _on_step_progress(
        self,
        step_no: int,
        action: str,
        status: str,
        duration_ms: float,
        message: str = "",
    ) -> None:
        w = self._w
        masked = get_secret_masker().mask_text(message)
        supervisor = get_async_task_supervisor()
        live = status if status else StepStatus.RUNNING.value
        if status in {"…", "...", ""}:
            live = StepStatus.RUNNING.value

        def _apply() -> None:
            w._run_logs.append_step(step_no, action, live, duration_ms, masked)
            if hasattr(w.builder, "set_step_run_status"):
                w.builder.set_step_run_status(step_no, live)

        # Prefer emit_if_alive when a Signal is available; otherwise apply on UI thread.
        signal = getattr(w, "step_progress", None)
        if signal is not None:
            supervisor.emit_if_alive(w, signal, step_no, action, live, duration_ms, masked)
            return
        try:
            loop = asyncio.get_running_loop()
            loop.call_soon(_apply)
        except RuntimeError:
            _apply()

    async def run_cases(self, cases: list[TestCase]) -> None:
        w = self._w
        if w._busy or w.project is None:
            return
        if not cases:
            return
        w.right_drawer.show_browser()
        w.top_bar.set_copilot_checked(True)
        w._sync_drawer_geometry()
        w._busy = True
        self._set_run_mutation_lock(True)
        w._set_busy_chrome(True)
        w._status_caption.setText(f"Đang chạy batch ({len(cases)})…")
        w._run_logs.clear()
        w.session.cancel_requested = False
        started = datetime.now(UTC).isoformat()
        run_id = str(uuid.uuid4())
        collected: list[CaseRunResult] = []
        healing: list[StepAuditEntry] = []
        skill = w.skill_combo.currentText()
        try:
            for index, case in enumerate(cases):
                if w.session.cancel_requested:
                    for pending in cases[index:]:
                        collected.append(self._cancelled_stub(pending))
                        pending.last_status = _CANCELLED
                    break
                w._status_caption.setText(f"Đang chạy {case.code} ({index + 1}/{len(cases)})…")
                sub_report = await w.session.run_case(
                    w.project,
                    case,
                    log=w._append_log,
                    on_step=self._on_step_progress,
                    skill_name=skill,
                    skills_dir=Path(DEFAULT_SKILLS_DIRNAME),
                    midscene_enabled=w.env.settings.mcp.external.midscene,
                    bundled_mcp=w._bundled_mcp,
                    reset_cancel=False,
                    emit_report=False,
                    project_root=w.storage.root,
                )
                collected.extend(sub_report.cases)
                healing.extend(sub_report.healing_audit)
                w._merge_case(case)
                w.explorer.refresh_case_status_labels()
                if w.session.cancel_requested:
                    for pending in cases[index + 1 :]:
                        collected.append(self._cancelled_stub(pending))
                        pending.last_status = _CANCELLED
                    break
            finished = datetime.now(UTC).isoformat()
            report = RunReport(
                run_id=run_id,
                project_name=w.project.name,
                started_at=started,
                finished_at=finished,
                cases=collected,
                healing_audit=healing,
            )
            w._last_report = report
            if hasattr(w.right_drawer, "evidence"):
                w.right_drawer.evidence.set_report(report)
                w.right_drawer.show_evidence()
            await asyncio.to_thread(w.storage.save, w.project)
            state = w.explorer.snapshot_tree_state()
            w.explorer.load_project(w.project, restore_state=state)
            await self.refresh_viewport()
            bundle = await ReportService(w.storage.root).export_all(
                w.project, report, write_excel=False
            )
            out = bundle.html_path or (bundle.evidence_dir / "report.html")
            w._append_log(
                f"Báo cáo batch HTML: {out} ({len(collected)} kết quả) · "
                f"evidence={bundle.evidence_dir}"
            )
            used = w._token_budget.count(w._run_logs.to_plain_text())
            w._set_pill(
                w._pill_tokens,
                f"Tokens: {used / 1000:.1f}k / {TOKEN_CONTEXT_BUDGET / 1000:.0f}k",
            )
        except Exception as exc:  # noqa: BLE001
            w._append_log(f"Batch thất bại: {exc}")
            w._toast_error("Run batch", str(exc))
        finally:
            w._busy = False
            self._set_run_mutation_lock(False)
            w._set_busy_chrome(False)
            w._status_caption.setText("Sẵn sàng")

    @staticmethod
    def _cancelled_stub(case: TestCase) -> CaseRunResult:
        return CaseRunResult(
            case_id=case.id,
            case_code=case.code,
            title=case.title,
            status=_CANCELLED,
            steps=[],
            skip_reason=BATCH_CANCELLED_MESSAGE,
        )

    async def stop_browser(self) -> None:
        w = self._w
        try:
            w.session.request_cancel()
            if self._active_run_key:
                await get_async_task_supervisor().cancel(self._active_run_key)
            await w.session.stop(log=w._append_log)
            w.viewport.set_placeholder("")
            w._set_pill(w._pill_mcp, "MCP: Playwright (Stopped)")
        except Exception as exc:  # noqa: BLE001
            w._append_log(f"Stop thất bại: {exc}")
        finally:
            w.top_bar.set_sync_loading(False)
            w._busy = False
            self._set_run_mutation_lock(False)
            w._set_busy_chrome(False)
            self._active_run_key = None
            w._status_caption.setText("Sẵn sàng")

    async def on_viewport_click(self, css_x: float, css_y: float) -> None:
        w = self._w
        if not w.session.started or w.project is None:
            w._append_log("Hãy mở trình duyệt trước khi Inspect.")
            return
        if not w.viewport.inspect_mode:
            return
        try:
            meta = await element_from_point(w.session.engine.page, css_x, css_y)
            if not meta:
                w._append_log("Không tìm thấy phần tử tại điểm click.")
                return
            case = w.builder.current_case()
            case_code = case.code if case else "CASE"
            el = w.session.pin_shared_element(w.project, meta, case_code=case_code)
            bound = w.builder.bind_element_to_selected_step(el.element_id)
            if case is not None:
                w._merge_case(case)
            w.storage.save(w.project)
            w.explorer.load_project(w.project)
            msg = f"Đã ghim {el.element_id} — {el.friendly_name}"
            if bound:
                msg += " và gắn vào bước đang chọn"
            w._append_log(msg)
            await self.refresh_viewport()
        except Exception as exc:  # noqa: BLE001
            w._append_log(f"Inspect thất bại: {exc}")

    async def refresh_viewport(self) -> None:
        w = self._w
        if not w.session.started:
            return
        w.viewport.set_placeholder(UI_EVIDENCE_LOADING)
        png, width, height = await w.session.capture_png()

        def _decode() -> QImage:
            return QImage.fromData(png)

        image = await asyncio.to_thread(_decode)
        if image.isNull():
            w.viewport.set_placeholder("Không đọc được khung hình")
            return
        # Reuse existing path — decode already off-thread; set_frame_png still validates.
        w.viewport.set_frame_png(png, width, height)
