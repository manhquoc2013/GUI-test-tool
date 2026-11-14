"""Owns the single BrowserEngine session for the UI (qasync-friendly)."""

from __future__ import annotations

import asyncio
import logging
from collections.abc import Callable, Coroutine
from pathlib import Path
from typing import Any

from ai_qa_test_studio.agent.auto_binder import (
    ShiftLeftAutoBinder,
    SyncBindResult,
    repo_from_project,
)
from ai_qa_test_studio.agent.bundled_mcp import BundledMcpRegistry
from ai_qa_test_studio.agent.case_binding import case_binding_summary
from ai_qa_test_studio.agent.dual_loop_agent import DualLoopAgent
from ai_qa_test_studio.agent.external_mcp_bridge import ExternalMcpBridge
from ai_qa_test_studio.agent.skill_loader import ProgressiveSkillLoader
from ai_qa_test_studio.agent.vision_bridge import make_vision_locate
from ai_qa_test_studio.browser.engine import BrowserEngine
from ai_qa_test_studio.constants import (
    AUTH_CAPTURE_TASK_KEY,
    DEFAULT_AUTH_PROFILE,
    DEFAULT_TARGET_URL,
    FALLBACK_BLANK_URL,
    MCP_ACTIONS_ENABLED,
    RUN_CANCEL_WAIT_TIMEOUT_S,
    UI_AUTH_EXPIRED_BODY,
    UI_VIEWPORT_HEIGHT,
    UI_VIEWPORT_WIDTH,
)
from ai_qa_test_studio.core.async_task_supervisor import get_async_task_supervisor
from ai_qa_test_studio.core.auth_session import (
    auth_state_path,
    ensure_auth_gitignore,
    looks_like_login_url,
    resolve_auth_state,
)
from ai_qa_test_studio.core.models import Project, RunReport, SharedElement, TestCase
from ai_qa_test_studio.core.url_validation import is_valid_target_url

logger = logging.getLogger(__name__)

LogFn = Callable[[str], None]
StepFn = Callable[[int, str, str, float, str], None]

SYNC_NO_BINDABLE_MSG = "Không có bước UI nào cần đồng bộ selector"
SYNC_INVALID_URL_MSG = "Target URL trống hoặc không hợp lệ — nhập http(s):// hoặc file:// trước khi Sync"


def sync_preflight_error(case: TestCase, url: str) -> str | None:
    """Return a user-facing error if Sync must not start; else None."""
    if not is_valid_target_url(url):
        return SYNC_INVALID_URL_MSG
    summary = case_binding_summary(case)
    if summary.total_bindable_steps == 0:
        return SYNC_NO_BINDABLE_MSG
    return None


