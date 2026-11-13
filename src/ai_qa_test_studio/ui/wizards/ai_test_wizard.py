"""Legacy modal shell — AI Wizard now lives inside Copilot (`ComposeWizardPanel`).

Kept for optional MessageBoxBase hosts; primary UX is Copilot → tab «AI Wizard».
"""

from __future__ import annotations

from PySide6.QtWidgets import QWidget
from qfluentwidgets import CaptionLabel, MessageBoxBase, SubtitleLabel

from ai_qa_test_studio.constants import UI_WIZARD_MIN_HEIGHT, UI_WIZARD_MIN_WIDTH
from ai_qa_test_studio.core.models import TestScenario
from ai_qa_test_studio.ui.widgets.compose_wizard_panel import ComposeWizardPanel


class AiTestWizard(MessageBoxBase):
    def __init__(self, parent: QWidget) -> None:
        super().__init__(parent)
        self.setWindowTitle("AI Wizard (Copilot)")
        self._accepted: list[TestScenario] = []
        self._url = ""

        self.widget.setMinimumWidth(UI_WIZARD_MIN_WIDTH)
        self.widget.setMinimumHeight(UI_WIZARD_MIN_HEIGHT)

        self.viewLayout.addWidget(SubtitleLabel("AI Wizard · trong Copilot", self.widget))
        self.viewLayout.addWidget(
            CaptionLabel(
                "Khuyến nghị: mở từ Top Bar Copilot → tab AI Wizard. "
                "Dialog này chỉ là vỏ tương thích.",
                self.widget,
            )
        )
        self.panel = ComposeWizardPanel(self.widget)
        self.panel.drafts_ready.connect(self._on_drafts)
        self.panel.cancelled.connect(self.reject)
        # Hide panel's own nav cancel duplication inside dialog chrome
        self.panel.btn_cancel.setVisible(False)
        self.viewLayout.addWidget(self.panel, stretch=1)

        self.cancelButton.setText("Đóng")
        self.yesButton.setVisible(False)

    def _on_drafts(self, scenarios: list[TestScenario], start_url: str) -> None:
        self._accepted = [s for s in scenarios if isinstance(s, TestScenario)]
        self._url = start_url
        self.accept()

    def result_scenarios(self) -> list[TestScenario]:
        return list(self._accepted)

    def source_url(self) -> str:
        return self._url

    def validate(self) -> bool:
        return False
