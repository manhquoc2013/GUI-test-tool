"""LLM/heuristic coordinator routes explain; protocol JSON must not leak to UI."""

from __future__ import annotations

import pytest

from ai_qa_test_studio.agent.crew_exchange import (
    CrewBoard,
    heuristic_next_action,
    looks_like_coord_protocol,
    parse_coord_decision,
)
from ai_qa_test_studio.agent.llm_gateway import UnifiedLLMGateway
from ai_qa_test_studio.agent.orchestrator import CopilotOrchestrator
from ai_qa_test_studio.constants import (
    COMPLEXITY_SIMPLE,
    COORD_ACTION_COMPLETE,
    COORD_ACTION_DISPATCH,
    COPILOT_COORD_COMPLETE_FALLBACK,
    COPILOT_COORD_JSON_HIDDEN,
    COPILOT_MODE_CASE,
    INTENT_EXPLAIN,
    LLM_PROVIDER_OFFLINE,
    ROLE_COMPOSER,
    ROLE_COORDINATOR,
    ROLE_CRITIC,
)
from ai_qa_test_studio.ui.bubble_summary import summarize_for_bubble


def test_looks_like_coord_protocol_detects_control_json() -> None:
    raw = '{"action":"complete","to":"coordinator","goal":"","summary":"x"}'
    assert looks_like_coord_protocol(raw) is True
    assert looks_like_coord_protocol("10*300 = 3000") is False


def test_soft_parse_complete_does_not_leak_raw_json() -> None:
    fallback = {
        "action": "dispatch",
        "to": "composer",
        "goal": "g",
        "acceptance": "a",
        "brief": "b",
        "summary": "",
    }
    got = parse_coord_decision(
        '{"action":"complete","to":"coordinator","go',
        fallback,
    )
    assert got["action"] == COORD_ACTION_COMPLETE
    assert got["summary"] == COPILOT_COORD_COMPLETE_FALLBACK
    assert not looks_like_coord_protocol(got["summary"])


def test_summarize_hides_coord_json() -> None:
    raw = '{"action":"complete","to":"coordinator","goal":""}'
    assert summarize_for_bubble(raw) == COPILOT_COORD_JSON_HIDDEN


def test_heuristic_explain_dispatches_composer_then_completes() -> None:
    board = CrewBoard(
        user_text="thời tiết hôm nay thế nào",
        mode=COPILOT_MODE_CASE,
        ctx_block="",
        intent=INTENT_EXPLAIN,
        complexity=COMPLEXITY_SIMPLE,
    )
    first = heuristic_next_action(
        board, run_analyzer=False, run_optimizer=False, max_critique_rounds=3
    )
    assert first["action"] == COORD_ACTION_DISPATCH
    assert first["to"] == ROLE_COMPOSER
    board.compose_text = "Hôm nay trời nắng, khoảng 30°C."
    second = heuristic_next_action(
        board, run_analyzer=False, run_optimizer=False, max_critique_rounds=3
    )
    assert second["action"] == COORD_ACTION_COMPLETE


def test_explain_roster_skips_critic() -> None:
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    roster = orch._crew_roster(INTENT_EXPLAIN, COMPLEXITY_SIMPLE, {})
    assert ROLE_COMPOSER in roster.roles
    assert ROLE_CRITIC not in roster.roles
    assert roster.run_critic is False


@pytest.mark.asyncio
async def test_casual_question_uses_coordinator_then_composer() -> None:
    """No keyword fast-path — offline coordinator heuristic still answers via composer."""
    from ai_qa_test_studio.constants import EVENT_AGENT_MESSAGE, EVENT_FINAL_REPORT

    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    roles_started: list[str] = []
    composer_ended = False
    kinds: list[str] = []
    async for event in orch.run(
        user_text="thời tiết hôm nay thế nào",
        mode=COPILOT_MODE_CASE,
        context={},
    ):
        kinds.append(event.kind)
        if event.kind == "role_start" and event.role:
            roles_started.append(event.role)
        if event.kind == "role_end" and event.role == ROLE_COMPOSER:
            composer_ended = True
        if event.text:
            assert not looks_like_coord_protocol(event.text) or not event.text.startswith(
                "{"
            )
    assert roles_started[0] == ROLE_COORDINATOR
    assert ROLE_COMPOSER in roles_started
    assert ROLE_CRITIC not in roles_started
    assert composer_ended
    assert EVENT_AGENT_MESSAGE not in kinds
    assert EVENT_FINAL_REPORT not in kinds


@pytest.mark.asyncio
async def test_explain_keeps_coordinator_answer_without_redispatch() -> None:
    """If coordinator already answered, do not force another composer hop."""
    from ai_qa_test_studio.agent.llm_gateway import ChatMessage
    from ai_qa_test_studio.constants import (
        EVENT_AGENT_MESSAGE,
        EVENT_FINAL_REPORT,
        LLM_PROVIDER_OPENAI_COMPAT,
    )

    class _CoordAnswersGateway(UnifiedLLMGateway):
        async def stream_chat(  # type: ignore[override]
            self,
            messages: list[ChatMessage],
            max_tokens: int = 256,
            cancel=None,
        ):
            system = next((m.content for m in messages if m.role == "system"), "").lower()
            if "you are the coordinator" in system:
                yield (
                    '{"action":"complete","summary":'
                    '"Câu trả lời đã có: 10×200+300 = 2.300, kèm cách tính."}'
                )
                return
            if "intent" in system or '"intent"' in system:
                yield '{"intent":"explain"}'
                return
            if "complexity" in system or "simple" in system:
                yield "simple"
                return
            yield "should-not-run-composer"

    orch = CopilotOrchestrator(
        _CoordAnswersGateway(
            provider=LLM_PROVIDER_OPENAI_COMPAT, api_key="test", model="mock"
        )
    )
    composer_texts: list[str] = []
    kinds: list[str] = []
    async for event in orch.run(
        user_text="10*200+300=?",
        mode=COPILOT_MODE_CASE,
        context={},
    ):
        kinds.append(event.kind)
        if event.kind == "role_end" and event.role == ROLE_COMPOSER:
            composer_texts.append(event.text or "")
    assert composer_texts
    assert "2.300" in composer_texts[0]
    assert EVENT_AGENT_MESSAGE not in kinds
    assert EVENT_FINAL_REPORT not in kinds
