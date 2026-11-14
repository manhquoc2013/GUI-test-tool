"""Right dock column — Pivot AI Copilot | Trình duyệt (splitter child)."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import QStackedWidget, QVBoxLayout, QWidget
from qfluentwidgets import FluentIcon as FIF
from qfluentwidgets import SegmentedWidget

from ai_qa_test_studio.constants import UI_DOCK_MAX_WIDTH, UI_DOCK_MIN_WIDTH
from ai_qa_test_studio.ui.theme import panel_stylesheet
from ai_qa_test_studio.ui.theme_bind import bind_widget_theme
from ai_qa_test_studio.ui.widgets.browser_agent_pane import BrowserAgentPane
from ai_qa_test_studio.ui.widgets.chat_pane import CopilotChatPane

TAB_COPILOT = "copilot"
TAB_BROWSER = "browser"


class RightDrawer(QWidget):
    drawer_opened = Signal()
    drawer_closed = Signal()

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("RightDock")
        self.setAccessibleName("Right Drawer Copilot Browser")
        self.setMinimumWidth(UI_DOCK_MIN_WIDTH)
        self.setMaximumWidth(UI_DOCK_MAX_WIDTH)
        self.setAttribute(Qt.WidgetAttribute.WA_StyledBackground, True)
        self._open = False

        layout = QVBoxLayout(self)
        layout.setContentsMargins(14, 12, 14, 12)
        layout.setSpacing(12)

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
        self._apply_theme_style()
        self._theme_slot = bind_widget_theme(self, self._apply_theme_style)

    def _apply_theme_style(self) -> None:
        self.setStyleSheet(panel_stylesheet("RightDock", border_left=True))

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
