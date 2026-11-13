"""Right slide-over drawer — Pivot AI Copilot | Trình duyệt."""

from __future__ import annotations

from PySide6.QtCore import Signal
from PySide6.QtWidgets import QStackedWidget, QVBoxLayout, QWidget
from qfluentwidgets import FluentIcon as FIF
from qfluentwidgets import SegmentedWidget

from ai_qa_test_studio.constants import (
    THEME_BG_SURFACE,
    THEME_BORDER_SUBTLE,
    UI_DRAWER_WIDTH,
)
from ai_qa_test_studio.ui.widgets.browser_agent_pane import BrowserAgentPane
from ai_qa_test_studio.ui.widgets.chat_pane import CopilotChatPane

TAB_COPILOT = "copilot"
TAB_BROWSER = "browser"


class RightDrawer(QWidget):
    drawer_opened = Signal()
    drawer_closed = Signal()

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setAccessibleName("Right Drawer Copilot Browser")
        self.setMinimumWidth(0)
        self.setMaximumWidth(UI_DRAWER_WIDTH + 40)
        self.setStyleSheet(
            f"background-color: {THEME_BG_SURFACE};"
            f"border-left: 1px solid {THEME_BORDER_SUBTLE};"
        )
        self._open = False

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 8, 0, 0)
        layout.setSpacing(6)

        self.pivot = SegmentedWidget(self)
        self.pivot.setAccessibleName("Chuyển AI Copilot hoặc Trình duyệt")
        self.pivot.addItem(routeKey=TAB_COPILOT, text="AI Copilot", icon=FIF.ROBOT)
        self.pivot.addItem(routeKey=TAB_BROWSER, text="Trình duyệt", icon=FIF.GLOBE)
        self.pivot.setCurrentItem(TAB_COPILOT)
        layout.addWidget(self.pivot)

        self.stack = QStackedWidget(self)
        self.copilot = CopilotChatPane(self.stack)
        self.browser = BrowserAgentPane(self.stack)
        self.stack.addWidget(self.copilot)
        self.stack.addWidget(self.browser)
        layout.addWidget(self.stack, stretch=1)

        self.pivot.currentItemChanged.connect(self._on_pivot_changed)

    @property
    def is_open(self) -> bool:
        return self._open

    def _on_pivot_changed(self, key: str) -> None:
        self.stack.setCurrentIndex(0 if key == TAB_COPILOT else 1)

    def open_drawer(self, tab: str = TAB_COPILOT, *, compose: bool = False) -> None:
        was_open = self._open
        self._open = True
        self.show()
        self.set_tab(tab, compose=compose)
        if not was_open:
            self.drawer_opened.emit()

    def close_drawer(self) -> None:
        if not self._open:
            return
        self._open = False
        self.hide()
        self.drawer_closed.emit()

    def toggle(self) -> None:
        if self._open:
            self.close_drawer()
        else:
            self.open_drawer(TAB_COPILOT)

    def set_tab(self, tab: str, *, compose: bool = False) -> None:
        key = TAB_COPILOT if tab != TAB_BROWSER else TAB_BROWSER
        self.pivot.setCurrentItem(key)
        self.stack.setCurrentIndex(0 if key == TAB_COPILOT else 1)
        if key == TAB_COPILOT:
            if compose:
                self.copilot.focus_compose()
            else:
                self.copilot.focus_case_chat()

    def show_copilot(self, *, compose: bool = False) -> None:
        self.open_drawer(TAB_COPILOT, compose=compose)

    def show_browser(self) -> None:
        self.open_drawer(TAB_BROWSER)
