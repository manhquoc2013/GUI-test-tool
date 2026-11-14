from __future__ import annotations

import asyncio
from pathlib import Path
from unittest.mock import patch

import pytest

from ai_qa_test_studio.core import doc_coverage as cov
from ai_qa_test_studio.core.doc_corpus import ingest_path


def test_section_map_from_headings(tmp_path: Path) -> None:
    body = "\n\n".join(
        [
            "# Login\n" + ("a " * 200),
            "# Checkout\n" + ("b " * 200),
            "# Reports\n" + ("c " * 200),
            "# Admin\n" + ("d " * 200),
        ]
    )
    doc = tmp_path / "srs.md"
    doc.write_text(body, encoding="utf-8")
    root = tmp_path / "proj"
    root.mkdir()
    result = ingest_path(root, doc)
    sections = cov.build_section_map(root, [result.doc_id])
    assert len(sections) >= 2
    titles = " ".join(s.title for s in sections)
    assert "Login" in titles or "Checkout" in titles or len(sections) >= 2
    for section in sections:
        text = cov.section_body(root, section)
        assert section.section_id in text
        assert text.strip()


def test_windows_cover_every_chunk_no_silent_truncate(tmp_path: Path) -> None:
    """Must: every ingested chunk appears in at least one window body."""
    parts = [f"# OnlyChapter\nBlock {i} " + ("x" * 900) for i in range(12)]
    doc = tmp_path / "fat.md"
    doc.write_text("\n\n".join(parts), encoding="utf-8")
    root = tmp_path / "proj"
    root.mkdir()
    result = ingest_path(root, doc)
    chunks = cov.load_chunks(root, [result.doc_id])
    assert len(chunks) >= 4
    expected = cov.expected_chunk_keys(chunks)

    sections = cov.build_section_map(root, [result.doc_id], max_sections=2)
    assert sections
    covered = cov.covered_chunk_keys(root, sections, max_chars=2500, chunks=chunks)
    assert covered == expected

    section = sections[0]
    windows = list(cov.iter_section_windows(root, section, max_chars=2500, chunks=chunks))
    assert len(windows) >= 2
    joined = "\n".join(w.body for w in windows)
    for chunk in chunks:
        if chunk.chunk_index not in section.chunk_indexes:
            continue
        if chunk.doc_id != section.doc_id:
            continue
        piece = chunk.content.strip()
        if piece:
            assert piece in joined


def test_multi_doc_completeness_uses_doc_id_pairs(tmp_path: Path) -> None:
    """Dropping one attached doc must fail pair equality (not index-only)."""
    root = tmp_path / "proj"
    root.mkdir()
    ids: list[str] = []
    for name, letter in (("a.md", "A"), ("b.md", "B")):
        path = tmp_path / name
        path.write_text(
            "\n\n".join(f"# {letter}{i}\n" + ("word " * 180) for i in range(5)),
            encoding="utf-8",
        )
        ids.append(ingest_path(root, path).doc_id)

    chunks = cov.load_chunks(root, ids)
    expected = cov.expected_chunk_keys(chunks)
    assert len(expected) >= 8
    # Index-only set collapses when docs share chunk_index ranges.
    assert len({i for _, i in expected}) < len(expected)

    sections = cov.build_section_map(root, ids)
    covered = cov.covered_chunk_keys(root, sections, chunks=chunks)
    assert covered == expected

    kept = [s for s in sections if s.doc_id == ids[0]]
    partial = cov.covered_chunk_keys(root, kept, chunks=chunks)
    assert partial != expected
    assert len(partial) < len(expected)


