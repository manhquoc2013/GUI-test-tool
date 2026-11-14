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
    APPLY_KIND_SWARM_AUDIT,
    APPLY_KIND_TEST_DATA,
    CHAT_ROLE_LABELS,
    CONTEXT_KEY_ANALYZER_ENABLED,
    CONTEXT_KEY_ATTACHMENTS,
    CONTEXT_KEY_DOC_IDS,
    CONTEXT_KEY_OPTIMIZER_ENABLED,
    CONTEXT_KEY_PROJECT_ROOT,
    CONTEXT_KEY_SKILL_BODY,
    CONTEXT_KEY_SKILL_CATALOG,
    CONTEXT_KEY_SKILL_NAME,
    COPILOT_ARTIFACT_READY_NOTE,
    COPILOT_AUDIT_EXPORT_TIP,
    COPILOT_CHAT_REPLY_MAX_CHARS,
    COPILOT_FINAL_REPORT_MAX_CHARS,
    COPILOT_STATUS_CLARIFY,
    COPILOT_STATUS_CONNECTING,
    COPILOT_STATUS_INDEXING,
    COPILOT_STATUS_PREPARING,
    COPILOT_STATUS_STREAMING,
    COPILOT_STATUS_WORKING,
    COPILOT_STREAM_ERROR_PREFIX,
    COPILOT_STREAM_LENGTH_NOTICE,
    DEFAULT_SKILLS_DIRNAME,
    EVENT_AGENT_MESSAGE,
    EVENT_CLARIFY_REQUEST,
    EVENT_CLARIFY_RESOLVED,
    EVENT_ERROR,
    EVENT_FINAL_REPORT,
    EVENT_LOOP_HALTED,
    EVENT_STATUS_SUMMARY,
    EVENT_TURN_END,
    ROLE_COMPOSER,
    ROLE_COORDINATOR,
    ROLE_CRITIC,
    ROLE_PLANNER,
    ROLE_SYSTEM,
    TOKEN_CONTEXT_BUDGET,
    WIZARD_IMPORT_BATCH_SIZE,
)
from ai_qa_test_studio.core.compose_mapping import (
    apply_start_url_to_cases,
    extract_start_url,
    normalize_compose_scenarios,
    should_rename_project,
    suggest_project_name,
)
from ai_qa_test_studio.core.doc_corpus import ingest_path
from ai_qa_test_studio.core.secret_masker import get_secret_masker
from ai_qa_test_studio.ui.bubble_summary import (
    extract_structured_payload,
    format_final_report_bubble,
    format_structured_payload_summary,
    structured_stream_progress,
    summarize_for_bubble,
)
from ai_qa_test_studio.ui.chat_markdown import looks_like_markdown_table
from ai_qa_test_studio.ui.controllers.copilot_turn_sink import CopilotTurnSink
from ai_qa_test_studio.ui.widgets.clarify_dialog import ClarifyDialog

if TYPE_CHECKING:
    from ai_qa_test_studio.ui.main_window import MainWindow


def clip_chat_reply(
    text: str,
    max_chars: int = COPILOT_CHAT_REPLY_MAX_CHARS,
    *,
    from_role: str | None = None,
    to_role: str | None = None,
    structured_payload: dict[str, Any] | None = None,
) -> str:
    """Short user-facing bubble; full body stays in artifacts / Apply / history."""
    raw = text or ""
    payload = structured_payload or extract_structured_payload(raw)
    if payload:
        card = format_structured_payload_summary(payload)
        if card:
            return summarize_for_bubble(
                raw,
                max_chars=max_chars,
                from_role=from_role,
                to_role=to_role,
                structured_payload=payload,
            )
    # Keep tables + length/error notices intact so the bubble never goes silent mid-sentence.
    if (
        looks_like_markdown_table(raw)
        or COPILOT_STREAM_LENGTH_NOTICE in raw
        or COPILOT_STREAM_ERROR_PREFIX in raw
    ):
        return raw
    return summarize_for_bubble(
        text,
        max_chars=max_chars,
        from_role=from_role,
        to_role=to_role,
        structured_payload=payload,
    )


