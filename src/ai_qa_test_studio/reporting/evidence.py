"""EvidenceCollector — write masked run artifacts under .studio/runs/<run_id>/."""

from __future__ import annotations

import base64
import json
import logging
from pathlib import Path
from typing import Any

from ai_qa_test_studio.constants import (
    EVIDENCE_AUDIT_FILENAME,
    EVIDENCE_MANIFEST_FILENAME,
    STUDIO_DIRNAME,
    STUDIO_RUNS_DIRNAME,
)
from ai_qa_test_studio.core.models import RunReport, RunStepResult
from ai_qa_test_studio.core.secret_masker import get_secret_masker

logger = logging.getLogger(__name__)


class EvidenceCollector:
    def __init__(self, project_root: Path) -> None:
        self.project_root = Path(project_root)
        self.runs_root = self.project_root / STUDIO_DIRNAME / STUDIO_RUNS_DIRNAME

    def run_dir(self, run_id: str) -> Path:
        path = self.runs_root / run_id
        path.mkdir(parents=True, exist_ok=True)
        return path

    def collect(self, report: RunReport) -> Path:
        """Persist masked report + step screenshots; return run directory."""
        masker = get_secret_masker()
        out = self.run_dir(report.run_id)
        step_paths: list[dict[str, Any]] = []
        for case in report.cases:
            for step in case.steps:
                shot = self._persist_step_shot(out, case.case_id, step)
                if shot is not None:
                    step.screenshot_path = str(shot)
                    step_paths.append(
                        {
                            "case_id": case.case_id,
                            "step_no": step.step_no,
                            "path": str(shot),
                        }
                    )
                if step.message:
                    step.message = masker.mask_text(step.message)

        payload = report.model_dump()
        masked_payload = masker.mask_mapping(payload)
        (out / EVIDENCE_MANIFEST_FILENAME).write_text(
            json.dumps(masked_payload, indent=2, ensure_ascii=False),
            encoding="utf-8",
        )
        audits = [a.model_dump() for a in report.healing_audit]
        (out / EVIDENCE_AUDIT_FILENAME).write_text(
            json.dumps(masker.mask_mapping({"audits": audits}), indent=2),
            encoding="utf-8",
        )
        index = {
            "run_id": report.run_id,
            "project_name": report.project_name,
            "screenshots": step_paths,
        }
        (out / "index.json").write_text(
            json.dumps(masker.mask_mapping(index), indent=2),
            encoding="utf-8",
        )
        logger.info("evidence_collected run_id=%s dir=%s", report.run_id, out)
        return out

    def _persist_step_shot(
        self, run_dir: Path, case_id: str, step: RunStepResult
    ) -> Path | None:
        if step.screenshot_path:
            existing = Path(step.screenshot_path)
            if existing.is_file():
                return existing
        if not step.screenshot_b64:
            return None
        try:
            raw = base64.b64decode(step.screenshot_b64)
        except Exception as exc:  # noqa: BLE001
            logger.warning("evidence_b64_decode_failed step=%s err=%s", step.step_no, exc)
            return None
        dest = run_dir / f"{case_id}_step_{step.step_no}.png"
        dest.write_bytes(raw)
        # Drop inline b64 after path persist to keep exporters lean
        step.screenshot_b64 = None
        return dest
