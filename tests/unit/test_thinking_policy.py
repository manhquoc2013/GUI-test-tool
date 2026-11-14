from __future__ import annotations

from ai_qa_test_studio.agent.intent_router import ThinkingPolicy
from ai_qa_test_studio.core.enums import IntentType, ThinkingLevel


def test_script_fully_bound_thinking_off() -> None:
    policy = ThinkingPolicy()
    cfg = policy.for_intent(IntentType.TEST_SCRIPT_GEN, fully_bound_script=True)
    assert cfg.level is ThinkingLevel.OFF
    assert cfg.budget_tokens == 0


def test_escalate_medium_to_high() -> None:
    policy = ThinkingPolicy()
    mid = policy.for_intent(IntentType.TEST_CASE_GEN)
    assert mid.level is ThinkingLevel.MEDIUM
    high = policy.escalate(mid, reason="DOM_CHANGED")
    assert high.level is ThinkingLevel.HIGH


def test_provider_kwargs() -> None:
    policy = ThinkingPolicy()
    cfg = policy.for_intent(IntentType.RESEARCH)
    assert "reasoning_effort" in cfg.to_provider_kwargs("openai")
    assert "thinking_budget" in cfg.to_provider_kwargs("anthropic")
