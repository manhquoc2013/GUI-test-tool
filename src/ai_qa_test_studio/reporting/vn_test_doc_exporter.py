"""Vietnamese QA test-document Excel export (ISTQB / IEEE 829 style).

Produces the standard multi-sheet workbook used by VN QA teams:
Tổng quan · Kịch bản · Danh sách Test Cases · Chi tiết các bước ·
(+ Dữ liệu kiểm thử / Test Cases Import when applicable).

Pure reporting — no PySide6.
"""

from __future__ import annotations

from datetime import datetime
from pathlib import Path
from typing import Any

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.worksheet import Worksheet
from pydantic import ValidationError

from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    APPLY_KIND_STEPS,
    APPLY_KIND_TEST_DATA,
    VN_TC_COL_ACTION,
    VN_TC_COL_CASE_ID,
    VN_TC_COL_CASE_NAME,
    VN_TC_COL_DESCRIPTION,
    VN_TC_COL_EXPECTED,
    VN_TC_COL_GROUP,
    VN_TC_COL_NOTES,
    VN_TC_COL_NUM,
    VN_TC_COL_PRECONDITIONS,
    VN_TC_COL_PRIORITY,
    VN_TC_COL_REQUIREMENT,
    VN_TC_COL_SCENARIO_ID,
    VN_TC_COL_SCENARIO_TITLE,
    VN_TC_COL_STATUS,
    VN_TC_COL_STEP_DESC,
    VN_TC_COL_STEP_NO,
    VN_TC_COL_STEPS_SUMMARY,
    VN_TC_COL_SUITE,
    VN_TC_COL_TARGET,
    VN_TC_COL_TEST_DATA,
    VN_TC_COL_TYPE,
    VN_TC_DEFAULT_GROUP,
    VN_TC_DEFAULT_PRECONDITION,
    VN_TC_EXCEL_MAX_CELL_CHARS,
    VN_TC_HEADER_FILL,
    VN_TC_HEADER_FONT_COLOR,
    VN_TC_OVERVIEW_STANDARD,
    VN_TC_OVERVIEW_TITLE,
    VN_TC_OVERVIEW_TOOL,
    VN_TC_SHEET_CASES,
    VN_TC_SHEET_IMPORT,
    VN_TC_SHEET_OVERVIEW,
    VN_TC_SHEET_SCENARIOS,
    VN_TC_SHEET_STEPS,
    VN_TC_SHEET_TEST_DATA,
    VN_TC_STATUS_DRAFT,
    VN_TC_ZEBRA_FILL,
    WIZARD_DOC_HEADER_ACTION,
    WIZARD_DOC_HEADER_CASE_ID,
    WIZARD_DOC_HEADER_DESCRIPTION,
    WIZARD_DOC_HEADER_EXPECTED,
    WIZARD_DOC_HEADER_PRECONDITIONS,
    WIZARD_DOC_HEADER_REQUIREMENT,
    WIZARD_DOC_HEADER_SCENARIO_ID,
    WIZARD_DOC_HEADER_STEP_NO,
    WIZARD_DOC_HEADER_TEST_DATA,
    WIZARD_DOC_HEADER_TITLE,
)
from ai_qa_test_studio.core.models import TestCase, TestScenario, TestStep

_CASES_WIDTHS = (7, 18, 30, 18, 22, 14, 16, 14, 28, 36, 24, 36, 22)
_STEPS_WIDTHS = (7, 18, 28, 10, 18, 28, 24, 36, 34)
_SCENARIO_WIDTHS = (7, 18, 36, 22, 44)
_IMPORT_WIDTHS = (16, 14, 10, 14, 28, 22, 32, 28, 18, 28)


def safe_cell_text(value: Any) -> str:
    text = "" if value is None else str(value)
    if len(text) > VN_TC_EXCEL_MAX_CELL_CHARS:
        return text[:VN_TC_EXCEL_MAX_CELL_CHARS] + "\n... [TRUNCATED]"
    return text


