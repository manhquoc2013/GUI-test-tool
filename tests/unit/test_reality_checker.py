from __future__ import annotations

from ai_qa_test_studio.agent.reality_checker import RealityChecker
from ai_qa_test_studio.core.models import TestCase, TestStep


def test_reality_checker_blocks_empty_steps() -> None:
    checker = RealityChecker()
    result = checker.check_case(
        {"id": "c1", "code": "TC1", "title": "X", "steps": []}
    )
    assert result.ok is False
    assert any(f.code == "schema" for f in result.findings)


def test_reality_checker_accepts_valid_case() -> None:
    checker = RealityChecker()
    case = TestCase(
        id="c1",
        code="TC1",
        title="Login",
        steps=[
            TestStep(
                step_no=1,
                action="click",
                element_ref="EL",
                intent_hint="submit",
                binding_status="BOUND",
            )
        ],
    )
    result = checker.check_case(case)
    assert result.ok is True
