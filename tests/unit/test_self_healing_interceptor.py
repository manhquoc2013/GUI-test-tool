from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock

import pytest

from ai_qa_test_studio.agent.external_mcp_bridge import ExternalMcpBridge
from ai_qa_test_studio.agent.healer import SelfHealingInterceptor
from ai_qa_test_studio.constants import (
    BINDING_HEALED,
    HEAL_PERSIST_THRESHOLD,
    HEAL_RUN_THRESHOLD,
)
from ai_qa_test_studio.core.models import ElementFingerprint, SharedElement, TestStep


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
async def test_heal_score_070_runtime_not_persisted() -> None:
    """Confidence between run and persist → Healed continue, persisted=False."""
    resolver = MagicMock()
    distiller = MagicMock()
    distiller.extract = AsyncMock(
        return_value=(
            "<compact/>",
            {
                "e1": {"name": "sign in button", "css": "#submit-primary", "tag": "button"},
            },
        )
    )
    healer = SelfHealingInterceptor(distiller=distiller, resolver=resolver)
    step = _step()
    element = _element()

    audit = await healer.heal_step(MagicMock(), step, element)

    assert audit is not None
    assert audit.confidence_score is not None
    assert audit.confidence_score >= HEAL_RUN_THRESHOLD
    assert audit.confidence_score < HEAL_PERSIST_THRESHOLD
    assert audit.persisted is False
    assert element.css == "#submit-primary"
    assert step.binding_status == BINDING_HEALED
    assert "medium_confidence" in audit.reason
    assert audit.healed_selector == "#submit-primary"
    assert ExternalMcpBridge.may_persist_bound_patch(audit.confidence_score) is False


@pytest.mark.asyncio
async def test_heal_score_090_persists_fingerprint() -> None:
    resolver = MagicMock()
    distiller = MagicMock()
    distiller.extract = AsyncMock(
        return_value=(
            "<compact/>",
            {
                "e1": {
                    "name": "Sign in",
                    "css": "#submit",
                    "tag": "button",
                    "role": "button",
                    "testId": "login-submit",
                    "xpath": "/html/body/button[1]",
                },
            },
        )
    )
    healer = SelfHealingInterceptor(distiller=distiller, resolver=resolver)
    step = _step(intent="Sign in")
    element = _element()
    element.fingerprint = ElementFingerprint(
        tag="button",
        role="button",
        accessible_name="Sign in",
        text_sample="Sign in",
        test_id="login-submit",
        css="#old",
        xpath="/html/body/button[1]",
    )

    audit = await healer.heal_step(MagicMock(), step, element)

    assert audit is not None
    assert audit.confidence_score is not None
    assert audit.confidence_score >= HEAL_PERSIST_THRESHOLD
    assert audit.persisted is True
    assert element.fingerprint is not None
    assert element.fingerprint.test_id == "login-submit"
    assert ExternalMcpBridge.may_persist_bound_patch(audit.confidence_score) is True


@pytest.mark.asyncio
async def test_vision_heal_still_works() -> None:
    resolver = MagicMock()
    distiller = MagicMock()
    distiller.extract = AsyncMock(
        return_value=("<compact/>", {"e1": {"name": "noise", "css": "#noise"}})
    )
    vision_locate = AsyncMock(return_value={"css": "#vision-submit", "confidence": 0.9})
    healer = SelfHealingInterceptor(distiller=distiller, resolver=resolver)
    audit = await healer.heal_step(
        MagicMock(), _step(intent="Click green continue"), _element(), vision_locate=vision_locate
    )
    assert audit is not None
    assert audit.reason.startswith("midscene_vision")
    assert audit.persisted is True
