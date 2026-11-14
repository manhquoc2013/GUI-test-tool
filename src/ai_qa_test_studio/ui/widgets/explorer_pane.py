"""Left explorer: SearchLineEdit + SegmentedWidget (Suites | Elements) + TreeWidget."""

from __future__ import annotations

from typing import Any

from PySide6.QtCore import Qt, Signal
from PySide6.QtGui import QAction, QBrush, QColor, QKeySequence
from PySide6.QtWidgets import QMenu, QStackedWidget, QTreeWidgetItem, QVBoxLayout, QWidget
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
    UI_EXPLORER_CONTENT_H_MARGIN,
    UI_EXPLORER_DENSITY_WIDE,
    UI_EXPLORER_MIN_WIDTH,
    UI_TREE_INDENT,
    UI_TREE_ROW_HEIGHT,
)
from ai_qa_test_studio.core.async_bridge import run_sync
from ai_qa_test_studio.core.index_sync import ProjectIndexSync
from ai_qa_test_studio.core.models import (
    Project,
    SharedElement,
    TestCase,
    TestGroup,
    TestScenario,
    TestSuite,
)
from ai_qa_test_studio.core.project_tree_ops import collect_cases
from ai_qa_test_studio.ui.formatting import case_binding_rollups
from ai_qa_test_studio.ui.theme import get_theme_tokens
from ai_qa_test_studio.ui.theme_bind import bind_widget_theme

_SEARCH_PLACEHOLDER_WIDE = "Tìm test case, element…"
_SEARCH_PLACEHOLDER_NARROW = "Tìm…"
# Search blob for filter-without-rebuild (avoids TreeWidget.clear AX churn on macOS)
_SEARCH_ROLE = int(Qt.ItemDataRole.UserRole) + 1
_NODE_ID_ROLE = int(Qt.ItemDataRole.UserRole) + 2

TreePayload = TestCase | TestScenario | TestSuite | TestGroup


