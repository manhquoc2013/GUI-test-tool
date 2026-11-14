"""Copilot prompt orchestration, clarify, apply, and session persistence."""

from __future__ import annotations

import asyncio
from pathlib import Path
from typing import TYPE_CHECKING, Any

from ai_qa_test_studio.agent.llm_gateway import ChatMessage, UnifiedLLMGateway
from ai_qa_test_studio.agent.model_router import ModelRouter
from ai_qa_test_studio.agent.orchestrator import (
    ClarifyRequest,
    ClarifyResponse,
    CopilotOrchestrator,
)
from ai_qa_test_studio.agent.skill_loader import ProgressiveSkillLoader
from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    APPLY_KIND_TEST_DATA,
    CONTEXT_KEY_ANALYZER_ENABLED,
    CONTEXT_KEY_ATTACHMENTS,
    CONTEXT_KEY_OPTIMIZER_ENABLED,
    CONTEXT_KEY_PROJECT_ROOT,
    CONTEXT_KEY_SKILL_BODY,
    CONTEXT_KEY_SKILL_CATALOG,
    CONTEXT_KEY_SKILL_NAME,
    COPILOT_ARTIFACT_READY_NOTE,
    COPILOT_CHAT_REPLY_MAX_CHARS,
    COPILOT_STATUS_CLARIFY,
    COPILOT_STATUS_CONNECTING,
    COPILOT_STATUS_PREPARING,
    COPILOT_STATUS_WORKING,
    DEFAULT_SKILLS_DIRNAME,
    EVENT_AGENT_MESSAGE,
    EVENT_CLARIFY_REQUEST,
    EVENT_CLARIFY_RESOLVED,
    EVENT_LOOP_HALTED,
    EVENT_STATUS_SUMMARY,
    ROLE_COMPOSER,
    ROLE_CRITIC,
    ROLE_PLANNER,
    ROLE_SYSTEM,
    TOKEN_CONTEXT_BUDGET,
)
from ai_qa_test_studio.core.models import TestScenario
from ai_qa_test_studio.ui.widgets.clarify_dialog import ClarifyDialog

if TYPE_CHECKING:
    from ai_qa_test_studio.ui.main_window import MainWindow


def clip_chat_reply(text: str, max_chars: int = COPILOT_CHAT_REPLY_MAX_CHARS) -> str:
    """Short bubble for the transcript; full body stays in artifacts / Apply."""
    compact = " ".join((text or "").split())
    if len(compact) <= max_chars:
        return compact
    return compact[: max_chars - 1].rstrip() + "…"


