"""Coverage Pass orchestration (extracted from CopilotOrchestrator)."""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator, Sequence
from typing import Any

from ai_qa_test_studio.agent.bundled_mcp import BundledMcpRegistry
from ai_qa_test_studio.agent.crew_exchange import CrewBoard
from ai_qa_test_studio.agent.llm_gateway import ChatMessage
from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    CONTEXT_KEY_ATTACHMENTS,
    CONTEXT_KEY_DOC_IDS,
    CONTEXT_KEY_PROJECT_ROOT,
    COPILOT_ARTIFACT_CTA_COMPOSE,
    EVENT_AGENT_MESSAGE,
    EVENT_CHUNK,
    EVENT_STATUS_SUMMARY,
    INTENT_COMPOSE,
    LLM_TASK_COMPOSE,
    MSG_STATUS_STOPPED,
    ROLE_COMPOSER,
    ROLE_COORDINATOR,
)
from ai_qa_test_studio.core.doc_coverage import plan_coverage_windows
from ai_qa_test_studio.core.models import TestScenario


def doc_ids_from_context(context: dict[str, Any]) -> list[str]:
    ids: list[str] = []
    raw = context.get(CONTEXT_KEY_DOC_IDS)
    if isinstance(raw, list):
        ids.extend(str(i) for i in raw if str(i).strip())
    attachments = context.get(CONTEXT_KEY_ATTACHMENTS) or []
    if isinstance(attachments, list):
        for item in attachments:
            if not isinstance(item, dict):
                continue
            doc_id = str(item.get("doc_id") or "").strip()
            if doc_id and doc_id not in ids:
                ids.append(doc_id)
    return ids


def _dedupe_scenarios(scenarios: Sequence[TestScenario]) -> list[TestScenario]:
    by_code: dict[str, TestScenario] = {}
    order: list[str] = []
    for scenario in scenarios:
        key = (scenario.code or scenario.id).strip().upper()
        if key not in by_code:
            by_code[key] = scenario
            order.append(key)
            continue
        existing = by_code[key]
        if not existing.cases and scenario.cases:
            by_code[key] = scenario
    return [by_code[k] for k in order]


def _ingest_window_scenarios(
    *,
    payload: dict[str, Any],
    section_title: str,
    code_prefix: str,
    merged: list[TestScenario],
) -> int:
    """Append validated scenarios; return how many were added."""
    added = 0
    for raw in payload.get("scenarios") or []:
        try:
            scenario = TestScenario.model_validate(raw)
        except Exception:  # noqa: BLE001
            continue
        if not scenario.code:
            scenario.code = f"{code_prefix}_{len(merged) + 1:02d}"
        else:
            scenario.code = f"{code_prefix}_{scenario.code}"
        if section_title and not scenario.requirement_ref:
            scenario.requirement_ref = section_title
        merged.append(scenario)
        added += 1
    return added


async def _emit_coverage_result(
    host: Any,
    *,
    board: CrewBoard,
    mcp: BundledMcpRegistry,
    route: Any,
    unique: list[TestScenario],
    summary: str,
    section_notes: list[str],
    early_stop: bool,
) -> AsyncIterator[Any]:
    from ai_qa_test_studio.agent.orchestrator import OrchestratorEvent

    if not unique:
        board.compose_text = summary
        board.apply_payload = {"kind": APPLY_KIND_COMPOSE, "scenarios": []}
        board.mark_compose(revising=False)
        board.intent = INTENT_COMPOSE
        board.record_report(ROLE_COMPOSER, summary)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_COMPOSER,
            text=host._clip(summary),
            model=route.model,
        )
        yield OrchestratorEvent(kind="role_end", role=ROLE_COMPOSER, text=summary)
        return

    text = host._with_artifact_cta(
        summary + "\n" + "\n".join(f"- {n}" for n in section_notes[:12]),
        COPILOT_ARTIFACT_CTA_COMPOSE.format(count=len(unique)),
    )
    board.compose_text = text
    board.apply_payload = {
        "kind": APPLY_KIND_COMPOSE,
        "scenarios": [s.model_dump(mode="json") for s in unique],
    }
    board.mark_compose(revising=False)
    board.intent = INTENT_COMPOSE
    ref = host._put_artifact(mcp, ROLE_COMPOSER, "compose", text)
    yield OrchestratorEvent(
        kind=EVENT_STATUS_SUMMARY,
        role=ROLE_COMPOSER,
        text=host._summary_of(mcp, ref, text, role=ROLE_COMPOSER),
        artifact_ref=ref,
        model=route.model,
    )
    yield OrchestratorEvent(
        kind="apply_ready",
        role=ROLE_COMPOSER,
        apply_payload=board.apply_payload,
        text=text,
    )
    yield OrchestratorEvent(kind="role_end", role=ROLE_COMPOSER, text=text)
    if not early_stop:
        yield OrchestratorEvent(
            kind=EVENT_AGENT_MESSAGE,
            role=ROLE_COMPOSER,
            from_role=ROLE_COMPOSER,
            to_role=ROLE_COORDINATOR,
            text=host._clip(
                host._handoff(
                    ROLE_COMPOSER,
                    ROLE_COORDINATOR,
                    goal="Báo cáo coverage",
                    acceptance="Đủ mục đã soạn",
                    body=summary,
                )
            ),
        )
    board.record_report(ROLE_COMPOSER, summary)


