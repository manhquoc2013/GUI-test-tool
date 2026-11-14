"""AI Wizard multi-doc ingest: tables, auto-classify, import, LLM fallback."""

from __future__ import annotations

import csv
import logging
import re
import uuid
from collections.abc import Callable
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
    WIZARD_DOC_HEADER_COMPACT_ALIASES,
    WIZARD_DOC_HEADER_DESCRIPTION,
    WIZARD_DOC_HEADER_EXPECTED,
    WIZARD_DOC_HEADER_PRECONDITIONS,
    WIZARD_DOC_HEADER_REQUIREMENT,
    WIZARD_DOC_HEADER_SCENARIO_ID,
    WIZARD_DOC_HEADER_STEP_NO,
    WIZARD_DOC_HEADER_TEST_DATA,
    WIZARD_DOC_HEADER_TITLE,
    WIZARD_DOC_KIND_LABELS,
    WIZARD_DOC_KIND_SRS,
    WIZARD_DOC_KIND_TEST_CASE,
    WIZARD_DOC_KIND_TEST_SCENARIO,
    WIZARD_DOC_MARKDOWN_SUFFIXES,
    WIZARD_DOC_MAX_FILE_BYTES,
    WIZARD_DOC_MAX_SHEETS,
    WIZARD_DOC_PREVIEW_TABLE_ROWS,
    WIZARD_DOC_SHEET_CASES,
    WIZARD_DOC_SHEET_SCENARIOS,
    WIZARD_DOC_XLSX_SUFFIXES,
)
from ai_qa_test_studio.core.models import StepAction, TestCase, TestScenario, TestStep
from ai_qa_test_studio.core.token_budget import ContextCompressor

logger = logging.getLogger(__name__)

WizardDocKind = Literal["srs", "test_scenario", "test_case"]
LlmTableParser = Callable[[str], list[TestScenario]]

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
    needs_llm: bool = False

    @property
    def kind_label(self) -> str:
        return WIZARD_DOC_KIND_LABELS.get(self.kind, self.kind)

    @property
    def display_name(self) -> str:
        suffix = " · LLM" if self.needs_llm else ""
        return f"{self.path.name} · {self.kind_label}{suffix}"


def _norm_header(value: object) -> str:
    spaced = re.sub(r"[\s_\-]+", " ", str(value or "").strip().lower())
    compact = re.sub(r"[^a-z0-9]+", "", spaced)
    if compact in WIZARD_DOC_HEADER_COMPACT_ALIASES:
        return WIZARD_DOC_HEADER_COMPACT_ALIASES[compact]
    return spaced


def _stem_tokens(path: Path) -> str:
    stem = path.stem.replace("_", " ").replace("-", " ").lower()
    return f"{stem} {path.suffix.lower()}"


def _headers_look_like_cases(headers: set[str]) -> bool:
    has_case = WIZARD_DOC_HEADER_CASE_ID in headers or any("case" in h for h in headers)
    has_stepish = (
        WIZARD_DOC_HEADER_STEP_NO in headers
        or WIZARD_DOC_HEADER_ACTION in headers
        or WIZARD_DOC_HEADER_EXPECTED in headers
    )
    return has_case and has_stepish


def _headers_look_like_scenarios(headers: set[str]) -> bool:
    return WIZARD_DOC_HEADER_SCENARIO_ID in headers and WIZARD_DOC_HEADER_CASE_ID not in headers


def classify_wizard_document(
    path: Path,
    *,
    markdown: str = "",
    table_headers: list[str] | None = None,
    sheet_names: list[str] | None = None,
) -> WizardDocKind:
    blob = _stem_tokens(path)
    headers = {_norm_header(h) for h in (table_headers or []) if str(h or "").strip()}
    sheets = {_norm_header(s) for s in (sheet_names or [])}
    md_l = markdown.lower()

    # Structural signals first (never let filename drop case rows).
    if _headers_look_like_cases(headers):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_CASE)
    if _headers_look_like_scenarios(headers):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_SCENARIO)
    if any(WIZARD_DOC_SHEET_CASES in name for name in sheets):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_CASE)
    if any(WIZARD_DOC_SHEET_SCENARIOS in name for name in sheets):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_SCENARIO)

    if _MD_CASE_RE.search(markdown) and _MD_SCENARIO_RE.search(markdown):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_CASE)
    if _MD_CASE_RE.search(markdown):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_CASE)
    if _MD_SCENARIO_RE.search(markdown):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_SCENARIO)
    if "test case id" in md_l or "| step no |" in md_l:
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_CASE)
    if "scenario id" in md_l:
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_SCENARIO)

    if any(tok in blob for tok in WIZARD_DOC_CLASSIFY_CASE_TOKENS):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_CASE)
    if any(tok in blob for tok in WIZARD_DOC_CLASSIFY_SCENARIO_TOKENS):
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_SCENARIO)
    if any(tok in blob for tok in WIZARD_DOC_CLASSIFY_SRS_TOKENS):
        return cast(WizardDocKind, WIZARD_DOC_KIND_SRS)

    if len(headers) >= 2:
        return cast(WizardDocKind, WIZARD_DOC_KIND_TEST_CASE)
    return cast(WizardDocKind, WIZARD_DOC_KIND_SRS)


