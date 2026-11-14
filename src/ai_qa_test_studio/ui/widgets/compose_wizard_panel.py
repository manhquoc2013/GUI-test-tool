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
    SmoothScrollArea,
    StrongBodyLabel,
)
from qfluentwidgets import (
    FluentIcon as FIF,
)

from ai_qa_test_studio.agent.spec_generator import SpecGenerator
from ai_qa_test_studio.constants import (
    DEFAULT_TARGET_URL,
    ICON_COLOR_WARN,
    UI_CARD_GAP,
    UI_MARKDOWN_PREVIEW_CHARS,
    UI_WIZARD_DOC_LIST_MIN_HEIGHT,
    UI_WIZARD_STEP_COUNT,
    WIZARD_DOC_FILE_FILTER,
)
from ai_qa_test_studio.core.models import TestScenario
from ai_qa_test_studio.core.wizard_documents import (
    WizardDocument,
    build_wizard_llm_context,
    collect_imported_scenarios,
    load_wizard_document,
    merge_wizard_scenarios,
)
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
        self.btn_cancel.clicked.connect(lambda: self.cancelled.emit())
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
        self.source_page.reset()
        self._sync_chrome()

    def _go_back(self) -> None:
        if self._step <= 0:
            return
        self._step -= 1
        self._sync_chrome()

    def _go_next(self) -> None:
        if self._step == 0:
            if not self.source_page.has_input():
                self.source_page.show_hint(
                    "Nhập mô tả hoặc đính kèm ít nhất một tài liệu trước khi tiếp tục."
                )
                return
            self._generate_outline()
            if not self.scenarios:
                self.source_page.show_hint("Không tạo được scenario từ tài liệu đã cho.")
                return
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
        documents = self.source_page.documents()
        context = build_wizard_llm_context(
            freeform_markdown=self.source_page.markdown_text(),
            documents=documents,
        )
        imported = collect_imported_scenarios(documents)
        generated = self.generator.generate_outline_sync(context) if context.strip() else []
        self.scenarios = merge_wizard_scenarios(imported, generated)
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
        self._documents: list[WizardDocument] = []
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(8)

        scroll = SmoothScrollArea(self)
        scroll.setWidgetResizable(True)
        scroll.setStyleSheet("background: transparent; border: none;")
        container = QWidget()
        form = QVBoxLayout(container)
        form.setContentsMargins(2, 4, 6, 4)
        form.setSpacing(UI_CARD_GAP)

        def add_card(title: str, hint: str, widget: QWidget) -> CardWidget:
            card = CardWidget(container)
            cl = QVBoxLayout(card)
            cl.setContentsMargins(12, 10, 12, 10)
            cl.setSpacing(6)
            cl.addWidget(StrongBodyLabel(title, card))
            if hint:
                h = CaptionLabel(hint, card)
                h.setWordWrap(True)
                cl.addWidget(h)
            cl.addWidget(widget)
            form.addWidget(card)
            return card

        self.url = LineEdit()
        self.url.setPlaceholderText(_URL_HINT)
        self.url.setText(DEFAULT_TARGET_URL)
        self.url.setClearButtonEnabled(True)
        add_card("URL đích (tùy chọn)", "Dùng để Agent quét DOM trực tiếp khi tạo draft.", self.url)

        self.figma = LineEdit()
        self.figma.setPlaceholderText("https://www.figma.com/file/…")
        self.figma.setClearButtonEnabled(True)
        add_card("Figma Design Link", "", self.figma)

        docs_wrap = QWidget()
        docs_col = QVBoxLayout(docs_wrap)
        docs_col.setContentsMargins(0, 0, 0, 0)
        docs_col.setSpacing(6)
        self.doc_list = ListWidget(docs_wrap)
        self.doc_list.setMinimumHeight(UI_WIZARD_DOC_LIST_MIN_HEIGHT)
        docs_col.addWidget(self.doc_list)
        docs_btns = QHBoxLayout()
        docs_btns.setContentsMargins(0, 0, 0, 0)
        self.btn_add_doc = PushButton(FIF.FOLDER, "Thêm tệp")
        self.btn_add_doc.setToolTip(
            "Đính kèm SRS / test scenario / test case — hệ thống tự nhận loại"
        )
        self.btn_add_doc.clicked.connect(self._add_documents)
        self.btn_remove_doc = PushButton("Xóa")
        self.btn_remove_doc.clicked.connect(self._remove_selected_document)
        docs_btns.addWidget(self.btn_add_doc)
        docs_btns.addWidget(self.btn_remove_doc)
        docs_btns.addStretch(1)
        docs_col.addLayout(docs_btns)
        add_card(
            "Tài liệu đầu vào",
            "Hỗ trợ MarkItDown (md/xlsx/csv/pdf/docx…). Tự nhận SRS / Test Scenario / Test Case; "
            "import + dùng làm context LLM.",
            docs_wrap,
        )

        self.markdown = PlainTextEdit()
        self.markdown.setPlaceholderText("Mô tả luồng nghiệp vụ hoặc dán User Story...")
        self.markdown.setPlainText("Sample SRS: login and checkout happy path")
        self.markdown.setMinimumHeight(100)
        add_card("Mô tả tính năng / Markdown", "", self.markdown)

        self.hint = CaptionLabel("")
        self.hint.setStyleSheet(f"color: {ICON_COLOR_WARN};")
        form.addWidget(self.hint)
        form.addStretch(1)
        scroll.setWidget(container)
        layout.addWidget(scroll, 1)

    def reset(self) -> None:
        self._documents.clear()
        self.doc_list.clear()
        self.hint.setText("")

    def _add_documents(self) -> None:
        paths, _ = QFileDialog.getOpenFileNames(
            self, "Tài liệu đầu vào", "", WIZARD_DOC_FILE_FILTER
        )
        if not paths:
            return
        errors: list[str] = []
        preview_chunks: list[str] = []
        for raw in paths:
            doc = load_wizard_document(raw)
            if doc.error:
                errors.append(f"{Path(raw).name}: {doc.error}")
                continue
            if any(existing.path == doc.path for existing in self._documents):
                continue
            self._documents.append(doc)
            item = QListWidgetItem(doc.display_name)
            item.setData(Qt.ItemDataRole.UserRole, str(doc.path))
            self.doc_list.addItem(item)
            if doc.markdown.strip():
                preview_chunks.append(doc.markdown.strip()[: UI_MARKDOWN_PREVIEW_CHARS // 2])
        if preview_chunks and not self.markdown.toPlainText().strip():
            self.markdown.setPlainText("\n\n".join(preview_chunks)[:UI_MARKDOWN_PREVIEW_CHARS])
        if errors:
            self.hint.setText("Một số tệp lỗi: " + "; ".join(errors[:3]))
        else:
            self.hint.setText("")

    def _remove_selected_document(self) -> None:
        row = self.doc_list.currentRow()
        if row < 0 or row >= len(self._documents):
            return
        self.doc_list.takeItem(row)
        del self._documents[row]

    def documents(self) -> list[WizardDocument]:
        return list(self._documents)

    def markdown_text(self) -> str:
        return str(self.markdown.toPlainText()).strip()

    def url_text(self) -> str:
        return str(self.url.text()).strip()

    def has_input(self) -> bool:
        if self.markdown_text():
            return True
        return any(not doc.error and doc.markdown.strip() for doc in self._documents)

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
