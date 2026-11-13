"""Headless reporting CLI — must not import PySide6."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from ai_qa_test_studio.core.models import RunReport
from ai_qa_test_studio.core.storage import ProjectStorage
from ai_qa_test_studio.reporting.code_exporter import CodeExporter
from ai_qa_test_studio.reporting.excel_exporter import ExcelExporter
from ai_qa_test_studio.reporting.html_reporter import HtmlReporter


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="ai-qa-report", description="Headless QA report exporter")
    parser.add_argument("--project", type=Path, required=True, help="Directory with project.v4.json")
    parser.add_argument("--report", type=Path, help="RunReport JSON file")
    parser.add_argument("--html", type=Path, help="Output HTML path")
    parser.add_argument("--excel", type=Path, help="Output Excel path")
    parser.add_argument("--pytest-out", type=Path, help="Export pytest project directory")
    args = parser.parse_args(argv)

    storage = ProjectStorage(args.project)
    project = storage.load()
    if project is None:
        print("project.v4.json not found", file=sys.stderr)
        return 2

    report: RunReport | None = None
    if args.report and args.report.is_file():
        report = RunReport.model_validate(json.loads(args.report.read_text(encoding="utf-8")))

    if args.html:
        if report is None:
            print("--report required for HTML", file=sys.stderr)
            return 2
        HtmlReporter().write(report, args.html)
        print(args.html)

    if args.excel:
        ExcelExporter().write(project, report, args.excel)
        print(args.excel)

    if args.pytest_out:
        CodeExporter().export(project, args.pytest_out)
        print(args.pytest_out)

    if not any([args.html, args.excel, args.pytest_out]):
        print(f"Loaded project: {project.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
