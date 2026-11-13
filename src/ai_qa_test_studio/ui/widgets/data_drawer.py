"""Collapsible Test Data drawer with fixed 38px row height TableWidget."""

from __future__ import annotations

from typing import Any

from PySide6.QtCore import Qt
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
    THEME_BG_PANEL,
    UI_DRAWER_COLLAPSED_HEIGHT,
    UI_DRAWER_EXPANDED_HEIGHT,
    UI_TABLE_ROW_HEIGHT,
)
from ai_qa_test_studio.ui.formatting import format_param_cell, humanize_header


class TestDataDrawer(QWidget):
    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setAccessibleName("Bottom Drawer")
        self.setStyleSheet(f"background-color: {THEME_BG_PANEL};")
        self._collapsed = False
        self._raw_rows: list[dict[str, Any]] = []

        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 6, 12, 8)
        layout.setSpacing(6)

        header = QHBoxLayout()
        header.setSpacing(8)
        header.addWidget(StrongBodyLabel("Test Data (Data-Driven Matrix)", self))

        self.btn_add_row = PushButton(FIF.ADD, "Thêm bộ dữ liệu", self)
        self.btn_add_row.setFixedHeight(28)
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

        hdr = self.table.horizontalHeader()
        hdr.setSectionResizeMode(0, QHeaderView.ResizeMode.Interactive)
        hdr.setSectionResizeMode(1, QHeaderView.ResizeMode.Stretch)
        hdr.setSectionResizeMode(2, QHeaderView.ResizeMode.Interactive)
        self.table.setColumnWidth(0, 280)
        self.table.setColumnWidth(2, 160)
        layout.addWidget(self.table)
        self.setMinimumHeight(UI_DRAWER_EXPANDED_HEIGHT)

    @property
    def collapsed(self) -> bool:
        return self._collapsed

    def load_rows(self, rows: list[dict[str, Any]]) -> None:
        self._raw_rows = list(rows)
        self.table.setRowCount(len(rows))
        for r_idx, row in enumerate(rows):
            scenario, params, expected = self._split_row(row, r_idx)
            self.table.setItem(r_idx, 0, QTableWidgetItem(scenario))
            param_item = QTableWidgetItem(params)
            param_item.setToolTip(params.replace(" | ", "\n"))
            self.table.setItem(r_idx, 1, param_item)
            self.table.setItem(r_idx, 2, QTableWidgetItem(expected))
            self.table.setRowHeight(r_idx, UI_TABLE_ROW_HEIGHT)

    def toggle_collapse(self) -> None:
        self._collapsed = not self._collapsed
        self.table.setVisible(not self._collapsed)
        self.btn_add_row.setVisible(not self._collapsed)
        self.btn_toggle.setIcon(FIF.UP if self._collapsed else FIF.CHEVRON_DOWN_MED)
        if self._collapsed:
            self.setFixedHeight(UI_DRAWER_COLLAPSED_HEIGHT)
        else:
            self.setMinimumHeight(UI_DRAWER_EXPANDED_HEIGHT)
            self.setMaximumHeight(16777215)
            self.setFixedHeight(UI_DRAWER_EXPANDED_HEIGHT)

    def _split_row(self, row: dict[str, Any], index: int) -> tuple[str, str, str]:
        scenario_keys = ("scenario", "Scenario", "case", "Case", "name", "Name", "title")
        expected_keys = (
            "ExpectedOutcome",
            "expected_outcome",
            "Expected Outcome",
            "expected",
            "ExpectedResult",
        )
        scenario = next((str(row[k]) for k in scenario_keys if k in row and row[k]), "")
        if not scenario:
            scenario = f"Bộ dữ liệu {index + 1}"

        expected = ""
        for key in expected_keys:
            if key in row:
                expected = str(row[key])
                break

        if "parameters" in row:
            params = format_param_cell(row["parameters"])
        else:
            skip = set(scenario_keys) | set(expected_keys) | {"parameters", "Parameters"}
            leftover = {k: v for k, v in row.items() if k not in skip}
            params = format_param_cell(leftover) if leftover else ""

        # Prefer humanized parameter header for empty leftover that was only ExpectedOutcome+params
        if not params and len(row) == 1:
            only_key = next(iter(row))
            params = f"{humanize_header(only_key)}: {row[only_key]}"
        return scenario, params, expected
