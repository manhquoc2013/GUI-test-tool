"""Test Data drawer — add row button must mutate and emit rows."""

from __future__ import annotations

import pytest

pytest.importorskip("PySide6")
from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.constants import DEFAULT_EXPECTED_OUTCOME, TEST_DATA_NEW_ROW_LABEL
from ai_qa_test_studio.ui.widgets.data_drawer import TestDataDrawer


@pytest.fixture(scope="session")
def qapp() -> QApplication:
    app = QApplication.instance()
    if app is None:
        app = QApplication([])
    return app


def test_add_blank_row_emits_and_fills_template(qapp: QApplication) -> None:
    drawer = TestDataDrawer()
    drawer.load_rows(
        [
            {
                "scenario": "Happy Path",
                "parameters": {"loaiHoSo": "Cá nhân", "hoTen": "A"},
                "ExpectedOutcome": "SUCCESS",
            }
        ]
    )
    seen: list[list[dict[str, object]]] = []
    drawer.rows_changed.connect(lambda rows: seen.append(rows))

    drawer.add_blank_row()
    assert len(seen) == 1
    assert drawer.table.rowCount() == 2
    rows = seen[0]
    assert str(rows[1]["scenario"]).startswith(TEST_DATA_NEW_ROW_LABEL)
    assert rows[1]["ExpectedOutcome"] == DEFAULT_EXPECTED_OUTCOME
    assert rows[1]["parameters"] == {"loaiHoSo": "", "hoTen": ""}


def test_add_requested_signal_from_button(qapp: QApplication) -> None:
    drawer = TestDataDrawer()
    hits: list[int] = []
    drawer.add_requested.connect(lambda: hits.append(1))
    drawer.btn_add_row.click()
    assert hits == [1]
    assert drawer.table.rowCount() == 0
