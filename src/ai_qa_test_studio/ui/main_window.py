from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import Qt
from PySide6.QtGui import QAction, QKeySequence
from PySide6.QtWidgets import (
    QDockWidget,
    QHBoxLayout,
    QInputDialog,
    QLabel,
    QMainWindow,
    QMessageBox,
    QPlainTextEdit,
    QPushButton,
    QSplitter,
    QStatusBar,
    QToolBar,
    QVBoxLayout,
    QWidget,
)
from qasync import asyncSlot

from ai_qa_test_studio.browser.visual_inspector import element_from_point
from ai_qa_test_studio.core.env_manager import EnvManager
from ai_qa_test_studio.core.java_importer import JavaProjectImporter
from ai_qa_test_studio.core.models import Project, TestCase
from ai_qa_test_studio.core.storage import ProjectStorage
from ai_qa_test_studio.reporting.html_reporter import HtmlReporter
from ai_qa_test_studio.ui.session_controller import SessionController
from ai_qa_test_studio.ui.widgets.data_grid import DataGrid
from ai_qa_test_studio.ui.widgets.embedded_browser import ScreencastViewport
from ai_qa_test_studio.ui.widgets.step_builder import StepBuilder
from ai_qa_test_studio.ui.widgets.test_explorer import TestExplorer
from ai_qa_test_studio.ui.wizards.ai_test_wizard import AiTestWizard


