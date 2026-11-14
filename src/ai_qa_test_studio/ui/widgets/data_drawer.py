"""Collapsible Test Data drawer — summary table + modal add/edit."""

from __future__ import annotations

from typing import Any

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QHBoxLayout,
    QHeaderView,
    QStackedWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)
from qfluentwidgets import (
    FluentIcon as FIF,
)
from qfluentwidgets import (
    PushButton,
    SegmentedWidget,
    TableWidget,
    TextEdit,
    TransparentToolButton,
)

from ai_qa_test_studio.constants import (
    DEFAULT_EXPECTED_OUTCOME,
    TEST_DATA_NEW_ROW_LABEL,
    UI_DATA_COL_EXPECTED_WIDTH,
    UI_DATA_COL_SCENARIO_WIDTH,
    UI_DATA_HEADER_MIN_SECTION,
    UI_DATA_TABLE_ROW_HEIGHT,
    UI_DRAWER_COLLAPSED_HEIGHT,
    UI_DRAWER_EXPAND_MIN_HEIGHT,
    UI_DRAWER_EXPANDED_HEIGHT,
    UI_WIDGET_SIZE_MAX,
)
from ai_qa_test_studio.ui.formatting import format_param_cell, humanize_header
from ai_qa_test_studio.ui.theme import card_stylesheet
from ai_qa_test_studio.ui.theme_bind import bind_widget_theme
from ai_qa_test_studio.ui.widgets.test_data_row_dialog import TestDataRowDialog

_SCENARIO_KEYS = ("scenario", "Scenario", "case", "Case", "name", "Name", "title")
_EXPECTED_KEYS = (
    "expected_outcome",
    "expectedOutcome",
    "ExpectedOutcome",
    "Expected Outcome",
    "expected",
    "ExpectedResult",
)


