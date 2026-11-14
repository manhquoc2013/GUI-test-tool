"""Async report export facade — heavy IO via asyncio.to_thread."""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from pathlib import Path

from ai_qa_test_studio.core.models import Project, RunReport
from ai_qa_test_studio.reporting.evidence import EvidenceCollector
from ai_qa_test_studio.reporting.excel_exporter import ExcelExporter
from ai_qa_test_studio.reporting.html_reporter import HtmlReporter


@dataclass(frozen=True)
class ExportBundle:
    evidence_dir: Path
    html_path: Path | None
    excel_path: Path | None


class ReportService:
    def __init__(
        self,
        project_root: Path,
        *,
        html: HtmlReporter | None = None,
        excel: ExcelExporter | None = None,
        evidence: EvidenceCollector | None = None,
    ) -> None:
        self.project_root = Path(project_root)
        self.html = html or HtmlReporter()
        self.excel = excel or ExcelExporter()
        self.evidence = evidence or EvidenceCollector(self.project_root)

    def export_all_sync(
        self,
        project: Project,
        report: RunReport,
        *,
        write_html: bool = True,
        write_excel: bool = True,
    ) -> ExportBundle:
        evidence_dir = self.evidence.collect(report)
        html_path: Path | None = None
        excel_path: Path | None = None
        if write_html:
            html_path = self.html.write(report, evidence_dir / "report.html")
        if write_excel:
            excel_path = self.excel.write(project, report, evidence_dir / "report.xlsx")
        return ExportBundle(
            evidence_dir=evidence_dir, html_path=html_path, excel_path=excel_path
        )

    async def export_all(
        self,
        project: Project,
        report: RunReport,
        *,
        write_html: bool = True,
        write_excel: bool = True,
    ) -> ExportBundle:
        return await asyncio.to_thread(
            self.export_all_sync,
            project,
            report,
            write_html=write_html,
            write_excel=write_excel,
        )
