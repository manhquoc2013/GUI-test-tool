from __future__ import annotations

import asyncio

import pytest

from ai_qa_test_studio.agent.bundled_mcp import BundledMcpRegistry
from ai_qa_test_studio.agent.llm_gateway import ChatMessage, UnifiedLLMGateway
from ai_qa_test_studio.agent.orchestrator import (
    ClarifyRequest,
    ClarifyResponse,
    CopilotOrchestrator,
    _RoleResult,
)
from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    APPLY_KIND_TEST_DATA,
    CLARIFY_SOURCE_DEFAULT_SKIP,
    CLARIFY_SOURCE_STOPPED,
    CONTEXT_KEY_ANALYZER_ENABLED,
    CONTEXT_KEY_ATTACHMENTS,
    CONTEXT_KEY_OPTIMIZER_ENABLED,
    CONTEXT_KEY_PROJECT_ROOT,
    CONTEXT_KEY_SKILL_BODY,
    CONTEXT_KEY_SKILL_CATALOG,
    CONTEXT_KEY_SKILL_NAME,
    COPILOT_MODE_CASE,
    COPILOT_MODE_COMPOSE,
    COPILOT_SUMMARY_MAX_CHARS,
    EVENT_CLARIFY_REQUEST,
    EVENT_CLARIFY_RESOLVED,
    EVENT_STATUS_SUMMARY,
    LLM_PROVIDER_OFFLINE,
    ROLE_ANALYZER,
    ROLE_COMPOSER,
    ROLE_COORDINATOR,
    ROLE_CRITIC,
    ROLE_OPTIMIZER,
    ROLE_PLANNER,
)


@pytest.mark.asyncio
async def test_default_role_order_coordinator_then_specialists() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    roles: list[str] = []
    async for event in orch.run(
        user_text="Giải thích bước lỗi",
        mode=COPILOT_MODE_CASE,
        context={},
    ):
        if event.kind == "role_start" and event.role:
            roles.append(event.role)
    assert roles[0] == ROLE_COORDINATOR
    assert ROLE_COMPOSER in roles
    assert ROLE_CRITIC in roles
    assert roles.index(ROLE_COMPOSER) < roles.index(ROLE_CRITIC)


@pytest.mark.asyncio
async def test_compose_mode_emits_apply_payload() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    payloads = []
    async for event in orch.run(
        user_text="Tạo kịch bản từ SRS login",
        mode=COPILOT_MODE_COMPOSE,
        context={"srs": "Login happy path"},
    ):
        if event.kind == "apply_ready" and event.apply_payload:
            payloads.append(event.apply_payload)
    assert payloads
    assert payloads[-1]["kind"] == APPLY_KIND_COMPOSE
    assert payloads[-1]["scenarios"]


@pytest.mark.asyncio
async def test_attachment_analyze_typo_emits_apply_payload() -> None:
    """Regression: 'test screnaro' + attached SRS must still produce Apply/Excel artifact."""
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    payloads = []
    texts: list[str] = []
    async for event in orch.run(
        user_text="phân tích và tạo các test screnaro",
        mode=COPILOT_MODE_CASE,
        context={
            CONTEXT_KEY_ATTACHMENTS: [
                {
                    "name": "srs.docx",
                    "markdown": "# SRS Đăng ký người yếu thế\n## Happy path\n## Negative",
                }
            ]
        },
    ):
        if event.kind == "apply_ready" and event.apply_payload:
            payloads.append(event.apply_payload)
        if event.kind == "role_end" and event.role == ROLE_COMPOSER:
            texts.append(event.text)
    assert payloads, "expected compose artifact for attached SRS + create intent"
    assert payloads[-1]["kind"] == APPLY_KIND_COMPOSE
    assert payloads[-1]["scenarios"]
    assert any("Tải Excel" in t or "Apply" in t for t in texts)


@pytest.mark.asyncio
async def test_natural_language_without_keywords_still_composes() -> None:
    """LLM/heuristic intent — no magic keywords required when a doc is attached."""
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    payloads = []
    async for event in orch.run(
        user_text="làm giúp tôi bộ kiểm thử từ file này đi",
        mode=COPILOT_MODE_CASE,
        context={
            CONTEXT_KEY_ATTACHMENTS: [
                {"name": "req.docx", "markdown": "# Yêu cầu đăng ký\nForm CCCD"}
            ]
        },
    ):
        if event.kind == "apply_ready" and event.apply_payload:
            payloads.append(event.apply_payload)
    assert payloads
    assert payloads[-1]["kind"] == APPLY_KIND_COMPOSE