class TestDataDrawer(QWidget):
    add_requested = Signal()
    rows_changed = Signal(list)
    toggled = Signal(bool)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("TestDataDrawer")
        self.setAccessibleName("Bottom Drawer")
        self._collapsed = False
        self._raw_rows: list[dict[str, Any]] = []
        self._loading = False

        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 6, 12, 10)
        layout.setSpacing(8)

        header = QHBoxLayout()
        header.setSpacing(8)

        self.tabs = SegmentedWidget(self)
        self.tabs.addItem("data", "Test Data")
        self.tabs.addItem("console", "Console Log")
        self.tabs.setCurrentItem("data")
        header.addWidget(self.tabs)

        self.btn_add_row = PushButton(FIF.ADD, "Thêm bộ dữ liệu", self)
        self.btn_add_row.setFixedHeight(28)
        self.btn_add_row.setToolTip("Mở form thêm bộ dữ liệu cho case đang mở")
        self.btn_add_row.setAccessibleName("Thêm bộ dữ liệu")
        self.btn_add_row.clicked.connect(lambda: self.add_requested.emit())
        header.addWidget(self.btn_add_row)
        header.addStretch(1)

        self.btn_toggle = TransparentToolButton(FIF.CHEVRON_DOWN_MED, self)
        self.btn_toggle.setFixedSize(28, 28)
        self.btn_toggle.setToolTip("Thu gọn / Mở rộng ngăn dữ liệu")
        self.btn_toggle.setAccessibleName("Thu gọn Bottom Drawer")
        self.btn_toggle.clicked.connect(self.toggle_collapse)
        header.addWidget(self.btn_toggle)
        layout.addLayout(header)

        self.stack = QStackedWidget(self)
        self.table = self._build_data_table()
        self.console = TextEdit(self)
        self.console.setReadOnly(True)
        self.console.setPlaceholderText("Console log…")
        self.stack.addWidget(self.table)
        self.stack.addWidget(self.console)
        self.tabs.currentItemChanged.connect(
            lambda key: self.stack.setCurrentIndex(0 if key == "data" else 1)
        )
        layout.addWidget(self.stack, 1)

        self.setMinimumHeight(UI_DRAWER_EXPANDED_HEIGHT)
        self._apply_theme_style()
        self._theme_slot = bind_widget_theme(self, self._apply_theme_style)

    def _build_data_table(self) -> TableWidget:
        table = TableWidget(self)
        table.setAccessibleName("Bảng Test Data")
        table.setColumnCount(3)
        table.setHorizontalHeaderLabels(
            ["Scenario / Case", "Parameters (Tóm tắt 1 dòng)", "Expected Outcome"]
        )
        table.verticalHeader().setVisible(False)
        table.verticalHeader().setDefaultSectionSize(UI_DATA_TABLE_ROW_HEIGHT)
        table.verticalHeader().setMinimumSectionSize(UI_DATA_TABLE_ROW_HEIGHT)
        table.setWordWrap(False)
        table.setTextElideMode(Qt.TextElideMode.ElideRight)
        table.setHorizontalScrollMode(TableWidget.ScrollMode.ScrollPerPixel)
        table.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        table.setEditTriggers(TableWidget.EditTrigger.NoEditTriggers)
        table.setSelectionBehavior(TableWidget.SelectionBehavior.SelectRows)
        table.setSelectionMode(TableWidget.SelectionMode.SingleSelection)
        table.setToolTip("Nhấp đúp để sửa bộ dữ liệu")
        hdr = table.horizontalHeader()
        hdr.setMinimumSectionSize(UI_DATA_HEADER_MIN_SECTION)
        hdr.setSectionResizeMode(0, QHeaderView.ResizeMode.Interactive)
        hdr.setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        hdr.setSectionResizeMode(2, QHeaderView.ResizeMode.Interactive)
        table.setColumnWidth(0, UI_DATA_COL_SCENARIO_WIDTH)
        table.setColumnWidth(2, UI_DATA_COL_EXPECTED_WIDTH)
        table.doubleClicked.connect(self._on_row_double_clicked)
        return table

    @property
    def collapsed(self) -> bool:
        return self._collapsed

    def rows(self) -> list[dict[str, Any]]:
        return [dict(row) for row in self._raw_rows]

    def load_rows(self, rows: list[dict[str, Any]]) -> None:
        self._loading = True
        try:
            self._raw_rows = [dict(row) for row in rows]
            self._refresh_table()
        finally:
            self._loading = False

    def open_add_dialog(self) -> bool:
        index = len(self._raw_rows)
        dialog = TestDataRowDialog(
            self.window() or self,
            scenario=f"{TEST_DATA_NEW_ROW_LABEL} {index + 1}",
            parameters=self._template_parameters(),
            expected=DEFAULT_EXPECTED_OUTCOME,
            editing=False,
            index=index,
        )
        if not dialog.exec():
            return False
        self._raw_rows.append(dialog.result_row())
        self._refresh_table()
        self.table.selectRow(len(self._raw_rows) - 1)
        self.table.scrollToBottom()
        self.rows_changed.emit(self.rows())
        return True

    def open_edit_dialog(self, row_index: int) -> bool:
        if row_index < 0 or row_index >= len(self._raw_rows):
            return False
        raw = self._raw_rows[row_index]
        scenario, _params_text, expected = self._split_row(raw, row_index)
        parameters = self._extract_parameters(raw)
        dialog = TestDataRowDialog(
            self.window() or self,
            scenario=scenario,
            parameters=parameters,
            expected=expected,
            editing=True,
            index=row_index,
        )
        if not dialog.exec():
            return False
        if dialog.deleted:
            del self._raw_rows[row_index]
        else:
            self._raw_rows[row_index] = dialog.result_row()
        self._refresh_table()
        self.rows_changed.emit(self.rows())
        return True

    def add_blank_row(self) -> None:
        """Compat: mở modal thêm (không còn chèn dòng trống inline)."""
        self.open_add_dialog()

    def toggle_collapse(self) -> None:
        self._collapsed = not self._collapsed
        self.stack.setVisible(not self._collapsed)
        self.btn_add_row.setVisible(not self._collapsed)
        self.btn_toggle.setIcon(FIF.CHEVRON_RIGHT_MED if self._collapsed else FIF.CHEVRON_DOWN_MED)
        if self._collapsed:
            self.setMinimumHeight(UI_DRAWER_COLLAPSED_HEIGHT)
            self.setMaximumHeight(UI_DRAWER_COLLAPSED_HEIGHT)
        else:
            self.setMinimumHeight(UI_DRAWER_EXPAND_MIN_HEIGHT)
            self.setMaximumHeight(UI_WIDGET_SIZE_MAX)
        self.toggled.emit(not self._collapsed)

    def _apply_theme_style(self) -> None:
        self.setStyleSheet(card_stylesheet("#TestDataDrawer"))

    def _on_row_double_clicked(self, index) -> None:  # type: ignore[no-untyped-def]
        if self._loading:
            return
        self.open_edit_dialog(index.row())

    def _refresh_table(self) -> None:
        self._loading = True
        try:
            self.table.setRowCount(len(self._raw_rows))
            self.tabs.setItemText("data", f"Test Data ({len(self._raw_rows)})")
            for r_idx, row in enumerate(self._raw_rows):
                scenario, params, expected = self._split_row(row, r_idx)
                self.table.setItem(r_idx, 0, QTableWidgetItem(scenario))
                param_item = QTableWidgetItem(params)
                param_item.setToolTip(params.replace(" | ", "\n") if params else "")
                self.table.setItem(r_idx, 1, param_item)
                self.table.setItem(r_idx, 2, QTableWidgetItem(expected))
                self.table.setRowHeight(r_idx, UI_DATA_TABLE_ROW_HEIGHT)
        finally:
            self._loading = False

    def _template_parameters(self) -> dict[str, str]:
        for row in reversed(self._raw_rows):
            params = self._extract_parameters(row)
            if params:
                return {str(k): "" for k in params}
        return {}

    def _extract_parameters(self, row: dict[str, Any]) -> dict[str, str]:
        if isinstance(row.get("parameters"), dict):
            return {str(k): "" if v is None else str(v) for k, v in row["parameters"].items()}
        skip = set(_SCENARIO_KEYS) | set(_EXPECTED_KEYS) | {"parameters", "Parameters"}
        leftover = {k: v for k, v in row.items() if k not in skip}
        return {str(k): "" if v is None else str(v) for k, v in leftover.items()}

    def _split_row(self, row: dict[str, Any], index: int) -> tuple[str, str, str]:
        scenario = next((str(row[k]) for k in _SCENARIO_KEYS if k in row and row[k]), "")
        if not scenario:
            scenario = f"Bộ dữ liệu {index + 1}"

        expected = DEFAULT_EXPECTED_OUTCOME
        for key in _EXPECTED_KEYS:
            value = row.get(key)
            if value is not None and str(value).strip():
                expected = str(value)
                break

        if "parameters" in row:
            params = format_param_cell(row["parameters"])
        else:
            skip = set(_SCENARIO_KEYS) | set(_EXPECTED_KEYS) | {"parameters", "Parameters"}
            leftover = {k: v for k, v in row.items() if k not in skip}
            params = format_param_cell(leftover) if leftover else ""

        if not params and len(row) == 1:
            only_key = next(iter(row))
            params = f"{humanize_header(only_key)}: {row[only_key]}"
        return scenario, params, expected