async def run_coverage_pass(
    host: Any,
    *,
    board: CrewBoard,
    context: dict[str, Any],
    hist: list[ChatMessage],
    stop: asyncio.Event,
    max_tokens: int,
    mcp: BundledMcpRegistry,
    complexity: str,
    complexity_vn: str,
) -> AsyncIterator[Any]:
    """Section-map large attached docs and compose every window before Critic."""
    from ai_qa_test_studio.agent.orchestrator import OrchestratorEvent, _RoleResult

    if board.intent != INTENT_COMPOSE:
        return
    doc_ids = doc_ids_from_context(context)
    if not doc_ids:
        return
    root = str(context.get(CONTEXT_KEY_PROJECT_ROOT) or "").strip() or None

    # Single SQLite load: threshold gate + section windows.
    plan = plan_coverage_windows(root, doc_ids)
    if plan is None:
        return

    sections = plan.sections
    section_windows = plan.section_windows
    total_windows = plan.total_windows
    covered_chunks = plan.covered_chunks
    total_chunks = plan.total_chunks

    yield OrchestratorEvent(
        kind=EVENT_STATUS_SUMMARY,
        role=ROLE_COORDINATOR,
        text=host._clip(
            f"Coverage Pass: {len(sections)} mục · {total_windows} cửa sổ · "
            f"{covered_chunks}/{total_chunks} chunks"
        ),
    )
    yield OrchestratorEvent(kind="role_start", role=ROLE_COMPOSER)
    route = host._route(LLM_TASK_COMPOSE, complexity)
    yield OrchestratorEvent(
        kind=EVENT_STATUS_SUMMARY,
        role=ROLE_COMPOSER,
        text=host._clip(
            f"{host._role_label(ROLE_COMPOSER)} · coverage · {complexity_vn} · {route.model}"
        ),
        model=route.model,
    )

    merged_scenarios: list[TestScenario] = []
    section_notes: list[str] = []
    window_done = 0
    for section_index, (section, windows) in enumerate(section_windows, start=1):
        for window in windows:
            if stop.is_set():
                if merged_scenarios:
                    unique = _dedupe_scenarios(merged_scenarios)
                    case_count = sum(len(s.cases) for s in unique)
                    summary = (
                        f"Coverage dừng sớm {window_done}/{total_windows} cửa sổ · "
                        f"{covered_chunks}/{total_chunks} chunks → "
                        f"{len(unique)} scenario / {case_count} case (partial)."
                    )
                    async for ev in _emit_coverage_result(
                        host,
                        board=board,
                        mcp=mcp,
                        route=route,
                        unique=unique,
                        summary=summary,
                        section_notes=section_notes,
                        early_stop=True,
                    ):
                        yield ev
                yield OrchestratorEvent(
                    kind="stopped", role=ROLE_COMPOSER, status=MSG_STATUS_STOPPED
                )
                return
            window_done += 1
            win_label = (
                f"{section.section_id} w{window.window_index}/{window.window_count}"
            )
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=ROLE_COORDINATOR,
                text=host._clip(
                    f"Cửa sổ {window_done}/{total_windows} · mục "
                    f"{section_index}/{len(sections)}: {win_label} · {section.title}"
                ),
            )
            body = window.body
            section_ctx = f"{board.ctx_block}\n\nSECTION:\n{body}"
            handoff = host._handoff(
                ROLE_COORDINATOR,
                ROLE_COMPOSER,
                goal=f"Soạn kịch bản cho {win_label}: {section.title}",
                acceptance="Scenario/case UNBOUND cho đúng cửa sổ này",
                body=body,
            )
            out: list[_RoleResult] = []
            async for ev in host._compose(
                board.user_text,
                board.mode,
                section_ctx,
                handoff,
                hist,
                stop,
                max_tokens,
                route.gateway(),
                out,
                revise=False,
                intent_hint=INTENT_COMPOSE,
            ):
                if ev.kind == EVENT_CHUNK:
                    continue
                yield ev
            if not out:
                continue
            result = out[0]
            payload = result.apply_payload or {}
            code_prefix = (
                f"{section.section_id}_w{window.window_index}"
                if window.window_count > 1
                else section.section_id
            )
            added = _ingest_window_scenarios(
                payload=payload,
                section_title=section.title,
                code_prefix=code_prefix,
                merged=merged_scenarios,
            )
            if added:
                section_notes.append(f"{win_label} {section.title}: ok")
            board.dispatched.append(ROLE_COMPOSER)

    if not merged_scenarios:
        summary = (
            f"Coverage Pass: 0 scenario sau {window_done}/{total_windows} cửa sổ · "
            f"{covered_chunks}/{total_chunks} chunks — không tạo được kịch bản từ tài liệu."
        )
        async for ev in _emit_coverage_result(
            host,
            board=board,
            mcp=mcp,
            route=route,
            unique=[],
            summary=summary,
            section_notes=section_notes,
            early_stop=False,
        ):
            yield ev
        return

    unique = _dedupe_scenarios(merged_scenarios)
    case_count = sum(len(s.cases) for s in unique)
    summary = (
        f"Coverage xong {window_done}/{total_windows} cửa sổ · "
        f"{covered_chunks}/{total_chunks} chunks · {len(sections)} mục → "
        f"{len(unique)} scenario / {case_count} case — Tải Excel hoặc Apply."
    )
    async for ev in _emit_coverage_result(
        host,
        board=board,
        mcp=mcp,
        route=route,
        unique=unique,
        summary=summary,
        section_notes=section_notes,
        early_stop=False,
    ):
        yield ev
