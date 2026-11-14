"""Browser launch/sync/run/stop and viewport inspect path."""

from __future__ import annotations

from pathlib import Path
from typing import TYPE_CHECKING

from ai_qa_test_studio.browser.visual_inspector import element_from_point
from ai_qa_test_studio.constants import (
    DEFAULT_SKILLS_DIRNAME,
    DEFAULT_TARGET_URL,
    FALLBACK_BLANK_URL,
    SCREENCAST_STATUS_NAV_FAILED,
    SCREENCAST_STATUS_WAITING,
    TOKEN_CONTEXT_BUDGET,
)
from ai_qa_test_studio.reporting.html_reporter import HtmlReporter

if TYPE_CHECKING:
    from ai_qa_test_studio.ui.main_window import MainWindow


class RunController:
    def __init__(self, window: MainWindow) -> None:
        self._w = window

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
        w = self._w
        if w._busy or w.project is None:
            return
        case = w._require_case()
        if case is None:
            return
        url = w.url_edit.text().strip()
        if not url:
            url = DEFAULT_TARGET_URL
            w.url_edit.setText(url)
            w._toast_info("Sync URL", f"URL trống — dùng mặc định {url}")
        w._busy = True
        w._set_busy_chrome(True)
        w._status_caption.setText("Đang đồng bộ…")
        try:
            case.start_url = url
            updated = await w.session.sync_bind(
                w.project, case, url, log=w._append_log
            )
            w.builder.load_case(updated)
            w._merge_case(updated)
            w.storage.save(w.project)
            w.explorer.load_project(w.project)
            w._update_unbound_badge(updated)
            await self.refresh_viewport()
            endpoint = w.session.engine.cdp_http_for_mcp() if w.session.started else "n/a"
            w._set_pill(w._pill_mcp, f"MCP: Playwright ({endpoint})")
        except Exception as exc:  # noqa: BLE001
            w._append_log(f"Sync thất bại: {exc}")
            w._toast_error("Sync URL", str(exc))
        finally:
            w._busy = False
            w._set_busy_chrome(False)
            w._status_caption.setText("Sẵn sàng")

    async def run_current(self) -> None:
        w = self._w
        if w._busy or w.project is None:
            return
        case = w._require_case()
        if case is None:
            return
        w.right_drawer.show_browser()
        w.top_bar.set_copilot_checked(True)
        w._sync_drawer_geometry()
        w._busy = True
        w._set_busy_chrome(True)
        w._status_caption.setText("Đang chạy…")
        w._run_logs.clear()
        try:
            skill = w.skill_combo.currentText()
            report = await w.session.run_case(
                w.project,
                case,
                log=w._append_log,
                on_step=w._run_logs.append_step,
                skill_name=skill,
                skills_dir=Path(DEFAULT_SKILLS_DIRNAME),
            )
            w._last_report = report
            w.builder.load_case(case)
            w._merge_case(case)
            w.storage.save(w.project)
            w.explorer.load_project(w.project)
            await self.refresh_viewport()
            out = w.data_root / "exports" / f"{report.run_id}.html"
            HtmlReporter().write(report, out)
            w._append_log(f"Báo cáo HTML: {out}")
            w._set_pill(w._pill_mcp, f"MCP: Playwright ({report.cases[0].status})")
            used = w._token_budget.count(w._run_logs.to_plain_text())
            w._set_pill(
                w._pill_tokens,
                f"Tokens: {used / 1000:.1f}k / {TOKEN_CONTEXT_BUDGET / 1000:.0f}k",
            )
        except Exception as exc:  # noqa: BLE001
            w._append_log(f"Chạy thất bại: {exc}")
            w._toast_error("Run", str(exc))
        finally:
            w._busy = False
            w._set_busy_chrome(False)
            w._status_caption.setText("Sẵn sàng")

    async def stop_browser(self) -> None:
        w = self._w
        try:
            await w.session.stop(log=w._append_log)
            w.viewport.set_placeholder("")
            w._set_pill(w._pill_mcp, "MCP: Playwright (Stopped)")
        except Exception as exc:  # noqa: BLE001
            w._append_log(f"Stop thất bại: {exc}")

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
        png, width, height = await w.session.capture_png()
        w.viewport.set_frame_png(png, width, height)
