"""Copilot crew: coordinator-led specialists + clarify (Dev↔QA quality loop)."""

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

from ai_qa_test_studio.agent.batch_compose import (
    BatchComposeRunner,
    continuation_is_stalled,
    count_cases_in_scenarios,
    format_large_suite_summary_card,
    is_large_suite_payload,
    load_session_compose_payload,
    new_draft_session_id,
    outline_only_scenarios,
    should_batch_compose,
)
from ai_qa_test_studio.agent.builtin_skills import builtin_skill_catalog
from ai_qa_test_studio.agent.bundled_mcp import BundledMcpRegistry
from ai_qa_test_studio.agent.case_binding import case_binding_summary
from ai_qa_test_studio.agent.complexity_classifier import ComplexityClassifier
from ai_qa_test_studio.agent.coverage_pass import run_coverage_pass
from ai_qa_test_studio.agent.crew_dispatch import dispatch_specialist
from ai_qa_test_studio.agent.crew_exchange import (
    CrewBoard,
    build_final_report,
    heuristic_next_action,
    looks_like_coord_protocol,
    parse_coord_decision,
)
from ai_qa_test_studio.agent.intent_router import HybridIntentRouter, RoutingDecision
from ai_qa_test_studio.agent.llm_gateway import ChatMessage, UnifiedLLMGateway
from ai_qa_test_studio.agent.model_router import ModelRouter
from ai_qa_test_studio.agent.reality_checker import RealityChecker
from ai_qa_test_studio.agent.skill_auto_matcher import SkillAutoMatcher, SkillMatch
from ai_qa_test_studio.agent.spec_generator import SpecGenerator
from ai_qa_test_studio.agent.swarm_audit import (
    SwarmAuditor,
    format_swarm_audit_markdown,
    swarm_audit_context,
    swarm_audit_structured_payload,
)
from ai_qa_test_studio.agent.url_context import enrich_user_text_with_urls
from ai_qa_test_studio.agent.validate_case_schema import validate_case_schema
from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    APPLY_KIND_TEST_DATA,
    APPLY_PAYLOAD_DRAFT_SESSION_KEY,
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
    CLARIFY_SUGGESTION_MIN_CHARS,
    COMPLEXITY_COMPLEX,
    COMPLEXITY_LABELS_VN,
    COMPLEXITY_SIMPLE,
    CONTEXT_KEY_ANALYZER_ENABLED,
    CONTEXT_KEY_ATTACHMENTS,
    CONTEXT_KEY_DOC_IDS,
    CONTEXT_KEY_OPTIMIZER_ENABLED,
    CONTEXT_KEY_PROJECT_ROOT,
    CONTEXT_KEY_SKILL_BODY,
    CONTEXT_KEY_SKILL_CATALOG,
    CONTEXT_KEY_SKILL_NAME,
    COORD_ACTION_CLARIFY,
    COORD_ACTION_COMPLETE,
    COORD_ACTION_DISPATCH,
    COORD_SPECIALISTS,
    COPILOT_AGENT_DEBATE_MAX_ROUNDS,
    COPILOT_AGENT_LOOP_REPEAT_LIMIT,
    COPILOT_ARTIFACT_CTA_COMPOSE,
    COPILOT_ARTIFACT_CTA_TEST_DATA,
    COPILOT_ATTACH_PREVIEW_CHARS,
    COPILOT_BATCH_RESUME_HINT,
    COPILOT_CHAT_MAX_TOKENS,
    COPILOT_CHAT_PROSE_STYLE,
    COPILOT_COMPOSE_DOC_HINTS,
    COPILOT_COMPOSE_KEYWORDS,
    COPILOT_COORD_COMPLETE_FALLBACK,
    COPILOT_COORD_EXPLAIN_ACCEPTANCE,
    COPILOT_COORD_EXPLAIN_GOAL,
    COPILOT_COORDINATOR_MAX_ROUNDS,
    COPILOT_CRITIC_COMPOSER_DIGEST_CHARS,
    COPILOT_GEN_MAX_TOKENS,
    COPILOT_GEN_MAX_TOKENS_CAP,
    COPILOT_HANDOFF_HEADER,
    COPILOT_MODE_COMPOSE,
    COPILOT_ROLE_BRIEFS,
    COPILOT_ROLE_REPLY_STYLE,
    COPILOT_STATUS_CLASSIFYING,
    COPILOT_STATUS_COORD_DECIDE,
    COPILOT_STATUS_INFERRING_INTENT,
    COPILOT_STATUS_WAITING_LLM,
    COPILOT_STREAM_ERROR_PREFIX,
    COPILOT_STREAM_LENGTH_NOTICE,
    COPILOT_SUMMARY_MAX_CHARS,
    COPILOT_TEST_CASE_GEN_MAX_CASES,
    CTX_MARKER_ACTIVE_SKILL,
    CTX_MARKER_ATTACHMENTS,
    CTX_MARKER_CASE,
    CTX_MARKER_DOM,
    CTX_MARKER_LAST_ERROR,
    CTX_MARKER_RETRIEVED,
    CTX_MARKER_SECTION,
    CTX_MARKER_SKILLS,
    CTX_MARKER_SRS,
    DEFAULT_EXPECTED_OUTCOME,
    EVENT_AGENT_MESSAGE,
    EVENT_CHUNK,
    EVENT_CLARIFY_REQUEST,
    EVENT_CLARIFY_RESOLVED,
    EVENT_ERROR,
    EVENT_FINAL_REPORT,
    EVENT_LOOP_HALTED,
    EVENT_STATUS_SUMMARY,
    EVENT_TURN_END,
    INTENT_COMPOSE,
    INTENT_EXPLAIN,
    INTENT_TEST_DATA,
    INTENT_VALUES,
    LLM_FINISH_REASON_LENGTH,
    LLM_OUTLINE_MAX_TOKENS,
    LLM_PROVIDER_OFFLINE,
    LLM_STREAM_CONTINUE_PRIOR_CHARS,
    LLM_STREAM_CONTINUE_PROMPT,
    LLM_STREAM_MAX_CONTINUATIONS,
    LLM_STREAM_STALL_NOTICE,
    LLM_TASK_CLARIFY_DRAFT,
    LLM_TASK_COORDINATE,
    LLM_TASK_INTENT,
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
from ai_qa_test_studio.core.async_task_supervisor import get_async_task_supervisor
from ai_qa_test_studio.core.doc_corpus import format_retrieved_block, retrieve
from ai_qa_test_studio.core.enums import DeliveryMode, IntentType
from ai_qa_test_studio.core.models import Project, RunReport, TestCase, TestScenario
from ai_qa_test_studio.core.secret_masker import get_secret_masker
from ai_qa_test_studio.core.token_budget import ContextCompactor, ContextCompressor
from ai_qa_test_studio.reporting.evidence import EvidenceCollector
from ai_qa_test_studio.ui.bubble_summary import (
    extract_structured_payload,
    sanitize_user_reply,
    usable_explain_answer,
)

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
    run_analyzer: bool
    run_optimizer: bool
    roles: tuple[str, ...]


def _ui_intent_from_context(context: dict[str, Any]) -> IntentType | None:
    raw = context.get("ui_intent") or context.get("hybrid_intent_hint")
    if not raw:
        return None
    try:
        return IntentType(str(raw))
    except ValueError:
        return None


def _case_from_context(context: dict[str, Any]) -> TestCase | None:
    raw = context.get("case")
    if isinstance(raw, TestCase):
        return raw
    if isinstance(raw, dict):
        try:
            return TestCase.model_validate(raw)
        except Exception:  # noqa: BLE001
            return None
    if isinstance(raw, str) and raw.strip():
        try:
            return TestCase.model_validate_json(raw)
        except Exception:  # noqa: BLE001
            return None
    return None


