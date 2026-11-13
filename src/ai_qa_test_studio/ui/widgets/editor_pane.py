"""Center editor: Segmented 4 modes + InfoBar + expanding Spec CardWidget form."""

from __future__ import annotations

import yaml
from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import (
    QHBoxLayout,
    QHeaderView,
    QSizePolicy,
    QStackedWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)
from qfluentwidgets import (
    CaptionLabel,
    CardWidget,
    ComboBox,
    InfoBar,
    InfoBarPosition,
    LineEdit,
    PlainTextEdit,
    PrimaryPushButton,
    SegmentedWidget,
    SmoothScrollArea,
    StrongBodyLabel,
    SubtitleLabel,
    TableWidget,
    TextEdit,
    TransparentToolButton,
)
from qfluentwidgets import (
    FluentIcon as FIF,
)

from ai_qa_test_studio.constants import (
    BINDING_UNBOUND,
    THEME_BG_CANVAS,
    UI_BREAKPOINT_MEDIUM,
    UI_BREAKPOINT_WIDE,
    UI_CANVAS_MARGIN_H,
    UI_CANVAS_MARGIN_H_COMPACT,
    UI_CANVAS_MARGIN_V,
    UI_CANVAS_MARGIN_V_COMPACT,
    UI_STEPS_COL_BINDING_WIDTH,
    UI_STEPS_COL_INDEX_WIDTH,
)
from ai_qa_test_studio.core.models import TestCase, TestStep
from ai_qa_test_studio.ui.formatting import count_unbound_steps

_PRIORITIES = ["P0 - Critical", "P1 - High", "P2 - Medium", "P3 - Low"]
_TYPES = ["Functional", "E2E Regression", "Boundary (BVA)", "API + UI Hybrid", "Negative", "UI/UX", "Security"]
_MODE_KEYS = ("spec", "nocode", "yaml", "code")


