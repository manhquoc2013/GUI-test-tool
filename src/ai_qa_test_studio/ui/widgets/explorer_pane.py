"""Left explorer: SearchLineEdit + SegmentedWidget (Suites | Elements) + TreeWidget."""

from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QBrush, QColor
from PySide6.QtWidgets import QStackedWidget, QTreeWidgetItem, QVBoxLayout, QWidget
from qfluentwidgets import FluentIcon as FIF
from qfluentwidgets import SearchLineEdit, SegmentedWidget, TreeWidget

from ai_qa_test_studio.constants import (
    BINDING_BOUND,
    BINDING_UNBOUND,
    ICON_COLOR_DANGER,
    ICON_COLOR_HEALED,
    ICON_COLOR_SUCCESS,
    ICON_COLOR_WARN,
    STATUS_DOT,
    THEME_BG_PANEL,
    UI_EXPLORER_MIN_WIDTH,
)
from ai_qa_test_studio.core.models import Project, SharedElement, TestCase
from ai_qa_test_studio.ui.formatting import case_binding_rollups

# Search blob for filter-without-rebuild (avoids TreeWidget.clear AX churn on macOS)
_SEARCH_ROLE = int(Qt.ItemDataRole.UserRole) + 1


class ExplorerPane(QWidget):
    case_selected = Signal(object)
    element_selected = Signal(object)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setAccessibleName("Test Explorer")
        self.setMinimumWidth(UI_EXPLORER_MIN_WIDTH)
        self.setStyleSheet(f"background-color: {THEME_BG_PANEL};")
        self._project: Project | None = None
        self._filter = ""

        layout = QVBoxLayout(self)
        layout.setContentsMargins(10, 10, 10, 10)
        layout.setSpacing(8)

        self.search_input = SearchLineEdit(self)
        self.search_input.setPlaceholderText("Tìm test case, element (Ctrl+P)...")
        self.search_input.setAccessibleName("Ô tìm kiếm Explorer")
        self.search_input.textChanged.connect(self._on_filter)
        layout.addWidget(self.search_input)

        self.pivot = SegmentedWidget(self)
        self.pivot.setAccessibleName("Chuyển Suites hoặc Elements")
        self.pivot.addItem(routeKey="suites", text="Suites", icon=FIF.FOLDER)
        self.pivot.addItem(routeKey="elements", text="Elements (0)", icon=FIF.TAG)
        self.pivot.setCurrentItem("suites")
        layout.addWidget(self.pivot)

        self.stack = QStackedWidget(self)
        self.suite_tree = self._make_tree("Cây Test Suites và Cases")
        self.element_tree = self._make_tree("Kho Shared Elements")
        self.stack.addWidget(self.suite_tree)
        self.stack.addWidget(self.element_tree)
        layout.addWidget(self.stack, stretch=1)

        self.pivot.currentItemChanged.connect(self._on_pivot_changed)
        self.suite_tree.itemSelectionChanged.connect(self._on_case_selection)
        self.element_tree.itemSelectionChanged.connect(self._on_element_selection)

    def _make_tree(self, accessible_name: str) -> TreeWidget:
        tree = TreeWidget(self)
        tree.setHeaderHidden(True)
        tree.setIndentation(16)
        tree.setAccessibleName(accessible_name)
        tree.setAttribute(Qt.WidgetAttribute.WA_MacShowFocusRect, False)
        return tree

    def _on_pivot_changed(self, key: str) -> None:
        self.stack.setCurrentIndex(0 if key == "suites" else 1)

    def load_project(self, project: Project) -> None:
        self._project = project
        count = len(project.shared_elements)
        self.pivot.setItemText("elements", f"Elements ({count})")
        self._rebuild()

    def _on_filter(self, text: str) -> None:
        self._filter = text.strip().lower()
        # Hide/show only — never clear() on each keystroke (macOS AX invalid-object spam)
        self._apply_filter(self.suite_tree)
        self._apply_filter(self.element_tree)

    def _rebuild(self) -> None:
        trees = (self.suite_tree, self.element_tree)
        for tree in trees:
            tree.setUpdatesEnabled(False)
            tree.blockSignals(True)
        try:
            self.suite_tree.clear()
            self.element_tree.clear()
            if self._project is None:
                return

            root = QTreeWidgetItem([self._project.name])
            root.setIcon(0, FIF.FOLDER.icon())
            root.setData(0, _SEARCH_ROLE, self._project.name)
            self.suite_tree.addTopLevelItem(root)
            self._add_group(root, self._project.root_group)
            root.setExpanded(True)

            for el in self._project.shared_elements:
                item = QTreeWidgetItem([self._element_label(el)])
                item.setIcon(0, FIF.TAG.icon())
                tip = el.primary_playwright or el.css or el.xpath or el.element_id
                item.setToolTip(0, tip)
                item.setData(0, Qt.ItemDataRole.UserRole, el)
                item.setData(
                    0,
                    _SEARCH_ROLE,
                    f"{el.element_id} {el.friendly_name} {el.css or ''} {el.role or ''}",
                )
                self.element_tree.addTopLevelItem(item)
        finally:
            for tree in trees:
                tree.blockSignals(False)
                tree.setUpdatesEnabled(True)
        self._apply_filter(self.suite_tree)
        self._apply_filter(self.element_tree)

    def _apply_filter(self, tree: TreeWidget) -> None:
        kw = self._filter
        root = tree.invisibleRootItem()
        self._filter_item(root, kw)

    def _filter_item(self, item: QTreeWidgetItem, kw: str) -> bool:
        matched_child = False
        for i in range(item.childCount()):
            child = item.child(i)
            if child is not None and self._filter_item(child, kw):
                matched_child = True
        blob = item.data(0, _SEARCH_ROLE)
        text = str(blob) if blob else item.text(0)
        self_match = not kw or kw in text.lower()
        visible = self_match or matched_child
        if item.parent() is not None:
            item.setHidden(not visible)
        return visible

    def _element_label(self, el: SharedElement) -> str:
        role = el.role or "Element"
        return f"[{el.element_id}] {role}: {el.friendly_name}"

    def _add_group(self, parent: QTreeWidgetItem, group) -> None:  # type: ignore[no-untyped-def]
        node = QTreeWidgetItem([group.name])
        node.setIcon(0, FIF.FOLDER.icon())
        node.setData(0, _SEARCH_ROLE, group.name)
        parent.addChild(node)
        for scenario in group.scenarios:
            sc = QTreeWidgetItem([f"{scenario.code}: {scenario.title}"])
            sc.setIcon(0, FIF.DOCUMENT.icon())
            sc.setData(0, _SEARCH_ROLE, f"{scenario.code} {scenario.title}")
            for case in scenario.cases:
                sc.addChild(self._case_item(case))
            node.addChild(sc)
            sc.setExpanded(True)
        for suite in group.suites:
            su = QTreeWidgetItem([f"Suite: {suite.name}"])
            su.setIcon(0, FIF.FOLDER.icon())
            su.setData(0, _SEARCH_ROLE, suite.name)
            for case in suite.cases:
                su.addChild(self._case_item(case))
            node.addChild(su)
            su.setExpanded(True)
        for child in group.children:
            self._add_group(node, child)
        node.setExpanded(True)

    def _case_item(self, case: TestCase) -> QTreeWidgetItem:
        rollup = case_binding_rollups(case)
        item = QTreeWidgetItem([f"{STATUS_DOT} {case.code}: {case.title}"])
        item.setIcon(0, FIF.DOCUMENT.icon())
        item.setData(0, Qt.ItemDataRole.UserRole, case)
        item.setData(0, _SEARCH_ROLE, f"{case.code} {case.title}")
        item.setToolTip(0, f"{case.code} — {rollup}")
        color = {
            "FAILED": ICON_COLOR_DANGER,
            BINDING_UNBOUND: ICON_COLOR_WARN,
            BINDING_BOUND: ICON_COLOR_SUCCESS,
            "HEALED": ICON_COLOR_HEALED,
        }.get(rollup, ICON_COLOR_WARN)
        item.setForeground(0, QBrush(QColor(color)))
        return item

    def _on_case_selection(self) -> None:
        items = self.suite_tree.selectedItems()
        if not items:
            return
        data = items[0].data(0, Qt.ItemDataRole.UserRole)
        if isinstance(data, TestCase):
            self.case_selected.emit(data)

    def _on_element_selection(self) -> None:
        items = self.element_tree.selectedItems()
        if not items:
            return
        data = items[0].data(0, Qt.ItemDataRole.UserRole)
        if isinstance(data, SharedElement):
            self.element_selected.emit(data)
