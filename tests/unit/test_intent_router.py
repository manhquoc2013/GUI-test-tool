from __future__ import annotations

import asyncio

import pytest

from ai_qa_test_studio.agent.intent_router import HybridIntentRouter
from ai_qa_test_studio.core.enums import DeliveryMode, IntentType, ThinkingLevel


def test_slash_and_keyword_fast_path() -> None:
    router = HybridIntentRouter()
    d = router.route_fast("/script emit login test")
    assert d is not None
    assert d.intent is IntentType.TEST_SCRIPT_GEN
    assert d.source == "slash"
    d2 = router.route_fast("please research the login docs")
    assert d2 is not None
    assert d2.intent is IntentType.RESEARCH


def test_apply_regex_sets_delivery() -> None:
    router = HybridIntentRouter()
    d = router.route_fast("apply create test cases for checkout")
    assert d is not None
    assert d.delivery_mode is DeliveryMode.APPLY


@pytest.mark.asyncio
async def test_micro_timeout_falls_back_chat_fast() -> None:
    async def slow(_prompt: str, _ctx: dict) -> dict:
        await asyncio.sleep(10)
        return {"intent": "research", "confidence": 0.99}

    router = HybridIntentRouter(micro_router=slow, timeout_s=0.05)
    decision = await router.route("ambiguous request about maybe something")
    assert decision.intent is IntentType.CHAT_FAST
    assert decision.source == "fallback"
    assert decision.thinking.level is ThinkingLevel.LOW


@pytest.mark.asyncio
async def test_micro_low_confidence_falls_back() -> None:
    async def low(_prompt: str, _ctx: dict) -> dict:
        return {"intent": "research", "confidence": 0.2, "delivery_mode": "draft"}

    router = HybridIntentRouter(micro_router=low, timeout_s=2.0, min_confidence=0.6)
    decision = await router.route("maybe do research?")
    # keyword may catch "research" first — use neutral prompt
    decision = await router.route("please handle this carefully")
    assert decision.intent is IntentType.CHAT_FAST


def test_router_module_does_not_import_matcher() -> None:
    import ast
    from pathlib import Path

    path = Path(__file__).resolve().parents[2] / "src" / "ai_qa_test_studio" / "agent" / "intent_router.py"
    tree = ast.parse(path.read_text(encoding="utf-8"))
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom) and node.module:
            assert "skill_auto_matcher" not in node.module
            assert "builtin_skills" not in node.module


@pytest.mark.asyncio
async def test_make_llm_micro_router_uses_thinking_off_kwargs() -> None:
    from ai_qa_test_studio.agent.intent_router import ThinkingConfig, make_llm_micro_router
    from ai_qa_test_studio.constants import THINKING_BUDGET_OFF

    captured: dict[str, object] = {}

    async def fake_stream(messages, max_tokens=128, cancel=None, **kwargs):  # noqa: ANN001
        captured["kwargs"] = dict(kwargs)
        captured["max_tokens"] = max_tokens
        yield '{"intent":"research","confidence":0.91,"delivery_mode":"draft"}'

    micro = make_llm_micro_router(fake_stream, provider="openai")
    off = ThinkingConfig(level=ThinkingLevel.OFF, budget_tokens=THINKING_BUDGET_OFF)
    assert off.to_provider_kwargs("openai") == {}
    result = await micro("please classify this carefully", {})
    assert result["intent"] == "research"
    assert result["confidence"] == 0.91
    assert captured["kwargs"] == {}
    assert captured["max_tokens"] == 128


@pytest.mark.asyncio
async def test_micro_success_routes_intent() -> None:
    async def ok(_prompt: str, _ctx: dict) -> dict:
        return {"intent": "swarm_audit", "confidence": 0.88, "delivery_mode": "draft"}

    router = HybridIntentRouter(micro_router=ok, timeout_s=2.0, min_confidence=0.6)
    decision = await router.route("please handle this carefully")
    assert decision.intent is IntentType.SWARM_AUDIT
    assert decision.source == "micro"
    assert decision.thinking.level is ThinkingLevel.HIGH