@pytest.mark.asyncio
async def test_explain_only_skips_compose_artifact() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    payloads = []
    async for event in orch.run(
        user_text="Giải thích lỗi bước đăng nhập vừa chạy",
        mode=COPILOT_MODE_CASE,
        context={},
    ):
        if event.kind == "apply_ready" and event.apply_payload:
            payloads.append(event.apply_payload)
    assert payloads == []


def test_infer_intent_attachment_defaults_to_compose() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    ctx = "ATTACHMENTS:\n### Attached: a.docx\n# SRS"
    assert orch._heuristic_intent("làm giúp tôi từ file này", COPILOT_MODE_CASE, ctx) == "compose"
    assert orch._heuristic_intent("Giải thích lỗi bước", COPILOT_MODE_CASE, "") == "explain"


def test_crew_roster_explain_skips_tail_roles() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    roster = orch._crew_roster(
        "explain",
        "simple",
        {"analyzer_enabled": True, "optimizer_enabled": True},
    )
    assert roster.roles[0] == ROLE_COORDINATOR
    assert roster.run_critic is True
    assert roster.run_debate is True
    assert roster.run_analyzer is False
    assert roster.run_optimizer is False


def test_crew_roster_complex_compose_enables_analyzer_optimizer() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    roster = orch._crew_roster(
        "compose",
        "complex",
        {"analyzer_enabled": True, "optimizer_enabled": True},
    )
    assert roster.roles[0] == ROLE_COORDINATOR
    assert roster.run_debate is True
    assert roster.run_analyzer is True
    assert roster.run_optimizer is True
    assert "analyzer" in roster.roles
    assert "optimizer" in roster.roles


def test_heuristic_coord_revises_after_critic_reject() -> None:
    from ai_qa_test_studio.agent.crew_exchange import CrewBoard, heuristic_next_action

    board = CrewBoard(
        user_text="tạo kịch bản",
        mode=COPILOT_MODE_COMPOSE,
        ctx_block="",
        intent="compose",
        complexity="normal",
        compose_text="draft",
        critique_text="REVISE",
        critique_approved=False,
        critique_notes="thiếu expected",
        dispatched=["composer", "critic"],
    )
    nxt = heuristic_next_action(board, run_analyzer=False, run_optimizer=False)
    assert nxt["action"] == "dispatch"
    assert nxt["to"] == ROLE_COMPOSER


def test_handoff_packet_is_structured() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    packet = orch._handoff(
        "planner",
        "composer",
        goal="Sinh draft",
        acceptance="Có Apply",
        body="Kế hoạch 1-2-3",
    )
    assert "[HANDOFF]" in packet
    assert "From:" in packet and "To:" in packet
    assert "Goal: Sinh draft" in packet
    assert "Kế hoạch 1-2-3" in packet


@pytest.mark.asyncio
async def test_explain_turn_emits_coordinator_exchange() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    handoffs = []
    async for event in orch.run(
        user_text="Giải thích lỗi bước đăng nhập vừa chạy",
        mode=COPILOT_MODE_CASE,
        context={},
    ):
        if event.kind == "agent_message" and event.from_role and event.to_role:
            handoffs.append((event.from_role, event.to_role))
    assert (ROLE_COORDINATOR, ROLE_COMPOSER) in handoffs
    assert (ROLE_COMPOSER, ROLE_COORDINATOR) in handoffs
    assert (ROLE_COORDINATOR, ROLE_CRITIC) in handoffs


def test_srs_from_attachments_preferred_over_prompt() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    ctx = orch._format_context(
        {
            CONTEXT_KEY_ATTACHMENTS: [
                {"name": "a.docx", "markdown": "Nội dung SRS dài về đăng ký"}
            ]
        }
    )
    srs = orch._srs_from_context(ctx, "phân tích file")
    assert "Nội dung SRS dài" in srs
    assert orch._wants_compose("phân tích và tạo các test screnaro", COPILOT_MODE_CASE, ctx)


