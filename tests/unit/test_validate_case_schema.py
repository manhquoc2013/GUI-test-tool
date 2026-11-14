from __future__ import annotations

from ai_qa_test_studio.agent.validate_case_schema import validate_case_schema
from ai_qa_test_studio.core.models import TestCase, TestStep


def test_validate_ok() -> None:
    case = TestCase(
        id="c1",
        code="TC1",
        title="Login",
        steps=[TestStep(step_no=1, action="click", element_ref="EL_1")],
    )
    ok, msg, parsed = validate_case_schema(case)
    assert ok is True
    assert msg == "ok"
    assert parsed is not None


def test_validate_rejects_empty_steps() -> None:
    ok, msg, parsed = validate_case_schema(
        {"id": "c1", "code": "TC1", "title": "X", "steps": []}
    )
    assert ok is False
    assert parsed is None
    assert "step" in msg.lower() or "APPLY" in msg
