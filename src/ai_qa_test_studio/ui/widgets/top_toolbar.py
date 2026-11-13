"""Airy Top Bar — hamburger, Tạo bằng AI, URL, Sync(N), Run, Copilot toggle, ⋯."""

from __future__ import annotations

from PySide6.QtCore import QRectF, Signal
from PySide6.QtGui import QPainter
from PySide6.QtWidgets import QHBoxLayout, QPushButton, QSizePolicy, QWidget
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
    UI_BREAKPOINT_MEDIUM,
    UI_BREAKPOINT_WIDE,
    UI_BTN_COPILOT_MIN_WIDTH,
    UI_BTN_RUN_MIN_WIDTH,
    UI_BTN_SYNC_MIN_WIDTH,
    UI_FLUENT_ICON_LEFT_PAD,
    UI_LABEL_COPILOT,
    UI_LABEL_RUN,
    UI_LABEL_STOP,
    UI_LABEL_WIZARD,
    UI_LABEL_WIZARD_SHORT,
    UI_TOP_BAR_HEIGHT,
    UI_URL_INPUT_MAX_WIDTH,
    UI_URL_INPUT_MAX_WIDTH_COMPACT,
    UI_URL_INPUT_MAX_WIDTH_NARROW,
    UI_URL_INPUT_MIN_WIDTH,
    UI_URL_INPUT_MIN_WIDTH_COMPACT,
    UI_URL_INPUT_MIN_WIDTH_NARROW,
)

_URL_PLACEHOLDER = "Target URL…"


class _IconLeftPrimaryButton(PrimaryPushButton):
    """Fluent centers icon when minWidth > content — pin icon to the left padding."""

    def paintEvent(self, e) -> None:  # type: ignore[no-untyped-def]
        QPushButton.paintEvent(self, e)
        if self.icon().isNull():
            return
        painter = QPainter(self)
        painter.setRenderHints(
            QPainter.RenderHint.Antialiasing | QPainter.RenderHint.SmoothPixmapTransform
        )
        if not self.isEnabled():
            painter.setOpacity(0.3628)
        elif self.isPressed:
            painter.setOpacity(0.786)
        w = self.iconSize().width()
        h = self.iconSize().height()
        y = (self.height() - h) / 2
        x = float(UI_FLUENT_ICON_LEFT_PAD)
        if self.isRightToLeft():
            x = float(self.width() - w - UI_FLUENT_ICON_LEFT_PAD)
        self._drawIcon(self._icon, painter, QRectF(x, y, w, h))


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
        self._unbound_count = 0
        self._density = "wide"

        layout = QHBoxLayout(self)
        layout.setContentsMargins(12, 8, 12, 8)
        layout.setSpacing(8)

        self.btn_hamburger = TransparentToolButton(FIF.MENU, self)
        self.btn_hamburger.setToolTip("Thu gọn / mở Sidebar (Ctrl+\\)")
        self.btn_hamburger.setAccessibleName("Toggle Sidebar")
        self.btn_hamburger.clicked.connect(lambda: self.sidebar_toggled.emit())
        layout.addWidget(self.btn_hamburger)

        self.btn_wizard = PushButton(FIF.ROBOT, UI_LABEL_WIZARD, self)
        self.btn_wizard.setMinimumWidth(UI_BTN_COPILOT_MIN_WIDTH)
        self.btn_wizard.setToolTip("Mở Copilot AI Wizard")
        self.btn_wizard.setAccessibleName("Tạo bằng AI")
        self.btn_wizard.clicked.connect(lambda: self.wizard_clicked.emit())
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

        self.btn_run = _IconLeftPrimaryButton(FIF.PLAY, UI_LABEL_RUN, self)
        self.btn_run.setMinimumWidth(UI_BTN_RUN_MIN_WIDTH)
        self.btn_run.setObjectName("airyRunButton")
        self.btn_run.setAccessibleName("Chạy hoặc Dừng")
        self.btn_run.clicked.connect(self._on_run_primary)
        layout.addWidget(self.btn_run)

        self.btn_copilot = PushButton(FIF.CHAT, UI_LABEL_COPILOT, self)
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
            Action(FIF.ADD, "New Case", triggered=lambda: self.new_case_clicked.emit())
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
            Action(
                FIF.SETTING,
                "System Settings",
                triggered=lambda: self.settings_requested.emit(),
            )
        )

        def _open_more() -> None:
            more_menu.exec(self.btn_more.mapToGlobal(self.btn_more.rect().bottomLeft()))

        self.btn_more.clicked.connect(_open_more)
        layout.addWidget(self.btn_more)

        self.skill_combo = ComboBox(self)
        self.skill_combo.setVisible(False)
        self.skill_combo.setAccessibleName("Chọn Agent Skill")

        self.btn_stop = self.btn_run
        self.btn_export = self.btn_more
        self.btn_new_case = self.btn_more

    def apply_density(self, width: int) -> None:
        if width >= UI_BREAKPOINT_WIDE:
            density = "wide"
            url_min, url_max = UI_URL_INPUT_MIN_WIDTH, UI_URL_INPUT_MAX_WIDTH
            wizard, copilot = UI_LABEL_WIZARD, UI_LABEL_COPILOT
            sync_min = UI_BTN_SYNC_MIN_WIDTH
        elif width >= UI_BREAKPOINT_MEDIUM:
            density = "medium"
            url_min, url_max = UI_URL_INPUT_MIN_WIDTH_COMPACT, UI_URL_INPUT_MAX_WIDTH_COMPACT
            wizard, copilot = UI_LABEL_WIZARD_SHORT, UI_LABEL_COPILOT
            sync_min = 88
        else:
            density = "narrow"
            url_min, url_max = UI_URL_INPUT_MIN_WIDTH_NARROW, UI_URL_INPUT_MAX_WIDTH_NARROW
            wizard, copilot = "", ""
            sync_min = 72

        self._density = density
        self.url_input.setMinimumWidth(url_min)
        self.url_input.setMaximumWidth(url_max)
        self.btn_wizard.setText(wizard)
        self.btn_wizard.setMinimumWidth(36 if not wizard else UI_BTN_COPILOT_MIN_WIDTH)
        self.btn_copilot.setText(copilot)
        self.btn_copilot.setMinimumWidth(36 if not copilot else 88)
        self.btn_sync.setMinimumWidth(sync_min)
        self.set_unbound_count(self._unbound_count)
        self._refresh_sync_label()

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
            self.btn_run.setText(UI_LABEL_STOP)
            self.btn_run.setIcon(FIF.CLOSE)
        else:
            self.btn_run.setText(UI_LABEL_RUN)
            self.btn_run.setIcon(FIF.PLAY)

    def set_copilot_checked(self, checked: bool) -> None:
        self.btn_copilot.blockSignals(True)
        self.btn_copilot.setChecked(checked)
        self.btn_copilot.blockSignals(False)
        self._copilot_on = checked

    def set_skills(self, skills: list[str]) -> None:
        self.skill_combo.clear()
        self.skill_combo.addItems(skills)

    def set_unbound_count(self, count: int) -> None:
        self._unbound_count = count
        self._refresh_sync_label()
        if count > 0:
            label = f"{count}" if self._density == "narrow" else f"{count} UNBOUND"
            self.unbound_badge.setText(label)
            self.unbound_badge.setVisible(True)
        else:
            self.unbound_badge.setVisible(False)

    def _refresh_sync_label(self) -> None:
        if self._density == "narrow":
            self.btn_sync.setText(str(self._unbound_count))
        else:
            self.btn_sync.setText(f"Sync ({self._unbound_count})")
