"""Apply/Import mapping: steps, actions, Test Data, Target URL, preconditions."""

from __future__ import annotations

from pathlib import Path

from openpyxl import Workbook

from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    COPILOT_SUITE_TITLE_SUFFIX,
    DEFAULT_TARGET_URL,
)
from ai_qa_test_studio.core.compose_mapping import (
    coerce_preconditions,
    extract_start_url,
    format_preconditions_cell,
    normalize_compose_scenarios,
    normalize_raw_step,
    suggest_project_name,
)
from ai_qa_test_studio.core.text_normalize import normalize_text_list
from ai_qa_test_studio.core.wizard_documents import load_wizard_document


def _pricing_payload() -> dict:
    return {
        "kind": APPLY_KIND_COMPOSE,
        "title": "Pricing DeepSeek",
        "start_url": "",
        "scenarios": [
            {
                "code": "TS_PRICING",
                "title": "Pricing DeepSeek",
                "description": "Pricing page coverage",
                "requirement_ref": "REQ-PRICE",
                "cases": [
                    {
                        "code": "TC_NAV",
                        "title": "Open pricing",
                        "priority": "P1",
                        "preconditions": "Cookie consent accepted",
                        "description": "Navigate to pricing docs",
                        "steps": [
                            {
                                "step_no": 1,
                                "action": "Navigate to pricing",
                                "intent_hint": "Open https://api-docs.deepseek.com/quick_start/pricing",
                                "expected_result": "Pricing page loads",
                            },
                            {
                                "step_no": 2,
                                "action": "scroll",
                                "intent_hint": "Cuộn xuống bảng giá deepseek-chat",
                                "expected_result": "Bảng giá hiển thị",
                            },
                            {
                                "step_no": 3,
                                "action": "verify",
                                "target": "price table",
                                "intent_hint": "Kiểm tra giá deepseek-chat",
                                "data_input": "",
                                "expected_result": "Giá > 0",
                            },
                        ],
                    },
                    {
                        "code": "TC_ASSERT",
                        "title": "Assert model price",
                        "preconditions": ["Logged in", "Plan selected"],
                        "steps": [
                            {
                                "step_no": 1,
                                "action": "assert",
                                "intent_hint": "Đối chiếu giá model deepseek-reasoner",
                                "input_data": "0.55",
                                "expected_result": "Giá khớp tài liệu",
                            }
                        ],
                    },
                ],
            }
        ],
    }


def test_normalize_text_list_never_joins_characters() -> None:
    phrase = "Cookie consent accepted"
    assert normalize_text_list(phrase) == phrase
    assert format_preconditions_cell(phrase) == phrase
    assert coerce_preconditions(phrase) == [phrase]
    assert coerce_preconditions("A; B; C") == ["A", "B", "C"]
    assert normalize_text_list(["A", "B"]) == "A; B"
    # Character-join bug: joining a str must never become "C; o; o; k; i; e..."
    broken = "; ".join(phrase)
    assert broken != phrase
    assert normalize_text_list(phrase) != broken


def test_normalize_raw_step_infers_actions_and_data_input() -> None:
    navigate = normalize_raw_step(
        {
            "action": "Open page",
            "intent_hint": "Truy cập https://api-docs.deepseek.com/quick_start/pricing",
            "expected_result": "page ready",
        }
    )
    assert navigate.action == "navigate"
    assert navigate.input_data == "https://api-docs.deepseek.com/quick_start/pricing"
    assert navigate.expected_result == "page ready"

    scroll = normalize_raw_step({"action": "kéo xuống", "intent_hint": "Cuộn bảng giá"})
    assert scroll.action == "scroll"

    assert_step = normalize_raw_step(
        {
            "action": "check",
            "target": "price cell",
            "intent_hint": "Kiểm tra giá",
            "data_input": "0.14",
            "expected_result": "ok",
        }
    )
    assert assert_step.action == "assert"
    assert assert_step.element_ref == "price cell"
    assert assert_step.input_data == "0.14"
    assert assert_step.expected_result == "ok"


def test_normalize_compose_scenarios_builds_test_data_and_url() -> None:
    scenarios = normalize_compose_scenarios(_pricing_payload()["scenarios"])
    assert len(scenarios) == 1
    assert scenarios[0].title == "Pricing DeepSeek"
    cases = scenarios[0].cases
    assert len(cases) == 2

    nav = cases[0]
    assert nav.preconditions == ["Cookie consent accepted"]
    assert nav.steps[0].action == "navigate"
    assert nav.steps[0].expected_result == "Pricing page loads"
    assert nav.steps[0].input_data == "https://api-docs.deepseek.com/quick_start/pricing"
    assert nav.steps[1].action == "scroll"
    assert nav.steps[2].action == "assert"
    assert nav.steps[2].element_ref == "price table"
    assert len(nav.test_data_rows) >= 1

    assert_case = cases[1]
    assert assert_case.preconditions == ["Logged in", "Plan selected"]
    assert assert_case.steps[0].action == "assert"
    assert assert_case.steps[0].input_data == "0.55"
    assert len(assert_case.test_data_rows) >= 1

    start_url = extract_start_url(scenarios, payload_start_url="")
    assert start_url == "https://api-docs.deepseek.com/quick_start/pricing"
    assert suggest_project_name(scenarios).endswith(COPILOT_SUITE_TITLE_SUFFIX.strip()) or (
        COPILOT_SUITE_TITLE_SUFFIX in suggest_project_name(scenarios)
    )
    assert DEFAULT_TARGET_URL not in start_url


def test_excel_import_preserves_expected_and_actions(tmp_path: Path) -> None:
    path = tmp_path / "pricing_cases.xlsx"
    wb = Workbook()
    ws = wb.active
    assert ws is not None
    ws.title = "Test Cases"
    ws.append(
        [
            "Test Case ID",
            "Scenario ID",
            "Title",
            "Pre-conditions",
            "Step No",
            "Action",
            "Target",
            "Test Data",
            "Expected Result",
        ]
    )
    ws.append(
        [
            "TC01",
            "TS_PRICE",
            "Pricing",
            "Cookie consent accepted",
            1,
            "navigate",
            "",
            "https://api-docs.deepseek.com/quick_start/pricing",
            "Page opens",
        ]
    )
    ws.append(
        [
            "TC01",
            "TS_PRICE",
            "Pricing",
            "",
            2,
            "scroll",
            "pricing table",
            "",
            "Table visible",
        ]
    )
    ws.append(
        [
            "TC01",
            "TS_PRICE",
            "Pricing",
            "",
            3,
            "assert",
            "price",
            "0.14",
            "Price matches",
        ]
    )
    wb.save(path)

    doc = load_wizard_document(path)
    assert doc.error == ""
    assert len(doc.scenarios) == 1
    case = doc.scenarios[0].cases[0]
    assert case.preconditions == ["Cookie consent accepted"]
    assert [s.action for s in case.steps] == ["navigate", "scroll", "assert"]
    assert case.steps[0].input_data == "https://api-docs.deepseek.com/quick_start/pricing"
    assert case.steps[0].expected_result == "Page opens"
    assert case.steps[1].element_ref == "pricing table"
    assert case.steps[2].input_data == "0.14"
    assert case.steps[2].expected_result == "Price matches"
    assert len(case.test_data_rows) > 0
