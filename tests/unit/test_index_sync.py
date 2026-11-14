"""Incremental YAML → SQLite index + FTS5 accent-insensitive search."""

from __future__ import annotations

from pathlib import Path

import pytest

from ai_qa_test_studio.constants import STUDIO_DIRNAME, STUDIO_RUNTIME_DB
from ai_qa_test_studio.core.index_sync import ProjectIndexSync
from ai_qa_test_studio.core.models import (
    Project,
    SharedElement,
    TestCase,
    TestGroup,
    TestScenario,
    TestStep,
)
from ai_qa_test_studio.core.project_yaml import ProjectYamlStorage


def _project() -> Project:
    case = TestCase(
        id="case-1",
        code="TC01",
        title="Tạo hồ sơ luồng chuẩn",
        steps=[TestStep(step_no=1, action="click", intent_hint="Mở", binding_status="UNBOUND")],
    )
    return Project(
        id="p1",
        name="Demo",
        root_group=TestGroup(
            id="root",
            name="Root",
            scenarios=[TestScenario(id="sc1", code="TS1", title="Sc", cases=[case])],
        ),
        shared_elements=[
            SharedElement(element_id="EL_SAVE", page_name="Form", friendly_name="Nút lưu")
        ],
    )


@pytest.mark.asyncio
async def test_index_sync_skips_unchanged_and_fts_finds_unaccented(tmp_path: Path) -> None:
    yaml_store = ProjectYamlStorage(tmp_path)
    project = _project()
    yaml_store.save(project)

    sync = ProjectIndexSync(tmp_path)
    first = await sync.sync()
    assert first.updated >= 1
    assert first.skipped == 0

    second = await sync.sync()
    assert second.updated == 0
    assert second.skipped >= 1

    hits = await sync.search("tao ho so")
    assert any(h.entity_id == "case-1" for h in hits)

    el_hits = await sync.search("nut luu")
    assert any(h.entity_id == "EL_SAVE" for h in el_hits)

    db = tmp_path / STUDIO_DIRNAME / STUDIO_RUNTIME_DB
    assert db.is_file()
