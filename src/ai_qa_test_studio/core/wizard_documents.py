"""AI Wizard multi-doc ingest: MarkItDown load, auto-classify, structured import."""

from __future__ import annotations

import csv
import logging
import re
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Literal, cast

from ai_qa_test_studio.constants import (
    WIZARD_DOC_CLASSIFY_CASE_TOKENS,
    WIZARD_DOC_CLASSIFY_SCENARIO_TOKENS,
    WIZARD_DOC_CLASSIFY_SRS_TOKENS,
    WIZARD_DOC_CONTEXT_CAP_CHARS,
    WIZARD_DOC_CSV_SUFFIXES,
    WIZARD_DOC_HEADER_ACTION,
    WIZARD_DOC_HEADER_CASE_ID,
    WIZARD_DOC_HEADER_SCENARIO_ID,
    WIZARD_DOC_HEADER_STEP_NO,
    WIZARD_DOC_HEADER_TITLE,
    WIZARD_DOC_KIND_LABELS,
    WIZARD_DOC_KIND_SRS,
    WIZARD_DOC_KIND_TEST_CASE,
    WIZARD_DOC_KIND_TEST_SCENARIO,
    WIZARD_DOC_MARKDOWN_SUFFIXES,
    WIZARD_DOC_SHEET_CASES,
    WIZARD_DOC_SHEET_SCENARIOS,
    WIZARD_DOC_XLSX_SUFFIXES,
)
from ai_qa_test_studio.core.models import StepAction, TestCase, TestScenario, TestStep
from ai_qa_test_studio.core.token_budget import ContextCompressor

logger = logging.getLogger(__name__)

WizardDocKind = Literal["srs", "test_scenario", "test_case"]

_STEP_ACTIONS: frozenset[str] = frozenset(
    {
        "goto",
        "click",
        "fill",
        "select",
        "press_enter",
        "hover",
        "assert_visible",
        "assert_text",
        "assert_url",
        "verify_db",
    }
)
_MD_SCENARIO_RE = re.compile(
    r"^#{1,3}\s*(?:scenario|test\s*scenario|ts)[:\s#-]*([^\n]+)",
    re.IGNORECASE | re.MULTILINE,
)
_MD_CASE_RE = re.compile(
    r"^#{1,3}\s*(?:test\s*case|case|tc)[:\s#-]*([^\n]+)",
    re.IGNORECASE | re.MULTILINE,
)


@dataclass
class WizardDocument:
    path: Path
    kind: WizardDocKind
    markdown: str
    scenarios: list[TestScenario] = field(default_factory=list)
    error: str = ""

    @property
    def kind_label(self) -> str:
        return WIZARD_DOC_KIND_LABELS.get(self.kind, self.kind)

    @property
    def display_name(self) -> str:
        return f"{self.path.name} · {self.kind_label}"


def _norm_header(value: object) -> str:
    return re.sub(r"\s+", " ", str(value or "").strip().lower())


def _stem_tokens(path: Path) -> str:
    stem = path.stem.replace("_", " ").replace("-", " ").lower()
    return f"{stem} {path.suffix.lower()}"


def classify_wizard_document(
    path: Path,
    *,
    markdown: str = "",
    table_headers: list[str] | None = None,
    sheet_names: list[str] | None = None,
) -> WizardDocKind:
    blob = _stem_tokens(path)
    headers = {_norm_header(h) for h in (table_headers or [])}
    sheets = {_norm_header(s) for s in (sheet_names or [])}
    md_l = markdown.lower()

    if any(tok in blob for tok in WIZARD_DOC_CLASSIFY_CASE_TOKENS):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_CASE)
    if any(tok in blob for tok in WIZARD_DOC_CLASSIFY_SCENARIO_TOKENS):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_SCENARIO)
    if any(tok in blob for tok in WIZARD_DOC_CLASSIFY_SRS_TOKENS):
        return cast(WizardDocKind, WIZARD_DOC_KIND_SRS)

    if WIZARD_DOC_HEADER_CASE_ID in headers and (
        WIZARD_DOC_HEADER_STEP_NO in headers or WIZARD_DOC_HEADER_ACTION in headers
    ):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_CASE)
    if WIZARD_DOC_HEADER_SCENARIO_ID in headers and WIZARD_DOC_HEADER_CASE_ID not in headers:
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_SCENARIO)
    if any(WIZARD_DOC_SHEET_CASES in name for name in sheets):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_CASE)
    if any(WIZARD_DOC_SHEET_SCENARIOS in name for name in sheets):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_SCENARIO)

    if _MD_CASE_RE.search(markdown) and not _MD_SCENARIO_RE.search(markdown):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_CASE)
    if _MD_SCENARIO_RE.search(markdown):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_SCENARIO)
    if "test case id" in md_l or "| step no |" in md_l:
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_CASE)
    if "scenario id" in md_l:
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_SCENARIO)
    return cast(WizardDocKind, WIZARD_DOC_KIND_SRS)


