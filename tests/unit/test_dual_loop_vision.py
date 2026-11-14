from __future__ import annotations

from typing import Any
from unittest.mock import AsyncMock

import pytest

from ai_qa_test_studio.agent.dual_loop_agent import DualLoopAgent
from ai_qa_test_studio.constants import CASE_STATUS_CANCELLED
from ai_qa_test_studio.core.models import SharedElement, StepAuditEntry, TestCase, TestStep


class _FailThenPassResolver:
    def __init__(self) -> None:
        self.calls = 0

    async def execute_step(self, page: Any, step: TestStep, element: SharedElement | None) -> None:
        self.calls += 1
        if self.calls == 1:
            raise RuntimeError("stale locator")


class _AlwaysFailResolver:
    def __init__(self) -> None:
        self.calls = 0
        self.heal_started = False

    async def execute_step(self, page: Any, step: TestStep, element: SharedElement | None) -> None:
        self.calls += 1
        raise RuntimeError("always fail")


@pytest.mark.asyncio
async def test_vision_locate_heals_when_dom_misses() -> None:
    resolver = _FailThenPassResolver()
    page = AsyncMock()
    page.inner_text = AsyncMock(return_value="ok")
    el = SharedElement(element_id="EL_A", page_name="P", friendly_name="A", css="#stale")
    case = TestCase(
        id="c1",
        code="TC1",
        title="vision",
        steps=[TestStep(step_no=1, action="click", element_ref="EL_A", intent_hint="continue")],
    )
    vision_locate = AsyncMock(return_value={"css": "#healed-btn", "confidence": 0.9})
    healer = AsyncMock()
    healer.heal_step = AsyncMock(
        return_value=StepAuditEntry(
            step_no=1,
            old_locator="#stale",
            new_locator="#healed-btn",
            reason="midscene_vision",
            binding_status="HEALED",
        )
    )

    agent = DualLoopAgent(
        page,
        {"EL_A": el},
        resolver=resolver,  # type: ignore[arg-type]
        healer=healer,
        vision_locate=vision_locate,
    )
    report = await agent.run_case(case, project_name="t")
    healer.heal_step.assert_awaited()
    kwargs = healer.heal_step.await_args.kwargs
    assert kwargs.get("vision_locate") is vision_locate
    assert report.cases[0].status in {"PASS", "HEALED"}
    assert resolver.calls == 2
    assert report.cases[0].steps[0].status == "HEALED"


@pytest.mark.asyncio
async def test_cancel_between_execute_fail_and_heal() -> None:
    resolver = _AlwaysFailResolver()
    page = AsyncMock()
    page.inner_text = AsyncMock(return_value="ok")
    el = SharedElement(element_id="EL_A", page_name="P", friendly_name="A", css="#a")
    case = TestCase(
        id="c1",
        code="TC1",
        title="cancel-heal",
        steps=[TestStep(step_no=1, action="click", element_ref="EL_A", intent_hint="a")],
    )
    cancel = {"flag": False}
    healer = AsyncMock()
    healer.heal_step = AsyncMock(return_value=None)

    async def execute_and_cancel(
        page: Any, step: TestStep, element: SharedElement | None
    ) -> None:
        resolver.calls += 1
        cancel["flag"] = True
        raise RuntimeError("boom")

    resolver.execute_step = execute_and_cancel  # type: ignore[method-assign]
    agent = DualLoopAgent(
        page,
        {"EL_A": el},
        resolver=resolver,  # type: ignore[arg-type]
        healer=healer,
    )
    report = await agent.run_case(case, project_name="t", should_cancel=lambda: cancel["flag"])
    assert report.cases[0].status == CASE_STATUS_CANCELLED
    healer.heal_step.assert_not_awaited()
    assert report.cases[0].steps[0].status == CASE_STATUS_CANCELLED
