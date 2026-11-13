from __future__ import annotations

import asyncio

import pytest

from ai_qa_test_studio.agent.llm_gateway import UnifiedLLMGateway
from ai_qa_test_studio.agent.orchestrator import CopilotOrchestrator
from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    APPLY_KIND_TEST_DATA,
    COPILOT_MODE_CASE,
    COPILOT_MODE_COMPOSE,
    LLM_PROVIDER_OFFLINE,
    ROLE_COMPOSER,
    ROLE_CRITIC,
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