def _is_standard_case_table(headers: list[str]) -> bool:
    header_set = set(headers)
    return WIZARD_DOC_HEADER_CASE_ID in header_set and (
        WIZARD_DOC_HEADER_STEP_NO in header_set or WIZARD_DOC_HEADER_ACTION in header_set
    )


def _is_standard_scenario_table(headers: list[str]) -> bool:
    header_set = set(headers)
    return WIZARD_DOC_HEADER_SCENARIO_ID in header_set and WIZARD_DOC_HEADER_CASE_ID not in header_set


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


def _rows_from_delimited(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    delimiter = "\t" if path.suffix.lower() == ".tsv" else ","
    with path.open(newline="", encoding="utf-8-sig") as handle:
        reader = csv.DictReader(handle, delimiter=delimiter)
        if not reader.fieldnames:
            return [], []
        headers = [_norm_header(name) for name in reader.fieldnames if name is not None]
        rows: list[dict[str, str]] = []
        for raw in reader:
            rows.append({_norm_header(k): str(v or "").strip() for k, v in raw.items() if k})
        return headers, rows


def _dedupe_scenarios(scenarios: list[TestScenario]) -> list[TestScenario]:
    by_code: dict[str, TestScenario] = {}
    order: list[str] = []
    for scenario in scenarios:
        key = (scenario.code or "").strip().upper() or scenario.id
        existing = by_code.get(key)
        if existing is None:
            by_code[key] = scenario
            order.append(key)
            continue
        if not existing.cases and scenario.cases:
            by_code[key] = scenario
        elif scenario.cases:
            existing.cases.extend(scenario.cases)
        if not existing.description and scenario.description:
            existing.description = scenario.description
    return [by_code[key] for key in order]


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
                for part in str(row.get(WIZARD_DOC_HEADER_PRECONDITIONS) or "").split(";")
                if part.strip()
            ]
            case = _new_case(
                case_code,
                title,
                scenario_code=scenario_code,
                preconditions=preconditions,
                requirement_ref=row.get(WIZARD_DOC_HEADER_REQUIREMENT) or "",
            )
            cases_by_key[key] = case
            by_scenario[scenario_code].cases.append(case)
        step_raw = row.get(WIZARD_DOC_HEADER_STEP_NO) or ""
        action_raw = row.get(WIZARD_DOC_HEADER_ACTION) or ""
        intent = (
            row.get("intent")
            or row.get(WIZARD_DOC_HEADER_DESCRIPTION)
            or row.get(WIZARD_DOC_HEADER_TITLE)
            or ""
        )
        if not step_raw and not action_raw and not intent:
            continue
        try:
            step_no = int(float(step_raw)) if step_raw else len(case.steps) + 1
        except ValueError:
            step_no = len(case.steps) + 1
        case.steps.append(
            TestStep(
                step_no=step_no,
                action=_coerce_action(action_raw or "click"),
                intent_hint=str(intent),
                input_data=row.get(WIZARD_DOC_HEADER_TEST_DATA) or None,
                expected_result=row.get(WIZARD_DOC_HEADER_EXPECTED) or "",
                binding_status="UNBOUND",
            )
        )
    return _dedupe_scenarios(list(by_scenario.values()))


def _import_scenario_rows(rows: list[dict[str, str]]) -> list[TestScenario]:
    by_code: dict[str, TestScenario] = {}
    order: list[str] = []
    for row in rows:
        code = row.get(WIZARD_DOC_HEADER_SCENARIO_ID) or row.get("scenario") or ""
        title = row.get(WIZARD_DOC_HEADER_TITLE) or code
        if not code and not title:
            continue
        key = (code or title).strip().upper()
        if key in by_code:
            existing = by_code[key]
            if not existing.description:
                existing.description = row.get(WIZARD_DOC_HEADER_DESCRIPTION) or ""
            continue
        scenario = _new_scenario(
            code or f"TS_{len(order) + 1:02d}",
            title,
            description=row.get(WIZARD_DOC_HEADER_DESCRIPTION) or "",
            requirement_ref=row.get(WIZARD_DOC_HEADER_REQUIREMENT) or "",
        )
        by_code[key] = scenario
        order.append(key)
    return [by_code[key] for key in order]


