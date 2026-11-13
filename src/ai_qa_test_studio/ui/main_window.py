"""QMainWindow shell — Fluent panes inside standard window (no FramelessWindow)."""

from __future__ import annotations

import asyncio
import uuid
from pathlib import Path
from typing import Any

from PySide6.QtCore import Qt
from PySide6.QtGui import QAction, QKeySequence
from PySide6.QtWidgets import (
    QInputDialog,
    QMainWindow,
    QMessageBox,
    QSplitter,
    QVBoxLayout,
    QWidget,
)
from qasync import asyncSlot
from qfluentwidgets import CaptionLabel, InfoBadge, InfoBar, InfoBarPosition

from ai_qa_test_studio.agent.llm_gateway import ChatMessage, UnifiedLLMGateway
from ai_qa_test_studio.agent.orchestrator import CopilotOrchestrator
from ai_qa_test_studio.agent.skill_loader import ProgressiveSkillLoader
from ai_qa_test_studio.browser.dom_distiller import DomDistiller
from ai_qa_test_studio.browser.visual_inspector import element_from_point
from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    APPLY_KIND_TEST_DATA,
    COPILOT_MODE_COMPOSE,
    DEFAULT_SKILLS_DIRNAME,
    DEFAULT_TARGET_URL,
    FALLBACK_BLANK_URL,
    ROLE_COMPOSER,
    SCREENCAST_STATUS_NAV_FAILED,
    SCREENCAST_STATUS_WAITING,
    TOKEN_CONTEXT_BUDGET,
    UI_INFOBAR_DURATION_MS,
    UI_SPLIT_SIZES_H,
    UI_SPLIT_SIZES_V,
)
from ai_qa_test_studio.core.chat_history import ChatHistoryStore
from ai_qa_test_studio.core.env_manager import EnvManager
from ai_qa_test_studio.core.java_importer import JavaProjectImporter
from ai_qa_test_studio.core.models import Project, RunReport, TestCase, TestScenario, TestStep
from ai_qa_test_studio.core.storage import ProjectStorage
from ai_qa_test_studio.core.token_budget import TokenBudget
from ai_qa_test_studio.reporting.code_exporter import CodeExporter
from ai_qa_test_studio.reporting.excel_exporter import ExcelExporter
from ai_qa_test_studio.reporting.html_reporter import HtmlReporter
from ai_qa_test_studio.ui.formatting import count_unbound_steps
from ai_qa_test_studio.ui.qt_compat import bind_accessible_root
from ai_qa_test_studio.ui.session_controller import SessionController
from ai_qa_test_studio.ui.widgets.browser_agent_pane import BrowserAgentPane
from ai_qa_test_studio.ui.widgets.command_palette import CommandPalette, PaletteCommand
from ai_qa_test_studio.ui.widgets.data_drawer import TestDataDrawer
from ai_qa_test_studio.ui.widgets.editor_pane import EditorPane
from ai_qa_test_studio.ui.widgets.explorer_pane import ExplorerPane
from ai_qa_test_studio.ui.widgets.top_toolbar import UnifiedTopBar


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
        self._last_report: RunReport | None = None
        self._last_error: str = ""
        self._token_budget = TokenBudget()
        self._skills = ProgressiveSkillLoader(Path(DEFAULT_SKILLS_DIRNAME))
        self._chat_store = ChatHistoryStore(self.data_root)
        self._copilot_cancel: asyncio.Event | None = None
        self._dom_distiller = DomDistiller(self._token_budget)
        self._build_chrome()
        if self.project is None:
            self.project = self._import_or_empty()
            self.storage.save(self.project)
        self._reload_ui()
        self._refresh_status_pills()

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
        self.top_bar.wizard_clicked.connect(self._open_copilot)
        self.top_bar.new_case_clicked.connect(self._new_case)
        self.top_bar.sync_url_clicked.connect(lambda _url: self._sync_url())
        self.top_bar.run_clicked.connect(self._run_current)
        self.top_bar.stop_clicked.connect(self._stop_browser)
        self.top_bar.export_requested.connect(self._export)
        root_layout.addWidget(self.top_bar)

        # Compatibility aliases used by tests / handlers
        self.url_edit = self.top_bar.url_input
        self.skill_combo = self.top_bar.skill_combo
        self.unbound_badge = self.top_bar.unbound_badge

        v_splitter = QSplitter(Qt.Orientation.Vertical, central)
        h_splitter = QSplitter(Qt.Orientation.Horizontal, v_splitter)

        self.explorer = ExplorerPane(h_splitter)
        self.explorer.case_selected.connect(self._on_case_selected)
        self.builder = EditorPane(h_splitter)
        self.builder.case_changed.connect(self._on_case_fields_changed)
        self.builder.sync_requested.connect(self._sync_url)

        self.browser_agent = BrowserAgentPane(h_splitter)
        self.viewport = self.browser_agent
        self._run_logs = self.browser_agent.agent_feed
        self.copilot = self.browser_agent.copilot
        self.browser_agent.launch_browser_clicked.connect(self._launch_browser)
        self.browser_agent.clicked_css.connect(self._on_viewport_click)
        self.copilot.prompt_submitted.connect(self._on_copilot_prompt)
        self.copilot.stop_requested.connect(self._stop_copilot)
        self.copilot.apply_requested.connect(self._apply_copilot_payload)
        self.copilot.info_message.connect(
            lambda title, body: self._toast_info(title, body)
        )
        self.copilot.session_changed.connect(self._persist_copilot_session)

        h_splitter.addWidget(self.explorer)
        h_splitter.addWidget(self.builder)
        h_splitter.addWidget(self.browser_agent)
        h_splitter.setSizes(list(UI_SPLIT_SIZES_H))

        self.data_drawer = TestDataDrawer(v_splitter)
        self.data_grid = self.data_drawer.table
        v_splitter.addWidget(h_splitter)
        v_splitter.addWidget(self.data_drawer)
        v_splitter.setSizes(list(UI_SPLIT_SIZES_V))

        root_layout.addWidget(v_splitter, stretch=1)
        self.setCentralWidget(central)

        act_save = QAction("Save", self)
        act_save.setShortcut(QKeySequence.StandardKey.Save)
        act_save.triggered.connect(self._save_project)
        self.addAction(act_save)

        palette = QAction("Command Palette", self)
        palette.setShortcut(QKeySequence("Ctrl+K"))
        palette.triggered.connect(self._command_palette)
        self.addAction(palette)

        status = self.statusBar()
        status.setAccessibleName("Thanh trạng thái")
        self._status_caption = CaptionLabel("Sẵn sàng", self)
        status.addWidget(self._status_caption, stretch=1)
        self._pill_mcp = InfoBadge.success("MCP: Playwright Ready", self)
        self._pill_mcp.setAccessibleName("Trạng thái MCP Playwright")
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

    def _load_case(self, case: TestCase) -> None:
        self.builder.load_case(case)
        if case.start_url:
            self.url_edit.setText(case.start_url)
        if case.test_data_rows:
            self.data_drawer.load_rows(case.test_data_rows)
        else:
            self.data_drawer.load_rows([])
        self._update_unbound_badge(case)
        self._status_caption.setText(f"Case {case.code} · {len(case.steps)} bước")

    def _update_unbound_badge(self, case: TestCase | None) -> None:
        if case is None:
            self.top_bar.set_unbound_count(0)
            return
        self.top_bar.set_unbound_count(count_unbound_steps(case))

    def _on_case_fields_changed(self) -> None:
        self._update_unbound_badge(self.builder.peek_case())

    def _open_copilot(self) -> None:
        self.browser_agent.show_copilot(compose=True)
        self._toast_info(
            "QA Copilot",
            "Tab AI Wizard (3 bước) nằm trong Copilot. Chat dùng Planner→Composer→Critic.",
        )

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
        self._chat_store.upsert(self.copilot.session)
        self._chat_store.save()

    def _stop_copilot(self) -> None:
        if self._copilot_cancel is not None:
            self._copilot_cancel.set()
        self.copilot.mark_stopped()

    @asyncSlot(str, dict)
    async def _on_copilot_prompt(self, text: str, flags: dict[str, Any]) -> None:
        self.copilot.set_busy(True)
        self._copilot_cancel = asyncio.Event()
        self.copilot.set_apply_payload(None)
        try:
            context = await self._build_copilot_context(flags)
            mode = str(flags.get("mode") or self.copilot.session.mode)
            if "srs" in text.lower() or "kịch bản" in text.lower():
                mode = COPILOT_MODE_COMPOSE
                self.copilot.session.mode = mode
            gateway = UnifiedLLMGateway.from_settings(self.env.settings.ai)
            orch = CopilotOrchestrator(gateway)
            history = [
                ChatMessage(role=m.role if m.role == "user" else "assistant", content=m.content)
                for m in self.copilot.session.llm_window()
                if m.role in {"user", ROLE_COMPOSER, "assistant"}
            ]
            current_role: str | None = None
            async for event in orch.run(
                user_text=text,
                mode=mode,
                context=context,
                history=history[:-1] if history else [],
                cancel=self._copilot_cancel,
            ):
                if event.kind == "role_start" and event.role:
                    current_role = event.role
                    self.copilot.begin_role(event.role)
                elif event.kind == "chunk":
                    self.copilot.append_stream_chunk(event.text)
                elif event.kind == "role_end" and event.role:
                    self.copilot.end_role(event.role, event.text)
                    current_role = None
                elif event.kind == "apply_ready" and event.apply_payload:
                    self.copilot.set_apply_payload(event.apply_payload)
                elif event.kind == "stopped":
                    self.copilot.mark_stopped(event.role or current_role)
                    return
            self._persist_copilot_session()
            used = self._token_budget.count(
                "\n".join(m.content for m in self.copilot.session.messages)
            )
            self._set_pill(
                self._pill_tokens,
                f"Tokens: {used / 1000:.1f}k / {TOKEN_CONTEXT_BUDGET / 1000:.0f}k",
            )
        except Exception as exc:  # noqa: BLE001
            self._last_error = str(exc)
            self._toast_error("Copilot", str(exc))
            self.copilot.mark_stopped()
        finally:
            self.copilot.set_busy(False)
            self._copilot_cancel = None

    async def _build_copilot_context(self, flags: dict[str, Any]) -> dict[str, Any]:
        ctx: dict[str, Any] = {
            "include_case": bool(flags.get("include_case")),
            "include_dom": bool(flags.get("include_dom")),
            "include_last_error": bool(flags.get("include_last_error")),
        }
        case = self.builder.peek_case() if hasattr(self.builder, "peek_case") else self.builder.current_case()
        if ctx["include_case"] and case is not None:
            ctx["case"] = case.model_dump_json()
            ctx["srs"] = f"{case.title}\n{case.expected_final_result or ''}"
        if ctx["include_last_error"] and self._last_error:
            ctx["last_error"] = self._last_error
        if ctx["include_dom"] and self.session.started:
            try:
                compact, _ = await self._dom_distiller.extract(self.session.engine.page)
                ctx["dom"] = compact
            except Exception as exc:  # noqa: BLE001
                ctx["dom"] = f"(DOM unavailable: {exc})"
        return ctx

    def _apply_copilot_payload(self, payload: dict[str, Any]) -> None:
        if self.project is None:
            return
        kind = payload.get("kind")
        if kind == APPLY_KIND_COMPOSE:
            scenarios_raw = payload.get("scenarios") or []
            scenarios = [TestScenario.model_validate(item) for item in scenarios_raw]
            if not scenarios:
                return
            start_url = str(payload.get("start_url") or "").strip()
            if start_url:
                self.url_edit.setText(start_url)
                for scenario in scenarios:
                    for case in scenario.cases:
                        case.start_url = start_url
            self.project.root_group.scenarios.extend(scenarios)
            self.storage.save(self.project)
            self._reload_ui()
            self._append_log(
                f"Copilot/AI Wizard Apply: {len(scenarios)} scenario, "
                f"{sum(len(s.cases) for s in scenarios)} case UNBOUND"
            )
            self._toast_info("Apply", f"Đã thêm {len(scenarios)} scenario draft.")
            return
        if kind == APPLY_KIND_TEST_DATA:
            rows = list(payload.get("rows") or [])
            target = self.builder.current_case()
            if target is None:
                self._toast_warn("Apply", "Chọn một test case trước khi thêm Test Data.")
                return
            target.test_data_rows = list(target.test_data_rows) + rows
            self._merge_case(target)
            self.storage.save(self.project)
            self.data_drawer.load_rows(target.test_data_rows)
            self._append_log(f"Copilot Apply: +{len(rows)} Test Data rows")
            self._toast_info("Apply", f"Đã thêm {len(rows)} bộ Test Data.")
            return
        self._toast_warn("Apply", f"Loại artifact chưa hỗ trợ: {kind}")

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
            QMessageBox.information(self, "Case", "Chọn một test case trên Explorer trước.")
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
        if self._busy:
            return
        self._busy = True
        try:
            url = self.url_edit.text().strip() or DEFAULT_TARGET_URL
            self.viewport.set_placeholder(SCREENCAST_STATUS_WAITING)
            opened = await self.session.launch_url(url, log=self._append_log)
            self._set_pill(self._pill_mcp, "MCP: Playwright Ready")
            if opened == FALLBACK_BLANK_URL and url not in {"", FALLBACK_BLANK_URL}:
                self.viewport.set_placeholder(f"{SCREENCAST_STATUS_NAV_FAILED}\n«{url}»")
                self._toast_warn(
                    "URL không mở được",
                    f"Không resolve được «{url}». Đã mở trang trống — nhập URL thật rồi Launch lại.",
                )
            else:
                await self._refresh_viewport()
        except Exception as exc:  # noqa: BLE001
            self._append_log(f"Không mở được trình duyệt: {exc}")
            self._toast_error("Launch Browser", str(exc))
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
        self._status_caption.setText("Đang đồng bộ…")
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
            self._set_pill(self._pill_mcp, f"MCP: Playwright ({endpoint})")
        except Exception as exc:  # noqa: BLE001
            self._append_log(f"Sync thất bại: {exc}")
            self._toast_error("Sync URL", str(exc))
        finally:
            self._busy = False
            self._status_caption.setText("Sẵn sàng")

    @asyncSlot()
    async def _run_current(self) -> None:
        if self._busy or self.project is None:
            return
        case = self._require_case()
        if case is None:
            return
        self._busy = True
        self._status_caption.setText("Đang chạy…")
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
            self._set_pill(self._pill_mcp, f"MCP: Playwright ({report.cases[0].status})")
            used = self._token_budget.count(self._run_logs.to_plain_text())
            self._set_pill(
                self._pill_tokens,
                f"Tokens: {used / 1000:.1f}k / {TOKEN_CONTEXT_BUDGET / 1000:.0f}k",
            )
        except Exception as exc:  # noqa: BLE001
            self._append_log(f"Chạy thất bại: {exc}")
            self._toast_error("Run", str(exc))
        finally:
            self._busy = False
            self._status_caption.setText("Sẵn sàng")

    @asyncSlot()
    async def _stop_browser(self) -> None:
        try:
            await self.session.stop(log=self._append_log)
            self.viewport.set_placeholder("")
            self._set_pill(self._pill_mcp, "MCP: Playwright (Stopped)")
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
        state = "Ready" if not self.session.started else "Connected"
        self._set_pill(self._pill_mcp, f"MCP: Playwright ({state})")

    def showEvent(self, event) -> None:  # type: ignore[no-untyped-def]
        super().showEvent(event)
        bind_accessible_root(self)

    def closeEvent(self, event) -> None:  # type: ignore[no-untyped-def]
        if self.session.started:
            self._append_log("Đang đóng — trình duyệt sẽ dừng ở phiên tiếp theo")
        super().closeEvent(event)
