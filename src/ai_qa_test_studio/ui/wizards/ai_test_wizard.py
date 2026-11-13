from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import Qt
from PySide6.QtWidgets import (
    QFileDialog,
    QFormLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QVBoxLayout,
    QWizard,
    QWizardPage,
)

from ai_qa_test_studio.agent.spec_generator import SpecGenerator
from ai_qa_test_studio.core.models import TestScenario


class AiTestWizard(QWizard):
    def __init__(self, parent=None) -> None:  # type: ignore[no-untyped-def]
        super().__init__(parent)
        self.setWindowTitle("AI Test Wizard")
        self.generator = SpecGenerator()
        self.scenarios: list[TestScenario] = []
        self.source_page = _SourcePage()
        self.scenario_page = _ScenarioPage()
        self.review_page = _ReviewPage()
        self.addPage(self.source_page)
        self.addPage(self.scenario_page)
        self.addPage(self.review_page)
        self.currentIdChanged.connect(self._on_page)

    def _on_page(self, page_id: int) -> None:
        if self.page(page_id) is self.scenario_page:
            markdown = self.source_page.markdown_text()
            self.scenarios = self.generator.generate_outline_sync(markdown)
            self.scenario_page.load_scenarios(self.scenarios)
        if self.page(page_id) is self.review_page:
            selected = self.scenario_page.selected_scenarios()
            for scenario in selected:
                if not scenario.cases:
                    scenario.cases = self.generator.generate_cases_sync(scenario, batch_size=2)
                    start = self.source_page.url.text().strip()
                    if start:
                        for case in scenario.cases:
                            case.start_url = start
                            if case.steps and case.steps[0].action == "goto":
                                case.steps[0].input_data = start
            self.review_page.set_summary(selected)

    def result_scenarios(self) -> list[TestScenario]:
        return self.scenario_page.selected_scenarios()

    def source_url(self) -> str:
        return str(self.source_page.url.text()).strip()


class _SourcePage(QWizardPage):
    def __init__(self) -> None:
        super().__init__()
        self.setTitle("Nhập liệu")
        self.url = QLineEdit()
        self.url.setPlaceholderText("https://… (optional — dùng cho Sync/Run)")
        self.figma = QLineEdit()
        self.doc_path = QLineEdit()
        self.markdown = QLineEdit("Sample SRS: login and checkout happy path")
        form = QFormLayout(self)
        form.addRow("URL (optional)", self.url)
        form.addRow("Figma link", self.figma)
        browse = QLabel('<a href="#">Chọn file SRS…</a>')
        browse.linkActivated.connect(self._browse)
        form.addRow("Tài liệu", self.doc_path)
        form.addRow("", browse)
        form.addRow("Markdown / mô tả", self.markdown)

    def _browse(self) -> None:
        path, _ = QFileDialog.getOpenFileName(self, "SRS", "", "Docs (*.md *.txt *.docx *.pdf)")
        if path:
            self.doc_path.setText(path)
            try:
                self.markdown.setText(Path(path).read_text(encoding="utf-8")[:4000])
            except OSError:
                pass

    def markdown_text(self) -> str:
        return self.markdown.text().strip() or "Untitled feature"


class _ScenarioPage(QWizardPage):
    def __init__(self) -> None:
        super().__init__()
        self.setTitle("Chọn Scenario")
        self.list = QListWidget()
        layout = QVBoxLayout(self)
        layout.addWidget(self.list)

    def load_scenarios(self, scenarios: list[TestScenario]) -> None:
        self.list.clear()
        for sc in scenarios:
            item = QListWidgetItem(f"{sc.code}: {sc.title}")
            item.setFlags(item.flags() | Qt.ItemFlag.ItemIsUserCheckable)
            item.setCheckState(Qt.CheckState.Checked)
            item.setData(Qt.ItemDataRole.UserRole, sc)
            self.list.addItem(item)

    def selected_scenarios(self) -> list[TestScenario]:
        selected: list[TestScenario] = []
        for i in range(self.list.count()):
            item = self.list.item(i)
            if item and item.checkState() == Qt.CheckState.Checked:
                selected.append(item.data(Qt.ItemDataRole.UserRole))
        return selected


class _ReviewPage(QWizardPage):
    def __init__(self) -> None:
        super().__init__()
        self.setTitle("Duyệt & tạo Draft UNBOUND")
        self.label = QLabel("Chưa có scenario")
        self.label.setObjectName("title")
        self.label.setWordWrap(True)
        layout = QVBoxLayout(self)
        layout.addWidget(self.label)

    def set_summary(self, scenarios: list[TestScenario]) -> None:
        if not scenarios:
            self.label.setText("Không có scenario nào được chọn.")
            return
        case_count = sum(len(s.cases) for s in scenarios)
        lines = [
            f"{len(scenarios)} scenario(s), {case_count} case(s) draft UNBOUND:",
        ]
        for s in scenarios:
            lines.append(f"- {s.code}: {s.title} ({len(s.cases)} cases)")
        self.label.setText("\n".join(lines))