@pytest.mark.asyncio
async def test_bva_request_emits_test_data_rows() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    payloads = []
    async for event in orch.run(
        user_text="Sinh thêm 5 bộ Test Data biên (BVA)",
        mode=COPILOT_MODE_CASE,
        context={},
    ):
        if event.kind == "apply_ready" and event.apply_payload:
            payloads.append(event.apply_payload)
    assert payloads
    assert payloads[-1]["kind"] == APPLY_KIND_TEST_DATA
    assert len(payloads[-1]["rows"]) >= 3


@pytest.mark.asyncio
async def test_cancel_stops_before_critic() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    cancel = asyncio.Event()
    roles: list[str] = []
    async for event in orch.run(
        user_text="hello",
        mode=COPILOT_MODE_CASE,
        context={},
        cancel=cancel,
    ):
        if event.kind == "role_start" and event.role == ROLE_COMPOSER:
            cancel.set()
        if event.kind == "role_start" and event.role:
            roles.append(event.role)
        if event.kind == "stopped":
            break
    assert ROLE_CRITIC not in roles


@pytest.mark.asyncio
async def test_status_summaries_are_short() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    async for event in orch.run(
        user_text="Giải thích bước lỗi đăng nhập",
        mode=COPILOT_MODE_CASE,
        context={},
    ):
        if event.kind == EVENT_STATUS_SUMMARY:
            assert len(event.text) <= COPILOT_SUMMARY_MAX_CHARS


@pytest.mark.asyncio
async def test_clarify_aborts_when_stop_set() -> None:
    hang = asyncio.Event()

    async def never_answer(_req: ClarifyRequest) -> ClarifyResponse:
        await hang.wait()
        return ClarifyResponse(answer="should-not-reach")

    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline"),
        clarify_handler=never_answer,
    )
    stop = asyncio.Event()
    result = _RoleResult(text="plan with CLARIFY marker", needs_clarify=True)
    events = []

    async def _drive() -> None:
        async for ev in orch._maybe_clarify(
            ROLE_PLANNER, result, "??? unclear", "simple", stop, 64
        ):
            events.append(ev)
            if ev.kind == "clarify_request":
                stop.set()

    await asyncio.wait_for(_drive(), timeout=2.0)
    resolved = [e for e in events if e.kind == EVENT_CLARIFY_RESOLVED]
    assert resolved
    assert resolved[-1].status in {
        CLARIFY_SOURCE_STOPPED,
        CLARIFY_SOURCE_DEFAULT_SKIP,
    }
    assert resolved[-1].text
    hang.set()


@pytest.mark.asyncio
async def test_critic_clarify_appends_to_user_text() -> None:
    post_critic_prompts: list[str] = []
    critic_calls = {"n": 0}

    class _TrackingGateway(UnifiedLLMGateway):
        async def stream_chat(  # type: ignore[override]
            self,
            messages: list[ChatMessage],
            max_tokens: int = 256,
            cancel: asyncio.Event | None = None,
        ):
            system = next((m.content for m in messages if m.role == "system"), "")
            user = next((m.content for m in reversed(messages) if m.role == "user"), "")
            sys_l = system.lower()
            if "you are the critic" in sys_l:
                critic_calls["n"] += 1
                if critic_calls["n"] == 1:
                    yield "CLARIFY — need scope. REVISE: add expected outcome."
                else:
                    yield "APPROVE"
                return
            if "clarification" in sys_l or "draft one clarification" in sys_l:
                yield (
                    '{"question":"Phạm vi?","suggestions":["Happy","Full"],'
                    '"default":"Happy"}'
                )
                return
            if "debate" in sys_l:
                post_critic_prompts.append(user)
                yield "Critic→Composer: add expected outcome."
                return
            if "planner" in sys_l:
                yield "1. Plan step"
                return
            if "composer" in sys_l:
                if "[Clarify]" in user:
                    post_critic_prompts.append(user)
                yield "Draft artifact"
                return
            if "classify" in sys_l:
                # Complexity (simple/normal) vs intent JSON — keep both offline-safe.
                if "vietnamese qa" in sys_l or '"intent"' in sys_l:
                    yield '{"intent":"explain"}'
                else:
                    yield "simple"
                return
            yield "ok"

    async def clarify(_req: ClarifyRequest) -> ClarifyResponse:
        return ClarifyResponse(answer="Chỉ happy path", source="user")

    orch = CopilotOrchestrator(
        _TrackingGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline"),
        clarify_handler=clarify,
    )
    async for _ in orch.run(
        user_text="Yêu cầu chưa rõ phạm vi login — ???",
        mode=COPILOT_MODE_CASE,
        context={},
    ):
        pass
    merged = [t for t in post_critic_prompts if "[Clarify]" in t and "happy path" in t.lower()]
    assert merged, f"expected critic clarify merge after critic, got {post_critic_prompts!r}"


