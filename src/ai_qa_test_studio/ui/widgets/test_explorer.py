from __future__ import annotations

from PySide6.QtCore import Qt, Signal
from PySide6.QtWidgets import QTreeWidget, QTreeWidgetItem

from ai_qa_test_studio.core.models import Project, TestCase


class TestExplorer(QTreeWidget):
    case_selected = Signal(object)

    def __init__(self, parent=None) -> None:  # type: ignore[no-untyped-def]
        super().__init__(parent)
        self.setHeaderLabels(["Test Explorer"])
        self.setMinimumWidth(220)
        self.itemSelectionChanged.connect(self._on_selection)

    def load_project(self, project: Project) -> None:
        self.clear()
        root = QTreeWidgetItem([project.name])
        self.addTopLevelItem(root)
        self._add_group(root, project.root_group)
        elements = QTreeWidgetItem(["Shared Elements"])
        for el in project.shared_elements:
            elements.addChild(QTreeWidgetItem([f"{el.element_id} — {el.friendly_name}"]))
        root.addChild(elements)
        root.setExpanded(True)

    def _add_group(self, parent: QTreeWidgetItem, group) -> None:  # type: ignore[no-untyped-def]
        node = QTreeWidgetItem([group.name])
        parent.addChild(node)
        for scenario in group.scenarios:
            sc = QTreeWidgetItem([f"{scenario.code}: {scenario.title}"])
            for case in scenario.cases:
                item = QTreeWidgetItem([f"{case.code}: {case.title}"])
                item.setData(0, Qt.ItemDataRole.UserRole, case)
                sc.addChild(item)
            node.addChild(sc)
        for suite in group.suites:
            su = QTreeWidgetItem([f"Suite: {suite.name}"])
            for case in suite.cases:
                item = QTreeWidgetItem([f"{case.code}: {case.title}"])
                item.setData(0, Qt.ItemDataRole.UserRole, case)
                su.addChild(item)
            node.addChild(su)
        for child in group.children:
            self._add_group(node, child)

    def _on_selection(self) -> None:
        items = self.selectedItems()
        if not items:
            return
        data = items[0].data(0, Qt.ItemDataRole.UserRole)
        if isinstance(data, TestCase):
            self.case_selected.emit(data)
