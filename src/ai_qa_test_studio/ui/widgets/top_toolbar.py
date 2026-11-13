"""Airy Top Bar — hamburger, Tạo bằng AI, URL, Sync(N), Run, Copilot toggle, ⋯."""

from __future__ import annotations

from PySide6.QtCore import Signal
from PySide6.QtWidgets import QHBoxLayout, QSizePolicy, QWidget
from qfluentwidgets import (
    Action,
    ComboBox,
    InfoBadge,
    LineEdit,
    PrimaryPushButton,
    PushButton,
    RoundMenu,
    ToolButton,
    TransparentToolButton,
)
from qfluentwidgets import FluentIcon as FIF

from ai_qa_test_studio.constants import (
    DEFAULT_TARGET_URL,
    THEME_BG_CANVAS,
    THEME_BORDER_SUBTLE,
    THEME_STATUS_BOUND,
    UI_BTN_COPILOT_MIN_WIDTH,
    UI_BTN_RUN_MIN_WIDTH,
    UI_BTN_SYNC_MIN_WIDTH,
    UI_TOP_BAR_HEIGHT,
    UI_URL_INPUT_MAX_WIDTH,
    UI_URL_INPUT_MIN_WIDTH,
)

_URL_PLACEHOLDER = "Target URL…"


class UnifiedTopBar(QWidget):
    sidebar_toggled = Signal()
    wizard_clicked = Signal()
    new_case_clicked = Signal()
    sync_url_clicked = Signal(str)
    run_clicked = Signal()
    stop_clicked = Signal()
    copilot_toggled = Signal(bool)
    export_requested = Signal(str)
    settings_requested = Signal()

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setAccessibleName("Thanh hành động trên cùng")
        self.setFixedHeight(UI_TOP_BAR_HEIGHT)
        self.setStyleSheet(
            f"background-color: {THEME_BG_CANVAS}; border-bottom: 1px solid {THEME_BORDER_SUBTLE};"
        )
        self._running = False
        self._copilot_on = False

        layout = QHBoxLayout(self)
        layout.setContentsMargins(12, 8, 12, 8)
        layout.setSpacing(8)

        self.btn_hamburger = TransparentToolButton(FIF.MENU, self)
        self.btn_hamburger.setToolTip("Thu gọn / mở Sidebar (Ctrl+\\)")
        self.btn_hamburger.setAccessibleName("Toggle Sidebar")
        self.btn_hamburger.clicked.connect(self.sidebar_toggled.emit)
        layout.addWidget(self.btn_hamburger)

        self.btn_wizard = PushButton(FIF.ROBOT, "Tạo bằng AI", self)
        self.btn_wizard.setMinimumWidth(UI_BTN_COPILOT_MIN_WIDTH)
        self.btn_wizard.setToolTip("Mở Copilot AI Wizard")
        self.btn_wizard.setAccessibleName("Tạo bằng AI")
        self.btn_wizard.clicked.connect(self.wizard_clicked.emit)
        layout.addWidget(self.btn_wizard)

        layout.addStretch(1)

        self.url_input = LineEdit(self)
        self.url_input.setPlaceholderText(_URL_PLACEHOLDER)
        self.url_input.setText(DEFAULT_TARGET_URL)
        self.url_input.setClearButtonEnabled(True)
        self.url_input.setMinimumWidth(UI_URL_INPUT_MIN_WIDTH)
        self.url_input.setMaximumWidth(UI_URL_INPUT_MAX_WIDTH)
        self.url_input.setAccessibleName("Ô nhập Target URL")
        self.url_input.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        layout.addWidget(self.url_input, stretch=1)

        self.btn_sync = PushButton(FIF.SYNC, "Sync (0)", self)
        self.btn_sync.setMinimumWidth(UI_BTN_SYNC_MIN_WIDTH)
        self.btn_sync.setToolTip("Tự động ánh xạ các bước UNBOUND sang selector thật")
        self.btn_sync.setAccessibleName("Đồng bộ URL và bind selector")
        self.btn_sync.clicked.connect(
            lambda: self.sync_url_clicked.emit(self.url_input.text().strip())
        )
        layout.addWidget(self.btn_sync)

        self.unbound_badge = InfoBadge.warning("0 UNBOUND", self)
        self.unbound_badge.setAccessibleName("Số bước UNBOUND")
        self.unbound_badge.setVisible(False)
        layout.addWidget(self.unbound_badge)

        layout.addStretch(1)

        self.btn_run = PrimaryPushButton(FIF.PLAY, "Chạy", self)
        self.btn_run.setMinimumWidth(UI_BTN_RUN_MIN_WIDTH)
        self.btn_run.setStyleSheet(
            f"PrimaryPushButton {{ background-color: {THEME_STATUS_BOUND}; border: none; }}"
        )
        self.btn_run.setAccessibleName("Chạy hoặc Dừng")
        self.btn_run.clicked.connect(self._on_run_primary)
        layout.addWidget(self.btn_run)

        self.btn_copilot = PushButton(FIF.CHAT, "Copilot", self)
        self.btn_copilot.setCheckable(True)
        self.btn_copilot.setMinimumWidth(88)
        self.btn_copilot.setToolTip("Bật/tắt Copilot drawer (Ctrl+I)")
        self.btn_copilot.setAccessibleName("Toggle Copilot drawer")
        self.btn_copilot.toggled.connect(self._on_copilot_toggled)
        layout.addWidget(self.btn_copilot)

        self.btn_more = ToolButton(FIF.MORE, self)
        self.btn_more.setAccessibleName("Menu thêm")
        more_menu = RoundMenu(parent=self.btn_more)
        more_menu.addAction(
            Action(FIF.ADD, "New Case", triggered=self.new_case_clicked.emit)
        )
        more_menu.addSeparator()
        more_menu.addAction(
            Action(FIF.GLOBE, "Export HTML", triggered=lambda: self.export_requested.emit("html"))
        )
        more_menu.addAction(
            Action(
                FIF.DOCUMENT,
                "Export Excel",
                triggered=lambda: self.export_requested.emit("excel"),
            )
        )
        more_menu.addAction(
            Action(
                FIF.CODE,
                "Export Pytest",
                triggered=lambda: self.export_requested.emit("pytest"),
            )
        )
        more_menu.addSeparator()
        more_menu.addAction(
            Action(FIF.SETTING, "System Settings", triggered=self.settings_requested.emit)
        )
        def _open_more() -> None:
            more_menu.exec(self.btn_more.mapToGlobal(self.btn_more.rect().bottomLeft()))

        self.btn_more.clicked.connect(_open_more)
        layout.addWidget(self.btn_more)

        # Hidden skill combo — kept for MainWindow / run wiring
        self.skill_combo = ComboBox(self)
        self.skill_combo.setVisible(False)
        self.skill_combo.setAccessibleName("Chọn Agent Skill")

        # Compat aliases
        self.btn_stop = self.btn_run
        self.btn_export = self.btn_more
        self.btn_new_case = self.btn_more

    def _on_run_primary(self) -> None:
        if self._running:
            self.stop_clicked.emit()
        else:
            self.run_clicked.emit()

    def _on_copilot_toggled(self, checked: bool) -> None:
        self._copilot_on = checked
        self.copilot_toggled.emit(checked)

    def set_running(self, running: bool) -> None:
        self._running = running
        if running:
            self.btn_run.setText("Dừng")
            self.btn_run.setIcon(FIF.CLOSE.icon())
        else:
            self.btn_run.setText("Chạy")
            self.btn_run.setIcon(FIF.PLAY.icon())

    def set_copilot_checked(self, checked: bool) -> None:
        self.btn_copilot.blockSignals(True)
        self.btn_copilot.setChecked(checked)
        self.btn_copilot.blockSignals(False)
        self._copilot_on = checked

    def set_skills(self, skills: list[str]) -> None:
        self.skill_combo.clear()
        self.skill_combo.addItems(skills)

    def set_unbound_count(self, count: int) -> None:
        self.btn_sync.setText(f"Sync ({count})")
        if count > 0:
            self.unbound_badge.setText(f"{count} UNBOUND")
            self.unbound_badge.setVisible(True)
        else:
            self.unbound_badge.setVisible(False)
