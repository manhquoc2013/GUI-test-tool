"""Progressive skill loader (ECC + Hermes sidecar .usage.json)."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

import yaml
from pydantic import BaseModel

from ai_qa_test_studio.constants import MICRO_TOOL_LOAD_SKILL, SKILL_CONTEXT_MAX_CHARS

_FRONTMATTER_RE = re.compile(r"\A---\s*\n(.*?)\n---\s*\n?", re.DOTALL)


class SkillMetadata(BaseModel):
    name: str
    description: str
    skill_path: Path
    usage_path: Path


class ProgressiveSkillLoader:
    def __init__(self, skills_dir: Path) -> None:
        self.skills_dir = skills_dir
        self.registry: dict[str, SkillMetadata] = {}
        self._scan_skills()

    def _scan_skills(self) -> None:
        self.registry.clear()
        if not self.skills_dir.exists():
            return
        for folder in sorted(self.skills_dir.iterdir()):
            skill_file = folder / "SKILL.md"
            if folder.is_dir() and skill_file.is_file():
                raw = skill_file.read_text(encoding="utf-8")
                desc = self._description_from_skill(raw, folder.name)
                self.registry[folder.name] = SkillMetadata(
                    name=folder.name,
                    description=desc,
                    skill_path=skill_file,
                    usage_path=folder / ".usage.json",
                )

    @staticmethod
    def _description_from_skill(raw: str, fallback: str) -> str:
        match = _FRONTMATTER_RE.match(raw)
        if match:
            try:
                meta = yaml.safe_load(match.group(1)) or {}
            except yaml.YAMLError:
                meta = {}
            if isinstance(meta, dict):
                desc = str(meta.get("description") or "").strip()
                if desc:
                    return desc
        for line in raw.splitlines():
            stripped = line.strip()
            if stripped.startswith("#"):
                return stripped.lstrip("# ").strip() or fallback
        return fallback

    def get_compact_catalog(self) -> str:
        lines = [
            "AVAILABLE TESTING SKILLS (Call tool `load_skill` for full instructions):"
        ]
        for name, meta in self.registry.items():
            lines.append(f"- {name}: {meta.description}")
        return "\n".join(lines)

    def load_skill_context(self, skill_name: str, domain: str = "") -> str:
        meta = self.registry.get(skill_name)
        if not meta:
            return f"Skill '{skill_name}' không tồn tại."
        content = meta.skill_path.read_text(encoding="utf-8")
        if domain and meta.usage_path.exists():
            try:
                usage_data = json.loads(meta.usage_path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                usage_data = {}
            if isinstance(usage_data, dict):
                domain_notes = usage_data.get("domains", {}).get(domain, [])
                if isinstance(domain_notes, list) and domain_notes:
                    content += "\n\n### Kinh nghiệm thực chiến trên domain này:\n" + "\n".join(
                        f"- {n}" for n in domain_notes
                    )
        if len(content) > SKILL_CONTEXT_MAX_CHARS:
            return content[: SKILL_CONTEXT_MAX_CHARS - 1] + "…"
        return content

    def invoke_tool(self, tool_name: str, args: dict[str, Any] | None = None) -> str:
        payload = args or {}
        if tool_name != MICRO_TOOL_LOAD_SKILL:
            return f"Unsupported skill tool: {tool_name}"
        skill_name = str(payload.get("skill_name") or payload.get("name") or "").strip()
        domain = str(payload.get("domain") or "").strip()
        return self.load_skill_context(skill_name, domain=domain)

    def record_usage_note(self, skill_name: str, domain: str, note: str) -> None:
        meta = self.registry.get(skill_name)
        if not meta:
            return
        data: dict[str, object] = {"domains": {}}
        if meta.usage_path.exists():
            try:
                loaded = json.loads(meta.usage_path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                loaded = {}
            if isinstance(loaded, dict):
                data = loaded
        domains_obj = data.setdefault("domains", {})
        if not isinstance(domains_obj, dict):
            domains_obj = {}
            data["domains"] = domains_obj
        notes_obj = domains_obj.setdefault(domain, [])
        if not isinstance(notes_obj, list):
            notes_obj = []
            domains_obj[domain] = notes_obj
        if note not in notes_obj:
            notes_obj.append(note)
        meta.usage_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
