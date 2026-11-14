"""Project persistence façade — YAML tree primary (Hybrid Local-First)."""

from __future__ import annotations

import logging
import uuid
from pathlib import Path

from ai_qa_test_studio.core.models import Project, TestGroup
from ai_qa_test_studio.core.project_yaml import ProjectYamlStorage

logger = logging.getLogger(__name__)


class ProjectStorage:
    """Backward-compatible API; persists via ``ProjectYamlStorage``."""

    def __init__(self, root: Path) -> None:
        self.root = root
        self.root.mkdir(parents=True, exist_ok=True)
        self._yaml = ProjectYamlStorage(root)
        self.project_path = self._yaml.project_yaml

    def new_empty(self, name: str = "Untitled Project") -> Project:
        return Project(
            id=str(uuid.uuid4()),
            name=name,
            root_group=TestGroup(id=str(uuid.uuid4()), name="Root", description="Root project group"),
        )

    def save(self, project: Project) -> Path:
        return self._yaml.save(project)

    def load(self) -> Project | None:
        try:
            return self._yaml.load()
        except (OSError, ValueError, TypeError) as exc:
            logger.error("project_load_failed path=%s err=%s", self.root, exc)
            raise
