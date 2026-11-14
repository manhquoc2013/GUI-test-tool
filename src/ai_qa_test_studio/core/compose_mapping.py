"""Normalize Copilot / Excel compose payloads into TestScenario / TestCase / TestStep."""

from __future__ import annotations

import re
import uuid
from typing import Any

from ai_qa_test_studio.agent.url_context import extract_http_urls
from ai_qa_test_studio.constants import (
    COPILOT_SUITE_TITLE_SUFFIX,
    DEFAULT_EXPECTED_OUTCOME,
    PLACEHOLDER_PROJECT_NAMES,
    PRIORITY_MAP,
)
from ai_qa_test_studio.core.models import (
    Priority,
    TestCase,
    TestScenario,
    TestStep,
    TestType,
)
from ai_qa_test_studio.core.step_actions import (
    coerce_step_action,
    resolve_element_ref,
    resolve_input_data,
)
from ai_qa_test_studio.core.text_normalize import coerce_str_list, normalize_text_list

_QUOTED_VALUE_RE = re.compile(r"""['"]([^'"]{1,200})['"]""")
_NUMERIC_VALUE_RE = re.compile(
    r"(?<![\w./])(\d+(?:[.,]\d+)?\s*(?:%|ms|s|kb|mb|gb|usd|vnd|\$)?)(?![\w./])",
    re.IGNORECASE,
)
_MODEL_TOKEN_RE = re.compile(
    r"\b(deepseek[-_]?[a-z0-9.]+|gpt[-_]?[a-z0-9.]+|claude[-_]?[a-z0-9.]+|"
    r"gemini[-_]?[a-z0-9.]+|llama[-_]?[a-z0-9.]+)\b",
    re.IGNORECASE,
)


def coerce_preconditions(value: Any) -> list[str]:
    """Coerce preconditions without character-splitting a bare string.

    Semicolon-delimited Excel cells become multiple items; a plain phrase stays one item.
    """
    if isinstance(value, str) and ";" in value:
        parts = [part.strip() for part in value.split(";") if part.strip()]
        return parts
    return coerce_str_list(value)


def extract_input_candidate(text: str) -> str | None:
    """Pull a concrete value (URL, quoted token, model id, number) from prose."""
    blob = str(text or "").strip()
    if not blob:
        return None
    urls = extract_http_urls(blob)
    if urls:
        return urls[0]
    quoted = _QUOTED_VALUE_RE.search(blob)
    if quoted:
        return quoted.group(1).strip() or None
    model = _MODEL_TOKEN_RE.search(blob)
    if model:
        return model.group(1)
    numeric = _NUMERIC_VALUE_RE.search(blob)
    if numeric:
        return numeric.group(1).strip()
    return None


def resolve_step_input_data(raw_step: dict[str, Any], *, intent_hint: str = "") -> str | None:
    """Prefer explicit input fields, then harvest values from intent / expected text."""
    explicit = resolve_input_data(raw_step)
    if explicit:
        return explicit
    for key in ("data_input", "value", "url", "payload"):
        value = raw_step.get(key)
        if value is None:
            continue
        text = str(value).strip()
        if text:
            return text
    for candidate in (
        intent_hint,
        str(raw_step.get("description") or ""),
        str(raw_step.get("expected_result") or ""),
        str(raw_step.get("action") or ""),
    ):
        harvested = extract_input_candidate(candidate)
        if harvested:
            return harvested
    return None


def _parse_priority(raw: object) -> Priority:
    text = str(raw or "").strip()
    if not text:
        return "P1 - High"
    if text in {"P0 - Critical", "P1 - High", "P2 - Medium", "P3 - Low"}:
        return text  # type: ignore[return-value]
    mapped = PRIORITY_MAP.get(text.upper()[:2])
    if mapped is not None:
        return mapped  # type: ignore[return-value]
    lowered = text.lower()
    if "critical" in lowered or "p0" in lowered:
        return "P0 - Critical"
    if "high" in lowered or "p1" in lowered:
        return "P1 - High"
    if "medium" in lowered or "p2" in lowered:
        return "P2 - Medium"
    if "low" in lowered or "p3" in lowered:
        return "P3 - Low"
    return "P1 - High"


