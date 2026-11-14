from __future__ import annotations

import pytest

pytest.importorskip("PySide6")
from PySide6.QtWidgets import QApplication, QWidget

from ai_qa_test_studio.ui.widgets.command_palette import CommandPalette, PaletteCommand
from ai_qa_test_studio.ui.widgets.compose_wizard_panel import ComposeWizardPanel
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


def test_compose_wizard_panel_three_steps_emit_drafts(host: QWidget) -> None:
    panel = ComposeWizardPanel(host)
    caught: list[object] = []
    panel.drafts_ready.connect(lambda scenarios, url: caught.append((scenarios, url)))
    panel.source_page.markdown.setPlainText("Feature: tạo hồ sơ người yếu thế")
    panel.source_page.url.setText("https://staging.example.com/ho-so")
    panel._go_next()
    assert panel.progress.current_index == 1
    assert panel.scenario_page.list.count() >= 1
    panel._go_next()
    assert panel.progress.current_index == 2
    panel._go_next()
    assert caught
    scenarios, url = caught[0]  # type: ignore[misc]
    assert scenarios
    assert url.startswith("https://")
    assert any(case.steps for sc in scenarios for case in sc.cases)
    panel.close()


def test_compose_wizard_imports_csv_test_cases(host: QWidget, tmp_path) -> None:
    from pathlib import Path

    csv_path = Path(tmp_path) / "login_scenarios.csv"
    csv_path.write_text(
        "Test Case ID,Scenario ID,Title,Step No,Action,Test Data,Expected Result\n"
        "TC01,TS_LOGIN,Login,1,goto,https://app.example/,open\n"
        "TC01,TS_LOGIN,Login,2,click,,submit\n",
        encoding="utf-8",
    )
    panel = ComposeWizardPanel(host)
    panel.source_page.markdown.setPlainText("")
    from ai_qa_test_studio.core.wizard_documents import load_wizard_document

    doc = load_wizard_document(csv_path)
    panel.source_page._documents.append(doc)
    panel.source_page.doc_list.addItem(doc.display_name)
    panel._go_next()
    assert panel.progress.current_index == 1
    codes = [
        panel.scenario_page.list.item(i).text()
        for i in range(panel.scenario_page.list.count())
    ]
    assert any("TS_LOGIN" in text for text in codes)
    assert not any("TS_HAPPY" in text or "TS_NEG" in text for text in codes)
    panel.close()


def test_compose_wizard_reset_clears_sample_markdown(host: QWidget) -> None:
    from ai_qa_test_studio.constants import WIZARD_DOC_SAMPLE_MARKDOWN

    panel = ComposeWizardPanel(host)
    panel.source_page.markdown.setPlainText("custom notes")
    panel.reset()
    assert panel.source_page.markdown.toPlainText() == WIZARD_DOC_SAMPLE_MARKDOWN
    assert panel.source_page.documents() == []
    panel.close()


def test_ai_test_wizard_embeds_compose_panel(host: QWidget) -> None:
    wizard = AiTestWizard(host)
    assert isinstance(wizard.panel, ComposeWizardPanel)
    wizard.close()