class MainWindow(QMainWindow):
    def __init__(self, data_root: Path | None = None) -> None:
        super().__init__()
        self.setWindowTitle("AI QA Test Studio v4.0")
        self.resize(1280, 800)
        self.data_root = data_root or Path("data")
        self.storage = ProjectStorage(self.data_root / "v4")
        self.env = EnvManager(self.data_root / "config")
        self.env.load_settings()
        self.project: Project | None = self.storage.load()
        self.session = SessionController()
        self._busy = False
        self._run_logs = QPlainTextEdit()
        self._run_logs.setReadOnly(True)
        self._build_chrome()
        if self.project is None:
            self.project = self._import_or_empty()
            self.storage.save(self.project)
        self._reload_ui()

    def _build_chrome(self) -> None:
        toolbar = QToolBar("Main")
        self.addToolBar(toolbar)

        act_wizard = QAction("Wizard", self)
        act_wizard.triggered.connect(self._open_wizard)
        toolbar.addAction(act_wizard)

        act_sync = QAction("Sync URL", self)
        act_sync.triggered.connect(self._sync_url)
        toolbar.addAction(act_sync)

        act_run = QAction("Run", self)
        act_run.triggered.connect(self._run_current)
        toolbar.addAction(act_run)

        act_stop = QAction("Stop", self)
        act_stop.triggered.connect(self._stop_browser)
        toolbar.addAction(act_stop)

        act_save = QAction("Save", self)
        act_save.setShortcut(QKeySequence.StandardKey.Save)
        act_save.triggered.connect(self._save_project)
        toolbar.addAction(act_save)

        palette = QAction("Command Palette", self)
        palette.setShortcut(QKeySequence("Ctrl+K"))
        palette.triggered.connect(self._command_palette)
        self.addAction(palette)

        self.explorer = TestExplorer()
        self.explorer.case_selected.connect(self._on_case_selected)
        self.builder = StepBuilder()
        self.viewport = ScreencastViewport()
        self.viewport.clicked_css.connect(self._on_viewport_click)

        right = QWidget()
        right_layout = QVBoxLayout(right)
        right_layout.addWidget(QLabel("Agent / Browser"))
        right_layout.addWidget(self.viewport, stretch=2)
        right_layout.addWidget(self._run_logs, stretch=1)
        run_row = QHBoxLayout()
        btn_run = QPushButton("Run")
        btn_run.setObjectName("primary")
        btn_run.clicked.connect(self._run_current)
        btn_sync = QPushButton("Sync URL")
        btn_sync.clicked.connect(self._sync_url)
        btn_stop = QPushButton("Stop")
        btn_stop.clicked.connect(self._stop_browser)
        run_row.addWidget(btn_run)
        run_row.addWidget(btn_sync)
        run_row.addWidget(btn_stop)
        right_layout.addLayout(run_row)

        splitter = QSplitter()
        splitter.addWidget(self.explorer)
        splitter.addWidget(self.builder)
        splitter.addWidget(right)
        splitter.setStretchFactor(0, 1)
        splitter.setStretchFactor(1, 2)
        splitter.setStretchFactor(2, 2)
        self.setCentralWidget(splitter)

        drawer = QDockWidget("Bottom Drawer", self)
        drawer_body = QWidget()
        drawer_layout = QVBoxLayout(drawer_body)
        self.data_grid = DataGrid()
        self.mcp_status = QLabel("MCP: ready (CDP attach on Run/Sync)")
        drawer_layout.addWidget(QLabel("Test Data"))
        drawer_layout.addWidget(self.data_grid)
        drawer_layout.addWidget(self.mcp_status)
        drawer.setWidget(drawer_body)
        self.addDockWidget(Qt.DockWidgetArea.BottomDockWidgetArea, drawer)

        self._status = QStatusBar()
        self._status.showMessage("Ready")
        self.setStatusBar(self._status)

    def _import_or_empty(self) -> Project:
        legacy = Path("data/project")
        if legacy.is_dir():
            importer = JavaProjectImporter(legacy, self.env)
            return importer.import_project(name="Imported Legacy Project")
        return self.storage.new_empty("New Project")

    def _reload_ui(self) -> None:
        if self.project is None:
            return
        self.explorer.load_project(self.project)
        case = self._first_case(self.project)
        if case:
            self._load_case(case)
        self._status.showMessage(
            f"{self.project.name} · {len(self.project.shared_elements)} shared elements"
        )

    def _first_case(self, project: Project) -> TestCase | None:
        for group in [project.root_group, *project.root_group.children]:
            for scenario in group.scenarios:
                if scenario.cases:
                    return scenario.cases[0]
            for suite in group.suites:
                if suite.cases:
                    return suite.cases[0]
        return None

    def _on_case_selected(self, case: object) -> None:
        if isinstance(case, TestCase):
            self._load_case(case)

    def _load_case(self, case: TestCase) -> None:
        self.builder.load_case(case)
        if case.test_data_rows:
            self.data_grid.load_rows(case.test_data_rows)
        else:
            self.data_grid.load_rows([])
        self._status.showMessage(f"Case {case.code} · {len(case.steps)} steps")

    def _open_wizard(self) -> None:
        if self.project is None:
            return
        wizard = AiTestWizard(self)
        if wizard.exec():
            scenarios = wizard.result_scenarios()
            if not scenarios:
                return
            self.project.root_group.scenarios.extend(scenarios)
            self.storage.save(self.project)
            self._reload_ui()
            self._append_log(
                f"Wizard added {len(scenarios)} scenario(s), "
                f"{sum(len(s.cases) for s in scenarios)} case(s)"
            )

    def _save_project(self) -> None:
        if self.project is None:
            return
        case = self.builder.current_case()
        if case is not None:
            self._merge_case(case)
        self.storage.save(self.project)
        self._append_log(f"Saved {self.storage.project_path}")
        self._status.showMessage("Saved")

    def _merge_case(self, case: TestCase) -> None:
        if self.project is None:
            return
        for group in [self.project.root_group, *self.project.root_group.children]:
            for scenario in group.scenarios:
                for idx, existing in enumerate(scenario.cases):
                    if existing.id == case.id:
                        scenario.cases[idx] = case
                        return
            for suite in group.suites:
                for idx, existing in enumerate(suite.cases):
                    if existing.id == case.id:
                        suite.cases[idx] = case
                        return

    def _require_case(self) -> TestCase | None:
        case = self.builder.current_case()
        if case is None:
            QMessageBox.information(self, "Case", "Chọn một test case trên Explorer trước.")
            return None
        return case

    @asyncSlot()
    async def _sync_url(self) -> None:
        if self._busy or self.project is None:
            return
        case = self._require_case()
        if case is None:
            return
        default_url = case.start_url or next(
            (s.input_data for s in case.steps if s.action == "goto" and s.input_data),
            "https://example.com",
        )
        url, ok = QInputDialog.getText(self, "Sync & Bind URL", "Base URL:", text=default_url or "")
        if not ok or not url.strip():
            return
        self._busy = True
        self._status.showMessage("Syncing…")
        try:
            updated = await self.session.sync_bind(
                self.project, case, url.strip(), log=self._append_log
            )
            self.builder.load_case(updated)
            self._merge_case(updated)
            self.storage.save(self.project)
            await self._refresh_viewport()
            self.mcp_status.setText(
                f"CDP: {self.session.engine.cdp_http_for_mcp() if self.session.started else 'n/a'}"
            )
        except Exception as exc:  # noqa: BLE001
            self._append_log(f"Sync failed: {exc}")
            QMessageBox.warning(self, "Sync URL", str(exc))
        finally:
            self._busy = False
            self._status.showMessage("Ready")

    @asyncSlot()
    async def _run_current(self) -> None:
        if self._busy or self.project is None:
            return
        case = self._require_case()
        if case is None:
            return
        self._busy = True
        self._status.showMessage("Running…")
        try:
            report = await self.session.run_case(self.project, case, log=self._append_log)
            self.builder.load_case(case)
            self._merge_case(case)
            self.storage.save(self.project)
            await self._refresh_viewport()
            out = self.data_root / "exports" / f"{report.run_id}.html"
            HtmlReporter().write(report, out)
            self._append_log(f"HTML report: {out}")
            self.mcp_status.setText(f"Last run: {report.cases[0].status}")
        except Exception as exc:  # noqa: BLE001
            self._append_log(f"Run failed: {exc}")
            QMessageBox.warning(self, "Run", str(exc))
        finally:
            self._busy = False
            self._status.showMessage("Ready")

    @asyncSlot()
    async def _stop_browser(self) -> None:
        try:
            await self.session.stop(log=self._append_log)
            self.viewport.set_placeholder("Browser stopped")
            self.mcp_status.setText("MCP/CDP: stopped")
        except Exception as exc:  # noqa: BLE001
            self._append_log(f"Stop failed: {exc}")

    @asyncSlot()
    async def _on_viewport_click(self, css_x: float, css_y: float) -> None:
        if not self.session.started:
            return
        try:
            meta = await element_from_point(self.session.engine.page, css_x, css_y)
            self._append_log(f"Inspector @ ({css_x:.0f},{css_y:.0f}) → {meta}")
        except Exception as exc:  # noqa: BLE001
            self._append_log(f"Inspector failed: {exc}")

    async def _refresh_viewport(self) -> None:
        if not self.session.started:
            return
        png, w, h = await self.session.capture_png()
        self.viewport.set_frame_png(png, w, h)

    def _command_palette(self) -> None:
        choice, ok = QInputDialog.getItem(
            self,
            "Command Palette",
            "Action:",
            ["Wizard", "Sync URL", "Run", "Save", "Stop"],
            0,
            False,
        )
        if not ok:
            return
        mapping = {
            "Wizard": self._open_wizard,
            "Sync URL": self._sync_url,
            "Run": self._run_current,
            "Save": self._save_project,
            "Stop": self._stop_browser,
        }
        mapping[choice]()

    def _append_log(self, message: str) -> None:
        self._run_logs.appendPlainText(message)

    def closeEvent(self, event) -> None:  # type: ignore[no-untyped-def]
        # Best-effort async stop without blocking forever
        if self.session.started:
            self._append_log("Closing — stop browser on next opportunity")
        super().closeEvent(event)