def test_looks_ambiguous_ignores_role_clarify_word() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    # Role output may say "clarify"/"unclear" without meaning the *user* was ambiguous.
    assert orch._looks_ambiguous("Làm rõ bước login", "Please clarify the selector") is False
    assert orch._looks_ambiguous("normal request here", "This is unclear; please clarify") is False
    assert orch._looks_ambiguous("Yêu cầu chưa rõ phạm vi", "ok") is True
    assert orch._looks_ambiguous("???", "anything") is True


def test_actionable_intent_skips_clarify_popup() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    assert orch._intent_is_actionable("Tạo draft login happy path") is True
    assert orch._intent_is_actionable("Sinh thêm 5 bộ Test Data biên (BVA)") is True
    assert orch._intent_is_actionable("ok?", mode="") is False
    draft = orch._heuristic_clarify_draft("làm gì đó")
    assert "giả định mặc định" not in draft["question"].lower()
    assert all("giả định mặc định" not in s.lower() for s in draft["suggestions"])
    assert draft["default"] == draft["suggestions"][0]


@pytest.mark.asyncio
async def test_clear_compose_request_does_not_emit_clarify() -> None:
    class _ClarifyHappyGateway(UnifiedLLMGateway):
        async def stream_chat(  # type: ignore[override]
            self,
            messages: list[ChatMessage],
            max_tokens: int = 256,
            cancel: asyncio.Event | None = None,
        ):
            system = next((m.content for m in messages if m.role == "system"), "").lower()
            if "planner" in system:
                yield "1. Plan\nCLARIFY maybe"
                return
            if "composer" in system:
                yield "Draft UNBOUND CLARIFY"
                return
            if "critic" in system:
                yield "APPROVE"
                return
            if "clarification" in system or "draft one clarification" in system:
                yield (
                    '{"question":"điểm mờ","suggestions":["Tiếp tục với giả định mặc định"],'
                    '"default":"Tiếp tục với giả định mặc định"}'
                )
                return
            yield "ok"

    asked: list[str] = []

    async def clarify(req: ClarifyRequest) -> ClarifyResponse:
        asked.append(req.question)
        return ClarifyResponse(answer=req.default_answer, source="user")

    orch = CopilotOrchestrator(
        _ClarifyHappyGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline"),
        clarify_handler=clarify,
    )
    kinds: list[str] = []
    async for ev in orch.run(
        user_text="Tạo kịch bản login từ SRS",
        mode=COPILOT_MODE_CASE,
        context={},
    ):
        kinds.append(ev.kind)
    assert EVENT_CLARIFY_REQUEST not in kinds
    assert asked == []