def _parse_test_type(raw: object) -> TestType:
    text = str(raw or "").strip()
    allowed = {"Functional", "Negative", "Boundary", "UI/UX", "Security", "API"}
    if text in allowed:
        return text  # type: ignore[return-value]
    lowered = text.lower()
    if "api" in lowered:
        return "API"
    if "neg" in lowered:
        return "Negative"
    if "bound" in lowered or "bva" in lowered:
        return "Boundary"
    if "ui" in lowered or "ux" in lowered:
        return "UI/UX"
    if "sec" in lowered:
        return "Security"
    return "Functional"


def normalize_raw_step(raw_step: dict[str, Any], *, index: int = 1) -> TestStep:
    intent_hint = str(
        raw_step.get("intent_hint")
        or raw_step.get("description")
        or raw_step.get("target")
        or raw_step.get("action")
        or ""
    )
    action = coerce_step_action(
        raw_step.get("action"),
        intent_hint=intent_hint,
        description=str(raw_step.get("description") or ""),
    )
    try:
        step_no = int(raw_step.get("step_no") or index)
    except (TypeError, ValueError):
        step_no = index
    return TestStep(
        step_no=step_no,
        action=action,
        element_ref=resolve_element_ref(raw_step),
        intent_hint=intent_hint,
        input_data=resolve_step_input_data(raw_step, intent_hint=intent_hint),
        expected_result=str(raw_step.get("expected_result") or ""),
        binding_status="UNBOUND",
    )


def enrich_step_input_data(step: TestStep) -> TestStep:
    """Fill empty ``input_data`` from intent / expected prose when a concrete value exists."""
    if (step.input_data or "").strip():
        return step
    harvested = extract_input_candidate(
        " ".join(
            part
            for part in (step.intent_hint, step.expected_result, step.element_ref or "")
            if part
        )
    )
    if harvested:
        step.input_data = harvested
    return step


def case_has_concrete_data(case: TestCase) -> bool:
    if (case.expected_final_result or "").strip():
        return True
    for step in case.steps:
        if (step.input_data or "").strip() or (step.expected_result or "").strip():
            return True
    return False


def build_test_data_row(case: TestCase) -> dict[str, Any]:
    parameters: dict[str, str] = {}
    for step in case.steps:
        value = (step.input_data or "").strip()
        if not value:
            continue
        key = (step.intent_hint or step.element_ref or f"step_{step.step_no}").strip()
        key = key[:80] or f"step_{step.step_no}"
        parameters[key] = value
    expected = (case.expected_final_result or "").strip()
    if not expected:
        for step in reversed(case.steps):
            if (step.expected_result or "").strip():
                expected = step.expected_result.strip()
                break
    if not expected:
        expected = str(DEFAULT_EXPECTED_OUTCOME)
    return {
        "scenario": case.title or case.code,
        "parameters": parameters,
        "expected_outcome": expected,
    }


def ensure_case_test_data_rows(case: TestCase) -> TestCase:
    """Ensure cases with concrete inputs/expectations get ≥1 Test Data drawer row."""
    if case.test_data_rows:
        return case
    if not case_has_concrete_data(case):
        return case
    case.test_data_rows = [build_test_data_row(case)]
    return case


def normalize_raw_case(
    raw_case: dict[str, Any],
    *,
    scenario_code: str = "TS",
    requirement_ref: str = "",
) -> TestCase:
    steps_raw = raw_case.get("steps") or []
    steps: list[TestStep] = []
    if isinstance(steps_raw, list):
        for index, item in enumerate(steps_raw, start=1):
            if isinstance(item, dict):
                steps.append(enrich_step_input_data(normalize_raw_step(item, index=index)))
    description = str(raw_case.get("description") or "")
    expected_final = str(
        raw_case.get("expected_final_result")
        or raw_case.get("expected_result")
        or raw_case.get("expected_outcome")
        or ""
    )
    start_url = str(raw_case.get("start_url") or "").strip()
    if not start_url:
        for step in steps:
            if step.action in {"navigate", "goto"} and (step.input_data or "").strip():
                start_url = str(step.input_data).strip()
                break
            urls = extract_http_urls(step.intent_hint or "")
            if urls:
                start_url = urls[0]
                break
    case = TestCase(
        id=str(raw_case.get("id") or uuid.uuid4()),
        code=str(raw_case.get("code") or f"{scenario_code}_C01"),
        title=str(raw_case.get("title") or raw_case.get("name") or "Imported case"),
        priority=_parse_priority(raw_case.get("priority")),
        test_type=_parse_test_type(raw_case.get("test_type")),
        preconditions=coerce_preconditions(raw_case.get("preconditions")),
        test_data_rows=list(raw_case.get("test_data_rows") or []),
        expected_final_result=expected_final,
        steps=steps,
        requirement_ref=str(raw_case.get("requirement_ref") or requirement_ref or ""),
        start_url=start_url,
        description=description,
    )
    return ensure_case_test_data_rows(case)


