"""Hybrid Intent Router (2-tier). Does NOT import or call SkillAutoMatcher."""

from __future__ import annotations

import asyncio
import json
import logging
import re
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from typing import Any

from ai_qa_test_studio.constants import (
    APPLY_PROMPT_RE,
    INTENT_SLASH_MAP,
    MICRO_ROUTER_MIN_CONFIDENCE,
    MICRO_ROUTER_TIMEOUT_S,
    THINKING_BUDGET_HIGH,
    THINKING_BUDGET_LOW,
    THINKING_BUDGET_MEDIUM,
    THINKING_BUDGET_OFF,
)
from ai_qa_test_studio.core.enums import DeliveryMode, IntentType, ThinkingLevel

logger = logging.getLogger(__name__)

MicroRouterFn = Callable[[str, dict[str, Any]], Awaitable[dict[str, Any]]]

_APPLY_RE = re.compile(APPLY_PROMPT_RE)
_KEYWORD_RULES: tuple[tuple[re.Pattern[str], IntentType], ...] = (
    (re.compile(r"(?i)\b(research|tra cứu|docs?|document)\b"), IntentType.RESEARCH),
    (re.compile(r"(?i)\b(scenario|kịch bản|happy.?path|boundary)\b"), IntentType.TEST_SCENARIO),
    (re.compile(r"(?i)\b(test.?cases?|tc\b|bva|iso.?29119)\b"), IntentType.TEST_CASE_GEN),
    (re.compile(r"(?i)\b(script|playwright|codegen|exporter)\b"), IntentType.TEST_SCRIPT_GEN),
    (re.compile(r"(?i)\b(run|execute|bind|heal|dual.?loop)\b"), IntentType.EXEC_AND_BIND),
    (
        re.compile(
            r"(?i)\b("
            r"audit|swarm|traceability|evidence|"
            r"kiểm\s*toán|bằng\s*chứng|evidence\s*report|độ\s*phủ"
            r")\b"
        ),
        IntentType.SWARM_AUDIT,
    ),
)


@dataclass(frozen=True)
class ThinkingConfig:
    level: ThinkingLevel
    budget_tokens: int

    def to_provider_kwargs(self, provider: str = "openai") -> dict[str, Any]:
        if self.level == ThinkingLevel.OFF:
            return {}
        key = (provider or "openai").lower()
        if key in {"anthropic", "gemini"}:
            return {"thinking_budget": self.budget_tokens}
        # DeepSeek / OpenAI-style
        effort = {
            ThinkingLevel.LOW: "low",
            ThinkingLevel.MEDIUM: "medium",
            ThinkingLevel.HIGH: "high",
        }.get(self.level, "medium")
        return {"reasoning_effort": effort}


@dataclass(frozen=True)
class RoutingDecision:
    intent: IntentType
    delivery_mode: DeliveryMode
    thinking: ThinkingConfig
    confidence: float
    source: str  # slash | keyword | ui | micro | fallback
    prompt_body: str


class ThinkingPolicy:
    """Map Intent → ThinkingLevel; escalate MEDIUM→HIGH on failure signals."""

    _DEFAULT: dict[IntentType, ThinkingLevel] = {
        IntentType.CHAT_FAST: ThinkingLevel.LOW,
        IntentType.RESEARCH: ThinkingLevel.HIGH,
        IntentType.TEST_SCENARIO: ThinkingLevel.HIGH,
        IntentType.TEST_CASE_GEN: ThinkingLevel.MEDIUM,
        IntentType.TEST_SCRIPT_GEN: ThinkingLevel.LOW,
        IntentType.EXEC_AND_BIND: ThinkingLevel.MEDIUM,
        IntentType.SWARM_AUDIT: ThinkingLevel.HIGH,
    }

    _BUDGET: dict[ThinkingLevel, int] = {
        ThinkingLevel.OFF: THINKING_BUDGET_OFF,
        ThinkingLevel.LOW: THINKING_BUDGET_LOW,
        ThinkingLevel.MEDIUM: THINKING_BUDGET_MEDIUM,
        ThinkingLevel.HIGH: THINKING_BUDGET_HIGH,
    }

    def for_intent(
        self,
        intent: IntentType,
        *,
        fully_bound_script: bool = False,
        chat_trivial: bool = False,
    ) -> ThinkingConfig:
        if intent == IntentType.TEST_SCRIPT_GEN and fully_bound_script:
            level = ThinkingLevel.OFF
        elif intent == IntentType.CHAT_FAST and chat_trivial:
            level = ThinkingLevel.OFF
        else:
            level = self._DEFAULT.get(intent, ThinkingLevel.MEDIUM)
        return ThinkingConfig(level=level, budget_tokens=self._BUDGET[level])

    def escalate(self, config: ThinkingConfig, *, reason: str = "") -> ThinkingConfig:
        if config.level == ThinkingLevel.MEDIUM:
            logger.info("thinking_escalate reason=%s", reason or "policy")
            return ThinkingConfig(
                level=ThinkingLevel.HIGH,
                budget_tokens=self._BUDGET[ThinkingLevel.HIGH],
            )
        return config


