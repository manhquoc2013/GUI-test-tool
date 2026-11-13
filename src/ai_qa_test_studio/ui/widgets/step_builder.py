from __future__ import annotations

import yaml
from PySide6.QtWidgets import (
    QPlainTextEdit,
    QTableWidget,
    QTableWidgetItem,
    QTabWidget,
    QVBoxLayout,
    QWidget,
)

from ai_qa_test_studio.core.models import TestCase


class StepBuilder(QWidget):
    """Four-mode editor: Spec / No-Code / YAML / Code (bidirectional via shared TestCase)."""

    def __init__(self, parent=None) -> None:  # type: ignore[no-untyped-def]
        super().__init__(parent)
        self._case: TestCase | None = None
        self.tabs = QTabWidget()
        self.spec = QPlainTextEdit()
        self.table = QTableWidget(0, 5)
        self.table.setHorizontalHeaderLabels(["#", "Action", "Intent", "Data", "Binding"])
        self.yaml_edit = QPlainTextEdit()
        self.code_edit = QPlainTextEdit()
        self.tabs.addTab(self.spec, "Spec")
        self.tabs.addTab(self.table, "No-Code")
        self.tabs.addTab(self.yaml_edit, "YAML")
        self.tabs.addTab(self.code_edit, "Code")
        layout = QVBoxLayout(self)
        layout.addWidget(self.tabs)
        self.tabs.currentChanged.connect(self._on_tab_changed)

    def load_case(self, case: TestCase) -> None:
        self._case = case
        self._render_all()

    def current_case(self) -> TestCase | None:
        self._capture_active()
        return self._case

    def _render_all(self) -> None:
        if self._case is None:
            return
        self.spec.setPlainText(
            f"Code: {self._case.code}\nTitle: {self._case.title}\n"
            f"Priority: {self._case.priority}\nType: {self._case.test_type}\n"
            f"Preconditions:\n- " + "\n- ".join(self._case.preconditions or ["(none)"])
        )
        self.table.setRowCount(0)
        for step in self._case.steps:
            row = self.table.rowCount()
            self.table.insertRow(row)
            values = [
                str(step.step_no),
                step.action,
                step.intent_hint,
                step.input_data or "",
                step.binding_status,
            ]
            for col, value in enumerate(values):
                self.table.setItem(row, col, QTableWidgetItem(value))
        payload = {
            "code": self._case.code,
            "title": self._case.title,
            "steps": [s.model_dump() for s in self._case.steps],
        }
        self.yaml_edit.setPlainText(yaml.safe_dump(payload, allow_unicode=True, sort_keys=False))
        lines = [f"# {self._case.code}", "def test_case(page):"]
        for step in self._case.steps:
            lines.append(f"    # {step.step_no}: {step.action} {step.intent_hint}")
            if step.action == "goto":
                lines.append(f"    page.goto({(step.input_data or '/')!r})")
        self.code_edit.setPlainText("\n".join(lines))

    def _on_tab_changed(self, _index: int) -> None:
        self._capture_active()
        self._render_all()

    def _capture_active(self) -> None:
        if self._case is None:
            return
        # Sync from YAML if that tab was edited.
        if self.tabs.currentWidget() is self.yaml_edit:
            try:
                data = yaml.safe_load(self.yaml_edit.toPlainText()) or {}
                if isinstance(data, dict) and "steps" in data:
                    from ai_qa_test_studio.core.models import TestStep

                    self._case.title = str(data.get("title") or self._case.title)
                    steps = []
                    for raw in data.get("steps") or []:
                        if isinstance(raw, dict):
                            steps.append(TestStep.model_validate(raw))
                    self._case.steps = steps
            except Exception:
                return
        elif self.tabs.currentWidget() is self.spec:
            # Spec is documentation-oriented; title line sync only.
            for line in self.spec.toPlainText().splitlines():
                if line.startswith("Title:"):
                    self._case.title = line.split(":", 1)[1].strip()
