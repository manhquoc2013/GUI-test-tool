from __future__ import annotations

from ai_qa_test_studio.core.models import TestCase, TestStep
from ai_qa_test_studio.ui.formatting import (
    case_binding_rollups,
    count_unbound_steps,
    format_param_cell,
    humanize_header,
)


def test_format_param_cell_dict() -> None:
    assert format_param_cell({"loaiHoSo": "Cấp mới", "x": 1}) == "loaiHoSo: Cấp mới | x: 1"


def test_humanize_expected_outcome() -> None:
    assert humanize_header("ExpectedOutcome") == "Expected Outcome"
    assert humanize_header("parameters") == "Parameters"


def test_unbound_count_and_rollup() -> None:
    case = TestCase(
        id="c1",
        code="TC1",
        title="t",
        steps=[
            TestStep(step_no=1, action="goto", intent_hint="a", binding_status="UNBOUND"),
            TestStep(step_no=2, action="click", intent_hint="b", binding_status="BOUND"),
        ],
    )
    assert count_unbound_steps(case) == 1
    assert case_binding_rollups(case) == "UNBOUND"