class HybridIntentRouter:
    """Tier-1 rules/UI/slash; Tier-2 micro-router with timeout → CHAT_FAST fallback."""

    def __init__(
        self,
        *,
        micro_router: MicroRouterFn | None = None,
        policy: ThinkingPolicy | None = None,
        timeout_s: float = MICRO_ROUTER_TIMEOUT_S,
        min_confidence: float = MICRO_ROUTER_MIN_CONFIDENCE,
    ) -> None:
        self._micro = micro_router
        self.policy = policy or ThinkingPolicy()
        self.timeout_s = timeout_s
        self.min_confidence = min_confidence

    def route_fast(
        self,
        prompt: str,
        *,
        ui_intent: IntentType | None = None,
        apply_requested: bool = False,
    ) -> RoutingDecision | None:
        text = (prompt or "").strip()
        delivery = DeliveryMode.APPLY if (
            apply_requested or bool(_APPLY_RE.match(text))
        ) else DeliveryMode.DRAFT
        body = text
        # Slash
        for slash, intent_val in INTENT_SLASH_MAP.items():
            if text.lower().startswith(slash):
                body = text[len(slash) :].lstrip()
                intent = IntentType(intent_val)
                thinking = self.policy.for_intent(intent, chat_trivial=intent == IntentType.CHAT_FAST)
                return RoutingDecision(
                    intent=intent,
                    delivery_mode=delivery,
                    thinking=thinking,
                    confidence=1.0,
                    source="slash",
                    prompt_body=body or text,
                )
        if ui_intent is not None:
            thinking = self.policy.for_intent(ui_intent)
            return RoutingDecision(
                intent=ui_intent,
                delivery_mode=delivery,
                thinking=thinking,
                confidence=0.95,
                source="ui",
                prompt_body=body,
            )
        for pattern, intent in _KEYWORD_RULES:
            if pattern.search(text):
                thinking = self.policy.for_intent(intent)
                return RoutingDecision(
                    intent=intent,
                    delivery_mode=delivery,
                    thinking=thinking,
                    confidence=0.85,
                    source="keyword",
                    prompt_body=body,
                )
        # Short greetings → CHAT_FAST without micro
        if len(text) <= 24 and re.fullmatch(
            r"(?i)(hi|hello|hey|chào|xin chào|yo|ok|thanks|cảm ơn)[!?.]*",
            text,
        ):
            thinking = self.policy.for_intent(IntentType.CHAT_FAST, chat_trivial=True)
            return RoutingDecision(
                intent=IntentType.CHAT_FAST,
                delivery_mode=delivery,
                thinking=thinking,
                confidence=1.0,
                source="keyword",
                prompt_body=body,
            )
        return None

    async def route(
        self,
        prompt: str,
        *,
        ui_intent: IntentType | None = None,
        apply_requested: bool = False,
        context: dict[str, Any] | None = None,
    ) -> RoutingDecision:
        fast = self.route_fast(
            prompt, ui_intent=ui_intent, apply_requested=apply_requested
        )
        if fast is not None:
            return fast
        return await self._route_micro(prompt, context=context or {}, apply_requested=apply_requested)

    async def _route_micro(
        self,
        prompt: str,
        *,
        context: dict[str, Any],
        apply_requested: bool,
    ) -> RoutingDecision:
        delivery = DeliveryMode.APPLY if (
            apply_requested or bool(_APPLY_RE.match(prompt or ""))
        ) else DeliveryMode.DRAFT
        fallback = RoutingDecision(
            intent=IntentType.CHAT_FAST,
            delivery_mode=delivery,
            thinking=self.policy.for_intent(IntentType.CHAT_FAST),
            confidence=0.0,
            source="fallback",
            prompt_body=prompt,
        )
        if self._micro is None:
            return fallback
        try:
            raw = await asyncio.wait_for(
                self._micro(prompt, context),
                timeout=self.timeout_s,
            )
        except TimeoutError:
            logger.warning("micro_router_timeout timeout_s=%.1f → CHAT_FAST", self.timeout_s)
            return fallback
        except Exception as exc:  # noqa: BLE001
            logger.warning("micro_router_failed err=%s → CHAT_FAST", exc)
            return fallback

        try:
            if isinstance(raw, str):
                raw = json.loads(raw)
            intent = IntentType(str(raw.get("intent") or IntentType.CHAT_FAST.value))
            conf = float(raw.get("confidence") or 0.0)
            mode_raw = str(raw.get("delivery_mode") or delivery.value)
            mode = DeliveryMode(mode_raw) if mode_raw in DeliveryMode._value2member_map_ else delivery
        except (TypeError, ValueError, KeyError, json.JSONDecodeError) as exc:
            logger.warning("micro_router_parse_failed err=%s → CHAT_FAST", exc)
            return fallback

        if conf < self.min_confidence:
            logger.info("micro_router_low_confidence conf=%.2f → CHAT_FAST", conf)
            return fallback

        thinking = self.policy.for_intent(intent)
        return RoutingDecision(
            intent=intent,
            delivery_mode=mode,
            thinking=thinking,
            confidence=conf,
            source="micro",
            prompt_body=prompt,
        )
