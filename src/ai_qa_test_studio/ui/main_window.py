from __future__ import annotations

import uuid
from pathlib import Path

from PySide6.QtCore import Qt
from PySide6.QtGui import QAction, QKeySequence
from PySide6.QtWidgets import (
    QComboBox,
    QDockWidget,
    QFrame,
    QHBoxLayout,
    QInputDialog,
    QLabel,
    QLineEdit,
    QMainWindow,
    QMessageBox,
    QPushButton,
    QSizePolicy,
    QSplitter,
    QStatusBar,
    QToolBar,
    QVBoxLayout,
    QWidget,
)
from qasync import asyncSlot

from ai_qa_test_studio.agent.skill_loader import ProgressiveSkillLoader
from ai_qa_test_studio.browser.visual_inspector import element_from_point
from ai_qa_test_studio.constants import (
    DEFAULT_SKILLS_DIRNAME,
    DEFAULT_TARGET_URL,
    TOKEN_CONTEXT_BUDGET,
    UI_DRAWER_COLLAPSED_HEIGHT,
)
from ai_qa_test_studio.core.env_manager import EnvManager
from ai_qa_test_studio.core.java_importer import JavaProjectImporter
from ai_qa_test_studio.core.models import Project, RunReport, TestCase, TestScenario, TestStep
from ai_qa_test_studio.core.storage import ProjectStorage
from ai_qa_test_studio.core.token_budget import TokenBudget
from ai_qa_test_studio.reporting.code_exporter import CodeExporter
from ai_qa_test_studio.reporting.excel_exporter import ExcelExporter
from ai_qa_test_studio.reporting.html_reporter import HtmlReporter
from ai_qa_test_studio.ui.formatting import count_unbound_steps
from ai_qa_test_studio.ui.icons import (
    drawer_collapse_icon,
    drawer_expand_icon,
    export_icon,
    new_case_icon,
    run_icon,
    stop_icon,
    sync_icon,
    wizard_icon,
)
from ai_qa_test_studio.ui.session_controller import SessionController
from ai_qa_test_studio.ui.widgets.agent_feed import AgentFeed
from ai_qa_test_studio.ui.widgets.data_grid import DataGrid
from ai_qa_test_studio.ui.widgets.embedded_browser import ScreencastViewport
from ai_qa_test_studio.ui.widgets.step_builder import StepBuilder
from ai_qa_test_studio.ui.widgets.test_explorer import TestExplorer
from ai_qa_test_studio.ui.wizards.ai_test_wizard import AiTestWizard


