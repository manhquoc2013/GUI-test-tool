"""Estimate task complexity from cheap signals (optional fast LLM at boundary)."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Any

from ai_qa_test_studio.agent.llm_gateway import ChatMessage, UnifiedLLMGateway
from ai_qa_test_studio.constants import (
    COMPLEXITY_COMPLEX,
    COMPLEXITY_NORMAL,
    COMPLEXITY_NORMAL_MAX_CHARS,
    COMPLEXITY_SIMPLE,
    COMPLEXITY_SIMPLE_MAX_CHARS,
    COPILOT_MODE_COMPOSE,
)


@dataclass
class ComplexityClassifier:
    async def classify(
        self,
        user_text: str,
        mode: str,
        context: dict[str, Any],
        gateway: UnifiedLLMGateway | None = None,
        cancel: asyncio.Event | None = None,
    ) -> str:
        text = user_text or ""
        lowered = text.lower()
        score = 0
        length = len(text) + len(str(context.get("srs") or ""))
        if length <= COMPLEXITY_SIMPLE_MAX_CHARS:
            score += 0
        elif length <= COMPLEXITY_NORMAL_MAX_CHARS:
            score += 1
        else:
            score += 2
        if mode == COPILOT_MODE_COMPOSE:
            score += 1
        if context.get("include_dom"):
            score += 1
        if context.get("include_case"):
            score += 1
        if any(k in lowered for k in ("bva", "biên", "nhiều scenario", "multi", "srs", "toàn bộ")):
            score += 1
        if score <= 1:
            return COMPLEXITY_SIMPLE
        if score <= 3:
            # Boundary — optional fast LLM confirm
            if gateway is not None and gateway.provider != "offline":
                hinted = await self._llm_hint(text, gateway, cancel)
                if hinted in {COMPLEXITY_SIMPLE, COMPLEXITY_NORMAL, COMPLEXITY_COMPLEX}:
                    return hinted
            return COMPLEXITY_NORMAL
        return COMPLEXITY_COMPLEX

    async def _llm_hint(
        self,
        user_text: str,
        gateway: UnifiedLLMGateway,
        cancel: asyncio.Event | None,
    ) -> str:
        messages = [
            ChatMessage(
                role="system",
                content=(
                    "Classify QA task complexity. Reply with exactly one word: "
                    "simple, normal, or complex."
                ),
            ),
            ChatMessage(role="user", content=user_text[:1200]),
        ]
        buf: list[str] = []
        async for chunk in gateway.stream_chat(messages, max_tokens=8, cancel=cancel):
            buf.append(chunk)
        raw = "".join(buf).strip().lower()
        for level in (COMPLEXITY_SIMPLE, COMPLEXITY_NORMAL, COMPLEXITY_COMPLEX):
            if level in raw:
                return level
        return COMPLEXITY_NORMAL
