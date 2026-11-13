"""Default Copilot pipeline: Planner → Composer → Critic (one review pass)."""

from __future__ import annotations

import asyncio
import json
import logging
import uuid
from collections.abc import AsyncIterator
from dataclasses import dataclass, field
from typing import Any

from ai_qa_test_studio.agent.llm_gateway import ChatMessage, UnifiedLLMGateway
from ai_qa_test_studio.agent.spec_generator import SpecGenerator
from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    APPLY_KIND_TEST_DATA,
    COPILOT_CHAT_MAX_TOKENS,
    COPILOT_MODE_COMPOSE,
    DEFAULT_EXPECTED_OUTCOME,
    MSG_STATUS_STOPPED,
    ROLE_COMPOSER,
    ROLE_CRITIC,
    ROLE_PLANNER,
)
from ai_qa_test_studio.core.token_budget import ContextCompressor

logger = logging.getLogger(__name__)


@dataclass
class OrchestratorEvent:
    kind: str
    role: str | None = None
    text: str = ""
    apply_payload: dict[str, Any] | None = None
    status: str = ""


@dataclass
class _RoleResult:
    text: str = ""
    apply_payload: dict[str, Any] | None = None
    revise_notes: str = ""
    approved: bool = True


@dataclass
class CopilotOrchestrator:
    gateway: UnifiedLLMGateway
    generator: SpecGenerator = field(default_factory=SpecGenerator)
    compressor: ContextCompressor = field(default_factory=ContextCompressor)

    async def run(
        self,
        user_text: str,
        mode: str,
        context: dict[str, Any],
        history: list[ChatMessage] | None = None,
        cancel: asyncio.Event | None = None,
        max_tokens: int = COPILOT_CHAT_MAX_TOKENS,
    ) -> AsyncIterator[OrchestratorEvent]:
        stop = cancel or asyncio.Event()
        hist = list(history or [])
        ctx_block = self._format_context(context)

        # --- Planner ---
        yield OrchestratorEvent(kind="role_start", role=ROLE_PLANNER)
        plan = await self._plan(user_text, mode, ctx_block, hist, stop, max_tokens)
        if stop.is_set():
            yield OrchestratorEvent(kind="stopped", role=ROLE_PLANNER, status=MSG_STATUS_STOPPED)
            return
        async for chunk in self._emit_text(ROLE_PLANNER, plan.text):
            yield chunk
        yield OrchestratorEvent(kind="role_end", role=ROLE_PLANNER, text=plan.text)

        # --- Composer ---
        yield OrchestratorEvent(kind="role_start", role=ROLE_COMPOSER)
        if stop.is_set():
            yield OrchestratorEvent(kind="stopped", role=ROLE_COMPOSER, status=MSG_STATUS_STOPPED)
            return
        composed = await self._compose(user_text, mode, ctx_block, plan.text, hist, stop, max_tokens)
        if stop.is_set():
            yield OrchestratorEvent(kind="stopped", role=ROLE_COMPOSER, status=MSG_STATUS_STOPPED)
            return
        async for chunk in self._emit_text(ROLE_COMPOSER, composed.text):
            yield chunk
        if composed.apply_payload:
            yield OrchestratorEvent(
                kind="apply_ready",
                role=ROLE_COMPOSER,
                apply_payload=composed.apply_payload,
                text=composed.text,
            )
        yield OrchestratorEvent(kind="role_end", role=ROLE_COMPOSER, text=composed.text)

        # --- Critic (exactly one pass) ---
        if stop.is_set():
            yield OrchestratorEvent(kind="stopped", role=ROLE_COMPOSER, status=MSG_STATUS_STOPPED)
            return
        yield OrchestratorEvent(kind="role_start", role=ROLE_CRITIC)
        if stop.is_set():
            yield OrchestratorEvent(kind="stopped", role=ROLE_CRITIC, status=MSG_STATUS_STOPPED)
            return
        critique = await self._critique(user_text, composed.text, stop, max_tokens)
        if stop.is_set():
            yield OrchestratorEvent(kind="stopped", role=ROLE_CRITIC, status=MSG_STATUS_STOPPED)
            return
        async for chunk in self._emit_text(ROLE_CRITIC, critique.text):
            yield chunk
        yield OrchestratorEvent(kind="role_end", role=ROLE_CRITIC, text=critique.text)

        if critique.approved or not critique.revise_notes:
            yield OrchestratorEvent(kind="pipeline_complete")
            return

        # Single Composer revise — no second Critic
        yield OrchestratorEvent(kind="role_start", role=ROLE_COMPOSER)
        revised = await self._compose(
            user_text,
            mode,
            ctx_block,
            plan.text + "\n\nCritic notes:\n" + critique.revise_notes,
            hist,
            stop,
            max_tokens,
            revise=True,
        )
        if stop.is_set():
            yield OrchestratorEvent(kind="stopped", role=ROLE_COMPOSER, status=MSG_STATUS_STOPPED)
            return
        async for chunk in self._emit_text(ROLE_COMPOSER, revised.text):
            yield chunk
        if revised.apply_payload:
            yield OrchestratorEvent(
                kind="apply_ready",
                role=ROLE_COMPOSER,
                apply_payload=revised.apply_payload,
                text=revised.text,
            )
        yield OrchestratorEvent(kind="role_end", role=ROLE_COMPOSER, text=revised.text)
        yield OrchestratorEvent(kind="pipeline_complete")

    async def _emit_text(self, role: str, text: str) -> AsyncIterator[OrchestratorEvent]:
        for word in text.split(" "):
            yield OrchestratorEvent(kind="chunk", role=role, text=word + " ")
            await asyncio.sleep(0)

    def _format_context(self, context: dict[str, Any]) -> str:
        parts: list[str] = []
        if context.get("include_case") and context.get("case"):
            parts.append("CASE:\n" + self.compressor.compress_text(str(context["case"])))
        if context.get("include_dom") and context.get("dom"):
            parts.append("DOM:\n" + self.compressor.budget.truncate_to_cap(str(context["dom"])))
        if context.get("include_last_error") and context.get("last_error"):
            parts.append("LAST_ERROR:\n" + str(context["last_error"])[:1000])
        if context.get("srs"):
            parts.append("SRS:\n" + self.compressor.budget.truncate_to_cap(str(context["srs"]), cap=3000))
        return "\n\n".join(parts)

    async def _plan(
        self,
        user_text: str,
        mode: str,
        ctx_block: str,
        hist: list[ChatMessage],
        stop: asyncio.Event,
        max_tokens: int,
    ) -> _RoleResult:
        if self.gateway.provider == "offline" or not self.gateway.api_key:
            steps = [
                "1. Làm rõ mục tiêu từ yêu cầu người dùng",
                "2. Composer soạn artifact / draft",
                "3. Critic rà soát 1 lần (BVA / UNBOUND / mâu thuẫn)",
            ]
            if mode == COPILOT_MODE_COMPOSE:
                steps.insert(1, "1b. Sinh outline scenario UNBOUND từ SRS/mô tả")
            return _RoleResult(text="Kế hoạch:\n" + "\n".join(steps))
        system = (
            "You are the Planner role for AI QA Test Studio. "
            "Output a short numbered plan (3-7 steps). Do not mutate test assets."
        )
        messages = [
            ChatMessage(role="system", content=system),
            *hist[-6:],
            ChatMessage(
                role="user",
                content=f"Mode={mode}\n{ctx_block}\n\nRequest:\n{user_text}",
            ),
        ]
        buf: list[str] = []
        async for chunk in self.gateway.stream_chat(messages, max_tokens=max_tokens, cancel=stop):
            buf.append(chunk)
        return _RoleResult(text="".join(buf).strip() or "Kế hoạch: phân tích → soạn → rà soát")

    async def _compose(
        self,
        user_text: str,
        mode: str,
        ctx_block: str,
        plan_text: str,
        hist: list[ChatMessage],
        stop: asyncio.Event,
        max_tokens: int,
        revise: bool = False,
    ) -> _RoleResult:
        lowered = user_text.lower()
        wants_bva = any(k in lowered for k in ("bva", "test data", "biên", "bộ dữ liệu"))
        wants_compose = mode == COPILOT_MODE_COMPOSE or any(
            k in lowered for k in ("tạo kịch bản", "srs", "scenario", "draft")
        )

        if self.gateway.provider == "offline" or not self.gateway.api_key:
            if wants_bva and mode != COPILOT_MODE_COMPOSE:
                rows = self._offline_bva_rows()
                body = "Đề xuất Test Data (BVA):\n" + json.dumps(rows, ensure_ascii=False, indent=2)
                return _RoleResult(
                    text=body,
                    apply_payload={"kind": APPLY_KIND_TEST_DATA, "rows": rows},
                )
            if wants_compose:
                srs = ""
                if "SRS:" in ctx_block:
                    srs = ctx_block.split("SRS:", 1)[-1].strip()
                srs = srs or user_text
                scenarios = self.generator.generate_outline_sync(srs)
                for sc in scenarios:
                    sc.cases = self.generator.generate_cases_sync(sc, batch_size=2)
                payload_scenarios = [s.model_dump(mode="json") for s in scenarios]
                body = (
                    "Draft UNBOUND scenarios:\n"
                    + "\n".join(f"- {s.code}: {s.title} ({len(s.cases)} cases)" for s in scenarios)
                )
                if revise:
                    body = "[Revised] " + body
                return _RoleResult(
                    text=body,
                    apply_payload={"kind": APPLY_KIND_COMPOSE, "scenarios": payload_scenarios},
                )
            answer = (
                f"{'[Revised] ' if revise else ''}"
                f"Phân tích yêu cầu «{user_text[:160]}».\n"
                f"Plan ref:\n{plan_text[:400]}\n"
                "Gợi ý: bật @Case/@DOM nếu cần ngữ cảnh; dùng Apply khi có artifact."
            )
            return _RoleResult(text=answer)

        system = (
            "You are the Composer role. Produce the deliverable for the QA request. "
            "If proposing test data, include a JSON block with key rows. "
            "If proposing scenarios, describe them clearly for Apply."
        )
        messages = [
            ChatMessage(role="system", content=system),
            *hist[-6:],
            ChatMessage(
                role="user",
                content=f"Plan:\n{plan_text}\n\nContext:\n{ctx_block}\n\nRequest:\n{user_text}",
            ),
        ]
        buf: list[str] = []
        async for chunk in self.gateway.stream_chat(messages, max_tokens=max_tokens, cancel=stop):
            buf.append(chunk)
        text = "".join(buf).strip()
        apply_payload = None
        if wants_bva:
            apply_payload = {"kind": APPLY_KIND_TEST_DATA, "rows": self._offline_bva_rows()}
        elif wants_compose:
            scenarios = self.generator.generate_outline_sync(user_text)
            for sc in scenarios:
                sc.cases = self.generator.generate_cases_sync(sc, batch_size=2)
            apply_payload = {
                "kind": APPLY_KIND_COMPOSE,
                "scenarios": [s.model_dump(mode="json") for s in scenarios],
            }
        return _RoleResult(text=text or "Không có nội dung", apply_payload=apply_payload)

    async def _critique(
        self,
        user_text: str,
        composer_text: str,
        stop: asyncio.Event,
        max_tokens: int,
    ) -> _RoleResult:
        if self.gateway.provider == "offline" or not self.gateway.api_key:
            notes = []
            if "UNBOUND" not in composer_text and "Draft" in composer_text:
                notes.append("Nhắc: case draft cần Sync URL để bind selector.")
            if "BVA" in user_text.upper() or "biên" in user_text.lower():
                notes.append("Đã kiểm tra biên min/max/empty — OK.")
            if notes:
                return _RoleResult(
                    text="Critic: cần chỉnh nhẹ.\n- " + "\n- ".join(notes),
                    approved=False,
                    revise_notes="\n".join(notes),
                )
            return _RoleResult(text="Critic: APPROVE — không phát hiện lỗ hổng lớn.", approved=True)

        system = (
            "You are the Critic. Reply with APPROVE or REVISE and short notes. "
            "One review only."
        )
        messages = [
            ChatMessage(role="system", content=system),
            ChatMessage(
                role="user",
                content=f"Request:\n{user_text}\n\nComposer output:\n{composer_text}",
            ),
        ]
        buf: list[str] = []
        async for chunk in self.gateway.stream_chat(messages, max_tokens=max_tokens, cancel=stop):
            buf.append(chunk)
        text = "".join(buf).strip()
        approved = "REVISE" not in text.upper() or "APPROVE" in text.upper()
        revise_notes = text if not approved else ""
        return _RoleResult(text=text, approved=approved, revise_notes=revise_notes)

    def _offline_bva_rows(self) -> list[dict[str, Any]]:
        return [
            {
                "scenario": f"BVA-{i+1}",
                "parameters": {"value": tip},
                "expectedOutcome": outcome,
            }
            for i, (tip, outcome) in enumerate(
                [
                    ("min-1", "VALIDATION_ERROR"),
                    ("min", DEFAULT_EXPECTED_OUTCOME),
                    ("max", DEFAULT_EXPECTED_OUTCOME),
                    ("max+1", "VALIDATION_ERROR"),
                    ("empty", "VALIDATION_ERROR"),
                ]
            )
        ]


def new_message_id() -> str:
    return str(uuid.uuid4())
