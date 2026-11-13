from __future__ import annotations

import pytest

pytest.importorskip("PySide6")
from PySide6.QtWidgets import QApplication, QWidget

from ai_qa_test_studio.ui.widgets.command_palette import CommandPalette, PaletteCommand
from ai_qa_test_studio.ui.widgets.step_progress_bar import StepProgressBar
from ai_qa_test_studio.ui.wizards.ai_test_wizard import AiTestWizard


@pytest.fixture(scope="module")
def qapp() -> QApplication:
    app = QApplication.instance()
    if app is None:
        app = QApplication([])
    return app


@pytest.fixture
def host(qapp: QApplication) -> QWidget:
    widget = QWidget()
    widget.resize(960, 720)
    widget.show()
    return widget


def test_step_progress_bar_advances(qapp: QApplication) -> None:
    bar = StepProgressBar(["A", "B", "C"])
    assert bar.step_count == 3
    assert bar.current_index == 0
    bar.set_current(2)
    assert bar.current_index == 2
    bar.set_current(99)
    assert bar.current_index == 2


def test_command_palette_filters(host: QWidget) -> None:
    hits: list[str] = []

    def _mark(key: str) -> None:
        hits.append(key)

    commands = [
        PaletteCommand("run", "Run", "Chạy case", ("play",), lambda: _mark("run")),
        PaletteCommand("sync", "Sync URL", "Bind selector", ("bind",), lambda: _mark("sync")),
        PaletteCommand("save", "Save Project", "Persist", ("persist",), lambda: _mark("save")),
    ]
    palette = CommandPalette(commands, host)
    palette.search.setText("sync")
    assert palette.list.count() == 1
    assert "Sync" in palette.list.item(0).text()
    palette.list.setCurrentRow(0)
    assert palette.validate() is True
    chosen = palette.chosen()
    assert chosen is not None
    assert chosen.key == "sync"
    palette.close()


def test_wizard_three_steps_produce_drafts(host: QWidget) -> None:
    wizard = AiTestWizard(host)
    wizard.source_page.markdown.setPlainText("Feature: tạo hồ sơ người yếu thế")
    wizard.source_page.url.setText("https://staging.example.com/ho-so")
    assert wizard.validate() is False
    assert wizard.progress.current_index == 1
    assert wizard.scenario_page.list.count() >= 1
    assert wizard.validate() is False
    assert wizard.progress.current_index == 2
    assert wizard.validate() is True
    scenarios = wizard.result_scenarios()
    assert scenarios
    assert any(case.steps for sc in scenarios for case in sc.cases)
    wizard.close()
