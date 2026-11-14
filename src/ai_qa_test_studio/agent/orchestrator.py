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
from ai_qa_test_studio.agent.llm_gateway import ChatMessage, UnifiedLLMGateway
from ai_qa_test_studio.agent.model_router import ModelRouter
from ai_qa_test_studio.agent.spec_generator import SpecGenerator
from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    APPLY_KIND_TEST_DATA,
    CLARIFY_SOURCE_DEFAULT_SKIP,
    CLARIFY_SOURCE_DEFAULT_TIMEOUT,
    CLARIFY_SOURCE_STOPPED,
    CLARIFY_SOURCE_USER,
    CONTEXT_KEY_ANALYZER_ENABLED,
    CONTEXT_KEY_ATTACHMENTS,
    CONTEXT_KEY_OPTIMIZER_ENABLED,
    CONTEXT_KEY_PROJECT_ROOT,
    CONTEXT_KEY_SKILL_BODY,
    CONTEXT_KEY_SKILL_CATALOG,
    CONTEXT_KEY_SKILL_NAME,
    COPILOT_AGENT_DEBATE_MAX_ROUNDS,
    COPILOT_AGENT_LOOP_REPEAT_LIMIT,
    COPILOT_ATTACH_TOTAL_CHARS,
    COPILOT_CHAT_MAX_TOKENS,
    COPILOT_MODE_COMPOSE,
    COPILOT_ROLE_REPLY_STYLE,
    COPILOT_SUMMARY_MAX_CHARS,
    DEFAULT_EXPECTED_OUTCOME,
    EVENT_AGENT_MESSAGE,
    EVENT_CHUNK,
    EVENT_CLARIFY_REQUEST,
    EVENT_CLARIFY_RESOLVED,
    EVENT_LOOP_HALTED,
    EVENT_STATUS_SUMMARY,
    LLM_PROVIDER_OFFLINE,
    LLM_TASK_ANALYZE,
    LLM_TASK_CLARIFY_DRAFT,
    LLM_TASK_COMPOSE,
    LLM_TASK_CRITIQUE,
    LLM_TASK_DEBATE,
    LLM_TASK_OPTIMIZE,
    LLM_TASK_PLAN,
    LLM_TASK_REVISE,
    LLM_TASK_SUMMARIZE,
    MCP_QA_CREW_MEMORY,
    MSG_STATUS_STOPPED,
    ROLE_ANALYZER,
    ROLE_COMPOSER,
    ROLE_CRITIC,
    ROLE_OPTIMIZER,
    ROLE_PLANNER,
    SKILL_CONTEXT_MAX_CHARS,
)
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
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_PLANNER,
            text=self._clip(f"Phân loại độ phức tạp: {complexity}"),
        )

        # --- Planner ---
        plan_route = self._route(LLM_TASK_PLAN, complexity)
        yield OrchestratorEvent(kind="role_start", role=ROLE_PLANNER)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_PLANNER,
            text=self._clip(f"Planner · {complexity} · {plan_route.model}"),
            model=plan_route.model,
        )
        plan_out: list[_RoleResult] = []
        async for ev in self._plan(
            user_text, mode, ctx_block, hist, stop, max_tokens, plan_route.gateway(), plan_out
        ):
            yield ev
        if stop.is_set():
            yield OrchestratorEvent(kind="stopped", role=ROLE_PLANNER, status=MSG_STATUS_STOPPED)
            return
        plan = plan_out[0]
        plan_ref = self._put_artifact(mcp, ROLE_PLANNER, "plan", plan.text)
        plan.artifact_ref = plan_ref
        plan.summary = self._summary_of(mcp, plan_ref, plan.text, role=ROLE_PLANNER)
        async for ev in self._maybe_clarify(ROLE_PLANNER, plan, user_text, complexity, stop, max_tokens):
            if ev.kind == EVENT_CLARIFY_RESOLVED and ev.text:
                user_text = f"{user_text}\n\n[Clarify] {ev.text}"
            yield ev
            if stop.is_set():
                return
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_PLANNER,
            text=plan.summary,
            artifact_ref=plan_ref,
            model=plan_route.model,
        )
        yield OrchestratorEvent(kind="role_end", role=ROLE_PLANNER, text=plan.text)

        # --- Composer ---
        compose_route = self._route(LLM_TASK_COMPOSE, complexity)
        yield OrchestratorEvent(kind="role_start", role=ROLE_COMPOSER)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_COMPOSER,
            text=self._clip(f"Composer · {complexity} · {compose_route.model}"),
            model=compose_route.model,
        )
        composed_out: list[_RoleResult] = []
        async for ev in self._compose(
            user_text,
            mode,
            ctx_block,
            plan.text,
            hist,
            stop,
            max_tokens,
            compose_route.gateway(),
            composed_out,
        ):
            yield ev
        if stop.is_set():
            yield OrchestratorEvent(kind="stopped", role=ROLE_COMPOSER, status=MSG_STATUS_STOPPED)
            return
        composed = composed_out[0]
        composed_ref = self._put_artifact(mcp, ROLE_COMPOSER, "compose", composed.text)
        composed.artifact_ref = composed_ref
        composed.summary = self._summary_of(mcp, composed_ref, composed.text, role=ROLE_COMPOSER)
        async for ev in self._maybe_clarify(ROLE_COMPOSER, composed, user_text, complexity, stop, max_tokens):
            if ev.kind == EVENT_CLARIFY_RESOLVED and ev.text:
                user_text = f"{user_text}\n\n[Clarify] {ev.text}"
            yield ev
            if stop.is_set():
                return
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_COMPOSER,
            text=composed.summary,
            artifact_ref=composed_ref,
            model=compose_route.model,
        )
        if composed.apply_payload:
            yield OrchestratorEvent(
                kind="apply_ready",
                role=ROLE_COMPOSER,
                apply_payload=composed.apply_payload,
                text=composed.summary,
            )
        yield OrchestratorEvent(kind="role_end", role=ROLE_COMPOSER, text=composed.text)

        # --- Critic ---
        if stop.is_set():
            yield OrchestratorEvent(kind="stopped", role=ROLE_COMPOSER, status=MSG_STATUS_STOPPED)
            return
        critique_route = self._route(LLM_TASK_CRITIQUE, complexity)
        yield OrchestratorEvent(kind="role_start", role=ROLE_CRITIC)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_CRITIC,
            text=self._clip(f"Critic · {complexity} · {critique_route.model}"),
            model=critique_route.model,
        )
        critique_out: list[_RoleResult] = []
        async for ev in self._critique(
            user_text, composed.text, stop, max_tokens, critique_route.gateway(), critique_out
        ):
            yield ev
        if stop.is_set():
            yield OrchestratorEvent(kind="stopped", role=ROLE_CRITIC, status=MSG_STATUS_STOPPED)
            return
        critique = critique_out[0]
        critique_ref = self._put_artifact(mcp, ROLE_CRITIC, "critique", critique.text)
        critique.summary = self._summary_of(mcp, critique_ref, critique.text, role=ROLE_CRITIC)
        async for ev in self._maybe_clarify(ROLE_CRITIC, critique, user_text, complexity, stop, max_tokens):
            if ev.kind == EVENT_CLARIFY_RESOLVED and ev.text:
                user_text = f"{user_text}\n\n[Clarify] {ev.text}"
            yield ev
            if stop.is_set():
                return
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_CRITIC,
            text=critique.summary,
            artifact_ref=critique_ref,
            model=critique_route.model,
        )
        yield OrchestratorEvent(kind="role_end", role=ROLE_CRITIC, text=critique.text)

        # Bounded debate when not approved
        if not critique.approved and critique.revise_notes:
            async for ev in self._debate(
                user_text=user_text,
                composed_text=composed.text,
                critique_notes=critique.revise_notes,
                complexity=complexity,
                stop=stop,
                max_tokens=max_tokens,
                mcp=mcp,
            ):
                yield ev
                if stop.is_set():
                    return
                if ev.kind == EVENT_LOOP_HALTED:
                    break
            # Single revise after debate / notes
            revise_route = self._route(LLM_TASK_REVISE, complexity)
            yield OrchestratorEvent(kind="role_start", role=ROLE_COMPOSER)
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=ROLE_COMPOSER,
                text=self._clip(f"Composer revise · {revise_route.model}"),
                model=revise_route.model,
            )
            revised_out: list[_RoleResult] = []
            async for ev in self._compose(
                user_text,
                mode,
                ctx_block,
                plan.text + "\n\nCritic notes:\n" + critique.revise_notes,
                hist,
                stop,
                max_tokens,
                revise_route.gateway(),
                revised_out,
                revise=True,
            ):
                yield ev
            if stop.is_set():
                yield OrchestratorEvent(kind="stopped", role=ROLE_COMPOSER, status=MSG_STATUS_STOPPED)
                return
            revised = revised_out[0]
            rev_ref = self._put_artifact(mcp, ROLE_COMPOSER, "revise", revised.text)
            revised.summary = self._summary_of(mcp, rev_ref, revised.text, role=ROLE_COMPOSER)
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=ROLE_COMPOSER,
                text=revised.summary,
                artifact_ref=rev_ref,
                model=revise_route.model,
            )
            if revised.apply_payload:
                yield OrchestratorEvent(
                    kind="apply_ready",
                    role=ROLE_COMPOSER,
                    apply_payload=revised.apply_payload,
                    text=revised.summary,
                )
            yield OrchestratorEvent(kind="role_end", role=ROLE_COMPOSER, text=revised.text)

        final_compose = composed.text
        if bool(context.get(CONTEXT_KEY_ANALYZER_ENABLED)):
            analyze_route = self._route(LLM_TASK_ANALYZE, complexity)
            yield OrchestratorEvent(kind="role_start", role=ROLE_ANALYZER)
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=ROLE_ANALYZER,
                text=self._clip(f"Analyzer · {analyze_route.model}"),
                model=analyze_route.model,
            )
            analyze_out: list[_RoleResult] = []
            async for ev in self._analyze(
                user_text, final_compose, critique.text, stop, max_tokens, analyze_route.gateway(), analyze_out
            ):
                yield ev
            if stop.is_set():
                yield OrchestratorEvent(kind="stopped", role=ROLE_ANALYZER, status=MSG_STATUS_STOPPED)
                return
            analysis = analyze_out[0]
            analysis_ref = self._put_artifact(mcp, ROLE_ANALYZER, "analysis", analysis.text)
            analysis.summary = self._summary_of(mcp, analysis_ref, analysis.text, role=ROLE_ANALYZER)
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=ROLE_ANALYZER,
                text=analysis.summary,
                artifact_ref=analysis_ref,
                model=analyze_route.model,
            )
            yield OrchestratorEvent(kind="role_end", role=ROLE_ANALYZER, text=analysis.text)

        if bool(context.get(CONTEXT_KEY_OPTIMIZER_ENABLED)):
            optimize_route = self._route(LLM_TASK_OPTIMIZE, complexity)
            yield OrchestratorEvent(kind="role_start", role=ROLE_OPTIMIZER)
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=ROLE_OPTIMIZER,
                text=self._clip(f"Optimizer · {optimize_route.model}"),
                model=optimize_route.model,
            )
            optimize_out: list[_RoleResult] = []
            async for ev in self._optimize(
                user_text, final_compose, stop, max_tokens, optimize_route.gateway(), optimize_out
            ):
                yield ev
            if stop.is_set():
                yield OrchestratorEvent(kind="stopped", role=ROLE_OPTIMIZER, status=MSG_STATUS_STOPPED)
                return
            optimized = optimize_out[0]
            opt_ref = self._put_artifact(mcp, ROLE_OPTIMIZER, "optimize", optimized.text)
            optimized.summary = self._summary_of(mcp, opt_ref, optimized.text, role=ROLE_OPTIMIZER)
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=ROLE_OPTIMIZER,
                text=optimized.summary,
                artifact_ref=opt_ref,
                model=optimize_route.model,
            )
            yield OrchestratorEvent(kind="role_end", role=ROLE_OPTIMIZER, text=optimized.text)

        yield OrchestratorEvent(kind="pipeline_complete")

    def _route(self, task: str, complexity: str) -> Any:
        if self.router is not None:
            return self.router.resolve(task, complexity)
        return _FallbackRoute(self.gateway, task, complexity)

    def _gw(self, task: str, complexity: str) -> UnifiedLLMGateway:
        route = self._route(task, complexity)
        gw: UnifiedLLMGateway = route.gateway()
        return gw

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
    ) -> AsyncIterator[OrchestratorEvent]:
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
        markers = ("chưa rõ", "unclear", "ambiguous", "cần làm rõ", "???")
        if any(m in blob for m in markers):
            return True
        return len(user_text.strip()) < 8 and "?" in user_text

    async def _draft_clarify(
        self,
        role: str,
        user_text: str,
        role_text: str,
        complexity: str,
        stop: asyncio.Event,
        max_tokens: int,
    ) -> dict[str, Any]:
        gw = self._gw(LLM_TASK_CLARIFY_DRAFT, complexity)
        system = (
            "Draft one clarification question for a QA user. "
            "Reply JSON: {\"question\":\"...\",\"suggestions\":[\"a\",\"b\"],\"default\":\"a\"}"
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
        if parsed:
            suggestions = [str(s) for s in (parsed.get("suggestions") or [])][:4]
            fallback = "Tiếp tục với giả định mặc định"
            default = str(parsed.get("default") or (suggestions[0] if suggestions else fallback))
            return {
                "question": str(parsed.get("question") or "Bạn muốn làm rõ yêu cầu thế nào?"),
                "suggestions": suggestions or [fallback, "Bổ sung chi tiết sau"],
                "default": default,
            }
        return {
            "question": "Yêu cầu còn điểm mờ — bạn muốn tiếp tục theo hướng nào?",
            "suggestions": ["Tiếp tục với giả định mặc định", "Bổ sung chi tiết sau"],
            "default": "Tiếp tục với giả định mặc định",
        }

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
                "Reply with one short resolution note under 120 words. "
                "If stuck repeating, say LOOP."
            )
            messages = [
                ChatMessage(role="system", content=system),
                ChatMessage(
                    role="user",
                    content=(
                        f"Round {round_i + 1}/{self.debate_max_rounds}\n"
                        f"Request:{user_text}\nComposer:{composed_text[:600]}\nCritic:{notes}"
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
    ) -> AsyncIterator[OrchestratorEvent]:
        system = (
            "You are the Planner role for AI QA Test Studio. "
            "Output a short numbered plan (3-5 steps). Do not mutate test assets. "
            f"{COPILOT_ROLE_REPLY_STYLE} "
            "If the request is ambiguous, include the word CLARIFY."
        )
        messages = [
            ChatMessage(role="system", content=system),
            *hist[-6:],
            ChatMessage(role="user", content=f"Mode={mode}\n{ctx_block}\n\nRequest:\n{user_text}"),
        ]
        sink: list[str] = []
        async for ev in self._collect(gw, messages, stop, max_tokens, sink, role=ROLE_PLANNER):
            yield ev
        text = "".join(sink).strip()
        if not text:
            steps = [
                "1. Làm rõ mục tiêu từ yêu cầu người dùng",
                "2. Composer soạn artifact / draft",
                "3. Critic rà soát 1 lần (BVA / UNBOUND / mâu thuẫn)",
            ]
            if mode == COPILOT_MODE_COMPOSE:
                steps.insert(1, "1b. Sinh outline scenario UNBOUND từ SRS/mô tả")
            text = "Kế hoạch:\n" + "\n".join(steps)
        needs = "CLARIFY" in text.upper() or self._looks_ambiguous(user_text)
        out.append(_RoleResult(text=text, needs_clarify=needs))

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
    ) -> AsyncIterator[OrchestratorEvent]:
        lowered = user_text.lower()
        wants_bva = any(k in lowered for k in ("bva", "test data", "biên", "bộ dữ liệu"))
        wants_compose = mode == COPILOT_MODE_COMPOSE or any(
            k in lowered for k in ("tạo kịch bản", "srs", "scenario", "draft")
        )
        system = (
            "You are the Composer role. Produce the deliverable for the QA request. "
            "If proposing test data, include a compact JSON block with key rows. "
            "If proposing scenarios, list code + title only for Apply. "
            f"{COPILOT_ROLE_REPLY_STYLE} "
            "If ambiguous, include the word CLARIFY."
        )
        messages = [
            ChatMessage(role="system", content=system),
            *hist[-6:],
            ChatMessage(
                role="user",
                content=f"Plan:\n{plan_text}\n\nContext:\n{ctx_block}\n\nRequest:\n{user_text}",
            ),
        ]
        sink: list[str] = []
        async for ev in self._collect(gw, messages, stop, max_tokens, sink, role=ROLE_COMPOSER):
            yield ev
        text = "".join(sink).strip()
        apply_payload = None
        if wants_bva and mode != COPILOT_MODE_COMPOSE:
            rows = self._offline_bva_rows()
            apply_payload = {"kind": APPLY_KIND_TEST_DATA, "rows": rows}
            if not text or gw.provider == LLM_PROVIDER_OFFLINE:
                text = f"Đề xuất {len(rows)} bộ Test Data BVA — bấm Apply để thêm."
        elif wants_compose:
            srs = ""
            if "SRS:" in ctx_block:
                srs = ctx_block.split("SRS:", 1)[-1].strip()
            srs = srs or user_text
            scenarios = self.generator.generate_outline_sync(srs)
            for sc in scenarios:
                sc.cases = self.generator.generate_cases_sync(sc, batch_size=2)
            payload_scenarios = [s.model_dump(mode="json") for s in scenarios]
            apply_payload = {"kind": APPLY_KIND_COMPOSE, "scenarios": payload_scenarios}
            if not text or gw.provider == LLM_PROVIDER_OFFLINE:
                names = ", ".join(s.code for s in scenarios[:5])
                body = f"Draft UNBOUND: {len(scenarios)} scenario ({names}) — Apply để thêm."
                text = ("[Revised] " + body) if revise else body
        if not text:
            text = (
                f"{'[Revised] ' if revise else ''}"
                f"Đã xử lý yêu cầu «{user_text[:80]}» — xem Apply nếu có artifact."
            )
        needs = "CLARIFY" in text.upper()
        out.append(_RoleResult(text=text, apply_payload=apply_payload, needs_clarify=needs))

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
            "You are the Analyzer for AI QA Test Studio. "
            "Diagnose failure risks, root causes, and healing hints. "
            f"{COPILOT_ROLE_REPLY_STYLE}"
        )
        messages = [
            ChatMessage(role="system", content=system),
            ChatMessage(
                role="user",
                content=(
                    f"Request:\n{user_text}\n\nComposer:\n{composer_text[:1200]}\n\n"
                    f"Critic:\n{critique_text[:800]}"
                ),
            ),
        ]
        sink: list[str] = []
        async for ev in self._collect(gw, messages, stop, max_tokens, sink, role=ROLE_ANALYZER):
            yield ev
        text = "".join(sink).strip()
        if not text or gw.provider == LLM_PROVIDER_OFFLINE:
            text = (
                "Analyzer: ưu tiên kiểm tra locator stale, timing/networkidle, "
                "và assertion nghiệp vụ trước khi heal lại."
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
            "You are the Optimizer for AI QA Test Studio. "
            "Suggest suite pruning, P0 prioritization, and coverage gaps. "
            f"{COPILOT_ROLE_REPLY_STYLE}"
        )
        messages = [
            ChatMessage(role="system", content=system),
            ChatMessage(
                role="user",
                content=f"Request:\n{user_text}\n\nComposer:\n{composer_text[:1200]}",
            ),
        ]
        sink: list[str] = []
        async for ev in self._collect(gw, messages, stop, max_tokens, sink, role=ROLE_OPTIMIZER):
            yield ev
        text = "".join(sink).strip()
        if not text or gw.provider == LLM_PROVIDER_OFFLINE:
            text = (
                "Optimizer: giữ P0 happy-path + 1 negative; "
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
    ) -> AsyncIterator[OrchestratorEvent]:
        system = (
            "You are the Critic. Reply with APPROVE or REVISE and short notes. "
            "One review only. "
            f"{COPILOT_ROLE_REPLY_STYLE} "
            "If ambiguous user intent, include CLARIFY."
        )
        messages = [
            ChatMessage(role="system", content=system),
            ChatMessage(
                role="user",
                content=f"Request:\n{user_text}\n\nComposer output:\n{composer_text}",
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
                        text="Critic: cần chỉnh nhẹ.\n- " + "\n- ".join(notes),
                        approved=False,
                        revise_notes="\n".join(notes),
                    )
                )
                return
            out.append(
                _RoleResult(text="Critic: APPROVE — không phát hiện lỗ hổng lớn.", approved=True)
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
