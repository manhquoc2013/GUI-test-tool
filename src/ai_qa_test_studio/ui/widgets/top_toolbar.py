"""Unified top command bar — Wizard, URL, Sync+badge, Skill, Run/Stop, Export."""

from __future__ import annotations

from PySide6.QtCore import Signal
from PySide6.QtWidgets import QHBoxLayout, QSizePolicy, QWidget
from qfluentwidgets import (
    Action,
    ComboBox,
    DropDownPushButton,
    InfoBadge,
    LineEdit,
    PrimaryPushButton,
    PushButton,
    RoundMenu,
    ToolButton,
)
from qfluentwidgets import (
    FluentIcon as FIF,
)

from ai_qa_test_studio.constants import (
    DEFAULT_TARGET_URL,
    THEME_BG_TOPBAR,
    THEME_BORDER,
    THEME_RUN,
    UI_BTN_COPILOT_MIN_WIDTH,
    UI_BTN_EXPORT_MIN_WIDTH,
    UI_BTN_RUN_MIN_WIDTH,
    UI_BTN_STOP_SIZE,
    UI_BTN_SYNC_MIN_WIDTH,
    UI_TOP_BAR_HEIGHT,
)

_URL_PLACEHOLDER = "Nhập Target URL thật để Sync (vd: https://your-app.example/)..."


class UnifiedTopBar(QWidget):
    wizard_clicked = Signal()
    new_case_clicked = Signal()
    sync_url_clicked = Signal(str)
    run_clicked = Signal()
    stop_clicked = Signal()
    export_requested = Signal(str)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setAccessibleName("Thanh hành động trên cùng")
        self.setFixedHeight(UI_TOP_BAR_HEIGHT)
        self.setStyleSheet(
            f"background-color: {THEME_BG_TOPBAR}; border-bottom: 1px solid {THEME_BORDER};"
        )

        layout = QHBoxLayout(self)
        layout.setContentsMargins(12, 8, 12, 8)
        layout.setSpacing(8)

        self.btn_wizard = PrimaryPushButton(FIF.ROBOT, "Copilot", self)
        self.btn_wizard.setMinimumWidth(UI_BTN_COPILOT_MIN_WIDTH)
        self.btn_wizard.setToolTip(
            "Mở QA Copilot — Chat (Planner→Composer→Critic) + AI Wizard 3 bước"
        )
        self.btn_wizard.setAccessibleName("AI Copilot")
        self.btn_wizard.clicked.connect(self.wizard_clicked.emit)

        self.btn_new_case = ToolButton(FIF.ADD, self)
        self.btn_new_case.setToolTip("Thêm Test Case mới")
        self.btn_new_case.setAccessibleName("New Case")
        self.btn_new_case.clicked.connect(self.new_case_clicked.emit)

        layout.addWidget(self.btn_wizard)
        layout.addWidget(self.btn_new_case)

        self.url_input = LineEdit(self)
        self.url_input.setPlaceholderText(_URL_PLACEHOLDER)
        self.url_input.setText(DEFAULT_TARGET_URL)
        self.url_input.setClearButtonEnabled(True)
        self.url_input.setMinimumWidth(260)
        self.url_input.setAccessibleName("Ô nhập Target URL")
        self.url_input.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        layout.addWidget(self.url_input, stretch=1)

        sync_container = QWidget(self)
        sync_layout = QHBoxLayout(sync_container)
        sync_layout.setContentsMargins(0, 0, 0, 0)
        sync_layout.setSpacing(6)

        self.btn_sync = PushButton(FIF.SYNC, "Sync URL", sync_container)
        self.btn_sync.setMinimumWidth(UI_BTN_SYNC_MIN_WIDTH)
        self.btn_sync.setToolTip("Tự động ánh xạ các bước UNBOUND sang selector thật")
        self.btn_sync.setAccessibleName("Đồng bộ URL và bind selector")
        self.btn_sync.clicked.connect(
            lambda: self.sync_url_clicked.emit(self.url_input.text().strip())
        )

        self.unbound_badge = InfoBadge.warning("0 UNBOUND", sync_container)
        self.unbound_badge.setAccessibleName("Số bước UNBOUND")
        self.unbound_badge.setVisible(False)
        sync_layout.addWidget(self.btn_sync)
        sync_layout.addWidget(self.unbound_badge)
        layout.addWidget(sync_container)

        self.skill_combo = ComboBox(self)
        self.skill_combo.setMinimumWidth(190)
        self.skill_combo.setAccessibleName("Chọn Agent Skill")
        layout.addWidget(self.skill_combo)

        self.btn_run = PrimaryPushButton(FIF.PLAY, "Run", self)
        self.btn_run.setMinimumWidth(UI_BTN_RUN_MIN_WIDTH)
        self.btn_run.setSizePolicy(QSizePolicy.Policy.Fixed, QSizePolicy.Policy.Fixed)
        self.btn_run.setStyleSheet(
            f"PrimaryPushButton {{ background-color: {THEME_RUN}; border: none; }}"
        )
        self.btn_run.setAccessibleName("Run")
        self.btn_run.clicked.connect(self.run_clicked.emit)

        self.btn_stop = ToolButton(FIF.CLOSE, self)
        self.btn_stop.setFixedSize(UI_BTN_STOP_SIZE, UI_BTN_STOP_SIZE)
        self.btn_stop.setToolTip("Dừng thực thi (Esc)")
        self.btn_stop.setAccessibleName("Stop")
        self.btn_stop.clicked.connect(self.stop_clicked.emit)

        self.btn_export = DropDownPushButton(FIF.SHARE, "Export", self)
        self.btn_export.setMinimumWidth(UI_BTN_EXPORT_MIN_WIDTH)
        self.btn_export.setAccessibleName("Export")
        export_menu = RoundMenu(parent=self.btn_export)
        export_menu.addAction(
            Action(
                FIF.GLOBE,
                "HTML Report (Visual Replay)",
                triggered=lambda: self.export_requested.emit("html"),
            )
        )
        export_menu.addAction(
            Action(
                FIF.DOCUMENT,
                "Excel IEEE 829 + RTM",
                triggered=lambda: self.export_requested.emit("excel"),
            )
        )
        export_menu.addAction(
            Action(
                FIF.CODE,
                "Pytest Project + GitHub Actions",
                triggered=lambda: self.export_requested.emit("pytest"),
            )
        )
        self.btn_export.setMenu(export_menu)

        layout.addWidget(self.btn_run)
        layout.addWidget(self.btn_stop)
        layout.addWidget(self.btn_export)

    def set_skills(self, skills: list[str]) -> None:
        self.skill_combo.clear()
        self.skill_combo.addItems(skills)

    def set_unbound_count(self, count: int) -> None:
        if count > 0:
            self.unbound_badge.setText(f"{count} UNBOUND")
            self.unbound_badge.setVisible(True)
        else:
            self.unbound_badge.setVisible(False)