def _coerce_action(raw: object) -> StepAction:
    text = str(raw or "click").strip().lower().replace(" ", "_")
    if text in _STEP_ACTIONS:
        return text  # type: ignore[return-value]
    return "click"


def _new_scenario(
    code: str,
    title: str,
    *,
    description: str = "",
    requirement_ref: str = "",
) -> TestScenario:
    return TestScenario(
        id=str(uuid.uuid4()),
        code=code or f"TS_{uuid.uuid4().hex[:6].upper()}",
        title=title or code or "Imported scenario",
        requirement_ref=requirement_ref,
        description=description,
        cases=[],
    )


def _new_case(
    code: str,
    title: str,
    *,
    scenario_code: str,
    steps: list[TestStep] | None = None,
    preconditions: list[str] | None = None,
    requirement_ref: str = "",
) -> TestCase:
    return TestCase(
        id=str(uuid.uuid4()),
        code=code or f"{scenario_code}_C01",
        title=title or code or "Imported case",
        preconditions=list(preconditions or []),
        steps=list(steps or []),
        requirement_ref=requirement_ref,
    )


def _csv_to_markdown(rows: list[dict[str, str]], headers: list[str]) -> str:
    if not headers:
        return ""
    lines = [
        "| " + " | ".join(headers) + " |",
        "| " + " | ".join("---" for _ in headers) + " |",
    ]
    for row in rows:
        lines.append("| " + " | ".join(row.get(h, "") for h in headers) + " |")
    return "\n".join(lines)


def _rows_from_csv(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    with path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle)
        if not reader.fieldnames:
            return [], []
        headers = [_norm_header(name) for name in reader.fieldnames if name is not None]
        rows: list[dict[str, str]] = []
        for raw in reader:
            rows.append({_norm_header(k): str(v or "").strip() for k, v in raw.items() if k})
        return headers, rows


def _import_case_rows(rows: list[dict[str, str]]) -> list[TestScenario]:
    if not rows:
        return []
    by_scenario: dict[str, TestScenario] = {}
    cases_by_key: dict[tuple[str, str], TestCase] = {}

    for row in rows:
        scenario_code = row.get(WIZARD_DOC_HEADER_SCENARIO_ID) or "TS_IMPORTED"
        case_code = row.get(WIZARD_DOC_HEADER_CASE_ID) or f"{scenario_code}_C01"
        title = row.get(WIZARD_DOC_HEADER_TITLE) or case_code
        if scenario_code not in by_scenario:
            by_scenario[scenario_code] = _new_scenario(scenario_code, scenario_code)
        key = (scenario_code, case_code)
        case = cases_by_key.get(key)
        if case is None:
            preconditions = [
                part.strip()
                for part in str(row.get("pre-conditions") or row.get("preconditions") or "").split(
                    ";"
                )
                if part.strip()
            ]
            case = _new_case(
                case_code,
                title,
                scenario_code=scenario_code,
                preconditions=preconditions,
                requirement_ref=row.get("requirement") or "",
            )
            cases_by_key[key] = case
            by_scenario[scenario_code].cases.append(case)
        step_raw = row.get(WIZARD_DOC_HEADER_STEP_NO) or ""
        action_raw = row.get(WIZARD_DOC_HEADER_ACTION) or ""
        if not step_raw and not action_raw:
            continue
        try:
            step_no = int(float(step_raw)) if step_raw else len(case.steps) + 1
        except ValueError:
            step_no = len(case.steps) + 1
        case.steps.append(
            TestStep(
                step_no=step_no,
                action=_coerce_action(action_raw),
                intent_hint=str(row.get("intent") or row.get("description") or ""),
                input_data=row.get("test data") or row.get("input_data") or None,
                expected_result=row.get("expected result") or "",
                binding_status="UNBOUND",
            )
        )
    return list(by_scenario.values())


