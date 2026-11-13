"""Four-mode editor: Spec form / No-Code / YAML / Code."""

from __future__ import annotations

import yaml
from PySide6.QtCore import Signal
from PySide6.QtWidgets import (
    QComboBox,
    QFormLayout,
    QFrame,
    QLabel,
    QLineEdit,
    QPlainTextEdit,
    QTableWidget,
    QTableWidgetItem,
    QTabWidget,
    QVBoxLayout,
    QWidget,
)

from ai_qa_test_studio.constants import BINDING_UNBOUND
from ai_qa_test_studio.core.models import TestCase, TestStep
from ai_qa_test_studio.ui.formatting import count_unbound_steps

_PRIORITIES = ["P0 - Critical", "P1 - High", "P2 - Medium", "P3 - Low"]
_TYPES = ["Functional", "Negative", "Boundary", "UI/UX", "Security"]


class StepBuilder(QWidget):
    case_changed = Signal()
    step_row_selected = Signal(int)

    def __init__(self, parent=None) -> None:  # type: ignore[no-untyped-def]
        super().__init__(parent)
        self.setAccessibleName("Workspace Editor")
        self._case: TestCase | None = None
        self._loading = False
        self.tabs = QTabWidget()

        self.unbound_banner = QLabel("")
        self.unbound_banner.setObjectName("bannerWarn")
        self.unbound_banner.setWordWrap(True)
        self.unbound_banner.setVisible(False)
        self.unbound_banner.setAccessibleName("Cảnh báo bước UNBOUND")

        self.title_edit = QLineEdit()
        self.title_edit.setAccessibleName("Tiêu đề Test Case")
        self.code_label = QLabel("—")
        self.priority_combo = QComboBox()
        self.priority_combo.addItems(_PRIORITIES)
        self.type_combo = QComboBox()
        self.type_combo.addItems(_TYPES)
        self.preconditions_edit = QPlainTextEdit()
        self.preconditions_edit.setPlaceholderText("Mỗi điều kiện một dòng")
        self.preconditions_edit.setMaximumHeight(96)
        self.expected_edit = QPlainTextEdit()
        self.expected_edit.setPlaceholderText("Kết quả cuối mong đợi (Outer Loop đối chiếu)")
        self.expected_edit.setMaximumHeight(80)

        form_card = QFrame()
        form_card.setObjectName("card")
        form = QFormLayout(form_card)
        form.setContentsMargins(12, 12, 12, 12)
        form.setSpacing(8)
        form.addRow("Mã", self.code_label)
        form.addRow("Title", self.title_edit)
        form.addRow("Priority", self.priority_combo)
        form.addRow("Type", self.type_combo)
        form.addRow("Preconditions", self.preconditions_edit)
        form.addRow("Expected Final Result", self.expected_edit)

        spec_page = QWidget()
        spec_layout = QVBoxLayout(spec_page)
        spec_layout.setContentsMargins(8, 8, 8, 8)
        spec_layout.setSpacing(8)
        spec_layout.addWidget(self.unbound_banner)
        spec_layout.addWidget(form_card)
        spec_layout.addStretch(1)

        self.table = QTableWidget(0, 5)
        self.table.setHorizontalHeaderLabels(["#", "Action", "Intent", "Data", "Binding"])
        self.table.setAccessibleName("Bảng bước No-Code")
        self.table.itemSelectionChanged.connect(self._emit_step_selection)

        self.yaml_edit = QPlainTextEdit()
        self.code_edit = QPlainTextEdit()
        self.tabs.addTab(spec_page, "Spec")
        self.tabs.addTab(self.table, "No-Code")
        self.tabs.addTab(self.yaml_edit, "YAML")
        self.tabs.addTab(self.code_edit, "Code")

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.tabs)
        self.tabs.currentChanged.connect(self._on_tab_changed)

        for widget in (self.title_edit, self.priority_combo, self.type_combo):
            if hasattr(widget, "editingFinished"):
                widget.editingFinished.connect(self._capture_spec_fields)  # type: ignore[attr-defined]
            if hasattr(widget, "currentIndexChanged"):
                widget.currentIndexChanged.connect(lambda _i: self._capture_spec_fields())  # type: ignore[attr-defined]

    def load_case(self, case: TestCase) -> None:
        self._loading = True
        try:
            self._case = case
            self._render_all()
        finally:
            self._loading = False

    def peek_case(self) -> TestCase | None:
        return self._case

    def current_case(self) -> TestCase | None:
        self._capture_active()
        return self._case

    def selected_step_index(self) -> int | None:
        rows = self.table.selectionModel().selectedRows() if self.table.selectionModel() else []
        if not rows:
            return None
        return int(rows[0].row())

    def bind_element_to_selected_step(self, element_id: str) -> bool:
        case = self.current_case()
        if case is None or not case.steps:
            return False
        idx = self.selected_step_index()
        if idx is None:
            idx = next(
                (i for i, s in enumerate(case.steps) if s.binding_status == BINDING_UNBOUND),
                0,
            )
        if idx < 0 or idx >= len(case.steps):
            return False
        step = case.steps[idx]
        step.element_ref = element_id
        step.binding_status = "BOUND"
        self._render_all()
        self.case_changed.emit()
        return True

    def _render_all(self) -> None:
        if self._case is None:
            return
        unbound = count_unbound_steps(self._case)
        if unbound > 0:
            self.unbound_banner.setText(
                f"Kịch bản này có {unbound} bước chưa gắn selector thật. "
                "Nhập URL ở trên và nhấn Sync URL để tự động ánh xạ."
            )
            self.unbound_banner.setVisible(True)
        else:
            self.unbound_banner.setVisible(False)

        self.code_label.setText(self._case.code)
        self.title_edit.setText(self._case.title)
        self.priority_combo.setCurrentText(self._case.priority)
        self.type_combo.setCurrentText(self._case.test_type)
        self.preconditions_edit.setPlainText("\n".join(self._case.preconditions or []))
        self.expected_edit.setPlainText(self._case.expected_final_result or "")

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

    def _capture_spec_fields(self) -> None:
        if self._case is None or self._loading:
            return
        self._case.title = self.title_edit.text().strip() or self._case.title
        self._case.priority = self.priority_combo.currentText()  # type: ignore[assignment]
        self._case.test_type = self.type_combo.currentText()  # type: ignore[assignment]
        self._case.preconditions = [
            line.strip() for line in self.preconditions_edit.toPlainText().splitlines() if line.strip()
        ]
        self._case.expected_final_result = self.expected_edit.toPlainText().strip()
        self.case_changed.emit()

    def _capture_active(self) -> None:
        if self._case is None or self._loading:
            return
        self._capture_spec_fields()
        if self.tabs.currentWidget() is self.yaml_edit:
            try:
                data = yaml.safe_load(self.yaml_edit.toPlainText()) or {}
                if isinstance(data, dict) and "steps" in data:
                    self._case.title = str(data.get("title") or self._case.title)
                    steps = []
                    for raw in data.get("steps") or []:
                        if isinstance(raw, dict):
                            steps.append(TestStep.model_validate(raw))
                    self._case.steps = steps
            except Exception:
                return

    def _emit_step_selection(self) -> None:
        idx = self.selected_step_index()
        if idx is not None:
            self.step_row_selected.emit(idx)
