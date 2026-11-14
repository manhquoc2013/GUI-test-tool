"""EvidenceCollector — write masked run artifacts under .studio/runs/<run_id>/."""

from __future__ import annotations

import base64
import json
import logging
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from ai_qa_test_studio.constants import (
    ASSERT_STEP_ACTIONS,
    AUDIT_CODE_MISSING_EVIDENCE,
    EVIDENCE_AUDIT_FILENAME,
    EVIDENCE_MANIFEST_FILENAME,
    STUDIO_DIRNAME,
    STUDIO_RUNS_DIRNAME,
)
from ai_qa_test_studio.core.enums import StepStatus
from ai_qa_test_studio.core.models import RunReport, RunStepResult
from ai_qa_test_studio.core.secret_masker import get_secret_masker

logger = logging.getLogger(__name__)

_CRITICAL_STATUSES = frozenset(
    {
        "FAIL",
        "FAILED",
        "HEALED",
        StepStatus.FAILED.value,
        StepStatus.HEALED.value,
    }
)


@dataclass(frozen=True)
class EvidenceFlag:
    code: str
    message: str
    case_code: str = ""
    step_no: int | None = None
    severity: str = "warning"


@dataclass
class EvidenceIntegrityReport:
    flags: list[EvidenceFlag] = field(default_factory=list)
    checked_steps: int = 0

    @property
    def missing_count(self) -> int:
        return sum(1 for f in self.flags if f.code == AUDIT_CODE_MISSING_EVIDENCE)


class EvidenceCollector:
    def __init__(self, project_root: Path) -> None:
        self.project_root = Path(project_root)
        self.runs_root = self.project_root / STUDIO_DIRNAME / STUDIO_RUNS_DIRNAME

    def run_dir(self, run_id: str) -> Path:
        path = self.runs_root / run_id
        path.mkdir(parents=True, exist_ok=True)
        return path

    def persist_step_png(
        self,
        run_id: str,
        case_id: str,
        step: RunStepResult,
        png: bytes,
        *,
        console_errors: list[str] | None = None,
        dom_snippet: str = "",
    ) -> Path:
        """Write screenshot (+ optional console/DOM sidecars); keep screenshot_path only."""
        masker = get_secret_masker()
        out = self.run_dir(run_id)
        dest = out / f"{case_id}_step_{step.step_no}.png"
        dest.write_bytes(png)
        step.screenshot_path = str(dest)
        step.screenshot_b64 = None
        if console_errors:
            side = out / f"{case_id}_step_{step.step_no}_console.json"
            side.write_text(
                json.dumps(
                    masker.mask_mapping({"errors": list(console_errors)}),
                    indent=2,
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
        if dom_snippet:
            side_dom = out / f"{case_id}_step_{step.step_no}_dom.txt"
            side_dom.write_text(masker.mask_text(dom_snippet), encoding="utf-8")
        return dest

    def latest_run_dir(self) -> Path | None:
        if not self.runs_root.is_dir():
            return None
        candidates = [p for p in self.runs_root.iterdir() if p.is_dir()]
        if not candidates:
            return None
        return max(candidates, key=lambda p: p.stat().st_mtime)

    def load_latest_report(self) -> RunReport | None:
        """Read newest masked manifest from .studio/runs/<run_id>/ (sync; call via to_thread)."""
        run_dir = self.latest_run_dir()
        if run_dir is None:
            return None
        manifest = run_dir / EVIDENCE_MANIFEST_FILENAME
        if not manifest.is_file():
            return None
        try:
            raw = json.loads(manifest.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            logger.warning("evidence_load_failed path=%s err=%s", manifest, exc)
            return None
        if not isinstance(raw, dict):
            return None
        masker = get_secret_masker()
        try:
            return RunReport.model_validate(masker.mask_mapping(raw))
        except Exception as exc:  # noqa: BLE001
            logger.warning("evidence_validate_failed err=%s", exc)
            return None

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
                if step.console_logs:
                    step.console_logs = [masker.mask_text(line) for line in step.console_logs]

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
        integrity = self.verify_integrity(report)
        if integrity.flags:
            (out / "integrity.json").write_text(
                json.dumps(
                    masker.mask_mapping(
                        {
                            "flags": [
                                {
                                    "code": f.code,
                                    "message": f.message,
                                    "case_code": f.case_code,
                                    "step_no": f.step_no,
                                    "severity": f.severity,
                                }
                                for f in integrity.flags
                            ]
                        }
                    ),
                    indent=2,
                ),
                encoding="utf-8",
            )
        logger.info("evidence_collected run_id=%s dir=%s", report.run_id, out)
        return out

    def verify_integrity(self, report: RunReport) -> EvidenceIntegrityReport:
        """Flag Failed/Healed/Assert steps missing non-empty screenshot or audit trail."""
        flags: list[EvidenceFlag] = []
        checked = 0
        for case in report.cases:
            for step in case.steps:
                if not self._needs_evidence(step):
                    continue
                checked += 1
                missing: list[str] = []
                if not self._screenshot_ok(step.screenshot_path):
                    missing.append("screenshot")
                if step.step_status == StepStatus.HEALED or step.status == "HEALED":
                    if step.audit is None and not report.healing_audit:
                        missing.append("StepAuditEntry")
                if missing:
                    flags.append(
                        EvidenceFlag(
                            code=AUDIT_CODE_MISSING_EVIDENCE,
                            message=(
                                f"{case.case_code} step {step.step_no}: "
                                f"missing {', '.join(missing)}"
                            ),
                            case_code=case.case_code,
                            step_no=step.step_no,
                        )
                    )
        return EvidenceIntegrityReport(flags=flags, checked_steps=checked)

    @staticmethod
    def _needs_evidence(step: RunStepResult) -> bool:
        status = str(step.step_status or step.status or "")
        if status in _CRITICAL_STATUSES:
            return True
        return str(step.action) in ASSERT_STEP_ACTIONS

    @staticmethod
    def _screenshot_ok(path: str | None) -> bool:
        if not path:
            return False
        file_path = Path(path)
        try:
            return file_path.is_file() and file_path.stat().st_size > 0
        except OSError:
            return False

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
