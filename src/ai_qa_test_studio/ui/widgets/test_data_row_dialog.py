"""Modal thêm/sửa một bộ Test Data (scenario + parameters + expected)."""

from __future__ import annotations

from typing import Any

from PySide6.QtCore import Qt
from PySide6.QtWidgets import QHBoxLayout, QSizePolicy, QVBoxLayout, QWidget
from qfluentwidgets import (
    BodyLabel,
    LineEdit,
    MessageBoxBase,
    PushButton,
    StrongBodyLabel,
    SubtitleLabel,
    TransparentToolButton,
)
from qfluentwidgets import FluentIcon as FIF

from ai_qa_test_studio.constants import (
    DEFAULT_EXPECTED_OUTCOME,
    TEST_DATA_NEW_ROW_LABEL,
    UI_TEST_DATA_DIALOG_MIN_WIDTH,
    UI_TEST_DATA_PARAM_ROW_SPACING,
)


class TestDataRowDialog(MessageBoxBase):
    def __init__(
        self,
        parent: QWidget,
        *,
        scenario: str = "",
        parameters: dict[str, Any] | None = None,
        expected: str = DEFAULT_EXPECTED_OUTCOME,
        editing: bool = False,
        index: int = 0,
    ) -> None:
        super().__init__(parent)
        self._editing = editing
        self._deleted = False
        title = "Sửa bộ dữ liệu" if editing else "Thêm bộ dữ liệu"
        self.setWindowTitle(title)
        self.widget.setMinimumWidth(UI_TEST_DATA_DIALOG_MIN_WIDTH)

        self.viewLayout.addWidget(SubtitleLabel(title, self.widget))

        self.viewLayout.addWidget(StrongBodyLabel("Scenario / Case", self.widget))
        self.scenario_edit = LineEdit(self.widget)
        self.scenario_edit.setPlaceholderText(f"{TEST_DATA_NEW_ROW_LABEL} {index + 1}")
        self.scenario_edit.setText(scenario)
        self.scenario_edit.setClearButtonEnabled(True)
        self.viewLayout.addWidget(self.scenario_edit)

        self.viewLayout.addWidget(StrongBodyLabel("Parameters", self.widget))
        self.viewLayout.addWidget(
            BodyLabel("Mỗi dòng: tên tham số → giá trị (dùng trong bước dạng {{ten}}).", self.widget)
        )
        self._params_host = QWidget(self.widget)
        self._params_layout = QVBoxLayout(self._params_host)
        self._params_layout.setContentsMargins(0, 0, 0, 0)
        self._params_layout.setSpacing(UI_TEST_DATA_PARAM_ROW_SPACING)
        self._param_rows: list[tuple[LineEdit, LineEdit, QWidget]] = []
        self.viewLayout.addWidget(self._params_host)

        seed = parameters or {}
        if seed:
            for key, value in seed.items():
                self._add_param_row(str(key), "" if value is None else str(value))
        else:
            self._add_param_row("", "")

        self.btn_add_param = PushButton(FIF.ADD, "Thêm tham số", self.widget)
        self.btn_add_param.setFixedHeight(28)
        self.btn_add_param.clicked.connect(lambda: self._add_param_row("", ""))
        self.viewLayout.addWidget(self.btn_add_param, alignment=Qt.AlignmentFlag.AlignLeft)

        self.viewLayout.addWidget(StrongBodyLabel("Expected Outcome", self.widget))
        self.expected_edit = LineEdit(self.widget)
        self.expected_edit.setPlaceholderText(DEFAULT_EXPECTED_OUTCOME)
        self.expected_edit.setText(expected or DEFAULT_EXPECTED_OUTCOME)
        self.expected_edit.setClearButtonEnabled(True)
        self.viewLayout.addWidget(self.expected_edit)

        self.yesButton.setText("Lưu")
        self.cancelButton.setText("Hủy")
        if editing:
            self.btn_delete = PushButton(FIF.DELETE, "Xóa bộ này", self.widget)
            self.btn_delete.clicked.connect(self._on_delete)
            self.viewLayout.addWidget(self.btn_delete, alignment=Qt.AlignmentFlag.AlignLeft)

    def _add_param_row(self, key: str, value: str) -> None:
        row = QWidget(self._params_host)
        row.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        layout = QHBoxLayout(row)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(UI_TEST_DATA_PARAM_ROW_SPACING)
        key_edit = LineEdit(row)
        key_edit.setPlaceholderText("tên")
        key_edit.setText(key)
        key_edit.setMinimumWidth(120)
        value_edit = LineEdit(row)
        value_edit.setPlaceholderText("giá trị")
        value_edit.setText(value)
        value_edit.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        remove = TransparentToolButton(FIF.DELETE, row)
        remove.setFixedSize(28, 28)
        remove.setToolTip("Xóa tham số")
        remove.clicked.connect(lambda: self._remove_param_row(row))
        layout.addWidget(key_edit)
        layout.addWidget(value_edit, stretch=1)
        layout.addWidget(remove)
        self._params_layout.addWidget(row)
        self._param_rows.append((key_edit, value_edit, row))

    def _remove_param_row(self, row: QWidget) -> None:
        kept: list[tuple[LineEdit, LineEdit, QWidget]] = []
        for key_edit, value_edit, widget in self._param_rows:
            if widget is row:
                self._params_layout.removeWidget(widget)
                widget.deleteLater()
                continue
            kept.append((key_edit, value_edit, widget))
        self._param_rows = kept
        if not self._param_rows:
            self._add_param_row("", "")

    def _on_delete(self) -> None:
        self._deleted = True
        self.accept()

    @property
    def deleted(self) -> bool:
        return self._deleted

    def result_row(self) -> dict[str, Any]:
        scenario = self.scenario_edit.text().strip()
        if not scenario:
            scenario = self.scenario_edit.placeholderText().strip() or TEST_DATA_NEW_ROW_LABEL
        params: dict[str, str] = {}
        for key_edit, value_edit, _row in self._param_rows:
            key = key_edit.text().strip()
            if not key:
                continue
            params[key] = value_edit.text().strip()
        expected = self.expected_edit.text().strip() or DEFAULT_EXPECTED_OUTCOME
        return {
            "scenario": scenario,
            "parameters": params,
            "ExpectedOutcome": expected,
        }
