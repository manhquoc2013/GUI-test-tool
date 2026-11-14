"""QMainWindow shell — Fluent panes inside standard window (no FramelessWindow)."""

from __future__ import annotations

import asyncio
import uuid
from pathlib import Path
from typing import Any

from PySide6.QtCore import QEvent, Qt
from PySide6.QtGui import QAction, QCloseEvent, QKeySequence, QResizeEvent, QShowEvent
from PySide6.QtWidgets import (
    QHBoxLayout,
    QInputDialog,
    QMainWindow,
    QSplitter,
    QVBoxLayout,
    QWidget,
)
from qasync import asyncSlot
from qfluentwidgets import (
    CaptionLabel,
    IndeterminateProgressBar,
    InfoBadge,
    InfoBar,
    InfoBarPosition,
    Theme,
    isDarkTheme,
)

from ai_qa_test_studio.agent.bundled_mcp import BundledMcpRegistry
from ai_qa_test_studio.agent.orchestrator import ClarifyRequest, ClarifyResponse
from ai_qa_test_studio.agent.skill_loader import ProgressiveSkillLoader
from ai_qa_test_studio.browser.dom_distiller import DomDistiller
from ai_qa_test_studio.constants import (
    DEFAULT_SKILLS_DIRNAME,
    DEFAULT_TARGET_URL,
    THEME_PREF_DARK,
    THEME_PREF_LIGHT,
    TOKEN_CONTEXT_BUDGET,
    UI_BREAKPOINT_WIDE,
    UI_DRAWER_WIDTH,
    UI_INFOBAR_DURATION_MS,
    UI_SPLIT_SIZES_H,
    UI_WINDOW_DEFAULT_HEIGHT,
    UI_WINDOW_DEFAULT_WIDTH,
    UI_WINDOW_MIN_HEIGHT,
    UI_WINDOW_MIN_WIDTH,
)
from ai_qa_test_studio.core.chat_history import ChatHistoryStore
from ai_qa_test_studio.core.env_manager import EnvManager
from ai_qa_test_studio.core.java_importer import JavaProjectImporter
from ai_qa_test_studio.core.models import Project, RunReport, TestCase, TestScenario, TestStep
from ai_qa_test_studio.core.storage import ProjectStorage
from ai_qa_test_studio.core.token_budget import TokenBudget
from ai_qa_test_studio.ui.controllers import (
    CopilotController,
    ExportController,
    LayoutController,
    RunController,
)
from ai_qa_test_studio.ui.formatting import count_unbound_steps
from ai_qa_test_studio.ui.qt_compat import bind_accessible_root
from ai_qa_test_studio.ui.session_controller import SessionController
from ai_qa_test_studio.ui.theme import apply_studio_theme, studio_shell_qss, toggle_resolved_theme
from ai_qa_test_studio.ui.widgets.clarify_dialog import ClarifyDialog
from ai_qa_test_studio.ui.widgets.command_palette import CommandPalette, PaletteCommand
from ai_qa_test_studio.ui.widgets.editor_pane import EditorPane
from ai_qa_test_studio.ui.widgets.explorer_pane import ExplorerPane
from ai_qa_test_studio.ui.widgets.right_drawer import RightDrawer
from ai_qa_test_studio.ui.widgets.system_settings_dialog import SystemSettingsDialog
from ai_qa_test_studio.ui.widgets.top_toolbar import UnifiedTopBar


