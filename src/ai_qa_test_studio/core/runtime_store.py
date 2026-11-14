"""Execution runs + artifact path helpers for per-project runtime.db."""

from __future__ import annotations

from pathlib import Path

from ai_qa_test_studio.constants import STUDIO_ARTIFACTS_DIRNAME, STUDIO_DIRNAME, STUDIO_RUNTIME_DB
from ai_qa_test_studio.core.sqlite_db import init_high_speed_db


class RuntimeStore:
    def __init__(self, project_root: Path) -> None:
        self.project_root = project_root
        self.studio_dir = project_root / STUDIO_DIRNAME
        self.artifacts_dir = self.studio_dir / STUDIO_ARTIFACTS_DIRNAME
        self.db_path = self.studio_dir / STUDIO_RUNTIME_DB

    def ensure_dirs(self) -> None:
        self.artifacts_dir.mkdir(parents=True, exist_ok=True)

    def artifact_path(self, *parts: str) -> Path:
        self.ensure_dirs()
        path = self.artifacts_dir.joinpath(*parts)
        path.parent.mkdir(parents=True, exist_ok=True)
        return path

    async def record_run(
        self,
        *,
        run_id: str,
        case_id: str,
        status: str,
        started_at: str,
        duration_ms: int | None = None,
        tokens_used: int = 0,
        healed_count: int = 0,
        report_path: str | None = None,
    ) -> None:
        conn = await init_high_speed_db(self.db_path)
        try:
            await conn.execute(
                """
                INSERT INTO execution_runs(
                    run_id, case_id, status, duration_ms, tokens_used,
                    healed_count, started_at, report_path
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(run_id) DO UPDATE SET
                    status=excluded.status,
                    duration_ms=excluded.duration_ms,
                    tokens_used=excluded.tokens_used,
                    healed_count=excluded.healed_count,
                    report_path=excluded.report_path
                """,
                (
                    run_id,
                    case_id,
                    status,
                    duration_ms,
                    tokens_used,
                    healed_count,
                    started_at,
                    report_path,
                ),
            )
            await conn.commit()
        finally:
            await conn.close()
