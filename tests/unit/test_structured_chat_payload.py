"""Structured payload summary cards + TEST_SCENARIO stage isolation."""

from __future__ import annotations

from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    COPILOT_STRUCTURED_DRAFT_BANNER,
    ROLE_COMPOSER,
)
from ai_qa_test_studio.core.chat_models import CopilotMessage
from ai_qa_test_studio.core.enums import IntentType
from ai_qa_test_studio.ui.bubble_summary import (
    extract_structured_payload,
    format_structured_payload_summary,
    summarize_for_bubble,
)
from ai_qa_test_studio.ui.controllers.copilot_controller import clip_chat_reply
from ai_qa_test_studio.ui.widgets.chat_message_row import ChatMessageRow


def _sample_payload() -> dict:
    return {
        "kind": APPLY_KIND_COMPOSE,
        "scenarios": [
            {
                "code": "TS-01",
                "title": "Đăng nhập thành công",
                "cases": [
                    {"code": "TC-01", "title": "Happy path"},
                    {"code": "TC-02", "title": "Remember me"},
                ],
            },
            {
                "code": "TS-02",
                "title": "Đăng nhập thất bại",
                "cases": [{"code": "TC-03", "title": "Sai mật khẩu"}],
            },
        ],
    }


def test_structured_summary_hides_raw_json_keeps_payload() -> None:
    payload = _sample_payload()
    raw = (
        'Dưới đây là draft:\n```json\n'
        + '{"code":"TS-01","title":"x","cases":[{"code":"TC-01","steps":[]}]}'
        + "\n```\n"
        + str(payload).replace("'", '"')
    )
    # Prefer explicit structured payload (Apply source of truth).
    display = clip_chat_reply(raw, structured_payload=payload)
    assert '{"code":' not in display
    assert COPILOT_STRUCTURED_DRAFT_BANNER in display
    assert "TS-01" in display
    assert "Đăng nhập thành công" in display
    assert "Happy path" in display

    msg = CopilotMessage(role=ROLE_COMPOSER, content=raw, summary=display)
    msg.structured_payload = payload
    assert msg.structured_payload == payload
    assert msg.apply_payload == payload
    assert '{"code":' not in msg.bubble_text()


def test_chat_bubble_renders_summary_not_raw_json(qapp: QApplication) -> None:
    payload = _sample_payload()
    summary = format_structured_payload_summary(payload)
    assert summary
    assert '{"code":' not in summary
    row = ChatMessageRow("m-struct", ROLE_COMPOSER, summary)
    row.show()
    qapp.processEvents()
    visible = row.body.text()
    assert '{"code":' not in visible
    assert "TS-01" in visible or "TS-01" in summary
    row.close()


def test_test_scenario_stage_strips_nested_steps() -> None:
    from ai_qa_test_studio.agent.orchestrator import CopilotOrchestrator

    nested = {
        "kind": APPLY_KIND_COMPOSE,
        "scenarios": [
            {
                "code": "TS-01",
                "title": "Login",
                "cases": [
                    {
                        "code": "TC-01",
                        "title": "Happy",
                        "steps": [
                            {
                                "step_no": 1,
                                "action": "fill",
                                "intent_hint": "username",
                                "expected_result": "ok",
                            }
                        ],
                    }
                ],
            }
        ],
    }
    cleaned = CopilotOrchestrator._strip_scenario_stage_nesting(nested)
    assert cleaned["scenarios"][0]["cases"] == []
    for scenario in cleaned["scenarios"]:
        assert "steps" not in scenario
        for case in scenario.get("cases") or []:
            assert "steps" not in case

    schema = CopilotOrchestrator._composer_schema_for_stage(IntentType.TEST_SCENARIO.value)
    assert "Do NOT nest cases or steps" in schema
    case_schema = CopilotOrchestrator._composer_schema_for_stage(
        IntentType.TEST_CASE_GEN.value
    )
    assert "steps" in case_schema
    assert "Cap at" in case_schema


def test_extract_payload_and_summarize_roundtrip() -> None:
    blob = (
        '{"kind":"compose_scenarios","scenarios":['
        '{"code":"TS-09","title":"Checkout","cases":[{"code":"TC-1","title":"Pay"}]}'
        "]}"
    )
    payload = extract_structured_payload(blob)
    assert payload is not None
    out = summarize_for_bubble(blob, structured_payload=payload)
    assert '{"code":' not in out
    assert "TS-09" in out
    assert COPILOT_STRUCTURED_DRAFT_BANNER in out
