"""IEEE 829 + RTM Excel export with SecretMasker and healing audit sheets."""

from __future__ import annotations

from collections import Counter
from pathlib import Path
from typing import Any

from openpyxl import Workbook

from ai_qa_test_studio.core.compose_mapping import format_preconditions_cell
from ai_qa_test_studio.core.models import Project, RunReport, TestCase, TestStep
from ai_qa_test_studio.core.secret_masker import SecretMasker, get_secret_masker
from ai_qa_test_studio.core.step_actions import step_target_label
from ai_qa_test_studio.core.text_normalize import normalize_text_list


class ExcelExporter:
    def __init__(self, masker: SecretMasker | None = None) -> None:
        self._masker = masker or get_secret_masker()

    def _m(self, value: Any) -> str:
        if value is None:
            return ""
        return self._masker.mask_text(str(value))

    def write(self, project: Project, report: RunReport | None, output: Path) -> Path:
        wb = Workbook()
        self._write_rates_sheet(wb, report)
        self._write_overview_rtm(wb, project, report)
        self._write_scenarios(wb, project)
        self._write_cases_ieee(wb, project, report)
        self._write_healing_audit(wb, report)
        self._write_defects(wb, report)

        output.parent.mkdir(parents=True, exist_ok=True)
        wb.save(output)
        return output

    def _write_rates_sheet(self, wb: Workbook, report: RunReport | None) -> None:
        sheet = wb.active
        sheet.title = "Tổng quan tỷ lệ"
        sheet.append(["Trạng thái", "Số lượng", "Tỷ lệ %"])
        if report is None or not report.cases:
            for status in ("PASS", "HEALED", "FAIL", "BLOCKED"):
                sheet.append([status, 0, 0.0])
            return
        counts: Counter[str] = Counter(str(case.status) for case in report.cases)
        total = max(len(report.cases), 1)
        for status in ("PASS", "HEALED", "FAIL", "BLOCKED", "SKIPPED", "CANCELLED"):
            n = int(counts.get(status, 0))
            sheet.append([status, n, round(100.0 * n / total, 2)])
        sheet.append(["TOTAL", len(report.cases), 100.0])

    def _write_overview_rtm(
        self, wb: Workbook, project: Project, report: RunReport | None
    ) -> None:
        overview = wb.create_sheet("Overview & RTM")
        overview.append(["Requirement ID", "Scenario", "Test Case", "Execution Status"])
        for group in [project.root_group, *project.root_group.children]:
            for scenario in group.scenarios:
                for case in scenario.cases:
                    status = "NOT_RUN"
                    if report:
                        for cr in report.cases:
                            if cr.case_id == case.id:
                                status = cr.status
                    overview.append(
                        [
                            self._m(case.requirement_ref or scenario.requirement_ref),
                            self._m(scenario.code),
                            self._m(case.code),
                            status,
                        ]
                    )

    def _write_scenarios(self, wb: Workbook, project: Project) -> None:
        scenarios_sheet = wb.create_sheet("Test Scenarios")
        scenarios_sheet.append(["Scenario ID", "Title", "Requirement", "Description"])
        for group in [project.root_group, *project.root_group.children]:
            for scenario in group.scenarios:
                scenarios_sheet.append(
                    [
                        self._m(scenario.code),
                        self._m(scenario.title),
                        self._m(scenario.requirement_ref),
                        self._m(scenario.description),
                    ]
                )

    def _write_cases_ieee(
        self, wb: Workbook, project: Project, report: RunReport | None
    ) -> None:
        cases_sheet = wb.create_sheet("Test Cases (IEEE 829)")
        cases_sheet.append(
            [
                "Test Case ID",
                "Scenario ID",
                "Title",
                "Priority",
                "Test Type",
                "Điều kiện tiên quyết",
                "Step No",
                "Action",
                "Target",
                "Test Data",
                "Expected Result",
                "Actual Result",
                "Binding Status",
                "Execution Status",
            ]
        )
        for group in [project.root_group, *project.root_group.children]:
            for scenario in group.scenarios:
                for case in scenario.cases:
                    self._append_case_rows(cases_sheet, scenario.code, case, report)

    def _append_case_rows(
        self,
        sheet: Any,
        scenario_code: str,
        case: TestCase,
        report: RunReport | None,
    ) -> None:
        exec_status = case.last_status
        actuals: dict[int, str] = {}
        if report:
            for cr in report.cases:
                if cr.case_id == case.id:
                    exec_status = cr.status
                    for sr in cr.steps:
                        actuals[sr.step_no] = self._m(sr.message)
        preconditions = normalize_text_list(
            format_preconditions_cell(case.preconditions)
        )
        preconditions = self._m(preconditions)
        if not case.steps:
            sheet.append(
                [
                    self._m(case.code),
                    self._m(scenario_code),
                    self._m(case.title),
                    case.priority,
                    case.test_type,
                    preconditions,
                    "",
                    "",
                    "",
                    "",
                    self._m(case.expected_final_result),
                    "",
                    "",
                    exec_status,
                ]
            )
            return
        for step in case.steps:
            sheet.append(self._case_step_row(case, scenario_code, step, preconditions, actuals, exec_status))

    def _case_step_row(
        self,
        case: TestCase,
        scenario_code: str,
        step: TestStep,
        preconditions: str,
        actuals: dict[int, str],
        exec_status: str,
    ) -> list[Any]:
        test_data = self._masker.mask_mapping(
            {"test_data": step.input_data or ""}
        ).get("test_data", "")
        return [
            self._m(case.code),
            self._m(scenario_code),
            self._m(case.title),
            case.priority,
            case.test_type,
            preconditions,
            step.step_no,
            step.action,
            self._m(step_target_label(step)),
            self._m(test_data),
            self._m(step.expected_result),
            actuals.get(step.step_no, ""),
            step.binding_status,
            exec_status,
        ]

    def _write_healing_audit(self, wb: Workbook, report: RunReport | None) -> None:
        sheet = wb.create_sheet("Chi tiết Healing Audit")
        sheet.append(
            [
                "Case",
                "Step",
                "Action",
                "Status",
                "confidence",
                "previous_selector",
                "healed_selector",
                "persisted",
                "reason",
                "error_message",
                "console_logs",
                "screenshot_path",
            ]
        )
        if report is None:
            return
        audits = {a.step_no: a for a in report.healing_audit}
        for case_result in report.cases:
            for step in case_result.steps:
                audit = step.audit or audits.get(step.step_no)
                console = "\n".join(step.console_logs)
                sheet.append(
                    [
                        self._m(case_result.case_code),
                        step.step_no,
                        step.action,
                        step.step_status.value if step.step_status else step.status,
                        audit.confidence_score if audit else step.confidence_score,
                        self._m(
                            (audit.previous_selector if audit else None)
                            or step.previous_selector
                            or (audit.old_locator if audit else None)
                            or ""
                        ),
                        self._m(
                            (audit.healed_selector if audit else None)
                            or (audit.new_locator if audit else None)
                            or ""
                        ),
                        bool(audit.persisted) if audit else False,
                        self._m(audit.reason if audit else ""),
                        self._m(step.message),
                        self._m(console),
                        step.screenshot_path or "",
                    ]
                )

    def _write_defects(self, wb: Workbook, report: RunReport | None) -> None:
        defects = wb.create_sheet("Defects & AI Insights")
        defects.append(["Case", "Step", "Message", "Healing", "Screenshot Path"])
        if report is None:
            return
        for case_result in report.cases:
            for step_result in case_result.steps:
                if step_result.status in {"FAIL", "HEALED"}:
                    defects.append(
                        [
                            self._m(case_result.case_code),
                            step_result.step_no,
                            self._m(step_result.message),
                            step_result.status,
                            step_result.screenshot_path or "",
                        ]
                    )
        for audit in report.healing_audit:
            defects.append(
                [
                    "",
                    audit.step_no,
                    self._m(audit.reason),
                    self._m(f"{audit.old_locator} -> {audit.new_locator}"),
                ]
            )
