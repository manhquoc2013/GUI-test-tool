from __future__ import annotations

from PySide6.QtWidgets import QTableWidget, QTableWidgetItem


class DataGrid(QTableWidget):
    def __init__(self, parent=None) -> None:  # type: ignore[no-untyped-def]
        super().__init__(0, 3, parent)
        self.setHorizontalHeaderLabels(["Column A", "Column B", "Column C"])

    def load_rows(self, rows: list[dict]) -> None:  # type: ignore[type-arg]
        if not rows:
            self.setRowCount(0)
            return
        keys = list(rows[0].keys())
        self.setColumnCount(len(keys))
        self.setHorizontalHeaderLabels([str(k) for k in keys])
        self.setRowCount(len(rows))
        for r, row in enumerate(rows):
            for c, key in enumerate(keys):
                self.setItem(r, c, QTableWidgetItem(str(row.get(key, ""))))
