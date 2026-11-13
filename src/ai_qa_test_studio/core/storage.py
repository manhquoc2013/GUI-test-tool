"""JSON project persistence for v4 models."""

from __future__ import annotations

import json
import logging
import uuid
from pathlib import Path

from ai_qa_test_studio.constants import DEFAULT_V4_PROJECT_FILENAME
from ai_qa_test_studio.core.models import Project, TestGroup

logger = logging.getLogger(__name__)


class ProjectStorage:
    def __init__(self, root: Path) -> None:
        self.root = root
        self.root.mkdir(parents=True, exist_ok=True)
        self.project_path = self.root / DEFAULT_V4_PROJECT_FILENAME

    def new_empty(self, name: str = "Untitled Project") -> Project:
        return Project(
            id=str(uuid.uuid4()),
            name=name,
            root_group=TestGroup(id=str(uuid.uuid4()), name="Root", description="Root project group"),
        )

    def save(self, project: Project) -> Path:
        tmp = self.project_path.with_suffix(".tmp")
        tmp.write_text(
            project.model_dump_json(indent=2),
            encoding="utf-8",
        )
        tmp.replace(self.project_path)
        return self.project_path

    def load(self) -> Project | None:
        if not self.project_path.is_file():
            return None
        try:
            payload = json.loads(self.project_path.read_text(encoding="utf-8"))
            return Project.model_validate(payload)
        except (OSError, json.JSONDecodeError, ValueError) as exc:
            logger.error("project_load_failed path=%s err=%s", self.project_path, exc)
            raise
