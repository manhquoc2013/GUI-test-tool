from __future__ import annotations

from pathlib import Path

from openpyxl import Workbook

from ai_qa_test_studio.constants import (
    WIZARD_DOC_KIND_SRS,
    WIZARD_DOC_KIND_TEST_CASE,
    WIZARD_DOC_KIND_TEST_SCENARIO,
)
from ai_qa_test_studio.core.models import TestScenario
from ai_qa_test_studio.core.wizard_documents import (
    WizardDocument,
    build_wizard_llm_context,
    classify_wizard_document,
    collect_imported_scenarios,
    load_wizard_document,
    merge_wizard_scenarios,
)


def test_classify_by_filename_tokens(tmp_path: Path) -> None:
    assert (
        classify_wizard_document(tmp_path / "login_test_cases.csv") == WIZARD_DOC_KIND_TEST_CASE
    )
    assert (
        classify_wizard_document(tmp_path / "checkout_scenarios.md")
        == WIZARD_DOC_KIND_TEST_SCENARIO
    )
    assert classify_wizard_document(tmp_path / "product_srs.pdf") == WIZARD_DOC_KIND_SRS


def test_load_csv_test_cases_imports_steps(tmp_path: Path) -> None:
    path = tmp_path / "suite_test_cases.csv"
    path.write_text(
        "Test Case ID,Scenario ID,Title,Step No,Action,Test Data,Expected Result\n"
        "TC01,TS_LOGIN,Login happy,1,goto,https://app.example/,page open\n"
        "TC01,TS_LOGIN,Login happy,2,fill,user@x.com,filled\n"
        "TC01,TS_LOGIN,Login happy,3,click,,submitted\n",
        encoding="utf-8",
    )
    doc = load_wizard_document(path)
    assert doc.error == ""
    assert doc.kind == WIZARD_DOC_KIND_TEST_CASE
    assert len(doc.scenarios) == 1
    assert doc.scenarios[0].code == "TS_LOGIN"
    assert len(doc.scenarios[0].cases) == 1
    assert len(doc.scenarios[0].cases[0].steps) == 3
    assert doc.scenarios[0].cases[0].steps[0].action == "goto"


def test_load_xlsx_scenarios_sheet(tmp_path: Path) -> None:
    path = tmp_path / "outline.xlsx"
    wb = Workbook()
    ws = wb.active
    assert ws is not None
    ws.title = "Test Scenarios"
    ws.append(["Scenario ID", "Title", "Requirement", "Description"])
    ws.append(["TS_A", "Auth happy", "REQ-1", "Login success"])
    ws.append(["TS_B", "Auth neg", "REQ-1", "Bad password"])
    wb.save(path)
    doc = load_wizard_document(path)
    assert doc.error == ""
    assert doc.kind == WIZARD_DOC_KIND_TEST_SCENARIO
    codes = {s.code for s in doc.scenarios}
    assert codes == {"TS_A", "TS_B"}


def test_load_markdown_cases_and_context_merge(tmp_path: Path) -> None:
    path = tmp_path / "cases.md"
    path.write_text(
        "# Suite\n\n## Test Case: Reset password\n\n## Test Case: Lock account\n",
        encoding="utf-8",
    )
    doc = load_wizard_document(path)
    assert doc.kind == WIZARD_DOC_KIND_TEST_CASE
    assert len(doc.scenarios[0].cases) == 2
    context = build_wizard_llm_context(
        freeform_markdown="Feature notes",
        documents=[doc],
    )
    assert "Feature notes" in context
    assert "Attached (Test Case)" in context
    assert "cases.md" in context


def test_merge_and_collect_dedupe() -> None:
    imported = [
        TestScenario(id="1", code="TS_A", title="A"),
        TestScenario(id="2", code="TS_B", title="B"),
    ]
    generated = [
        TestScenario(id="3", code="TS_A", title="dup"),
        TestScenario(id="4", code="TS_C", title="C"),
    ]
    merged = merge_wizard_scenarios(imported, generated)
    assert [s.code for s in merged] == ["TS_A", "TS_B", "TS_C"]
    docs = [
        WizardDocument(
            path=Path("a.csv"),
            kind=WIZARD_DOC_KIND_TEST_CASE,
            markdown="x",
            scenarios=imported,
        ),
        WizardDocument(path=Path("b.md"), kind=WIZARD_DOC_KIND_SRS, markdown="srs"),
    ]
    assert len(collect_imported_scenarios(docs)) == 2
