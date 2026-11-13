"""IEEE 829 + RTM Excel export (4 sheets)."""

from __future__ import annotations

from pathlib import Path

from openpyxl import Workbook

from ai_qa_test_studio.core.models import Project, RunReport


class ExcelExporter:
    def write(self, project: Project, report: RunReport | None, output: Path) -> Path:
        wb = Workbook()
        overview = wb.active
        overview.title = "Overview & RTM"
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
                        [case.requirement_ref or scenario.requirement_ref, scenario.code, case.code, status]
                    )

        scenarios_sheet = wb.create_sheet("Test Scenarios")
        scenarios_sheet.append(["Scenario ID", "Title", "Requirement", "Description"])
        for group in [project.root_group, *project.root_group.children]:
            for scenario in group.scenarios:
                scenarios_sheet.append(
                    [scenario.code, scenario.title, scenario.requirement_ref, scenario.description]
                )

        cases_sheet = wb.create_sheet("Test Cases (IEEE 829)")
        cases_sheet.append(
            [
                "Test Case ID",
                "Scenario ID",
                "Title",
                "Priority",
                "Test Type",
                "Pre-conditions",
                "Step No",
                "Action",
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
                    exec_status = case.last_status
                    actuals: dict[int, str] = {}
                    if report:
                        for cr in report.cases:
                            if cr.case_id == case.id:
                                exec_status = cr.status
                                for sr in cr.steps:
                                    actuals[sr.step_no] = sr.message
                    if not case.steps:
                        cases_sheet.append(
                            [
                                case.code,
                                scenario.code,
                                case.title,
                                case.priority,
                                case.test_type,
                                "; ".join(case.preconditions),
                                "",
                                "",
                                "",
                                case.expected_final_result,
                                "",
                                "",
                                exec_status,
                            ]
                        )
                    for step in case.steps:
                        cases_sheet.append(
                            [
                                case.code,
                                scenario.code,
                                case.title,
                                case.priority,
                                case.test_type,
                                "; ".join(case.preconditions),
                                step.step_no,
                                step.action,
                                step.input_data or "",
                                step.expected_result,
                                actuals.get(step.step_no, ""),
                                step.binding_status,
                                exec_status,
                            ]
                        )

        defects = wb.create_sheet("Defects & AI Insights")
        defects.append(["Case", "Step", "Message", "Healing"])
        if report:
            for case_result in report.cases:
                for step_result in case_result.steps:
                    if step_result.status in {"FAIL", "HEALED"}:
                        defects.append(
                            [
                                case_result.case_code,
                                step_result.step_no,
                                step_result.message,
                                step_result.status,
                            ]
                        )
            for audit in report.healing_audit:
                defects.append(
                    [
                        "",
                        audit.step_no,
                        audit.reason,
                        f"{audit.old_locator} -> {audit.new_locator}",
                    ]
                )

        output.parent.mkdir(parents=True, exist_ok=True)
        wb.save(output)
        return output
