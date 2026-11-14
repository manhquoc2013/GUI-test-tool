"""case_binding_summary — bindable UI steps only (exclude Navigate/Wait/AssertUrl)."""

from __future__ import annotations

from dataclasses import dataclass

from ai_qa_test_studio.constants import (
    BINDABLE_STEP_ACTIONS,
    BINDING_BOUND,
    BINDING_HEALED,
    NON_BINDABLE_STEP_ACTIONS,
)
from ai_qa_test_studio.core.models import TestCase, TestStep


@dataclass(frozen=True)
class CaseBindingSummary:
    total_bindable_steps: int
    bound_bindable: int
    is_fully_bound: bool
    is_partial: bool
    is_unbound: bool


def is_bindable_step(step: TestStep) -> bool:
    if not step.enabled:
        return False
    action = str(step.action or "")
    if action in NON_BINDABLE_STEP_ACTIONS:
        return False
    if action in BINDABLE_STEP_ACTIONS:
        return True
    # Unknown action: bindable only if it references an element
    return bool(step.element_ref)


def _is_bound_status(status: str) -> bool:
    return status in {BINDING_BOUND, BINDING_HEALED}


def case_binding_summary(case: TestCase) -> CaseBindingSummary:
    bindable = [s for s in case.steps if is_bindable_step(s)]
    total = len(bindable)
    bound = sum(1 for s in bindable if _is_bound_status(str(s.binding_status)))
    if total == 0:
        # No bindable targets → not deterministic; treat as unbound for script gate
        return CaseBindingSummary(
            total_bindable_steps=0,
            bound_bindable=0,
            is_fully_bound=False,
            is_partial=False,
            is_unbound=True,
        )
    fully = bound == total
    partial = 0 < bound < total
    unbound = bound == 0
    return CaseBindingSummary(
        total_bindable_steps=total,
        bound_bindable=bound,
        is_fully_bound=fully,
        is_partial=partial,
        is_unbound=unbound,
    )
