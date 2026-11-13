"""Progressive skill loader (ECC + Hermes sidecar .usage.json)."""

from __future__ import annotations

import json
from pathlib import Path

from pydantic import BaseModel


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
                first_lines = skill_file.read_text(encoding="utf-8").splitlines()[:5]
                desc = next(
                    (line.lstrip("# ").strip() for line in first_lines if line.strip()),
                    folder.name,
                )
                self.registry[folder.name] = SkillMetadata(
                    name=folder.name,
                    description=desc,
                    skill_path=skill_file,
                    usage_path=folder / ".usage.json",
                )

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
            usage_data = json.loads(meta.usage_path.read_text(encoding="utf-8"))
            domain_notes = usage_data.get("domains", {}).get(domain, [])
            if domain_notes:
                content += "\n\n### Kinh nghiệm thực chiến trên domain này:\n" + "\n".join(
                    f"- {n}" for n in domain_notes
                )
        return content

    def record_usage_note(self, skill_name: str, domain: str, note: str) -> None:
        meta = self.registry.get(skill_name)
        if not meta:
            return
        data: dict[str, object] = {"domains": {}}
        if meta.usage_path.exists():
            loaded = json.loads(meta.usage_path.read_text(encoding="utf-8"))
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