def test_multi_doc_merge_low_max_sections_keeps_all_chunks(tmp_path: Path) -> None:
    """Must: aggressive section budget must not merge across doc_id."""
    root = tmp_path / "proj"
    root.mkdir()
    ids: list[str] = []
    for name, letter in (("a.md", "A"), ("b.md", "B")):
        path = tmp_path / name
        path.write_text(
            "\n\n".join(f"# {letter}{i}\n" + ("word " * 100) for i in range(20)),
            encoding="utf-8",
        )
        ids.append(ingest_path(root, path).doc_id)

    chunks = cov.load_chunks(root, ids)
    expected = cov.expected_chunk_keys(chunks)
    sections = cov.build_section_map_from_chunks(
        chunks, max_sections=3, chunks_per_section=1
    )
    assert sections
    # No section may list indexes that only exist on another doc.
    by = {(c.doc_id, c.chunk_index): c for c in chunks}
    for section in sections:
        for index in section.chunk_indexes:
            assert (section.doc_id, index) in by
    covered = cov.covered_chunk_keys(root, sections, chunks=chunks)
    assert covered == expected
    # Mutation trap: old cross-doc merge lost pairs under low max_sections.
    assert len(covered) == len(expected)


def test_ingest_fills_missing_scenario_id() -> None:
    from ai_qa_test_studio.agent.coverage_pass import _ingest_window_scenarios

    merged: list = []
    added = _ingest_window_scenarios(
        payload={
            "scenarios": [
                {"code": "T1", "title": "Login", "cases": []},
            ]
        },
        section_title="Auth",
        code_prefix="S01",
        merged=merged,
    )
    assert added == 1
    assert merged[0].id
    assert merged[0].code.startswith("S01_")
    assert merged[0].requirement_ref == "Auth"


def test_plan_coverage_windows_single_load(tmp_path: Path) -> None:
    doc = tmp_path / "s.md"
    doc.write_text(
        "\n\n".join(f"# H{i}\n" + ("x" * 900) for i in range(16)),
        encoding="utf-8",
    )
    root = tmp_path / "proj"
    root.mkdir()
    result = ingest_path(root, doc)
    calls = {"n": 0}
    real = cov.load_chunks

    def counting(
        project_root: Path | str | None, doc_ids: list[str] | tuple[str, ...]
    ) -> list[cov.ChunkRow]:
        calls["n"] += 1
        return real(project_root, doc_ids)

    with patch.object(cov, "load_chunks", side_effect=counting):
        plan = cov.plan_coverage_windows(root, [result.doc_id], max_chars=2500)
    assert plan is not None
    assert plan.total_windows >= 1
    assert calls["n"] == 1
    assert cov.covered_chunk_keys(
        root, plan.sections, max_chars=2500, chunks=cov.load_chunks(root, [result.doc_id])
    ) == cov.expected_chunk_keys(cov.load_chunks(root, [result.doc_id]))


def test_plan_coverage_windows_below_min_returns_none(tmp_path: Path) -> None:
    doc = tmp_path / "tiny.md"
    doc.write_text("# Only\nshort", encoding="utf-8")
    root = tmp_path / "proj"
    root.mkdir()
    result = ingest_path(root, doc)
    assert result.total_chunks < 4
    assert cov.plan_coverage_windows(root, [result.doc_id]) is None


def test_max_sections_zero_does_not_divide_by_zero(tmp_path: Path) -> None:
    doc = tmp_path / "s.md"
    doc.write_text(
        "\n\n".join(f"# H{i}\n" + ("word " * 100) for i in range(6)),
        encoding="utf-8",
    )
    root = tmp_path / "proj"
    root.mkdir()
    result = ingest_path(root, doc)
    chunks = cov.load_chunks(root, [result.doc_id])
    sections = cov.build_section_map_from_chunks(chunks, max_sections=0)
    assert sections  # coerced to ≥1, no ZeroDivisionError



def test_section_body_concat_equals_all_windows(tmp_path: Path) -> None:
    doc = tmp_path / "s.md"
    doc.write_text(
        "\n\n".join(f"# H{i}\n" + ("word " * 180) for i in range(6)),
        encoding="utf-8",
    )
    root = tmp_path / "proj"
    root.mkdir()
    result = ingest_path(root, doc)
    sections = cov.build_section_map(root, [result.doc_id])
    for section in sections:
        windows = list(cov.iter_section_windows(root, section, max_chars=2000))
        full = cov.section_body(root, section, max_chars=2000)
        assert full == "\n\n".join(w.body for w in windows)


