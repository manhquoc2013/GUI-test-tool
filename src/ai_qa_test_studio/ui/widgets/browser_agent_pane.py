"""Right column: Inspect + CDP screencast + Segmented Copilot | Agent Feed."""

from __future__ import annotations

from PySide6.QtCore import Signal
from PySide6.QtWidgets import QHBoxLayout, QStackedWidget, QVBoxLayout, QWidget
from qfluentwidgets import (
    CaptionLabel,
    CardWidget,
    PrimaryPushButton,
    SegmentedWidget,
    TogglePushButton,
)
from qfluentwidgets import (
    FluentIcon as FIF,
)

from ai_qa_test_studio.constants import (
    THEME_BG_PANEL,
    UI_BROWSER_PANE_MIN_WIDTH,
    UI_BTN_INSPECT_MIN_WIDTH,
    UI_BTN_LAUNCH_MIN_WIDTH,
    UI_LABEL_BROWSER,
    UI_LABEL_INSPECT,
    UI_LABEL_INSPECT_ON,
    UI_VIEWPORT_HEIGHT,
    UI_VIEWPORT_MIN_HEIGHT,
    UI_VIEWPORT_WIDTH,
)
from ai_qa_test_studio.ui.widgets.agent_feed import AgentFeed
from ai_qa_test_studio.ui.widgets.chat_pane import CopilotChatPane
from ai_qa_test_studio.ui.widgets.embedded_browser import ScreencastViewport


class BrowserAgentPane(QWidget):
    launch_browser_clicked = Signal()
    inspect_toggled = Signal(bool)
    clicked_css = Signal(float, float)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setAccessibleName("Cột Browser và Agent")
        self.setMinimumWidth(UI_BROWSER_PANE_MIN_WIDTH)
        self.setStyleSheet(f"background-color: {THEME_BG_PANEL};")

        layout = QVBoxLayout(self)
        layout.setContentsMargins(10, 10, 10, 10)
        layout.setSpacing(10)

        bar = QHBoxLayout()
        bar.setSpacing(8)

        self.btn_inspect = TogglePushButton(FIF.SEARCH, UI_LABEL_INSPECT, self)
        self.btn_inspect.setMinimumWidth(UI_BTN_INSPECT_MIN_WIDTH)
        self.btn_inspect.setToolTip("Inspect Element — Rê chuột lên trình duyệt để bắt locator")
        self.btn_inspect.setAccessibleName("Bật chế độ Inspect Element")
        self.btn_inspect.toggled.connect(self._on_inspect_toggled)

        self.btn_launch = PrimaryPushButton(FIF.GLOBE, UI_LABEL_BROWSER, self)
        self.btn_launch.setMinimumWidth(UI_BTN_LAUNCH_MIN_WIDTH)
        self.btn_launch.setToolTip("Launch Browser — Khởi chạy Chromium CDP")
        self.btn_launch.setAccessibleName("Mở trình duyệt")
        self.btn_launch.clicked.connect(self.launch_browser_clicked.emit)

        self.res_label = CaptionLabel(f"{UI_VIEWPORT_WIDTH}×{UI_VIEWPORT_HEIGHT}", self)

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

        lower = CardWidget(self)
        lower_layout = QVBoxLayout(lower)
        lower_layout.setContentsMargins(12, 10, 12, 10)
        lower_layout.setSpacing(6)

        self.pivot = SegmentedWidget(lower)
        self.pivot.setAccessibleName("Chuyển Copilot hoặc Agent Feed")
        self.pivot.addItem(routeKey="copilot", text="AI Copilot", icon=FIF.ROBOT)
        self.pivot.addItem(routeKey="feed", text="Agent Feed", icon=FIF.CHAT)
        self.pivot.setCurrentItem("copilot")
        lower_layout.addWidget(self.pivot)

        self.stack = QStackedWidget(lower)
        self.copilot = CopilotChatPane(self.stack)
        feed_wrap = QWidget(self.stack)
        feed_layout = QVBoxLayout(feed_wrap)
        feed_layout.setContentsMargins(0, 0, 0, 0)
        self.agent_feed = AgentFeed(feed_wrap)
        self.agent_feed.set_title_visible(False)
        feed_layout.addWidget(self.agent_feed)
        self.stack.addWidget(self.copilot)
        self.stack.addWidget(feed_wrap)
        lower_layout.addWidget(self.stack, stretch=1)
        layout.addWidget(lower, stretch=2)

        self.pivot.currentItemChanged.connect(self._on_pivot_changed)

    def _on_pivot_changed(self, key: str) -> None:
        self.stack.setCurrentIndex(0 if key == "copilot" else 1)

    def show_copilot(self, *, compose: bool = False) -> None:
        self.pivot.setCurrentItem("copilot")
        self.stack.setCurrentIndex(0)
        if compose:
            self.copilot.focus_compose()
        else:
            self.copilot.focus_case_chat()

    @property
    def inspect_mode(self) -> bool:
        return self.viewport.inspect_mode

    def _on_inspect_toggled(self, checked: bool) -> None:
        self.viewport.set_inspect_mode(checked)
        self.btn_inspect.setText(UI_LABEL_INSPECT_ON if checked else UI_LABEL_INSPECT)
        self.inspect_toggled.emit(checked)

    def set_placeholder(self, text: str) -> None:
        self.viewport.set_placeholder(text)

    def set_frame_png(self, png_bytes: bytes, viewport_w: int, viewport_h: int) -> None:
        self.viewport.set_frame_png(png_bytes, viewport_w, viewport_h)
        self.res_label.setText(f"{viewport_w}×{viewport_h}")
