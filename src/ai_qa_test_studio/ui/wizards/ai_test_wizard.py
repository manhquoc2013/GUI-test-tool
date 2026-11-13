"""AI Test Wizard — 3 Fluent steps on MessageBoxBase (no FramelessWindow)."""

from __future__ import annotations

from pathlib import Path

from PySide6.QtCore import Qt
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
    MessageBoxBase,
    PlainTextEdit,
    PushButton,
    StrongBodyLabel,
    SubtitleLabel,
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
    UI_WIZARD_MIN_HEIGHT,
    UI_WIZARD_MIN_WIDTH,
    UI_WIZARD_STEP_COUNT,
)
from ai_qa_test_studio.core.models import TestScenario
from ai_qa_test_studio.ui.widgets.step_progress_bar import StepProgressBar

_URL_HINT = "https://your-app.example/ (tuỳ chọn — dùng cho Sync/Run)"
_STEP_TITLES = ("Nhập liệu", "Chọn Scenario", "Duyệt draft")


class AiTestWizard(MessageBoxBase):
    def __init__(self, parent: QWidget) -> None:
        super().__init__(parent)
        self.setWindowTitle("AI Test Wizard")
        self.generator = SpecGenerator()
        self.scenarios: list[TestScenario] = []
        self._accepted_scenarios: list[TestScenario] = []
        self._step = 0

        self.widget.setMinimumWidth(UI_WIZARD_MIN_WIDTH)
        self.widget.setMinimumHeight(UI_WIZARD_MIN_HEIGHT)

        self.title_label = SubtitleLabel("AI Test Wizard", self.widget)
        self.subtitle = CaptionLabel(
            "Tạo kịch bản draft UNBOUND từ SRS / mô tả / URL trong 3 bước.",
            self.widget,
        )
        self.progress = StepProgressBar(list(_STEP_TITLES), self.widget)

        self.stack = QStackedWidget(self.widget)
        self.source_page = _SourceStep(self.widget)
        self.scenario_page = _ScenarioStep(self.widget)
        self.review_page = _ReviewStep(self.widget)
        self.stack.addWidget(self.source_page)
        self.stack.addWidget(self.scenario_page)
        self.stack.addWidget(self.review_page)

        self.viewLayout.addWidget(self.title_label)
        self.viewLayout.addWidget(self.subtitle)
        self.viewLayout.addWidget(self.progress)
        self.viewLayout.addWidget(self.stack, stretch=1)

        self.back_button = PushButton("Quay lại", self.buttonGroup)
        self.back_button.clicked.connect(self._go_back)
        self.buttonLayout.insertWidget(0, self.back_button, 1, Qt.AlignmentFlag.AlignVCenter)

        self.cancelButton.setText("Hủy")
        self.yesButton.setText("Tiếp theo")
        self._sync_chrome()

    def validate(self) -> bool:
        if self._step == 0:
            if not self.source_page.markdown_text():
                self.source_page.show_hint("Nhập mô tả / SRS trước khi tiếp tục.")
                return False
            self._generate_outline()
            self._step = 1
            self._sync_chrome()
            return False
        if self._step == 1:
            selected = self.scenario_page.selected_scenarios()
            if not selected:
                self.scenario_page.show_hint("Chọn ít nhất một scenario.")
                return False
            self._prepare_review(selected)
            self._step = 2
            self._sync_chrome()
            return False
        self._accepted_scenarios = self.scenario_page.selected_scenarios()
        return True

    def result_scenarios(self) -> list[TestScenario]:
        return list(self._accepted_scenarios)

    def source_url(self) -> str:
        return self.source_page.url_text()

    def _go_back(self) -> None:
        if self._step <= 0:
            return
        self._step -= 1
        self._sync_chrome()

    def _sync_chrome(self) -> None:
        self.progress.set_current(self._step)
        self.stack.setCurrentIndex(self._step)
        self.back_button.setEnabled(self._step > 0)
        if self._step >= UI_WIZARD_STEP_COUNT - 1:
            self.yesButton.setText("Tạo draft UNBOUND")
        else:
            self.yesButton.setText("Tiếp theo")

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
        layout.setSpacing(10)

        card = CardWidget(self)
        card.setStyleSheet(f"CardWidget {{ background-color: {THEME_BG_PANEL}; }}")
        form = QVBoxLayout(card)
        form.setContentsMargins(16, 14, 16, 14)
        form.setSpacing(8)

        form.addWidget(StrongBodyLabel("Nguồn đầu vào", card))
        form.addWidget(CaptionLabel("URL đích (tuỳ chọn — dùng cho Sync/Run)", card))
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
        self.markdown.setMinimumHeight(140)
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
        layout.setSpacing(10)

        card = CardWidget(self)
        form = QVBoxLayout(card)
        form.setContentsMargins(16, 14, 16, 14)
        form.setSpacing(8)
        form.addWidget(StrongBodyLabel("Scenario đề xuất", card))
        form.addWidget(
            CaptionLabel("Bỏ chọn scenario không cần thiết trước khi tạo draft.", card)
        )
        self.list = ListWidget(card)
        self.list.setMinimumHeight(220)
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
        layout.setSpacing(10)

        card = CardWidget(self)
        form = QVBoxLayout(card)
        form.setContentsMargins(16, 14, 16, 14)
        form.setSpacing(8)
        form.addWidget(StrongBodyLabel("Tóm tắt draft UNBOUND", card))
        self.summary = BodyLabel("Chưa có scenario", card)
        self.summary.setWordWrap(True)
        form.addWidget(self.summary)
        form.addWidget(
            CaptionLabel(
                "Nhấn «Tạo draft UNBOUND» để đưa các case vào Test Explorer. "
                "Selector thật sẽ được gắn sau khi Sync URL.",
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