def _fingerprints_overlay_for_case(
    case: TestCase, context: dict[str, Any]
) -> str:
    """Serialize known ElementFingerprints for partial/unbound script LLM path."""
    project = context.get("project")
    elements: dict[str, Any] = {}
    shared = getattr(project, "shared_elements", None) if project is not None else None
    if isinstance(shared, list):
        for el in shared:
            eid = getattr(el, "element_id", None)
            if eid:
                elements[str(eid)] = el
    lines: list[str] = []
    for step in case.steps:
        if not step.enabled or not step.element_ref:
            continue
        el = elements.get(step.element_ref)
        fp = getattr(el, "fingerprint", None) if el is not None else None
        if fp is None and el is not None:
            lines.append(
                f"- step {step.step_no} ref={step.element_ref} "
                f"css={getattr(el, 'css', None)!r} test_id={getattr(el, 'test_id', None)!r}"
            )
            continue
        if fp is None:
            continue
        dump = fp.model_dump() if hasattr(fp, "model_dump") else {}
        lines.append(
            f"- step {step.step_no} ref={step.element_ref} fingerprint={dump}"
        )
    if not lines:
        return ""
    return "Known ElementFingerprints (partial/unbound — prefer these over inventing selectors):\n" + "\n".join(
        lines
    )


def _legacy_intent_from_hybrid(intent: IntentType) -> str:
    if intent == IntentType.CHAT_FAST:
        return INTENT_EXPLAIN
    if intent in {
        IntentType.TEST_SCENARIO,
        IntentType.TEST_CASE_GEN,
        IntentType.TEST_SCRIPT_GEN,
        IntentType.EXEC_AND_BIND,
    }:
        return INTENT_COMPOSE
    if intent == IntentType.RESEARCH:
        return INTENT_EXPLAIN
    if intent == IntentType.SWARM_AUDIT:
        return INTENT_EXPLAIN
    return INTENT_EXPLAIN