def _import_scenario_rows(rows: list[dict[str, str]]) -> list[TestScenario]:
    scenarios: list[TestScenario] = []
    for row in rows:
        code = row.get(WIZARD_DOC_HEADER_SCENARIO_ID) or row.get("scenario") or ""
        title = row.get(WIZARD_DOC_HEADER_TITLE) or code
        if not code and not title:
            continue
        scenarios.append(
            _new_scenario(
                code or f"TS_{len(scenarios) + 1:02d}",
                title,
                description=row.get("description") or "",
                requirement_ref=row.get("requirement") or row.get("requirement id") or "",
            )
        )
    return scenarios


def _import_markdown(markdown: str, kind: WizardDocKind) -> list[TestScenario]:
    if kind == WIZARD_DOC_KIND_TEST_CASE:
        scenario = _new_scenario("TS_IMPORTED", "Imported test cases", description="From markdown")
        for idx, match in enumerate(_MD_CASE_RE.finditer(markdown), start=1):
            title = match.group(1).strip()
            scenario.cases.append(
                _new_case(
                    f"TC_IMP_{idx:02d}",
                    title,
                    scenario_code=scenario.code,
                    steps=[
                        TestStep(
                            step_no=1,
                            action="goto",
                            intent_hint=title,
                            input_data="/",
                            binding_status="UNBOUND",
                        )
                    ],
                )
            )
        return [scenario] if scenario.cases else []

    if kind == WIZARD_DOC_KIND_TEST_SCENARIO:
        scenarios: list[TestScenario] = []
        for idx, match in enumerate(_MD_SCENARIO_RE.finditer(markdown), start=1):
            title = match.group(1).strip()
            scenarios.append(
                _new_scenario(f"TS_IMP_{idx:02d}", title, description="From markdown")
            )
        return scenarios
    return []


def _load_xlsx(path: Path) -> tuple[list[str], list[str], list[TestScenario], str]:
    try:
        from openpyxl import load_workbook
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError("openpyxl is required for Excel import") from exc

    wb = load_workbook(path, read_only=True, data_only=True)
    sheet_names = list(wb.sheetnames)
    headers: list[str] = []
    imported: list[TestScenario] = []
    md_chunks: list[str] = []

    for name in sheet_names:
        ws = wb[name]
        rows_iter = ws.iter_rows(values_only=True)
        try:
            header_row = next(rows_iter)
        except StopIteration:
            continue
        col_headers = [_norm_header(cell) for cell in header_row]
        headers.extend([h for h in col_headers if h])
        dict_rows: list[dict[str, str]] = []
        for values in rows_iter:
            if values is None or all(v is None or str(v).strip() == "" for v in values):
                continue
            row_map: dict[str, str] = {}
            for index, header in enumerate(col_headers):
                if not header:
                    continue
                cell = values[index] if index < len(values) else ""
                row_map[header] = "" if cell is None else str(cell).strip()
            dict_rows.append(row_map)
        sheet_l = _norm_header(name)
        md_chunks.append(f"## Sheet: {name}")
        usable_headers = [h for h in col_headers if h]
        if dict_rows and usable_headers:
            md_chunks.append("| " + " | ".join(usable_headers) + " |")
            md_chunks.append("| " + " | ".join("---" for _ in usable_headers) + " |")
            for row in dict_rows[:80]:
                md_chunks.append(
                    "| " + " | ".join(row.get(header, "") for header in usable_headers) + " |"
                )
        if WIZARD_DOC_SHEET_CASES in sheet_l or (
            WIZARD_DOC_HEADER_CASE_ID in col_headers and WIZARD_DOC_HEADER_ACTION in col_headers
        ):
            imported.extend(_import_case_rows(dict_rows))
        elif WIZARD_DOC_SHEET_SCENARIOS in sheet_l or (
            WIZARD_DOC_HEADER_SCENARIO_ID in col_headers
            and WIZARD_DOC_HEADER_CASE_ID not in col_headers
        ):
            imported.extend(_import_scenario_rows(dict_rows))

    wb.close()
    return sheet_names, headers, imported, "\n".join(md_chunks)


