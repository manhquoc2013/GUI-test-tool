from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_qa_test_studio.agent.healer import Healer
from ai_qa_test_studio.constants import BINDING_HEALED
from ai_qa_test_studio.core.models import SharedElement, TestStep


def _step(*, intent: str = "Sign in button") -> TestStep:
    return TestStep(
        step_no=3,
        action="click",
        element_ref="EL_SUBMIT",
        intent_hint=intent,
        binding_status="BOUND",
    )


def _element(*, css: str = "#stale-submit") -> SharedElement:
    return SharedElement(
        element_id="EL_SUBMIT",
        page_name="Login",
        friendly_name="Submit",
        primary_playwright=css,
        css=css,
    )


@pytest.mark.asyncio
async def test_heal_attempts_dom_when_semantic_match_exists() -> None:
    """Post-failure heal must continue even when the locator still matches."""
    resolver = MagicMock()
    resolver.try_semantic_match = AsyncMock(return_value=MagicMock(name="matched_locator"))
    distiller = MagicMock()
    distiller.extract = AsyncMock(
        return_value=(
            "<compact/>",
            {
                "e1": {"name": "sign in button", "css": "#submit-primary"},
            },
        )
    )
    healer = Healer(distiller=distiller, resolver=resolver)
    step = _step()
    element = _element()

    audit = await healer.heal_step(MagicMock(), step, element)

    distiller.extract.assert_awaited_once()
    assert audit is not None
    assert audit.reason == "dom_heuristic"
    assert audit.new_locator == "#submit-primary"
    assert element.css == "#submit-primary"
    assert step.binding_status == BINDING_HEALED


@pytest.mark.asyncio
async def test_heal_attempts_vision_when_semantic_match_exists() -> None:
    """If DOM heuristic misses, vision_locate must still run after a semantic match."""
    resolver = MagicMock()
    resolver.try_semantic_match = AsyncMock(return_value=MagicMock(name="matched_locator"))
    distiller = MagicMock()
    distiller.extract = AsyncMock(
        return_value=(
            "<compact/>",
            {"e1": {"name": "unrelated widget", "css": "#noise"}},
        )
    )
    vision_locate = AsyncMock(return_value={"css": "#vision-submit"})
    healer = Healer(distiller=distiller, resolver=resolver)
    step = _step(intent="Click the green continue control")
    element = _element()

    audit = await healer.heal_step(MagicMock(), step, element, vision_locate=vision_locate)

    vision_locate.assert_awaited_once()
    assert audit is not None
    assert audit.reason == "midscene_vision"
    assert audit.new_locator == "#vision-submit"
    assert element.css == "#vision-submit"
    assert step.binding_status == BINDING_HEALED
