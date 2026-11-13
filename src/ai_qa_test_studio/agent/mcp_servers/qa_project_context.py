"""qa-project-context — lean case/SRS/test-data/DOM briefs."""

from __future__ import annotations

import json
from collections.abc import Callable
from dataclasses import dataclass, field
from typing import Any

from ai_qa_test_studio.constants import TOKEN_DOM_SOFT_CAP
from ai_qa_test_studio.core.token_budget import ContextCompressor

DomFn = Callable[[], str | None]


@dataclass
class QaProjectContextServer:
    compressor: ContextCompressor = field(default_factory=ContextCompressor)
    case_json: str = ""
    srs: str = ""
    test_data_rows: list[dict[str, Any]] = field(default_factory=list)
    dom_provider: DomFn | None = None

    def bind(
        self,
        *,
        case_json: str = "",
        srs: str = "",
        test_data_rows: list[dict[str, Any]] | None = None,
        dom_provider: DomFn | None = None,
    ) -> None:
        self.case_json = case_json
        self.srs = srs
        self.test_data_rows = list(test_data_rows or [])
        self.dom_provider = dom_provider

    def case_brief(self, case_id: str = "") -> dict[str, Any]:
        raw = self.case_json or "{}"
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            data = {"raw": raw[:400]}
        brief = {
            "id": case_id or data.get("id") or data.get("code") or "",
            "title": data.get("title") or "",
            "steps": len(data.get("steps") or []),
            "expected": (data.get("expected_final_result") or data.get("expectedFinalResult") or "")[:200],
        }
        text = json.dumps(brief, ensure_ascii=False)
        return {"brief": self.compressor.compress_text(text), "chars": len(text)}

    def srs_excerpt(self, cap: int = 800) -> dict[str, Any]:
        text = (self.srs or "")[: max(1, cap)]
        return {"excerpt": self.compressor.budget.truncate_to_cap(text, cap=cap)}

    def test_data_sample(self, n: int = 3) -> dict[str, Any]:
        rows = self.test_data_rows[: max(0, n)]
        return {"rows": rows, "total": len(self.test_data_rows)}

    def dom_distill(self) -> dict[str, Any]:
        if self.dom_provider is None:
            return {"dom": "", "available": False}
        raw = self.dom_provider() or ""
        return {
            "dom": self.compressor.budget.truncate_to_cap(raw, cap=TOKEN_DOM_SOFT_CAP),
            "available": bool(raw),
        }

    def invoke(self, tool: str, args: dict[str, Any]) -> dict[str, Any]:
        if tool == "case_brief":
            return self.case_brief(str(args.get("case_id") or ""))
        if tool == "srs_excerpt":
            return self.srs_excerpt(int(args.get("cap") or 800))
        if tool == "test_data_sample":
            return self.test_data_sample(int(args.get("n") or 3))
        if tool == "dom_distill":
            return self.dom_distill()
        raise KeyError(f"unknown tool: {tool}")
