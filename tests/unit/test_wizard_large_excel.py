from __future__ import annotations

from pathlib import Path

import pytest

from ai_qa_test_studio.core.wizard_documents import load_wizard_document


def test_wizard_xlsx_imports_all_rows_beyond_old_500_cap(tmp_path: Path) -> None:
    pytest.importorskip("openpyxl")
    from openpyxl import Workbook

    wb = Workbook()
    ws = wb.active
    assert ws is not None
    ws.title = "Test Cases"
    ws.append(
        [
            "Test Case ID",
            "Title",
            "Step No",
            "Action",
            "Expected Result",
        ]
    )
    total = 650
    for i in range(total):
        ws.append(
            [
                f"TC-{i:04d}",
                f"Case {i}",
                "1",
                "click",
                f"ok {i}",
            ]
        )
    path = tmp_path / "big.xlsx"
    wb.save(path)
    doc = load_wizard_document(path)
    assert not doc.error
    case_count = sum(len(s.cases) for s in doc.scenarios)
    assert case_count >= total
    # Preview markdown stays bounded (not 650 full rows dumped)
    assert "more rows" in doc.markdown or doc.markdown.count("\n") < total * 2
