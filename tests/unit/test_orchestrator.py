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
    EVENT_CLARIFY_RESOLVED,
    EVENT_STATUS_SUMMARY,
    LLM_PROVIDER_OFFLINE,
    ROLE_ANALYZER,
    ROLE_COMPOSER,
    ROLE_CRITIC,
    ROLE_OPTIMIZER,
    ROLE_PLANNER,
)


@pytest.mark.asyncio
async def test_default_role_order_planner_composer_critic() -> None:
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
    assert roles[:3] == [ROLE_PLANNER, ROLE_COMPOSER, ROLE_CRITIC]


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
            if "critic" in sys_l and "clarification" not in sys_l:
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
        user_text="Tạo draft login happy path",
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
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    roles: list[str] = []
    async for event in orch.run(
        user_text="Giải thích bước lỗi",
        mode=COPILOT_MODE_CASE,
        context={
            CONTEXT_KEY_ANALYZER_ENABLED: True,
            CONTEXT_KEY_OPTIMIZER_ENABLED: True,
        },
    ):
        if event.kind == "role_start" and event.role:
            roles.append(event.role)
    assert ROLE_ANALYZER in roles
    assert ROLE_OPTIMIZER in roles
    assert roles.index(ROLE_CRITIC) < roles.index(ROLE_ANALYZER)


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