class MainWindow(QMainWindow):
    def __init__(self, data_root: Path | None = None) -> None:
        super().__init__()
        self.setWindowTitle("AI QA Test Studio v4.0")
        self.setAccessibleName("AI QA Test Studio")
        self.resize(1440, 900)
        self.data_root = data_root or Path("data")
        self.storage = ProjectStorage(self.data_root / "v4")
        self.env = EnvManager(self.data_root / "config")
        self.env.load_settings()
        self.project: Project | None = self.storage.load()
        self.session = SessionController()
        self._busy = False
        self._drawer_collapsed = False
        self._last_report: RunReport | None = None
        self._token_budget = TokenBudget()
        self._skills = ProgressiveSkillLoader(Path(DEFAULT_SKILLS_DIRNAME))
        self._run_logs = AgentFeed()
        self._build_chrome()
        if self.project is None:
            self.project = self._import_or_empty()
            self.storage.save(self.project)
        self._reload_ui()
        self._refresh_status_pills()

    def _build_chrome(self) -> None:
        toolbar = QToolBar("Thanh hành động")
        toolbar.setMovable(False)
        toolbar.setFloatable(False)
        toolbar.setAccessibleName("Thanh hành động trên cùng")
        self.addToolBar(toolbar)

        act_wizard = QAction(wizard_icon(), "AI Wizard", self)
        act_wizard.setToolTip("Mở AI Test Wizard")
        act_wizard.triggered.connect(self._open_wizard)
        toolbar.addAction(act_wizard)

        act_new = QAction(new_case_icon(), "New Case", self)
        act_new.setToolTip("Tạo Test Case mới")
        act_new.triggered.connect(self._new_case)
        toolbar.addAction(act_new)

        toolbar.addSeparator()

        url_host = QWidget()
        url_row = QHBoxLayout(url_host)
        url_row.setContentsMargins(4, 0, 4, 0)
        url_row.setSpacing(8)
        self.url_edit = QLineEdit()
        self.url_edit.setPlaceholderText(DEFAULT_TARGET_URL)
        self.url_edit.setText(DEFAULT_TARGET_URL)
        self.url_edit.setMinimumWidth(280)
        self.url_edit.setAccessibleName("Ô nhập Target URL")
        self.btn_sync = QPushButton("Sync URL")
        self.btn_sync.setIcon(sync_icon())
        self.btn_sync.setAccessibleName("Đồng bộ URL và bind selector")
        self.btn_sync.clicked.connect(self._sync_url)
        self.unbound_badge = QLabel("")
        self.unbound_badge.setObjectName("bannerWarn")
        self.unbound_badge.setVisible(False)
        url_row.addWidget(self.url_edit, stretch=1)
        url_row.addWidget(self.btn_sync)
        url_row.addWidget(self.unbound_badge)
        toolbar.addWidget(url_host)

        toolbar.addSeparator()

        self.skill_combo = QComboBox()
        self.skill_combo.setMinimumWidth(160)
        self.skill_combo.setAccessibleName("Chọn Agent Skill")
        skills = list(self._skills.registry.keys()) or [
            "web-e2e-functional",
            "boundary-value-analysis",
            "shift-left-figma-sync",
        ]
        self.skill_combo.addItems(skills)
        toolbar.addWidget(self.skill_combo)

        act_run = QAction(run_icon(), "Run", self)
        act_run.triggered.connect(self._run_current)
        toolbar.addAction(act_run)
        act_stop = QAction(stop_icon(), "Stop", self)
        act_stop.triggered.connect(self._stop_browser)
        toolbar.addAction(act_stop)

        export_trigger = QAction(export_icon(), "Export ▾", self)
        export_trigger.setToolTip("Xuất HTML / Excel / Pytest")
        export_trigger.triggered.connect(self._export_menu)
        toolbar.addAction(export_trigger)

        act_save = QAction("Save", self)
        act_save.setShortcut(QKeySequence.StandardKey.Save)
        act_save.triggered.connect(self._save_project)
        self.addAction(act_save)

        palette = QAction("Command Palette", self)
        palette.setShortcut(QKeySequence("Ctrl+K"))
        palette.triggered.connect(self._command_palette)
        self.addAction(palette)

        self.explorer = TestExplorer()
        self.explorer.case_selected.connect(self._on_case_selected)
        self.builder = StepBuilder()
        self.builder.case_changed.connect(self._on_case_fields_changed)
        self.viewport = ScreencastViewport()
        self.viewport.clicked_css.connect(self._on_viewport_click)
        self.viewport.launch_requested.connect(self._launch_browser)

        right = QWidget()
        right.setAccessibleName("Cột Browser và Agent")
        right_layout = QVBoxLayout(right)
        right_layout.setContentsMargins(8, 8, 8, 8)
        right_layout.setSpacing(8)
        right_layout.addWidget(self.viewport, stretch=2)
        right_layout.addWidget(self._run_logs, stretch=1)

        splitter = QSplitter()
        splitter.addWidget(self.explorer)
        splitter.addWidget(self.builder)
        splitter.addWidget(right)
        splitter.setStretchFactor(0, 22)
        splitter.setStretchFactor(1, 40)
        splitter.setStretchFactor(2, 38)
        splitter.setSizes([280, 520, 480])
        self.setCentralWidget(splitter)

        self.drawer = QDockWidget("Bottom Drawer", self)
        self.drawer.setAccessibleName("Bottom Drawer")
        self.drawer.setFeatures(
            QDockWidget.DockWidgetFeature.DockWidgetMovable
            | QDockWidget.DockWidgetFeature.DockWidgetClosable
        )
        drawer_body = QWidget()
        drawer_layout = QVBoxLayout(drawer_body)
        drawer_layout.setContentsMargins(8, 8, 8, 8)
        drawer_layout.setSpacing(8)

        header = QHBoxLayout()
        title = QLabel("Test Data")
        title.setObjectName("title")
        self.btn_drawer = QPushButton()
        self.btn_drawer.setIcon(drawer_collapse_icon())
        self.btn_drawer.setObjectName("ghost")
        self.btn_drawer.setToolTip("Thu gọn / mở rộng drawer")
        self.btn_drawer.setAccessibleName("Thu gọn Bottom Drawer")
        self.btn_drawer.clicked.connect(self._toggle_drawer)
        header.addWidget(title)
        header.addStretch(1)
        header.addWidget(self.btn_drawer)
        drawer_layout.addLayout(header)

        self.data_grid = DataGrid()
        self._drawer_content = self.data_grid
        drawer_layout.addWidget(self.data_grid)
        self.drawer.setWidget(drawer_body)
        self.addDockWidget(Qt.DockWidgetArea.BottomDockWidgetArea, self.drawer)

        status = QStatusBar()
        status.setAccessibleName("Thanh trạng thái")
        self._pill_mcp = self._make_pill("● MCP: Playwright (Ready)")
        self._pill_skills = self._make_pill(f"● Skills: {len(self._skills.registry)} Active")
        self._pill_tokens = self._make_pill(f"Token Budget: 0 / {TOKEN_CONTEXT_BUDGET}")
        for pill in (self._pill_mcp, self._pill_skills, self._pill_tokens):
            status.addPermanentWidget(pill)
        self._status = status
        self._status.showMessage("Sẵn sàng")
        self.setStatusBar(self._status)

    def _make_pill(self, text: str) -> QFrame:
        frame = QFrame()
        frame.setObjectName("statusPill")
        lay = QHBoxLayout(frame)
        lay.setContentsMargins(8, 2, 8, 2)
        label = QLabel(text)
        label.setObjectName("muted")
        lay.addWidget(label)
        frame.setSizePolicy(QSizePolicy.Policy.Minimum, QSizePolicy.Policy.Preferred)
        return frame

    def _set_pill(self, frame: QWidget, text: str) -> None:
        label = frame.findChild(QLabel)
        if label is not None:
            label.setText(text)

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
        self._refresh_status_pills()

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
        if case.start_url:
            self.url_edit.setText(case.start_url)
        if case.test_data_rows:
            self.data_grid.load_rows(case.test_data_rows)
        else:
            self.data_grid.load_rows([])
        self._update_unbound_badge(case)
        self._status.showMessage(f"Case {case.code} · {len(case.steps)} bước")

    def _update_unbound_badge(self, case: TestCase | None) -> None:
        if case is None:
            self.unbound_badge.setVisible(False)
            return
        unbound = count_unbound_steps(case)
        if unbound > 0:
            self.unbound_badge.setText(f"{unbound} UNBOUND")
            self.unbound_badge.setVisible(True)
        else:
            self.unbound_badge.setVisible(False)

    def _on_case_fields_changed(self) -> None:
        self._update_unbound_badge(self.builder.peek_case())

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
                f"Wizard đã thêm {len(scenarios)} scenario, "
                f"{sum(len(s.cases) for s in scenarios)} case"
            )

    def _new_case(self) -> None:
        if self.project is None:
            return
        title, ok = QInputDialog.getText(self, "New Case", "Tiêu đề Test Case:")
        if not ok or not title.strip():
            return
        code = f"TC_{uuid.uuid4().hex[:6].upper()}"
        case = TestCase(
            id=str(uuid.uuid4()),
            code=code,
            title=title.strip(),
            steps=[
                TestStep(
                    step_no=1,
                    action="goto",
                    intent_hint="Mở trang đích",
                    input_data=self.url_edit.text().strip() or DEFAULT_TARGET_URL,
                    binding_status="UNBOUND",
                )
            ],
            start_url=self.url_edit.text().strip(),
        )
        group = self.project.root_group
        if group.scenarios:
            group.scenarios[0].cases.append(case)
        else:
            group.scenarios.append(
                TestScenario(
                    id=str(uuid.uuid4()),
                    code=f"TS_{code}",
                    title="Scenario mặc định",
                    cases=[case],
                )
            )
        self.storage.save(self.project)
        self._reload_ui()
        self._load_case(case)
        self._append_log(f"Đã tạo {case.code}")

    def _save_project(self) -> None:
        if self.project is None:
            return
        case = self.builder.current_case()
        if case is not None:
            self._merge_case(case)
        self.storage.save(self.project)
        self._append_log(f"Đã lưu {self.storage.project_path}")
        self._status.showMessage("Đã lưu")

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

    def _toggle_drawer(self) -> None:
        self._drawer_collapsed = not self._drawer_collapsed
        body = self.drawer.widget()
        if body is None:
            return
        self.data_grid.setVisible(not self._drawer_collapsed)
        if self._drawer_collapsed:
            self.btn_drawer.setIcon(drawer_expand_icon())
            self.drawer.setMaximumHeight(UI_DRAWER_COLLAPSED_HEIGHT + 48)
        else:
            self.btn_drawer.setIcon(drawer_collapse_icon())
            self.drawer.setMaximumHeight(16777215)

    @asyncSlot()
    async def _launch_browser(self) -> None:
        if self._busy:
            return
        self._busy = True
        try:
            url = self.url_edit.text().strip() or DEFAULT_TARGET_URL
            await self.session.launch_url(url, log=self._append_log)
            await self._refresh_viewport()
            self._set_pill(self._pill_mcp, "● MCP: Playwright (Ready)")
        except Exception as exc:  # noqa: BLE001
            self._append_log(f"Không mở được trình duyệt: {exc}")
            QMessageBox.warning(self, "Launch Browser", str(exc))
        finally:
            self._busy = False

    @asyncSlot()
    async def _sync_url(self) -> None:
        if self._busy or self.project is None:
            return
        case = self._require_case()
        if case is None:
            return
        url = self.url_edit.text().strip()
        if not url:
            url, ok = QInputDialog.getText(
                self, "Sync & Bind URL", "Base URL:", text=DEFAULT_TARGET_URL
            )
            if not ok or not url.strip():
                return
            url = url.strip()
            self.url_edit.setText(url)
        self._busy = True
        self._status.showMessage("Đang đồng bộ…")
        try:
            case.start_url = url
            updated = await self.session.sync_bind(
                self.project, case, url, log=self._append_log
            )
            self.builder.load_case(updated)
            self._merge_case(updated)
            self.storage.save(self.project)
            self.explorer.load_project(self.project)
            self._update_unbound_badge(updated)
            await self._refresh_viewport()
            endpoint = self.session.engine.cdp_http_for_mcp() if self.session.started else "n/a"
            self._set_pill(self._pill_mcp, f"● MCP: Playwright ({endpoint})")
        except Exception as exc:  # noqa: BLE001
            self._append_log(f"Sync thất bại: {exc}")
            QMessageBox.warning(self, "Sync URL", str(exc))
        finally:
            self._busy = False
            self._status.showMessage("Sẵn sàng")

    @asyncSlot()
    async def _run_current(self) -> None:
        if self._busy or self.project is None:
            return
        case = self._require_case()
        if case is None:
            return
        self._busy = True
        self._status.showMessage("Đang chạy…")
        self._run_logs.clear()
        try:
            skill = self.skill_combo.currentText()
            report = await self.session.run_case(
                self.project,
                case,
                log=self._append_log,
                on_step=self._run_logs.append_step,
                skill_name=skill,
                skills_dir=Path(DEFAULT_SKILLS_DIRNAME),
            )
            self._last_report = report
            self.builder.load_case(case)
            self._merge_case(case)
            self.storage.save(self.project)
            self.explorer.load_project(self.project)
            await self._refresh_viewport()
            out = self.data_root / "exports" / f"{report.run_id}.html"
            HtmlReporter().write(report, out)
            self._append_log(f"Báo cáo HTML: {out}")
            self._set_pill(self._pill_mcp, f"● MCP: Playwright ({report.cases[0].status})")
            used = self._token_budget.count(self._run_logs.to_plain_text())
            self._set_pill(
                self._pill_tokens, f"Token Budget: {used / 1000:.1f}k / {TOKEN_CONTEXT_BUDGET / 1000:.0f}k"
            )
        except Exception as exc:  # noqa: BLE001
            self._append_log(f"Chạy thất bại: {exc}")
            QMessageBox.warning(self, "Run", str(exc))
        finally:
            self._busy = False
            self._status.showMessage("Sẵn sàng")

    @asyncSlot()
    async def _stop_browser(self) -> None:
        try:
            await self.session.stop(log=self._append_log)
            self.viewport.set_placeholder("")
            self._set_pill(self._pill_mcp, "● MCP: Playwright (Stopped)")
        except Exception as exc:  # noqa: BLE001
            self._append_log(f"Stop thất bại: {exc}")

    @asyncSlot()
    async def _on_viewport_click(self, css_x: float, css_y: float) -> None:
        if not self.session.started or self.project is None:
            self._append_log("Hãy mở trình duyệt trước khi Inspect.")
            return
        if not self.viewport.inspect_mode:
            return
        try:
            meta = await element_from_point(self.session.engine.page, css_x, css_y)
            if not meta:
                self._append_log("Không tìm thấy phần tử tại điểm click.")
                return
            case = self.builder.current_case()
            case_code = case.code if case else "CASE"
            el = self.session.pin_shared_element(self.project, meta, case_code=case_code)
            bound = self.builder.bind_element_to_selected_step(el.element_id)
            if case is not None:
                self._merge_case(case)
            self.storage.save(self.project)
            self.explorer.load_project(self.project)
            msg = f"Đã ghim {el.element_id} — {el.friendly_name}"
            if bound:
                msg += " và gắn vào bước đang chọn"
            self._append_log(msg)
            await self._refresh_viewport()
        except Exception as exc:  # noqa: BLE001
            self._append_log(f"Inspect thất bại: {exc}")

    async def _refresh_viewport(self) -> None:
        if not self.session.started:
            return
        png, w, h = await self.session.capture_png()
        self.viewport.set_frame_png(png, w, h)

    def _export_menu(self) -> None:
        choice, ok = QInputDialog.getItem(
            self,
            "Export",
            "Định dạng:",
            ["HTML Report", "Excel IEEE 829", "Pytest"],
            0,
            False,
        )
        if not ok:
            return
        mapping = {
            "HTML Report": "html",
            "Excel IEEE 829": "excel",
            "Pytest": "pytest",
        }
        self._export(mapping[choice])

    def _export(self, kind: str) -> None:
        if self.project is None:
            return
        exports = self.data_root / "exports"
        exports.mkdir(parents=True, exist_ok=True)
        try:
            if kind == "html":
                if self._last_report is None:
                    QMessageBox.information(self, "Export", "Chạy test trước để có báo cáo HTML.")
                    return
                path = exports / f"{self._last_report.run_id}.html"
                HtmlReporter().write(self._last_report, path)
            elif kind == "excel":
                path = exports / f"{self.project.name.replace(' ', '_')}_ieee829.xlsx"
                ExcelExporter().write(self.project, self._last_report, path)
            else:
                path = exports / "pytest_suite"
                CodeExporter().export(self.project, path)
            self._append_log(f"Đã xuất: {path}")
            QMessageBox.information(self, "Export", f"Đã xuất:\n{path}")
        except Exception as exc:  # noqa: BLE001
            QMessageBox.warning(self, "Export", str(exc))

    def _command_palette(self) -> None:
        choice, ok = QInputDialog.getItem(
            self,
            "Command Palette",
            "Hành động:",
            [
                "AI Wizard",
                "New Case",
                "Sync URL",
                "Run",
                "Stop",
                "Launch Browser",
                "Export",
                "Save",
            ],
            0,
            False,
        )
        if not ok:
            return
        mapping = {
            "AI Wizard": self._open_wizard,
            "New Case": self._new_case,
            "Sync URL": self._sync_url,
            "Run": self._run_current,
            "Stop": self._stop_browser,
            "Launch Browser": self._launch_browser,
            "Export": self._export_menu,
            "Save": self._save_project,
        }
        mapping[choice]()

    def _append_log(self, message: str) -> None:
        self._run_logs.append_message(message)
        used = self._token_budget.count(self._run_logs.to_plain_text())
        self._set_pill(
            self._pill_tokens,
            f"Token Budget: {used / 1000:.1f}k / {TOKEN_CONTEXT_BUDGET / 1000:.0f}k",
        )

    def _refresh_status_pills(self) -> None:
        active = len(self._skills.registry) or self.skill_combo.count()
        self._set_pill(self._pill_skills, f"● Skills: {active} Active")
        state = "Ready" if not self.session.started else "Connected"
        self._set_pill(self._pill_mcp, f"● MCP: Playwright ({state})")

    def closeEvent(self, event) -> None:  # type: ignore[no-untyped-def]
        if self.session.started:
            self._append_log("Đang đóng — trình duyệt sẽ dừng ở phiên tiếp theo")
        super().closeEvent(event)
