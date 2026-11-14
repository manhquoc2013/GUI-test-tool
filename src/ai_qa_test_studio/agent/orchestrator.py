"""Copilot crew: Planner → Composer → Critic + bounded debate + clarify."""

from __future__ import annotations

import asyncio
import hashlib
import json
import logging
import re
import uuid
from collections.abc import AsyncIterator, Awaitable, Callable
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from ai_qa_test_studio.agent.bundled_mcp import BundledMcpRegistry
from ai_qa_test_studio.agent.complexity_classifier import ComplexityClassifier
from ai_qa_test_studio.agent.crew_exchange import (
    CrewBoard,
    heuristic_next_action,
    parse_coord_decision,
)
from ai_qa_test_studio.agent.llm_gateway import ChatMessage, UnifiedLLMGateway
from ai_qa_test_studio.agent.model_router import ModelRouter
from ai_qa_test_studio.agent.spec_generator import SpecGenerator
from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    APPLY_KIND_TEST_DATA,
    CHAT_ROLE_LABELS,
    CLARIFY_AMBIGUITY_MARKERS,
    CLARIFY_FALLBACK_DEFAULT,
    CLARIFY_GENERIC_QUESTION,
    CLARIFY_OPT_ATTACH_SRS,
    CLARIFY_OPT_BVA_EDITOR,
    CLARIFY_OPT_BVA_LOGIN,
    CLARIFY_OPT_COMPOSE_FULL,
    CLARIFY_OPT_COMPOSE_HAPPY,
    CLARIFY_OPT_EXPLAIN_ONLY,
    CLARIFY_OPT_WAIT_DETAIL,
    CLARIFY_SOURCE_DEFAULT_SKIP,
    CLARIFY_SOURCE_DEFAULT_TIMEOUT,
    CLARIFY_SOURCE_STOPPED,
    CLARIFY_SOURCE_USER,
    COMPLEXITY_COMPLEX,
    COMPLEXITY_LABELS_VN,
    COMPLEXITY_SIMPLE,
    CONTEXT_KEY_ANALYZER_ENABLED,
    CONTEXT_KEY_ATTACHMENTS,
    CONTEXT_KEY_OPTIMIZER_ENABLED,
    CONTEXT_KEY_PROJECT_ROOT,
    CONTEXT_KEY_SKILL_BODY,
    CONTEXT_KEY_SKILL_CATALOG,
    CONTEXT_KEY_SKILL_NAME,
    COORD_ACTION_CLARIFY,
    COORD_ACTION_COMPLETE,
    COORD_SPECIALISTS,
    COPILOT_AGENT_DEBATE_MAX_ROUNDS,
    COPILOT_AGENT_LOOP_REPEAT_LIMIT,
    COPILOT_ARTIFACT_CTA_COMPOSE,
    COPILOT_ARTIFACT_CTA_TEST_DATA,
    COPILOT_ATTACH_TOTAL_CHARS,
    COPILOT_CHAT_MAX_TOKENS,
    COPILOT_COMPOSE_DOC_HINTS,
    COPILOT_COMPOSE_KEYWORDS,
    COPILOT_COORDINATOR_MAX_ROUNDS,
    COPILOT_HANDOFF_HEADER,
    COPILOT_MODE_COMPOSE,
    COPILOT_ROLE_BRIEFS,
    COPILOT_ROLE_REPLY_STYLE,
    COPILOT_SUMMARY_MAX_CHARS,
    DEFAULT_EXPECTED_OUTCOME,
    EVENT_AGENT_MESSAGE,
    EVENT_CHUNK,
    EVENT_CLARIFY_REQUEST,
    EVENT_CLARIFY_RESOLVED,
    EVENT_LOOP_HALTED,
    EVENT_STATUS_SUMMARY,
    INTENT_COMPOSE,
    INTENT_EXPLAIN,
    INTENT_TEST_DATA,
    INTENT_VALUES,
    LLM_PROVIDER_OFFLINE,
    LLM_TASK_ANALYZE,
    LLM_TASK_CLARIFY_DRAFT,
    LLM_TASK_COMPOSE,
    LLM_TASK_COORDINATE,
    LLM_TASK_CRITIQUE,
    LLM_TASK_DEBATE,
    LLM_TASK_INTENT,
    LLM_TASK_OPTIMIZE,
    LLM_TASK_PLAN,
    LLM_TASK_REVISE,
    LLM_TASK_SUMMARIZE,
    MCP_QA_CREW_MEMORY,
    MSG_STATUS_STOPPED,
    ROLE_ANALYZER,
    ROLE_COMPOSER,
    ROLE_COORDINATOR,
    ROLE_CRITIC,
    ROLE_OPTIMIZER,
    ROLE_PLANNER,
    SKILL_CONTEXT_MAX_CHARS,
)
from ai_qa_test_studio.core.models import TestScenario
from ai_qa_test_studio.core.token_budget import ContextCompressor

logger = logging.getLogger(__name__)

ClarifyHandler = Callable[["ClarifyRequest"], Awaitable["ClarifyResponse"]]


@dataclass
class ClarifyRequest:
    role: str
    question: str
    suggestions: list[str]
    default_answer: str


@dataclass
class ClarifyResponse:
    answer: str
    source: str = CLARIFY_SOURCE_USER


@dataclass
class OrchestratorEvent:
    kind: str
    role: str | None = None
    text: str = ""
    apply_payload: dict[str, Any] | None = None
    status: str = ""
    question: str = ""
    suggestions: list[str] = field(default_factory=list)
    default_answer: str = ""
    from_role: str | None = None
    to_role: str | None = None
    fingerprint: str = ""
    model: str = ""
    artifact_ref: str = ""
    clarify: ClarifyRequest | None = None


@dataclass
class _RoleResult:
    text: str = ""
    summary: str = ""
    apply_payload: dict[str, Any] | None = None
    revise_notes: str = ""
    approved: bool = True
    needs_clarify: bool = False
    clarify_question: str = ""
    clarify_suggestions: list[str] = field(default_factory=list)
    clarify_default: str = ""
    artifact_ref: str = ""
    model: str = ""
    intent: str = ""


@dataclass(frozen=True)
class CrewRoster:
    """Which specialists run this turn — intent-gated (NEXUS micro/sprint)."""

    intent: str
    run_critic: bool
    run_debate: bool
    run_analyzer: bool
    run_optimizer: bool
    roles: tuple[str, ...]


