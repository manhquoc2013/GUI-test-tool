"""Estimate Copilot task complexity for (task × complexity) → model tier routing.

Inspired by Ruflo QE model-routing (category→complexity) and ECC `/model-route`
(haiku/sonnet/opus by risk), adapted to AI QA Test Studio signals — not vendored.
"""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Any

from ai_qa_test_studio.agent.llm_gateway import ChatMessage, UnifiedLLMGateway
from ai_qa_test_studio.constants import (
    COMPLEXITY_COMPLEX,
    COMPLEXITY_EXPLAIN_HINTS,
    COMPLEXITY_HARD_HINTS,
    COMPLEXITY_LARGE_DOC_CHARS,
    COMPLEXITY_NORMAL,
    COMPLEXITY_NORMAL_HINTS,
    COMPLEXITY_NORMAL_MAX_CHARS,
    COMPLEXITY_SIMPLE,
    COMPLEXITY_SIMPLE_MAX_CHARS,
    CONTEXT_KEY_ATTACHMENTS,
    COPILOT_COMPOSE_DOC_HINTS,
    COPILOT_COMPOSE_KEYWORDS,
    COPILOT_MODE_COMPOSE,
    LLM_PROVIDER_OFFLINE,
)


@dataclass(frozen=True)
class ComplexityAssessment:
    level: str
    score: int
    signals: tuple[str, ...]


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
        assessment = self.assess(user_text, mode, context)
        level = assessment.level
        if (
            level == COMPLEXITY_NORMAL
            and gateway is not None
            and gateway.provider != LLM_PROVIDER_OFFLINE
        ):
            hinted = await self._llm_hint(
                user_text, mode, context, assessment, gateway, cancel
            )
            if hinted in {COMPLEXITY_SIMPLE, COMPLEXITY_NORMAL, COMPLEXITY_COMPLEX}:
                return hinted
        return level

    def assess(
        self,
        user_text: str,
        mode: str,
        context: dict[str, Any],
    ) -> ComplexityAssessment:
        """Deterministic score — unit-testable without LLM."""
        text = user_text or ""
        lowered = text.lower()
        signals: list[str] = []
        score = 0

        doc_chars = self._document_chars(context)
        prompt_chars = len(text)
        total_chars = prompt_chars + doc_chars

        if total_chars <= COMPLEXITY_SIMPLE_MAX_CHARS:
            signals.append("size:small")
        elif total_chars <= COMPLEXITY_NORMAL_MAX_CHARS:
            score += 1
            signals.append("size:medium")
        elif total_chars <= COMPLEXITY_LARGE_DOC_CHARS:
            score += 2
            signals.append("size:large")
        else:
            score += 3
            signals.append("size:xlarge")

        attachments = context.get(CONTEXT_KEY_ATTACHMENTS) or []
        if isinstance(attachments, list) and attachments:
            n = len(attachments)
            if n == 1:
                score += 1
                signals.append("attachments:1")
            else:
                score += 2
                signals.append(f"attachments:{n}")

        if mode == COPILOT_MODE_COMPOSE:
            score += 1
            signals.append("mode:compose")
        elif any(k in lowered for k in COPILOT_COMPOSE_KEYWORDS) or (
            doc_chars > 0 and any(k in lowered for k in COPILOT_COMPOSE_DOC_HINTS)
        ):
            score += 1
            signals.append("intent:compose")

        if context.get("include_dom"):
            score += 1
            signals.append("ctx:dom")
        if context.get("include_case"):
            score += 1
            signals.append("ctx:case")
        if context.get("srs") or doc_chars > 0:
            score += 1
            signals.append("ctx:doc")

        if any(k in lowered for k in COMPLEXITY_HARD_HINTS):
            score += 2
            signals.append("hint:hard")
        elif any(k in lowered for k in COMPLEXITY_NORMAL_HINTS):
            score += 1
            signals.append("hint:normal")

        explain_only = any(k in lowered for k in COMPLEXITY_EXPLAIN_HINTS) and not (
            mode == COPILOT_MODE_COMPOSE
            or any(k in lowered for k in COPILOT_COMPOSE_KEYWORDS)
            or doc_chars > COMPLEXITY_SIMPLE_MAX_CHARS
        )
        if explain_only:
            score = min(score, 1)
            signals.append("cap:explain")

        # Floors: large SRS / multi-doc compose must not stay "simple".
        if doc_chars > COMPLEXITY_NORMAL_MAX_CHARS and score < 2:
            score = 2
            signals.append("floor:doc→normal")
        if doc_chars > COMPLEXITY_LARGE_DOC_CHARS and (
            mode == COPILOT_MODE_COMPOSE
            or any(k in lowered for k in COPILOT_COMPOSE_KEYWORDS)
            or any(k in lowered for k in COPILOT_COMPOSE_DOC_HINTS)
        ):
            score = max(score, 4)
            signals.append("floor:xlarge-compose→complex")

        if score <= 1:
            level = COMPLEXITY_SIMPLE
        elif score <= 3:
            level = COMPLEXITY_NORMAL
        else:
            level = COMPLEXITY_COMPLEX
        return ComplexityAssessment(level=level, score=score, signals=tuple(signals))

    def _document_chars(self, context: dict[str, Any]) -> int:
        total = len(str(context.get("srs") or ""))
        attachments = context.get(CONTEXT_KEY_ATTACHMENTS) or []
        if not isinstance(attachments, list):
            return total
        for item in attachments:
            if isinstance(item, dict):
                total += len(str(item.get("markdown") or item.get("text") or ""))
            else:
                total += len(str(item))
        return total

    async def _llm_hint(
        self,
        user_text: str,
        mode: str,
        context: dict[str, Any],
        assessment: ComplexityAssessment,
        gateway: UnifiedLLMGateway,
        cancel: asyncio.Event | None,
    ) -> str:
        doc_chars = self._document_chars(context)
        messages = [
            ChatMessage(
                role="system",
                content=(
                    "Classify Vietnamese QA Copilot task complexity for model routing. "
                    "Reply with exactly one word: simple, normal, or complex.\n"
                    "simple = explain/debug short ask, tiny edit, no large SRS "
                    "(fast/cheap model).\n"
                    "normal = BVA/test-data, single scenario draft, modest doc "
                    "(mid model).\n"
                    "complex = full SRS suite, multi-scenario/E2E/security, large "
                    "or multi attachments (strong model).\n"
                    "Prefer meaning over keywords; typos still count."
                ),
            ),
            ChatMessage(
                role="user",
                content=(
                    f"Mode={mode}\nHeuristicScore={assessment.score}\n"
                    f"HeuristicLevel={assessment.level}\n"
                    f"Signals={','.join(assessment.signals)}\n"
                    f"DocChars={doc_chars}\n"
                    f"Request:\n{(user_text or '')[:1200]}"
                ),
            ),
        ]
        buf: list[str] = []
        async for chunk in gateway.stream_chat(messages, max_tokens=8, cancel=cancel):
            buf.append(chunk)
        raw = "".join(buf).strip().lower()
        for level in (COMPLEXITY_SIMPLE, COMPLEXITY_NORMAL, COMPLEXITY_COMPLEX):
            if level in raw:
                return level
        return COMPLEXITY_NORMAL
