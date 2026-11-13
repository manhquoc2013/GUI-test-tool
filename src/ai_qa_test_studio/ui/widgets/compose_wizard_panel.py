"""AI Wizard 3-step flow embedded inside Copilot (Compose mode)."""

from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QFileDialog,
    QHBoxLayout,
    QListWidgetItem,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)
from qfluentwidgets import (
    BodyLabel,
    CaptionLabel,
    CardWidget,
    LineEdit,
    ListWidget,
    PlainTextEdit,
    PrimaryPushButton,
    PushButton,
    StrongBodyLabel,
    ToolButton,
)
from qfluentwidgets import (
    FluentIcon as FIF,
)

from ai_qa_test_studio.agent.spec_generator import SpecGenerator
from ai_qa_test_studio.constants import (
    DEFAULT_TARGET_URL,
    ICON_COLOR_WARN,
    THEME_BG_PANEL,
    UI_MARKDOWN_PREVIEW_CHARS,
    UI_WIZARD_STEP_COUNT,
)
from ai_qa_test_studio.core.models import TestScenario
from ai_qa_test_studio.ui.widgets.step_progress_bar import StepProgressBar

_URL_HINT = "https://your-app.example/ (tuỳ chọn — dùng cho Sync/Run)"
_STEP_TITLES = ("Nhập liệu", "Chọn Scenario", "Duyệt draft")


class ComposeWizardPanel(QWidget):
    """In-pane Wizard — part of Copilot Compose, not a separate modal entry."""

    drafts_ready = Signal(list, str)  # scenarios, start_url
    cancelled = Signal()

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setAccessibleName("AI Wizard trong Copilot")
        self.generator = SpecGenerator()
        self.scenarios: list[TestScenario] = []
        self._step = 0

        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(8)

        layout.addWidget(
            CaptionLabel(
                "AI Wizard · 3 bước tạo draft UNBOUND (nằm trong Copilot)",
                self,
            )
        )
        self.progress = StepProgressBar(list(_STEP_TITLES), self)
        layout.addWidget(self.progress)

        self.stack = QStackedWidget(self)
        self.source_page = _SourceStep(self)
        self.scenario_page = _ScenarioStep(self)
        self.review_page = _ReviewStep(self)
        self.stack.addWidget(self.source_page)
        self.stack.addWidget(self.scenario_page)
        self.stack.addWidget(self.review_page)
        layout.addWidget(self.stack, stretch=1)

        nav = QHBoxLayout()
        nav.setSpacing(8)
        self.btn_back = PushButton("Quay lại", self)
        self.btn_back.clicked.connect(self._go_back)
        self.btn_cancel = PushButton("Về Chat", self)
        self.btn_cancel.clicked.connect(self.cancelled.emit)
        self.btn_next = PrimaryPushButton("Tiếp theo", self)
        self.btn_next.clicked.connect(self._go_next)
        nav.addWidget(self.btn_back)
        nav.addStretch(1)
        nav.addWidget(self.btn_cancel)
        nav.addWidget(self.btn_next)
        layout.addLayout(nav)
        self._sync_chrome()

    def reset(self) -> None:
        self._step = 0
        self.scenarios = []
        self._sync_chrome()

    def _go_back(self) -> None:
        if self._step <= 0:
            return
        self._step -= 1
        self._sync_chrome()

    def _go_next(self) -> None:
        if self._step == 0:
            if not self.source_page.markdown_text():
                self.source_page.show_hint("Nhập mô tả / SRS trước khi tiếp tục.")
                return
            self._generate_outline()
            self._step = 1
            self._sync_chrome()
            return
        if self._step == 1:
            selected = self.scenario_page.selected_scenarios()
            if not selected:
                self.scenario_page.show_hint("Chọn ít nhất một scenario.")
                return
            self._prepare_review(selected)
            self._step = 2
            self._sync_chrome()
            return
        accepted = self.scenario_page.selected_scenarios()
        self.drafts_ready.emit(accepted, self.source_page.url_text())

    def _sync_chrome(self) -> None:
        self.progress.set_current(self._step)
        self.stack.setCurrentIndex(self._step)
        self.btn_back.setEnabled(self._step > 0)
        if self._step >= UI_WIZARD_STEP_COUNT - 1:
            self.btn_next.setText("Tạo draft UNBOUND")
        else:
            self.btn_next.setText("Tiếp theo")

    def _generate_outline(self) -> None:
        markdown = self.source_page.markdown_text()
        self.scenarios = self.generator.generate_outline_sync(markdown)
        self.scenario_page.load_scenarios(self.scenarios)

    def _prepare_review(self, selected: list[TestScenario]) -> None:
        start = self.source_page.url_text()
        for scenario in selected:
            if not scenario.cases:
                scenario.cases = self.generator.generate_cases_sync(scenario, batch_size=2)
            if start:
                for case in scenario.cases:
                    case.start_url = start
                    if case.steps and case.steps[0].action == "goto":
                        case.steps[0].input_data = start
        self.review_page.set_summary(selected)