@pytest.mark.asyncio
async def test_orchestrator_coverage_pass_merges_sections(tmp_path: Path) -> None:
    from ai_qa_test_studio.agent.llm_gateway import UnifiedLLMGateway
    from ai_qa_test_studio.agent.orchestrator import CopilotOrchestrator
    from ai_qa_test_studio.constants import (
        CONTEXT_KEY_DOC_IDS,
        CONTEXT_KEY_PROJECT_ROOT,
        COPILOT_MODE_COMPOSE,
        LLM_PROVIDER_OFFLINE,
    )

    parts = [f"# Module {i}\nRequirement {i} " + ("word " * 120) for i in range(5)]
    doc = tmp_path / "big.md"
    doc.write_text("\n\n".join(parts), encoding="utf-8")
    root = tmp_path / "proj"
    root.mkdir()
    ingested = ingest_path(root, doc)
    assert ingested.total_chunks >= 4

    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    payloads = []
    statuses = []
    async for event in orch.run(
        user_text="Tạo bộ kịch bản kiểm thử từ SRS đính kèm",
        mode=COPILOT_MODE_COMPOSE,
        context={
            CONTEXT_KEY_DOC_IDS: [ingested.doc_id],
            CONTEXT_KEY_PROJECT_ROOT: str(root),
            "attachments": [
                {
                    "name": "big.md",
                    "path": str(doc),
                    "markdown": "preview",
                    "doc_id": ingested.doc_id,
                }
            ],
        },
    ):
        if event.kind == "apply_ready" and event.apply_payload:
            payloads.append(event.apply_payload)
        if event.kind == "status_summary" and event.text:
            statuses.append(event.text)

    assert any("Coverage Pass:" in s and "cửa sổ" in s for s in statuses)
    assert any("/" in s and "chunks" in s for s in statuses)
    assert payloads
    scenarios = payloads[-1].get("scenarios") or []
    assert len(scenarios) >= 2


@pytest.mark.asyncio
async def test_empty_coverage_marks_compose_no_topk_fallback(tmp_path: Path) -> None:
    from ai_qa_test_studio.agent.llm_gateway import UnifiedLLMGateway
    from ai_qa_test_studio.agent.orchestrator import CopilotOrchestrator, _RoleResult
    from ai_qa_test_studio.constants import (
        CONTEXT_KEY_DOC_IDS,
        CONTEXT_KEY_PROJECT_ROOT,
        COPILOT_MODE_COMPOSE,
        LLM_PROVIDER_OFFLINE,
    )

    parts = [f"# Module {i}\nRequirement {i} " + ("word " * 120) for i in range(5)]
    doc = tmp_path / "big.md"
    doc.write_text("\n\n".join(parts), encoding="utf-8")
    root = tmp_path / "proj"
    root.mkdir()
    ingested = ingest_path(root, doc)

    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )

    async def empty_compose_gen(*a: object, **k: object):
        out = k.get("out") if "out" in k else (a[8] if len(a) > 8 else None)
        if isinstance(out, list):
            out.append(
                _RoleResult(
                    text="no scenarios",
                    apply_payload={"kind": "compose", "scenarios": []},
                )
            )
        if False:  # pragma: no cover — async generator shape
            yield None

    statuses: list[str] = []
    compose_calls = {"n": 0}
    section_compose = {"n": 0}

    async def counting_compose(*a: object, **k: object):
        compose_calls["n"] += 1
        ctx = str(k.get("ctx_block") or (a[2] if len(a) > 2 else ""))
        if "SECTION:" in ctx:
            section_compose["n"] += 1
        async for ev in empty_compose_gen(*a, **k):
            yield ev

    with patch.object(orch, "_compose", counting_compose):
        async for event in orch.run(
            user_text="Tạo bộ kịch bản kiểm thử từ SRS đính kèm",
            mode=COPILOT_MODE_COMPOSE,
            context={
                CONTEXT_KEY_DOC_IDS: [ingested.doc_id],
                CONTEXT_KEY_PROJECT_ROOT: str(root),
            },
        ):
            if event.kind == "status_summary" and event.text:
                statuses.append(event.text)

    assert any("0 scenario" in s and "cửa sổ" in s for s in statuses)
    assert section_compose["n"] >= 1
    # No Top-K compose after coverage marked the board (SECTION-only calls).
    assert compose_calls["n"] == section_compose["n"]


