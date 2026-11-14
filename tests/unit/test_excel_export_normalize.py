"""Excel/Copilot export: preconditions string safety + action/target mapping."""

from __future__ import annotations

from pathlib import Path

from openpyxl import load_workbook

from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    VN_TC_COL_ACTION,
    VN_TC_COL_PRECONDITIONS,
    VN_TC_COL_TARGET,
    VN_TC_COL_TEST_DATA,
    VN_TC_SHEET_CASES,
    VN_TC_SHEET_STEPS,
)
from ai_qa_test_studio.core.models import Project, TestCase, TestGroup, TestScenario, TestStep
from ai_qa_test_studio.core.step_actions import coerce_step_action, step_target_label
from ai_qa_test_studio.core.text_normalize import coerce_str_list, normalize_text_list
from ai_qa_test_studio.reporting.excel_exporter import ExcelExporter
from ai_qa_test_studio.reporting.vn_test_doc_exporter import (
    CopilotArtifactExcelExporter,
    format_preconditions,
)


def test_normalize_text_list_keeps_string_verbatim() -> None:
    raw = "Trang pricing tải thành công"
    assert normalize_text_list(raw) == raw
    assert "; " not in normalize_text_list(raw)
    assert format_preconditions(raw) == raw


def test_normalize_text_list_joins_lists_only() -> None:
    assert normalize_text_list(["A", "B"]) == "A; B"
    assert normalize_text_list(None) == ""
    assert coerce_str_list("Trang pricing tải thành công") == ["Trang pricing tải thành công"]
    # Character-split regression: iterating a str must not produce one char per item.
    broken = [str(ch) for ch in "Trang pricing tải thành công"]
    assert normalize_text_list(broken) != "Trang pricing tải thành công"
    assert coerce_str_list("hello") == ["hello"]


def test_testcase_preconditions_validator_rejects_char_split() -> None:
    case = TestCase(
        id="c1",
        code="TC_01",
        title="Pricing",
        preconditions="Trang pricing tải thành công",  # type: ignore[arg-type]
    )
    assert case.preconditions == ["Trang pricing tải thành công"]
    assert format_preconditions(case.preconditions) == "Trang pricing tải thành công"


def test_infer_navigate_scroll_assert_actions() -> None:
    # Explicit valid token wins over hint.
    assert coerce_step_action("click", intent_hint="Navigate to pricing page") == "click"
    assert coerce_step_action(None, intent_hint="Navigate to pricing page") == "navigate"
    assert coerce_step_action("navigate") == "navigate"
    assert coerce_step_action(None, intent_hint="Scroll to pricing table") == "scroll"
    assert coerce_step_action("scroll") == "scroll"
    assert coerce_step_action(None, intent_hint="Check deepseek-flash price row") == "assert"
    assert coerce_step_action("assert") == "assert"
    assert coerce_step_action(None, intent_hint="Send request to /v1/models") == "api_request"
    assert coerce_step_action("click", intent_hint="Bấm nút Đăng nhập") == "click"


def test_unbound_target_uses_intent_hint() -> None:
    step = TestStep(
        step_no=1,
        action="assert",
        intent_hint="Bảng giá PRICING",
        binding_status="UNBOUND",
    )
    assert step_target_label(step) == "Bảng giá PRICING"
    assert step.element_ref is None


def test_vn_exporter_preconditions_string_and_action_target(tmp_path: Path) -> None:
    scenario = TestScenario(
        id="s1",
        code="TS_PRICE",
        title="Pricing",
        cases=[
            TestCase(
                id="c1",
                code="TC_PRICE_01",
                title="Xác minh bảng giá",
                preconditions="Trang pricing tải thành công",  # type: ignore[arg-type]
                steps=[
                    TestStep(
                        step_no=1,
                        action="navigate",
                        intent_hint="Mở trang Pricing",
                        input_data="https://example.com/pricing",
                        expected_result="Trang hiển thị",
                    ),
                    TestStep(
                        step_no=2,
                        action="scroll",
                        intent_hint="Bảng giá PRICING",
                        expected_result="Bảng nằm trong viewport",
                    ),
                    TestStep(
                        step_no=3,
                        action="assert",
                        intent_hint="Dòng cache hit - deepseek-flash",
                        input_data="model=deepseek-flash; expected_price=$0.003",
                        expected_result="Giá khớp $0.003",
                    ),
                ],
            )
        ],
    )
    out = tmp_path / "kich_ban.xlsx"
    CopilotArtifactExcelExporter().write(
        {
            "kind": APPLY_KIND_COMPOSE,
            "scenarios": [scenario.model_dump(mode="json")],
        },
        out,
    )
    wb = load_workbook(out)
    cases = wb[VN_TC_SHEET_CASES]
    headers = [cases.cell(1, c).value for c in range(1, cases.max_column + 1)]
    pre_col = headers.index(VN_TC_COL_PRECONDITIONS) + 1
    pre_value = str(cases.cell(2, pre_col).value or "")
    assert "Trang pricing tải thành công" in pre_value
    assert not pre_value.startswith("T; r")
    assert "T; r; a; n; g" not in pre_value
    # start_url may be prefixed by the exporter; core phrase must stay intact.
    assert "; ".join("Trang pricing tải thành công") not in pre_value

    steps = wb[VN_TC_SHEET_STEPS]
    step_headers = [steps.cell(1, c).value for c in range(1, steps.max_column + 1)]
    action_col = step_headers.index(VN_TC_COL_ACTION) + 1
    target_col = step_headers.index(VN_TC_COL_TARGET) + 1
    data_col = step_headers.index(VN_TC_COL_TEST_DATA) + 1

    actions = [steps.cell(r, action_col).value for r in range(2, 5)]
    targets = [steps.cell(r, target_col).value for r in range(2, 5)]
    assert actions == ["navigate", "scroll", "assert"]
    assert targets[0] == "Mở trang Pricing"
    assert targets[1] == "Bảng giá PRICING"
    assert targets[2] == "Dòng cache hit - deepseek-flash"
    assert all(t not in (None, "", "nan", "NaN") for t in targets)
    assert steps.cell(4, data_col).value == "model=deepseek-flash; expected_price=$0.003"


def test_ieee_excel_exporter_preconditions_string(tmp_path: Path) -> None:
    project = Project(
        id="p1",
        name="demo",
        root_group=TestGroup(
            id="g1",
            name="root",
            scenarios=[
                TestScenario(
                    id="s1",
                    code="TS_01",
                    title="Scenario",
                    cases=[
                        TestCase(
                            id="c1",
                            code="TC_01",
                            title="Case",
                            preconditions="Trang pricing tải thành công",  # type: ignore[arg-type]
                            steps=[
                                TestStep(
                                    step_no=1,
                                    action="navigate",
                                    intent_hint="Mở trang",
                                )
                            ],
                        )
                    ],
                )
            ],
        ),
    )
    out = tmp_path / "report.xlsx"
    ExcelExporter().write(project, None, out)
    wb = load_workbook(out)
    sheet = wb["Test Cases (IEEE 829)"]
    # Headers row 1; data row 2 — Pre-conditions is column 6
    pre = str(sheet.cell(2, 6).value or "")
    assert pre == "Trang pricing tải thành công"
    assert "; " not in pre
