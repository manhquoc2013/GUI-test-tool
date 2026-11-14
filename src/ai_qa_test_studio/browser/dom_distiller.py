"""Distill interactive DOM — thin wrapper over SnapshotDistiller."""

from __future__ import annotations

from typing import Any

from ai_qa_test_studio.browser.snapshot_distiller import SnapshotDistiller
from ai_qa_test_studio.core.models import DistilledNode
from ai_qa_test_studio.core.token_budget import TokenBudget


class DomDistiller:
    def __init__(self, budget: TokenBudget | None = None) -> None:
        self.budget = budget or TokenBudget()
        self._snapshot = SnapshotDistiller(budget=self.budget)

    async def distill(self, page: Any) -> list[DistilledNode]:
        return await self._snapshot.distill(page)

    async def extract(self, page: Any) -> tuple[str, dict[str, dict[str, Any]]]:
        return await self._snapshot.extract_legacy(page)