class SessionController:
    def __init__(self) -> None:
        self.engine = BrowserEngine()
        self._started = False
        self.cancel_requested = False
        self._active_run: asyncio.Future[None] | None = None
        self._external_mcp = ExternalMcpBridge()
        self._supervisor = get_async_task_supervisor()
        self._auth_engine: BrowserEngine | None = None
        self._auth_path: Path | None = None
        self._project_root: Path | None = None
        self._ui_parent: Any | None = None

    def set_ui_parent(self, parent: Any | None) -> None:
        self._ui_parent = parent

    @property
    def started(self) -> bool:
        return self._started

    @property
    def run_in_progress(self) -> bool:
        return self._active_run is not None and not self._active_run.done()

    @property
    def auth_capture_active(self) -> bool:
        return self._auth_engine is not None

    def set_project_root(self, project_root: Path | None) -> None:
        self._project_root = project_root

    async def ensure_started(
        self,
        headless: bool = False,
        log: LogFn | None = None,
        *,
        project_root: Path | None = None,
        storage_state: str | Path | None = None,
    ) -> None:
        emit = log or (lambda _m: None)
        if self._started:
            return
        from ai_qa_test_studio.ui.chromium_ensure import ensure_chromium_with_dialog

        if not await ensure_chromium_with_dialog(self._ui_parent):
            raise RuntimeError("Chromium chưa sẵn sàng (đã hủy hoặc lỗi tải)")
        root = project_root if project_root is not None else self._project_root
        state = storage_state
        if state is None and root is not None:
            resolved = resolve_auth_state(root)
            state = resolved
        emit("Đang khởi động Chromium (một context + CDP)…")
        cdp = await self.engine.start(headless=headless, storage_state=state)
        page = self.engine.page
        await page.set_viewport_size({"width": UI_VIEWPORT_WIDTH, "height": UI_VIEWPORT_HEIGHT})
        self._started = True
        if state:
            emit(f"Trình duyệt sẵn sàng — CDP {cdp.http_endpoint} (storageState đã nạp)")
        else:
            emit(f"Trình duyệt sẵn sàng — CDP {cdp.http_endpoint}")

    async def _warn_if_login_redirect(self, emit: LogFn) -> bool:
        try:
            current = str(self.engine.page.url or "")
        except Exception:  # noqa: BLE001
            return False
        if looks_like_login_url(current):
            emit(f"{UI_AUTH_EXPIRED_BODY} (url={current})")
            return True
        return False

    async def launch_url(self, url: str, log: LogFn | None = None) -> str:
        """Start Chromium and navigate. On nav failure keep browser open at blank page."""
        emit = log or (lambda _m: None)
        target = (url or "").strip() or DEFAULT_TARGET_URL
        await self.ensure_started(headless=False, log=emit, project_root=self._project_root)
        try:
            await self.engine.goto(target)
            await self._warn_if_login_redirect(emit)
            emit(f"Đã mở {target}")
            return target
        except Exception as exc:  # noqa: BLE001
            emit(f"Không điều hướng được tới {target}: {exc}")
            if target != FALLBACK_BLANK_URL:
                try:
                    await self.engine.goto(FALLBACK_BLANK_URL)
                    emit(
                        f"Đã mở {FALLBACK_BLANK_URL} — sửa Target URL rồi Launch/Sync lại"
                    )
                    return FALLBACK_BLANK_URL
                except Exception as blank_exc:  # noqa: BLE001
                    emit(f"Fallback {FALLBACK_BLANK_URL} cũng thất bại: {blank_exc}")
            raise

    async def start_auth_capture(
        self,
        url: str,
        project_root: Path,
        *,
        profile_name: str = DEFAULT_AUTH_PROFILE,
        log: LogFn | None = None,
    ) -> Path:
        """Open a headed browser for manual login (SSO / OTP / CAPTCHA)."""
        emit = log or (lambda _m: None)
        if not is_valid_target_url(url):
            raise ValueError(SYNC_INVALID_URL_MSG)
        await self.abort_auth_capture(log=emit)
        from ai_qa_test_studio.ui.chromium_ensure import ensure_chromium_with_dialog

        if not await ensure_chromium_with_dialog(self._ui_parent):
            raise RuntimeError("Chromium chưa sẵn sàng (đã hủy hoặc lỗi tải)")
        ensure_auth_gitignore(project_root)
        target = auth_state_path(project_root, profile_name)
        target.parent.mkdir(parents=True, exist_ok=True)
        engine = BrowserEngine()

        async def _open() -> None:
            await engine.start(headless=False)
            await engine.goto(url)

        task = self._supervisor.register(_open(), key=AUTH_CAPTURE_TASK_KEY, replace=True)
        await task
        self._auth_engine = engine
        self._auth_path = target
        self._project_root = project_root
        emit(
            f"Đã mở trình duyệt đăng nhập ({url}) — đăng nhập xong rồi bấm «Lưu phiên»"
        )
        return target

    async def save_auth_capture(self, log: LogFn | None = None) -> Path:
        """Persist ``storage_state`` then close the capture browser via AsyncTaskSupervisor."""
        emit = log or (lambda _m: None)
        engine = self._auth_engine
        path = self._auth_path
        if engine is None or path is None:
            raise RuntimeError("Chưa mở phiên đăng nhập — bấm «Phiên đăng nhập» trước")

        async def _save_and_close() -> Path:
            saved = await engine.save_storage_state(path)
            await engine.stop()
            return saved

        task = self._supervisor.register(
            _save_and_close(),
            key=AUTH_CAPTURE_TASK_KEY,
            replace=True,
        )
        saved = await task
        self._auth_engine = None
        self._auth_path = None
        emit(f"Đã lưu phiên đăng nhập → {saved}")
        return Path(saved)

    async def abort_auth_capture(self, log: LogFn | None = None) -> None:
        emit = log or (lambda _m: None)
        engine = self._auth_engine
        self._auth_engine = None
        self._auth_path = None
        await self._supervisor.cancel(AUTH_CAPTURE_TASK_KEY)
        if engine is not None:
            try:
                await engine.stop()
            except Exception as exc:  # noqa: BLE001
                logger.warning("auth_capture_stop_failed err=%s", exc)
            emit("Đã đóng trình duyệt thu thập phiên đăng nhập")

    def request_cancel(self) -> None:
        self.cancel_requested = True

    async def stop(self, log: LogFn | None = None) -> None:
        emit = log or (lambda _m: None)
        self.cancel_requested = True
        await self.abort_auth_capture(log=emit)
        await self._supervisor.cancel_all()
        if self.run_in_progress and self._active_run is not None:
            emit("Đang chờ dual-loop dừng…")
            try:
                await asyncio.wait_for(self._active_run, timeout=RUN_CANCEL_WAIT_TIMEOUT_S)
            except TimeoutError:
                emit("Hết thời gian chờ dual-loop — tiếp tục đóng trình duyệt")
            except Exception as exc:  # noqa: BLE001
                logger.warning("wait_active_run_failed err=%s", exc)
        await self._external_mcp.stop_all()
        if self._started:
            await self.engine.stop()
            self._started = False
            emit("Đã dừng trình duyệt")
        # Leave cancel_requested sticky until the next run resets it (batch Stop-safe).

    async def capture_png(self) -> tuple[bytes, int, int]:
        page = self.engine.page
        png = await page.screenshot(type="png", full_page=False)
        viewport = page.viewport_size or {"width": UI_VIEWPORT_WIDTH, "height": UI_VIEWPORT_HEIGHT}
        return png, int(viewport["width"]), int(viewport["height"])

    def register_sync(
        self,
        coro: Coroutine[Any, Any, Any],
        *,
        case_id: str,
    ) -> asyncio.Task[Any]:
        """Register Sync under ``sync:{case_id}`` so Stop / re-Sync cancels cleanly."""
        return self._supervisor.register(
            coro,
            key=f"sync:{case_id}",
            replace=True,
        )

    async def sync_bind(
        self,
        project: Project,
        case: TestCase,
        base_url: str,
        log: LogFn | None = None,
        *,
        headless: bool = False,
    ) -> SyncBindResult:
        """Warm MCP, open Target URL, distill DOM, map UNBOUND→BOUND (dual thresholds)."""
        emit = log or (lambda _m: None)
        preflight = sync_preflight_error(case, base_url)
        if preflight is not None:
            raise ValueError(preflight)

        root = self._project_root
        await self.ensure_started(
            headless=headless,
            log=emit,
            project_root=root,
        )
        cdp = self.engine.cdp_http_for_mcp() if self._started else ""
        if cdp:
            await self._external_mcp.ensure_playwright(cdp, log=emit)

        repo = repo_from_project(project)
        binder = ShiftLeftAutoBinder(self.engine.page, repo)

        def progress(msg: str) -> None:
            emit(msg)

        result = await binder.sync_with_audit(case, base_url=base_url, on_progress=progress)
        await self._warn_if_login_redirect(emit)
        by_id = {el.element_id: el for el in project.shared_elements}
        for eid, el in repo.items():
            by_id[eid] = el
        project.shared_elements = list(by_id.values())
        bound = sum(1 for s in result.case.steps if s.binding_status == "BOUND")
        unbound = sum(1 for s in result.case.steps if s.binding_status == "UNBOUND")
        emit(f"Đồng bộ xong — BOUND={bound} UNBOUND={unbound}")
        return result

    async def run_case(
        self,
        project: Project,
        case: TestCase,
        log: LogFn | None = None,
        on_step: StepFn | None = None,
        skill_name: str = "",
        skills_dir: Path | None = None,
        midscene_enabled: bool = False,
        bundled_mcp: BundledMcpRegistry | None = None,
        *,
        reset_cancel: bool = True,
        emit_report: bool = True,
        project_root: Path | None = None,
    ) -> RunReport:
        emit = log or (lambda _m: None)
        if reset_cancel:
            self.cancel_requested = False
        loop = asyncio.get_running_loop()
        done: asyncio.Future[None] = loop.create_future()
        self._active_run = done
        _ = emit_report  # callers suppress side-effects; return value always full
        try:
            root = project_root if project_root is not None else self._project_root
            await self.ensure_started(headless=False, log=emit, project_root=root)
            skill_context = ""
            skill_loader: ProgressiveSkillLoader | None = None
            if skills_dir is not None:
                skill_loader = ProgressiveSkillLoader(skills_dir)
            if skill_name and skill_loader is not None and skill_name in skill_loader.registry:
                skill_context = skill_loader.load_skill_context(skill_name)
                emit(f"[✓] Nạp skill: {skill_name} ({len(skill_context)} chars)")
            if case.start_url:
                await self.engine.goto(case.start_url)
                emit(f"Mở start_url={case.start_url}")
                await self._warn_if_login_redirect(emit)
            repo = repo_from_project(project)
            vision_locate = None
            if bundled_mcp is not None:
                bundled_mcp.playwright.bind_page(lambda: self.engine.page)
                cdp = self.engine.cdp_http_for_mcp() if self._started else ""
                if cdp:
                    bundled_mcp.playwright.bind_cdp(cdp)
            if midscene_enabled and bundled_mcp is not None:
                cdp = self.engine.cdp_http_for_mcp() if self._started else ""
                external_ok = False
                if cdp:
                    external_ok = await self._external_mcp.ensure_midscene(cdp, log=emit)
                vision_locate = await make_vision_locate(
                    self.engine.page,
                    bundled_mcp,
                    enabled=True,
                    external=self._external_mcp,
                    cdp_endpoint=cdp,
                )
                mode = "external+bundled-fallback" if external_ok else "bundled-fallback"
                emit(f"Midscene vision locate: on ({mode})")
            from ai_qa_test_studio.reporting.evidence import EvidenceCollector

            evidence = (
                EvidenceCollector(project_root) if project_root is not None else None
            )
            agent = DualLoopAgent(
                self.engine.page,
                repo,
                vision_locate=vision_locate,
                skill_context=skill_context,
                skill_loader=skill_loader,
                evidence=evidence,
            )
            if MCP_ACTIONS_ENABLED:
                cdp_http = self.engine.cdp_http_for_mcp() if self._started else ""
                if cdp_http:
                    await self._external_mcp.ensure_playwright(cdp_http, log=emit)
                agent.mcp_actions_enabled = True
                agent.mcp_executor = self._external_mcp.client.smart_executor()
                emit("DualLoop MCP-first: SmartMCPToolExecutor attached")
            emit(f"Đang chạy dual-loop {case.code} ({len(case.steps)} bước)…")

            def _progress(
                step_no: int,
                action: str,
                status: str,
                duration_ms: float,
                message: str,
            ) -> None:
                if on_step is not None:
                    on_step(step_no, action, status, duration_ms, message)
                else:
                    emit(f"  #{step_no} {action} → {status} ({duration_ms:.0f} ms) {message}")

            report = await agent.run_case(
                case,
                project_name=project.name,
                should_cancel=lambda: self.cancel_requested,
                on_step=_progress,
            )
            result = report.cases[0]
            case.last_status = result.status
            emit(f"Hoàn tất {case.code}: {result.status}")
            return report
        finally:
            # Always release the run latch so Stop / UI busy state cannot stick
            # (normal end, cancel, or exception).
            if not done.done():
                done.set_result(None)
            self._active_run = None

    def pin_shared_element(
        self,
        project: Project,
        meta: dict[str, object],
        case_code: str = "CASE",
    ) -> SharedElement:
        tag = str(meta.get("tag") or "element")
        name = str(meta.get("name") or meta.get("id") or tag)
        css = str(meta.get("css") or "")
        safe = "".join(ch if ch.isalnum() or ch == "_" else "_" for ch in name.upper())[:40]
        element_id = f"EL_{case_code}_{tag.upper()}_{safe}".replace("__", "_")
        existing = {el.element_id for el in project.shared_elements}
        suffix = 1
        base_id = element_id
        while element_id in existing:
            suffix += 1
            element_id = f"{base_id}_{suffix}"
        el = SharedElement(
            element_id=element_id,
            page_name="Inspected",
            friendly_name=name[:80] or tag,
            role=tag,
            name=name[:80] or None,
            css=css or None,
            primary_playwright=f"page.locator({css!r})" if css else None,
        )
        project.shared_elements.append(el)
        return el
