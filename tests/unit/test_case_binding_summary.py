from __future__ import annotations

from ai_qa_test_studio.agent.case_binding import case_binding_summary
from ai_qa_test_studio.core.models import TestCase, TestStep


def _case(steps: list[TestStep]) -> TestCase:
    return TestCase(id="c1", code="TC1", title="Login", steps=steps)


def test_excludes_goto_wait_assert_url_from_denominator() -> None:
    case = _case(
        [
            TestStep(step_no=1, action="goto", intent_hint="open", binding_status="BOUND"),
            TestStep(
                step_no=2,
                action="fill",
                element_ref="EL_EMAIL",
                intent_hint="email",
                binding_status="BOUND",
            ),
            TestStep(
                step_no=3,
                action="click",
                element_ref="EL_SUBMIT",
                intent_hint="submit",
                binding_status="UNBOUND",
            ),
            TestStep(
                step_no=4,
                action="assert_url",
                intent_hint="url",
                binding_status="BOUND",
            ),
        ]
    )
    summary = case_binding_summary(case)
    assert summary.total_bindable_steps == 2
    assert summary.bound_bindable == 1
    assert summary.is_partial is True
    assert summary.is_fully_bound is False


def test_fully_bound_bindable_only() -> None:
    case = _case(
        [
            TestStep(step_no=1, action="goto", binding_status="UNBOUND"),
            TestStep(
                step_no=2,
                action="click",
                element_ref="EL_A",
                binding_status="BOUND",
            ),
            TestStep(
                step_no=3,
                action="fill",
                element_ref="EL_B",
                binding_status="HEALED",
            ),
        ]
    )
    summary = case_binding_summary(case)
    assert summary.is_fully_bound is True
    assert summary.total_bindable_steps == 2


def test_zero_bindable_is_unbound_not_deterministic() -> None:
    case = _case(
        [
            TestStep(step_no=1, action="goto", binding_status="BOUND"),
            TestStep(step_no=2, action="assert_url", binding_status="BOUND"),
        ]
    )
    summary = case_binding_summary(case)
    assert summary.total_bindable_steps == 0
    assert summary.is_fully_bound is False
    assert summary.is_unbound is True
