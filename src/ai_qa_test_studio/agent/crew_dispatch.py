"""Specialist dispatch for coordinator-led crew (extracted from orchestrator)."""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from typing import Any

from ai_qa_test_studio.agent.bundled_mcp import BundledMcpRegistry
from ai_qa_test_studio.agent.crew_exchange import CrewBoard
from ai_qa_test_studio.agent.llm_gateway import ChatMessage
from ai_qa_test_studio.constants import (
    EVENT_CLARIFY_RESOLVED,
    EVENT_STATUS_SUMMARY,
    LLM_TASK_ANALYZE,
    LLM_TASK_COMPOSE,
    LLM_TASK_CRITIQUE,
    LLM_TASK_OPTIMIZE,
    LLM_TASK_PLAN,
    LLM_TASK_REVISE,
    ROLE_ANALYZER,
    ROLE_COMPOSER,
    ROLE_COORDINATOR,
    ROLE_CRITIC,
    ROLE_OPTIMIZER,
    ROLE_PLANNER,
)


async def dispatch_specialist(
    host: Any,
    *,
    to_role: str,
    board: CrewBoard,
    roster: Any,
    packet: str,
    hist: list[ChatMessage],
    stop: asyncio.Event,
    max_tokens: int,
    mcp: BundledMcpRegistry,
    complexity: str,
    complexity_vn: str,
) -> AsyncIterator[Any]:
    """Run one specialist hop; mutates board and yields orchestrator events."""
    from ai_qa_test_studio.agent.orchestrator import OrchestratorEvent, _RoleResult

    board.dispatched.append(to_role)
    yield OrchestratorEvent(kind="role_start", role=to_role)
    if to_role == ROLE_PLANNER:
        route = host._route(LLM_TASK_PLAN, complexity)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=to_role,
            text=host._clip(f"{host._role_label(to_role)} · {complexity_vn} · {route.model}"),
            model=route.model,
        )
        out: list[_RoleResult] = []
        async for ev in host._plan(
            board.user_text,
            board.mode,
            board.ctx_block,
            hist,
            stop,
            max_tokens,
            route.gateway(),
            out,
            intent=board.intent,
            roster=roster,
        ):
            yield ev
        if not out:
            return
        result = out[0]
        board.plan_text = result.text
        ref = host._put_artifact(mcp, to_role, "plan", result.text)
        result.summary = host._summary_of(mcp, ref, result.text, role=to_role)
        async for ev in host._maybe_clarify(
            to_role, result, board.user_text, complexity, stop, max_tokens, mode=board.mode
        ):
            if ev.kind == EVENT_CLARIFY_RESOLVED and ev.text:
                board.user_text = f"{board.user_text}\n\n[Clarify] {ev.text}"
            yield ev
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=to_role,
            text=result.summary,
            artifact_ref=ref,
            model=route.model,
        )
        yield OrchestratorEvent(kind="role_end", role=to_role, text=result.text)
        return

    if to_role == ROLE_COMPOSER:
        task = LLM_TASK_REVISE if (board.compose_text and not board.critique_approved) else LLM_TASK_COMPOSE
        route = host._route(task, complexity)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=to_role,
            text=host._clip(f"{host._role_label(to_role)} · {complexity_vn} · {route.model}"),
            model=route.model,
        )
        plan_for_compose = packet if not board.plan_text else (
            host._handoff(
                ROLE_COORDINATOR,
                ROLE_COMPOSER,
                goal="Thực thi theo kế hoạch + handoff điều phối",
                acceptance="Artifact/Apply hoặc trả lời rõ",
                body=f"{board.plan_text}\n\n{packet}",
            )
        )
        revising = bool(board.compose_text and not board.critique_approved)
        out = []
        async for ev in host._compose(
            board.user_text,
            board.mode,
            board.ctx_block,
            plan_for_compose,
            hist,
            stop,
            max_tokens,
            route.gateway(),
            out,
            revise=revising,
            intent_hint=board.intent,
        ):
            yield ev
        if not out:
            return
        result = out[0]
        board.compose_text = result.text
        board.mark_compose(revising=revising)
        if result.intent:
            board.intent = result.intent
        if result.apply_payload:
            board.apply_payload = result.apply_payload
        ref = host._put_artifact(mcp, to_role, "compose", result.text)
        result.summary = host._summary_of(mcp, ref, result.text, role=to_role)
        async for ev in host._maybe_clarify(
            to_role, result, board.user_text, complexity, stop, max_tokens, mode=board.mode
        ):
            if ev.kind == EVENT_CLARIFY_RESOLVED and ev.text:
                board.user_text = f"{board.user_text}\n\n[Clarify] {ev.text}"
            yield ev
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=to_role,
            text=result.summary,
            artifact_ref=ref,
            model=route.model,
        )
        if result.apply_payload:
            yield OrchestratorEvent(
                kind="apply_ready",
                role=to_role,
                apply_payload=result.apply_payload,
                text=result.summary,
            )
        yield OrchestratorEvent(kind="role_end", role=to_role, text=result.text)
        return

    if to_role == ROLE_CRITIC:
        route = host._route(LLM_TASK_CRITIQUE, complexity)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=to_role,
            text=host._clip(f"{host._role_label(to_role)} · {complexity_vn} · {route.model}"),
            model=route.model,
        )
        out = []
        async for ev in host._critique(
            board.user_text,
            board.compose_text or board.user_text,
            stop,
            max_tokens,
            route.gateway(),
            out,
            intent=board.intent,
        ):
            yield ev
        if not out:
            return
        result = out[0]
        board.mark_critique(
            approved=result.approved,
            notes=result.revise_notes,
            text=result.text,
        )
        ref = host._put_artifact(mcp, to_role, "critique", result.text)
        result.summary = host._summary_of(mcp, ref, result.text, role=to_role)
        async for ev in host._maybe_clarify(
            to_role, result, board.user_text, complexity, stop, max_tokens, mode=board.mode
        ):
            if ev.kind == EVENT_CLARIFY_RESOLVED and ev.text:
                board.user_text = f"{board.user_text}\n\n[Clarify] {ev.text}"
            yield ev
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=to_role,
            text=result.summary,
            artifact_ref=ref,
            model=route.model,
        )
        yield OrchestratorEvent(kind="role_end", role=to_role, text=result.text)
        return

    if to_role == ROLE_ANALYZER:
        route = host._route(LLM_TASK_ANALYZE, complexity)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=to_role,
            text=host._clip(f"{host._role_label(to_role)} · {route.model}"),
            model=route.model,
        )
        out = []
        async for ev in host._analyze(
            board.user_text,
            board.compose_text,
            board.critique_text,
            stop,
            max_tokens,
            route.gateway(),
            out,
        ):
            yield ev
        if not out:
            return
        result = out[0]
        board.analyze_text = result.text
        ref = host._put_artifact(mcp, to_role, "analysis", result.text)
        result.summary = host._summary_of(mcp, ref, result.text, role=to_role)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=to_role,
            text=result.summary,
            artifact_ref=ref,
            model=route.model,
        )
        yield OrchestratorEvent(kind="role_end", role=to_role, text=result.text)
        return

    if to_role == ROLE_OPTIMIZER:
        route = host._route(LLM_TASK_OPTIMIZE, complexity)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=to_role,
            text=host._clip(f"{host._role_label(to_role)} · {route.model}"),
            model=route.model,
        )
        out = []
        async for ev in host._optimize(
            board.user_text,
            board.compose_text,
            stop,
            max_tokens,
            route.gateway(),
            out,
        ):
            yield ev
        if not out:
            return
        result = out[0]
        board.optimize_text = result.text
        ref = host._put_artifact(mcp, to_role, "optimize", result.text)
        result.summary = host._summary_of(mcp, ref, result.text, role=to_role)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=to_role,
            text=result.summary,
            artifact_ref=ref,
            model=route.model,
        )
        yield OrchestratorEvent(kind="role_end", role=to_role, text=result.text)
