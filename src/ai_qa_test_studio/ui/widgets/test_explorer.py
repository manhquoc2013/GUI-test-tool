"""Left column: Test Suites/Cases + Shared Elements with search."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QBrush, QColor
from PySide6.QtWidgets import (
    QLabel,
    QLineEdit,
    QSplitter,
    QTreeWidget,
    QTreeWidgetItem,
    QVBoxLayout,
    QWidget,
)

from ai_qa_test_studio.constants import (
    BINDING_BOUND,
    BINDING_UNBOUND,
    ICON_COLOR_DANGER,
    ICON_COLOR_SUCCESS,
    ICON_COLOR_WARN,
    STATUS_DOT,
    UI_EXPLORER_MIN_WIDTH,
)
from ai_qa_test_studio.core.models import Project, SharedElement, TestCase
from ai_qa_test_studio.ui.formatting import case_binding_rollups
from ai_qa_test_studio.ui.icons import case_icon, element_icon, search_icon, suite_icon


class TestExplorer(QWidget):
    case_selected = Signal(object)
    element_selected = Signal(object)

    def __init__(self, parent=None) -> None:  # type: ignore[no-untyped-def]
        super().__init__(parent)
        self.setAccessibleName("Test Explorer")
        self.setMinimumWidth(UI_EXPLORER_MIN_WIDTH)
        self._project: Project | None = None
        self._filter = ""

        self.search = QLineEdit()
        self.search.setPlaceholderText("Tìm kiếm…")
        self.search.setClearButtonEnabled(True)
        self.search.setAccessibleName("Ô tìm kiếm Explorer")
        self.search.addAction(search_icon(), QLineEdit.ActionPosition.LeadingPosition)
        self.search.textChanged.connect(self._on_filter)

        self.cases_tree = QTreeWidget()
        self.cases_tree.setHeaderLabels(["Test Suites & Cases"])
        self.cases_tree.setAccessibleName("Cây Test Suites và Cases")
        self.cases_tree.itemSelectionChanged.connect(self._on_case_selection)

        self.elements_tree = QTreeWidget()
        self.elements_tree.setHeaderLabels(["Shared Elements"])
        self.elements_tree.setAccessibleName("Kho Shared Elements")
        self.elements_tree.itemSelectionChanged.connect(self._on_element_selection)

        split = QSplitter(Qt.Orientation.Vertical)
        split.addWidget(self._wrap("Kịch bản", self.cases_tree))
        split.addWidget(self._wrap("Shared Elements (EL_*)", self.elements_tree))
        split.setStretchFactor(0, 3)
        split.setStretchFactor(1, 2)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(8)
        layout.addWidget(self.search)
        layout.addWidget(split, stretch=1)

    def _wrap(self, title: str, tree: QTreeWidget) -> QWidget:
        box = QWidget()
        lay = QVBoxLayout(box)
        lay.setContentsMargins(0, 0, 0, 0)
        lay.setSpacing(4)
        label = QLabel(title)
        label.setObjectName("muted")
        lay.addWidget(label)
        lay.addWidget(tree)
        return box

    def load_project(self, project: Project) -> None:
        self._project = project
        self._rebuild()

    def _on_filter(self, text: str) -> None:
        self._filter = text.strip().lower()
        self._rebuild()

    def _matches(self, *parts: str) -> bool:
        if not self._filter:
            return True
        blob = " ".join(parts).lower()
        return self._filter in blob

    def _rebuild(self) -> None:
        self.cases_tree.clear()
        self.elements_tree.clear()
        if self._project is None:
            return
        root = QTreeWidgetItem([self._project.name])
        root.setIcon(0, suite_icon())
        self.cases_tree.addTopLevelItem(root)
        self._add_group(root, self._project.root_group)
        root.setExpanded(True)

        for el in self._project.shared_elements:
            if not self._matches(el.element_id, el.friendly_name, el.css or "", el.role or ""):
                continue
            label = self._element_label(el)
            item = QTreeWidgetItem([label])
            item.setIcon(0, element_icon())
            tip = el.primary_playwright or el.css or el.xpath or el.element_id
            item.setToolTip(0, tip)
            item.setData(0, Qt.ItemDataRole.UserRole, el)
            self.elements_tree.addTopLevelItem(item)

    def _element_label(self, el: SharedElement) -> str:
        role = el.role or "Element"
        return f"[{el.element_id}] {role}: {el.friendly_name}"

    def _add_group(self, parent: QTreeWidgetItem, group) -> None:  # type: ignore[no-untyped-def]
        if not self._group_visible(group):
            return
        node = QTreeWidgetItem([group.name])
        node.setIcon(0, suite_icon())
        parent.addChild(node)
        for scenario in group.scenarios:
            if not self._matches(scenario.code, scenario.title) and not any(
                self._matches(c.code, c.title) for c in scenario.cases
            ):
                if self._filter:
                    continue
            sc = QTreeWidgetItem([f"{scenario.code}: {scenario.title}"])
            sc.setIcon(0, suite_icon())
            for case in scenario.cases:
                if self._filter and not self._matches(case.code, case.title):
                    continue
                item = self._case_item(case)
                sc.addChild(item)
            if sc.childCount() or not self._filter:
                node.addChild(sc)
                sc.setExpanded(True)
        for suite in group.suites:
            su = QTreeWidgetItem([f"Suite: {suite.name}"])
            su.setIcon(0, suite_icon())
            for case in suite.cases:
                if self._filter and not self._matches(case.code, case.title):
                    continue
                su.addChild(self._case_item(case))
            if su.childCount() or not self._filter:
                node.addChild(su)
                su.setExpanded(True)
        for child in group.children:
            self._add_group(node, child)
        node.setExpanded(True)

    def _group_visible(self, group) -> bool:  # type: ignore[no-untyped-def]
        if not self._filter:
            return True
        if self._matches(group.name):
            return True
        for scenario in group.scenarios:
            if self._matches(scenario.code, scenario.title):
                return True
            if any(self._matches(c.code, c.title) for c in scenario.cases):
                return True
        for suite in group.suites:
            if self._matches(suite.name):
                return True
            if any(self._matches(c.code, c.title) for c in suite.cases):
                return True
        return any(self._group_visible(child) for child in group.children)

    def _case_item(self, case: TestCase) -> QTreeWidgetItem:
        rollup = case_binding_rollups(case)
        item = QTreeWidgetItem([f"{STATUS_DOT} {case.code}: {case.title}"])
        item.setIcon(0, case_icon())
        item.setData(0, Qt.ItemDataRole.UserRole, case)
        item.setToolTip(0, f"{case.code} — {rollup}")
        color = {
            "FAILED": ICON_COLOR_DANGER,
            BINDING_UNBOUND: ICON_COLOR_WARN,
            BINDING_BOUND: ICON_COLOR_SUCCESS,
        }.get(rollup, ICON_COLOR_WARN)
        item.setForeground(0, QBrush(QColor(color)))
        return item

    def _on_case_selection(self) -> None:
        items = self.cases_tree.selectedItems()
        if not items:
            return
        data = items[0].data(0, Qt.ItemDataRole.UserRole)
        if isinstance(data, TestCase):
            self.case_selected.emit(data)

    def _on_element_selection(self) -> None:
        items = self.elements_tree.selectedItems()
        if not items:
            return
        data = items[0].data(0, Qt.ItemDataRole.UserRole)
        if isinstance(data, SharedElement):
            self.element_selected.emit(data)
