from __future__ import annotations

from typing import Any
from unittest.mock import AsyncMock

import pytest

from ai_qa_test_studio.agent.dual_loop_agent import DualLoopAgent
from ai_qa_test_studio.core.models import SharedElement, TestCase, TestStep


class _Resolver:
    def __init__(self) -> None:
        self.calls = 0

    async def execute_step(self, page: Any, step: TestStep, element: SharedElement | None) -> None:
        self.calls += 1
        await AsyncMock()()  # yield to event loop


@pytest.mark.asyncio
async def test_run_case_stops_between_steps_when_cancel_requested() -> None:
    resolver = _Resolver()
    page = AsyncMock()
    page.inner_text = AsyncMock(return_value="ok")
    el = SharedElement(element_id="EL_A", page_name="P", friendly_name="A", css="#a")
    case = TestCase(
        id="c1",
        code="TC1",
        title="multi",
        steps=[
            TestStep(step_no=1, action="click", element_ref="EL_A", intent_hint="a"),
            TestStep(step_no=2, action="click", element_ref="EL_A", intent_hint="b"),
            TestStep(step_no=3, action="click", element_ref="EL_A", intent_hint="c"),
        ],
    )
    cancel = {"flag": False}

    def should_cancel() -> bool:
        return cancel["flag"]

    agent = DualLoopAgent(page, {"EL_A": el}, resolver=resolver)  # type: ignore[arg-type]

    async def _run() -> None:
        # Cancel after first step completes: flip flag mid-run via side effect
        original = resolver.execute_step

        async def wrapped(page: Any, step: TestStep, element: SharedElement | None) -> None:
            await original(page, step, element)
            if step.step_no == 1:
                cancel["flag"] = True

        resolver.execute_step = wrapped  # type: ignore[method-assign]
        report = await agent.run_case(case, project_name="t", should_cancel=should_cancel)
        assert report.cases[0].status == "CANCELLED"
        assert resolver.calls == 1
        statuses = [s.status for s in report.cases[0].steps]
        assert statuses[0] == "PASS"
        assert statuses[1] == "CANCELLED"

    await _run()


@pytest.mark.asyncio
async def test_expected_final_normalizes_whitespace() -> None:
    resolver = _Resolver()
    page = AsyncMock()
    page.inner_text = AsyncMock(return_value="Hello   World\n")
    case = TestCase(
        id="c1",
        code="TC1",
        title="verify",
        expected_final_result="Hello World",
        steps=[
            TestStep(step_no=1, action="click", element_ref="EL_A", intent_hint="a"),
        ],
    )
    el = SharedElement(element_id="EL_A", page_name="P", friendly_name="A", css="#a")
    agent = DualLoopAgent(page, {"EL_A": el}, resolver=resolver)  # type: ignore[arg-type]
    report = await agent.run_case(case, project_name="t")
    assert report.cases[0].status == "PASS"
