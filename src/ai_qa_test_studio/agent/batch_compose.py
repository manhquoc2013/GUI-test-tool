"""Map-Reduce batch compose — outline → chunked workers → disk checkpoints."""

from __future__ import annotations

import asyncio
import json
import logging
import re
import uuid
from collections.abc import Awaitable, Callable, Sequence
from dataclasses import dataclass, field
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any

from ai_qa_test_studio.constants import (
    APPLY_KIND_COMPOSE,
    APPLY_PAYLOAD_DRAFT_SESSION_KEY,
    COPILOT_BATCH_CASE_THRESHOLD,
    COPILOT_BATCH_PROGRESS,
    COPILOT_BATCH_SCENARIO_SIZE,
    COPILOT_BATCH_SCENARIO_THRESHOLD,
    COPILOT_BATCH_SEMAPHORE,
    COPILOT_LARGE_SUITE_SAMPLES,
    COPILOT_LARGE_SUITE_UI_CASE_THRESHOLD,
    LLM_STREAM_PROGRESS_GUARD_RATIO,
    STUDIO_DIRNAME,
    STUDIO_DRAFTS_DIRNAME,
)
from ai_qa_test_studio.core.compose_mapping import normalize_compose_scenarios
from ai_qa_test_studio.core.secret_masker import get_secret_masker
from ai_qa_test_studio.core.text_normalize import normalize_text_list

logger = logging.getLogger(__name__)

ProgressCb = Callable[[str], None]
ExpandBatchFn = Callable[[list[dict[str, Any]], int], Awaitable[list[dict[str, Any]]]]

_LARGE_REQUEST_RE = re.compile(
    r"(?i)(?:\b(?:300|400|500|toàn\s*bộ|full\s*suite|nhiều|lớn|large)\b|"
    r"(?:hơn|tren|>)\s*\d{2,}\s*(?:case|tc|test))"
)


def continuation_is_stalled(
    previous: str,
    addition: str,
    *,
    ratio: float = LLM_STREAM_PROGRESS_GUARD_RATIO,
) -> bool:
    """True when the new segment is empty or nearly duplicates the prior segment.

    Compares full segments (not a short sliding window) so adjacent unique
    chunks that share a short prefix are not false-positive stalled.
    """
    add = (addition or "").strip()
    if not add:
        return True
    prev = (previous or "").strip()
    if not prev:
        return False
    if add == prev:
        return True
    # Lengths must be comparable; a short echo of a long prior is still a stall.
    shorter, longer = (add, prev) if len(add) <= len(prev) else (prev, add)
    if len(longer) > 0 and (len(shorter) / len(longer)) < 0.5:
        # Very short relative addition — treat as stall only on exact tail echo.
        return longer.endswith(shorter) and len(shorter) >= 16
    return SequenceMatcher(None, prev, add).ratio() >= ratio


def drafts_root(project_root: Path, session_id: str) -> Path:
    return Path(project_root) / STUDIO_DIRNAME / STUDIO_DRAFTS_DIRNAME / session_id


def batch_checkpoint_path(project_root: Path, session_id: str, batch_index: int) -> Path:
    return drafts_root(project_root, session_id) / f"batch_{batch_index:04d}.json"


def should_batch_compose(
    *,
    scenario_count: int = 0,
    estimated_cases: int = 0,
    user_text: str = "",
) -> bool:
    if scenario_count > COPILOT_BATCH_SCENARIO_THRESHOLD:
        return True
    if estimated_cases > COPILOT_BATCH_CASE_THRESHOLD:
        return True
    return bool(_LARGE_REQUEST_RE.search(user_text or ""))


def chunk_scenarios(
    scenarios: Sequence[dict[str, Any]],
    *,
    batch_size: int = COPILOT_BATCH_SCENARIO_SIZE,
) -> list[list[dict[str, Any]]]:
    size = max(1, int(batch_size))
    items = [s for s in scenarios if isinstance(s, dict)]
    return [list(items[i : i + size]) for i in range(0, len(items), size)]


