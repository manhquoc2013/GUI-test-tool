from __future__ import annotations

from pathlib import Path

from openpyxl import load_workbook

from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    APPLY_KIND_TEST_DATA,
    VN_TC_COL_CASE_ID,
    VN_TC_COL_NUM,
    VN_TC_OVERVIEW_TITLE,
    VN_TC_SHEET_CASES,
    VN_TC_SHEET_IMPORT,
    VN_TC_SHEET_OVERVIEW,
    VN_TC_SHEET_SCENARIOS,
    VN_TC_SHEET_STEPS,
    VN_TC_SHEET_TEST_DATA,
)
from ai_qa_test_studio.core.models import TestCase, TestScenario, TestStep
from ai_qa_test_studio.reporting.vn_test_doc_exporter import CopilotArtifactExcelExporter


def _sample_scenarios() -> list[TestScenario]:
    return [
        TestScenario(
            id="s1",
            code="TS_LOGIN",
            title="Đăng nhập",
            requirement_ref="REQ-01",
            description="Happy path đăng nhập",
            cases=[
                TestCase(
                    id="c1",
                    code="TC_LOGIN_01",
                    title="Đăng nhập thành công",
                    priority="P1 - High",
                    test_type="Functional",
                    preconditions=["Đã mở trang login"],
                    expected_final_result="Vào dashboard",
                    requirement_ref="REQ-01",
                    start_url="https://example.com/login",
                    test_data_rows=[{"username": "user01", "password": "Secret1"}],
                    steps=[
                        TestStep(
                            step_no=1,
                            action="goto",
                            intent_hint="Mở trang login",
                            expected_result="Form login hiển thị",
                        ),
                        TestStep(
                            step_no=2,
                            action="fill",
                            element_ref="EL_USER",
                            input_data="user01",
                            intent_hint="Nhập username",
                            expected_result="Username được điền",
                        ),
                        TestStep(
                            step_no=3,
                            action="click",
                            element_ref="EL_SUBMIT",
                            intent_hint="Bấm Đăng nhập",
                            expected_result="Vào dashboard",
                        ),
                    ],
                ),
                TestCase(
                    id="c2",
                    code="TC_LOGIN_02",
                    title="Sai mật khẩu",
                    priority="P2 - Medium",
                    test_type="Negative",
                    expected_final_result="Báo lỗi mật khẩu",
                    steps=[],
                ),
            ],
        )
    ]


def test_export_compose_payload_has_vn_sheets(tmp_path: Path) -> None:
    scenarios = _sample_scenarios()
    payload = {
        "kind": APPLY_KIND_COMPOSE,
        "scenarios": [s.model_dump(mode="json") for s in scenarios],
    }
    out = tmp_path / "kich_ban.xlsx"
    CopilotArtifactExcelExporter().write(payload, out, summary="Sinh từ SRS login")
    assert out.is_file()

    wb = load_workbook(out)
    assert wb.sheetnames[0] == VN_TC_SHEET_OVERVIEW
    assert VN_TC_SHEET_SCENARIOS in wb.sheetnames
    assert VN_TC_SHEET_CASES in wb.sheetnames
    assert VN_TC_SHEET_STEPS in wb.sheetnames
    assert VN_TC_SHEET_IMPORT in wb.sheetnames
    assert VN_TC_SHEET_TEST_DATA in wb.sheetnames

    overview = wb[VN_TC_SHEET_OVERVIEW]
    assert overview["A1"].value == VN_TC_OVERVIEW_TITLE
    assert overview["B8"].value == 1  # scenarios
    assert overview["B9"].value == 2  # cases

    cases = wb[VN_TC_SHEET_CASES]
    assert cases["A1"].value == VN_TC_COL_NUM
    assert cases["B1"].value == VN_TC_COL_CASE_ID
    assert cases["B2"].value == "TC_LOGIN_01"
    assert cases["B3"].value == "TC_LOGIN_02"

    steps = wb[VN_TC_SHEET_STEPS]
    assert steps["B2"].value == "TC_LOGIN_01"
    assert steps["E2"].value == "goto"
    assert steps.max_row >= 4  # header + 3 steps + empty-case fallback

    scenarios_sheet = wb[VN_TC_SHEET_SCENARIOS]
    assert scenarios_sheet["B2"].value == "TS_LOGIN"

    data = wb[VN_TC_SHEET_TEST_DATA]
    row_values = {data.cell(row=2, column=c).value for c in range(1, data.max_column + 1)}
    assert "user01" in row_values
    assert "TC_LOGIN_01" in row_values


def test_export_test_data_payload(tmp_path: Path) -> None:
    payload = {
        "kind": APPLY_KIND_TEST_DATA,
        "rows": [
            {"field": "age", "value": "17", "expected": "reject"},
            {"field": "age", "value": "18", "expected": "accept"},
        ],
    }
    out = tmp_path / "test_data.xlsx"
    CopilotArtifactExcelExporter().write(payload, out)
    wb = load_workbook(out)
    assert VN_TC_SHEET_OVERVIEW in wb.sheetnames
    assert VN_TC_SHEET_TEST_DATA in wb.sheetnames
    data = wb[VN_TC_SHEET_TEST_DATA]
    assert data.max_row == 3
    assert data["A2"].value == 1
    assert data["B2"].value == "age"


def test_export_rejects_unknown_kind(tmp_path: Path) -> None:
    try:
        CopilotArtifactExcelExporter().write({"kind": "nope"}, tmp_path / "x.xlsx")
        raise AssertionError("expected ValueError")
    except ValueError as exc:
        assert "Unsupported" in str(exc)