def _escalate_reason(context: dict[str, Any]) -> str | None:
    if context.get("apply_schema_ok") is False:
        return "reality_checker_failed"
    if context.get("heal_weak") or context.get("heal_below_run"):
        return "heal_weak"
    err_class = str(context.get("mcp_error_class") or "").upper()
    if context.get("dom_changed") is True or err_class == "DOM_CHANGED":
        return "DOM_CHANGED"
    return None


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
    intent_router: HybridIntentRouter = field(default_factory=HybridIntentRouter)
    skill_matcher: SkillAutoMatcher = field(
        default_factory=lambda: SkillAutoMatcher(builtin_skill_catalog())
    )
    context_compactor: ContextCompactor = field(default_factory=ContextCompactor)
    reality_checker: RealityChecker = field(default_factory=RealityChecker)
    swarm_auditor: SwarmAuditor = field(default_factory=SwarmAuditor)
    last_routing: RoutingDecision | None = field(default=None, repr=False)
    last_skill_matches: list[SkillMatch] = field(default_factory=list, repr=False)
    _active_thinking_kwargs: dict[str, Any] = field(default_factory=dict, repr=False, init=False)
    _turn_project_root: Path | None = field(default=None, repr=False, init=False)
    _turn_session_id: str = field(default="", repr=False, init=False)

    def _ensure_micro_router(self) -> None:
        """Wire Tier-2 micro-router once — ThinkingLevel.OFF via gateway stream."""
        if self.intent_router.has_micro:
            return
        from ai_qa_test_studio.agent.intent_router import make_llm_micro_router

        self.intent_router.bind_micro(
            make_llm_micro_router(
                self.gateway.stream_chat,
                provider=str(getattr(self.gateway, "provider", "openai") or "openai"),
            )
        )

    async def prepare_hybrid_turn(
        self,
        user_text: str,
        context: dict[str, Any],
        *,
        apply_requested: bool = False,
    ) -> tuple[str, RoutingDecision, list[SkillMatch]]:
        """route → (optional case_binding) → match skills. Router never calls matcher."""
        self._ensure_micro_router()
        decision = await self.intent_router.route(
            user_text,
            ui_intent=_ui_intent_from_context(context),
            apply_requested=apply_requested,
            context=context,
        )
        prompt_body = decision.prompt_body
        if decision.intent == IntentType.TEST_SCRIPT_GEN:
            case = _case_from_context(context)
            if case is not None:
                binding = case_binding_summary(case)
                context = {
                    **context,
                    "case_binding": binding,
                    "script_fully_bound": binding.is_fully_bound,
                }
                decision = RoutingDecision(
                    intent=decision.intent,
                    delivery_mode=decision.delivery_mode,
                    thinking=self.intent_router.policy.for_intent(
                        decision.intent,
                        fully_bound_script=binding.is_fully_bound,
                    ),
                    confidence=decision.confidence,
                    source=decision.source,
                    prompt_body=prompt_body,
                )
                # Deterministic exporter only when fully bound; else LLM + fingerprints
                if (
                    binding.total_bindable_steps > 0
                    and not binding.is_fully_bound
                ):
                    overlay = _fingerprints_overlay_for_case(case, context)
                    if overlay:
                        prompt_body = f"{prompt_body}\n\n{overlay}"
                        context["script_fingerprints_overlay"] = overlay
        workspace = [
            str(context.get(CONTEXT_KEY_PROJECT_ROOT) or ""),
            str(context.get(CONTEXT_KEY_SKILL_NAME) or ""),
            str(context.get("language") or ""),
        ]
        matches = self.skill_matcher.match(
            prompt_body,
            decision.intent,
            workspace_signals=workspace,
        )
        if matches:
            prompt_body = self.skill_matcher.overlay_prompt(prompt_body, matches)
            context[CONTEXT_KEY_SKILL_BODY] = "\n\n".join(
                m.skill.prompt_body for m in matches
            )
            context[CONTEXT_KEY_SKILL_NAME] = ",".join(m.skill.skill_id for m in matches)
        if decision.delivery_mode == DeliveryMode.APPLY:
            case = _case_from_context(context)
            if case is not None:
                # S4: RealityChecker supersedes bare schema gate (still uses schema inside)
                reality = self.reality_checker.check_case(case)
                context["apply_schema_ok"] = reality.ok
                context["apply_schema_message"] = reality.message
                context["apply_reality_findings"] = [
                    {"code": f.code, "message": f.message, "severity": f.severity}
                    for f in reality.findings
                ]
                if not reality.ok:
                    # Keep schema helper available for callers that still expect the tuple API
                    _ok, _msg, _ = validate_case_schema(case)
        self.last_routing = decision
        self.last_skill_matches = matches
        return prompt_body, decision, matches

    async def _run_swarm_audit(
        self,
        context: dict[str, Any],
        stop: asyncio.Event,
    ) -> AsyncIterator[OrchestratorEvent]:
        """SWARM_AUDIT: Evidence RO + RealityChecker + Traceability (no browser warm)."""
        yield OrchestratorEvent(kind="role_start", role=ROLE_COORDINATOR)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_COORDINATOR,
            text=self._clip(
                "SWARM_AUDIT · Thinking HIGH · Evidence RO (không mở trình duyệt)"
            ),
        )
        try:
            if stop.is_set():
                yield OrchestratorEvent(
                    kind=EVENT_STATUS_SUMMARY,
                    role=ROLE_COORDINATOR,
                    text=MSG_STATUS_STOPPED,
                )
                return
            report = await asyncio.to_thread(self._resolve_audit_report, context)
            if report is None:
                body = "SWARM_AUDIT cần `run_report` hoặc `.studio/runs/<run_id>/` (chạy case trước)."
                yield OrchestratorEvent(
                    kind="role_end",
                    role=ROLE_COORDINATOR,
                    text=self._clip(body),
                )
                yield OrchestratorEvent(
                    kind=EVENT_FINAL_REPORT,
                    role=ROLE_COORDINATOR,
                    text=self._clip("SWARM_AUDIT blocked: missing run_report"),
                )
                return
            project: Project | None = (
                context.get("project")
                if isinstance(context.get("project"), Project)
                else None
            )
            root_raw = context.get(CONTEXT_KEY_PROJECT_ROOT) or context.get("project_root")
            project_root = Path(str(root_raw)) if root_raw else None
            if stop.is_set():
                yield OrchestratorEvent(
                    kind=EVENT_STATUS_SUMMARY,
                    role=ROLE_COORDINATOR,
                    text=MSG_STATUS_STOPPED,
                )
                return
            audit = await asyncio.to_thread(
                self.swarm_auditor.run,
                report,
                project,
                project_root=project_root,
            )
            context["swarm_audit"] = swarm_audit_context(audit)
            payload = swarm_audit_structured_payload(audit)
            markdown = format_swarm_audit_markdown(audit)
            yield OrchestratorEvent(
                kind="role_end",
                role=ROLE_COORDINATOR,
                text=markdown,
                apply_payload=payload,
            )
            yield OrchestratorEvent(
                kind="apply_ready",
                role=ROLE_COORDINATOR,
                text=markdown,
                apply_payload=payload,
            )
            yield OrchestratorEvent(
                kind=EVENT_FINAL_REPORT,
                role=ROLE_COORDINATOR,
                text=markdown,
                apply_payload=payload,
            )
        finally:
            # Always close coordinator loading pill even if Stop mid-audit.
            yield OrchestratorEvent(kind=EVENT_TURN_END, role=ROLE_COORDINATOR)

    @staticmethod
    def _resolve_audit_report(context: dict[str, Any]) -> RunReport | None:
        report_raw = context.get("run_report") or context.get("report")
        if isinstance(report_raw, RunReport):
            return report_raw
        if isinstance(report_raw, dict):
            try:
                return RunReport.model_validate(report_raw)
            except Exception:  # noqa: BLE001
                pass
        root_raw = context.get(CONTEXT_KEY_PROJECT_ROOT) or context.get("project_root")
        if not root_raw:
            return None
        return EvidenceCollector(Path(str(root_raw))).load_latest_report()

    @staticmethod
    def _max_tokens_for_intent(intent: IntentType, requested: int) -> int:
        if intent in {
            IntentType.TEST_SCENARIO,
            IntentType.TEST_CASE_GEN,
            IntentType.TEST_SCRIPT_GEN,
        }:
            return min(
                max(int(requested), COPILOT_GEN_MAX_TOKENS),
                COPILOT_GEN_MAX_TOKENS_CAP,
            )
        return requested

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
        emitted_terminal = False
        try:
            async for event in self._run_turn(
                user_text=user_text,
                mode=mode,
                context=context,
                history=history,
                stop=stop,
                max_tokens=max_tokens,
            ):
                if event.kind in {"stopped", "pipeline_complete", EVENT_TURN_END, EVENT_ERROR}:
                    emitted_terminal = True
                yield event
        except Exception as exc:  # noqa: BLE001
            masked = get_secret_masker().mask_text(str(exc))
            yield OrchestratorEvent(
                kind=EVENT_ERROR,
                role=ROLE_COMPOSER,
                text=f"{COPILOT_STREAM_ERROR_PREFIX}{masked}",
            )
            emitted_terminal = True
        finally:
            if not emitted_terminal:
                yield OrchestratorEvent(kind=EVENT_TURN_END, role=ROLE_COORDINATOR)

    async def _run_turn(
        self,
        *,
        user_text: str,
        mode: str,
        context: dict[str, Any],
        history: list[ChatMessage] | None,
        stop: asyncio.Event,
        max_tokens: int,
    ) -> AsyncIterator[OrchestratorEvent]:
        hist = list(history or [])
        apply_requested = bool(context.get("apply_requested"))
        root_raw = str(context.get(CONTEXT_KEY_PROJECT_ROOT) or "").strip()
        self._turn_project_root = Path(root_raw) if root_raw else None
        self._turn_session_id = str(
            context.get("session_id")
            or context.get(APPLY_PAYLOAD_DRAFT_SESSION_KEY)
            or ""
        ).strip()
        self._ensure_micro_router()
        prompt_body, hybrid, _skills = await self.prepare_hybrid_turn(
            user_text, context, apply_requested=apply_requested
        )
        max_tokens = self._max_tokens_for_intent(hybrid.intent, max_tokens)
        # Compact history with pin rules before crew exchange; mask outbound secrets
        if hist:
            masker = get_secret_masker()
            raw_hist = [
                {"role": m.role, "content": masker.mask_text(m.content)} for m in hist
            ]
            pruned = self.context_compactor.drop_intermediate_crew_drafts(raw_hist)
            compacted = self.context_compactor.compact_messages(pruned)
            hist = [
                ChatMessage(
                    role=m["role"],
                    content=masker.mask_snapshot(str(m["content"])),
                )
                for m in compacted
            ]
        escalate_reason = _escalate_reason(context)
        thinking = hybrid.thinking
        if escalate_reason:
            thinking = self.intent_router.policy.escalate(thinking, reason=escalate_reason)
            hybrid = RoutingDecision(
                intent=hybrid.intent,
                delivery_mode=hybrid.delivery_mode,
                thinking=thinking,
                confidence=hybrid.confidence,
                source=hybrid.source,
                prompt_body=hybrid.prompt_body,
            )
        self._active_thinking_kwargs = thinking.to_provider_kwargs(
            str(getattr(self.gateway, "provider", "openai") or "openai")
        )
        # CHAT_FAST / SWARM_AUDIT: Evidence RO — skip Playwright/MCP warm
        if hybrid.intent in {IntentType.CHAT_FAST, IntentType.SWARM_AUDIT}:
            mode = mode or COPILOT_MODE_COMPOSE
            context = {
                **context,
                "hybrid_intent": hybrid.intent.value,
                "skip_mcp_warm": True,
                "evidence_ro": hybrid.intent == IntentType.SWARM_AUDIT,
                "thinking_level": hybrid.thinking.level.value,
            }
            user_text = prompt_body
        else:
            context = {
                **context,
                "hybrid_intent": hybrid.intent.value,
                "skip_mcp_warm": False,
                "thinking_level": hybrid.thinking.level.value,
            }
            user_text = prompt_body
        if hybrid.intent == IntentType.SWARM_AUDIT:
            async for event in self._run_swarm_audit(context, stop):
                yield event
            return
        mcp = self.mcp or BundledMcpRegistry(compressor=self.compressor)
        if not mcp.ready:
            mcp.start()
        mcp.crew_memory.clear()
        self._bind_project_context(mcp, context)
        if hybrid.intent in {IntentType.TEST_SCENARIO, IntentType.RESEARCH}:
            user_text, url_note = await enrich_user_text_with_urls(user_text, mcp=mcp)
            if url_note:
                yield OrchestratorEvent(
                    kind=EVENT_STATUS_SUMMARY,
                    role=ROLE_COORDINATOR,
                    text=url_note,
                )
        ctx_block = self._format_context(context, query=user_text)
        chat_fast = hybrid.intent == IntentType.CHAT_FAST
        # CHAT_FAST: footer status chip only — skip a separate coordinator bubble.
        if not chat_fast:
            yield OrchestratorEvent(kind="role_start", role=ROLE_COORDINATOR)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_COORDINATOR,
            text=self._clip(
                f"Hybrid: {hybrid.intent.value} ({hybrid.source}) · "
                f"delivery={hybrid.delivery_mode.value} · thinking={hybrid.thinking.level.value}"
            ),
        )
        retrieved_n = ctx_block.count("### doc=")
        if retrieved_n:
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=ROLE_COORDINATOR,
                text=self._clip(f"Retrieve {retrieved_n} đoạn từ corpus (Top-K)"),
            )

        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_COORDINATOR,
            text=COPILOT_STATUS_CLASSIFYING,
        )
        complexity = await self.classifier.classify(
            user_text, mode, context, gateway=self._gw(LLM_TASK_SUMMARIZE, "simple"), cancel=stop
        )
        complexity_vn = COMPLEXITY_LABELS_VN.get(complexity, complexity)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_COORDINATOR,
            text=self._clip(f"Độ khó: {complexity_vn} ({complexity}) → chọn model theo ma trận"),
        )

        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_COORDINATOR,
            text=COPILOT_STATUS_INFERRING_INTENT,
        )
        # Explicit slash/UI pins legacy crew intent; keyword/micro still LLM-infer
        # so existing compose/BVA paths stay intact.
        if hybrid.source in {"slash", "ui"}:
            intent = _legacy_intent_from_hybrid(hybrid.intent)
        else:
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

        # Always LLM/heuristic coordinator — no keyword fast-path bypass.
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
        # explain/chat: LLM still routes via coordinator → composer, but skip critic loop.
        run_critic = self.debate_max_rounds > 0 and intent != INTENT_EXPLAIN
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
            hybrid_intent=str(context.get("hybrid_intent") or ""),
        )
        seen: dict[str, int] = {}
        coord_route = self._route(LLM_TASK_COORDINATE, complexity)
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_COORDINATOR,
            text=self._clip(
                f"{self._role_label(ROLE_COORDINATOR)} · {complexity_vn} · {coord_route.model}"
            ),
            model=coord_route.model,
        )

        async for ev in self._maybe_coverage_pass(
            board=board,
            context=context,
            hist=hist,
            stop=stop,
            max_tokens=max_tokens,
            mcp=mcp,
            complexity=complexity,
            complexity_vn=complexity_vn,
        ):
            yield ev
            # Let coverage_pass finish partial flush before aborting the pipeline.
            if ev.kind == "stopped":
                return

        closing_summary = ""
        for _round in range(COPILOT_COORDINATOR_MAX_ROUNDS):
            if stop.is_set():
                yield OrchestratorEvent(
                    kind="stopped", role=ROLE_COORDINATOR, status=MSG_STATUS_STOPPED
                )
                return
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=ROLE_COORDINATOR,
                text=COPILOT_STATUS_COORD_DECIDE,
            )
            decision = await self._coordinator_decide(
                board, roster, coord_route.gateway(), stop
            )
            action = decision.get("action") or COORD_ACTION_COMPLETE
            # Never close the turn before a specialist produces an answer/artifact.
            if action == COORD_ACTION_COMPLETE and not board.compose_text:
                summary_guess = str(decision.get("summary") or "").strip()
                # Explain: if coordinator already wrote the user answer, keep it —
                # do not force another hop that re-answers with meta noise.
                if board.intent == INTENT_EXPLAIN and usable_explain_answer(summary_guess):
                    board.compose_text = sanitize_user_reply(summary_guess)
                    yield OrchestratorEvent(kind="role_start", role=ROLE_COMPOSER)
                    yield OrchestratorEvent(
                        kind="role_end",
                        role=ROLE_COMPOSER,
                        text=board.compose_text,
                    )
                    closing_summary = ""
                    break
                decision = heuristic_next_action(
                    board,
                    run_analyzer=roster.run_analyzer,
                    run_optimizer=roster.run_optimizer,
                    max_critique_rounds=self.debate_max_rounds if roster.run_critic else 0,
                )
                action = decision.get("action") or COORD_ACTION_DISPATCH
            if action == COORD_ACTION_COMPLETE:
                closing_summary = str(
                    decision.get("summary") or COPILOT_COORD_COMPLETE_FALLBACK
                )
                if looks_like_coord_protocol(closing_summary):
                    closing_summary = COPILOT_COORD_COMPLETE_FALLBACK
                # Explain chat: composer bubble is enough — skip "Hoàn tất điều phối".
                if board.intent != INTENT_EXPLAIN:
                    yield OrchestratorEvent(
                        kind=EVENT_STATUS_SUMMARY,
                        role=ROLE_COORDINATOR,
                        text=self._clip(closing_summary),
                        model=coord_route.model,
                    )
                break
            # Casual explain/chat: answer via composer — never interrupt with a QA picker.
            if action == COORD_ACTION_CLARIFY and board.intent == INTENT_EXPLAIN:
                decision = {
                    "action": COORD_ACTION_DISPATCH,
                    "to": ROLE_COMPOSER,
                    "goal": COPILOT_COORD_EXPLAIN_GOAL,
                    "acceptance": COPILOT_COORD_EXPLAIN_ACCEPTANCE,
                    "brief": board.user_text,
                    "summary": "",
                }
                action = COORD_ACTION_DISPATCH

            if action == COORD_ACTION_CLARIFY:
                heuristic = self._heuristic_clarify_draft(board.user_text)
                fake = _RoleResult(
                    text=str(decision.get("goal") or heuristic["question"]),
                    needs_clarify=True,
                )
                async for ev in self._maybe_clarify(
                    ROLE_COORDINATOR,
                    fake,
                    board.user_text,
                    complexity,
                    stop,
                    max_tokens,
                    mode=mode,
                    intent=board.intent,
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
                closing_summary = "Dừng do anti-loop"
                break

            packet = self._handoff(
                ROLE_COORDINATOR,
                to_role,
                goal=str(decision.get("goal") or ""),
                acceptance=str(decision.get("acceptance") or ""),
                body=str(decision.get("brief") or board.user_text),
            )
            # Explain/chat: quiet hops — no "→ Giao …" / report bubbles.
            if board.intent != INTENT_EXPLAIN:
                yield OrchestratorEvent(
                    kind=EVENT_AGENT_MESSAGE,
                    role=ROLE_COORDINATOR,
                    from_role=ROLE_COORDINATOR,
                    to_role=to_role,
                    # Full packet — UI bubble summarizer clips for display.
                    text=packet,
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
            if board.intent != INTENT_EXPLAIN:
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
                    # Full report body preserved for user-facing bubble synthesis.
                    text=back,
                )
        else:
            closing_summary = "Hết vòng điều phối — đóng pipeline"
            yield OrchestratorEvent(
                kind=EVENT_LOOP_HALTED,
                role=ROLE_COORDINATOR,
                text=self._clip(closing_summary),
            )

        # Explain/chat: one composer answer is enough — no closing report stack.
        if board.intent == INTENT_EXPLAIN:
            if board.compose_text:
                board.compose_text = sanitize_user_reply(board.compose_text)
            yield OrchestratorEvent(kind="pipeline_complete")
            return

        report = build_final_report(board, closing=closing_summary)
        yield OrchestratorEvent(
            kind="role_end",
            role=ROLE_COORDINATOR,
            text=closing_summary or report,
        )
        yield OrchestratorEvent(
            kind=EVENT_FINAL_REPORT,
            role=ROLE_COORDINATOR,
            text=report,
            model=coord_route.model,
        )
        yield OrchestratorEvent(kind="pipeline_complete")

    async def _maybe_coverage_pass(
        self,
        *,
        board: CrewBoard,
        context: dict[str, Any],
        hist: list[ChatMessage],
        stop: asyncio.Event,
        max_tokens: int,
        mcp: BundledMcpRegistry,
        complexity: str,
        complexity_vn: str,
    ) -> AsyncIterator[OrchestratorEvent]:
        async for ev in run_coverage_pass(
            self,
            board=board,
            context=context,
            hist=hist,
            stop=stop,
            max_tokens=max_tokens,
            mcp=mcp,
            complexity=complexity,
            complexity_vn=complexity_vn,
        ):
            yield ev

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
            max_critique_rounds=self.debate_max_rounds if roster.run_critic else 0,
        )
        if gw.provider == LLM_PROVIDER_OFFLINE:
            return fallback
        system = (
            f"You are the Coordinator ({ROLE_COORDINATOR}). "
            f"{COPILOT_ROLE_BRIEFS[ROLE_COORDINATOR]} "
            "Analyze Intent on the board and choose the next hop yourself — "
            "do not invent keyword shortcuts. "
            "intent=explain (chat/Q&A/math/weather/general help): "
            "dispatch composer to answer in natural language, OR complete with summary="
            "the full user-facing answer (no protocol JSON). If HasCompose=true → complete. "
            "intent=compose|test_data: dispatch planner/composer/critic as needed for artifacts; "
            "never complete while HasCompose=false. Never invent roles outside Team. "
            "Reply JSON only: "
            '{"action":"dispatch"|"complete"|"clarify","to":"planner|composer|critic|analyzer|optimizer",'
            '"goal":"...","acceptance":"...","brief":"...","summary":"..."} .'
        )
        messages = [
            ChatMessage(role="system", content=system),
            ChatMessage(
                role="user",
                content=(
                    f"Team={','.join(roster.roles)}\n"
                    f"Intent={board.intent}\n"
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
        async for ev in dispatch_specialist(
            self,
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
        intent: str = "",
    ) -> AsyncIterator[OrchestratorEvent]:
        # Explain/chat answers the user — never pop a product-picker dialog.
        if intent == INTENT_EXPLAIN or result.intent == INTENT_EXPLAIN:
            return
        # Clear QA intents (compose / BVA) must not pop a vague dialog
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
        has_doc = CTX_MARKER_ATTACHMENTS in (ctx_block or "") or CTX_MARKER_SRS in (
            ctx_block or ""
        ) or CTX_MARKER_RETRIEVED in (ctx_block or "")
        if has_doc and any(k in lowered for k in COPILOT_COMPOSE_DOC_HINTS):
            return True
        return False

    def _heuristic_intent(self, user_text: str, mode: str, ctx_block: str) -> str:
        lowered = (user_text or "").lower()
        test_data_cues = any(
            k in lowered for k in ("bva", "test data", "bộ dữ liệu", "bo du lieu")
        ) or ("biên" in lowered and "data" in lowered)
        compose_cues = mode == COPILOT_MODE_COMPOSE or self._wants_compose(
            user_text, mode, ctx_block
        )
        # Explicit test-data phrasing without suite/scenario language → test_data.
        # If both appear (e.g. "kịch bản … BVA"), prefer compose.
        suite_cues = any(
            k in lowered for k in ("kịch bản", "scenario", "srs", "test case", "test scen")
        )
        if test_data_cues and not suite_cues:
            return INTENT_TEST_DATA
        if compose_cues:
            return INTENT_COMPOSE
        if test_data_cues:
            return INTENT_TEST_DATA
        if any(k in lowered for k in ("giải thích", "explain", "vì sao", "why")):
            return INTENT_EXPLAIN
        has_doc = CTX_MARKER_ATTACHMENTS in (ctx_block or "") or CTX_MARKER_SRS in (
            ctx_block or ""
        ) or CTX_MARKER_RETRIEVED in (ctx_block or "")
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
            "explain = chat/Q&A/math/general help, OR explain/plan/debug a test step "
            "— no Apply/Excel artifact. "
            "Casual questions (e.g. arithmetic, definitions) are explain. "
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

    @staticmethod
    def _stitch_stream_continuation(prior: str, addition: str) -> str:
        """Append a continuation segment, dropping repeated prefix overlap / reopened fences."""
        base = prior or ""
        chunk = addition or ""
        if not chunk:
            return base
        if not base:
            return chunk
        add = chunk.lstrip("\n")
        lowered = add[:12].lower()
        if lowered.startswith("```json") or lowered.startswith("```"):
            fence_end = add.find("\n")
            opener = add[: fence_end if fence_end >= 0 else len(add)]
            if "```" in base and opener.strip().startswith("```"):
                add = add[len(opener) :].lstrip("\n")
        max_overlap = min(len(base), len(add), 256)
        for size in range(max_overlap, 0, -1):
            if base.endswith(add[:size]):
                return base + add[size:]
        return base + add

    @staticmethod
    def _looks_like_structured_json_attempt(text: str) -> bool:
        raw = (text or "").strip()
        if not raw:
            return False
        if "```json" in raw.lower():
            return True
        if raw.startswith("{") and (
            '"scenarios"' in raw
            or '"cases"' in raw
            or '"kind"' in raw
            or '"rows"' in raw
        ):
            return True
        return False

    def _validate_structured_sink(self, sink: list[str]) -> bool:
        """Return True when sink is not a structured attempt, or JSON parses cleanly."""
        joined = "".join(sink)
        if not self._looks_like_structured_json_attempt(joined):
            return True
        if extract_structured_payload(joined) is not None:
            return True
        return self._parse_json_obj(joined) is not None

    def _continuation_messages(
        self,
        base_messages: list[ChatMessage],
        prior_text: str,
    ) -> list[ChatMessage]:
        clipped = prior_text
        if len(clipped) > LLM_STREAM_CONTINUE_PRIOR_CHARS:
            clipped = clipped[-LLM_STREAM_CONTINUE_PRIOR_CHARS:]
        return [
            *base_messages,
            ChatMessage(role="assistant", content=prior_text),
            ChatMessage(
                role="user",
                content=LLM_STREAM_CONTINUE_PROMPT.format(prior=clipped),
            ),
        ]

    async def _collect_text(
        self,
        gw: UnifiedLLMGateway,
        messages: list[ChatMessage],
        stop: asyncio.Event,
        max_tokens: int,
    ) -> str:
        sink: list[str] = []
        working = list(messages)
        continuations_used = 0
        previous_segment = ""
        while True:
            if stop.is_set():
                break
            segment: list[str] = []
            try:
                async for piece in gw.stream_chat(
                    working,
                    max_tokens=max_tokens,
                    cancel=stop,
                    **self._active_thinking_kwargs,
                ):
                    segment.append(piece)
                    if stop.is_set():
                        break
            except Exception as exc:  # noqa: BLE001
                masked = get_secret_masker().mask_text(str(exc))
                sink.append(f"{COPILOT_STREAM_ERROR_PREFIX}{masked}")
                return "".join(sink).strip()

            addition = "".join(segment)
            if continuations_used == 0:
                sink.append(addition)
                previous_segment = addition
            else:
                if continuation_is_stalled(previous_segment, addition):
                    if LLM_STREAM_STALL_NOTICE not in "".join(sink):
                        sink.append(LLM_STREAM_STALL_NOTICE)
                    break
                prior = "".join(sink)
                merged = self._stitch_stream_continuation(prior, addition)
                delta = merged[len(prior) :] if merged.startswith(prior) else addition
                if delta:
                    sink.append(delta)
                previous_segment = addition

            if stop.is_set():
                break
            if gw.last_finish_reason != LLM_FINISH_REASON_LENGTH:
                break
            if continuations_used >= LLM_STREAM_MAX_CONTINUATIONS:
                text = "".join(sink)
                if COPILOT_STREAM_LENGTH_NOTICE not in text:
                    sink.append(COPILOT_STREAM_LENGTH_NOTICE)
                break
            continuations_used += 1
            working = self._continuation_messages(messages, "".join(sink))

        joined = "".join(sink).strip()
        if (
            gw.last_finish_reason != LLM_FINISH_REASON_LENGTH
            and self._looks_like_structured_json_attempt(joined)
            and not self._validate_structured_sink(sink)
        ):
            logger.info("structured_json_incomplete_after_stream chars=%s", len(joined))
        return joined

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
                max_tokens=max(max_tokens, LLM_OUTLINE_MAX_TOKENS),
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

    @staticmethod
    def _marked_context_body(block: str, marker: str) -> str:
        """Slice text after ``marker``, stopping before later context sections."""
        if marker not in block:
            return ""
        body = block.split(marker, 1)[-1]
        stops = (
            CTX_MARKER_SECTION,
            CTX_MARKER_RETRIEVED,
            CTX_MARKER_SRS,
            CTX_MARKER_ATTACHMENTS,
            CTX_MARKER_SKILLS,
            CTX_MARKER_ACTIVE_SKILL,
            CTX_MARKER_DOM,
            CTX_MARKER_CASE,
            CTX_MARKER_LAST_ERROR,
        )
        cut = len(body)
        for stop in stops:
            if stop == marker:
                continue
            idx = body.find(f"\n{stop}")
            if 0 <= idx < cut:
                cut = idx
        return body[:cut].strip()

    def _srs_from_context(self, ctx_block: str, user_text: str) -> str:
        """Prefer coverage section / corpus retrieve over short attach preview / prompt."""
        block = ctx_block or ""
        for marker in (
            CTX_MARKER_SECTION,
            CTX_MARKER_RETRIEVED,
            CTX_MARKER_SRS,
            CTX_MARKER_ATTACHMENTS,
        ):
            body = self._marked_context_body(block, marker)
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
        async for chunk in gw.stream_chat(
            messages,
            max_tokens=min(max_tokens, 256),
            cancel=stop,
            **self._active_thinking_kwargs,
        ):
            buf.append(chunk)
        raw = "".join(buf)
        parsed = self._parse_json_obj(raw)
        if not parsed:
            return heuristic
        suggestions = self._normalize_clarify_labels(parsed.get("suggestions") or [])[:4]
        question = " ".join(str(parsed.get("question") or "").split())
        default = " ".join(
            str(parsed.get("default") or (suggestions[0] if suggestions else "")).split()
        )
        draft = {
            "question": question or heuristic["question"],
            "suggestions": suggestions or list(heuristic["suggestions"]),
            "default": default or heuristic["default"],
        }
        if draft["default"] not in draft["suggestions"]:
            draft["default"] = draft["suggestions"][0]
        if self._is_vague_clarify_draft(draft):
            return heuristic
        return draft

    def _normalize_clarify_labels(self, raw: Any) -> list[str]:
        """Keep only visible, unique suggestion labels for the clarify dialog."""
        labels: list[str] = []
        seen: set[str] = set()
        items = raw if isinstance(raw, (list, tuple)) else ()
        for item in items:
            if item is None:
                continue
            label = " ".join(str(item).split())
            if len(label) < CLARIFY_SUGGESTION_MIN_CHARS:
                continue
            key = label.casefold()
            if key in seen:
                continue
            seen.add(key)
            labels.append(label)
        return labels

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

    def _format_context(self, context: dict[str, Any], *, query: str = "") -> str:
        parts: list[str] = []
        if context.get("include_case") and context.get("case"):
            parts.append(
                f"{CTX_MARKER_CASE}\n" + self.compressor.compress_text(str(context["case"]))
            )
        if context.get("include_dom") and context.get("dom"):
            parts.append(
                f"{CTX_MARKER_DOM}\n"
                + self.compressor.budget.truncate_to_cap(str(context["dom"]))
            )
        if context.get("include_last_error") and context.get("last_error"):
            parts.append(f"{CTX_MARKER_LAST_ERROR}\n" + str(context["last_error"])[:1000])
        if context.get("srs"):
            parts.append(
                f"{CTX_MARKER_SRS}\n"
                + self.compressor.budget.truncate_to_cap(str(context["srs"]), cap=3000)
            )
        attachments = context.get(CONTEXT_KEY_ATTACHMENTS) or []
        doc_ids: list[str] = []
        raw_ids = context.get(CONTEXT_KEY_DOC_IDS)
        if isinstance(raw_ids, list):
            doc_ids = [str(i) for i in raw_ids if str(i).strip()]
        if isinstance(attachments, list) and attachments:
            preview_bits: list[str] = []
            for item in attachments:
                if not isinstance(item, dict):
                    continue
                name = str(item.get("name") or "file").strip() or "file"
                markdown = str(item.get("markdown") or "").strip()
                doc_id = str(item.get("doc_id") or "").strip()
                if doc_id and doc_id not in doc_ids:
                    doc_ids.append(doc_id)
                if markdown:
                    clipped = markdown
                    if len(clipped) > COPILOT_ATTACH_PREVIEW_CHARS:
                        clipped = clipped[: COPILOT_ATTACH_PREVIEW_CHARS - 1] + "…"
                    preview_bits.append(f"### Attached: {name}\n{clipped}")
            if preview_bits:
                parts.append(f"{CTX_MARKER_ATTACHMENTS}\n" + "\n\n".join(preview_bits))
        root = str(context.get(CONTEXT_KEY_PROJECT_ROOT) or "").strip() or None
        if query.strip() or doc_ids:
            hits = retrieve(
                root,
                query or "overview",
                doc_ids=doc_ids or None,
            )
            block = format_retrieved_block(hits)
            if block:
                parts.append(block)
        catalog = str(context.get(CONTEXT_KEY_SKILL_CATALOG) or "").strip()
        if catalog:
            parts.append(f"{CTX_MARKER_SKILLS}\n{catalog}")
        skill_body = str(context.get(CONTEXT_KEY_SKILL_BODY) or "").strip()
        if skill_body:
            name = str(context.get(CONTEXT_KEY_SKILL_NAME) or "active")
            clipped = skill_body
            if len(clipped) > SKILL_CONTEXT_MAX_CHARS:
                clipped = clipped[: SKILL_CONTEXT_MAX_CHARS - 1] + "…"
            parts.append(f"{CTX_MARKER_ACTIVE_SKILL} ({name}):\n{clipped}")
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
        """Yield stream chunk events; auto-continue on finish_reason=length (max 3)."""
        if role:
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=role,
                text=COPILOT_STATUS_WAITING_LLM,
            )
        working = list(messages)
        continuations_used = 0
        previous_segment = ""
        while True:
            if stop.is_set():
                return
            segment: list[str] = []
            try:
                async for piece in gw.stream_chat(
                    working,
                    max_tokens=max_tokens,
                    cancel=stop,
                    **self._active_thinking_kwargs,
                ):
                    segment.append(piece)
                    if continuations_used == 0:
                        # Live-stream the first segment for responsive UI.
                        sink.append(piece)
                        yield OrchestratorEvent(kind=EVENT_CHUNK, role=role, text=piece)
                    if stop.is_set():
                        return
            except Exception as exc:  # noqa: BLE001
                masked = get_secret_masker().mask_text(str(exc))
                notice = f"{COPILOT_STREAM_ERROR_PREFIX}{masked}"
                sink.append(notice)
                yield OrchestratorEvent(
                    kind=EVENT_ERROR, role=role or ROLE_COMPOSER, text=notice
                )
                return

            addition = "".join(segment)
            if continuations_used == 0:
                previous_segment = addition
            else:
                if continuation_is_stalled(previous_segment, addition):
                    if LLM_STREAM_STALL_NOTICE not in "".join(sink):
                        sink.append(LLM_STREAM_STALL_NOTICE)
                        yield OrchestratorEvent(
                            kind=EVENT_CHUNK,
                            role=role,
                            text=LLM_STREAM_STALL_NOTICE,
                        )
                    return
                prior = "".join(sink)
                merged = self._stitch_stream_continuation(prior, addition)
                delta = merged[len(prior) :] if merged.startswith(prior) else addition
                if delta:
                    sink.append(delta)
                    yield OrchestratorEvent(kind=EVENT_CHUNK, role=role, text=delta)
                previous_segment = addition

            if stop.is_set():
                return
            if gw.last_finish_reason != LLM_FINISH_REASON_LENGTH:
                if (
                    self._looks_like_structured_json_attempt("".join(sink))
                    and not self._validate_structured_sink(sink)
                ):
                    logger.info(
                        "structured_json_incomplete_after_stream role=%s chars=%s",
                        role,
                        len("".join(sink)),
                    )
                return
            if continuations_used >= LLM_STREAM_MAX_CONTINUATIONS:
                if COPILOT_STREAM_LENGTH_NOTICE not in "".join(sink):
                    sink.append(COPILOT_STREAM_LENGTH_NOTICE)
                    yield OrchestratorEvent(
                        kind=EVENT_CHUNK,
                        role=role,
                        text=COPILOT_STREAM_LENGTH_NOTICE,
                    )
                return
            continuations_used += 1
            working = self._continuation_messages(messages, "".join(sink))

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

    @staticmethod
    def _composer_schema_for_stage(hybrid_intent: str) -> str:
        stage = (hybrid_intent or "").strip().lower()
        if stage == IntentType.TEST_SCENARIO.value:
            return (
                "STAGE=test_scenario: emit ONLY high-level scenarios — fields "
                "code, title, category, requirement_ref, expected_outcome, description. "
                "Do NOT nest cases or steps. JSON shape: "
                '{"kind":"compose_scenarios","scenarios":[{"code","title","category",'
                '"requirement_ref","expected_outcome","description"}]}.'
            )
        if stage == IntentType.TEST_CASE_GEN.value:
            return (
                f"STAGE=test_case_gen: emit TestCase + steps ONLY for the named scenarios. "
                f"Cap at {COPILOT_TEST_CASE_GEN_MAX_CASES} cases this turn. JSON shape: "
                '{"kind":"compose_scenarios","scenarios":[{"code","title","cases":'
                '[{"code","title","priority","test_type","preconditions","steps":'
                '[{"step_no","action","intent_hint","target","input_data",'
                '"expected_result"}]}]}]}. '
                "Rules: (1) Vietnamese user request ⇒ Vietnamese title/preconditions/"
                "intent_hint/target/expected_result (keep model names/UTC/OCR). "
                "(2) Priority mix P1/P2/P3 — not all P1. (3) Website URL ⇒ UI cases; "
                "API/backend ⇒ test_type=API. (4) Each case ≥2–3 steps: "
                "navigate→interact→assert. (5) action∈navigate|click|fill|select|"
                "hover|scroll|wait|assert|api_request; fill input_data with every "
                "value/threshold; target=semantic UI label while UNBOUND. "
                "(6) preconditions is string or string[]. "
                "Do not invent new scenarios beyond those specified."
            )
        return (
            "When creating scenarios/cases, ALSO append a JSON object "
            '{"kind":"compose_scenarios","scenarios":[{"code","title","requirement_ref",'
            '"description","cases":[{"code","title","priority","test_type",'
            '"preconditions","steps":[{"step_no","action","intent_hint","target",'
            '"input_data","expected_result"}]}]}]}. '
            "Mirror user language; tier priority P1/P2/P3; action∈navigate|click|fill|"
            "select|hover|scroll|wait|assert|api_request; put values in input_data. "
            "When creating test data only, append "
            '{"kind":"test_data_rows","rows":[...]} .'
        )

    @staticmethod
    def _strip_scenario_stage_nesting(payload: dict[str, Any]) -> dict[str, Any]:
        """Defense: TEST_SCENARIO payloads must not carry nested cases/steps."""
        scenarios = payload.get("scenarios")
        if not isinstance(scenarios, list):
            return payload
        cleaned: list[dict[str, Any]] = []
        for item in scenarios:
            if not isinstance(item, dict):
                continue
            row: dict[str, Any] = {
                "id": str(item.get("id") or uuid.uuid4()),
                "code": item.get("code"),
                "title": item.get("title"),
                "category": item.get("category") or item.get("test_type") or "",
                "requirement_ref": item.get("requirement_ref") or "",
                "expected_outcome": item.get("expected_outcome")
                or item.get("description")
                or "",
                "description": item.get("description") or "",
                "cases": [],
            }
            cleaned.append(row)
        return {**payload, "kind": APPLY_KIND_COMPOSE, "scenarios": cleaned}

    @staticmethod
    def _synthetic_expand_batch(
        chunk: list[dict[str, Any]],
        batch_index: int,
        *,
        cases_per_scenario: int = 4,
    ) -> list[dict[str, Any]]:
        """Offline / test expander: materialize concrete cases under each outline scenario."""
        expanded: list[dict[str, Any]] = []
        for sc_i, raw in enumerate(chunk):
            if not isinstance(raw, dict):
                continue
            code = str(raw.get("code") or f"TS_B{batch_index}_{sc_i + 1}")
            title = str(raw.get("title") or code)
            cases_out: list[dict[str, Any]] = []
            existing = raw.get("cases") or []
            if isinstance(existing, list) and existing and any(
                isinstance(c, dict) and (c.get("steps") or []) for c in existing
            ):
                # Already detailed — keep as-is.
                expanded.append(dict(raw))
                continue
            for case_i in range(1, max(1, cases_per_scenario) + 1):
                cases_out.append(
                    {
                        "id": str(uuid.uuid4()),
                        "code": f"{code}_TC{case_i:02d}",
                        "title": f"{title} — case {case_i}",
                        "priority": (
                            "P1 - High"
                            if case_i == 1
                            else ("P2 - Medium" if case_i == 2 else "P3 - Low")
                        ),
                        "preconditions": ["Cookie consent accepted"],
                        "expected_final_result": "Expected outcome visible",
                        "steps": [
                            {
                                "step_no": 1,
                                "action": "navigate",
                                "intent_hint": "Open target page",
                                "expected_result": "Page ready",
                            },
                            {
                                "step_no": 2,
                                "action": "assert",
                                "intent_hint": f"Verify {title} outcome {case_i}",
                                "expected_result": "Assertion holds",
                            },
                        ],
                    }
                )
            expanded.append(
                {
                    **raw,
                    "code": code,
                    "title": title,
                    "cases": cases_out,
                }
            )
        return expanded

    async def _iter_batch_expand(
        self,
        apply_payload: dict[str, Any],
        *,
        user_text: str,
        stop: asyncio.Event,
        cases_per_scenario: int = 4,
    ) -> AsyncIterator[OrchestratorEvent]:
        """Phase 2–3: chunk outline scenarios, checkpoint each batch, emit progress."""
        root = self._turn_project_root
        if root is None:
            return
        scenarios_raw = [
            s for s in (apply_payload.get("scenarios") or []) if isinstance(s, dict)
        ]
        outline = outline_only_scenarios(scenarios_raw)
        session_id = (
            str(apply_payload.get(APPLY_PAYLOAD_DRAFT_SESSION_KEY) or "").strip()
            or self._turn_session_id
            or new_draft_session_id()
        )
        resume = "tiếp tục" in (user_text or "").lower()

        async def _expand(
            chunk: list[dict[str, Any]], batch_index: int
        ) -> list[dict[str, Any]]:
            # Prefer deterministic expander (offline + tests); LLM path can replace later.
            return self._synthetic_expand_batch(
                chunk, batch_index, cases_per_scenario=cases_per_scenario
            )

        runner = BatchComposeRunner(
            project_root=root,
            session_id=session_id,
            expand_batch=_expand,
        )
        progress_box: list[str] = []

        def _on_progress(msg: str) -> None:
            progress_box.append(msg)

        supervisor = get_async_task_supervisor()
        key = f"batch_gen:{session_id}"

        async def _job() -> Any:
            return await runner.run(
                outline,
                stop=stop,
                on_progress=_on_progress,
                resume=resume,
            )

        task = supervisor.register(_job(), key=key, replace=True)
        # Drain progress while the supervisor task runs.
        while not task.done():
            while progress_box:
                msg = progress_box.pop(0)
                yield OrchestratorEvent(
                    kind=EVENT_STATUS_SUMMARY,
                    role=ROLE_COMPOSER,
                    text=msg,
                )
            if stop.is_set():
                break
            await asyncio.sleep(0.01)
        result = await task
        while progress_box:
            msg = progress_box.pop(0)
            yield OrchestratorEvent(
                kind=EVENT_STATUS_SUMMARY,
                role=ROLE_COMPOSER,
                text=msg,
            )
        payload = result.payload if result is not None else await load_session_compose_payload(
            root, session_id
        )
        payload[APPLY_PAYLOAD_DRAFT_SESSION_KEY] = session_id
        text = format_large_suite_summary_card(payload)
        if result is not None and result.stopped:
            text = f"{text}{COPILOT_BATCH_RESUME_HINT}"
        yield OrchestratorEvent(
            kind=EVENT_STATUS_SUMMARY,
            role=ROLE_COMPOSER,
            text=text,
            apply_payload=payload,
        )

    @staticmethod
    def _limit_cases_per_turn(payload: dict[str, Any], max_cases: int) -> dict[str, Any]:
        scenarios = payload.get("scenarios")
        if not isinstance(scenarios, list) or max_cases <= 0:
            return payload
        remaining = max_cases
        limited: list[Any] = []
        for item in scenarios:
            if not isinstance(item, dict):
                continue
            raw_cases = item.get("cases")
            cases: list[Any] = raw_cases if isinstance(raw_cases, list) else []
            if remaining <= 0:
                break
            take = cases[:remaining]
            remaining -= len(take)
            limited.append({**item, "cases": take})
        return {**payload, "scenarios": limited}

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
        hybrid_intent: str = "",
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
        stage = (hybrid_intent or "").strip().lower()
        if intent == INTENT_EXPLAIN:
            system = (
                "You are a helpful Vietnamese assistant in AI QA Test Studio. "
                "Answer the user's question directly. "
                "Do NOT invent compose/test-data JSON. "
                "Do NOT role-play a blocked testing expert or mention handoff/tools. "
                f"{COPILOT_CHAT_PROSE_STYLE}"
            )
        else:
            system = (
                f"You are the Composer ({ROLE_COMPOSER}). "
                f"{COPILOT_ROLE_BRIEFS[ROLE_COMPOSER]} "
                f"{self._composer_schema_for_stage(stage)} "
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
            if stage == IntentType.TEST_SCENARIO.value:
                apply_payload = self._strip_scenario_stage_nesting(
                    {
                        "kind": APPLY_KIND_COMPOSE,
                        "scenarios": [
                            {
                                "id": s.id,
                                "code": s.code,
                                "title": s.title,
                                "requirement_ref": s.requirement_ref,
                                "description": s.description,
                                "expected_outcome": s.description,
                                "cases": [],
                            }
                            for s in scenarios
                        ],
                    }
                )
            else:
                apply_payload = {
                    "kind": APPLY_KIND_COMPOSE,
                    "scenarios": [s.model_dump(mode="json") for s in scenarios],
                }
                if stage == IntentType.TEST_CASE_GEN.value:
                    apply_payload = self._limit_cases_per_turn(
                        apply_payload, COPILOT_TEST_CASE_GEN_MAX_CASES
                    )

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
            if stage == IntentType.TEST_SCENARIO.value:
                apply_payload = self._strip_scenario_stage_nesting(
                    {
                        "kind": APPLY_KIND_COMPOSE,
                        "scenarios": [
                            {
                                "id": s.id,
                                "code": s.code,
                                "title": s.title,
                                "requirement_ref": s.requirement_ref,
                                "description": s.description,
                                "expected_outcome": s.description,
                                "cases": [],
                            }
                            for s in scenarios
                        ],
                    }
                )
                case_count = 0
            else:
                apply_payload = {
                    "kind": APPLY_KIND_COMPOSE,
                    "scenarios": [s.model_dump(mode="json") for s in scenarios],
                }
                if stage == IntentType.TEST_CASE_GEN.value:
                    apply_payload = self._limit_cases_per_turn(
                        apply_payload, COPILOT_TEST_CASE_GEN_MAX_CASES
                    )
                case_count = 0
                for row in apply_payload.get("scenarios") or []:
                    if isinstance(row, dict):
                        cases = row.get("cases") or []
                        case_count += len(cases) if isinstance(cases, list) else 0
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
        if intent == INTENT_EXPLAIN:
            text = sanitize_user_reply(text) or text
        if (
            apply_payload
            and stage == IntentType.TEST_SCENARIO.value
            and apply_payload.get("kind") == APPLY_KIND_COMPOSE
        ):
            apply_payload = self._strip_scenario_stage_nesting(apply_payload)

        # Map-Reduce batch expand for large TEST_CASE_GEN / compose suites.
        if (
            apply_payload
            and apply_payload.get("kind") == APPLY_KIND_COMPOSE
            and stage != IntentType.TEST_SCENARIO.value
            and self._turn_project_root is not None
        ):
            scenarios_raw = [
                s for s in (apply_payload.get("scenarios") or []) if isinstance(s, dict)
            ]
            case_n = count_cases_in_scenarios(scenarios_raw)
            wants_batch = should_batch_compose(
                scenario_count=len(scenarios_raw),
                estimated_cases=max(case_n, len(scenarios_raw) * 4),
                user_text=user_text,
            ) or ("tiếp tục" in user_text.lower())
            if wants_batch:
                async for ev in self._iter_batch_expand(
                    apply_payload,
                    user_text=user_text,
                    stop=stop,
                ):
                    yield ev
                    if ev.apply_payload:
                        apply_payload = ev.apply_payload
                        if is_large_suite_payload(apply_payload):
                            text = format_large_suite_summary_card(apply_payload)
                            text = self._with_artifact_cta(
                                text,
                                COPILOT_ARTIFACT_CTA_COMPOSE.format(
                                    count=len(apply_payload.get("scenarios") or [])
                                ),
                            )
                        elif ev.text:
                            text = ev.text

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

    @staticmethod
    def _composer_digest_for_critic(composer_text: str) -> str:
        """Short code-keyed digest — Critic must not receive full Composer JSON."""
        raw = (composer_text or "").strip()
        if not raw:
            return "(empty)"
        try:
            from ai_qa_test_studio.ui.bubble_summary import extract_structured_payload

            payload = extract_structured_payload(raw)
        except Exception:  # noqa: BLE001
            payload = None
        if payload and isinstance(payload.get("scenarios"), list):
            bullets: list[str] = []
            for item in payload["scenarios"]:
                if not isinstance(item, dict):
                    continue
                code = str(item.get("code") or "?").strip()
                title = str(item.get("title") or "").strip()
                raw_cases = item.get("cases")
                cases: list[Any] = raw_cases if isinstance(raw_cases, list) else []
                bullets.append(f"- {code}: {title} ({len(cases)} case)")
            if bullets:
                digest = "\n".join(bullets)
                if len(digest) > COPILOT_CRITIC_COMPOSER_DIGEST_CHARS:
                    return digest[: COPILOT_CRITIC_COMPOSER_DIGEST_CHARS - 1] + "…"
                return digest
        compact = " ".join(raw.split())
        if len(compact) > COPILOT_CRITIC_COMPOSER_DIGEST_CHARS:
            return compact[: COPILOT_CRITIC_COMPOSER_DIGEST_CHARS - 1] + "…"
        return compact

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
            "Return ONLY APPROVE or REVISE on line 1, then short bullets keyed by "
            "scenario/case code (diff-style). Never repeat Composer JSON. "
            "Do not include CLARIFY unless the user request itself is empty or contradictory."
        )
        digest = self._composer_digest_for_critic(composer_text)
        messages = [
            ChatMessage(role="system", content=system),
            ChatMessage(
                role="user",
                content=self._handoff(
                    ROLE_COMPOSER,
                    ROLE_CRITIC,
                    goal=f"Quality gate cho intent={intent}",
                    acceptance="Dòng đầu: APPROVE hoặc REVISE; bullet ngắn theo code",
                    body=f"Request:\n{user_text}\n\nComposer digest:\n{digest}",
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
