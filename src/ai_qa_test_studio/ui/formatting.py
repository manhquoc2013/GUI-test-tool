"""UI-facing format helpers (no Qt dependency)."""

from __future__ import annotations

from typing import Any

from ai_qa_test_studio.agent.case_binding import case_binding_summary
from ai_qa_test_studio.constants import BINDING_BOUND, BINDING_UNBOUND, PARAM_CELL_SEP
from ai_qa_test_studio.core.models import TestCase


def count_unbound_steps(case: TestCase) -> int:
    """Remaining bindable UNBOUND steps (Navigate/Wait/AssertUrl excluded)."""
    summary = case_binding_summary(case)
    return summary.total_bindable_steps - summary.bound_bindable


def case_binding_rollups(case: TestCase) -> str:
    if case.last_status == "FAIL":
        return "FAILED"
    summary = case_binding_summary(case)
    if summary.is_fully_bound:
        return BINDING_BOUND
    return BINDING_UNBOUND


def format_param_cell(value: Any) -> str:
    """Render dict/list parameters as a single-line QA-friendly summary."""
    if value is None:
        return ""
    if isinstance(value, dict):
        if not value:
            return ""
        return PARAM_CELL_SEP.join(f"{key}: {value[key]}" for key in value)
    if isinstance(value, list):
        return PARAM_CELL_SEP.join(format_param_cell(item) for item in value)
    return str(value)


def humanize_header(key: str) -> str:
    mapping = {
        "expectedoutcome": "Expected Outcome",
        "expected_outcome": "Expected Outcome",
        "expectedresult": "Expected Result",
        "parameters": "Parameters",
        "input_data": "Input Data",
    }
    lowered = key.replace(" ", "").lower()
    if lowered in mapping:
        return mapping[lowered]
    if "_" in key:
        return " ".join(part.capitalize() for part in key.split("_"))
    if key and key[0].islower() and any(c.isupper() for c in key[1:]):
        # camelCase → Title Case words
        chars: list[str] = []
        for ch in key:
            if ch.isupper() and chars:
                chars.append(" ")
            chars.append(ch)
        return "".join(chars).title()
    return key
