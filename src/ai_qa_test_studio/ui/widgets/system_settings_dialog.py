"""System Settings hub — app config + multi AI API + multi models + routing."""

from __future__ import annotations

import copy
import uuid

from PySide6.QtWidgets import QFormLayout, QHBoxLayout, QListWidgetItem, QVBoxLayout, QWidget
from qfluentwidgets import (
    BodyLabel,
    CheckBox,
    ComboBox,
    LineEdit,
    ListWidget,
    MessageBoxBase,
    PasswordLineEdit,
    Pivot,
    PrimaryPushButton,
    PushButton,
    SpinBox,
    SubtitleLabel,
    SwitchButton,
)

from ai_qa_test_studio.constants import (
    LLM_PROVIDER_GEMINI,
    LLM_PROVIDER_OFFLINE,
    LLM_PROVIDER_OPENAI_COMPAT,
    LLM_TIER_FAST,
    LLM_TIER_MID,
    LLM_TIER_STRONG,
    ROUTING_MODE_AUTO,
    ROUTING_MODE_MANUAL,
    UI_SETTINGS_MIN_HEIGHT,
    UI_SETTINGS_MIN_WIDTH,
)
from ai_qa_test_studio.core.env_manager import default_task_complexity_matrix
from ai_qa_test_studio.core.models import (
    AiConnection,
    AiModelEntry,
    AiRoutingSettings,
    AiSettings,
    AppSettings,
    CopilotRuntimeSettings,
    McpExternalSettings,
    McpSettings,
)