def count_cases_in_scenarios(scenarios: Sequence[Any]) -> int:
    total = 0
    for item in scenarios:
        if not isinstance(item, dict):
            continue
        cases = item.get("cases") or []
        if isinstance(cases, list):
            total += len(cases)
    return total


def count_steps_in_scenarios(scenarios: Sequence[Any]) -> int:
    total = 0
    for item in scenarios:
        if not isinstance(item, dict):
            continue
        cases = item.get("cases") or []
        if not isinstance(cases, list):
            continue
        for case in cases:
            if not isinstance(case, dict):
                continue
            steps = case.get("steps") or []
            if isinstance(steps, list):
                total += len(steps)
    return total


def priority_distribution(scenarios: Sequence[Any]) -> dict[str, int]:
    buckets = {"P1": 0, "P2": 0, "P3": 0, "other": 0}
    for item in scenarios:
        if not isinstance(item, dict):
            continue
        cases = item.get("cases") or []
        if not isinstance(cases, list):
            continue
        for case in cases:
            if not isinstance(case, dict):
                continue
            raw = str(case.get("priority") or "")
            if raw.startswith("P1"):
                buckets["P1"] += 1
            elif raw.startswith("P2"):
                buckets["P2"] += 1
            elif raw.startswith("P3"):
                buckets["P3"] += 1
            else:
                buckets["other"] += 1
    return buckets


def sample_cases(scenarios: Sequence[Any], *, limit: int = COPILOT_LARGE_SUITE_SAMPLES) -> list[dict[str, str]]:
    samples: list[dict[str, str]] = []
    for item in scenarios:
        if not isinstance(item, dict):
            continue
        sc_code = str(item.get("code") or "").strip()
        cases = item.get("cases") or []
        if not isinstance(cases, list):
            continue
        for case in cases:
            if not isinstance(case, dict):
                continue
            samples.append(
                {
                    "scenario": sc_code,
                    "code": str(case.get("code") or "").strip(),
                    "title": str(case.get("title") or "").strip(),
                    "priority": str(case.get("priority") or "").strip(),
                }
            )
            if len(samples) >= limit:
                return samples
    return samples


def format_batch_progress(*, done: int, total: int, batch: int, batches: int) -> str:
    return COPILOT_BATCH_PROGRESS.format(
        done=done, total=total, batch=batch, batches=batches
    )


def format_large_suite_summary_card(payload: dict[str, Any]) -> str:
    """Compact card for chat when case count exceeds the large-suite UI threshold."""
    scenarios = payload.get("scenarios") or []
    if not isinstance(scenarios, list):
        scenarios = []
    case_total = count_cases_in_scenarios(scenarios)
    step_total = count_steps_in_scenarios(scenarios)
    dist = priority_distribution(scenarios)
    samples = sample_cases(scenarios)
    lines = [
        "## Bộ Test Case lớn (tóm tắt)",
        "",
        f"- **Scenarios:** {len(scenarios)}",
        f"- **Test Cases:** {case_total}",
        f"- **Steps:** {step_total}",
        f"- **Ưu tiên:** P1={dist['P1']} · P2={dist['P2']} · P3={dist['P3']}",
        "",
        "### 5 Test Case mẫu",
    ]
    if samples:
        lines.extend(
            [
                "| # | Scenario | Mã TC | Tiêu đề | Priority |",
                "| ---: | --- | --- | --- | --- |",
            ]
        )
        for idx, row in enumerate(samples, start=1):
            lines.append(
                f"| {idx} | `{row['scenario'] or '—'}` | `{row['code'] or '—'}` | "
                f"{row['title'] or '—'} | {row['priority'] or '—'} |"
            )
    else:
        lines.append("_Chưa có case mẫu._")
    lines.extend(
        [
            "",
            "**Tải Excel** · **Apply vào Editor / Test Data** — dữ liệu đầy đủ đọc từ checkpoint trên đĩa.",
        ]
    )
    draft = str(payload.get(APPLY_PAYLOAD_DRAFT_SESSION_KEY) or "").strip()
    if draft:
        lines.append(f"_Checkpoint session:_ `{draft}`")
    return "\n".join(lines).strip()