def normalize_raw_scenario(raw_scenario: dict[str, Any]) -> TestScenario:
    code = str(raw_scenario.get("code") or f"TS_{uuid.uuid4().hex[:6].upper()}")
    requirement_ref = str(raw_scenario.get("requirement_ref") or "")
    cases_raw = raw_scenario.get("cases") or []
    cases: list[TestCase] = []
    if isinstance(cases_raw, list):
        for item in cases_raw:
            if isinstance(item, dict):
                cases.append(
                    normalize_raw_case(
                        item,
                        scenario_code=code,
                        requirement_ref=requirement_ref,
                    )
                )
            elif isinstance(item, TestCase):
                cases.append(ensure_case_test_data_rows(enrich_case_steps(item)))
    return TestScenario(
        id=str(raw_scenario.get("id") or uuid.uuid4()),
        code=code,
        title=str(raw_scenario.get("title") or raw_scenario.get("name") or code),
        requirement_ref=requirement_ref,
        description=str(raw_scenario.get("description") or ""),
        cases=cases,
    )


def enrich_case_steps(case: TestCase) -> TestCase:
    case.steps = [enrich_step_input_data(step) for step in case.steps]
    return case


def normalize_compose_scenarios(scenarios_raw: list[Any]) -> list[TestScenario]:
    """Map Copilot Apply payload scenarios (dicts or models) into validated models."""
    out: list[TestScenario] = []
    for item in scenarios_raw:
        if isinstance(item, TestScenario):
            for case in item.cases:
                enrich_case_steps(case)
                ensure_case_test_data_rows(case)
            out.append(item)
            continue
        if isinstance(item, dict):
            out.append(normalize_raw_scenario(item))
    return out


def extract_start_url(
    scenarios: list[TestScenario],
    *,
    payload_start_url: str = "",
) -> str:
    explicit = str(payload_start_url or "").strip()
    if explicit:
        return explicit
    for scenario in scenarios:
        for case in scenario.cases:
            if (case.start_url or "").strip():
                return case.start_url.strip()
            for step in case.steps:
                if step.action in {"navigate", "goto"} and (step.input_data or "").strip():
                    return str(step.input_data).strip()
                urls = extract_http_urls(
                    " ".join(
                        part
                        for part in (step.input_data or "", step.intent_hint, step.expected_result)
                        if part
                    )
                )
                if urls:
                    return urls[0]
    return ""


def suggest_project_name(scenarios: list[TestScenario], *, payload_title: str = "") -> str:
    base = str(payload_title or "").strip()
    if not base and scenarios:
        base = (scenarios[0].title or scenarios[0].code or "").strip()
    if not base:
        base = "Copilot Suite"
    if base.endswith(COPILOT_SUITE_TITLE_SUFFIX.strip()) or base.endswith("Copilot Suite"):
        return base
    return f"{base}{COPILOT_SUITE_TITLE_SUFFIX}"


def should_rename_project(current_name: str) -> bool:
    return (current_name or "").strip() in PLACEHOLDER_PROJECT_NAMES


def apply_start_url_to_cases(scenarios: list[TestScenario], start_url: str) -> None:
    if not start_url:
        return
    for scenario in scenarios:
        for case in scenario.cases:
            if not (case.start_url or "").strip():
                case.start_url = start_url


def format_preconditions_cell(value: str | list[str] | tuple[str, ...] | None) -> str:
    """Export-safe preconditions cell (never joins characters of a str)."""
    return normalize_text_list(value)