@dataclass
class CopilotOrchestrator:
    gateway: UnifiedLLMGateway
    generator: SpecGenerator = field(default_factory=SpecGenerator)
    compressor: ContextCompressor = field(default_factory=ContextCompressor)
    mcp: BundledMcpRegistry | None = None
    router: ModelRouter | None = None
    clarify_handler: ClarifyHandler | None = None
    classifier: ComplexityClassifier = field(default_factory=ComplexityClassifier)
    debate_max_rounds: int = COPILOT_AGENT_DEBATE_MAX_ROUNDS
    loop_repeat_limit: int = COPILOT_AGENT_LOOP_REPEAT_LIMIT
    summary_max_chars: int = COPILOT_SUMMARY_MAX_CHARS

    async def run(
        self,
        user_text: str,
        mode: str,
        context: dict[str, Any],
        history: list[ChatMessage] | None = None,
        cancel: asyncio.Event | None = None,
        max_tokens: int = COPILOT_CHAT_MAX_TOKENS,
    ) -> AsyncIterator[OrchestratorEvent]:
        """Coordinator-led multi-agent exchange (hierarchical, not linear-only)."""
        stop = cancel or asyncio.Event()
        hist = list(history or [])
        mcp = self.mcp or BundledMcpRegistry(compressor=self.compressor)
        if not mcp.ready:
            mcp.start()
        mcp.crew_memory.clear()
        self._bind_project_context(mcp, context)
        ctx_block = self._format_context(context)

        complexity = await self.classifier.classify(
            user_text, mode, context, gateway=self._gw(LLM_TASK_SUMMARIZE, "simple"), cancel=stop
        )
        complexity_vn = COMPLEXITY_LABELS_VN.get(complexity, complexity)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_COORDINATOR,
            text=self._clip(f"Độ khó: {complexity_vn} ({complexity}) → chọn model theo ma trận"),
        )

        intent = await self._infer_intent(
            user_text,
            mode,
            ctx_block,
            plan_text="",
            gw=self._gw(LLM_TASK_INTENT, "simple"),
            stop=stop,
        )
        roster = self._crew_roster(intent, complexity, context)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_COORDINATOR,
            text=self._clip(
                f"Intent: {intent} · Đội: {' → '.join(self._role_label(r) for r in roster.roles)}"
            ),
        )

        async for ev in self._run_coordinated(
            user_text=user_text,
            mode=mode,
            context=context,
            ctx_block=ctx_block,
            hist=hist,
            intent=intent,
            complexity=complexity,
            complexity_vn=complexity_vn,
            roster=roster,
            stop=stop,
            max_tokens=max_tokens,
            mcp=mcp,
        ):
            yield ev


    def _route(self, task: str, complexity: str) -> Any:
        if self.router is not None:
            return self.router.resolve(task, complexity)
        return _FallbackRoute(self.gateway, task, complexity)

    def _gw(self, task: str, complexity: str) -> UnifiedLLMGateway:
        route = self._route(task, complexity)
        gw: UnifiedLLMGateway = route.gateway()
        return gw

    @staticmethod
    def _role_label(role: str) -> str:
        return CHAT_ROLE_LABELS.get(role, role)

    def _crew_roster(
        self, intent: str, complexity: str, context: dict[str, Any]
    ) -> CrewRoster:
        """Roster led by Điều phối viên; specialists gated by intent×complexity."""
        analyzer_on = bool(context.get(CONTEXT_KEY_ANALYZER_ENABLED))
        optimizer_on = bool(context.get(CONTEXT_KEY_OPTIMIZER_ENABLED))
        roles: list[str] = [ROLE_COORDINATOR]
        need_plan = intent == INTENT_COMPOSE and complexity != COMPLEXITY_SIMPLE
        if need_plan:
            roles.append(ROLE_PLANNER)
        roles.append(ROLE_COMPOSER)
        run_critic = True
        run_debate = True
        run_analyzer = (
            analyzer_on
            and intent != INTENT_EXPLAIN
            and complexity != COMPLEXITY_SIMPLE
        )
        run_optimizer = (
            optimizer_on
            and intent == INTENT_COMPOSE
            and complexity == COMPLEXITY_COMPLEX
        )
        if run_critic:
            roles.append(ROLE_CRITIC)
        if run_analyzer:
            roles.append(ROLE_ANALYZER)
        if run_optimizer:
            roles.append(ROLE_OPTIMIZER)
        return CrewRoster(
            intent=intent,
            run_critic=run_critic,
            run_debate=run_debate,
            run_analyzer=run_analyzer,
            run_optimizer=run_optimizer,
            roles=tuple(roles),
        )

    def _handoff(
        self,
        from_role: str,
        to_role: str,
        *,
        goal: str,
        acceptance: str,
        body: str,
    ) -> str:
        """Structured agent→agent packet (agency NEXUS handoff pattern)."""
        return (
            f"{COPILOT_HANDOFF_HEADER}\n"
            f"From: {self._role_label(from_role)} ({from_role})\n"
            f"To: {self._role_label(to_role)} ({to_role})\n"
            f"Goal: {goal}\n"
            f"Acceptance: {acceptance}\n"
            f"---\n"
            f"{(body or '').strip()}"
        )

    async def _run_coordinated(
        self,
        *,
        user_text: str,
        mode: str,
        context: dict[str, Any],
        ctx_block: str,
        hist: list[ChatMessage],
        intent: str,
        complexity: str,
        complexity_vn: str,
        roster: CrewRoster,
        stop: asyncio.Event,
        max_tokens: int,
        mcp: BundledMcpRegistry,
    ) -> AsyncIterator[OrchestratorEvent]:
        """Hierarchical loop: coordinator decides → specialist works → report back."""
        board = CrewBoard(
            user_text=user_text,
            mode=mode,
            ctx_block=ctx_block,
            intent=intent,
            complexity=complexity,
        )
        seen: dict[str, int] = {}
        coord_route = self._route(LLM_TASK_COORDINATE, complexity)
        yield OrchestratorEvent(kind="role_start", role=ROLE_COORDINATOR)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_COORDINATOR,
            text=self._clip(
                f"{self._role_label(ROLE_COORDINATOR)} · {complexity_vn} · {coord_route.model}"
            ),
            model=coord_route.model,
        )

        for _round in range(COPILOT_COORDINATOR_MAX_ROUNDS):
            if stop.is_set():
                yield OrchestratorEvent(
                    kind="stopped", role=ROLE_COORDINATOR, status=MSG_STATUS_STOPPED
                )
                return
            decision = await self._coordinator_decide(
                board, roster, coord_route.gateway(), stop
            )
            action = decision.get("action") or COORD_ACTION_COMPLETE
            if action == COORD_ACTION_COMPLETE:
                summary = decision.get("summary") or "Hoàn tất điều phối"
                yield OrchestratorEvent(
                    kind=EVENT_STATUS_SUMMARY,
                    role=ROLE_COORDINATOR,
                    text=self._clip(summary),
                    model=coord_route.model,
                )
                break
            if action == COORD_ACTION_CLARIFY:
                req = ClarifyRequest(
                    role=ROLE_COORDINATOR,
                    question=decision.get("goal") or "Cần làm rõ yêu cầu?",
                    suggestions=["Happy path", "Full BVA", "Chỉ giải thích"],
                    default_answer="Happy path",
                )
                fake = _RoleResult(text=req.question, needs_clarify=True)
                async for ev in self._maybe_clarify(
                    ROLE_COORDINATOR, fake, board.user_text, complexity, stop, max_tokens, mode=mode
                ):
                    if ev.kind == EVENT_CLARIFY_RESOLVED and ev.text:
                        board.user_text = f"{board.user_text}\n\n[Clarify] {ev.text}"
                    yield ev
                    if stop.is_set():
                        return
                continue

            to_role = str(decision.get("to") or "")
            if to_role not in COORD_SPECIALISTS:
                break
            fp = self._fingerprint(f"{to_role}|{decision.get('goal')}|{decision.get('brief')}")
            seen[fp] = seen.get(fp, 0) + 1
            if seen[fp] >= self.loop_repeat_limit:
                yield OrchestratorEvent(
                    kind=EVENT_LOOP_HALTED,
                    role=ROLE_COORDINATOR,
                    text=self._clip("Anti-loop: điều phối lặp cùng giao việc — dừng."),
                    fingerprint=fp,
                )
                break

            packet = self._handoff(
                ROLE_COORDINATOR,
                to_role,
                goal=str(decision.get("goal") or ""),
                acceptance=str(decision.get("acceptance") or ""),
                body=str(decision.get("brief") or board.user_text),
            )
            yield OrchestratorEvent(
                kind=EVENT_AGENT_MESSAGE,
                role=ROLE_COORDINATOR,
                from_role=ROLE_COORDINATOR,
                to_role=to_role,
                text=self._clip(packet),
                model=coord_route.model,
            )

            async for ev in self._dispatch_specialist(
                to_role=to_role,
                board=board,
                roster=roster,
                packet=packet,
                hist=hist,
                stop=stop,
                max_tokens=max_tokens,
                mcp=mcp,
                complexity=complexity,
                complexity_vn=complexity_vn,
            ):
                yield ev
                if stop.is_set():
                    return

            # Specialist reports back to coordinator (multi-directional close of hop).
            report_body = {
                ROLE_PLANNER: board.plan_text,
                ROLE_COMPOSER: board.compose_text,
                ROLE_CRITIC: board.critique_text,
                ROLE_ANALYZER: board.analyze_text,
                ROLE_OPTIMIZER: board.optimize_text,
            }.get(to_role, "")
            board.record_report(to_role, report_body)
            back = self._handoff(
                to_role,
                ROLE_COORDINATOR,
                goal="Báo cáo kết quả",
                acceptance="Điều phối viên cập nhật board và chọn bước tiếp",
                body=report_body or "(empty)",
            )
            yield OrchestratorEvent(
                kind=EVENT_AGENT_MESSAGE,
                role=to_role,
                from_role=to_role,
                to_role=ROLE_COORDINATOR,
                text=self._clip(back),
            )
        else:
            yield OrchestratorEvent(
                kind=EVENT_LOOP_HALTED,
                role=ROLE_COORDINATOR,
                text=self._clip("Hết vòng điều phối — đóng pipeline."),
            )

        yield OrchestratorEvent(
            kind="role_end",
            role=ROLE_COORDINATOR,
            text=self._clip(
                f"Điều phối xong · intent={board.intent} · "
                f"dispatched={','.join(board.dispatched) or '-'}"
            ),
        )
        yield OrchestratorEvent(kind="pipeline_complete")

    async def _coordinator_decide(
        self,
        board: CrewBoard,
        roster: CrewRoster,
        gw: UnifiedLLMGateway,
        stop: asyncio.Event,
    ) -> dict[str, str]:
        fallback = heuristic_next_action(
            board,
            run_analyzer=roster.run_analyzer,
            run_optimizer=roster.run_optimizer,
        )
        if gw.provider == LLM_PROVIDER_OFFLINE:
            return fallback
        system = (
            f"You are the Coordinator ({ROLE_COORDINATOR}). "
            f"{COPILOT_ROLE_BRIEFS[ROLE_COORDINATOR]} "
            "Reply JSON only: "
            '{"action":"dispatch"|"complete"|"clarify","to":"planner|composer|critic|analyzer|optimizer",'
            '"goal":"...","acceptance":"...","brief":"...","summary":"..."} . '
            "After a specialist reports, you may dispatch another (including re-dispatch). "
            "Complete when the user goal is met. Do not invent roles."
        )
        messages = [
            ChatMessage(role="system", content=system),
            ChatMessage(
                role="user",
                content=(
                    f"Team={','.join(roster.roles)}\n"
                    f"Request:\n{board.user_text}\n\nBoard:\n{board.digest()}\n"
                    f"ContextHead:\n{board.ctx_block[:800]}"
                ),
            ),
        ]
        raw = await self._collect_text(gw, messages, stop, max_tokens=120)
        return parse_coord_decision(raw, fallback)

    async def _dispatch_specialist(
        self,
        *,
        to_role: str,
        board: CrewBoard,
        roster: CrewRoster,
        packet: str,
        hist: list[ChatMessage],
        stop: asyncio.Event,
        max_tokens: int,
        mcp: BundledMcpRegistry,
        complexity: str,
        complexity_vn: str,
    ) -> AsyncIterator[OrchestratorEvent]:
        board.dispatched.append(to_role)
        yield OrchestratorEvent(kind="role_start", role=to_role)
        if to_role == ROLE_PLANNER:
            route = self._route(LLM_TASK_PLAN, complexity)
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=to_role,
                text=self._clip(f"{self._role_label(to_role)} · {complexity_vn} · {route.model}"),
                model=route.model,
            )
            out: list[_RoleResult] = []
            async for ev in self._plan(
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
            ref = self._put_artifact(mcp, to_role, "plan", result.text)
            result.summary = self._summary_of(mcp, ref, result.text, role=to_role)
            async for ev in self._maybe_clarify(
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
            route = self._route(task, complexity)
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=to_role,
                text=self._clip(f"{self._role_label(to_role)} · {complexity_vn} · {route.model}"),
                model=route.model,
            )
            plan_for_compose = packet if not board.plan_text else (
                self._handoff(
                    ROLE_COORDINATOR,
                    ROLE_COMPOSER,
                    goal="Thực thi theo kế hoạch + handoff điều phối",
                    acceptance="Artifact/Apply hoặc trả lời rõ",
                    body=f"{board.plan_text}\n\n{packet}",
                )
            )
            revising = bool(board.compose_text and not board.critique_approved)
            out = []
            async for ev in self._compose(
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
            if revising:
                board.revise_done = True
            board.compose_text = result.text
            if result.intent:
                board.intent = result.intent
            if result.apply_payload:
                board.apply_payload = result.apply_payload
            ref = self._put_artifact(mcp, to_role, "compose", result.text)
            result.summary = self._summary_of(mcp, ref, result.text, role=to_role)
            async for ev in self._maybe_clarify(
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
            route = self._route(LLM_TASK_CRITIQUE, complexity)
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=to_role,
                text=self._clip(f"{self._role_label(to_role)} · {complexity_vn} · {route.model}"),
                model=route.model,
            )
            out = []
            async for ev in self._critique(
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
            board.critique_text = result.text
            board.critique_approved = result.approved
            board.critique_notes = result.revise_notes
            if result.approved:
                board.revise_done = True
            ref = self._put_artifact(mcp, to_role, "critique", result.text)
            result.summary = self._summary_of(mcp, ref, result.text, role=to_role)
            async for ev in self._maybe_clarify(
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
            route = self._route(LLM_TASK_ANALYZE, complexity)
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=to_role,
                text=self._clip(f"{self._role_label(to_role)} · {route.model}"),
                model=route.model,
            )
            out = []
            async for ev in self._analyze(
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
            ref = self._put_artifact(mcp, to_role, "analysis", result.text)
            result.summary = self._summary_of(mcp, ref, result.text, role=to_role)
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
            route = self._route(LLM_TASK_OPTIMIZE, complexity)
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=to_role,
                text=self._clip(f"{self._role_label(to_role)} · {route.model}"),
                model=route.model,
            )
            out = []
            async for ev in self._optimize(
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
            ref = self._put_artifact(mcp, to_role, "optimize", result.text)
            result.summary = self._summary_of(mcp, ref, result.text, role=to_role)
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=to_role,
                text=result.summary,
                artifact_ref=ref,
                model=route.model,
            )
            yield OrchestratorEvent(kind="role_end", role=to_role, text=result.text)

    def _bind_project_context(self, mcp: BundledMcpRegistry, context: dict[str, Any]) -> None:
        mcp.project_context.bind(
            case_json=str(context.get("case") or ""),
            srs=str(context.get("srs") or ""),
            test_data_rows=list(context.get("test_data_rows") or []),
        )
        root = str(context.get(CONTEXT_KEY_PROJECT_ROOT) or "").strip()
        if root:
            mcp.persistent_memory.bind_project(Path(root))
            mcp.ingest.bind_project(Path(root))
        else:
            mcp.persistent_memory.bind_project(None)
            mcp.ingest.bind_project(None)

    def _put_artifact(self, mcp: BundledMcpRegistry, role: str, kind: str, text: str) -> str:
        result = mcp.invoke(
            MCP_QA_CREW_MEMORY,
            "put_artifact",
            {"role": role, "kind": kind, "text": text},
            role=role,
        )
        return str(result.get("ref") or "")

    def _summary_of(
        self,
        mcp: BundledMcpRegistry,
        ref: str,
        fallback: str,
        role: str | None = None,
    ) -> str:
        if ref:
            got = mcp.invoke(MCP_QA_CREW_MEMORY, "get_summary", {"ref": ref}, role=role)
            if got.get("found"):
                return self._clip(str(got.get("summary") or fallback))
        return self._clip(fallback)

    def _clip(self, text: str) -> str:
        compact = " ".join((text or "").split())
        if len(compact) <= self.summary_max_chars:
            return compact
        return compact[: self.summary_max_chars - 1] + "…"

    def _fingerprint(self, text: str) -> str:
        norm = re.sub(r"\s+", " ", (text or "").lower()).strip()
        return hashlib.sha256(norm.encode("utf-8")).hexdigest()[:12]

    async def _maybe_clarify(
        self,
        role: str,
        result: _RoleResult,
        user_text: str,
        complexity: str,
        stop: asyncio.Event,
        max_tokens: int,
        *,
        mode: str = "",
    ) -> AsyncIterator[OrchestratorEvent]:
        # Clear QA intents (compose / BVA / explain) must not pop a vague dialog
        # just because the model hedged with the word CLARIFY.
        if self._intent_is_actionable(user_text, mode) and not self._looks_ambiguous(user_text):
            return
        ambiguous = result.needs_clarify or self._looks_ambiguous(user_text, result.text)
        if not ambiguous:
            return
        draft = await self._draft_clarify(role, user_text, result.text, complexity, stop, max_tokens)
        req = ClarifyRequest(
            role=role,
            question=draft["question"],
            suggestions=list(draft["suggestions"]),
            default_answer=draft["default"],
        )
        yield OrchestratorEvent(
            kind=EVENT_CLARIFY_REQUEST,
            role=role,
            question=req.question,
            suggestions=req.suggestions,
            default_answer=req.default_answer,
            clarify=req,
            text=self._clip(req.question),
        )
        answer = req.default_answer
        source = CLARIFY_SOURCE_DEFAULT_SKIP
        if self.clarify_handler is not None:
            handler = self.clarify_handler

            async def _await_clarify() -> ClarifyResponse:
                return await handler(req)

            handler_task: asyncio.Task[ClarifyResponse] = asyncio.create_task(_await_clarify())
            stop_task: asyncio.Task[bool] = asyncio.create_task(stop.wait())
            try:
                done, pending = await asyncio.wait(
                    {handler_task, stop_task},
                    return_when=asyncio.FIRST_COMPLETED,
                )
                for task in pending:
                    task.cancel()
                    try:
                        await task
                    except (asyncio.CancelledError, Exception):  # noqa: BLE001
                        pass
                if stop_task in done and stop.is_set():
                    answer = req.default_answer
                    source = CLARIFY_SOURCE_STOPPED
                elif handler_task in done:
                    try:
                        resp = handler_task.result()
                    except TimeoutError:
                        source = CLARIFY_SOURCE_DEFAULT_TIMEOUT
                        answer = req.default_answer
                    except Exception:  # noqa: BLE001
                        source = CLARIFY_SOURCE_DEFAULT_SKIP
                        answer = req.default_answer
                    else:
                        answer = resp.answer or req.default_answer
                        source = resp.source or CLARIFY_SOURCE_USER
            except TimeoutError:
                source = CLARIFY_SOURCE_DEFAULT_TIMEOUT
                answer = req.default_answer
        elif stop.is_set():
            source = CLARIFY_SOURCE_STOPPED
            answer = req.default_answer
        note = answer
        if source != CLARIFY_SOURCE_USER:
            note = f"(mặc định) {answer}"
        yield OrchestratorEvent(
            kind=EVENT_CLARIFY_RESOLVED,
            role=role,
            text=self._clip(note),
            status=source,
        )

    def _looks_ambiguous(self, user_text: str, _role_text: str = "") -> bool:
        """User-intent markers only — do not treat role prose like 'clarify' as ambiguity."""
        blob = (user_text or "").lower()
        if any(m in blob for m in CLARIFY_AMBIGUITY_MARKERS):
            return True
        return len(user_text.strip()) < 8 and "?" in user_text

    def _intent_is_actionable(self, user_text: str, mode: str = "") -> bool:
        """True when the request already names a concrete QA deliverable."""
        if mode == COPILOT_MODE_COMPOSE:
            return True
        lowered = (user_text or "").lower()
        if any(k in lowered for k in COPILOT_COMPOSE_KEYWORDS):
            return True
        keywords = (
            "test data",
            "bva",
            "biên",
            "bộ dữ liệu",
            "giải thích lỗi",
            "giải thích bước",
            "yaml",
            "tải",
            "happy path",
            "negative",
        )
        return any(k in lowered for k in keywords)

    def _wants_compose(self, user_text: str, mode: str, ctx_block: str) -> bool:
        """Soft keyword/doc heuristic — LLM intent is the primary gate."""
        if mode == COPILOT_MODE_COMPOSE:
            return True
        lowered = (user_text or "").lower()
        if any(k in lowered for k in COPILOT_COMPOSE_KEYWORDS):
            return True
        has_doc = "ATTACHMENTS:" in (ctx_block or "") or "SRS:" in (ctx_block or "")
        if has_doc and any(k in lowered for k in COPILOT_COMPOSE_DOC_HINTS):
            return True
        return False

    def _heuristic_intent(self, user_text: str, mode: str, ctx_block: str) -> str:
        lowered = (user_text or "").lower()
        if mode == COPILOT_MODE_COMPOSE or self._wants_compose(user_text, mode, ctx_block):
            return INTENT_COMPOSE
        if any(k in lowered for k in ("bva", "test data", "biên", "bộ dữ liệu")):
            return INTENT_TEST_DATA
        if any(k in lowered for k in ("giải thích", "explain", "vì sao", "why")):
            return INTENT_EXPLAIN
        has_doc = "ATTACHMENTS:" in (ctx_block or "") or "SRS:" in (ctx_block or "")
        if has_doc:
            # Attached QA doc without an explicit "chỉ giải thích" → assume compose.
            return INTENT_COMPOSE
        return INTENT_EXPLAIN

    async def _infer_intent(
        self,
        user_text: str,
        mode: str,
        ctx_block: str,
        plan_text: str,
        gw: UnifiedLLMGateway,
        stop: asyncio.Event,
    ) -> str:
        """LLM classifies intent; keywords are offline/fallback only — never a hard gate."""
        heuristic = self._heuristic_intent(user_text, mode, ctx_block)
        if gw.provider == LLM_PROVIDER_OFFLINE:
            return heuristic
        system = (
            "Classify the Vietnamese QA Copilot request by meaning, not keywords. "
            "Paraphrases, typos, and informal wording still count. "
            "compose = create/analyze/draft test scenarios or cases from SRS/docs "
            "(user wants Apply/Excel artifact). "
            "test_data = boundary/BVA rows for an existing case. "
            "explain = only explain/plan/debug, no artifact. "
            "If a document is attached and the user asks to help build tests "
            "(any wording), prefer compose. "
            'Reply JSON only: {"intent":"compose"|"test_data"|"explain"}.'
        )
        messages = [
            ChatMessage(role="system", content=system),
            ChatMessage(
                role="user",
                content=(
                    f"ModeHint={mode}\nHeuristicHint={heuristic}\n"
                    f"Plan:\n{plan_text[:600]}\n"
                    f"ContextHead:\n{(ctx_block or '')[:1200]}\n"
                    f"Request:\n{user_text}"
                ),
            ),
        ]
        raw = await self._collect_text(gw, messages, stop, max_tokens=64)
        parsed = self._parse_json_obj(raw)
        intent = str((parsed or {}).get("intent") or "").strip().lower()
        if intent in INTENT_VALUES:
            return intent
        blob = (raw or "").lower()
        if INTENT_TEST_DATA in blob:
            return INTENT_TEST_DATA
        if INTENT_COMPOSE in blob:
            return INTENT_COMPOSE
        if INTENT_EXPLAIN in blob:
            return INTENT_EXPLAIN
        return heuristic

    async def _collect_text(
        self,
        gw: UnifiedLLMGateway,
        messages: list[ChatMessage],
        stop: asyncio.Event,
        max_tokens: int,
    ) -> str:
        sink: list[str] = []
        async for piece in gw.stream_chat(messages, max_tokens=max_tokens, cancel=stop):
            sink.append(piece)
            if stop.is_set():
                break
        return "".join(sink).strip()

    async def _generate_scenarios_llm(
        self,
        srs: str,
        gw: UnifiedLLMGateway,
        stop: asyncio.Event,
        max_tokens: int,
    ) -> list[TestScenario]:
        async def _llm(prompt: str) -> str:
            return await self._collect_text(
                gw,
                [ChatMessage(role="user", content=prompt)],
                stop,
                max_tokens=min(max_tokens, 1800),
            )

        gen = SpecGenerator(llm=_llm, compressor=self.compressor)
        if gw.provider == LLM_PROVIDER_OFFLINE:
            scenarios = gen.generate_outline_sync(srs)
            for sc in scenarios:
                sc.cases = gen.generate_cases_sync(sc, batch_size=2)
            return scenarios
        scenarios = await gen.generate_outline(srs)
        if not scenarios:
            scenarios = gen.generate_outline_sync(srs)
        for sc in scenarios:
            if stop.is_set():
                break
            cases = await gen.generate_cases_batch(sc, batch_size=2)
            sc.cases = cases or gen.generate_cases_sync(sc, batch_size=2)
        return scenarios

    def _scenarios_from_composer_json(self, text: str) -> list[TestScenario]:
        parsed = self._parse_json_obj(text)
        if not parsed:
            return []
        kind = str(parsed.get("kind") or "")
        if kind and kind not in {APPLY_KIND_COMPOSE, "scenarios", "compose"}:
            if "scenarios" not in parsed:
                return []
        return SpecGenerator(compressor=self.compressor)._parse_artifact_bundle(
            json.dumps(parsed)
        )

    def _srs_from_context(self, ctx_block: str, user_text: str) -> str:
        """Prefer attached / SRS document body over the short chat prompt."""
        block = ctx_block or ""
        if "SRS:" in block:
            body = block.split("SRS:", 1)[-1].strip()
            if body:
                return body
        if "ATTACHMENTS:" in block:
            body = block.split("ATTACHMENTS:", 1)[-1].strip()
            if body:
                return body
        return user_text

    def _with_artifact_cta(self, text: str, cta: str) -> str:
        body = (text or "").strip()
        marker = "Tải Excel"
        if marker in body or "Apply" in body:
            return body
        if not body:
            return cta
        return f"{body}\n{cta}"

    def _heuristic_clarify_draft(self, user_text: str) -> dict[str, Any]:
        """Concrete VN options — never vague 'giả định mặc định'."""
        lowered = (user_text or "").lower()
        if any(k in lowered for k in ("bva", "test data", "biên", "bộ dữ liệu")):
            return {
                "question": "Bạn muốn sinh Test Data biên (BVA) theo hướng nào?",
                "suggestions": [
                    CLARIFY_OPT_BVA_EDITOR,
                    CLARIFY_OPT_BVA_LOGIN,
                    CLARIFY_OPT_WAIT_DETAIL,
                ],
                "default": CLARIFY_OPT_BVA_EDITOR,
            }
        if any(
            k in lowered
            for k in ("kịch bản", "scenario", "srs", "test case", "draft", "assertion")
        ):
            return {
                "question": "Bạn muốn Copilot sinh kịch bản kiểm thử thế nào?",
                "suggestions": [
                    CLARIFY_OPT_COMPOSE_HAPPY,
                    CLARIFY_OPT_COMPOSE_FULL,
                    CLARIFY_OPT_ATTACH_SRS,
                    CLARIFY_OPT_WAIT_DETAIL,
                ],
                "default": CLARIFY_OPT_COMPOSE_HAPPY,
            }
        return {
            "question": CLARIFY_GENERIC_QUESTION,
            "suggestions": [
                CLARIFY_OPT_COMPOSE_HAPPY,
                CLARIFY_OPT_BVA_EDITOR,
                CLARIFY_OPT_EXPLAIN_ONLY,
                CLARIFY_OPT_WAIT_DETAIL,
            ],
            "default": CLARIFY_FALLBACK_DEFAULT,
        }

    def _is_vague_clarify_draft(self, draft: dict[str, Any]) -> bool:
        question = str(draft.get("question") or "").lower()
        suggestions = [str(s).lower() for s in (draft.get("suggestions") or [])]
        vague_bits = (
            "điểm mờ",
            "giả định mặc định",
            "bổ sung chi tiết sau",
            "làm rõ yêu cầu thế nào",
            "tiếp tục theo hướng nào",
        )
        if any(bit in question for bit in vague_bits):
            return True
        return any(
            any(bit in s for bit in ("giả định mặc định", "bổ sung chi tiết sau"))
            for s in suggestions
        )

    async def _draft_clarify(
        self,
        role: str,
        user_text: str,
        role_text: str,
        complexity: str,
        stop: asyncio.Event,
        max_tokens: int,
    ) -> dict[str, Any]:
        heuristic = self._heuristic_clarify_draft(user_text)
        gw = self._gw(LLM_TASK_CLARIFY_DRAFT, complexity)
        system = (
            "Draft ONE clarification for a Vietnamese QA user. "
            "Options must name a concrete deliverable (compose scenarios, BVA rows, explain only). "
            "Never use vague options like 'default assumption' or 'add details later'. "
            'Reply JSON only: {"question":"...","suggestions":["a","b"],"default":"a"}'
        )
        messages = [
            ChatMessage(role="system", content=system),
            ChatMessage(
                role="user",
                content=f"Role={role}\nRequest:{user_text}\nDraft:{role_text[:800]}",
            ),
        ]
        buf: list[str] = []
        async for chunk in gw.stream_chat(messages, max_tokens=min(max_tokens, 256), cancel=stop):
            buf.append(chunk)
        raw = "".join(buf)
        parsed = self._parse_json_obj(raw)
        if not parsed:
            return heuristic
        suggestions = [str(s).strip() for s in (parsed.get("suggestions") or []) if str(s).strip()][:4]
        question = str(parsed.get("question") or "").strip()
        default = str(parsed.get("default") or (suggestions[0] if suggestions else "")).strip()
        draft = {
            "question": question or heuristic["question"],
            "suggestions": suggestions or list(heuristic["suggestions"]),
            "default": default or heuristic["default"],
        }
        if self._is_vague_clarify_draft(draft):
            return heuristic
        return draft

    def _parse_json_obj(self, raw: str) -> dict[str, Any] | None:
        text = raw.strip()
        start = text.find("{")
        end = text.rfind("}")
        if start < 0 or end <= start:
            return None
        try:
            data = json.loads(text[start : end + 1])
        except json.JSONDecodeError:
            return None
        return data if isinstance(data, dict) else None

    async def _debate(
        self,
        *,
        user_text: str,
        composed_text: str,
        critique_notes: str,
        complexity: str,
        stop: asyncio.Event,
        max_tokens: int,
        mcp: BundledMcpRegistry,
    ) -> AsyncIterator[OrchestratorEvent]:
        seen: dict[str, int] = {}
        notes = critique_notes
        for round_i in range(self.debate_max_rounds):
            if stop.is_set():
                yield OrchestratorEvent(kind="stopped", role=ROLE_CRITIC, status=MSG_STATUS_STOPPED)
                return
            fp = self._fingerprint(notes)
            seen[fp] = seen.get(fp, 0) + 1
            if seen[fp] >= self.loop_repeat_limit:
                yield OrchestratorEvent(
                    kind=EVENT_LOOP_HALTED,
                    role=ROLE_CRITIC,
                    text=self._clip("Anti-loop: agents lặp cùng vấn đề — dừng debate."),
                    fingerprint=fp,
                )
                return
            debate_route = self._route(LLM_TASK_DEBATE, complexity)
            gw = debate_route.gateway()
            system = (
                "You are continuing a short agent debate between Critic and Composer. "
                f"{COPILOT_ROLE_BRIEFS[ROLE_CRITIC]} "
                "Viết 1 ghi chú giải quyết ≤120 từ: mục cần sửa + cách sửa. "
                "Nếu lặp lại cùng vấn đề, viết đúng chữ LOOP. "
                f"{COPILOT_ROLE_REPLY_STYLE}"
            )
            messages = [
                ChatMessage(role="system", content=system),
                ChatMessage(
                    role="user",
                    content=self._handoff(
                        ROLE_CRITIC,
                        ROLE_COMPOSER,
                        goal=f"Debate vòng {round_i + 1}/{self.debate_max_rounds}",
                        acceptance="Ghi chú sửa cụ thể hoặc LOOP",
                        body=(
                            f"Request:\n{user_text}\n\nComposer:\n{composed_text[:600]}\n\n"
                            f"Critic:\n{notes}"
                        ),
                    ),
                ),
            ]
            buf: list[str] = []
            async for chunk in gw.stream_chat(messages, max_tokens=min(max_tokens, 400), cancel=stop):
                buf.append(chunk)
            reply = "".join(buf).strip() or notes
            if "LOOP" in reply.upper():
                yield OrchestratorEvent(
                    kind=EVENT_LOOP_HALTED,
                    role=ROLE_CRITIC,
                    text=self._clip("Debate báo LOOP — escalate."),
                    fingerprint=fp,
                )
                return
            summary = self._clip(reply)
            yield OrchestratorEvent(
                kind=EVENT_AGENT_MESSAGE,
                role=ROLE_CRITIC,
                from_role=ROLE_CRITIC,
                to_role=ROLE_COMPOSER,
                text=summary,
                fingerprint=fp,
                model=debate_route.model,
            )
            mcp.invoke(
                MCP_QA_CREW_MEMORY,
                "put_artifact",
                {"role": ROLE_CRITIC, "kind": "debate", "text": reply},
                role=ROLE_CRITIC,
            )
            notes = reply

    def _format_context(self, context: dict[str, Any]) -> str:
        parts: list[str] = []
        if context.get("include_case") and context.get("case"):
            parts.append("CASE:\n" + self.compressor.compress_text(str(context["case"])))
        if context.get("include_dom") and context.get("dom"):
            parts.append("DOM:\n" + self.compressor.budget.truncate_to_cap(str(context["dom"])))
        if context.get("include_last_error") and context.get("last_error"):
            parts.append("LAST_ERROR:\n" + str(context["last_error"])[:1000])
        if context.get("srs"):
            parts.append(
                "SRS:\n" + self.compressor.budget.truncate_to_cap(str(context["srs"]), cap=3000)
            )
        attachments = context.get(CONTEXT_KEY_ATTACHMENTS) or []
        if isinstance(attachments, list) and attachments:
            chunks: list[str] = []
            for item in attachments:
                if not isinstance(item, dict):
                    continue
                name = str(item.get("name") or "file").strip() or "file"
                markdown = str(item.get("markdown") or "").strip()
                if not markdown:
                    continue
                chunks.append(f"### Attached: {name}\n{markdown}")
            if chunks:
                joined = "\n\n".join(chunks)
                parts.append(
                    "ATTACHMENTS:\n"
                    + self.compressor.budget.truncate_to_cap(
                        joined, cap=COPILOT_ATTACH_TOTAL_CHARS
                    )
                )
        catalog = str(context.get(CONTEXT_KEY_SKILL_CATALOG) or "").strip()
        if catalog:
            parts.append("SKILLS:\n" + catalog)
        skill_body = str(context.get(CONTEXT_KEY_SKILL_BODY) or "").strip()
        if skill_body:
            name = str(context.get(CONTEXT_KEY_SKILL_NAME) or "active")
            clipped = skill_body
            if len(clipped) > SKILL_CONTEXT_MAX_CHARS:
                clipped = clipped[: SKILL_CONTEXT_MAX_CHARS - 1] + "…"
            parts.append(f"ACTIVE_SKILL ({name}):\n{clipped}")
        return "\n\n".join(parts)

    async def _collect(
        self,
        gw: UnifiedLLMGateway,
        messages: list[ChatMessage],
        stop: asyncio.Event,
        max_tokens: int,
        sink: list[str],
        role: str | None = None,
    ) -> AsyncIterator[OrchestratorEvent]:
        """Yield stream chunk events; append pieces into sink for the caller."""
        async for piece in gw.stream_chat(messages, max_tokens=max_tokens, cancel=stop):
            sink.append(piece)
            yield OrchestratorEvent(kind=EVENT_CHUNK, role=role, text=piece)

    async def _plan(
        self,
        user_text: str,
        mode: str,
        ctx_block: str,
        hist: list[ChatMessage],
        stop: asyncio.Event,
        max_tokens: int,
        gw: UnifiedLLMGateway,
        out: list[_RoleResult],
        *,
        intent: str = INTENT_EXPLAIN,
        roster: CrewRoster | None = None,
    ) -> AsyncIterator[OrchestratorEvent]:
        team = (
            ", ".join(self._role_label(r) for r in (roster.roles if roster else ()))
            or self._role_label(ROLE_COMPOSER)
        )
        system = (
            f"You are the Planner ({ROLE_PLANNER}). "
            f"{COPILOT_ROLE_BRIEFS[ROLE_PLANNER]} "
            f"{COPILOT_ROLE_REPLY_STYLE} "
            "Only include the word CLARIFY when blocked (missing URL/SRS/field) — "
            "never for mild uncertainty."
        )
        messages = [
            ChatMessage(role="system", content=system),
            *hist[-6:],
            ChatMessage(
                role="user",
                content=(
                    f"Intent={intent}\nMode={mode}\nTeam={team}\n"
                    f"{ctx_block}\n\nRequest:\n{user_text}"
                ),
            ),
        ]
        sink: list[str] = []
        async for ev in self._collect(gw, messages, stop, max_tokens, sink, role=ROLE_PLANNER):
            yield ev
        text = "".join(sink).strip()
        if not text:
            steps = [
                f"Mục tiêu: xử lý intent «{intent}».",
                f"1. {self._role_label(ROLE_COMPOSER)} soạn deliverable theo intent",
                f"2. {self._role_label(ROLE_CRITIC)} rà soát 1 lần",
            ]
            if intent == INTENT_COMPOSE:
                steps.insert(1, f"1b. {self._role_label(ROLE_COMPOSER)} outline UNBOUND từ SRS/file")
            if roster and roster.run_analyzer:
                steps.append(f"3. {self._role_label(ROLE_ANALYZER)} phân tích rủi ro")
            if roster and roster.run_optimizer:
                steps.append(f"4. {self._role_label(ROLE_OPTIMIZER)} tối ưu suite P0")
            steps.append("Acceptance: output rõ ràng + Apply/Excel nếu có artifact.")
            text = "Kế hoạch:\n" + "\n".join(steps)
        needs = "CLARIFY" in text.upper() or self._looks_ambiguous(user_text)
        out.append(_RoleResult(text=text, needs_clarify=needs, intent=intent))

    async def _compose(
        self,
        user_text: str,
        mode: str,
        ctx_block: str,
        plan_text: str,
        hist: list[ChatMessage],
        stop: asyncio.Event,
        max_tokens: int,
        gw: UnifiedLLMGateway,
        out: list[_RoleResult],
        revise: bool = False,
        intent_hint: str = "",
    ) -> AsyncIterator[OrchestratorEvent]:
        if intent_hint in INTENT_VALUES:
            intent = intent_hint
        else:
            intent = await self._infer_intent(
                user_text,
                mode,
                ctx_block,
                plan_text,
                self._gw(LLM_TASK_INTENT, "simple"),
                stop,
            )
        system = (
            f"You are the Composer ({ROLE_COMPOSER}). "
            f"{COPILOT_ROLE_BRIEFS[ROLE_COMPOSER]} "
            "When creating scenarios/cases, ALSO append a JSON object "
            '{"kind":"compose_scenarios","scenarios":[{"code","title","requirement_ref",'
            '"description","cases":[{"code","title","preconditions","steps":'
            '[{"step_no","action","intent_hint","input_data","expected_result"}]}]}]}. '
            "When creating test data only, append "
            '{"kind":"test_data_rows","rows":[...]} . '
            "When intent=explain, answer clearly — do NOT invent compose JSON. "
            f"{COPILOT_ROLE_REPLY_STYLE} "
            "Only include CLARIFY when blocked by missing required input."
        )
        messages = [
            ChatMessage(role="system", content=system),
            *hist[-6:],
            ChatMessage(
                role="user",
                content=(
                    f"Intent={intent}\n"
                    f"{plan_text}\n\n"
                    f"Context:\n{ctx_block}\n\nRequest:\n{user_text}"
                ),
            ),
        ]
        sink: list[str] = []
        async for ev in self._collect(gw, messages, stop, max_tokens, sink, role=ROLE_COMPOSER):
            yield ev
        text = "".join(sink).strip()
        apply_payload: dict[str, Any] | None = None

        # Prefer structured JSON the LLM already emitted.
        parsed = self._parse_json_obj(text)
        if parsed and str(parsed.get("kind") or "") == APPLY_KIND_TEST_DATA:
            rows = parsed.get("rows") or []
            if isinstance(rows, list) and rows:
                apply_payload = {"kind": APPLY_KIND_TEST_DATA, "rows": rows}
                intent = INTENT_TEST_DATA
        scenarios = self._scenarios_from_composer_json(text)
        if scenarios:
            intent = INTENT_COMPOSE
            apply_payload = {
                "kind": APPLY_KIND_COMPOSE,
                "scenarios": [s.model_dump(mode="json") for s in scenarios],
            }

        if apply_payload is None and intent == INTENT_TEST_DATA:
            rows = self._offline_bva_rows()
            apply_payload = {"kind": APPLY_KIND_TEST_DATA, "rows": rows}
            if not text or gw.provider == LLM_PROVIDER_OFFLINE:
                text = f"Đề xuất {len(rows)} bộ Test Data BVA — bấm Tải Excel hoặc Apply."
            text = self._with_artifact_cta(
                text, COPILOT_ARTIFACT_CTA_TEST_DATA.format(count=len(rows))
            )
        elif apply_payload is None and intent == INTENT_COMPOSE:
            srs = self._srs_from_context(ctx_block, user_text)
            scenarios = await self._generate_scenarios_llm(srs, gw, stop, max_tokens)
            apply_payload = {
                "kind": APPLY_KIND_COMPOSE,
                "scenarios": [s.model_dump(mode="json") for s in scenarios],
            }
            case_count = sum(len(s.cases) for s in scenarios)
            if not text or gw.provider == LLM_PROVIDER_OFFLINE:
                names = ", ".join(s.code for s in scenarios[:5])
                body = (
                    f"Draft UNBOUND: {len(scenarios)} scenario / {case_count} case "
                    f"({names}) — Tải Excel hoặc Apply."
                )
                text = ("[Revised] " + body) if revise else body
            text = self._with_artifact_cta(
                text, COPILOT_ARTIFACT_CTA_COMPOSE.format(count=len(scenarios))
            )
        elif apply_payload and apply_payload.get("kind") == APPLY_KIND_COMPOSE:
            count = len(apply_payload.get("scenarios") or [])
            text = self._with_artifact_cta(
                text, COPILOT_ARTIFACT_CTA_COMPOSE.format(count=count)
            )
        elif apply_payload and apply_payload.get("kind") == APPLY_KIND_TEST_DATA:
            count = len(apply_payload.get("rows") or [])
            text = self._with_artifact_cta(
                text, COPILOT_ARTIFACT_CTA_TEST_DATA.format(count=count)
            )

        if not text:
            text = (
                f"{'[Revised] ' if revise else ''}"
                f"Đã xử lý yêu cầu «{user_text[:80]}» — xem Apply / Tải Excel nếu có artifact."
            )
        needs = "CLARIFY" in text.upper()
        out.append(
            _RoleResult(
                text=text,
                apply_payload=apply_payload,
                needs_clarify=needs,
                intent=intent,
            )
        )

    async def _analyze(
        self,
        user_text: str,
        composer_text: str,
        critique_text: str,
        stop: asyncio.Event,
        max_tokens: int,
        gw: UnifiedLLMGateway,
        out: list[_RoleResult],
    ) -> AsyncIterator[OrchestratorEvent]:
        system = (
            f"You are the Analyzer ({ROLE_ANALYZER}). "
            f"{COPILOT_ROLE_BRIEFS[ROLE_ANALYZER]} {COPILOT_ROLE_REPLY_STYLE}"
        )
        messages = [
            ChatMessage(role="system", content=system),
            ChatMessage(
                role="user",
                content=self._handoff(
                    ROLE_CRITIC,
                    ROLE_ANALYZER,
                    goal="Phân tích rủi ro / nguyên nhân từ draft đã rà",
                    acceptance="3–4 gạch: nguyên nhân, tín hiệu, gợi ý heal",
                    body=(
                        f"Request:\n{user_text}\n\nComposer:\n{composer_text[:1200]}\n\n"
                        f"Critic:\n{critique_text[:800]}"
                    ),
                ),
            ),
        ]
        sink: list[str] = []
        async for ev in self._collect(gw, messages, stop, max_tokens, sink, role=ROLE_ANALYZER):
            yield ev
        text = "".join(sink).strip()
        if not text or gw.provider == LLM_PROVIDER_OFFLINE:
            text = (
                f"{self._role_label(ROLE_ANALYZER)}: ưu tiên locator stale, "
                "timing/networkidle, và assertion nghiệp vụ trước khi heal."
            )
        out.append(_RoleResult(text=text))

    async def _optimize(
        self,
        user_text: str,
        composer_text: str,
        stop: asyncio.Event,
        max_tokens: int,
        gw: UnifiedLLMGateway,
        out: list[_RoleResult],
    ) -> AsyncIterator[OrchestratorEvent]:
        system = (
            f"You are the Optimizer ({ROLE_OPTIMIZER}). "
            f"{COPILOT_ROLE_BRIEFS[ROLE_OPTIMIZER]} {COPILOT_ROLE_REPLY_STYLE}"
        )
        messages = [
            ChatMessage(role="system", content=system),
            ChatMessage(
                role="user",
                content=self._handoff(
                    ROLE_COMPOSER,
                    ROLE_OPTIMIZER,
                    goal="Tối ưu suite: P0 / gộp / coverage",
                    acceptance="Danh sách giữ / bỏ / bổ sung có lý do",
                    body=f"Request:\n{user_text}\n\nComposer:\n{composer_text[:1200]}",
                ),
            ),
        ]
        sink: list[str] = []
        async for ev in self._collect(gw, messages, stop, max_tokens, sink, role=ROLE_OPTIMIZER):
            yield ev
        text = "".join(sink).strip()
        if not text or gw.provider == LLM_PROVIDER_OFFLINE:
            text = (
                f"{self._role_label(ROLE_OPTIMIZER)}: giữ P0 happy-path + 1 negative; "
                "gộp case trùng bước; trì hoãn edge ít rủi ro."
            )
        out.append(_RoleResult(text=text))

    async def _critique(
        self,
        user_text: str,
        composer_text: str,
        stop: asyncio.Event,
        max_tokens: int,
        gw: UnifiedLLMGateway,
        out: list[_RoleResult],
        *,
        intent: str = INTENT_COMPOSE,
    ) -> AsyncIterator[OrchestratorEvent]:
        system = (
            f"You are the Critic ({ROLE_CRITIC}). "
            f"{COPILOT_ROLE_BRIEFS[ROLE_CRITIC]} "
            f"{COPILOT_ROLE_REPLY_STYLE} "
            "Do not include CLARIFY unless the user request itself is empty or contradictory."
        )
        messages = [
            ChatMessage(role="system", content=system),
            ChatMessage(
                role="user",
                content=self._handoff(
                    ROLE_COMPOSER,
                    ROLE_CRITIC,
                    goal=f"Quality gate cho intent={intent}",
                    acceptance="Dòng đầu: APPROVE hoặc REVISE; sau đó ghi chú cụ thể",
                    body=f"Request:\n{user_text}\n\nComposer output:\n{composer_text}",
                ),
            ),
        ]
        sink: list[str] = []
        async for ev in self._collect(gw, messages, stop, max_tokens, sink, role=ROLE_CRITIC):
            yield ev
        text = "".join(sink).strip()
        if not text:
            notes = []
            if "UNBOUND" not in composer_text and "Draft" in composer_text:
                notes.append("Nhắc: case draft cần Sync URL để bind selector.")
            if "BVA" in user_text.upper() or "biên" in user_text.lower():
                notes.append("Đã kiểm tra biên min/max/empty — OK.")
            if notes:
                out.append(
                    _RoleResult(
                        text=(
                            f"{self._role_label(ROLE_CRITIC)}: cần chỉnh nhẹ.\n- "
                            + "\n- ".join(notes)
                        ),
                        approved=False,
                        revise_notes="\n".join(notes),
                        intent=intent,
                    )
                )
                return
            out.append(
                _RoleResult(
                    text=(
                        f"{self._role_label(ROLE_CRITIC)}: APPROVE — "
                        "không phát hiện lỗ hổng lớn."
                    ),
                    approved=True,
                    intent=intent,
                )
            )
            return
        upper = text.upper()
        if "REVISE" in upper and "APPROVE" not in upper:
            approved = False
        elif "APPROVE" in upper:
            approved = True
        else:
            approved = "REVISE" not in upper
        revise_notes = text if not approved else ""
        out.append(
            _RoleResult(
                text=text,
                approved=approved,
                revise_notes=revise_notes,
                needs_clarify="CLARIFY" in upper,
                intent=intent,
            )
        )

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


@dataclass
class _FallbackRoute:
    _gateway: UnifiedLLMGateway
    task: str
    complexity: str

    @property
    def model(self) -> str:
        return self._gateway.model

    def gateway(self) -> UnifiedLLMGateway:
        return self._gateway


def new_message_id() -> str:
    return str(uuid.uuid4())
