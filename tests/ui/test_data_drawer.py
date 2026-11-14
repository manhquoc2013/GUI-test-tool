"""Test Data drawer — summary table + modal add/edit."""

from __future__ import annotations

import pytest

pytest.importorskip("PySide6")
from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.constants import DEFAULT_EXPECTED_OUTCOME, TEST_DATA_NEW_ROW_LABEL
from ai_qa_test_studio.ui.widgets.data_drawer import TestDataDrawer
from ai_qa_test_studio.ui.widgets.test_data_row_dialog import TestDataRowDialog


@pytest.fixture(scope="session")
def qapp() -> QApplication:
    app = QApplication.instance()
    if app is None:
        app = QApplication([])
    return app


def test_add_requested_signal_from_button(qapp: QApplication) -> None:
    drawer = TestDataDrawer()
    hits: list[int] = []
    drawer.add_requested.connect(lambda: hits.append(1))
    drawer.btn_add_row.click()
    assert hits == [1]
    assert drawer.table.rowCount() == 0


def test_table_is_read_only_summary(qapp: QApplication) -> None:
    from PySide6.QtWidgets import QAbstractItemView

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
    assert drawer.table.rowCount() == 1
    assert drawer.table.editTriggers() == QAbstractItemView.EditTrigger.NoEditTriggers
    assert "loaiHoSo" in (drawer.table.item(0, 1).text() or "")


def test_row_dialog_builds_result(qapp: QApplication) -> None:
    from PySide6.QtWidgets import QWidget

    host = QWidget()
    dialog = TestDataRowDialog(
        host,
        scenario="Case A",
        parameters={"cccd": "001", "hoTen": ""},
        expected="SUCCESS",
        editing=False,
        index=0,
    )
    dialog.scenario_edit.setText("Case A")
    dialog._param_rows[0][1].setText("001060012345")
    dialog._param_rows[1][1].setText("Nguyen Van A")
    dialog.expected_combo.setCurrentText("SUCCESS")
    row = dialog.result_row()
    assert row["scenario"] == "Case A"
    assert row["parameters"]["cccd"] == "001060012345"
    assert row["parameters"]["hoTen"] == "Nguyen Van A"
    assert row["ExpectedOutcome"] == str(DEFAULT_EXPECTED_OUTCOME)
    dialog.close()


def test_row_dialog_preserves_legacy_outcome(qapp: QApplication) -> None:
    from PySide6.QtWidgets import QWidget

    host = QWidget()
    dialog = TestDataRowDialog(
        host,
        scenario="Legacy",
        parameters={},
        expected="200 OK",
        editing=True,
        index=0,
    )
    row = dialog.result_row()
    assert row["ExpectedOutcome"] == "200 OK"
    dialog.expected_combo.setCurrentText("VALIDATION_ERROR")
    touched = dialog.result_row()
    assert touched["ExpectedOutcome"] == "VALIDATION_ERROR"
    dialog.close()


def test_apply_dialog_row_via_upsert(qapp: QApplication) -> None:
    drawer = TestDataDrawer()
    drawer.load_rows([])
    seen: list[list[dict[str, object]]] = []
    drawer.rows_changed.connect(lambda rows: seen.append(rows))
    drawer._raw_rows.append(
        {
            "scenario": f"{TEST_DATA_NEW_ROW_LABEL} 1",
            "parameters": {"loaiHoSo": "", "hoTen": ""},
            "ExpectedOutcome": DEFAULT_EXPECTED_OUTCOME,
        }
    )
    drawer._refresh_table()
    drawer.rows_changed.emit(drawer.rows())
    assert len(seen) == 1
    assert drawer.table.rowCount() == 1
    assert str(seen[0][0]["scenario"]).startswith(TEST_DATA_NEW_ROW_LABEL)