class CopilotArtifactExcelExporter:
    """Export Copilot apply_payload to VN-standard QA Excel without Apply."""

    def write(
        self,
        payload: dict[str, Any],
        output: Path,
        *,
        summary: str = "",
    ) -> Path:
        kind = str(payload.get("kind") or "")
        if kind == APPLY_KIND_COMPOSE:
            scenarios = self._parse_scenarios(payload.get("scenarios") or [])
            return self.write_scenarios(scenarios, output, summary=summary)
        if kind == APPLY_KIND_TEST_DATA:
            rows = payload.get("rows") or []
            if not isinstance(rows, list):
                rows = []
            return self.write_test_data(list(rows), output, summary=summary)
        if kind == APPLY_KIND_STEPS:
            steps = payload.get("steps") or []
            if not isinstance(steps, list):
                steps = []
            return self.write_steps_patch(list(steps), output, summary=summary)
        raise ValueError(f"Unsupported artifact kind: {kind or '(empty)'}")

    def write_scenarios(
        self,
        scenarios: list[TestScenario],
        output: Path,
        *,
        summary: str = "",
    ) -> Path:
        cases_flat = self._flatten_cases(scenarios)
        wb = Workbook()
        overview = wb.active
        overview.title = VN_TC_SHEET_OVERVIEW
        self._write_overview(
            overview,
            scope="Copilot — compose scenarios",
            description=summary or "Bộ kịch bản kiểm thử sinh từ Copilot (chưa Apply).",
            scenario_count=len(scenarios),
            case_count=len(cases_flat),
            step_count=sum(len(c.steps) for _, c in cases_flat),
        )
        scenarios_sheet = wb.create_sheet(VN_TC_SHEET_SCENARIOS)
        self._write_scenarios_sheet(scenarios_sheet, scenarios)
        cases_sheet = wb.create_sheet(VN_TC_SHEET_CASES)
        self._write_cases_sheet(cases_sheet, cases_flat)
        steps_sheet = wb.create_sheet(VN_TC_SHEET_STEPS)
        self._write_steps_sheet(steps_sheet, cases_flat)
        import_sheet = wb.create_sheet(VN_TC_SHEET_IMPORT)
        self._write_import_sheet(import_sheet, cases_flat)
        data_rows = self._collect_test_data_rows(cases_flat)
        if data_rows:
            data_sheet = wb.create_sheet(VN_TC_SHEET_TEST_DATA)
            self._write_test_data_sheet(data_sheet, data_rows)
        return self._save(wb, output)

    def write_test_data(
        self,
        rows: list[dict[str, Any]],
        output: Path,
        *,
        summary: str = "",
    ) -> Path:
        wb = Workbook()
        overview = wb.active
        overview.title = VN_TC_SHEET_OVERVIEW
        self._write_overview(
            overview,
            scope="Copilot — test data",
            description=summary or "Bộ dữ liệu kiểm thử (BVA / phân vùng) sinh từ Copilot.",
            scenario_count=0,
            case_count=0,
            step_count=0,
            extra_stats=[("Tổng số bộ dữ liệu", len(rows))],
        )
        data_sheet = wb.create_sheet(VN_TC_SHEET_TEST_DATA)
        self._write_test_data_sheet(data_sheet, rows)
        return self._save(wb, output)

    def write_steps_patch(
        self,
        steps: list[dict[str, Any]],
        output: Path,
        *,
        summary: str = "",
    ) -> Path:
        parsed_steps: list[TestStep] = []
        for idx, item in enumerate(steps, start=1):
            if not isinstance(item, dict):
                continue
            raw_action = str(item.get("action") or "click")
            try:
                step = TestStep(
                    step_no=int(item.get("step_no") or idx),
                    action=raw_action,  # type: ignore[arg-type]
                    element_ref=str(item.get("element_ref") or "") or None,
                    intent_hint=str(item.get("intent_hint") or item.get("description") or ""),
                    input_data=str(item.get("input_data") or "") or None,
                    expected_result=str(item.get("expected_result") or ""),
                )
            except (ValidationError, TypeError, ValueError):
                step = TestStep(
                    step_no=idx,
                    action="click",
                    intent_hint=raw_action,
                    expected_result=str(item.get("expected_result") or ""),
                )
            parsed_steps.append(step)
        fake_case = TestCase(
            id="steps_patch",
            code="TC_STEPS",
            title="Steps patch (Copilot)",
            steps=parsed_steps,
        )
        scenario = TestScenario(
            id="steps_patch",
            code="TS_STEPS",
            title="Steps patch",
            cases=[fake_case],
        )
        return self.write_scenarios([scenario], output, summary=summary or "Steps patch từ Copilot.")

    def _parse_scenarios(self, raw: list[Any]) -> list[TestScenario]:
        out: list[TestScenario] = []
        for item in raw:
            if isinstance(item, TestScenario):
                out.append(item)
            elif isinstance(item, dict):
                out.append(TestScenario.model_validate(item))
        return out

    def _flatten_cases(
        self, scenarios: list[TestScenario]
    ) -> list[tuple[TestScenario, TestCase]]:
        rows: list[tuple[TestScenario, TestCase]] = []
        for scenario in scenarios:
            for case in scenario.cases:
                rows.append((scenario, case))
        return rows

    def _collect_test_data_rows(
        self, cases_flat: list[tuple[TestScenario, TestCase]]
    ) -> list[dict[str, Any]]:
        rows: list[dict[str, Any]] = []
        for scenario, case in cases_flat:
            for idx, row in enumerate(case.test_data_rows, start=1):
                if not isinstance(row, dict):
                    continue
                entry = {
                    VN_TC_COL_SCENARIO_ID: scenario.code,
                    VN_TC_COL_CASE_ID: case.code,
                    "Row #": idx,
                }
                entry.update({str(k): v for k, v in row.items()})
                rows.append(entry)
        return rows

    def _styles(self) -> tuple[Font, PatternFill, Font, PatternFill, Border, Alignment]:
        header_font = Font(name="Calibri", bold=True, color=VN_TC_HEADER_FONT_COLOR, size=11)
        header_fill = PatternFill("solid", fgColor=VN_TC_HEADER_FILL)
        body_font = Font(name="Calibri", size=11)
        zebra_fill = PatternFill("solid", fgColor=VN_TC_ZEBRA_FILL)
        thin = Side(style="thin", color="CBD5E1")
        border = Border(left=thin, right=thin, top=thin, bottom=thin)
        wrap = Alignment(wrap_text=True, vertical="center")
        return header_font, header_fill, body_font, zebra_fill, border, wrap

    def _write_header_row(self, sheet: Worksheet, headers: list[str], row: int = 1) -> None:
        header_font, header_fill, _, _, border, wrap = self._styles()
        for col, title in enumerate(headers, start=1):
            cell = sheet.cell(row=row, column=col, value=title)
            cell.font = header_font
            cell.fill = header_fill
            cell.border = border
            cell.alignment = Alignment(wrap_text=True, vertical="center", horizontal="center")
        sheet.freeze_panes = "A2"
        sheet.row_dimensions[row].height = 26

    def _apply_widths(self, sheet: Worksheet, widths: tuple[int, ...]) -> None:
        for idx, width in enumerate(widths, start=1):
            sheet.column_dimensions[get_column_letter(idx)].width = width

    def _paint_row(self, sheet: Worksheet, row: int, col_count: int, zebra: bool) -> None:
        _, _, body_font, zebra_fill, border, wrap = self._styles()
        for col in range(1, col_count + 1):
            cell = sheet.cell(row=row, column=col)
            cell.font = body_font
            cell.border = border
            cell.alignment = wrap
            if zebra:
                cell.fill = zebra_fill

    def _write_overview(
        self,
        sheet: Worksheet,
        *,
        scope: str,
        description: str,
        scenario_count: int,
        case_count: int,
        step_count: int,
        extra_stats: list[tuple[str, Any]] | None = None,
    ) -> None:
        header_font, header_fill, body_font, _, border, wrap = self._styles()
        sheet.merge_cells("A1:B1")
        title = sheet["A1"]
        title.value = VN_TC_OVERVIEW_TITLE
        title.font = Font(name="Calibri", bold=True, color=VN_TC_HEADER_FONT_COLOR, size=14)
        title.fill = header_fill
        title.alignment = Alignment(vertical="center")
        sheet.row_dimensions[1].height = 28

        rows: list[tuple[str, Any]] = [
            ("Phạm vi kiểm thử", scope),
            ("Mô tả", description),
            ("Thời điểm xuất", datetime.now().strftime("%Y-%m-%d %H:%M:%S")),
            ("Tiêu chuẩn tài liệu", VN_TC_OVERVIEW_STANDARD),
            ("Công cụ sinh", VN_TC_OVERVIEW_TOOL),
            ("Tổng số kịch bản (Scenarios)", scenario_count),
            ("Tổng số Test Cases", case_count),
            ("Tổng số bước thao tác (Steps)", step_count),
        ]
        if extra_stats:
            rows.extend(extra_stats)
        for idx, (label, value) in enumerate(rows, start=3):
            label_cell = sheet.cell(row=idx, column=1, value=label)
            cell_value: Any = value if isinstance(value, (int, float)) else safe_cell_text(value)
            value_cell = sheet.cell(row=idx, column=2, value=cell_value)
            label_cell.font = Font(name="Calibri", bold=True, size=11)
            value_cell.font = body_font
            label_cell.border = border
            value_cell.border = border
            value_cell.alignment = wrap
        sheet.column_dimensions["A"].width = 36
        sheet.column_dimensions["B"].width = 72

    def _write_scenarios_sheet(self, sheet: Worksheet, scenarios: list[TestScenario]) -> None:
        headers = [
            VN_TC_COL_NUM,
            VN_TC_COL_SCENARIO_ID,
            VN_TC_COL_SCENARIO_TITLE,
            VN_TC_COL_REQUIREMENT,
            VN_TC_COL_DESCRIPTION,
        ]
        self._write_header_row(sheet, headers)
        for idx, scenario in enumerate(scenarios, start=1):
            row = idx + 1
            values = [
                idx,
                scenario.code,
                scenario.title,
                scenario.requirement_ref,
                scenario.description,
            ]
            for col, value in enumerate(values, start=1):
                sheet.cell(row=row, column=col, value=safe_cell_text(value) if col > 1 else value)
            self._paint_row(sheet, row, len(headers), zebra=idx % 2 == 0)
        self._apply_widths(sheet, _SCENARIO_WIDTHS)
        if scenarios:
            sheet.auto_filter.ref = f"A1:{get_column_letter(len(headers))}{len(scenarios) + 1}"

    def _write_cases_sheet(
        self, sheet: Worksheet, cases_flat: list[tuple[TestScenario, TestCase]]
    ) -> None:
        headers = [
            VN_TC_COL_NUM,
            VN_TC_COL_CASE_ID,
            VN_TC_COL_CASE_NAME,
            VN_TC_COL_GROUP,
            VN_TC_COL_SUITE,
            VN_TC_COL_PRIORITY,
            VN_TC_COL_STATUS,
            VN_TC_COL_TYPE,
            VN_TC_COL_PRECONDITIONS,
            VN_TC_COL_STEPS_SUMMARY,
            VN_TC_COL_TEST_DATA,
            VN_TC_COL_EXPECTED,
            VN_TC_COL_NOTES,
        ]
        self._write_header_row(sheet, headers)
        for idx, (scenario, case) in enumerate(cases_flat, start=1):
            row = idx + 1
            preconditions = "; ".join(case.preconditions) or VN_TC_DEFAULT_PRECONDITION
            if case.start_url:
                preconditions = f"URL khởi đầu: {case.start_url}; {preconditions}"
            steps_summary = self._steps_summary(case)
            test_data = self._test_data_summary(case)
            expected = case.expected_final_result or self._expected_summary(case)
            values: list[Any] = [
                idx,
                case.code,
                case.title,
                VN_TC_DEFAULT_GROUP,
                f"{scenario.code} — {scenario.title}" if scenario.title else scenario.code,
                case.priority,
                VN_TC_STATUS_DRAFT,
                case.test_type,
                preconditions,
                steps_summary,
                test_data,
                expected,
                case.requirement_ref or scenario.requirement_ref or case.description,
            ]
            for col, value in enumerate(values, start=1):
                sheet.cell(
                    row=row,
                    column=col,
                    value=value if col == 1 else safe_cell_text(value),
                )
            self._paint_row(sheet, row, len(headers), zebra=idx % 2 == 0)
        self._apply_widths(sheet, _CASES_WIDTHS)
        if cases_flat:
            sheet.auto_filter.ref = f"A1:{get_column_letter(len(headers))}{len(cases_flat) + 1}"

    def _write_steps_sheet(
        self, sheet: Worksheet, cases_flat: list[tuple[TestScenario, TestCase]]
    ) -> None:
        headers = [
            VN_TC_COL_NUM,
            VN_TC_COL_CASE_ID,
            VN_TC_COL_CASE_NAME,
            VN_TC_COL_STEP_NO,
            VN_TC_COL_ACTION,
            VN_TC_COL_TARGET,
            VN_TC_COL_TEST_DATA,
            VN_TC_COL_STEP_DESC,
            VN_TC_COL_EXPECTED,
        ]
        self._write_header_row(sheet, headers)
        row_idx = 1
        global_idx = 1
        for _, case in cases_flat:
            steps = case.steps
            if not steps:
                row_idx += 1
                values: list[Any] = [
                    global_idx,
                    case.code,
                    case.title,
                    1,
                    "EXECUTE",
                    "",
                    self._test_data_summary(case),
                    case.description or case.title,
                    case.expected_final_result,
                ]
                for col, value in enumerate(values, start=1):
                    sheet.cell(
                        row=row_idx,
                        column=col,
                        value=value if col in {1, 4} else safe_cell_text(value),
                    )
                self._paint_row(sheet, row_idx, len(headers), zebra=global_idx % 2 == 0)
                global_idx += 1
                continue
            for step in steps:
                row_idx += 1
                values = [
                    global_idx,
                    case.code,
                    case.title,
                    step.step_no,
                    step.action,
                    step.element_ref or "",
                    step.input_data or "",
                    step.intent_hint or "",
                    step.expected_result,
                ]
                for col, value in enumerate(values, start=1):
                    sheet.cell(
                        row=row_idx,
                        column=col,
                        value=value if col in {1, 4} else safe_cell_text(value),
                    )
                self._paint_row(sheet, row_idx, len(headers), zebra=global_idx % 2 == 0)
                global_idx += 1
        self._apply_widths(sheet, _STEPS_WIDTHS)
        if row_idx > 1:
            sheet.auto_filter.ref = f"A1:{get_column_letter(len(headers))}{row_idx}"

    def _write_import_sheet(
        self, sheet: Worksheet, cases_flat: list[tuple[TestScenario, TestCase]]
    ) -> None:
        """English wizard-compatible headers for round-trip import."""
        headers = [
            WIZARD_DOC_HEADER_CASE_ID,
            WIZARD_DOC_HEADER_SCENARIO_ID,
            WIZARD_DOC_HEADER_STEP_NO,
            WIZARD_DOC_HEADER_ACTION,
            WIZARD_DOC_HEADER_TITLE,
            WIZARD_DOC_HEADER_TEST_DATA,
            WIZARD_DOC_HEADER_EXPECTED,
            WIZARD_DOC_HEADER_PRECONDITIONS,
            WIZARD_DOC_HEADER_REQUIREMENT,
            WIZARD_DOC_HEADER_DESCRIPTION,
        ]
        # Display with Title Case for human readability (wizard normalizes).
        display = [h.title() for h in headers]
        self._write_header_row(sheet, display)
        row_idx = 1
        for scenario, case in cases_flat:
            preconditions = "; ".join(case.preconditions)
            requirement = case.requirement_ref or scenario.requirement_ref
            if not case.steps:
                row_idx += 1
                values = [
                    case.code,
                    scenario.code,
                    "",
                    "",
                    case.title,
                    self._test_data_summary(case),
                    case.expected_final_result,
                    preconditions,
                    requirement,
                    case.description or scenario.description,
                ]
                for col, value in enumerate(values, start=1):
                    sheet.cell(row=row_idx, column=col, value=safe_cell_text(value))
                self._paint_row(sheet, row_idx, len(headers), zebra=row_idx % 2 == 0)
                continue
            for step in case.steps:
                row_idx += 1
                step_values: list[Any] = [
                    case.code,
                    scenario.code,
                    step.step_no,
                    step.action,
                    case.title,
                    step.input_data or "",
                    step.expected_result or case.expected_final_result,
                    preconditions,
                    requirement,
                    step.intent_hint or case.description,
                ]
                for col, value in enumerate(step_values, start=1):
                    sheet.cell(
                        row=row_idx,
                        column=col,
                        value=value if col == 3 else safe_cell_text(value),
                    )
                self._paint_row(sheet, row_idx, len(headers), zebra=row_idx % 2 == 0)
        self._apply_widths(sheet, _IMPORT_WIDTHS)
        if row_idx > 1:
            sheet.auto_filter.ref = f"A1:{get_column_letter(len(headers))}{row_idx}"

    def _write_test_data_sheet(self, sheet: Worksheet, rows: list[dict[str, Any]]) -> None:
        if not rows:
            self._write_header_row(sheet, [VN_TC_COL_NUM, VN_TC_COL_TEST_DATA])
            return
        keys: list[str] = []
        seen: set[str] = set()
        for row in rows:
            for key in row:
                key_s = str(key)
                if key_s not in seen:
                    seen.add(key_s)
                    keys.append(key_s)
        headers = [VN_TC_COL_NUM, *keys]
        self._write_header_row(sheet, headers)
        for idx, row in enumerate(rows, start=1):
            excel_row = idx + 1
            sheet.cell(row=excel_row, column=1, value=idx)
            for col, key in enumerate(keys, start=2):
                sheet.cell(row=excel_row, column=col, value=safe_cell_text(row.get(key)))
            self._paint_row(sheet, excel_row, len(headers), zebra=idx % 2 == 0)
        widths = (7, *[max(14, min(36, len(k) + 4)) for k in keys])
        self._apply_widths(sheet, tuple(widths))
        sheet.auto_filter.ref = f"A1:{get_column_letter(len(headers))}{len(rows) + 1}"

    def _steps_summary(self, case: TestCase) -> str:
        if not case.steps:
            return case.description or "1. Thực thi kịch bản kiểm thử"
        lines: list[str] = []
        for step in case.steps:
            label = step.intent_hint or f"{step.action} {step.element_ref or ''}".strip()
            lines.append(f"• Bước {step.step_no}: {label}")
        return "\n".join(lines)

    def _test_data_summary(self, case: TestCase) -> str:
        parts: list[str] = []
        for step in case.steps:
            if step.input_data:
                parts.append(str(step.input_data))
        for row in case.test_data_rows:
            if isinstance(row, dict):
                parts.append(", ".join(f"{k}={v}" for k, v in row.items()))
        return "; ".join(parts)

    def _expected_summary(self, case: TestCase) -> str:
        for step in reversed(case.steps):
            if step.expected_result:
                return step.expected_result
        return ""

    def _save(self, wb: Workbook, output: Path) -> Path:
        output.parent.mkdir(parents=True, exist_ok=True)
        wb.save(output)
        return output
