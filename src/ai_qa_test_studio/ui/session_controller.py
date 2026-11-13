"""Owns the single BrowserEngine session for the UI (qasync-friendly)."""

from __future__ import annotations

import logging
from collections.abc import Callable

from ai_qa_test_studio.agent.auto_binder import ShiftLeftAutoBinder, repo_from_project
from ai_qa_test_studio.agent.dual_loop_agent import DualLoopAgent
from ai_qa_test_studio.browser.engine import BrowserEngine
from ai_qa_test_studio.core.models import Project, RunReport, TestCase

logger = logging.getLogger(__name__)

LogFn = Callable[[str], None]


class SessionController:
    def __init__(self) -> None:
        self.engine = BrowserEngine()
        self._started = False
        self.cancel_requested = False

    @property
    def started(self) -> bool:
        return self._started

    async def ensure_started(self, headless: bool = False, log: LogFn | None = None) -> None:
        emit = log or (lambda _m: None)
        if self._started:
            return
        emit("Starting Chromium (single context + CDP)…")
        cdp = await self.engine.start(headless=headless)
        self._started = True
        emit(f"Browser ready — CDP {cdp.http_endpoint}")

    async def stop(self, log: LogFn | None = None) -> None:
        emit = log or (lambda _m: None)
        self.cancel_requested = True
        if self._started:
            await self.engine.stop()
            self._started = False
            emit("Browser stopped")
        self.cancel_requested = False

    async def capture_png(self) -> tuple[bytes, int, int]:
        page = self.engine.page
        png = await page.screenshot(type="png", full_page=False)
        viewport = page.viewport_size or {"width": 1280, "height": 720}
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
        # Persist healed/bound locators back into project shared elements
        by_id = {el.element_id: el for el in project.shared_elements}
        for eid, el in repo.items():
            by_id[eid] = el
        project.shared_elements = list(by_id.values())
        emit(
            "Sync done — "
            f"BOUND={sum(1 for s in updated.steps if s.binding_status == 'BOUND')} "
            f"UNBOUND={sum(1 for s in updated.steps if s.binding_status == 'UNBOUND')}"
        )
        return updated

    async def run_case(
        self,
        project: Project,
        case: TestCase,
        log: LogFn | None = None,
    ) -> RunReport:
        emit = log or (lambda _m: None)
        self.cancel_requested = False
        await self.ensure_started(headless=False, log=emit)
        if case.start_url:
            await self.engine.goto(case.start_url)
            emit(f"Opened start_url={case.start_url}")
        repo = repo_from_project(project)
        agent = DualLoopAgent(self.engine.page, repo)
        emit(f"Dual-loop running {case.code} ({len(case.steps)} steps)…")
        report = await agent.run_case(case, project_name=project.name)
        result = report.cases[0]
        case.last_status = result.status
        emit(f"Finished {case.code}: {result.status}")
        for step in result.steps:
            emit(f"  #{step.step_no} {step.action} → {step.status} ({step.duration_ms} ms) {step.message}")
        return report
