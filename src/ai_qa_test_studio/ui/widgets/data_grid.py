"""Test Data grid with human-readable parameters and resizable headers."""

from __future__ import annotations

from typing import Any

from PySide6.QtCore import Qt
from PySide6.QtWidgets import QHeaderView, QTableWidget, QTableWidgetItem

from ai_qa_test_studio.ui.formatting import format_param_cell, humanize_header


class DataGrid(QTableWidget):
    def __init__(self, parent=None) -> None:  # type: ignore[no-untyped-def]
        super().__init__(0, 3, parent)
        self.setAccessibleName("Bảng Test Data")
        self.setHorizontalHeaderLabels(["Cột A", "Cột B", "Cột C"])
        self._raw_rows: list[dict[str, Any]] = []
        self._keys: list[str] = []
        header = self.horizontalHeader()
        header.setSectionResizeMode(QHeaderView.ResizeMode.Interactive)
        header.setStretchLastSection(True)
        header.setDefaultAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        self.setWordWrap(True)
        self.cellDoubleClicked.connect(self._on_double_click)

    def load_rows(self, rows: list[dict[str, Any]]) -> None:
        self._raw_rows = list(rows)
        if not rows:
            self._keys = []
            self.setRowCount(0)
            return
        keys = list(rows[0].keys())
        self._keys = keys
        self.setColumnCount(len(keys))
        self.setHorizontalHeaderLabels([humanize_header(str(k)) for k in keys])
        self.setRowCount(len(rows))
        for r, row in enumerate(rows):
            for c, key in enumerate(keys):
                value = row.get(key, "")
                display = format_param_cell(value)
                item = QTableWidgetItem(display)
                item.setData(Qt.ItemDataRole.UserRole, value)
                item.setToolTip(repr(value) if isinstance(value, (dict, list)) else display)
                self.setItem(r, c, item)
        self.resizeRowsToContents()

    def _on_double_click(self, row: int, column: int) -> None:
        item = self.item(row, column)
        if item is None:
            return
        raw = item.data(Qt.ItemDataRole.UserRole)
        if isinstance(raw, (dict, list)):
            item.setText(repr(raw))
            item.setToolTip("Double-click lại để xem dạng gọn")
        else:
            # Toggle back to compact if previously expanded via repr
            if row < len(self._raw_rows) and column < len(self._keys):
                value = self._raw_rows[row].get(self._keys[column], "")
                item.setText(format_param_cell(value))
                item.setData(Qt.ItemDataRole.UserRole, value)
