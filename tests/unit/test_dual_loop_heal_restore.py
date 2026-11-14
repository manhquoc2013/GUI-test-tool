"""Invariant #3 — mid-band heal mutates for retest then restores SharedElement (no YAML)."""

from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_qa_test_studio.agent.dual_loop_agent import DualLoopAgent
from ai_qa_test_studio.agent.healer import Healer
from ai_qa_test_studio.constants import HEAL_PERSIST_THRESHOLD, HEAL_RUN_THRESHOLD
from ai_qa_test_studio.core.models import DistilledNode, ElementFingerprint, SharedElement, TestCase, TestStep


@pytest.mark.asyncio
async def test_mid_band_heal_restores_shared_element_after_retest() -> None:
    page = AsyncMock()
    page.screenshot = AsyncMock(return_value=b"\x89PNG")
    element = SharedElement(
        element_id="EL_BTN",
        page_name="P",
        friendly_name="Go",
        css="#stale",
        primary_playwright="#stale",
        fingerprint=ElementFingerprint(
            tag="button",
            role="button",
            accessible_name="Continue",
            css="#stale",
        ),
    )
    distiller = MagicMock()
    distiller.distill = AsyncMock(
        return_value=[
            DistilledNode(
                short_id="e1",
                fingerprint=ElementFingerprint(
                    tag="button",
                    role="button",
                    accessible_name="Continue action",
                    css="#healed-mid",
                    text_sample="Continue action",
                ),
            )
        ]
    )
    healer = Healer(distiller=distiller)
    resolver = MagicMock()
    resolver.execute_step = AsyncMock(side_effect=[RuntimeError("stale"), None])

    agent = DualLoopAgent(page, {"EL_BTN": element}, resolver=resolver, healer=healer)
    agent.mcp_actions_enabled = False

    case = TestCase(
        id="c1",
        code="TC1",
        title="t",
        steps=[
            TestStep(
                step_no=1,
                action="click",
                element_ref="EL_BTN",
                intent_hint="Continue",
                binding_status="BOUND",
            )
        ],
    )
    report = await agent.run_case(case, project_name="demo")
    assert report.cases
    step = report.cases[0].steps[0]
    assert step.step_status.value == "Healed" or str(step.status) == "HEALED"
    assert element.css == "#stale"
    assert element.primary_playwright == "#stale"
    assert element.fingerprint is not None
    assert element.fingerprint.css == "#stale"
    audit = report.healing_audit[0] if report.healing_audit else step.audit
    assert audit is not None
    assert audit.persisted is False
    assert audit.confidence_score is not None
    assert HEAL_RUN_THRESHOLD <= audit.confidence_score < HEAL_PERSIST_THRESHOLD