@pytest.mark.asyncio
async def test_composer_role_end_keeps_full_text() -> None:
    long_body = "COMPOSER_FULL_" + ("x" * 200)
    class _Gw(UnifiedLLMGateway):
        async def stream_chat(  # type: ignore[override]
            self,
            messages: list[ChatMessage],
            max_tokens: int = 256,
            cancel: asyncio.Event | None = None,
        ):
            system = next((m.content for m in messages if m.role == "system"), "")
            if "Composer" in system or "composer" in system.lower():
                yield long_body
                return
            if "Critic" in system or "critic" in system.lower():
                yield "APPROVE"
                return
            if "Planner" in system or "planner" in system.lower():
                yield "1. plan"
                return
            if "classify" in system.lower():
                yield "simple"
                return
            yield "ok"

    orch = CopilotOrchestrator(
        _Gw(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    role_ends: list[str] = []
    async for event in orch.run(
        user_text="Giải thích bước",
        mode=COPILOT_MODE_CASE,
        context={},
    ):
        if event.kind == "role_end" and event.role == ROLE_COMPOSER:
            role_ends.append(event.text)
    assert role_ends
    assert long_body in role_ends[0]
    assert len(role_ends[0]) > COPILOT_SUMMARY_MAX_CHARS


@pytest.mark.asyncio
async def test_collect_yields_chunk_events() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    chunks: list[str] = []
    async for event in orch.run(
        user_text="hello chunks",
        mode=COPILOT_MODE_CASE,
        context={},
    ):
        if event.kind == "chunk":
            chunks.append(event.text)
    assert chunks


@pytest.mark.asyncio
async def test_optional_analyzer_optimizer_roles() -> None:
    """Complex compose + Settings on → Analyzer/Optimizer join the roster."""
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    roles: list[str] = []
    async for event in orch.run(
        user_text="Tạo toàn bộ kịch bản e2e từ SRS và BVA biên",
        mode=COPILOT_MODE_COMPOSE,
        context={
            CONTEXT_KEY_ANALYZER_ENABLED: True,
            CONTEXT_KEY_OPTIMIZER_ENABLED: True,
            "srs": "A" * 900,
            "include_dom": True,
            "include_case": True,
        },
    ):
        if event.kind == "role_start" and event.role:
            roles.append(event.role)
    assert ROLE_ANALYZER in roles
    assert ROLE_OPTIMIZER in roles
    assert roles.index(ROLE_CRITIC) < roles.index(ROLE_ANALYZER)


@pytest.mark.asyncio
async def test_explain_skips_analyzer_even_when_enabled() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    roles: list[str] = []
    async for event in orch.run(
        user_text="Giải thích lỗi bước đăng nhập vừa chạy",
        mode=COPILOT_MODE_CASE,
        context={
            CONTEXT_KEY_ANALYZER_ENABLED: True,
            CONTEXT_KEY_OPTIMIZER_ENABLED: True,
        },
    ):
        if event.kind == "role_start" and event.role:
            roles.append(event.role)
    assert ROLE_ANALYZER not in roles
    assert ROLE_OPTIMIZER not in roles
    assert roles[0] == ROLE_COORDINATOR
    assert ROLE_COMPOSER in roles and ROLE_CRITIC in roles


def test_format_context_includes_skill_catalog_and_body() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    block = orch._format_context(
        {
            CONTEXT_KEY_SKILL_CATALOG: "- web-e2e-functional: E2E rules",
            CONTEXT_KEY_SKILL_NAME: "web-e2e-functional",
            CONTEXT_KEY_SKILL_BODY: "Prefer get_by_role",
        }
    )
    assert "SKILLS:" in block
    assert "web-e2e-functional" in block
    assert "ACTIVE_SKILL" in block
    assert "Prefer get_by_role" in block


def test_format_context_includes_chat_attachments() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    block = orch._format_context(
        {
            CONTEXT_KEY_ATTACHMENTS: [
                {
                    "name": "login-srs.md",
                    "path": "/tmp/login-srs.md",
                    "markdown": "# Login\nUser can sign in.",
                }
            ]
        }
    )
    assert "ATTACHMENTS:" in block
    assert "login-srs.md" in block
    assert "User can sign in." in block


@pytest.mark.asyncio
async def test_run_clears_crew_memory_each_turn() -> None:
    mcp = BundledMcpRegistry()
    mcp.start()
    first = mcp.invoke(
        "qa-crew-memory",
        "put_artifact",
        {"role": "composer", "kind": "stale", "text": "leftover"},
        role="composer",
    )
    ref = first["ref"]
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline"),
        mcp=mcp,
    )
    async for _ in orch.run(user_text="hi", mode=COPILOT_MODE_CASE, context={}):
        pass
    got = mcp.invoke("qa-crew-memory", "get_full", {"ref": ref})
    assert got.get("found") is False


@pytest.mark.asyncio
async def test_bind_project_context_wires_memory_and_ingest(tmp_path) -> None:
    from pathlib import Path

    mcp = BundledMcpRegistry()
    mcp.start()
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline"),
        mcp=mcp,
    )
    root = Path(tmp_path)
    orch._bind_project_context(mcp, {CONTEXT_KEY_PROJECT_ROOT: str(root)})
    assert mcp.persistent_memory.project_root == root.resolve()
    assert mcp.ingest.project_root == root.resolve()
    put = mcp.persistent_memory.put_note("login", "bound ok")
    assert put["ok"] is True
    orch._bind_project_context(mcp, {})
    assert mcp.persistent_memory.project_root is None
    assert mcp.ingest.project_root is None