def load_wizard_document(
    path: Path | str,
    *,
    compressor: ContextCompressor | None = None,
) -> WizardDocument:
    target = Path(path).expanduser().resolve()
    engine = compressor or ContextCompressor()
    suffix = target.suffix.lower()

    if not target.is_file():
        return WizardDocument(
            path=target,
            kind=cast(WizardDocKind, WIZARD_DOC_KIND_SRS),
            markdown="",
            error=f"file not found: {target}",
        )

    try:
        if suffix in WIZARD_DOC_CSV_SUFFIXES:
            headers, rows = _rows_from_csv(target)
            markdown = _csv_to_markdown(rows, headers)
            if not markdown.strip():
                markdown = engine.document_to_markdown(target)
            kind = classify_wizard_document(target, markdown=markdown, table_headers=headers)
            scenarios: list[TestScenario] = []
            if kind == WIZARD_DOC_KIND_TEST_CASE:
                scenarios = _import_case_rows(rows)
            elif kind == WIZARD_DOC_KIND_TEST_SCENARIO:
                scenarios = _import_scenario_rows(rows)
            return WizardDocument(path=target, kind=kind, markdown=markdown, scenarios=scenarios)

        if suffix in WIZARD_DOC_XLSX_SUFFIXES:
            sheets, headers, imported, table_md = _load_xlsx(target)
            try:
                markdown = engine.document_to_markdown(target)
            except Exception:  # noqa: BLE001
                markdown = table_md
            if not markdown.strip():
                markdown = table_md
            kind = classify_wizard_document(
                target, markdown=markdown, table_headers=headers, sheet_names=sheets
            )
            scenarios = list(imported)
            if not scenarios and kind in {
                WIZARD_DOC_KIND_TEST_CASE,
                WIZARD_DOC_KIND_TEST_SCENARIO,
            }:
                scenarios = _import_markdown(markdown, kind)
            return WizardDocument(path=target, kind=kind, markdown=markdown, scenarios=scenarios)

        if suffix in WIZARD_DOC_MARKDOWN_SUFFIXES:
            markdown = target.read_text(encoding="utf-8")
        else:
            markdown = engine.document_to_markdown(target)
        kind = classify_wizard_document(target, markdown=markdown)
        scenarios = _import_markdown(markdown, kind)
        return WizardDocument(path=target, kind=kind, markdown=markdown, scenarios=scenarios)
    except Exception as exc:  # noqa: BLE001
        logger.warning("wizard_doc_load_failed path=%s err=%s", target, exc)
        return WizardDocument(
            path=target,
            kind=classify_wizard_document(target),
            markdown="",
            error=str(exc),
        )


def build_wizard_llm_context(
    *,
    freeform_markdown: str,
    documents: list[WizardDocument],
    cap: int = WIZARD_DOC_CONTEXT_CAP_CHARS,
) -> str:
    parts: list[str] = []
    free = freeform_markdown.strip()
    if free:
        parts.append("### Freeform / feature notes\n" + free)
    for doc in documents:
        if doc.error or not doc.markdown.strip():
            continue
        parts.append(f"### Attached ({doc.kind_label}): {doc.path.name}\n{doc.markdown.strip()}")
    joined = "\n\n".join(parts).strip()
    if len(joined) <= cap:
        return joined
    return joined[: cap - 1] + "…"


def merge_wizard_scenarios(
    imported: list[TestScenario],
    generated: list[TestScenario],
) -> list[TestScenario]:
    seen: set[str] = set()
    merged: list[TestScenario] = []
    for scenario in [*imported, *generated]:
        code = (scenario.code or "").strip().upper()
        if code and code in seen:
            continue
        if code:
            seen.add(code)
        merged.append(scenario)
    return merged


def collect_imported_scenarios(documents: list[WizardDocument]) -> list[TestScenario]:
    out: list[TestScenario] = []
    for doc in documents:
        if doc.kind in {WIZARD_DOC_KIND_TEST_CASE, WIZARD_DOC_KIND_TEST_SCENARIO}:
            out.extend(doc.scenarios)
    return out