def _normalize_scenario_dicts(scenarios: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Normalize via compose_mapping then dump JSON-safe dicts (preconditions intact)."""
    models = normalize_compose_scenarios(scenarios)
    out: list[dict[str, Any]] = []
    for scenario in models:
        dumped = scenario.model_dump(mode="json")
        for case in dumped.get("cases") or []:
            if not isinstance(case, dict):
                continue
            pre = case.get("preconditions")
            if isinstance(pre, list):
                # Keep list form; exporters call normalize_text_list (no char-split).
                case["preconditions"] = [
                    normalize_text_list(item) if not isinstance(item, str) else item
                    for item in pre
                ]
            elif isinstance(pre, str):
                case["preconditions"] = [normalize_text_list(pre)]
        out.append(dumped)
    return out


def write_batch_checkpoint_sync(
    project_root: Path,
    session_id: str,
    batch_index: int,
    scenarios: list[dict[str, Any]],
) -> Path:
    masker = get_secret_masker()
    normalized = _normalize_scenario_dicts(scenarios)
    payload = masker.mask_mapping(
        {
            "kind": APPLY_KIND_COMPOSE,
            "batch_index": batch_index,
            APPLY_PAYLOAD_DRAFT_SESSION_KEY: session_id,
            "scenarios": normalized,
        }
    )
    path = batch_checkpoint_path(project_root, session_id, batch_index)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return path


async def write_batch_checkpoint(
    project_root: Path,
    session_id: str,
    batch_index: int,
    scenarios: list[dict[str, Any]],
) -> Path:
    return await asyncio.to_thread(
        write_batch_checkpoint_sync,
        project_root,
        session_id,
        batch_index,
        scenarios,
    )


def list_batch_checkpoint_paths(project_root: Path, session_id: str) -> list[Path]:
    root = drafts_root(project_root, session_id)
    if not root.is_dir():
        return []
    return sorted(root.glob("batch_*.json"))


def load_session_compose_payload_sync(project_root: Path, session_id: str) -> dict[str, Any]:
    scenarios: list[dict[str, Any]] = []
    for path in list_batch_checkpoint_paths(project_root, session_id):
        try:
            raw = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            logger.warning("batch_checkpoint_load_failed path=%s err=%s", path, exc)
            continue
        if not isinstance(raw, dict):
            continue
        rows = raw.get("scenarios") or []
        if isinstance(rows, list):
            scenarios.extend(item for item in rows if isinstance(item, dict))
    return {
        "kind": APPLY_KIND_COMPOSE,
        APPLY_PAYLOAD_DRAFT_SESSION_KEY: session_id,
        "scenarios": scenarios,
    }


async def load_session_compose_payload(
    project_root: Path, session_id: str
) -> dict[str, Any]:
    return await asyncio.to_thread(load_session_compose_payload_sync, project_root, session_id)


def pending_batch_indices(
    project_root: Path,
    session_id: str,
    total_batches: int,
) -> list[int]:
    done = {
        int(path.stem.split("_")[-1])
        for path in list_batch_checkpoint_paths(project_root, session_id)
        if path.stem.split("_")[-1].isdigit()
    }
    return [idx for idx in range(1, total_batches + 1) if idx not in done]


@dataclass
class BatchComposeResult:
    payload: dict[str, Any]
    session_id: str
    batches_total: int
    batches_done: int
    case_total: int
    stopped: bool = False
    progress_messages: list[str] = field(default_factory=list)


@dataclass
class BatchComposeRunner:
    """Outline chunks → expand under semaphore → checkpoint each batch to disk."""

    project_root: Path
    session_id: str
    expand_batch: ExpandBatchFn
    batch_size: int = COPILOT_BATCH_SCENARIO_SIZE
    concurrency: int = COPILOT_BATCH_SEMAPHORE

    async def run(
        self,
        outline_scenarios: list[dict[str, Any]],
        *,
        stop: asyncio.Event | None = None,
        on_progress: ProgressCb | None = None,
        resume: bool = True,
    ) -> BatchComposeResult:
        stop_event = stop or asyncio.Event()
        chunks = chunk_scenarios(outline_scenarios, batch_size=self.batch_size)
        batches_total = len(chunks)
        if batches_total == 0:
            empty = {
                "kind": APPLY_KIND_COMPOSE,
                APPLY_PAYLOAD_DRAFT_SESSION_KEY: self.session_id,
                "scenarios": [],
            }
            return BatchComposeResult(
                payload=empty,
                session_id=self.session_id,
                batches_total=0,
                batches_done=0,
                case_total=0,
            )

        estimated_cases = max(
            count_cases_in_scenarios(outline_scenarios),
            batches_total * self.batch_size * 3,
        )
        pending = (
            pending_batch_indices(self.project_root, self.session_id, batches_total)
            if resume
            else list(range(1, batches_total + 1))
        )
        progress_messages: list[str] = []
        sem = asyncio.Semaphore(max(1, int(self.concurrency)))
        lock = asyncio.Lock()
        done_cases = 0
        # Seed done_cases from existing checkpoints
        existing = load_session_compose_payload_sync(self.project_root, self.session_id)
        done_cases = count_cases_in_scenarios(existing.get("scenarios") or [])

        async def _one(batch_index: int, chunk: list[dict[str, Any]]) -> None:
            nonlocal done_cases
            if stop_event.is_set():
                return
            async with sem:
                if stop_event.is_set():
                    return
                expanded = await self.expand_batch(chunk, batch_index)
                if stop_event.is_set():
                    return
                await write_batch_checkpoint(
                    self.project_root, self.session_id, batch_index, expanded
                )
                added = count_cases_in_scenarios(expanded)
                async with lock:
                    done_cases += added
                    msg = format_batch_progress(
                        done=done_cases,
                        total=max(estimated_cases, done_cases),
                        batch=batch_index,
                        batches=batches_total,
                    )
                    progress_messages.append(msg)
                    if on_progress is not None:
                        on_progress(msg)

        tasks = [
            asyncio.create_task(_one(idx, chunks[idx - 1]))
            for idx in pending
            if 1 <= idx <= batches_total
        ]
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)

        payload = load_session_compose_payload_sync(self.project_root, self.session_id)
        case_total = count_cases_in_scenarios(payload.get("scenarios") or [])
        batches_done = len(list_batch_checkpoint_paths(self.project_root, self.session_id))
        return BatchComposeResult(
            payload=payload,
            session_id=self.session_id,
            batches_total=batches_total,
            batches_done=batches_done,
            case_total=case_total,
            stopped=stop_event.is_set() and batches_done < batches_total,
            progress_messages=progress_messages,
        )


def new_draft_session_id() -> str:
    return uuid.uuid4().hex[:12]


def outline_only_scenarios(scenarios: Sequence[Any]) -> list[dict[str, Any]]:
    """Strip nested steps for Phase-1 outline payloads."""
    out: list[dict[str, Any]] = []
    for item in scenarios:
        if not isinstance(item, dict):
            continue
        cases_raw = item.get("cases") or []
        outline_cases: list[dict[str, Any]] = []
        if isinstance(cases_raw, list):
            for case in cases_raw:
                if not isinstance(case, dict):
                    continue
                outline_cases.append(
                    {
                        "id": case.get("id"),
                        "code": case.get("code"),
                        "title": case.get("title"),
                        "priority": case.get("priority") or "P1 - High",
                        "preconditions": case.get("preconditions") or [],
                        "steps": [],
                    }
                )
        out.append(
            {
                "id": item.get("id"),
                "code": item.get("code"),
                "title": item.get("title"),
                "requirement_ref": item.get("requirement_ref") or "",
                "description": item.get("description") or "",
                "cases": outline_cases,
            }
        )
    return out


def is_large_suite_payload(payload: dict[str, Any] | None) -> bool:
    if not isinstance(payload, dict):
        return False
    scenarios = payload.get("scenarios") or []
    if not isinstance(scenarios, list):
        return False
    return count_cases_in_scenarios(scenarios) > COPILOT_LARGE_SUITE_UI_CASE_THRESHOLD
