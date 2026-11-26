"""Right dock column — Pivot AI Copilot | Trình duyệt | Kết quả (splitter child)."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import QVBoxLayout, QWidget
from qfluentwidgets import FluentIcon as FIF
from qfluentwidgets import PopUpAniStackedWidget, SegmentedWidget

from ai_qa_test_studio.constants import (
    UI_DOCK_MAX_WIDTH,
    UI_DOCK_MIN_WIDTH,
    UI_EVIDENCE_TAB_LABEL,
    UI_MOTION_NORMAL_MS,
)
from ai_qa_test_studio.ui.motion import motion_enabled
from ai_qa_test_studio.ui.theme import panel_stylesheet
from ai_qa_test_studio.ui.theme_bind import bind_widget_theme
from ai_qa_test_studio.ui.widgets.browser_agent_pane import BrowserAgentPane
from ai_qa_test_studio.ui.widgets.chat_pane import CopilotChatPane
from ai_qa_test_studio.ui.widgets.evidence_panel import EvidencePanel

TAB_COPILOT = "copilot"
TAB_BROWSER = "browser"
TAB_EVIDENCE = "evidence"

_TAB_INDEX = {TAB_COPILOT: 0, TAB_BROWSER: 1, TAB_EVIDENCE: 2}


class RightDrawer(QWidget):
    drawer_opened = Signal()
    drawer_closed = Signal()

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("RightDock")
        self.setAccessibleName("Right Drawer Copilot Browser Evidence")
        self.setMinimumWidth(UI_DOCK_MIN_WIDTH)
        self.setMaximumWidth(UI_DOCK_MAX_WIDTH)
        self.setAttribute(Qt.WidgetAttribute.WA_StyledBackground, True)
        self._open = False

        layout = QVBoxLayout(self)
        layout.setContentsMargins(14, 12, 14, 12)
        layout.setSpacing(12)

        self.pivot = SegmentedWidget(self)
        self.pivot.setAccessibleName("Chuyển AI Copilot, Trình duyệt hoặc Kết quả")
        self.pivot.addItem(routeKey=TAB_COPILOT, text="AI Copilot", icon=FIF.ROBOT)
        self.pivot.addItem(routeKey=TAB_BROWSER, text="Trình duyệt", icon=FIF.GLOBE)
        self.pivot.addItem(
            routeKey=TAB_EVIDENCE, text=UI_EVIDENCE_TAB_LABEL, icon=FIF.HISTORY
        )
        self.pivot.setCurrentItem(TAB_COPILOT)
        layout.addWidget(self.pivot)

        self.stack = PopUpAniStackedWidget(self)
        self.copilot = CopilotChatPane(self.stack)
        self.browser = BrowserAgentPane(self.stack)
        self.evidence = EvidencePanel(self.stack)
        self.stack.addWidget(self.copilot)
        self.stack.addWidget(self.browser)
        self.stack.addWidget(self.evidence)
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
        self._switch_stack(_TAB_INDEX.get(key, 0))

    def _switch_stack(self, index: int) -> None:
        """Single animated switch; honours the "Giảm chuyển động" setting."""
        self.stack.setAnimationEnabled(motion_enabled())
        self.stack.setCurrentIndex(index, duration=UI_MOTION_NORMAL_MS)

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
        if tab == TAB_BROWSER:
            key = TAB_BROWSER
        elif tab == TAB_EVIDENCE:
            key = TAB_EVIDENCE
        else:
            key = TAB_COPILOT
        self.pivot.setCurrentItem(key)
        # setCurrentItem already animated the stack through _on_pivot_changed;
        # a second setCurrentIndex would abort that animation and snap.
        if self.stack.currentIndex() != _TAB_INDEX[key]:
            self._switch_stack(_TAB_INDEX[key])
        if key == TAB_COPILOT:
            if compose:
                self.copilot.focus_compose()
            else:
                self.copilot.focus_case_chat()

    def show_copilot(self, *, compose: bool = False) -> None:
        self.open_drawer(TAB_COPILOT, compose=compose)

    def show_browser(self) -> None:
        self.open_drawer(TAB_BROWSER)

    def show_evidence(self) -> None:
        self.open_drawer(TAB_EVIDENCE)