class CopilotController:
    def __init__(self, window: MainWindow) -> None:
        self._w = window

    def persist_session(self) -> None:
        w = self._w
        w._chat_store.upsert(w.copilot.session)
        w._chat_store.save()

    def stop(self) -> None:
        w = self._w
        if w._copilot_cancel is not None:
            w._copilot_cancel.set()
        dialog = w._clarify_dialog
        if dialog is not None:
            dialog.abort_stopped()
            w._clarify_dialog = None
        w.copilot.mark_stopped()

    def refresh_model_catalog(self) -> None:
        w = self._w
        router = ModelRouter(w.env.settings.ai)
        w.copilot.set_model_catalog(router.catalog_labels())

    async def clarify_from_ui(self, request: ClarifyRequest) -> ClarifyResponse:
        w = self._w
        loop = asyncio.get_running_loop()
        future: asyncio.Future[ClarifyResponse] = loop.create_future()
        dialog = ClarifyDialog(
            question=request.question,
            suggestions=list(request.suggestions),
            default_answer=request.default_answer,
            parent=w,
            timeout_ms=w.env.settings.copilot.clarify_timeout_ms,
            role=request.role or "Agents",
        )
        w._clarify_dialog = dialog

        def _on_answered(answer: str, source: str) -> None:
            if not future.done():
                future.set_result(ClarifyResponse(answer=answer, source=source))

        dialog.answered.connect(_on_answered)
        dialog.show()
        try:
            return await future
        finally:
            if w._clarify_dialog is dialog:
                w._clarify_dialog = None

    async def on_prompt(self, text: str, flags: dict[str, Any]) -> None:
        w = self._w
        if w._busy and flags.get("include_dom"):
            w._toast_warn(
                "Copilot",
                "Trình duyệt đang bận — bỏ qua @DOM cho lượt này.",
            )
        w.copilot.set_busy(True)
        w.copilot.set_status(COPILOT_STATUS_CONNECTING)
        w._copilot_cancel = asyncio.Event()
        w.copilot.set_apply_payload(None)
        try:
            context = await self.build_context(flags)
            # Mode is a UI hint only — orchestrator LLM classifies compose/test_data/explain.
            mode = str(flags.get("mode") or w.copilot.session.mode)
            w.copilot.set_status(COPILOT_STATUS_PREPARING)
            gateway = UnifiedLLMGateway.from_settings(w.env.settings.ai)
            router = ModelRouter(
                w.env.settings.ai,
                pin_ref=str(flags.get("model") or w.copilot.selected_model_ref()),
            )
            copilot_cfg = w.env.settings.copilot
            orch = CopilotOrchestrator(
                gateway,
                mcp=w._bundled_mcp,
                router=router,
                clarify_handler=self.clarify_from_ui,
                debate_max_rounds=copilot_cfg.debate_max_rounds,
                loop_repeat_limit=copilot_cfg.loop_repeat_limit,
                summary_max_chars=copilot_cfg.summary_max_chars,
            )
            history = [
                ChatMessage(role=m.role if m.role == "user" else "assistant", content=m.content)
                for m in w.copilot.session.llm_window(
                    max_chars=copilot_cfg.lean_tail_max_tokens
                )
                if m.role in {"user", ROLE_COMPOSER, ROLE_PLANNER, ROLE_CRITIC, "assistant"}
                and (m.content or "").strip()
            ]
            current_role: str | None = None
            reply_cap = max(
                80,
                int(getattr(w.env.settings.copilot, "summary_max_chars", 0) or COPILOT_CHAT_REPLY_MAX_CHARS),
            )
            w.copilot.set_status(COPILOT_STATUS_CONNECTING)
            async for event in orch.run(
                user_text=text,
                mode=mode,
                context=context,
                history=history[:-1] if history else [],
                cancel=w._copilot_cancel,
            ):
                if event.kind == "role_start" and event.role:
                    current_role = event.role
                    w.copilot.set_status(
                        COPILOT_STATUS_WORKING.format(role=event.role.capitalize())
                    )
                    w.copilot.begin_role(event.role)
                elif event.kind == "chunk":
                    # Skip raw token stream in transcript — bubbles stay short.
                    continue
                elif event.kind == "role_end" and event.role:
                    # Persist FULL body for next-turn / agent history; bubble stays short.
                    full = event.text or ""
                    display = clip_chat_reply(full, reply_cap)
                    w.copilot.end_role(event.role, full, summary=display)
                    current_role = None
                elif event.kind == EVENT_STATUS_SUMMARY and event.role:
                    summary = clip_chat_reply(event.text, reply_cap)
                    if summary:
                        w.copilot.set_status(summary)
                    # Preview only — do not overwrite session with the short summary.
                    # role_end carries the full body for agent/LLM context.
                    if current_role and event.artifact_ref:
                        w.copilot.preview_role(event.role, summary)
                    elif current_role is None and event.artifact_ref:
                        w.copilot.append_status_summary(
                            event.role, event.text or summary, summary=summary
                        )
                elif event.kind == EVENT_AGENT_MESSAGE:
                    full = event.text or ""
                    w.copilot.append_agent_message(
                        event.from_role or "critic",
                        event.to_role or "composer",
                        full,
                        summary=clip_chat_reply(full, reply_cap),
                    )
                elif event.kind == EVENT_CLARIFY_REQUEST:
                    w.copilot.set_status(COPILOT_STATUS_CLARIFY)
                    w.copilot.append_clarify_note(
                        event.role or "crew",
                        clip_chat_reply(event.question or event.text, reply_cap),
                    )
                elif event.kind == EVENT_CLARIFY_RESOLVED:
                    w.copilot.append_clarify_note(
                        event.role or "crew", clip_chat_reply(event.text, reply_cap)
                    )
                elif event.kind == EVENT_LOOP_HALTED:
                    w.copilot.append_clarify_note(
                        event.role or "crew", clip_chat_reply(event.text, reply_cap)
                    )
                elif event.kind == "apply_ready" and event.apply_payload:
                    payload = event.apply_payload
                    w.copilot.set_apply_payload(payload)
                    # Keep artifact on the latest composer/crew message for session history.
                    for msg in reversed(w.copilot.session.messages):
                        if msg.role in {ROLE_COMPOSER, ROLE_PLANNER, ROLE_CRITIC}:
                            msg.apply_payload = dict(payload)
                            break
                    if payload.get("kind") == APPLY_KIND_COMPOSE:
                        count = len(payload.get("scenarios") or [])
                    elif payload.get("kind") == APPLY_KIND_TEST_DATA:
                        count = len(payload.get("rows") or [])
                    else:
                        count = 1
                    note = COPILOT_ARTIFACT_READY_NOTE.format(count=count)
                    w.copilot.append_status_summary(ROLE_SYSTEM, note, summary=note)
                    w.copilot.set_status(note)
                elif event.kind == "stopped":
                    w.copilot.mark_stopped(event.role or current_role)
                    return
            self.persist_session()
            used = w._token_budget.count(
                "\n".join(m.content for m in w.copilot.session.messages)
            )
            w._set_pill(
                w._pill_tokens,
                f"Tokens: {used / 1000:.1f}k / {TOKEN_CONTEXT_BUDGET / 1000:.0f}k",
            )
        except Exception as exc:  # noqa: BLE001
            w._last_error = str(exc)
            w._toast_error("Copilot", str(exc))
            w.copilot.mark_stopped()
        finally:
            w.copilot.set_busy(False)
            w._copilot_cancel = None

    async def build_context(self, flags: dict[str, Any]) -> dict[str, Any]:
        w = self._w
        ctx: dict[str, Any] = {
            "include_case": bool(flags.get("include_case")),
            "include_dom": bool(flags.get("include_dom")),
            "include_last_error": bool(flags.get("include_last_error")),
        }
        raw_attachments = flags.get("attachments")
        if isinstance(raw_attachments, list) and raw_attachments:
            cleaned: list[dict[str, str]] = []
            for item in raw_attachments:
                if not isinstance(item, dict):
                    continue
                name = str(item.get("name") or "").strip()
                markdown = str(item.get("markdown") or "").strip()
                if not name or not markdown:
                    continue
                cleaned.append(
                    {
                        "name": name,
                        "path": str(item.get("path") or ""),
                        "markdown": markdown,
                    }
                )
            if cleaned:
                ctx[CONTEXT_KEY_ATTACHMENTS] = cleaned
        case = w.builder.peek_case() if hasattr(w.builder, "peek_case") else w.builder.current_case()
        if ctx["include_case"] and case is not None:
            ctx["case"] = case.model_dump_json()
            ctx["srs"] = f"{case.title}\n{case.expected_final_result or ''}"
        if ctx["include_last_error"] and w._last_error:
            ctx["last_error"] = w._last_error
        if ctx["include_dom"] and w.session.started and not w._busy:
            try:
                compact, _ = await w._dom_distiller.extract(w.session.engine.page)
                ctx["dom"] = compact
            except Exception as exc:  # noqa: BLE001
                ctx["dom"] = f"(DOM unavailable: {exc})"
        skills = getattr(w, "_skills", None)
        if skills is None:
            skills = ProgressiveSkillLoader(Path(DEFAULT_SKILLS_DIRNAME))
        ctx[CONTEXT_KEY_SKILL_CATALOG] = skills.get_compact_catalog()
        skill_name = ""
        if hasattr(w, "skill_combo"):
            skill_name = str(w.skill_combo.currentText() or "").strip()
        if skill_name and skill_name in skills.registry:
            ctx[CONTEXT_KEY_SKILL_NAME] = skill_name
            ctx[CONTEXT_KEY_SKILL_BODY] = skills.load_skill_context(skill_name)
        copilot_cfg = w.env.settings.copilot
        ctx[CONTEXT_KEY_ANALYZER_ENABLED] = bool(copilot_cfg.analyzer_enabled)
        ctx[CONTEXT_KEY_OPTIMIZER_ENABLED] = bool(copilot_cfg.optimizer_enabled)
        ctx[CONTEXT_KEY_PROJECT_ROOT] = str(getattr(w.storage, "root", "") or "")
        return ctx

    def apply_payload(self, payload: dict[str, Any]) -> None:
        w = self._w
        if w.project is None:
            return
        kind = payload.get("kind")
        if kind == APPLY_KIND_COMPOSE:
            scenarios_raw = payload.get("scenarios") or []
            scenarios = [TestScenario.model_validate(item) for item in scenarios_raw]
            if not scenarios:
                return
            start_url = str(payload.get("start_url") or "").strip()
            if start_url:
                w.url_edit.setText(start_url)
                for scenario in scenarios:
                    for case in scenario.cases:
                        case.start_url = start_url
            w.project.root_group.scenarios.extend(scenarios)
            w.storage.save(w.project)
            w._reload_ui()
            w._append_log(
                f"Copilot/AI Wizard Apply: {len(scenarios)} scenario, "
                f"{sum(len(s.cases) for s in scenarios)} case UNBOUND"
            )
            w._toast_info("Apply", f"Đã thêm {len(scenarios)} scenario draft.")
            return
        if kind == APPLY_KIND_TEST_DATA:
            rows = list(payload.get("rows") or [])
            target = w.builder.current_case()
            if target is None:
                w._toast_warn("Apply", "Chọn một test case trước khi thêm Test Data.")
                return
            target.test_data_rows = list(target.test_data_rows) + rows
            w._merge_case(target)
            w.storage.save(w.project)
            w.data_drawer.load_rows(target.test_data_rows)
            w._append_log(f"Copilot Apply: +{len(rows)} Test Data rows")
            w._toast_info("Apply", f"Đã thêm {len(rows)} bộ Test Data.")
            return
        w._toast_warn("Apply", f"Loại artifact chưa hỗ trợ: {kind}")