class SystemSettingsDialog(MessageBoxBase):
    def __init__(self, settings: AppSettings, parent: QWidget) -> None:
        super().__init__(parent)
        self.setWindowTitle("Cấu hình hệ thống")
        self._draft = settings.model_copy(deep=True)
        if not self._draft.ai.connections:
            self._draft.ai = AiSettings(
                connections=[
                    AiConnection(
                        id=str(uuid.uuid4()),
                        label="Default",
                        provider=LLM_PROVIDER_OFFLINE,
                        models=[
                            AiModelEntry(
                                id="offline", label="Offline", tier=LLM_TIER_MID, is_default=True
                            )
                        ],
                    )
                ],
                default_connection_id="",
                routing=AiRoutingSettings(task_complexity_models=default_task_complexity_matrix()),
            )
            self._draft.ai.default_connection_id = self._draft.ai.connections[0].id
        if not self._draft.ai.routing.task_complexity_models:
            self._draft.ai.routing.task_complexity_models = default_task_complexity_matrix()

        self.widget.setMinimumWidth(UI_SETTINGS_MIN_WIDTH)
        self.widget.setMinimumHeight(UI_SETTINGS_MIN_HEIGHT)
        self.viewLayout.addWidget(SubtitleLabel("Cấu hình hệ thống", self.widget))

        self.pivot = Pivot(self.widget)
        self.pages: dict[str, QWidget] = {}
        for key, title, builder in (
            ("general", "Chung", self._build_general),
            ("browser", "Browser", self._build_browser),
            ("apis", "AI APIs", self._build_apis),
            ("models", "Models", self._build_models),
            ("routing", "Routing", self._build_routing),
            ("copilot", "Copilot", self._build_copilot),
            ("mcp", "MCP", self._build_mcp),
        ):
            page = QWidget(self.widget)
            builder(page)
            self.pages[key] = page
            self.viewLayout.addWidget(page)
            page.setVisible(key == "general")
            self.pivot.addItem(routeKey=key, text=title, onClick=lambda checked, k=key: self._show(k))
        self.viewLayout.insertWidget(1, self.pivot)
        self.pivot.setCurrentItem("general")

        self.yesButton.setText("Lưu")
        self.cancelButton.setText("Hủy")
        self.yesButton.clicked.connect(self._on_save)
        self.cancelButton.clicked.connect(self.reject)
        self._accepted = False

    def _show(self, key: str) -> None:
        for k, page in self.pages.items():
            page.setVisible(k == key)
        if key == "models":
            self._reload_model_list()
        if key == "apis":
            self._reload_api_list()

    def _build_general(self, page: QWidget) -> None:
        form = QFormLayout(page)
        self.theme_combo = ComboBox(page)
        self.theme_combo.addItems(["dark", "light"])
        self.theme_combo.setCurrentText(self._draft.theme)
        self.env_edit = LineEdit(page)
        self.env_edit.setText(self._draft.active_environment)
        self.headless_switch = SwitchButton(page)
        self.headless_switch.setChecked(self._draft.headless_default)
        form.addRow("Theme", self.theme_combo)
        form.addRow("Environment", self.env_edit)
        form.addRow("Headless mặc định", self.headless_switch)

    def _build_browser(self, page: QWidget) -> None:
        form = QFormLayout(page)
        self.browser_combo = ComboBox(page)
        self.browser_combo.addItems(["CHROMIUM", "CHROME", "EDGE"])
        self.browser_combo.setCurrentText(self._draft.browser)
        form.addRow("Browser engine", self.browser_combo)
        form.addRow(BodyLabel("Viewport lấy từ constants runtime (1280×720).", page))

    def _build_apis(self, page: QWidget) -> None:
        layout = QVBoxLayout(page)
        self.api_list = ListWidget(page)
        layout.addWidget(self.api_list)
        row = QHBoxLayout()
        self.btn_add_api = PushButton("Thêm API", page)
        self.btn_del_api = PushButton("Xóa", page)
        row.addWidget(self.btn_add_api)
        row.addWidget(self.btn_del_api)
        layout.addLayout(row)
        form = QFormLayout()
        self.api_label = LineEdit(page)
        self.api_provider = ComboBox(page)
        self.api_provider.addItems(
            [LLM_PROVIDER_OFFLINE, LLM_PROVIDER_OPENAI_COMPAT, LLM_PROVIDER_GEMINI]
        )
        self.api_base = LineEdit(page)
        self.api_key = PasswordLineEdit(page)
        self.api_enabled = CheckBox("Enabled", page)
        form.addRow("Label", self.api_label)
        form.addRow("Provider", self.api_provider)
        form.addRow("Base URL", self.api_base)
        form.addRow("API Key", self.api_key)
        form.addRow("", self.api_enabled)
        layout.addLayout(form)
        self.btn_apply_api = PrimaryPushButton("Cập nhật API đang chọn", page)
        layout.addWidget(self.btn_apply_api)
        self.btn_add_api.clicked.connect(self._add_api)
        self.btn_del_api.clicked.connect(self._del_api)
        self.btn_apply_api.clicked.connect(self._apply_api)
        self.api_list.currentRowChanged.connect(self._load_api_row)
        self._reload_api_list()

    def _build_models(self, page: QWidget) -> None:
        layout = QVBoxLayout(page)
        self.model_api_combo = ComboBox(page)
        layout.addWidget(BodyLabel("API", page))
        layout.addWidget(self.model_api_combo)
        self.model_list = ListWidget(page)
        layout.addWidget(self.model_list)
        row = QHBoxLayout()
        self.btn_add_model = PushButton("Thêm model", page)
        self.btn_del_model = PushButton("Xóa model", page)
        row.addWidget(self.btn_add_model)
        row.addWidget(self.btn_del_model)
        layout.addLayout(row)
        form = QFormLayout()
        self.model_id = LineEdit(page)
        self.model_label = LineEdit(page)
        self.model_tier = ComboBox(page)
        self.model_tier.addItems([LLM_TIER_FAST, LLM_TIER_MID, LLM_TIER_STRONG])
        self.model_default = CheckBox("Default của API", page)
        form.addRow("Model id", self.model_id)
        form.addRow("Label", self.model_label)
        form.addRow("Tier", self.model_tier)
        form.addRow("", self.model_default)
        layout.addLayout(form)
        self.btn_apply_model = PrimaryPushButton("Cập nhật model", page)
        layout.addWidget(self.btn_apply_model)
        self.model_api_combo.currentIndexChanged.connect(self._reload_model_list)
        self.model_list.currentRowChanged.connect(self._load_model_row)
        self.btn_add_model.clicked.connect(self._add_model)
        self.btn_del_model.clicked.connect(self._del_model)
        self.btn_apply_model.clicked.connect(self._apply_model)
        self._reload_model_api_combo()

    def _build_routing(self, page: QWidget) -> None:
        form = QFormLayout(page)
        self.route_mode = ComboBox(page)
        self.route_mode.addItems([ROUTING_MODE_AUTO, ROUTING_MODE_MANUAL])
        self.route_mode.setCurrentText(self._draft.ai.routing.mode or ROUTING_MODE_AUTO)
        self.complexity_enabled = CheckBox("Bật chọn model theo độ phức tạp", page)
        self.complexity_enabled.setChecked(self._draft.ai.routing.complexity_enabled)
        self.default_conn = ComboBox(page)
        self._reload_default_conn()
        form.addRow("Mode", self.route_mode)
        form.addRow("", self.complexity_enabled)
        form.addRow("Default API", self.default_conn)
        form.addRow(
            BodyLabel("Ma trận task×complexity dùng tier fast/mid/strong (mặc định trong code).", page)
        )

    def _build_copilot(self, page: QWidget) -> None:
        form = QFormLayout(page)
        c = self._draft.copilot
        self.spin_summary = SpinBox(page)
        self.spin_summary.setRange(40, 400)
        self.spin_summary.setValue(c.summary_max_chars)
        self.spin_debate = SpinBox(page)
        self.spin_debate.setRange(0, 8)
        self.spin_debate.setValue(c.debate_max_rounds)
        self.spin_loop = SpinBox(page)
        self.spin_loop.setRange(1, 5)
        self.spin_loop.setValue(c.loop_repeat_limit)
        self.spin_clarify = SpinBox(page)
        self.spin_clarify.setRange(5_000, 300_000)
        self.spin_clarify.setSingleStep(1000)
        self.spin_clarify.setValue(c.clarify_timeout_ms)
        self.spin_lean = SpinBox(page)
        self.spin_lean.setRange(500, 16_000)
        self.spin_lean.setValue(c.lean_tail_max_tokens)
        form.addRow("Summary max chars", self.spin_summary)
        form.addRow("Debate max rounds", self.spin_debate)
        form.addRow("Loop repeat limit", self.spin_loop)
        form.addRow("Clarify timeout ms", self.spin_clarify)
        form.addRow("Lean tail tokens", self.spin_lean)

    def _build_mcp(self, page: QWidget) -> None:
        form = QFormLayout(page)
        form.addRow(BodyLabel("Bundled MCP (qa-compress / crew-memory / project-context): luôn bật.", page))
        self.mcp_pw = CheckBox("External Playwright MCP", page)
        self.mcp_pw.setChecked(self._draft.mcp.external.playwright)
        self.mcp_mid = CheckBox("External Midscene MCP", page)
        self.mcp_mid.setChecked(self._draft.mcp.external.midscene)
        form.addRow("", self.mcp_pw)
        form.addRow("", self.mcp_mid)

    def _reload_api_list(self) -> None:
        self.api_list.clear()
        for conn in self._draft.ai.connections:
            self.api_list.addItem(QListWidgetItem(f"{conn.label} ({conn.provider})"))
        if self._draft.ai.connections:
            self.api_list.setCurrentRow(0)

    def _reload_default_conn(self) -> None:
        self.default_conn.clear()
        for conn in self._draft.ai.connections:
            self.default_conn.addItem(conn.label or conn.id, conn.id)
        idx = max(
            0,
            next(
                (
                    i
                    for i, c in enumerate(self._draft.ai.connections)
                    if c.id == self._draft.ai.default_connection_id
                ),
                0,
            ),
        )
        self.default_conn.setCurrentIndex(idx)

    def _reload_model_api_combo(self) -> None:
        self.model_api_combo.blockSignals(True)
        self.model_api_combo.clear()
        for conn in self._draft.ai.connections:
            self.model_api_combo.addItem(conn.label or conn.id, conn.id)
        self.model_api_combo.blockSignals(False)
        self._reload_model_list()

    def _current_conn(self) -> AiConnection | None:
        row = self.api_list.currentRow()
        conns = list(self._draft.ai.connections)
        if row < 0 or row >= len(conns):
            return None
        conn = conns[row]
        return conn if isinstance(conn, AiConnection) else None

    def _models_conn(self) -> AiConnection | None:
        conn_id = self.model_api_combo.currentData()
        if not isinstance(conn_id, str):
            return None
        return next((c for c in self._draft.ai.connections if c.id == conn_id), None)

    def _load_api_row(self, row: int) -> None:
        if row < 0 or row >= len(self._draft.ai.connections):
            return
        conn = self._draft.ai.connections[row]
        self.api_label.setText(conn.label)
        self.api_provider.setCurrentText(conn.provider or LLM_PROVIDER_OFFLINE)
        self.api_base.setText(conn.base_url)
        self.api_key.setText(conn.api_key_ref)
        self.api_enabled.setChecked(conn.enabled)

    def _add_api(self) -> None:
        conn = AiConnection(
            id=str(uuid.uuid4()),
            label=f"API {len(self._draft.ai.connections) + 1}",
            provider=LLM_PROVIDER_OPENAI_COMPAT,
            models=[],
        )
        self._draft.ai.connections.append(conn)
        if not self._draft.ai.default_connection_id:
            self._draft.ai.default_connection_id = conn.id
        self._reload_api_list()
        self._reload_model_api_combo()
        self._reload_default_conn()
        self.api_list.setCurrentRow(len(self._draft.ai.connections) - 1)

    def _del_api(self) -> None:
        row = self.api_list.currentRow()
        if row < 0:
            return
        del self._draft.ai.connections[row]
        self._reload_api_list()
        self._reload_model_api_combo()
        self._reload_default_conn()

    def _apply_api(self) -> None:
        conn = self._current_conn()
        if conn is None:
            return
        conn.label = self.api_label.text().strip() or conn.label
        conn.provider = self.api_provider.currentText()
        conn.base_url = self.api_base.text().strip()
        conn.api_key_ref = self.api_key.text()
        conn.enabled = self.api_enabled.isChecked()
        self._reload_api_list()
        self._reload_model_api_combo()
        self._reload_default_conn()

    def _reload_model_list(self) -> None:
        self.model_list.clear()
        conn = self._models_conn()
        if conn is None:
            return
        for model in conn.models:
            mark = " *" if model.is_default else ""
            self.model_list.addItem(QListWidgetItem(f"{model.id} [{model.tier}]{mark}"))
        if conn.models:
            self.model_list.setCurrentRow(0)

    def _load_model_row(self, row: int) -> None:
        conn = self._models_conn()
        if conn is None or row < 0 or row >= len(conn.models):
            return
        model = conn.models[row]
        self.model_id.setText(model.id)
        self.model_label.setText(model.label)
        self.model_tier.setCurrentText(model.tier)
        self.model_default.setChecked(model.is_default)

    def _add_model(self) -> None:
        conn = self._models_conn()
        if conn is None:
            return
        mid = f"model-{len(conn.models) + 1}"
        conn.models.append(AiModelEntry(id=mid, label=mid, tier=LLM_TIER_MID, is_default=not conn.models))
        self._reload_model_list()
        self.model_list.setCurrentRow(len(conn.models) - 1)

    def _del_model(self) -> None:
        conn = self._models_conn()
        row = self.model_list.currentRow()
        if conn is None or row < 0:
            return
        del conn.models[row]
        self._reload_model_list()

    def _apply_model(self) -> None:
        conn = self._models_conn()
        row = self.model_list.currentRow()
        if conn is None or row < 0 or row >= len(conn.models):
            return
        model = conn.models[row]
        model.id = self.model_id.text().strip() or model.id
        model.label = self.model_label.text().strip() or model.id
        model.tier = self.model_tier.currentText()
        model.is_default = self.model_default.isChecked()
        if model.is_default:
            for other in conn.models:
                if other is not model:
                    other.is_default = False
        self._reload_model_list()

    def _on_save(self) -> None:
        self._draft.theme = self.theme_combo.currentText()
        self._draft.active_environment = self.env_edit.text().strip() or "DEV"
        self._draft.headless_default = self.headless_switch.isChecked()
        self._draft.browser = self.browser_combo.currentText()
        self._draft.ai.routing.mode = self.route_mode.currentText()
        self._draft.ai.routing.complexity_enabled = self.complexity_enabled.isChecked()
        conn_id = self.default_conn.currentData()
        if conn_id:
            self._draft.ai.default_connection_id = str(conn_id)
        primary = next(
            (c for c in self._draft.ai.connections if c.id == self._draft.ai.default_connection_id),
            self._draft.ai.connections[0] if self._draft.ai.connections else None,
        )
        if primary is not None:
            self._draft.ai.provider = primary.provider
            self._draft.ai.base_url = primary.base_url
            self._draft.ai.api_key_ref = primary.api_key_ref
            default_model = next((m for m in primary.models if m.is_default), None)
            if default_model is None and primary.models:
                default_model = primary.models[0]
            self._draft.ai.model = default_model.id if default_model else ""
        if not self._draft.ai.routing.task_complexity_models:
            self._draft.ai.routing.task_complexity_models = default_task_complexity_matrix()
        self._draft.copilot = CopilotRuntimeSettings(
            summary_max_chars=self.spin_summary.value(),
            debate_max_rounds=self.spin_debate.value(),
            loop_repeat_limit=self.spin_loop.value(),
            clarify_timeout_ms=self.spin_clarify.value(),
            lean_tail_max_tokens=self.spin_lean.value(),
        )
        self._draft.mcp = McpSettings(
            external=McpExternalSettings(
                playwright=self.mcp_pw.isChecked(),
                midscene=self.mcp_mid.isChecked(),
            )
        )
        self._accepted = True
        self.accept()

    def result_settings(self) -> AppSettings | None:
        return copy.deepcopy(self._draft) if self._accepted else None
