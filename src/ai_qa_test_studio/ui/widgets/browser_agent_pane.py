"""Right column: Toggle Inspect + CDP Screencast card + Agent timeline."""

from __future__ import annotations

from PySide6.QtCore import Signal
from PySide6.QtWidgets import QHBoxLayout, QVBoxLayout, QWidget
from qfluentwidgets import (
    CaptionLabel,
    CardWidget,
    PrimaryPushButton,
    StrongBodyLabel,
    TogglePushButton,
)
from qfluentwidgets import (
    FluentIcon as FIF,
)

from ai_qa_test_studio.constants import (
    THEME_BG_PANEL,
    UI_EXPLORER_MIN_WIDTH,
    UI_VIEWPORT_HEIGHT,
    UI_VIEWPORT_MIN_HEIGHT,
    UI_VIEWPORT_WIDTH,
)
from ai_qa_test_studio.ui.widgets.agent_feed import AgentFeed
from ai_qa_test_studio.ui.widgets.embedded_browser import ScreencastViewport


class BrowserAgentPane(QWidget):
    launch_browser_clicked = Signal()
    inspect_toggled = Signal(bool)
    clicked_css = Signal(float, float)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setAccessibleName("Cột Browser và Agent")
        self.setMinimumWidth(UI_EXPLORER_MIN_WIDTH + 80)
        self.setStyleSheet(f"background-color: {THEME_BG_PANEL};")

        layout = QVBoxLayout(self)
        layout.setContentsMargins(10, 10, 10, 10)
        layout.setSpacing(10)

        bar = QHBoxLayout()
        bar.setSpacing(8)

        self.btn_inspect = TogglePushButton(FIF.SEARCH, "Inspect Element", self)
        self.btn_inspect.setToolTip("Rê chuột lên trình duyệt để bắt locator phần tử")
        self.btn_inspect.setAccessibleName("Bật chế độ Inspect Element")
        self.btn_inspect.toggled.connect(self._on_inspect_toggled)

        self.btn_launch = PrimaryPushButton(FIF.GLOBE, "Launch Browser", self)
        self.btn_launch.setAccessibleName("Mở trình duyệt")
        self.btn_launch.clicked.connect(self.launch_browser_clicked.emit)

        self.res_label = CaptionLabel(f"{UI_VIEWPORT_WIDTH}×{UI_VIEWPORT_HEIGHT} · CDP", self)

        bar.addWidget(self.btn_inspect)
        bar.addWidget(self.btn_launch)
        bar.addStretch(1)
        bar.addWidget(self.res_label)
        layout.addLayout(bar)

        self.viewport_card = CardWidget(self)
        self.viewport_card.setMinimumHeight(UI_VIEWPORT_MIN_HEIGHT)
        vp_layout = QVBoxLayout(self.viewport_card)
        vp_layout.setContentsMargins(8, 8, 8, 8)
        vp_layout.setSpacing(0)

        self.viewport = ScreencastViewport(self.viewport_card)
        self.viewport.set_chrome_visible(False)
        self.viewport.clicked_css.connect(self.clicked_css.emit)
        self.viewport.launch_requested.connect(self.launch_browser_clicked.emit)
        vp_layout.addWidget(self.viewport, stretch=1)
        layout.addWidget(self.viewport_card, stretch=3)

        agent_card = CardWidget(self)
        agent_layout = QVBoxLayout(agent_card)
        agent_layout.setContentsMargins(12, 10, 12, 10)
        agent_layout.setSpacing(6)
        agent_layout.addWidget(StrongBodyLabel("Luồng thực thi Agent (ReAct Live Feed)", agent_card))
        self.agent_feed = AgentFeed(agent_card)
        self.agent_feed.set_title_visible(False)
        agent_layout.addWidget(self.agent_feed)
        layout.addWidget(agent_card, stretch=2)

    @property
    def inspect_mode(self) -> bool:
        return self.viewport.inspect_mode

    def _on_inspect_toggled(self, checked: bool) -> None:
        self.viewport.set_inspect_mode(checked)
        self.btn_inspect.setText("Inspect ON" if checked else "Inspect Element")
        self.inspect_toggled.emit(checked)

    def set_placeholder(self, text: str) -> None:
        self.viewport.set_placeholder(text)

    def set_frame_png(self, png_bytes: bytes, viewport_w: int, viewport_h: int) -> None:
        self.viewport.set_frame_png(png_bytes, viewport_w, viewport_h)
        self.res_label.setText(f"{viewport_w}×{viewport_h} · CDP")