class EditorPane(QWidget):
    case_changed = Signal()
    step_row_selected = Signal(int)
    sync_requested = Signal()

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setAccessibleName("Workspace Editor")
        self.setStyleSheet(f"background-color: {THEME_BG_CANVAS};")
        self._case: TestCase | None = None
        self._loading = False
        self._details_expanded = False

        layout = QVBoxLayout(self)
        layout.setContentsMargins(UI_CANVAS_MARGIN_H, UI_CANVAS_MARGIN_V, UI_CANVAS_MARGIN_H, UI_CANVAS_MARGIN_V)
        layout.setSpacing(10)

        self.title_label = SubtitleLabel("Chọn một test case", self)
        self.title_label.setAccessibleName("Tiêu đề kịch bản")
        self.title_label.setWordWrap(True)
        layout.addWidget(self.title_label)

        header_row = QHBoxLayout()
        self.mode_switch = SegmentedWidget(self)
        self.mode_switch.addItem(routeKey="spec", text="Spec", icon=FIF.DOCUMENT)
        self.mode_switch.addItem(routeKey="nocode", text="Steps", icon=FIF.CHECKBOX)
        self.mode_switch.addItem(routeKey="yaml", text="YAML", icon=FIF.ALIGNMENT)
        self.mode_switch.addItem(routeKey="code", text="Pytest", icon=FIF.CODE)
        self.mode_switch.setCurrentItem("nocode")
        header_row.addWidget(self.mode_switch)
        header_row.addStretch(1)
        layout.addLayout(header_row)

        self.unbound_banner = InfoBar.warning(
            title="Có bước chưa gắn selector thật (UNBOUND)",
            content="Nhập Target URL trên thanh công cụ và nhấn Sync URL để Agent tự động ánh xạ.",
            orient=Qt.Orientation.Horizontal,
            isClosable=True,
            position=InfoBarPosition.NONE,
            duration=-1,
            parent=self,
        )
        self.unbound_banner.setAccessibleName("Cảnh báo bước UNBOUND")
        btn_quick_sync = PrimaryPushButton("Sync ngay", self.unbound_banner)
        btn_quick_sync.clicked.connect(lambda: self.sync_requested.emit())
        self.unbound_banner.addWidget(btn_quick_sync)
        self.unbound_banner.hide()
        layout.addWidget(self.unbound_banner)

        self.stack = QStackedWidget(self)
        self.stack.addWidget(self._build_spec_view())
        self.table = self._build_nocode_table()
        self.stack.addWidget(self.table)
        self.yaml_edit = PlainTextEdit(self)
        self.yaml_edit.setStyleSheet("font-family: 'JetBrains Mono', 'Menlo', monospace; font-size: 13px;")
        self.stack.addWidget(self.yaml_edit)
        self.code_edit = PlainTextEdit(self)
        self.code_edit.setStyleSheet("font-family: 'JetBrains Mono', 'Menlo', monospace; font-size: 13px;")
        self.stack.addWidget(self.code_edit)
        layout.addWidget(self.stack, stretch=1)
        self.stack.setCurrentIndex(1)

        self.mode_switch.currentItemChanged.connect(self._on_mode_changed)
        self.table.itemSelectionChanged.connect(self._emit_step_selection)

        self.title_input.textChanged.connect(lambda _t: self._capture_spec_fields())
        self.priority_combo.currentIndexChanged.connect(lambda _i: self._capture_spec_fields())
        self.type_combo.currentIndexChanged.connect(lambda _i: self._capture_spec_fields())

    def _build_spec_view(self) -> SmoothScrollArea:
        scroll = SmoothScrollArea(self)
        scroll.setWidgetResizable(True)
        scroll.setStyleSheet("background: transparent; border: none;")

        container = QWidget()
        container.setStyleSheet("background: transparent;")
        vbox = QVBoxLayout(container)
        vbox.setContentsMargins(0, 0, 4, 0)
        vbox.setSpacing(12)

        meta_card = CardWidget(container)
        meta_layout = QVBoxLayout(meta_card)
        meta_layout.setContentsMargins(16, 14, 16, 14)
        meta_layout.setSpacing(10)

        meta_layout.addWidget(CaptionLabel("Mã Test Case", meta_card))
        self.code_input = LineEdit(meta_card)
        self.code_input.setReadOnly(True)
        self.code_input.setAccessibleName("Mã Test Case")
        self.code_input.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        meta_layout.addWidget(self.code_input)

        meta_layout.addWidget(CaptionLabel("Tiêu đề kịch bản (Title)", meta_card))
        self.title_input = LineEdit(meta_card)
        self.title_input.setAccessibleName("Tiêu đề Test Case")
        self.title_input.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Fixed)
        meta_layout.addWidget(self.title_input)

        row_meta = QHBoxLayout()
        row_meta.setSpacing(12)

        col_prio = QVBoxLayout()
        col_prio.addWidget(CaptionLabel("Độ ưu tiên (Priority)", meta_card))
        self.priority_combo = ComboBox(meta_card)
        self.priority_combo.addItems(_PRIORITIES)
        self.priority_combo.setMinimumWidth(160)
        col_prio.addWidget(self.priority_combo)

        col_type = QVBoxLayout()
        col_type.addWidget(CaptionLabel("Loại kiểm thử (Type)", meta_card))
        self.type_combo = ComboBox(meta_card)
        self.type_combo.addItems(_TYPES)
        self.type_combo.setMinimumWidth(180)
        col_type.addWidget(self.type_combo)

        row_meta.addLayout(col_prio)
        row_meta.addLayout(col_type)
        row_meta.addStretch(1)
        meta_layout.addLayout(row_meta)
        vbox.addWidget(meta_card)

        oracle_card = CardWidget(container)
        oracle_layout = QVBoxLayout(oracle_card)
        oracle_layout.setContentsMargins(16, 14, 16, 14)
        oracle_layout.setSpacing(10)

        details_header = QHBoxLayout()
        details_header.addWidget(StrongBodyLabel("Preconditions / Expected", oracle_card))
        details_header.addStretch(1)
        self.btn_toggle_details = TransparentToolButton(FIF.CARE_DOWN_SOLID, oracle_card)
        self.btn_toggle_details.setToolTip("Mở / thu gọn chi tiết")
        self.btn_toggle_details.clicked.connect(self._toggle_details)
        details_header.addWidget(self.btn_toggle_details)
        oracle_layout.addLayout(details_header)

        self.details_body = QWidget(oracle_card)
        details_layout = QVBoxLayout(self.details_body)
        details_layout.setContentsMargins(0, 0, 0, 0)
        details_layout.setSpacing(10)
        details_layout.addWidget(CaptionLabel("Điều kiện tiên quyết (Preconditions)", self.details_body))
        self.precond_edit = TextEdit(self.details_body)
        self.precond_edit.setPlaceholderText("Mỗi điều kiện một dòng...")
        self.precond_edit.setFixedHeight(72)
        details_layout.addWidget(self.precond_edit)
        details_layout.addWidget(
            CaptionLabel("Kết quả mong đợi cuối cùng (Expected Final Result)", self.details_body)
        )
        self.expected_edit = TextEdit(self.details_body)
        self.expected_edit.setPlaceholderText("Kết quả cuối mong đợi (Outer Loop đối chiếu)")
        self.expected_edit.setFixedHeight(88)
        details_layout.addWidget(self.expected_edit)
        self.details_body.setVisible(False)
        oracle_layout.addWidget(self.details_body)

        vbox.addWidget(oracle_card)
        vbox.addStretch(1)
        scroll.setWidget(container)
        return scroll

    def _build_nocode_table(self) -> TableWidget:
        table = TableWidget(self)
        table.setColumnCount(5)
        table.setHorizontalHeaderLabels(["#", "Action", "Intent", "Data", "Binding"])
        table.setAccessibleName("Bảng bước No-Code")
        table.setWordWrap(False)
        table.setTextElideMode(Qt.TextElideMode.ElideRight)
        table.setSizePolicy(QSizePolicy.Policy.Expanding, QSizePolicy.Policy.Expanding)
        table.verticalHeader().setVisible(False)
        table.setHorizontalScrollMode(TableWidget.ScrollMode.ScrollPerPixel)
        table.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAsNeeded)
        self._apply_steps_header(table)
        return table

    def _apply_steps_header(self, table: TableWidget) -> None:
        hdr = table.horizontalHeader()
        hdr.setMinimumSectionSize(64)
        hdr.setStretchLastSection(False)
        hdr.setSectionResizeMode(0, QHeaderView.ResizeMode.Fixed)
        hdr.setSectionResizeMode(1, QHeaderView.ResizeMode.Interactive)
        hdr.setSectionResizeMode(2, QHeaderView.ResizeMode.Stretch)
        hdr.setSectionResizeMode(3, QHeaderView.ResizeMode.Stretch)
        hdr.setSectionResizeMode(4, QHeaderView.ResizeMode.Fixed)
        table.setColumnWidth(0, UI_STEPS_COL_INDEX_WIDTH)
        table.setColumnWidth(1, 100)
        table.setColumnWidth(4, UI_STEPS_COL_BINDING_WIDTH)

    def apply_density(self, width: int) -> None:
        if width >= UI_BREAKPOINT_WIDE:
            h, v = UI_CANVAS_MARGIN_H, UI_CANVAS_MARGIN_V
        elif width >= UI_BREAKPOINT_MEDIUM:
            h, v = UI_CANVAS_MARGIN_H_COMPACT + 4, UI_CANVAS_MARGIN_V_COMPACT + 4
        else:
            h, v = UI_CANVAS_MARGIN_H_COMPACT, UI_CANVAS_MARGIN_V_COMPACT
        layout = self.layout()
        if layout is not None:
            layout.setContentsMargins(h, v, h, v)
        self._apply_steps_header(self.table)

    def _toggle_details(self) -> None:
        self._details_expanded = not self._details_expanded
        self.details_body.setVisible(self._details_expanded)
        icon = FIF.CARE_UP_SOLID if self._details_expanded else FIF.CARE_DOWN_SOLID
        self.btn_toggle_details.setIcon(icon.icon())

    def load_case(self, case: TestCase) -> None:
        self._loading = True
        try:
            self._case = case
            self.title_label.setText(f"{case.code} — {case.title}")
            self._render_all()
        finally:
            self._loading = False

    def peek_case(self) -> TestCase | None:
        return self._case

    def current_case(self) -> TestCase | None:
        self._capture_active()
        return self._case

    def selected_step_index(self) -> int | None:
        model = self.table.selectionModel()
        rows = model.selectedRows() if model else []
        if not rows:
            return None
        return int(rows[0].row())

    def bind_element_to_selected_step(self, element_id: str) -> bool:
        case = self.current_case()
        if case is None or not case.steps:
            return False
        idx = self.selected_step_index()
        if idx is None:
            idx = next(
                (i for i, s in enumerate(case.steps) if s.binding_status == BINDING_UNBOUND),
                0,
            )
        if idx < 0 or idx >= len(case.steps):
            return False
        step = case.steps[idx]
        step.element_ref = element_id
        step.binding_status = "BOUND"
        self._render_all()
        self.case_changed.emit()
        return True

    def _on_mode_changed(self, key: str) -> None:
        self._capture_active()
        self.stack.setCurrentIndex(_MODE_KEYS.index(key) if key in _MODE_KEYS else 0)
        self._render_all()

    def _render_all(self) -> None:
        if self._case is None:
            return
        unbound = count_unbound_steps(self._case)
        if unbound > 0:
            self.unbound_banner.titleLabel.setText(
                f"{unbound} bước chưa gắn selector thật (UNBOUND)"
            )
            self.unbound_banner.show()
        else:
            self.unbound_banner.hide()

        self.code_input.setText(self._case.code)
        self.title_input.setText(self._case.title)
        self.priority_combo.setCurrentText(self._case.priority)
        self.type_combo.setCurrentText(self._case.test_type)
        self.precond_edit.setPlainText("\n".join(self._case.preconditions or []))
        self.expected_edit.setPlainText(self._case.expected_final_result or "")

        self.table.setRowCount(0)
        for step in self._case.steps:
            row = self.table.rowCount()
            self.table.insertRow(row)
            values = [
                str(step.step_no),
                step.action,
                step.intent_hint,
                step.input_data or "",
                step.binding_status,
            ]
            for col, value in enumerate(values):
                item = QTableWidgetItem(value)
                item.setToolTip(value)
                self.table.setItem(row, col, item)
        self._apply_steps_header(self.table)

        payload = {
            "code": self._case.code,
            "title": self._case.title,
            "steps": [s.model_dump() for s in self._case.steps],
        }
        self.yaml_edit.setPlainText(yaml.safe_dump(payload, allow_unicode=True, sort_keys=False))
        lines = [f"# {self._case.code}", "def test_case(page):"]
        for step in self._case.steps:
            lines.append(f"    # {step.step_no}: {step.action} {step.intent_hint}")
            if step.action == "goto":
                lines.append(f"    page.goto({(step.input_data or '/')!r})")
        self.code_edit.setPlainText("\n".join(lines))

    def _capture_spec_fields(self) -> None:
        if self._case is None or self._loading:
            return
        self._case.title = self.title_input.text().strip() or self._case.title
        self._case.priority = self.priority_combo.currentText()  # type: ignore[assignment]
        self._case.test_type = self.type_combo.currentText()  # type: ignore[assignment]
        self._case.preconditions = [
            line.strip() for line in self.precond_edit.toPlainText().splitlines() if line.strip()
        ]
        self._case.expected_final_result = self.expected_edit.toPlainText().strip()
        self.case_changed.emit()

    def _capture_active(self) -> None:
        if self._case is None or self._loading:
            return
        self._capture_spec_fields()
        if self.stack.currentWidget() is not self.yaml_edit:
            return
        try:
            raw_yaml = self.yaml_edit.toPlainText()  # type: ignore[union-attr]
            data = yaml.safe_load(raw_yaml) or {}
            if isinstance(data, dict) and "steps" in data:
                self._case.title = str(data.get("title") or self._case.title)
                steps = []
                for raw in data.get("steps") or []:
                    if isinstance(raw, dict):
                        steps.append(TestStep.model_validate(raw))
                self._case.steps = steps
        except Exception:
            return

    def _emit_step_selection(self) -> None:
        idx = self.selected_step_index()
        if idx is not None:
            self.step_row_selected.emit(idx)
