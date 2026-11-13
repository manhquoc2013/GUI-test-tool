"""Collapsible Test Data drawer with fixed 38px row height TableWidget."""

from __future__ import annotations

from typing import Any

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import QHBoxLayout, QHeaderView, QTableWidgetItem, QVBoxLayout, QWidget
from qfluentwidgets import (
    FluentIcon as FIF,
)
from qfluentwidgets import (
    PushButton,
    StrongBodyLabel,
    TableWidget,
    ToolButton,
)

from ai_qa_test_studio.constants import (
    DEFAULT_EXPECTED_OUTCOME,
    TEST_DATA_NEW_ROW_LABEL,
    THEME_BG_PANEL,
    UI_DATA_COL_EXPECTED_WIDTH,
    UI_DATA_COL_SCENARIO_WIDTH,
    UI_DRAWER_COLLAPSED_HEIGHT,
    UI_DRAWER_EXPANDED_HEIGHT,
    UI_TABLE_ROW_HEIGHT,
    UI_WIDGET_SIZE_MAX,
)
from ai_qa_test_studio.ui.formatting import format_param_cell, humanize_header

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

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setAccessibleName("Bottom Drawer")
        self.setStyleSheet(f"background-color: {THEME_BG_PANEL};")
        self._collapsed = False
        self._raw_rows: list[dict[str, Any]] = []
        self._loading = False

        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 6, 12, 8)
        layout.setSpacing(6)

        header = QHBoxLayout()
        header.setSpacing(8)
        header.addWidget(StrongBodyLabel("Test Data (Data-Driven Matrix)", self))

        self.btn_add_row = PushButton(FIF.ADD, "Thêm bộ dữ liệu", self)
        self.btn_add_row.setFixedHeight(28)
        self.btn_add_row.setToolTip("Thêm một bộ dữ liệu trống vào case đang mở")
        self.btn_add_row.setAccessibleName("Thêm bộ dữ liệu")
        self.btn_add_row.clicked.connect(lambda: self.add_requested.emit())
        header.addWidget(self.btn_add_row)
        header.addStretch(1)

        self.btn_toggle = ToolButton(FIF.CHEVRON_DOWN_MED, self)
        self.btn_toggle.setFixedSize(28, 28)
        self.btn_toggle.setToolTip("Thu gọn / Mở rộng ngăn dữ liệu")
        self.btn_toggle.setAccessibleName("Thu gọn Bottom Drawer")
        self.btn_toggle.clicked.connect(self.toggle_collapse)
        header.addWidget(self.btn_toggle)
        layout.addLayout(header)

        self.table = TableWidget(self)
        self.table.setAccessibleName("Bảng Test Data")
        self.table.setColumnCount(3)
        self.table.setHorizontalHeaderLabels(
            ["Scenario / Case", "Parameters (Tóm tắt 1 dòng)", "Expected Outcome"]
        )
        self.table.verticalHeader().setDefaultSectionSize(UI_TABLE_ROW_HEIGHT)
        self.table.verticalHeader().setMinimumSectionSize(UI_TABLE_ROW_HEIGHT)
        self.table.setWordWrap(False)
        self.table.setTextElideMode(Qt.TextElideMode.ElideRight)
        self.table.setEditTriggers(
            TableWidget.EditTrigger.DoubleClicked | TableWidget.EditTrigger.EditKeyPressed
        )

        hdr = self.table.horizontalHeader()
        hdr.setSectionResizeMode(0, QHeaderView.ResizeMode.Interactive)
        hdr.setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        hdr.setSectionResizeMode(2, QHeaderView.ResizeMode.Interactive)
        self.table.setColumnWidth(0, UI_DATA_COL_SCENARIO_WIDTH)
        self.table.setColumnWidth(2, UI_DATA_COL_EXPECTED_WIDTH)
        self.table.itemChanged.connect(self._on_item_changed)
        layout.addWidget(self.table)
        self.setMinimumHeight(UI_DRAWER_EXPANDED_HEIGHT)

    @property
    def collapsed(self) -> bool:
        return self._collapsed

    def rows(self) -> list[dict[str, Any]]:
        return [dict(row) for row in self._raw_rows]

    def load_rows(self, rows: list[dict[str, Any]]) -> None:
        self._loading = True
        try:
            self._raw_rows = [dict(row) for row in rows]
            self.table.setRowCount(len(self._raw_rows))
            for r_idx, row in enumerate(self._raw_rows):
                scenario, params, expected = self._split_row(row, r_idx)
                self.table.setItem(r_idx, 0, QTableWidgetItem(scenario))
                param_item = QTableWidgetItem(params)
                param_item.setToolTip(params.replace(" | ", "\n"))
                self.table.setItem(r_idx, 1, param_item)
                self.table.setItem(r_idx, 2, QTableWidgetItem(expected))
                self.table.setRowHeight(r_idx, UI_TABLE_ROW_HEIGHT)
        finally:
            self._loading = False

    def add_blank_row(self) -> None:
        index = len(self._raw_rows) + 1
        params = self._template_parameters()
        row: dict[str, Any] = {
            "scenario": f"{TEST_DATA_NEW_ROW_LABEL} {index}",
            "parameters": params,
            "ExpectedOutcome": DEFAULT_EXPECTED_OUTCOME,
        }
        self._raw_rows.append(row)
        self._loading = True
        try:
            r_idx = self.table.rowCount()
            self.table.insertRow(r_idx)
            scenario, params_text, expected = self._split_row(row, r_idx)
            self.table.setItem(r_idx, 0, QTableWidgetItem(scenario))
            param_item = QTableWidgetItem(params_text)
            param_item.setToolTip(params_text.replace(" | ", "\n") if params_text else "")
            self.table.setItem(r_idx, 1, param_item)
            self.table.setItem(r_idx, 2, QTableWidgetItem(expected))
            self.table.setRowHeight(r_idx, UI_TABLE_ROW_HEIGHT)
            self.table.selectRow(r_idx)
            self.table.scrollToBottom()
        finally:
            self._loading = False
        self.rows_changed.emit(self.rows())

    def toggle_collapse(self) -> None:
        self._collapsed = not self._collapsed
        self.table.setVisible(not self._collapsed)
        self.btn_add_row.setVisible(not self._collapsed)
        self.btn_toggle.setIcon(FIF.UP if self._collapsed else FIF.CHEVRON_DOWN_MED)
        if self._collapsed:
            self.setFixedHeight(UI_DRAWER_COLLAPSED_HEIGHT)
        else:
            self.setMinimumHeight(UI_DRAWER_EXPANDED_HEIGHT)
            self.setMaximumHeight(UI_WIDGET_SIZE_MAX)
            self.setFixedHeight(UI_DRAWER_EXPANDED_HEIGHT)

    def _template_parameters(self) -> dict[str, str]:
        for row in reversed(self._raw_rows):
            if isinstance(row.get("parameters"), dict) and row["parameters"]:
                return {str(k): "" for k in row["parameters"]}
            skip = set(_SCENARIO_KEYS) | set(_EXPECTED_KEYS) | {"parameters", "Parameters"}
            leftover = {k: v for k, v in row.items() if k not in skip}
            if leftover:
                return {str(k): "" for k in leftover}
        return {}

    def _on_item_changed(self, item: QTableWidgetItem) -> None:
        if self._loading or item is None:
            return
        row = item.row()
        col = item.column()
        if row < 0 or row >= len(self._raw_rows):
            return
        text = item.text().strip()
        raw = self._raw_rows[row]
        if col == 0:
            raw["scenario"] = text or f"{TEST_DATA_NEW_ROW_LABEL} {row + 1}"
            for key in _SCENARIO_KEYS:
                if key != "scenario" and key in raw:
                    del raw[key]
        elif col == 2:
            raw["ExpectedOutcome"] = text or DEFAULT_EXPECTED_OUTCOME
            for key in _EXPECTED_KEYS:
                if key != "ExpectedOutcome" and key in raw:
                    del raw[key]
        elif col == 1:
            raw["parameters"] = self._parse_params_text(text)
            item.setToolTip(text.replace(" | ", "\n") if text else "")
        self.rows_changed.emit(self.rows())

    def _parse_params_text(self, text: str) -> dict[str, str]:
        params: dict[str, str] = {}
        if not text.strip():
            return params
        for part in text.split("|"):
            chunk = part.strip()
            if not chunk:
                continue
            if ":" in chunk:
                key, _, value = chunk.partition(":")
                params[key.strip()] = value.strip()
            else:
                params[chunk] = ""
        return params

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