class _SourceStep(QWidget):
    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(8)

        card = CardWidget(self)
        card.setStyleSheet(f"CardWidget {{ background-color: {THEME_BG_PANEL}; }}")
        form = QVBoxLayout(card)
        form.setContentsMargins(12, 10, 12, 10)
        form.setSpacing(6)

        form.addWidget(StrongBodyLabel("Nguồn đầu vào", card))
        form.addWidget(CaptionLabel("URL đích (tuỳ chọn)", card))
        self.url = LineEdit(card)
        self.url.setPlaceholderText(_URL_HINT)
        self.url.setText(DEFAULT_TARGET_URL)
        self.url.setClearButtonEnabled(True)
        form.addWidget(self.url)

        form.addWidget(CaptionLabel("Figma link (tuỳ chọn)", card))
        self.figma = LineEdit(card)
        self.figma.setPlaceholderText("https://www.figma.com/file/…")
        self.figma.setClearButtonEnabled(True)
        form.addWidget(self.figma)

        doc_row = QHBoxLayout()
        doc_col = QVBoxLayout()
        doc_col.addWidget(CaptionLabel("Tài liệu SRS", card))
        self.doc_path = LineEdit(card)
        self.doc_path.setReadOnly(True)
        self.doc_path.setPlaceholderText("Chưa chọn file…")
        doc_col.addWidget(self.doc_path)
        doc_row.addLayout(doc_col, stretch=1)
        self.btn_browse = ToolButton(FIF.FOLDER, card)
        self.btn_browse.setToolTip("Chọn file SRS (md/txt/docx/pdf)")
        self.btn_browse.clicked.connect(self._browse)
        doc_row.addWidget(self.btn_browse, alignment=Qt.AlignmentFlag.AlignBottom)
        form.addLayout(doc_row)

        form.addWidget(CaptionLabel("Markdown / mô tả tính năng", card))
        self.markdown = PlainTextEdit(card)
        self.markdown.setPlaceholderText("Mô tả luồng nghiệp vụ, tiêu chí chấp nhận…")
        self.markdown.setPlainText("Sample SRS: login and checkout happy path")
        self.markdown.setMinimumHeight(100)
        form.addWidget(self.markdown)

        self.hint = CaptionLabel("", card)
        self.hint.setStyleSheet(f"color: {ICON_COLOR_WARN};")
        form.addWidget(self.hint)
        layout.addWidget(card)

    def _browse(self) -> None:
        path, _ = QFileDialog.getOpenFileName(
            self, "SRS", "", "Docs (*.md *.txt *.docx *.pdf)"
        )
        if not path:
            return
        self.doc_path.setText(path)
        try:
            text = Path(path).read_text(encoding="utf-8")[:UI_MARKDOWN_PREVIEW_CHARS]
            self.markdown.setPlainText(text)
            self.hint.setText("")
        except OSError as exc:
            self.hint.setText(f"Không đọc được file: {exc}")

    def markdown_text(self) -> str:
        return str(self.markdown.toPlainText()).strip()

    def url_text(self) -> str:
        return str(self.url.text()).strip()

    def show_hint(self, message: str) -> None:
        self.hint.setText(message)


class _ScenarioStep(QWidget):
    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(8)

        card = CardWidget(self)
        form = QVBoxLayout(card)
        form.setContentsMargins(12, 10, 12, 10)
        form.setSpacing(6)
        form.addWidget(StrongBodyLabel("Scenario đề xuất", card))
        form.addWidget(
            CaptionLabel("Bỏ chọn scenario không cần trước khi tạo draft.", card)
        )
        self.list = ListWidget(card)
        self.list.setMinimumHeight(140)
        form.addWidget(self.list)
        self.hint = CaptionLabel("", card)
        self.hint.setStyleSheet(f"color: {ICON_COLOR_WARN};")
        form.addWidget(self.hint)
        layout.addWidget(card)

    def load_scenarios(self, scenarios: list[TestScenario]) -> None:
        self.list.clear()
        self.hint.setText("")
        for sc in scenarios:
            item = QListWidgetItem(f"{sc.code}: {sc.title}")
            item.setFlags(item.flags() | Qt.ItemFlag.ItemIsUserCheckable)
            item.setCheckState(Qt.CheckState.Checked)
            item.setData(Qt.ItemDataRole.UserRole, sc)
            self.list.addItem(item)

    def selected_scenarios(self) -> list[TestScenario]:
        selected: list[TestScenario] = []
        for i in range(self.list.count()):
            item = self.list.item(i)
            if item and item.checkState() == Qt.CheckState.Checked:
                data = item.data(Qt.ItemDataRole.UserRole)
                if isinstance(data, TestScenario):
                    selected.append(data)
        return selected

    def show_hint(self, message: str) -> None:
        self.hint.setText(message)


class _ReviewStep(QWidget):
    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(8)

        card = CardWidget(self)
        form = QVBoxLayout(card)
        form.setContentsMargins(12, 10, 12, 10)
        form.setSpacing(6)
        form.addWidget(StrongBodyLabel("Tóm tắt draft UNBOUND", card))
        self.summary = BodyLabel("Chưa có scenario", card)
        self.summary.setWordWrap(True)
        form.addWidget(self.summary)
        form.addWidget(
            CaptionLabel(
                "«Tạo draft UNBOUND» sẽ Apply vào Explorer. Sync URL để bind selector.",
                card,
            )
        )
        layout.addWidget(card)
        layout.addStretch(1)

    def set_summary(self, scenarios: list[TestScenario]) -> None:
        if not scenarios:
            self.summary.setText("Không có scenario nào được chọn.")
            return
        case_count = sum(len(s.cases) for s in scenarios)
        lines = [
            f"{len(scenarios)} scenario · {case_count} case draft UNBOUND:",
            "",
        ]
        for s in scenarios:
            lines.append(f"• {s.code}: {s.title} ({len(s.cases)} cases)")
        self.summary.setText("\n".join(lines))
