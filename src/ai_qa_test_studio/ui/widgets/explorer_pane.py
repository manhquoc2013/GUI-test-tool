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
    UI_EXPLORER_DENSITY_WIDE,
    UI_EXPLORER_MIN_WIDTH,
    UI_TREE_INDENT,
    UI_TREE_ROW_HEIGHT,
)
from ai_qa_test_studio.core.models import Project, SharedElement, TestCase
from ai_qa_test_studio.ui.formatting import case_binding_rollups

_SEARCH_PLACEHOLDER_WIDE = "Tìm test case, element…"
_SEARCH_PLACEHOLDER_NARROW = "Tìm…"
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
        self._element_count = 0
        self._density = "wide"

        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(6)

        self.search_input = SearchLineEdit(self)
        self.search_input.setPlaceholderText(_SEARCH_PLACEHOLDER_WIDE)
        self.search_input.setAccessibleName("Ô tìm kiếm Explorer")
        self.search_input.textChanged.connect(self._on_filter)
        layout.addWidget(self.search_input)

        self.pivot = SegmentedWidget(self)
        self.pivot.setAccessibleName("Chuyển Kịch bản, Elements hoặc Báo cáo")
        self.pivot.addItem(routeKey="suites", text="Kịch bản", icon=FIF.FOLDER)
        self.pivot.addItem(routeKey="elements", text="Elements (0)", icon=FIF.TAG)
        self.pivot.addItem(routeKey="reports", text="Báo cáo", icon=FIF.DOCUMENT)
        self.pivot.setCurrentItem("suites")
        layout.addWidget(self.pivot)

        self.stack = QStackedWidget(self)
        self.suite_tree = self._make_tree("Cây Test Suites và Cases")
        self.element_tree = self._make_tree("Kho Shared Elements")
        self.reports_tree = self._make_tree("Danh sách báo cáo")
        self.stack.addWidget(self.suite_tree)
        self.stack.addWidget(self.element_tree)
        self.stack.addWidget(self.reports_tree)
        layout.addWidget(self.stack, stretch=1)

        self.pivot.currentItemChanged.connect(self._on_pivot_changed)
        self.suite_tree.itemSelectionChanged.connect(self._on_case_selection)
        self.element_tree.itemSelectionChanged.connect(self._on_element_selection)

    def _make_tree(self, accessible_name: str) -> TreeWidget:
        tree = TreeWidget(self)
        tree.setHeaderHidden(True)
        tree.header().setStretchLastSection(True)
        tree.setIndentation(UI_TREE_INDENT)
        tree.setUniformRowHeights(True)
        tree.setTextElideMode(Qt.TextElideMode.ElideRight)
        tree.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff)
        tree.setAccessibleName(accessible_name)
        tree.setAttribute(Qt.WidgetAttribute.WA_MacShowFocusRect, False)
        tree.setStyleSheet(f"TreeWidget::item {{ height: {UI_TREE_ROW_HEIGHT}px; }}")
        return tree

    def _on_pivot_changed(self, key: str) -> None:
        index = {"suites": 0, "elements": 1, "reports": 2}.get(key, 0)
        self.stack.setCurrentIndex(index)

    def apply_density(self, width: int) -> None:
        if width >= UI_EXPLORER_DENSITY_WIDE:
            suites = "Kịch bản"
            elements = f"Elements ({self._element_count})"
            reports = "Báo cáo"
            density = "wide"
            search_ph = _SEARCH_PLACEHOLDER_WIDE
        elif width >= UI_EXPLORER_MIN_WIDTH:
            suites = "Kịch bản"
            elements = f"EL ({self._element_count})"
            reports = "BC"
            density = "medium"
            search_ph = _SEARCH_PLACEHOLDER_NARROW
        else:
            suites = ""
            elements = str(self._element_count)
            reports = ""
            density = "narrow"
            search_ph = _SEARCH_PLACEHOLDER_NARROW
        self._density = density
        self.search_input.setPlaceholderText(search_ph)
        self.pivot.setItemText("suites", suites)
        self.pivot.setItemText("elements", elements)
        self.pivot.setItemText("reports", reports)

    def set_element_count(self, count: int) -> None:
        self._element_count = max(0, count)
        self.apply_density(self.width() or UI_EXPLORER_MIN_WIDTH)

    def load_project(self, project: Project) -> None:
        self._project = project
        self.set_element_count(len(project.shared_elements))
        self._rebuild()
        self.reports_tree.clear()
        tip = QTreeWidgetItem(["Chưa có báo cáo — chạy test rồi Export HTML"])
        tip.setFlags(Qt.ItemFlag.NoItemFlags)
        self.reports_tree.addTopLevelItem(tip)

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
        node.setToolTip(0, group.name)
        parent.addChild(node)
        for scenario in group.scenarios:
            label = f"{scenario.code}: {scenario.title}"
            sc = QTreeWidgetItem([label])
            sc.setIcon(0, FIF.DOCUMENT.icon())
            sc.setData(0, _SEARCH_ROLE, f"{scenario.code} {scenario.title}")
            sc.setToolTip(0, label)
            for case in scenario.cases:
                sc.addChild(self._case_item(case))
            node.addChild(sc)
            sc.setExpanded(True)
        for suite in group.suites:
            label = f"Suite: {suite.name}"
            su = QTreeWidgetItem([label])
            su.setIcon(0, FIF.FOLDER.icon())
            su.setData(0, _SEARCH_ROLE, suite.name)
            su.setToolTip(0, label)
            for case in suite.cases:
                su.addChild(self._case_item(case))
            node.addChild(su)
            su.setExpanded(True)
        for child in group.children:
            self._add_group(node, child)
        node.setExpanded(True)

    def _case_item(self, case: TestCase) -> QTreeWidgetItem:
        rollup = case_binding_rollups(case)
        label = f"{STATUS_DOT} {case.code}: {case.title}"
        item = QTreeWidgetItem([label])
        item.setIcon(0, FIF.DOCUMENT.icon())
        item.setData(0, Qt.ItemDataRole.UserRole, case)
        item.setData(0, _SEARCH_ROLE, f"{case.code} {case.title}")
        item.setToolTip(0, f"{case.code}: {case.title}\n{rollup}")
        color = {
            "FAILED": ICON_COLOR_DANGER,
            BINDING_UNBOUND: ICON_COLOR_WARN,
            BINDING_BOUND: ICON_COLOR_SUCCESS,
            "HEALED": ICON_COLOR_HEALED,
        }.get(rollup, ICON_COLOR_WARN)
        item.setForeground(0, QBrush(QColor(color)))
        return item

    def focus_path(self, module: str, suite: str = "") -> None:
        """Expand/select tree nodes matching module (and optional suite) labels."""
        self.pivot.setCurrentItem("suites")
        root = self.suite_tree.invisibleRootItem()
        target = self._find_named_item(root, module) if module else None
        if target is None:
            return
        target.setExpanded(True)
        self.suite_tree.setCurrentItem(target)
        if suite:
            child = self._find_named_item(target, suite)
            if child is not None:
                child.setExpanded(True)
                self.suite_tree.setCurrentItem(child)
                self.suite_tree.scrollToItem(child)

    def _find_named_item(self, parent: QTreeWidgetItem, name: str) -> QTreeWidgetItem | None:
        needle = name.lower()
        for i in range(parent.childCount()):
            child = parent.child(i)
            if child is None:
                continue
            blob = child.data(0, _SEARCH_ROLE)
            text = str(blob) if blob else child.text(0)
            if needle and needle in text.lower():
                return child
            nested = self._find_named_item(child, name)
            if nested is not None:
                return nested
        return None

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