@pytest.mark.asyncio
async def test_coverage_pass_single_load_chunks(tmp_path: Path) -> None:
    from ai_qa_test_studio.agent.llm_gateway import UnifiedLLMGateway
    from ai_qa_test_studio.agent.orchestrator import CopilotOrchestrator
    from ai_qa_test_studio.constants import (
        CONTEXT_KEY_DOC_IDS,
        CONTEXT_KEY_PROJECT_ROOT,
        COPILOT_MODE_COMPOSE,
        LLM_PROVIDER_OFFLINE,
    )

    doc = tmp_path / "big.md"
    doc.write_text(
        "\n\n".join(f"# Module {i}\n" + ("word " * 120) for i in range(5)),
        encoding="utf-8",
    )
    root = tmp_path / "proj"
    root.mkdir()
    ingested = ingest_path(root, doc)
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    loads = {"n": 0}
    real = cov.load_chunks

    def counting(
        project_root: Path | str | None, doc_ids: list[str] | tuple[str, ...]
    ) -> list[cov.ChunkRow]:
        loads["n"] += 1
        return real(project_root, doc_ids)

    with patch.object(cov, "load_chunks", side_effect=counting):
        async for _ in orch.run(
            user_text="Tạo bộ kịch bản kiểm thử từ SRS đính kèm",
            mode=COPILOT_MODE_COMPOSE,
            context={
                CONTEXT_KEY_DOC_IDS: [ingested.doc_id],
                CONTEXT_KEY_PROJECT_ROOT: str(root),
            },
        ):
            pass
    assert loads["n"] == 1


@pytest.mark.asyncio
async def test_coverage_stop_flushes_partial_apply(tmp_path: Path) -> None:
    from ai_qa_test_studio.agent.llm_gateway import UnifiedLLMGateway
    from ai_qa_test_studio.agent.orchestrator import CopilotOrchestrator, _RoleResult
    from ai_qa_test_studio.constants import (
        CONTEXT_KEY_DOC_IDS,
        CONTEXT_KEY_PROJECT_ROOT,
        COPILOT_MODE_COMPOSE,
        LLM_PROVIDER_OFFLINE,
    )

    doc = tmp_path / "big.md"
    doc.write_text(
        "\n\n".join(f"# Module {i}\n" + ("word " * 120) for i in range(6)),
        encoding="utf-8",
    )
    root = tmp_path / "proj"
    root.mkdir()
    ingested = ingest_path(root, doc)
    orch = CopilotOrchestrator(
        UnifiedLLMGateway(provider=LLM_PROVIDER_OFFLINE, api_key="", model="offline")
    )
    cancel = asyncio.Event()
    composed = {"n": 0}

    async def compose_then_stop(*a: object, **k: object):
        out = a[8] if len(a) > 8 else k.get("out")
        composed["n"] += 1
        if isinstance(out, list):
            out.append(
                _RoleResult(
                    text="partial",
                    apply_payload={
                        "kind": "compose",
                        "scenarios": [
                            {
                                "id": f"id-{composed['n']}",
                                "code": f"P{composed['n']}",
                                "title": f"Partial {composed['n']}",
                                "cases": [],
                            }
                        ],
                    },
                )
            )
        cancel.set()
        if False:
            yield None

    payloads: list[dict] = []
    statuses: list[str] = []
    stopped = False
    with patch.object(orch, "_compose", compose_then_stop):
        async for event in orch.run(
            user_text="Tạo bộ kịch bản kiểm thử từ SRS đính kèm",
            mode=COPILOT_MODE_COMPOSE,
            context={
                CONTEXT_KEY_DOC_IDS: [ingested.doc_id],
                CONTEXT_KEY_PROJECT_ROOT: str(root),
            },
            cancel=cancel,
        ):
            if event.kind == "apply_ready" and event.apply_payload:
                payloads.append(event.apply_payload)
            if event.kind == "status_summary" and event.text:
                statuses.append(event.text)
            if event.kind == "stopped":
                stopped = True

    assert composed["n"] >= 1
    assert stopped
    assert payloads
    assert any("dừng sớm" in s or "partial" in s.lower() for s in statuses)
    assert len(payloads[-1].get("scenarios") or []) >= 1
