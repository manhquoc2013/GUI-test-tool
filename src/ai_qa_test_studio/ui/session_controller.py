"""Owns the single BrowserEngine session for the UI (qasync-friendly)."""

from __future__ import annotations

import asyncio
import logging
from collections.abc import Callable
from pathlib import Path

from ai_qa_test_studio.agent.auto_binder import ShiftLeftAutoBinder, repo_from_project
from ai_qa_test_studio.agent.bundled_mcp import BundledMcpRegistry
from ai_qa_test_studio.agent.dual_loop_agent import DualLoopAgent
from ai_qa_test_studio.agent.external_mcp_bridge import ExternalMcpBridge
from ai_qa_test_studio.agent.skill_loader import ProgressiveSkillLoader
from ai_qa_test_studio.agent.vision_bridge import make_vision_locate
from ai_qa_test_studio.browser.engine import BrowserEngine
from ai_qa_test_studio.constants import (
    DEFAULT_TARGET_URL,
    FALLBACK_BLANK_URL,
    RUN_CANCEL_WAIT_TIMEOUT_S,
    UI_VIEWPORT_HEIGHT,
    UI_VIEWPORT_WIDTH,
)
from ai_qa_test_studio.core.models import Project, RunReport, SharedElement, TestCase

logger = logging.getLogger(__name__)

LogFn = Callable[[str], None]
StepFn = Callable[[int, str, str, float, str], None]


class SessionController:
    def __init__(self) -> None:
        self.engine = BrowserEngine()
        self._started = False
        self.cancel_requested = False
        self._active_run: asyncio.Future[None] | None = None
        self._external_mcp = ExternalMcpBridge()

    @property
    def started(self) -> bool:
        return self._started

    @property
    def run_in_progress(self) -> bool:
        return self._active_run is not None and not self._active_run.done()

    async def ensure_started(self, headless: bool = False, log: LogFn | None = None) -> None:
        emit = log or (lambda _m: None)
        if self._started:
            return
        emit("Đang khởi động Chromium (một context + CDP)…")
        cdp = await self.engine.start(headless=headless)
        page = self.engine.page
        await page.set_viewport_size({"width": UI_VIEWPORT_WIDTH, "height": UI_VIEWPORT_HEIGHT})
        self._started = True
        emit(f"Trình duyệt sẵn sàng — CDP {cdp.http_endpoint}")

    async def launch_url(self, url: str, log: LogFn | None = None) -> str:
        """Start Chromium and navigate. On nav failure keep browser open at blank page."""
        emit = log or (lambda _m: None)
        target = (url or "").strip() or DEFAULT_TARGET_URL
        await self.ensure_started(headless=False, log=emit)
        try:
            await self.engine.goto(target)
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

    def request_cancel(self) -> None:
        self.cancel_requested = True

    async def stop(self, log: LogFn | None = None) -> None:
        emit = log or (lambda _m: None)
        self.cancel_requested = True
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
        self.cancel_requested = False

    async def capture_png(self) -> tuple[bytes, int, int]:
        page = self.engine.page
        png = await page.screenshot(type="png", full_page=False)
        viewport = page.viewport_size or {"width": UI_VIEWPORT_WIDTH, "height": UI_VIEWPORT_HEIGHT}
        return png, int(viewport["width"]), int(viewport["height"])

    async def sync_bind(
        self,
        project: Project,
        case: TestCase,
        base_url: str,
        log: LogFn | None = None,
    ) -> TestCase:
        emit = log or (lambda _m: None)
        await self.ensure_started(headless=False, log=emit)
        repo = repo_from_project(project)
        binder = ShiftLeftAutoBinder(self.engine.page, repo)

        def progress(msg: str) -> None:
            emit(msg)

        updated = await binder.sync_test_case(case, base_url=base_url, on_progress=progress)
        by_id = {el.element_id: el for el in project.shared_elements}
        for eid, el in repo.items():
            by_id[eid] = el
        project.shared_elements = list(by_id.values())
        bound = sum(1 for s in updated.steps if s.binding_status == "BOUND")
        unbound = sum(1 for s in updated.steps if s.binding_status == "UNBOUND")
        emit(f"Đồng bộ xong — BOUND={bound} UNBOUND={unbound}")
        return updated

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
    ) -> RunReport:
        emit = log or (lambda _m: None)
        self.cancel_requested = False
        loop = asyncio.get_running_loop()
        done: asyncio.Future[None] = loop.create_future()
        self._active_run = done
        try:
            await self.ensure_started(headless=False, log=emit)
            if skill_name and skills_dir is not None:
                loader = ProgressiveSkillLoader(skills_dir)
                if skill_name in loader.registry:
                    emit(f"[✓] Nạp skill: {skill_name}")
                    _ = loader.load_skill_context(skill_name)
            if case.start_url:
                await self.engine.goto(case.start_url)
                emit(f"Mở start_url={case.start_url}")
            repo = repo_from_project(project)
            vision_locate = None
            if bundled_mcp is not None:
                bundled_mcp.playwright.bind_page(lambda: self.engine.page)
                cdp = self.engine.cdp_http_for_mcp() if self._started else ""
                if cdp:
                    bundled_mcp.playwright.bind_cdp(cdp)
            if midscene_enabled and bundled_mcp is not None:
                cdp = self.engine.cdp_http_for_mcp() if self._started else ""
                if cdp:
                    await self._external_mcp.ensure_midscene(cdp, log=emit)
                vision_locate = await make_vision_locate(
                    self.engine.page, bundled_mcp, enabled=True
                )
                emit("Midscene vision locate: on (bundled qa-midscene)")
            agent = DualLoopAgent(self.engine.page, repo, vision_locate=vision_locate)
            emit(f"Đang chạy dual-loop {case.code} ({len(case.steps)} bước)…")
            report = await agent.run_case(
                case,
                project_name=project.name,
                should_cancel=lambda: self.cancel_requested,
            )
            result = report.cases[0]
            case.last_status = result.status
            emit(f"Hoàn tất {case.code}: {result.status}")
            for step in result.steps:
                if on_step is not None:
                    on_step(
                        step.step_no,
                        step.action,
                        step.status,
                        float(step.duration_ms),
                        step.message,
                    )
                else:
                    emit(
                        f"  #{step.step_no} {step.action} → {step.status} "
                        f"({step.duration_ms} ms) {step.message}"
                    )
            return report
        finally:
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