def _import_loose_tabular(rows: list[dict[str, str]], headers: list[str]) -> list[TestScenario]:
    """Best-effort mapping for arbitrary table shapes (no IEEE columns)."""
    if not rows or not headers:
        return []
    if _is_standard_case_table(headers):
        return _import_case_rows(rows)
    if _is_standard_scenario_table(headers):
        return _import_scenario_rows(rows)

    title_key = next(
        (h for h in headers if h in {WIZARD_DOC_HEADER_TITLE, WIZARD_DOC_HEADER_DESCRIPTION}),
        headers[0],
    )
    code_key = next(
        (
            h
            for h in headers
            if h in {WIZARD_DOC_HEADER_CASE_ID, WIZARD_DOC_HEADER_SCENARIO_ID} or "id" in h
        ),
        None,
    )
    scenario = _new_scenario("TS_TABLE", "Imported table", description="Loose tabular import")
    for index, row in enumerate(rows, start=1):
        title = row.get(title_key) or f"Row {index}"
        code = (row.get(code_key) if code_key else "") or f"TC_ROW_{index:02d}"
        detail_parts = [f"{h}: {row.get(h, '')}" for h in headers if row.get(h)]
        scenario.cases.append(
            _new_case(
                code,
                title,
                scenario_code=scenario.code,
                steps=[
                    TestStep(
                        step_no=1,
                        action="click",
                        intent_hint="; ".join(detail_parts)[:500],
                        binding_status="UNBOUND",
                    )
                ],
            )
        )
    return [scenario] if scenario.cases else []


