"""System Settings hub — app config + multi AI API + multi models + routing."""

from __future__ import annotations

import copy
import uuid

from PySide6.QtCore import Qt, QThread, Signal
from PySide6.QtWidgets import (
    QAbstractItemView,
    QCompleter,
    QFrame,
    QHBoxLayout,
    QListWidgetItem,
    QScrollArea,
    QSizePolicy,
    QStackedWidget,
    QTreeWidgetItem,
    QVBoxLayout,
    QWidget,
)
from qfluentwidgets import (
    BodyLabel,
    CaptionLabel,
    CheckBox,
    ComboBox,
    EditableComboBox,
    LineEdit,
    ListWidget,
    MessageBoxBase,
    PasswordLineEdit,
    Pivot,
    PrimaryPushButton,
    PushButton,
    SpinBox,
    StrongBodyLabel,
    SubtitleLabel,
    SwitchButton,
    TreeWidget,
)

from ai_qa_test_studio.agent.model_catalog import list_available_models
from ai_qa_test_studio.constants import (
    LLM_PROVIDER_GEMINI,
    LLM_PROVIDER_OFFLINE,
    LLM_PROVIDER_OPENAI_COMPAT,
    LLM_TIER_FAST,
    LLM_TIER_MID,
    LLM_TIER_STRONG,
    ROUTING_MODE_AUTO,
    ROUTING_MODE_MANUAL,
    THEME_CARD_RADIUS,
    UI_SETTINGS_BTN_MAX_WIDTH,
    UI_SETTINGS_BTN_MIN_WIDTH,
    UI_SETTINGS_CONTENT_HEIGHT,
    UI_SETTINGS_FIELD_SPACING,
    UI_SETTINGS_LIST_HEIGHT,
    UI_SETTINGS_MIN_HEIGHT,
    UI_SETTINGS_MIN_WIDTH,
    UI_SETTINGS_MODEL_COMBO_PLACEHOLDER,
    UI_SETTINGS_MODEL_COMBO_TOOLTIP,
    UI_SETTINGS_MODEL_COMPLETER_MAX_VISIBLE,
    UI_SETTINGS_MODEL_UNSET_LABEL,
    UI_SETTINGS_NAV_WIDTH,
    UI_SETTINGS_SECTION_GAP,
    UI_SETTINGS_SPIN_WIDTH,
    UI_SETTINGS_SPIN_WIDTH_WIDE,
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
from ai_qa_test_studio.core.secret_store import resolve_api_key, seal_api_key_ref
from ai_qa_test_studio.ui.theme import get_theme_tokens, text_primary_css

_TAB_DEFS: tuple[tuple[str, str], ...] = (
    ("general", "Chung"),
    ("browser", "Browser"),
    ("apis", "AI APIs"),
    ("models", "Models"),
    ("routing", "Routing"),
    ("copilot", "Copilot"),
    ("mcp", "MCP"),
)


class _ModelListWorker(QThread):
    succeeded = Signal(list)
    failed = Signal(str)

    def __init__(self, provider: str, api_key: str, base_url: str, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self._provider = provider
        self._api_key = api_key
        self._base_url = base_url

    def run(self) -> None:
        try:
            ids = list_available_models(
                provider=self._provider,
                api_key=self._api_key,
                base_url=self._base_url,
            )
            self.succeeded.emit(ids)
        except Exception as exc:  # noqa: BLE001 — surface to UI status
            self.failed.emit(str(exc))


class SystemSettingsDialog(MessageBoxBase):
    def __init__(self, settings: AppSettings, parent: QWidget) -> None:
        super().__init__(parent)
        self.setWindowTitle("Cấu hình hệ thống")
        self._draft = settings.model_copy(deep=True)
        self._ensure_ai_defaults()
        self._accepted = False
        self._model_worker: _ModelListWorker | None = None
        self._remote_model_ids: list[str] = []

        self.widget.setMinimumWidth(UI_SETTINGS_MIN_WIDTH)
        self.widget.setMinimumHeight(UI_SETTINGS_MIN_HEIGHT)
        self.viewLayout.setContentsMargins(28, 20, 28, 12)
        self.viewLayout.setSpacing(10)

        title = SubtitleLabel("Cấu hình hệ thống", self.widget)
        title.setAccessibleName("Tiêu đề cấu hình hệ thống")
        self.viewLayout.addWidget(title)
        hint = CaptionLabel(
            "API, model, routing, Copilot và MCP — lưu vào settings.json",
            self.widget,
        )
        hint.setStyleSheet(f"color: {get_theme_tokens().text_secondary};")
        self.viewLayout.addWidget(hint)

        # Pivot kept for API compatibility; nav UI is Master-Detail TreeWidget.
        self.pivot = Pivot(self.widget)
        self.pivot.hide()
        self.pivot.setAccessibleName("Tab cấu hình")
        self.stack = QStackedWidget(self.widget)
        self.stack.setMinimumHeight(UI_SETTINGS_CONTENT_HEIGHT)
        self.stack.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        self.pages: dict[str, QWidget] = {}
        builders = {
            "general": self._build_general,
            "browser": self._build_browser,
            "apis": self._build_apis,
            "models": self._build_models,
            "routing": self._build_routing,
            "copilot": self._build_copilot,
            "mcp": self._build_mcp,
        }
        body = QHBoxLayout()
        body.setSpacing(16)
        self.nav_tree = TreeWidget(self.widget)
        self.nav_tree.setHeaderHidden(True)
        self.nav_tree.setFixedWidth(UI_SETTINGS_NAV_WIDTH)
        self.nav_tree.setAccessibleName("Danh mục cấu hình")
        for index, (key, title_text) in enumerate(_TAB_DEFS):
            page = self._wrap_scroll(builders[key]())
            self.pages[key] = page
            self.stack.addWidget(page)
            self.pivot.addItem(
                routeKey=key,
                text=title_text,
                onClick=lambda _checked=False, k=key, i=index: self._show(k, i),
            )
            item = QTreeWidgetItem([title_text])
            item.setData(0, Qt.ItemDataRole.UserRole, (key, index))
            self.nav_tree.addTopLevelItem(item)
        self.nav_tree.currentItemChanged.connect(self._on_nav_changed)
        body.addWidget(self.nav_tree)
        body.addWidget(self.stack, stretch=1)
        self.viewLayout.addLayout(body, stretch=1)
        self.pivot.setCurrentItem("general")
        self.stack.setCurrentIndex(0)
        if self.nav_tree.topLevelItemCount() > 0:
            self.nav_tree.setCurrentItem(self.nav_tree.topLevelItem(0))

        self.yesButton.setText("Lưu")
        self.cancelButton.setText("Hủy")
        self._layout_footer_buttons()

    def _ensure_ai_defaults(self) -> None:
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

    def _layout_footer_buttons(self) -> None:
        while self.buttonLayout.count():
            item = self.buttonLayout.takeAt(0)
            if item is None:
                break
        self.buttonLayout.setContentsMargins(28, 16, 28, 20)
        self.buttonLayout.setSpacing(10)
        self.buttonLayout.addStretch(1)
        for btn in (self.yesButton, self.cancelButton):
            btn.setMinimumWidth(UI_SETTINGS_BTN_MIN_WIDTH)
            btn.setMaximumWidth(UI_SETTINGS_BTN_MAX_WIDTH)
            self.buttonLayout.addWidget(btn, 0, Qt.AlignmentFlag.AlignVCenter)

    def _wrap_scroll(self, inner: QWidget) -> QScrollArea:
        # Native QScrollArea — SmoothScrollArea animation fights nested ListWidget wheels.
        # Opaque fill is required: translucent CardWidget/SpinBox + transparent viewport
        # makes Qt scroll-blit leave overlapping paint ghosts.
        bg = get_theme_tokens().bg_canvas
        scroll = QScrollArea(self.widget)
        scroll.setObjectName("SystemSettingsScroll")
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.Shape.NoFrame)
        scroll.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        scroll.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        viewport = scroll.viewport()
        viewport.setObjectName("SystemSettingsScrollViewport")
        viewport.setAutoFillBackground(True)
        scroll.setStyleSheet(
            "#SystemSettingsScroll, #SystemSettingsScrollViewport {"
            f"background-color: {bg}; border: none;"
            "}"
        )
        scroll.setWidget(inner)
        bar = scroll.verticalScrollBar()
        bar.setSingleStep(24)
        bar.setPageStep(120)
        return scroll

    def _on_nav_changed(
        self, current: QTreeWidgetItem | None, _previous: QTreeWidgetItem | None
    ) -> None:
        if current is None:
            return
        data = current.data(0, Qt.ItemDataRole.UserRole)
        if isinstance(data, tuple) and len(data) == 2:
            key, index = data
            self._show(str(key), int(index))

    def _show(self, key: str, index: int) -> None:
        self.stack.setCurrentIndex(index)
        self.pivot.setCurrentItem(key)
        if key == "models":
            self._reload_model_list()
            self._fetch_remote_models()
        elif key == "apis":
            self._reload_api_list()

    def _card(self, parent: QWidget, title: str) -> tuple[QFrame, QVBoxLayout]:
        # Solid QFrame — Fluent CardWidget paints alpha overlays that ghost on scroll.
        tokens = get_theme_tokens()
        card = QFrame(parent)
        card.setObjectName("SystemSettingsCard")
        card.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Maximum)
        card.setAutoFillBackground(True)
        card.setStyleSheet(
            "#SystemSettingsCard {"
            f"background-color: {tokens.bg_topbar};"
            f"border: 1px solid {tokens.border_card};"
            f"border-radius: {THEME_CARD_RADIUS}px;"
            "}"
        )
        layout = QVBoxLayout(card)
        layout.setContentsMargins(16, 14, 16, 14)
        layout.setSpacing(UI_SETTINGS_FIELD_SPACING)
        layout.setAlignment(Qt.AlignmentFlag.AlignTop)
        layout.addWidget(StrongBodyLabel(title, card))
        return card, layout

    def _prep_settings_list(self, widget: ListWidget) -> None:
        """Keep rows glued to the top — do not let the list viewport expand and float items."""
        widget.setFixedHeight(UI_SETTINGS_LIST_HEIGHT)
        widget.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        widget.setVerticalScrollMode(QAbstractItemView.ScrollMode.ScrollPerPixel)
        widget.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        widget.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        widget.setSpacing(2)

    def _add_field(self, layout: QVBoxLayout, parent: QWidget, label: str, widget: QWidget) -> None:
        block = QVBoxLayout()
        block.setSpacing(4)
        block.setContentsMargins(0, 0, 0, 0)
        block.addWidget(CaptionLabel(label, parent))
        if not isinstance(widget, SpinBox):
            widget.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        block.addWidget(widget)
        layout.addLayout(block)

    def _style_spin(self, spin: SpinBox, *, wide: bool = False) -> None:
        """Fluent SpinBox needs fixed width + opaque fill; StrongFocus so wheel scrolls the page."""
        width = UI_SETTINGS_SPIN_WIDTH_WIDE if wide else UI_SETTINGS_SPIN_WIDTH
        tokens = get_theme_tokens()
        spin.setFixedWidth(width)
        spin.setSizePolicy(QSizePolicy.Policy.Fixed, QSizePolicy.Policy.Fixed)
        spin.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
        spin.setFocusPolicy(Qt.FocusPolicy.StrongFocus)
        spin.setAutoFillBackground(True)
        spin.setStyleSheet(
            "SpinBox {"
            f"color: {text_primary_css()};"
            f"background-color: {tokens.bg_canvas};"
            "padding-left: 12px;"
            "padding-right: 72px;"
            "}"
        )

    def _add_spin_field(
        self,
        layout: QVBoxLayout,
        parent: QWidget,
        label: str,
        hint: str,
        spin: SpinBox,
        *,
        wide: bool = False,
    ) -> None:
        self._style_spin(spin, wide=wide)
        block = QVBoxLayout()
        block.setSpacing(4)
        block.setContentsMargins(0, 0, 0, 0)
        block.addWidget(CaptionLabel(label, parent))
        tip = BodyLabel(hint, parent)
        tip.setWordWrap(True)
        tip.setStyleSheet(f"color: {get_theme_tokens().text_secondary};")
        block.addWidget(tip)
        block.addWidget(spin)
        layout.addLayout(block)

    def _page_root(self) -> tuple[QWidget, QVBoxLayout]:
        bg = get_theme_tokens().bg_canvas
        root = QWidget()
        root.setObjectName("SystemSettingsPage")
        root.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Minimum)
        root.setAutoFillBackground(True)
        root.setStyleSheet(f"#SystemSettingsPage {{ background-color: {bg}; }}")
        layout = QVBoxLayout(root)
        layout.setContentsMargins(0, 4, 4, 8)
        layout.setSpacing(UI_SETTINGS_SECTION_GAP)
        layout.setAlignment(Qt.AlignmentFlag.AlignTop)
        return root, layout

    def _build_general(self) -> QWidget:
        root, layout = self._page_root()
        card, body = self._card(root, "Giao diện & môi trường")
        self.theme_combo = ComboBox(card)
        self.theme_combo.addItems(["auto", "dark", "light"])
        theme_value = self._draft.theme if self._draft.theme in {"auto", "dark", "light"} else "auto"
        self.theme_combo.setCurrentText(theme_value)
        self.env_edit = LineEdit(card)
        self.env_edit.setText(self._draft.active_environment)
        self.env_edit.setPlaceholderText("DEV / STAGING / PROD")
        self.headless_switch = SwitchButton(card)
        self.headless_switch.setChecked(self._draft.headless_default)
        self._add_field(body, card, "Theme", self.theme_combo)
        self._add_field(body, card, "Environment", self.env_edit)
        switch_row = QHBoxLayout()
        switch_row.addWidget(CaptionLabel("Headless mặc định", card))
        switch_row.addStretch(1)
        switch_row.addWidget(self.headless_switch)
        body.addLayout(switch_row)
        layout.addWidget(card)
        layout.addStretch(1)
        return root

    def _build_browser(self) -> QWidget:
        root, layout = self._page_root()
        card, body = self._card(root, "Trình duyệt tự động")
        self.browser_combo = ComboBox(card)
        self.browser_combo.addItems(["CHROMIUM", "CHROME", "EDGE"])
        self.browser_combo.setCurrentText(self._draft.browser)
        self._add_field(body, card, "Browser engine", self.browser_combo)
        note = BodyLabel("Viewport runtime cố định 1280×720 (constants).", card)
        note.setWordWrap(True)
        note.setStyleSheet(f"color: {get_theme_tokens().text_secondary};")
        body.addWidget(note)
        layout.addWidget(card)
        layout.addStretch(1)
        return root

    def _build_apis(self) -> QWidget:
        root, layout = self._page_root()
        split = QHBoxLayout()
        split.setSpacing(UI_SETTINGS_SECTION_GAP)
        split.setAlignment(Qt.AlignmentFlag.AlignTop)

        list_card, list_body = self._card(root, "Danh sách API")
        self.api_list = ListWidget(list_card)
        self._prep_settings_list(self.api_list)
        list_body.addWidget(self.api_list)
        list_actions = QHBoxLayout()
        list_actions.setSpacing(8)
        self.btn_add_api = PushButton("Thêm", list_card)
        self.btn_del_api = PushButton("Xóa", list_card)
        list_actions.addWidget(self.btn_add_api)
        list_actions.addWidget(self.btn_del_api)
        list_actions.addStretch(1)
        list_body.addLayout(list_actions)
        split.addWidget(list_card, stretch=2, alignment=Qt.AlignmentFlag.AlignTop)

        form_card, form_body = self._card(root, "Chi tiết API")
        self.api_label = LineEdit(form_card)
        self.api_provider = ComboBox(form_card)
        self.api_provider.addItems(
            [LLM_PROVIDER_OFFLINE, LLM_PROVIDER_OPENAI_COMPAT, LLM_PROVIDER_GEMINI]
        )
        self.api_base = LineEdit(form_card)
        self.api_base.setPlaceholderText("https://api.example.com/v1")
        self.api_key = PasswordLineEdit(form_card)
        self.api_enabled = CheckBox("Enabled", form_card)
        self._add_field(form_body, form_card, "Label", self.api_label)
        self._add_field(form_body, form_card, "Provider", self.api_provider)
        self._add_field(form_body, form_card, "Base URL", self.api_base)
        self._add_field(form_body, form_card, "API Key", self.api_key)
        form_body.addWidget(self.api_enabled)
        self.btn_apply_api = PrimaryPushButton("Cập nhật API đang chọn", form_card)
        form_body.addWidget(self.btn_apply_api)
        split.addWidget(form_card, stretch=3, alignment=Qt.AlignmentFlag.AlignTop)

        layout.addLayout(split)
        layout.addStretch(1)
        self.btn_add_api.clicked.connect(self._add_api)
        self.btn_del_api.clicked.connect(self._del_api)
        self.btn_apply_api.clicked.connect(self._apply_api)
        self.api_list.currentRowChanged.connect(self._load_api_row)
        self._reload_api_list()
        return root

    def _build_models(self) -> QWidget:
        root, layout = self._page_root()
        api_card, api_body = self._card(root, "Chọn API")
        self.model_api_combo = ComboBox(api_card)
        self._add_field(api_body, api_card, "API", self.model_api_combo)
        layout.addWidget(api_card)

        split = QHBoxLayout()
        split.setSpacing(UI_SETTINGS_SECTION_GAP)
        split.setAlignment(Qt.AlignmentFlag.AlignTop)

        list_card, list_body = self._card(root, "Models")
        self.model_list = ListWidget(list_card)
        self._prep_settings_list(self.model_list)
        list_body.addWidget(self.model_list)
        list_actions = QHBoxLayout()
        self.btn_add_model = PushButton("Thêm", list_card)
        self.btn_del_model = PushButton("Xóa", list_card)
        list_actions.addWidget(self.btn_add_model)
        list_actions.addWidget(self.btn_del_model)
        list_actions.addStretch(1)
        list_body.addLayout(list_actions)
        split.addWidget(list_card, stretch=2, alignment=Qt.AlignmentFlag.AlignTop)

        form_card, form_body = self._card(root, "Chi tiết model")
        self.model_id = EditableComboBox(form_card)
        self.model_id.setPlaceholderText(UI_SETTINGS_MODEL_COMBO_PLACEHOLDER)
        self.model_id.setToolTip(UI_SETTINGS_MODEL_COMBO_TOOLTIP)
        # Fluent EditableComboBox adds free-typed text on Enter — keep catalog API-only.
        self.model_id.returnPressed.disconnect(self.model_id._onReturnPressed)
        self.model_id.returnPressed.connect(self._on_model_id_commit)
        self.btn_fetch_models = PushButton("Tải danh sách từ API", form_card)
        self.btn_fetch_models.setFixedHeight(28)
        self.model_fetch_status = CaptionLabel("", form_card)
        self.model_fetch_status.setWordWrap(True)
        self.model_fetch_status.setStyleSheet(f"color: {get_theme_tokens().text_secondary};")
        self.model_label = LineEdit(form_card)
        self.model_tier = ComboBox(form_card)
        self.model_tier.addItems([LLM_TIER_FAST, LLM_TIER_MID, LLM_TIER_STRONG])
        self.model_default = CheckBox("Default của API", form_card)
        self._add_field(form_body, form_card, "Model", self.model_id)
        form_body.addWidget(self.btn_fetch_models)
        form_body.addWidget(self.model_fetch_status)
        self._add_field(form_body, form_card, "Label hiển thị", self.model_label)
        self._add_field(form_body, form_card, "Tier", self.model_tier)
        form_body.addWidget(self.model_default)
        self.btn_apply_model = PrimaryPushButton("Cập nhật model", form_card)
        form_body.addWidget(self.btn_apply_model)
        split.addWidget(form_card, stretch=3, alignment=Qt.AlignmentFlag.AlignTop)

        layout.addLayout(split)
        layout.addStretch(1)
        self.model_api_combo.currentIndexChanged.connect(self._on_model_api_changed)
        self.model_list.currentRowChanged.connect(self._load_model_row)
        self.model_id.currentTextChanged.connect(self._on_model_id_changed)
        self.btn_add_model.clicked.connect(self._add_model)
        self.btn_del_model.clicked.connect(self._del_model)
        self.btn_apply_model.clicked.connect(self._apply_model)
        self.btn_fetch_models.clicked.connect(self._fetch_remote_models)
        self._reload_model_api_combo()
        return root

    def _build_routing(self) -> QWidget:
        root, layout = self._page_root()
        card, body = self._card(root, "Định tuyến model")
        self.route_mode = ComboBox(card)
        self.route_mode.addItems([ROUTING_MODE_AUTO, ROUTING_MODE_MANUAL])
        self.route_mode.setCurrentText(self._draft.ai.routing.mode or ROUTING_MODE_AUTO)
        self.complexity_enabled = CheckBox("Bật chọn model theo độ phức tạp", card)
        self.complexity_enabled.setChecked(self._draft.ai.routing.complexity_enabled)
        self.default_conn = ComboBox(card)
        self._reload_default_conn()
        self._add_field(body, card, "Mode", self.route_mode)
        body.addWidget(self.complexity_enabled)
        self._add_field(body, card, "Default API", self.default_conn)
        note = BodyLabel(
            "Ma trận task × complexity dùng tier fast / mid / strong (mặc định trong code).",
            card,
        )
        note.setWordWrap(True)
        note.setStyleSheet(f"color: {get_theme_tokens().text_secondary};")
        body.addWidget(note)
        layout.addWidget(card)
        layout.addStretch(1)
        return root

    def _build_copilot(self) -> QWidget:
        root, layout = self._page_root()
        card, body = self._card(root, "Giới hạn runtime Copilot")
        intro = BodyLabel(
            "Điều khiển vòng lặp agent (Planner → Composer → Critic). "
            "Giữ mặc định nếu không chắc; chỉnh khi agent bị lặp vòng hoặc hỏi clarify quá lâu.",
            card,
        )
        intro.setWordWrap(True)
        intro.setStyleSheet(f"color: {get_theme_tokens().text_secondary};")
        body.addWidget(intro)

        c = self._draft.copilot
        self.spin_summary = SpinBox(card)
        self.spin_summary.setRange(40, 400)
        self.spin_summary.setValue(c.summary_max_chars)
        self.spin_summary.setSuffix(" ký tự")
        self.spin_summary.setToolTip("Độ dài tối đa khi rút gọn tin nhắn cũ trong ngữ cảnh chat.")

        self.spin_debate = SpinBox(card)
        self.spin_debate.setRange(0, 8)
        self.spin_debate.setValue(c.debate_max_rounds)
        self.spin_debate.setSuffix(" vòng")
        self.spin_debate.setToolTip("Số vòng Critic phản biện Composer trước khi chốt kết quả.")

        self.spin_loop = SpinBox(card)
        self.spin_loop.setRange(1, 5)
        self.spin_loop.setValue(c.loop_repeat_limit)
        self.spin_loop.setSuffix(" lần")
        self.spin_loop.setToolTip("Dừng agent nếu cùng một bước lặp lại quá số lần này.")

        self.spin_clarify = SpinBox(card)
        self.spin_clarify.setRange(5_000, 300_000)
        self.spin_clarify.setSingleStep(1000)
        self.spin_clarify.setValue(c.clarify_timeout_ms)
        self.spin_clarify.setSuffix(" ms")
        self.spin_clarify.setToolTip(
            "Thời gian chờ người dùng trả lời hộp thoại làm rõ; hết giờ dùng đáp án mặc định."
        )

        self.spin_lean = SpinBox(card)
        self.spin_lean.setRange(500, 16_000)
        self.spin_lean.setValue(c.lean_tail_max_tokens)
        self.spin_lean.setSuffix(" token")
        self.spin_lean.setToolTip("Ngân sách token phần đuôi ngữ cảnh (lean tail) gửi lên LLM.")

        self._add_spin_field(
            body,
            card,
            "Độ dài tóm tắt tin nhắn",
            "Rút gọn lịch sử chat cũ để khỏi vượt context window.",
            self.spin_summary,
        )
        self._add_spin_field(
            body,
            card,
            "Số vòng tranh luận agent",
            "0 = bỏ Critic debate; 3 là mặc định cân bằng chất lượng/tốc độ.",
            self.spin_debate,
        )
        self._add_spin_field(
            body,
            card,
            "Giới hạn lặp bước",
            "Chống agent kẹt vòng lặp cùng một hành động.",
            self.spin_loop,
        )
        self._add_spin_field(
            body,
            card,
            "Timeout hỏi làm rõ",
            "60000 ms = 60 giây. Hết giờ → dùng câu trả lời mặc định.",
            self.spin_clarify,
            wide=True,
        )
        self._add_spin_field(
            body,
            card,
            "Lean tail tokens",
            "Token dành cho phần ngữ cảnh gần nhất gửi model.",
            self.spin_lean,
            wide=True,
        )
        roles_card, roles_body = self._card(root, "Optional crew roles")
        self.chk_analyzer = CheckBox("Analyzer (fail triage) — mặc định bật", roles_card)
        self.chk_analyzer.setChecked(c.analyzer_enabled)
        self.chk_optimizer = CheckBox("Optimizer (suite prune) — mặc định bật", roles_card)
        self.chk_optimizer.setChecked(c.optimizer_enabled)
        roles_body.addWidget(self.chk_analyzer)
        roles_body.addWidget(self.chk_optimizer)
        layout.addWidget(card)
        layout.addWidget(roles_card)
        layout.addStretch(1)
        return root

    def _build_mcp(self) -> QWidget:
        root, layout = self._page_root()
        card, body = self._card(root, "MCP servers")
        note = BodyLabel(
            "Bundled MCP (compress / ingest / memory / midscene / playwright): luôn bật. "
            "Playwright + Midscene external: bật mặc định. Figma/DB: opt-in.",
            card,
        )
        note.setWordWrap(True)
        note.setStyleSheet(f"color: {get_theme_tokens().text_secondary};")
        body.addWidget(note)
        self.mcp_pw = CheckBox("External Playwright MCP (auto-on)", card)
        self.mcp_pw.setChecked(self._draft.mcp.external.playwright)
        self.mcp_mid = CheckBox("External Midscene MCP (auto-on)", card)
        self.mcp_mid.setChecked(self._draft.mcp.external.midscene)
        self.mcp_figma = CheckBox("External Figma MCP (opt-in)", card)
        self.mcp_figma.setChecked(self._draft.mcp.external.figma)
        self.mcp_db = CheckBox("External DB MCP SELECT-only (opt-in)", card)
        self.mcp_db.setChecked(self._draft.mcp.external.db)
        body.addWidget(self.mcp_pw)
        body.addWidget(self.mcp_mid)
        body.addWidget(self.mcp_figma)
        body.addWidget(self.mcp_db)
        layout.addWidget(card)
        layout.addStretch(1)
        return root

    def validate(self) -> bool:
        self._persist_draft()
        self._accepted = True
        return True

    def _persist_draft(self) -> None:
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
        for conn in self._draft.ai.connections:
            conn.api_key_ref = seal_api_key_ref(conn.id, conn.api_key_ref)
        if primary is not None:
            self._draft.ai.api_key_ref = primary.api_key_ref
        if not self._draft.ai.routing.task_complexity_models:
            self._draft.ai.routing.task_complexity_models = default_task_complexity_matrix()
        self._draft.copilot = CopilotRuntimeSettings(
            summary_max_chars=self.spin_summary.value(),
            debate_max_rounds=self.spin_debate.value(),
            loop_repeat_limit=self.spin_loop.value(),
            clarify_timeout_ms=self.spin_clarify.value(),
            lean_tail_max_tokens=self.spin_lean.value(),
            analyzer_enabled=self.chk_analyzer.isChecked(),
            optimizer_enabled=self.chk_optimizer.isChecked(),
        )
        self._draft.mcp = McpSettings(
            external=McpExternalSettings(
                playwright=self.mcp_pw.isChecked(),
                midscene=self.mcp_mid.isChecked(),
                figma=self.mcp_figma.isChecked(),
                db=self.mcp_db.isChecked(),
            )
        )

    def _reload_api_list(self) -> None:
        self.api_list.clear()
        for conn in self._draft.ai.connections:
            self.api_list.addItem(QListWidgetItem(f"{conn.label} ({conn.provider})"))
        if self._draft.ai.connections:
            self.api_list.setCurrentRow(0)

    def _reload_default_conn(self) -> None:
        self.default_conn.clear()
        for conn in self._draft.ai.connections:
            self.default_conn.addItem(conn.label or conn.id, userData=conn.id)
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
            # Fluent ComboBox.addItem(text, icon=None, userData=None) — id must be userData=
            self.model_api_combo.addItem(conn.label or conn.id, userData=conn.id)
        if self.model_api_combo.count() > 0:
            self.model_api_combo.setCurrentIndex(0)
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
        connections: list[AiConnection] = list(self._draft.ai.connections)
        if not connections:
            return None
        conn_id = self.model_api_combo.currentData()
        if isinstance(conn_id, str) and conn_id:
            matched = [c for c in connections if c.id == conn_id]
            if matched:
                return matched[0]
        # Fallback: Fluent ComboBox index when userData missing
        idx = int(self.model_api_combo.currentIndex())
        if 0 <= idx < len(connections):
            return connections[idx]
        return connections[0]

    def _load_api_row(self, row: int) -> None:
        if row < 0 or row >= len(self._draft.ai.connections):
            return
        conn = self._draft.ai.connections[row]
        self.api_label.setText(conn.label)
        self.api_provider.setCurrentText(conn.provider or LLM_PROVIDER_OFFLINE)
        self.api_base.setText(conn.base_url)
        self.api_key.setText(resolve_api_key(conn.api_key_ref))
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
        conn.api_key_ref = seal_api_key_ref(conn.id, self.api_key.text())
        conn.enabled = self.api_enabled.isChecked()
        self._reload_api_list()
        self._reload_model_api_combo()
        self._reload_default_conn()

    def _on_model_api_changed(self) -> None:
        self._remote_model_ids = []
        self._fill_model_id_combo([])
        self._reload_model_list()
        self._fetch_remote_models()

    def _reload_model_list(self) -> None:
        self.model_list.clear()
        conn = self._models_conn()
        if conn is None:
            return
        for model in conn.models:
            mark = " *" if model.is_default else ""
            label = model.id.strip() or UI_SETTINGS_MODEL_UNSET_LABEL
            self.model_list.addItem(QListWidgetItem(f"{label} [{model.tier}]{mark}"))
        if conn.models:
            self.model_list.setCurrentRow(0)

    def _load_model_row(self, row: int) -> None:
        conn = self._models_conn()
        if conn is None or row < 0 or row >= len(conn.models):
            return
        model = conn.models[row]
        self._set_model_id_text(model.id)
        self.model_label.setText(model.label)
        self.model_tier.setCurrentText(model.tier)
        self.model_default.setChecked(model.is_default)

    def _add_model(self) -> None:
        conn = self._models_conn()
        if conn is None:
            return
        preferred = self.model_id.currentText().strip()
        configured = {m.id for m in conn.models}
        mid = ""
        if preferred and preferred in self._remote_model_ids and preferred not in configured:
            mid = preferred
        else:
            for candidate in self._remote_model_ids:
                if candidate not in configured:
                    mid = candidate
                    break
        conn.models.append(
            AiModelEntry(id=mid, label=mid, tier=LLM_TIER_MID, is_default=not conn.models)
        )
        self._reload_model_list()
        self.model_list.setCurrentRow(len(conn.models) - 1)
        if not self._remote_model_ids:
            self._fetch_remote_models()

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
        model.id = self.model_id.currentText().strip() or model.id
        model.label = self.model_label.text().strip() or model.id
        model.tier = self.model_tier.currentText()
        model.is_default = self.model_default.isChecked()
        if model.is_default:
            for other in conn.models:
                if other is not model:
                    other.is_default = False
        self._reload_model_list()

    def _on_model_id_changed(self, text: str) -> None:
        value = text.strip()
        if not value:
            return
        current_label = self.model_label.text().strip()
        if (
            not current_label
            or current_label.startswith("model-")
            or current_label == UI_SETTINGS_MODEL_UNSET_LABEL
        ):
            self.model_label.setText(value)

    def _on_model_id_commit(self) -> None:
        """Select matching API model on Enter — never invent dropdown items."""
        text = self.model_id.currentText().strip()
        if not text:
            return
        index = self.model_id.findText(text)
        if index >= 0:
            self.model_id.setCurrentIndex(index)

    def _set_model_id_text(self, model_id: str) -> None:
        self.model_id.blockSignals(True)
        index = self.model_id.findText(model_id) if model_id else -1
        if index >= 0:
            self.model_id.setCurrentIndex(index)
        else:
            # Show saved id without polluting the API-only catalog.
            self.model_id.setText(model_id)
        self.model_id.blockSignals(False)

    def _sync_model_id_completer(self, ids: list[str]) -> None:
        completer = QCompleter(ids, self.model_id)
        completer.setCaseSensitivity(Qt.CaseSensitivity.CaseInsensitive)
        completer.setFilterMode(Qt.MatchFlag.MatchContains)
        completer.setCompletionMode(QCompleter.CompletionMode.PopupCompletion)
        completer.setMaxVisibleItems(UI_SETTINGS_MODEL_COMPLETER_MAX_VISIBLE)
        self.model_id.setCompleter(completer)

    def _fill_model_id_combo(self, ids: list[str], *, keep: str = "") -> None:
        current = keep or self.model_id.currentText().strip()
        self.model_id.blockSignals(True)
        self.model_id.clear()
        self.model_id.addItems(ids)
        if current and self.model_id.findText(current) >= 0:
            self.model_id.setCurrentText(current)
        elif current:
            # Keep edit text for already-configured id; do not add to dropdown.
            self.model_id.setText(current)
        elif ids:
            self.model_id.setCurrentIndex(0)
        self.model_id.blockSignals(False)
        self._sync_model_id_completer(ids)

    def _fetch_remote_models(self) -> None:
        conn = self._models_conn()
        if conn is None:
            self.model_fetch_status.setText("Chọn một API trước.")
            return
        if (conn.provider or "").strip().lower() == LLM_PROVIDER_OFFLINE:
            self.model_fetch_status.setText("Provider offline — không có catalog từ API.")
            return
        # Prefer live form values if this connection is also selected on APIs tab.
        api_key = resolve_api_key(conn.api_key_ref)
        base_url = conn.base_url
        provider = conn.provider
        if self._current_conn() is not None and self._current_conn() is conn:
            typed = self.api_key.text().strip()
            if typed:
                api_key = typed
            base_url = self.api_base.text().strip() or base_url
            provider = self.api_provider.currentText() or provider
        if not api_key:
            self.model_fetch_status.setText("Thiếu API key — lưu key ở tab AI APIs rồi tải lại.")
            return
        if self._model_worker is not None and self._model_worker.isRunning():
            return
        self.btn_fetch_models.setEnabled(False)
        self.model_fetch_status.setText("Đang tải danh sách model…")
        worker = _ModelListWorker(provider, api_key, base_url, self)
        worker.succeeded.connect(self._on_remote_models_ok)
        worker.failed.connect(self._on_remote_models_err)
        worker.finished.connect(lambda: self.btn_fetch_models.setEnabled(True))
        self._model_worker = worker
        worker.start()

    def _on_remote_models_ok(self, ids: list[str]) -> None:
        self._remote_model_ids = list(ids)
        self._fill_model_id_combo(ids)
        if not ids:
            self.model_fetch_status.setText("API không trả về model nào.")
            return
        self.model_fetch_status.setText(
            f"Đã tải {len(ids)} model — gõ để tìm hoặc chọn trong danh sách."
        )

    def _on_remote_models_err(self, message: str) -> None:
        self.model_fetch_status.setText(f"Lỗi tải model: {message}")

    def result_settings(self) -> AppSettings | None:
        return copy.deepcopy(self._draft) if self._accepted else None