class ExplorerPane(QWidget):
    case_selected = Signal(object)
    element_selected = Signal(object)
    node_selected = Signal(object)
    rename_requested = Signal(object)
    delete_requested = Signal(object)
    run_requested = Signal(object)

    def __init__(self, parent: QWidget | None = None) -> None:
        super().__init__(parent)
        self.setObjectName("ExplorerDock")
        self.setAccessibleName("Test Explorer")
        self.setMinimumWidth(UI_EXPLORER_MIN_WIDTH)
        self.setAttribute(Qt.WidgetAttribute.WA_StyledBackground, True)
        self._project: Project | None = None
        self._filter = ""
        self._fts_ids: set[str] | None = None
        self._index_sync: ProjectIndexSync | None = None
        self._element_count = 0
        self._density = "wide"
        self._mutation_enabled = True
        self._selected_payload: TreePayload | None = None

        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(6)

        self.search_input = SearchLineEdit(self)
        self.search_input.setPlaceholderText(_SEARCH_PLACEHOLDER_WIDE)
        self.search_input.setAccessibleName("Ô tìm kiếm Explorer")
        self.search_input.textChanged.connect(self._on_filter)
        layout.addWidget(self.search_input)

        self.pivot = SegmentedWidget(self)
        self.pivot.setAccessibleName("Chuyển Cases, Elements hoặc Reports")
        self.pivot.addItem(routeKey="suites", text="Cases", icon=FIF.FOLDER)
        self.pivot.addItem(routeKey="elements", text="EL (0)", icon=FIF.TAG)
        self.pivot.addItem(routeKey="reports", text="Reports", icon=FIF.DOCUMENT)
        self.pivot.setCurrentItem("suites")
        for key, tip in (
            ("suites", "Cases"),
            ("elements", "Elements (0)"),
            ("reports", "Reports"),
        ):
            self.pivot.widget(key).setToolTip(tip)
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
        self.suite_tree.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.suite_tree.customContextMenuRequested.connect(self._on_suite_context_menu)

        self._act_rename = QAction("Sửa tên", self)
        self._act_rename.setShortcut(QKeySequence(Qt.Key.Key_F2))
        self._act_rename.triggered.connect(self._emit_rename)
        self._act_delete = QAction("Xóa", self)
        self._act_delete.setShortcut(QKeySequence(Qt.Key.Key_Delete))
        self._act_delete.triggered.connect(self._emit_delete)
        self._act_run = QAction("Chạy", self)
        self._act_run.triggered.connect(self._emit_run)
        self.addAction(self._act_rename)
        self.addAction(self._act_delete)

        self._apply_theme_style()
        self._theme_slot = bind_widget_theme(self, self._apply_theme_style)

    def _apply_theme_style(self) -> None:
        t = get_theme_tokens()
        self.setStyleSheet(
            f"#ExplorerDock {{"
            f"background-color: {t.bg_dock};"
            f"border-right: 1px solid {t.border_hairline};"
            f"}}"
        )

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
        """Fit pivot into explorer: labeled only when content width ≥ DENSITY_WIDE."""
        usable = max(0, width - UI_EXPLORER_CONTENT_H_MARGIN)
        count_tip = f"Elements ({self._element_count})"
        if usable >= UI_EXPLORER_DENSITY_WIDE:
            labels = ("Cases", f"EL ({self._element_count})", "Reports")
            tips = ("Cases", count_tip, "Reports")
            density = "wide"
            search_ph = _SEARCH_PLACEHOLDER_WIDE
        else:
            labels = ("", "", "")
            tips = ("Cases", count_tip, "Reports")
            density = "compact"
            search_ph = _SEARCH_PLACEHOLDER_NARROW
        self._density = density
        self.search_input.setPlaceholderText(search_ph)
        for route, text, tip in zip(
            ("suites", "elements", "reports"),
            labels,
            tips,
            strict=True,
        ):
            self.pivot.setItemText(route, text)
            item = self.pivot.widget(route)
            item.setToolTip(tip)
            item.adjustSize()
        self.pivot.adjustSize()

    def set_element_count(self, count: int) -> None:
        self._element_count = max(0, count)
        self.apply_density(self.width() or UI_EXPLORER_MIN_WIDTH)

    def set_index_sync(self, index_sync: ProjectIndexSync | None) -> None:
        self._index_sync = index_sync

    def set_mutation_enabled(self, enabled: bool) -> None:
        self._mutation_enabled = enabled
        self._act_rename.setEnabled(enabled)
        self._act_delete.setEnabled(enabled and self._can_delete_selection())
        self._act_run.setEnabled(True)

    def current_payload(self) -> TreePayload | None:
        return self._selected_payload

    def selection_case_count(self) -> int:
        payload = self._selected_payload
        if payload is None:
            return 0
        return len(collect_cases(payload))

    def is_root_group_selected(self) -> bool:
        if self._project is None or self._selected_payload is None:
            return False
        return (
            isinstance(self._selected_payload, TestGroup)
            and self._selected_payload.id == self._project.root_group.id
        )

    def load_project(self, project: Project, *, restore_state: dict[str, Any] | None = None) -> None:
        self._project = project
        self.set_element_count(len(project.shared_elements))
        self._rebuild()
        self.reports_tree.clear()
        tip = QTreeWidgetItem(["Chưa có báo cáo — chạy test rồi Export HTML"])
        tip.setFlags(Qt.ItemFlag.NoItemFlags)
        self.reports_tree.addTopLevelItem(tip)
        if restore_state:
            self.restore_tree_state(restore_state)

    def snapshot_tree_state(self) -> dict[str, Any]:
        expanded: list[str] = []
        root = self.suite_tree.invisibleRootItem()
        self._collect_expanded(root, expanded)
        selected_id = ""
        items = self.suite_tree.selectedItems()
        if items:
            sid = items[0].data(0, _NODE_ID_ROLE)
            if sid:
                selected_id = str(sid)
        return {"expanded_ids": expanded, "selected_id": selected_id}

    def restore_tree_state(self, state: dict[str, Any]) -> None:
        expanded = {str(x) for x in state.get("expanded_ids") or []}
        selected_id = str(state.get("selected_id") or "")
        root = self.suite_tree.invisibleRootItem()
        self._restore_expanded(root, expanded)
        if selected_id:
            target = self._find_by_node_id(root, selected_id)
            if target is not None:
                self.suite_tree.setCurrentItem(target)
                self.suite_tree.scrollToItem(target)

    def _collect_expanded(self, item: QTreeWidgetItem, out: list[str]) -> None:
        for i in range(item.childCount()):
            child = item.child(i)
            if child is None:
                continue
            nid = child.data(0, _NODE_ID_ROLE)
            if child.isExpanded() and nid:
                out.append(str(nid))
            self._collect_expanded(child, out)

    def _restore_expanded(self, item: QTreeWidgetItem, expanded: set[str]) -> None:
        for i in range(item.childCount()):
            child = item.child(i)
            if child is None:
                continue
            nid = child.data(0, _NODE_ID_ROLE)
            if nid and str(nid) in expanded:
                child.setExpanded(True)
            self._restore_expanded(child, expanded)

    def _find_by_node_id(self, parent: QTreeWidgetItem, node_id: str) -> QTreeWidgetItem | None:
        for i in range(parent.childCount()):
            child = parent.child(i)
            if child is None:
                continue
            nid = child.data(0, _NODE_ID_ROLE)
            if nid and str(nid) == node_id:
                return child
            nested = self._find_by_node_id(child, node_id)
            if nested is not None:
                return nested
        return None

    def _on_filter(self, text: str) -> None:
        self._filter = text.strip().lower()
        self._fts_ids = None
        if self._index_sync is not None and self._filter:
            try:
                hits = run_sync(self._index_sync.search(self._filter))
                self._fts_ids = {hit.entity_id for hit in hits}
            except (OSError, RuntimeError, ValueError):
                self._fts_ids = None
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
        fts_match = False
        if self._fts_ids is not None:
            payload = item.data(0, Qt.ItemDataRole.UserRole)
            if isinstance(payload, TestCase) and payload.id in self._fts_ids:
                fts_match = True
            elif isinstance(payload, SharedElement) and payload.element_id in self._fts_ids:
                fts_match = True
        self_match = not kw or kw in text.lower() or fts_match
        visible = self_match or matched_child
        if item.parent() is not None:
            item.setHidden(not visible)
        return visible

    def _element_label(self, el: SharedElement) -> str:
        role = el.role or "Element"
        return f"[{el.element_id}] {role}: {el.friendly_name}"

    def _bind_payload(self, item: QTreeWidgetItem, payload: TreePayload, search: str) -> None:
        item.setData(0, Qt.ItemDataRole.UserRole, payload)
        item.setData(0, _NODE_ID_ROLE, payload.id)
        item.setData(0, _SEARCH_ROLE, search)

    def _add_group(self, parent: QTreeWidgetItem, group: TestGroup) -> None:
        node = QTreeWidgetItem([group.name])
        node.setIcon(0, FIF.FOLDER.icon())
        self._bind_payload(node, group, group.name)
        node.setToolTip(0, group.name)
        parent.addChild(node)
        for scenario in group.scenarios:
            label = f"{scenario.code}: {scenario.title}"
            sc = QTreeWidgetItem([label])
            sc.setIcon(0, FIF.DOCUMENT.icon())
            self._bind_payload(sc, scenario, f"{scenario.code} {scenario.title}")
            sc.setToolTip(0, label)
            for case in scenario.cases:
                sc.addChild(self._case_item(case))
            node.addChild(sc)
            sc.setExpanded(True)
        for suite in group.suites:
            label = f"Suite: {suite.name}"
            su = QTreeWidgetItem([label])
            su.setIcon(0, FIF.FOLDER.icon())
            self._bind_payload(su, suite, suite.name)
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
        self._bind_payload(item, case, f"{case.code} {case.title}")
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
            self._selected_payload = None
            return
        data = items[0].data(0, Qt.ItemDataRole.UserRole)
        if isinstance(data, (TestCase, TestScenario, TestSuite, TestGroup)):
            self._selected_payload = data
            self.node_selected.emit(data)
            self._act_delete.setEnabled(self._mutation_enabled and self._can_delete_selection())
            if isinstance(data, TestCase):
                self.case_selected.emit(data)

    def _can_delete_selection(self) -> bool:
        if self._selected_payload is None:
            return False
        if self.is_root_group_selected():
            return False
        return True

    def _on_element_selection(self) -> None:
        items = self.element_tree.selectedItems()
        if not items:
            return
        data = items[0].data(0, Qt.ItemDataRole.UserRole)
        if isinstance(data, SharedElement):
            self.element_selected.emit(data)

    def _on_suite_context_menu(self, pos: Any) -> None:
        item = self.suite_tree.itemAt(pos)
        if item is None:
            return
        self.suite_tree.setCurrentItem(item)
        payload = item.data(0, Qt.ItemDataRole.UserRole)
        if not isinstance(payload, (TestCase, TestScenario, TestSuite, TestGroup)):
            return
        self._selected_payload = payload
        menu = QMenu(self)
        if self._mutation_enabled:
            menu.addAction(self._act_rename)
            if self._can_delete_selection():
                menu.addAction(self._act_delete)
        cases = collect_cases(payload)
        run_act = menu.addAction(f"Chạy ({len(cases)})")
        run_act.setEnabled(len(cases) > 0)
        chosen = menu.exec(self.suite_tree.viewport().mapToGlobal(pos))
        if chosen is run_act and len(cases) > 0:
            self.run_requested.emit(payload)

    def _emit_rename(self) -> None:
        if not self._mutation_enabled or self._selected_payload is None:
            return
        self.rename_requested.emit(self._selected_payload)

    def _emit_delete(self) -> None:
        if not self._mutation_enabled or not self._can_delete_selection():
            return
        if self._selected_payload is None:
            return
        self.delete_requested.emit(self._selected_payload)

    def _emit_run(self) -> None:
        if self._selected_payload is None:
            return
        if not collect_cases(self._selected_payload):
            return
        self.run_requested.emit(self._selected_payload)

    def refresh_case_status_labels(self) -> None:
        """Rebuild labels after last_status changes without full project reload."""
        if self._project is None:
            return
        state = self.snapshot_tree_state()
        self._rebuild()
        self.restore_tree_state(state)