def _import_markdown(markdown: str, kind: WizardDocKind) -> list[TestScenario]:
    scenarios: list[TestScenario] = []
    if kind in {WIZARD_DOC_KIND_TEST_SCENARIO, WIZARD_DOC_KIND_TEST_CASE}:
        for idx, match in enumerate(_MD_SCENARIO_RE.finditer(markdown), start=1):
            scenarios.append(
                _new_scenario(f"TS_IMP_{idx:02d}", match.group(1).strip(), description="From markdown")
            )
    case_matches = list(_MD_CASE_RE.finditer(markdown))
    if case_matches:
        host = scenarios[0] if scenarios else _new_scenario(
            "TS_IMPORTED", "Imported test cases", description="From markdown"
        )
        if not scenarios:
            scenarios = [host]
        for idx, match in enumerate(case_matches, start=1):
            title = match.group(1).strip()
            host.cases.append(
                _new_case(
                    f"TC_IMP_{idx:02d}",
                    title,
                    scenario_code=host.code,
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
    elif kind == WIZARD_DOC_KIND_TEST_CASE and not scenarios:
        return []
    return _dedupe_scenarios(scenarios)


def _guard_file_size(path: Path) -> str | None:
    try:
        size = path.stat().st_size
    except OSError as exc:
        return str(exc)
    if size > WIZARD_DOC_MAX_FILE_BYTES:
        return f"file exceeds {WIZARD_DOC_MAX_FILE_BYTES} bytes"
    return None


def _load_xlsx(path: Path) -> tuple[list[str], list[str], list[TestScenario], str, bool]:
    try:
        from openpyxl import load_workbook
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError("openpyxl is required for Excel import") from exc

    wb = load_workbook(path, read_only=True, data_only=True)
    sheet_names = list(wb.sheetnames)[:WIZARD_DOC_MAX_SHEETS]
    headers: list[str] = []
    imported: list[TestScenario] = []
    md_chunks: list[str] = []
    needs_llm = False

    for name in sheet_names:
        ws = wb[name]
        rows_iter = ws.iter_rows(values_only=True)
        try:
            header_row = next(rows_iter)
        except StopIteration:
            continue
        col_headers = [_norm_header(cell) for cell in header_row]
        usable_headers = [h for h in col_headers if h]
        headers.extend(usable_headers)
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
        preview_rows = dict_rows[:WIZARD_DOC_PREVIEW_TABLE_ROWS]
        if preview_rows and usable_headers:
            md_chunks.append(_csv_to_markdown(preview_rows, usable_headers))
            if len(dict_rows) > WIZARD_DOC_PREVIEW_TABLE_ROWS:
                md_chunks.append(
                    f"_… {len(dict_rows) - WIZARD_DOC_PREVIEW_TABLE_ROWS} more rows "
                    f"(full import keeps all {len(dict_rows)})_"
                )
        if WIZARD_DOC_SHEET_CASES in sheet_l or _is_standard_case_table(usable_headers):
            imported.extend(_import_case_rows(dict_rows))
        elif WIZARD_DOC_SHEET_SCENARIOS in sheet_l or _is_standard_scenario_table(usable_headers):
            imported.extend(_import_scenario_rows(dict_rows))
        elif usable_headers and dict_rows:
            imported.extend(_import_loose_tabular(dict_rows, usable_headers))
            needs_llm = True

    wb.close()
    return sheet_names, headers, _dedupe_scenarios(imported), "\n".join(md_chunks), needs_llm


def _import_from_table(
    headers: list[str],
    rows: list[dict[str, str]],
    kind: WizardDocKind,
) -> tuple[list[TestScenario], bool]:
    if kind == WIZARD_DOC_KIND_TEST_CASE and _is_standard_case_table(headers):
        return _import_case_rows(rows), False
    if kind == WIZARD_DOC_KIND_TEST_SCENARIO and _is_standard_scenario_table(headers):
        return _import_scenario_rows(rows), False
    if kind in {WIZARD_DOC_KIND_TEST_CASE, WIZARD_DOC_KIND_TEST_SCENARIO} and headers and rows:
        return _import_loose_tabular(rows, headers), True
    if len(headers) >= 2 and rows:
        return _import_loose_tabular(rows, headers), True
    return [], False


def load_wizard_document(
    path: Path | str,
    *,
    compressor: ContextCompressor | None = None,
    llm_parser: LlmTableParser | None = None,
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
    size_err = _guard_file_size(target)
    if size_err:
        return WizardDocument(
            path=target,
            kind=cast(WizardDocKind, WIZARD_DOC_KIND_SRS),
            markdown="",
            error=size_err,
        )

    try:
        if suffix in WIZARD_DOC_CSV_SUFFIXES:
            headers, rows = _rows_from_delimited(target)
            preview = rows[:WIZARD_DOC_PREVIEW_TABLE_ROWS]
            markdown = _csv_to_markdown(preview, headers)
            if len(rows) > WIZARD_DOC_PREVIEW_TABLE_ROWS:
                markdown += (
                    f"\n\n_… {len(rows) - WIZARD_DOC_PREVIEW_TABLE_ROWS} more rows "
                    f"(full import keeps all {len(rows)})_"
                )
            kind = classify_wizard_document(target, markdown=markdown, table_headers=headers)
            scenarios, needs_llm = _import_from_table(headers, rows, kind)
            if needs_llm and llm_parser is not None:
                llm_scenarios = llm_parser(markdown)
                if llm_scenarios:
                    scenarios = _dedupe_scenarios([*scenarios, *llm_scenarios])
                    needs_llm = False
            return WizardDocument(
                path=target,
                kind=kind,
                markdown=markdown,
                scenarios=scenarios,
                needs_llm=needs_llm,
            )

        if suffix in WIZARD_DOC_XLSX_SUFFIXES:
            sheets, headers, imported, table_md, needs_llm = _load_xlsx(target)
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
            if needs_llm and llm_parser is not None:
                llm_scenarios = llm_parser(markdown)
                if llm_scenarios:
                    scenarios = _dedupe_scenarios([*scenarios, *llm_scenarios])
                    needs_llm = False
            return WizardDocument(
                path=target,
                kind=kind,
                markdown=markdown,
                scenarios=scenarios,
                needs_llm=needs_llm,
            )

        if suffix in WIZARD_DOC_MARKDOWN_SUFFIXES:
            markdown = target.read_text(encoding="utf-8")[: WIZARD_DOC_MAX_FILE_BYTES]
        else:
            markdown = engine.document_to_markdown(target)
        kind = classify_wizard_document(target, markdown=markdown)
        scenarios = _import_markdown(markdown, kind)
        needs_llm = (
            kind in {WIZARD_DOC_KIND_TEST_CASE, WIZARD_DOC_KIND_TEST_SCENARIO} and not scenarios
        )
        if needs_llm and llm_parser is not None:
            llm_scenarios = llm_parser(markdown)
            if llm_scenarios:
                scenarios = llm_scenarios
                needs_llm = False
        return WizardDocument(
            path=target,
            kind=kind,
            markdown=markdown,
            scenarios=scenarios,
            needs_llm=needs_llm,
        )
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
        parts.append("### SRS / freeform notes\n" + free)
    for doc in documents:
        if doc.error or not doc.markdown.strip():
            continue
        tag = doc.kind_label
        if doc.needs_llm:
            tag = f"{tag}, needs structured parse"
        parts.append(f"### Attached ({tag}): {doc.path.name}\n{doc.markdown.strip()}")
    joined = "\n\n".join(parts).strip()
    if len(joined) <= cap:
        return joined
    return joined[: cap - 1] + "…"


def merge_wizard_scenarios(
    imported: list[TestScenario],
    generated: list[TestScenario],
) -> list[TestScenario]:
    return _dedupe_scenarios([*imported, *generated])


def collect_imported_scenarios(documents: list[WizardDocument]) -> list[TestScenario]:
    out: list[TestScenario] = []
    for doc in documents:
        if doc.kind in {WIZARD_DOC_KIND_TEST_CASE, WIZARD_DOC_KIND_TEST_SCENARIO}:
            out.extend(doc.scenarios)
    return _dedupe_scenarios(out)