class CopilotController:
    def __init__(self, window: MainWindow) -> None:
        self._w = window

    def persist_session(self) -> None:
        w = self._w
        session = w.copilot.session
        if session.has_user_messages():
            w._chat_store.upsert(session)
        elif w._chat_store.get(session.id) is not None:
            w._chat_store.delete(session.id)
        w._chat_store.prune_empties()
        w._chat_store.save()
        self.refresh_history_rail()

    def refresh_history_rail(self) -> None:
        w = self._w
        registry = w._chat_runs
        unread = {sid for sid in (s.id for s in w._chat_store.list_nonempty()) if registry.is_unread(sid)}
        w.copilot.set_history_sessions(
            w._chat_store.list_nonempty(),
            active_id=w.copilot.session.id,
            running_ids=registry.running_session_ids(),
            unread_ids=unread,
        )

    def restore_startup_session(self) -> None:
        w = self._w
        sessions = w._chat_store.list_nonempty()
        if sessions:
            # Runs do not survive process restart — settle zombie typing bubbles.
            if w.copilot.load_session(sessions[0], settle_incomplete=True):
                self.persist_session()
            w._chat_runs.mark_read(sessions[0].id)
        self.refresh_history_rail()

    def activate_history_session(self, session_id: str) -> None:
        w = self._w
        if not session_id or session_id == w.copilot.session.id:
            w._chat_runs.mark_read(session_id)
            self.refresh_history_rail()
            return
        self.persist_session()
        live = w._chat_runs.get_live_session(session_id)
        stored = w._chat_store.get(session_id)
        target = live or stored
        if target is None:
            self.refresh_history_rail()
            return
        running = w._chat_runs.is_running(session_id)
        # Keep live empties as typing; settle blanks when the run is already done.
        if w.copilot.load_session(target, settle_incomplete=not running):
            self.persist_session()
        w.copilot.set_busy(running)
        if running:
            run = w._chat_runs.get_run(session_id)
            if run is not None and run.status:
                w.copilot.set_status(run.status)
        else:
            w.copilot.set_status("")
        w._chat_runs.mark_read(session_id)
        self.refresh_history_rail()
        w.copilot.set_history_rail_visible(False)

    def delete_history_session(self, session_id: str) -> None:
        w = self._w
        if not session_id:
            return
        if w._chat_runs.is_running(session_id):
            w._chat_runs.request_cancel(session_id)
        self._abort_clarify(session_id)
        w._chat_runs.forget(session_id)
        w._chat_store.delete(session_id)
        w._chat_store.save()
        if w.copilot.session.id == session_id:
            w.copilot.new_session()
            w.copilot.set_busy(False)
        self.refresh_history_rail()

    def stop(self) -> None:
        w = self._w
        session_id = w.copilot.session.id
        # Registry owns cancel Events — never trip a stale window-level pointer.
        w._chat_runs.request_cancel(session_id)
        from ai_qa_test_studio.core.async_task_supervisor import get_async_task_supervisor
        from ai_qa_test_studio.core.chat_run_registry import ChatRunRegistry

        get_async_task_supervisor().cancel_now(
            ChatRunRegistry.supervisor_key_for(session_id)
        )
        self._abort_clarify(session_id)
        w.copilot.mark_stopped()
        # Persist settled bubbles immediately so restart cannot revive typing dots.
        self.persist_session()

    def cancel_all_runs(self) -> None:
        w = self._w
        for session_id in list(w._chat_runs.running_session_ids()):
            self._abort_clarify(session_id)
        w._chat_runs.cancel_all()

    def _abort_clarify(self, session_id: str) -> None:
        w = self._w
        dialog = w._clarify_dialogs.pop(session_id, None)
        if dialog is not None:
            dialog.abort_stopped()

    def refresh_model_catalog(self) -> None:
        w = self._w
        router = ModelRouter(w.env.settings.ai)
        w.copilot.set_model_catalog(router.catalog_labels())

    async def clarify_from_ui(
        self, request: ClarifyRequest, *, session_id: str
    ) -> ClarifyResponse:
        w = self._w
        if w.copilot.session.id != session_id:
            suggestions = list(request.suggestions)
            answer = (request.default_answer or "").strip()
            if not answer and suggestions:
                answer = str(suggestions[0])
            return ClarifyResponse(answer=answer or "OK", source="background")

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
        prior = w._clarify_dialogs.pop(session_id, None)
        if prior is not None:
            prior.abort_stopped()
        w._clarify_dialogs[session_id] = dialog

        def _on_answered(answer: str, source: str) -> None:
            if not future.done():
                future.set_result(ClarifyResponse(answer=answer, source=source))

        dialog.answered.connect(_on_answered)
        dialog.show()
        try:
            return await future
        finally:
            if w._clarify_dialogs.get(session_id) is dialog:
                w._clarify_dialogs.pop(session_id, None)

    async def on_prompt(self, text: str, flags: dict[str, Any]) -> None:
        w = self._w
        if w._busy and flags.get("include_dom"):
            w._toast_warn(
                "Copilot",
                "Trình duyệt đang bận — bỏ qua @DOM cho lượt này.",
            )
        target = w.copilot.session
        sink = CopilotTurnSink(w, target)
        run = w._chat_runs.start_run(target, COPILOT_STATUS_CONNECTING)
        cancel = run.cancel
        sink.set_busy(True)
        sink.set_status(COPILOT_STATUS_CONNECTING)
        sink.set_apply_payload(None)
        self.refresh_history_rail()
        current_role: str | None = None
        try:
            attach_paths = [
                str(p) for p in (flags.get("attach_paths") or []) if str(p).strip()
            ]
            if attach_paths:
                sink.set_status(COPILOT_STATUS_INDEXING)
                doc_by_path = await self._resolve_attachment_doc_ids(attach_paths)
                if doc_by_path:
                    flags[CONTEXT_KEY_DOC_IDS] = list(doc_by_path.values())
                    attachments = flags.get("attachments")
                    if isinstance(attachments, list):
                        for item in attachments:
                            if not isinstance(item, dict):
                                continue
                            path = str(item.get("path") or "")
                            doc_id = doc_by_path.get(path)
                            if doc_id and not str(item.get("doc_id") or "").strip():
                                item["doc_id"] = doc_id
            context = await self.build_context(flags)
            # Mode is a UI hint only — orchestrator LLM classifies compose/test_data/explain.
            mode = str(flags.get("mode") or target.mode)
            sink.set_status(COPILOT_STATUS_PREPARING)
            gateway = UnifiedLLMGateway.from_settings(w.env.settings.ai)
            model_ref = str(flags.get("model") or "")
            if not model_ref and sink.viewing():
                model_ref = w.copilot.selected_model_ref()
            router = ModelRouter(w.env.settings.ai, pin_ref=model_ref)
            copilot_cfg = w.env.settings.copilot
            turn_session_id = target.id

            async def _clarify(request: ClarifyRequest) -> ClarifyResponse:
                return await self.clarify_from_ui(request, session_id=turn_session_id)

            orch = CopilotOrchestrator(
                gateway,
                mcp=w._bundled_mcp,
                router=router,
                clarify_handler=_clarify,
                debate_max_rounds=copilot_cfg.debate_max_rounds,
                loop_repeat_limit=copilot_cfg.loop_repeat_limit,
                summary_max_chars=copilot_cfg.summary_max_chars,
            )
            history = [
                ChatMessage(role=m.role if m.role == "user" else "assistant", content=m.content)
                for m in target.llm_window(max_chars=copilot_cfg.lean_tail_max_tokens)
                if m.role
                in {
                    "user",
                    ROLE_COMPOSER,
                    ROLE_PLANNER,
                    ROLE_CRITIC,
                    ROLE_COORDINATOR,
                    "assistant",
                }
                and (m.content or "").strip()
            ]
            stream_hinted = False
            stream_buf = ""
            reply_cap = max(
                80,
                int(getattr(w.env.settings.copilot, "summary_max_chars", 0) or COPILOT_CHAT_REPLY_MAX_CHARS),
            )
            sink.set_status(COPILOT_STATUS_CONNECTING)
            async for event in orch.run(
                user_text=text,
                mode=mode,
                context=context,
                history=history[:-1] if history else [],
                cancel=cancel,
            ):
                # After Stop: ignore further UI events except the terminal "stopped".
                if cancel.is_set() and event.kind != "stopped":
                    continue
                if event.kind == "role_start" and event.role:
                    current_role = event.role
                    stream_hinted = False
                    stream_buf = ""
                    sink.begin_role(event.role)
                    sink.set_streaming_status(
                        COPILOT_STATUS_WORKING.format(
                            role=CHAT_ROLE_LABELS.get(event.role, event.role)
                        ),
                        role=event.role,
                    )
                elif event.kind == "chunk":
                    # Skip raw tokens in transcript; show JSON progress or streaming cue.
                    if current_role:
                        stream_buf += event.text or ""
                        progress = structured_stream_progress(stream_buf)
                        if progress:
                            sink.set_streaming_status(progress, role=current_role)
                            stream_hinted = True
                        elif not stream_hinted:
                            stream_hinted = True
                            sink.set_streaming_status(
                                COPILOT_STATUS_STREAMING, role=current_role
                            )
                    continue
                elif event.kind == "role_end" and event.role:
                    # Persist FULL body for next-turn / agent history; bubble stays short.
                    full = event.text or ""
                    payload = event.apply_payload or extract_structured_payload(full)
                    display = clip_chat_reply(
                        full, reply_cap, structured_payload=payload
                    )
                    sink.end_role(
                        event.role,
                        full,
                        summary=display,
                        structured_payload=payload,
                    )
                    if current_role == event.role:
                        current_role = None
                    stream_hinted = False
                    stream_buf = ""
                elif event.kind == EVENT_STATUS_SUMMARY and event.role:
                    summary = clip_chat_reply(event.text, reply_cap)
                    # Preview only — do not overwrite session with the short summary.
                    # role_end carries the full body for agent/LLM context.
                    if current_role and event.artifact_ref:
                        sink.preview_role(event.role, summary)
                    elif current_role is None and event.artifact_ref:
                        sink.append_status_summary(
                            event.role, event.text or summary, summary=summary
                        )
                    elif summary:
                        sink.set_streaming_status(summary, role=event.role)
                elif event.kind == EVENT_AGENT_MESSAGE:
                    full = event.text or ""
                    from_role = event.from_role or "critic"
                    to_role = event.to_role or "composer"
                    sink.append_agent_message(
                        from_role,
                        to_role,
                        full,
                        summary=clip_chat_reply(
                            full,
                            reply_cap,
                            from_role=from_role,
                            to_role=to_role,
                        ),
                    )
                elif event.kind == EVENT_FINAL_REPORT:
                    full = (event.text or "").strip()
                    if not full:
                        continue
                    audit_payload = event.apply_payload
                    if isinstance(audit_payload, dict) and audit_payload.get("kind") == APPLY_KIND_SWARM_AUDIT:
                        display = clip_chat_reply(
                            full, reply_cap, structured_payload=audit_payload
                        )
                        sink.append_status_summary(
                            event.role or ROLE_COORDINATOR,
                            full,
                            summary=display,
                        )
                        sink.set_apply_payload(audit_payload)
                        sink.attach_apply_to_latest_crew(audit_payload)
                        sink.set_status(display.splitlines()[0] if display else "")
                        continue
                    display = format_final_report_bubble(
                        full,
                        max_chars=max(reply_cap, COPILOT_FINAL_REPORT_MAX_CHARS),
                    )
                    sink.append_status_summary(
                        event.role or ROLE_COORDINATOR,
                        full,
                        summary=display,
                    )
                    sink.set_status(display.splitlines()[0] if display else "")
                elif event.kind == EVENT_CLARIFY_REQUEST:
                    sink.set_status(COPILOT_STATUS_CLARIFY)
                    sink.append_clarify_note(
                        event.role or "crew",
                        clip_chat_reply(event.question or event.text, reply_cap),
                    )
                elif event.kind == EVENT_CLARIFY_RESOLVED:
                    sink.append_clarify_note(
                        event.role or "crew", clip_chat_reply(event.text, reply_cap)
                    )
                elif event.kind == EVENT_LOOP_HALTED:
                    sink.append_clarify_note(
                        event.role or "crew", clip_chat_reply(event.text, reply_cap)
                    )
                elif event.kind == EVENT_ERROR:
                    notice = get_secret_masker().mask_text(event.text or "")
                    if notice:
                        sink.append_error_to_active_bubble(
                            notice, role=event.role or current_role
                        )
                elif event.kind == EVENT_TURN_END:
                    # Explicit terminal from orchestrator finally — settle UI.
                    continue
                elif event.kind == "apply_ready" and event.apply_payload:
                    payload = event.apply_payload
                    sink.set_apply_payload(payload)
                    sink.attach_apply_to_latest_crew(payload)
                    if payload.get("kind") == APPLY_KIND_SWARM_AUDIT:
                        note = COPILOT_AUDIT_EXPORT_TIP
                        sink.append_status_summary(ROLE_SYSTEM, note, summary=note)
                        sink.set_status(note)
                        continue
                    if payload.get("kind") == APPLY_KIND_COMPOSE:
                        count = len(payload.get("scenarios") or [])
                    elif payload.get("kind") == APPLY_KIND_TEST_DATA:
                        count = len(payload.get("rows") or [])
                    else:
                        count = 1
                    note = COPILOT_ARTIFACT_READY_NOTE.format(count=count)
                    sink.append_status_summary(ROLE_SYSTEM, note, summary=note)
                    sink.set_status(note)
                elif event.kind == "stopped":
                    sink.mark_stopped(event.role or current_role)
                    return
            if sink.viewing():
                used = w._token_budget.count(
                    "\n".join(m.content for m in target.messages)
                )
                w._set_pill(
                    w._pill_tokens,
                    f"Tokens: {used / 1000:.1f}k / {TOKEN_CONTEXT_BUDGET / 1000:.0f}k",
                )
        except Exception as exc:  # noqa: BLE001
            masked = get_secret_masker().mask_text(str(exc))
            w._last_error = masked
            sink.append_error_to_active_bubble(
                f"{COPILOT_STREAM_ERROR_PREFIX}{masked}",
                role=current_role or ROLE_COMPOSER,
            )
            sink.mark_stopped()
        finally:
            # Always settle Điều phối viên / typing dots — normal end, Stop, length, or error.
            viewed_id = w.copilot.session.id
            w._chat_runs.finish_run(target.id, viewed_session_id=viewed_id)
            sink.on_turn_finished()
            # Drop Planner/Critic/mid drafts; keep final Composer + user turns.
            if target.prune_intermediate_crew_drafts() and sink.viewing():
                w.copilot.load_session(target, settle_incomplete=False)
            if target.has_user_messages():
                w._chat_store.upsert(target)
                w._chat_store.prune_empties()
                w._chat_store.save()
            sink.set_busy(False)
            self.refresh_history_rail()

    async def _resolve_attachment_doc_ids(self, paths: list[str]) -> dict[str, str]:
        """Resolve corpus doc ids even if the user switched sessions mid-await."""
        w = self._w
        by_path: dict[str, str] = {}
        if hasattr(w.copilot, "await_attachment_ingests"):
            by_path.update(await w.copilot.await_attachment_ingests(paths))
        missing = [p for p in paths if p not in by_path]
        if not missing:
            return by_path
        root = getattr(w.copilot, "_project_root", None) or getattr(w.storage, "root", None)
        for raw in missing:
            try:
                result = await asyncio.to_thread(ingest_path, root, Path(raw))
            except Exception:  # noqa: BLE001
                continue
            if result.doc_id:
                by_path[raw] = result.doc_id
        return by_path

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
                if not name:
                    continue
                cleaned.append(
                    {
                        "name": name,
                        "path": str(item.get("path") or ""),
                        "markdown": markdown,
                        "doc_id": str(item.get("doc_id") or ""),
                    }
                )
            if cleaned:
                ctx[CONTEXT_KEY_ATTACHMENTS] = cleaned
        flag_ids = flags.get(CONTEXT_KEY_DOC_IDS)
        if isinstance(flag_ids, list) and flag_ids:
            ctx[CONTEXT_KEY_DOC_IDS] = [str(i) for i in flag_ids if str(i).strip()]
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
        ctx["session_id"] = str(getattr(w.copilot.session, "id", "") or "")
        if w.project is not None:
            ctx["project"] = w.project
        if getattr(w, "_last_report", None) is not None:
            ctx["run_report"] = w._last_report
        return ctx

    def apply_payload(self, payload: dict[str, Any]) -> None:
        w = self._w
        if w.project is None:
            return
        kind = payload.get("kind")
        if kind == APPLY_KIND_SWARM_AUDIT:
            self._export_audit_report(payload)
            return
        # Apply button ⇒ DeliveryMode.APPLY (RealityChecker gate on cases)
        payload = {**payload, "delivery_mode": "apply", "apply_requested": True}
        if kind == APPLY_KIND_COMPOSE:
            from ai_qa_test_studio.constants import APPLY_PAYLOAD_DRAFT_SESSION_KEY

            draft_id = str(payload.get(APPLY_PAYLOAD_DRAFT_SESSION_KEY) or "").strip()
            root = Path(str(getattr(w.storage, "root", "") or ""))
            if draft_id and root.is_dir():
                from ai_qa_test_studio.agent.batch_compose import (
                    load_session_compose_payload_sync,
                )

                merged = load_session_compose_payload_sync(root, draft_id)
                if merged.get("scenarios"):
                    payload = {
                        **payload,
                        **merged,
                        APPLY_PAYLOAD_DRAFT_SESSION_KEY: draft_id,
                    }
            scenarios_raw = payload.get("scenarios") or []
            scenarios = normalize_compose_scenarios(list(scenarios_raw))
            if not scenarios:
                return
            from ai_qa_test_studio.agent.reality_checker import RealityChecker

            checker = RealityChecker()
            for scenario in scenarios:
                for case in scenario.cases:
                    reality = checker.check_case(case)
                    if not reality.ok:
                        w._toast_warn(
                            "Apply blocked",
                            f"{case.code}: {reality.message}",
                        )
                        return
            start_url = extract_start_url(
                scenarios,
                payload_start_url=str(payload.get("start_url") or ""),
            )
            if start_url:
                w.url_edit.setText(start_url)
                apply_start_url_to_cases(scenarios, start_url)
            if should_rename_project(w.project.name):
                w.project.name = suggest_project_name(
                    scenarios,
                    payload_title=str(
                        payload.get("title")
                        or payload.get("suite_title")
                        or payload.get("name")
                        or ""
                    ),
                )
            # Batch extend; single save + one Explorer reload (no per-item refresh storm).
            batch = max(1, int(WIZARD_IMPORT_BATCH_SIZE))
            for offset in range(0, len(scenarios), batch):
                w.project.root_group.scenarios.extend(scenarios[offset : offset + batch])
            w.storage.save(w.project)
            w._reload_ui()
            data_rows = sum(len(c.test_data_rows) for s in scenarios for c in s.cases)
            w._append_log(
                f"Copilot/AI Wizard Apply: {len(scenarios)} scenario, "
                f"{sum(len(s.cases) for s in scenarios)} case UNBOUND, "
                f"{data_rows} Test Data"
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

    def _export_audit_report(self, payload: dict[str, Any]) -> None:
        """Export Audit HTML/Excel via ReportService (off-thread; UI via emit_if_alive)."""
        w = self._w
        if w.project is None:
            return
        report = w._last_report
        if report is None and isinstance(payload.get("run_report"), dict):
            try:
                from ai_qa_test_studio.core.models import RunReport

                report = RunReport.model_validate(payload["run_report"])
            except Exception:  # noqa: BLE001
                report = None
        if report is None:
            w._toast_warn("Audit", "Chưa có RunReport — chạy test trước rồi /audit.")
            return
        project = w.project
        root = Path(str(getattr(w.storage, "root", "") or ""))
        markdown = str(payload.get("markdown") or "")
        run_id = report.run_id
        from ai_qa_test_studio.core.async_task_supervisor import get_async_task_supervisor
        from ai_qa_test_studio.reporting.report_service import ReportService

        supervisor = get_async_task_supervisor()

        async def _job() -> None:
            service = ReportService(root)
            bundle = await service.export_audit(
                project,
                report,
                audit_markdown=markdown,
            )
            msg = f"Audit HTML/Excel: {bundle.evidence_dir}"
            # qasync loop is the Qt thread — toast/log are safe after await.to_thread.
            w._append_log(msg)
            w._toast_info("Audit", msg)

        supervisor.register(_job(), key=f"audit_export:{run_id}", replace=True)
