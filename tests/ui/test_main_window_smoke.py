from __future__ import annotations

from pathlib import Path

import pytest

pytest.importorskip("PySide6")
from PySide6.QtWidgets import QApplication

from ai_qa_test_studio.core.models import Project, TestCase, TestGroup, TestScenario, TestStep
from ai_qa_test_studio.core.storage import ProjectStorage
from ai_qa_test_studio.ui.main_window import MainWindow


@pytest.fixture(scope="session")
def qapp() -> QApplication:
    app = QApplication.instance()
    if app is None:
        app = QApplication([])
    return app


def test_open_project_select_case(qapp: QApplication, tmp_path: Path) -> None:
    storage = ProjectStorage(tmp_path / "v4")
    case = TestCase(
        id="c1",
        code="TC1",
        title="Sample",
        steps=[
            TestStep(
                step_no=1,
                action="goto",
                intent_hint="open",
                input_data="/",
                binding_status="UNBOUND",
            )
        ],
        test_data_rows=[{"parameters": {"loaiHoSo": "Cấp mới"}, "ExpectedOutcome": "OK"}],
    )
    project = Project(
        id="p1",
        name="UI Smoke",
        root_group=TestGroup(
            id="g1",
            name="Root",
            scenarios=[TestScenario(id="s1", code="TS1", title="Sc", cases=[case])],
        ),
    )
    storage.save(project)
    window = MainWindow(data_root=tmp_path)
    window.show()
    loaded = window.builder.current_case()
    assert loaded is not None
    assert loaded.code == "TC1"
    assert window.url_edit.text()
    assert window.unbound_badge.isVisible()
    assert "UNBOUND" in window.unbound_badge.text()
    assert window.builder.unbound_banner.isVisible()
    window._append_log("smoke-ok")
    assert "smoke-ok" in window._run_logs.to_plain_text()
    headers = [
        window.data_grid.horizontalHeaderItem(i).text()
        for i in range(window.data_grid.columnCount())
    ]
    assert "Expected Outcome" in headers
    assert "Parameters" in headers
    param_item = window.data_grid.item(0, headers.index("Parameters"))
    assert param_item is not None
    assert "loaiHoSo: Cấp mới" in param_item.text()
    window._toggle_drawer()
    assert window._drawer_collapsed is True
    window.close()