class MainWindow(QMainWindow):
    def __init__(self, data_root: Path | None = None) -> None:
        super().__init__()
        self.setWindowTitle("AI QA Test Studio v4.2")
        self.setAccessibleName("AI QA Test Studio")
        self.setMinimumSize(UI_WINDOW_MIN_WIDTH, UI_WINDOW_MIN_HEIGHT)
        self.resize(UI_WINDOW_DEFAULT_WIDTH, UI_WINDOW_DEFAULT_HEIGHT)
        self.data_root = data_root or Path("data")
        self.storage = ProjectStorage(self.data_root / "v4")
        self.env = EnvManager(self.data_root / "config")
        self.env.load_settings()
        self.project: Project | None = self.storage.load()
        self.session = SessionController()
        self._busy = False
        self._last_report: RunReport | None = None
        self._last_error: str = ""
        self._token_budget = TokenBudget()
        self._skills = ProgressiveSkillLoader(Path(DEFAULT_SKILLS_DIRNAME))
        self._chat_store = ChatHistoryStore(self.data_root)
        self._copilot_cancel: asyncio.Event | None = None
        self._clarify_dialog: ClarifyDialog | None = None
        self._dom_distiller = DomDistiller(self._token_budget)
        self._bundled_mcp = BundledMcpRegistry()
        self._bundled_mcp.start()
        self._sidebar_collapsed = False
        self._prefer_dock = True
        self._drawer_width = UI_DRAWER_WIDTH
        self._narrow_auto_collapsed = False
        self._shutdown_started = False
        self._shutdown_ready = False
        self._layout = LayoutController(self)
        self._run = RunController(self)
        self._copilot_ctl = CopilotController(self)
        self._export_ctl = ExportController(self)
        self._build_chrome()
        if self.project is None:
            self.project = self._import_or_empty()
            self.storage.save(self.project)
        self._reload_ui()
        self._refresh_model_catalog()
        self._refresh_status_pills()
        self.apply_layout_breakpoint(self.width())

    def _build_chrome(self) -> None:
        central = QWidget(self)
        root_layout = QVBoxLayout(central)
        root_layout.setContentsMargins(0, 0, 0, 0)
        root_layout.setSpacing(0)

        self.top_bar = UnifiedTopBar(central)
        skills = list(self._skills.registry.keys()) or [
            "web-e2e-functional",
            "boundary-value-analysis",
            "shift-left-figma-sync",
        ]
        self.top_bar.set_skills(skills)
        self.top_bar.sidebar_toggled.connect(self._toggle_sidebar)
        self.top_bar.wizard_clicked.connect(self._open_copilot)
        self.top_bar.new_case_clicked.connect(self._new_case)
        self.top_bar.sync_url_clicked.connect(lambda _url: self._sync_url())
        self.top_bar.run_clicked.connect(self._run_current)
        self.top_bar.stop_clicked.connect(self._stop_browser)
        self.top_bar.copilot_toggled.connect(self._on_copilot_toggle)
        self.top_bar.export_requested.connect(self._export)
        self.top_bar.settings_requested.connect(self._open_system_settings)
        self.top_bar.theme_toggle_requested.connect(self._toggle_theme)
        root_layout.addWidget(self.top_bar)

        self.busy_bar = IndeterminateProgressBar(central)
        self.busy_bar.setFixedHeight(2)
        self.busy_bar.hide()
        root_layout.addWidget(self.busy_bar)

        self.url_edit = self.top_bar.url_input
        self.skill_combo = self.top_bar.skill_combo
        self.unbound_badge = self.top_bar.unbound_badge

        self.workspace = QWidget(central)
        self.workspace.setAccessibleName("Workspace 3-column")
        workspace_layout = QHBoxLayout(self.workspace)
        workspace_layout.setContentsMargins(0, 0, 0, 0)
        workspace_layout.setSpacing(0)
        self.h_splitter = QSplitter(Qt.Orientation.Horizontal, self.workspace)

        self.explorer = ExplorerPane(self.h_splitter)
        self.explorer.installEventFilter(self)
        self.explorer.case_selected.connect(self._on_case_selected)
        self.builder = EditorPane(self.h_splitter)
        self.builder.case_changed.connect(self._on_case_fields_changed)
        self.builder.sync_requested.connect(self._sync_url)
        self.builder.breadcrumb_navigate.connect(self._on_breadcrumb_navigate)
        self.right_drawer = RightDrawer(self.h_splitter)
        self.browser_agent = self.right_drawer.browser
        self.viewport = self.browser_agent
        self._run_logs = self.browser_agent.agent_feed
        self.copilot = self.right_drawer.copilot
        self.browser_agent.launch_browser_clicked.connect(self._launch_browser)
        self.browser_agent.clicked_css.connect(self._on_viewport_click)
        self.copilot.prompt_submitted.connect(self._on_copilot_prompt)
        self.copilot.stop_requested.connect(self._stop_copilot)
        self.copilot.apply_requested.connect(self._apply_copilot_payload)
        self.copilot.info_message.connect(lambda title, body: self._toast_info(title, body))
        self.copilot.session_changed.connect(self._persist_copilot_session)
        self.copilot.settings_requested.connect(self._open_system_settings)
        self.right_drawer.drawer_opened.connect(lambda: self.top_bar.set_copilot_checked(True))
        self.right_drawer.drawer_closed.connect(lambda: self.top_bar.set_copilot_checked(False))

        self.data_drawer = self.builder.data_drawer
        self.data_grid = self.data_drawer.table
        self.data_drawer.add_requested.connect(self._add_test_data_row)
        self.data_drawer.rows_changed.connect(self._on_test_data_rows_changed)

        self.h_splitter.addWidget(self.explorer)
        self.h_splitter.addWidget(self.builder)
        self.h_splitter.addWidget(self.right_drawer)
        self.h_splitter.setChildrenCollapsible(False)
        self.h_splitter.setOpaqueResize(True)
        self.h_splitter.setStretchFactor(0, 0)
        self.h_splitter.setStretchFactor(1, 1)
        self.h_splitter.setStretchFactor(2, 0)
        self.h_splitter.setSizes(list(UI_SPLIT_SIZES_H))
        self._layout.connect_splitter()
        workspace_layout.addWidget(self.h_splitter, stretch=1)

        root_layout.addWidget(self.workspace, stretch=1)
        self.setCentralWidget(central)

        # Default: Copilot dock open on wide launches (M1).
        if self.width() >= UI_BREAKPOINT_WIDE:
            self.right_drawer.open_drawer()
            self.top_bar.set_copilot_checked(True)
        else:
            self.right_drawer.close_drawer()
            self.top_bar.set_copilot_checked(False)

        act_save = QAction("Save", self)
        act_save.setShortcut(QKeySequence.StandardKey.Save)
        act_save.triggered.connect(self._save_project)
        self.addAction(act_save)

        palette = QAction("Command Palette", self)
        palette.setShortcut(QKeySequence("Ctrl+K"))
        palette.triggered.connect(self._command_palette)
        self.addAction(palette)

        act_sidebar = QAction("Toggle Sidebar", self)
        act_sidebar.setShortcut(QKeySequence("Ctrl+\\"))
        act_sidebar.triggered.connect(self._toggle_sidebar)
        self.addAction(act_sidebar)

        act_copilot = QAction("Toggle Copilot", self)
        act_copilot.setShortcut(QKeySequence("Ctrl+I"))
        act_copilot.triggered.connect(self._toggle_copilot_drawer)
        self.addAction(act_copilot)

        act_esc = QAction("Close drawer / Stop", self)
        act_esc.setShortcut(QKeySequence(Qt.Key.Key_Escape))
        act_esc.triggered.connect(self._on_escape)
        self.addAction(act_esc)

        act_run = QAction("Run", self)
        act_run.setShortcut(QKeySequence("Ctrl+Return"))
        act_run.triggered.connect(self._run_current)
        self.addAction(act_run)

        status = self.statusBar()
        status.setAccessibleName("Thanh trạng thái")
        self._status_caption = CaptionLabel("Sẵn sàng", self)
        status.addWidget(self._status_caption, stretch=1)
        self._pill_mcp = InfoBadge.success("MCP: bundled ready", self)
        self._pill_mcp.setAccessibleName("Trạng thái MCP")
        self._pill_skills = InfoBadge.info("Skills: 0 Active", self)
        self._pill_skills.setAccessibleName("Số Skills đang active")
        self._pill_tokens = InfoBadge.attension(f"Tokens: 0 / {TOKEN_CONTEXT_BUDGET:,}", self)
        self._pill_tokens.setAccessibleName("Token budget")
        for pill in (self._pill_mcp, self._pill_skills, self._pill_tokens):
            status.addPermanentWidget(pill)
        self._status = status

    @property
    def _drawer_collapsed(self) -> bool:
        return self.data_drawer.collapsed

    def apply_layout_breakpoint(self, width: int) -> None:
        return self._layout.apply_layout_breakpoint(width)

    def eventFilter(self, watched: object, event: QEvent) -> bool:  # type: ignore[override]
        self._layout.handle_resize_filter(watched, event)
        return super().eventFilter(watched, event)  # type: ignore[arg-type]

    def _sync_drawer_geometry(self) -> None:
        self._layout.sync_drawer_geometry()

    def _toggle_sidebar(self) -> None:
        self._layout.toggle_sidebar()

    def _toggle_copilot_drawer(self) -> None:
        self._layout.toggle_copilot_drawer()

    def _on_copilot_toggle(self, checked: bool) -> None:
        self._layout.on_copilot_toggle(checked)

    def _on_escape(self) -> None:
        self._layout.on_escape()

    def _set_busy_chrome(self, busy: bool) -> None:
        self._layout.set_busy_chrome(busy)

    def resizeEvent(self, event: QResizeEvent) -> None:
        super().resizeEvent(event)
        self.apply_layout_breakpoint(self.width())

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
        self._status_caption.setText(
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

    def _case_path(self, case: TestCase) -> tuple[str, str]:
        if self.project is None:
            return "", ""
        for group in [self.project.root_group, *self.project.root_group.children]:
            for scenario in group.scenarios:
                if any(c.id == case.id for c in scenario.cases):
                    return group.name, scenario.title or scenario.code
            for suite in group.suites:
                if any(c.id == case.id for c in suite.cases):
                    return group.name, suite.name
        return self.project.root_group.name, ""

    def _load_case(self, case: TestCase) -> None:
        module, suite = self._case_path(case)
        self.builder.load_case(case, module=module, suite=suite)
        if case.start_url:
            self.url_edit.setText(case.start_url)
        if case.test_data_rows:
            self.data_drawer.load_rows(case.test_data_rows)
        else:
            self.data_drawer.load_rows([])
        self._update_unbound_badge(case)
        self._status_caption.setText(f"Case {case.code} · {len(case.steps)} bước")

    def _on_breadcrumb_navigate(self, module: str, suite: str) -> None:
        self.explorer.focus_path(module, suite)

    def _update_unbound_badge(self, case: TestCase | None) -> None:
        if case is None:
            self.top_bar.set_unbound_count(0)
            return
        self.top_bar.set_unbound_count(count_unbound_steps(case))

    def _on_case_fields_changed(self) -> None:
        self._update_unbound_badge(self.builder.peek_case())

    def _add_test_data_row(self) -> None:
        if self.project is None or self.builder.current_case() is None:
            self._toast_warn("Test Data", "Chọn một test case trước khi thêm bộ dữ liệu.")
            return
        self.data_drawer.open_add_dialog()

    def _on_test_data_rows_changed(self, rows: list[dict[str, Any]]) -> None:
        if self.project is None:
            return
        case = self.builder.current_case()
        if case is None:
            return
        case.test_data_rows = list(rows)
        self._merge_case(case)
        self.storage.save(self.project)
        self._status_caption.setText(f"Case {case.code} · {len(case.test_data_rows)} bộ Test Data")

    def _open_copilot(self) -> None:
        self._layout.open_copilot()

    def _toast_info(self, title: str, content: str) -> None:
        InfoBar.success(
            title=title,
            content=content,
            orient=Qt.Orientation.Horizontal,
            isClosable=True,
            position=InfoBarPosition.TOP,
            duration=UI_INFOBAR_DURATION_MS,
            parent=self,
        )

    def _persist_copilot_session(self) -> None:
        self._copilot_ctl.persist_session()

    def _stop_copilot(self) -> None:
        self._copilot_ctl.stop()

    def _refresh_model_catalog(self) -> None:
        self._copilot_ctl.refresh_model_catalog()

    async def _clarify_from_ui(self, request: ClarifyRequest) -> ClarifyResponse:
        return await self._copilot_ctl.clarify_from_ui(request)

    def _open_system_settings(self) -> None:
        dialog = SystemSettingsDialog(self.env.settings, self)
        if dialog.exec():
            result = dialog.result_settings()
            if result is not None:
                self.env._settings = result
                self.env.save_settings()
                self._apply_theme_preference(result.theme)
                self._refresh_model_catalog()
                self._refresh_status_pills()
                self._toast_info("Settings", "Đã lưu cấu hình hệ thống.")

    def _toggle_theme(self) -> None:
        next_theme = toggle_resolved_theme()
        preference = THEME_PREF_LIGHT if next_theme == Theme.LIGHT else THEME_PREF_DARK
        self.env.settings.theme = preference
        self.env.save_settings()
        self._refresh_app_stylesheet()
        tip = "Light" if not isDarkTheme() else "Dark"
        self._toast_info("Theme", f"Đã chuyển sang giao diện {tip}.")

    def _apply_theme_preference(self, preference: str) -> None:
        from PySide6.QtWidgets import QApplication

        qapp = QApplication.instance()
        if isinstance(qapp, QApplication):
            apply_studio_theme(qapp, preference=preference)

    def _refresh_app_stylesheet(self) -> None:
        from PySide6.QtWidgets import QApplication

        qapp = QApplication.instance()
        if isinstance(qapp, QApplication):
            qapp.setStyleSheet(studio_shell_qss())

    @asyncSlot(str, dict)
    async def _on_copilot_prompt(self, text: str, flags: dict[str, Any]) -> None:
        await self._copilot_ctl.on_prompt(text, flags)

    async def _build_copilot_context(self, flags: dict[str, Any]) -> dict[str, Any]:
        return await self._copilot_ctl.build_context(flags)

    def _apply_copilot_payload(self, payload: dict[str, Any]) -> None:
        self._copilot_ctl.apply_payload(payload)

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
        self._status_caption.setText("Đã lưu")

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
            self._toast_warn("Case", "Chọn một test case trên Explorer trước.")
            return None
        return case

    def _toggle_drawer(self) -> None:
        self.data_drawer.toggle_collapse()

    def _toast_error(self, title: str, content: str) -> None:
        """Non-modal error — avoid QMessageBox inside asyncSlot (qasync/Playwright clash)."""
        self._last_error = content
        InfoBar.error(
            title=title,
            content=content,
            orient=Qt.Orientation.Horizontal,
            isClosable=True,
            position=InfoBarPosition.TOP,
            duration=UI_INFOBAR_DURATION_MS,
            parent=self,
        )

    def _toast_warn(self, title: str, content: str) -> None:
        InfoBar.warning(
            title=title,
            content=content,
            orient=Qt.Orientation.Horizontal,
            isClosable=True,
            position=InfoBarPosition.TOP,
            duration=UI_INFOBAR_DURATION_MS,
            parent=self,
        )

    @asyncSlot()
    async def _launch_browser(self) -> None:
        await self._run.launch_browser()

    @asyncSlot()
    async def _sync_url(self) -> None:
        """Sync bind — empty URL uses DEFAULT_TARGET_URL; toast-only feedback."""
        await self._run.sync_url()

    @asyncSlot()
    async def _run_current(self) -> None:
        await self._run.run_current()

    def _request_stop_browser(self) -> None:
        self.session.request_cancel()
        asyncio.get_running_loop().create_task(self._run.stop_browser())

    @asyncSlot()
    async def _stop_browser(self) -> None:
        await self._run.stop_browser()

    @asyncSlot()
    async def _on_viewport_click(self, css_x: float, css_y: float) -> None:
        await self._run.on_viewport_click(css_x, css_y)

    async def _refresh_viewport(self) -> None:
        await self._run.refresh_viewport()

    def _export(self, kind: str) -> None:
        self._export_ctl.export(kind)

    def _palette_commands(self) -> list[PaletteCommand]:
        return [
            PaletteCommand(
                "copilot",
                "AI Copilot",
                "Mở Copilot Compose (Planner→Composer→Critic)",
                ("ai", "copilot", "wizard", "srs", "figma"),
                self._open_copilot,
            ),
            PaletteCommand(
                "new_case",
                "New Case",
                "Thêm test case mới vào scenario hiện tại",
                ("new", "case", "add"),
                self._new_case,
            ),
            PaletteCommand(
                "sync",
                "Sync URL",
                "Ánh xạ bước UNBOUND sang selector thật",
                ("sync", "bind", "url"),
                self._sync_url,
            ),
            PaletteCommand(
                "run",
                "Run",
                "Chạy test case đang chọn",
                ("run", "play", "execute"),
                self._run_current,
            ),
            PaletteCommand(
                "stop",
                "Stop",
                "Dừng trình duyệt / phiên chạy",
                ("stop", "cancel"),
                self._stop_browser,
            ),
            PaletteCommand(
                "launch",
                "Launch Browser",
                "Mở Chromium + CDP screencast",
                ("launch", "browser", "cdp"),
                self._launch_browser,
            ),
            PaletteCommand(
                "export_html",
                "Export HTML Report",
                "Xuất báo cáo HTML Visual Replay",
                ("export", "html", "report"),
                lambda: self._export("html"),
            ),
            PaletteCommand(
                "export_excel",
                "Export Excel IEEE 829",
                "Xuất Excel IEEE 829 + RTM",
                ("export", "excel", "ieee"),
                lambda: self._export("excel"),
            ),
            PaletteCommand(
                "export_pytest",
                "Export Pytest Project",
                "Xuất bộ pytest + scaffold CI",
                ("export", "pytest", "code"),
                lambda: self._export("pytest"),
            ),
            PaletteCommand(
                "save",
                "Save Project",
                "Lưu project.v4.json",
                ("save", "persist"),
                self._save_project,
            ),
            PaletteCommand(
                "settings",
                "System Settings",
                "Cấu hình hệ thống, AI API, models, routing, MCP",
                ("settings", "config", "ai", "model", "api"),
                self._open_system_settings,
            ),
        ]

    def _command_palette(self) -> None:
        palette = CommandPalette(self._palette_commands(), self)
        if palette.exec():
            chosen = palette.chosen()
            if chosen is not None:
                chosen.callback()

    def _append_log(self, message: str) -> None:
        self._run_logs.append_message(message)
        used = self._token_budget.count(self._run_logs.to_plain_text())
        self._set_pill(
            self._pill_tokens,
            f"Tokens: {used / 1000:.1f}k / {TOKEN_CONTEXT_BUDGET / 1000:.0f}k",
        )

    def _set_pill(self, badge: InfoBadge, text: str) -> None:
        badge.setText(text)

    def _refresh_status_pills(self) -> None:
        active = len(self._skills.registry) or self.skill_combo.count()
        self._set_pill(self._pill_skills, f"Skills: {active} Active")
        bundled = "ready" if self._bundled_mcp.ready else "off"
        ext = "on" if self.env.settings.mcp.external.playwright else "off"
        self._set_pill(self._pill_mcp, f"MCP: bundled {bundled} · PW {ext}")

    def showEvent(self, event: QShowEvent) -> None:
        super().showEvent(event)
        bind_accessible_root(self)
        self._sync_drawer_geometry()

    def closeEvent(self, event: QCloseEvent) -> None:
        if self._shutdown_ready:
            super().closeEvent(event)
            return
        event.ignore()
        if self._shutdown_started:
            return
        self._shutdown_started = True
        try:
            loop = asyncio.get_running_loop()
        except RuntimeError:
            self._bundled_mcp.stop()
            self._shutdown_ready = True
            self.close()
            return
        loop.create_task(self._async_shutdown())

    async def _async_shutdown(self) -> None:
        self._bundled_mcp.stop()
        if self._copilot_cancel is not None:
            self._copilot_cancel.set()
        dialog = self._clarify_dialog
        if dialog is not None:
            dialog.abort_stopped()
            self._clarify_dialog = None
        if self.session.started or self.session.run_in_progress:
            self.session.request_cancel()
            self._append_log("Đang đóng — dừng trình duyệt…")
            try:
                await self.session.stop(log=self._append_log)
            except Exception as exc:  # noqa: BLE001
                self._append_log(f"Stop khi đóng thất bại: {exc}")
        self._shutdown_ready = True
        self.close()
