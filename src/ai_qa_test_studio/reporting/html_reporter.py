"""Self-contained HTML report with status filters, heal diff, and lightbox thumbs."""

from __future__ import annotations

import base64
import html
from collections import Counter
from pathlib import Path

from jinja2 import Template

from ai_qa_test_studio.constants import HEAL_PERSIST_THRESHOLD, HEAL_RUN_THRESHOLD
from ai_qa_test_studio.core.models import RunReport, RunStepResult, StepAuditEntry
from ai_qa_test_studio.core.secret_masker import SecretMasker, get_secret_masker

_TEMPLATE = Template(
    """<!DOCTYPE html>
<html lang="vi"><head><meta charset="utf-8"/>
<title>Run {{ report.run_id }}</title>
<style>
body{font-family:system-ui,sans-serif;margin:24px;background:#111;color:#eee}
.card{border:1px solid #333;padding:12px;margin:8px 0;border-radius:8px}
.pass,.passed{color:#3ddc97}.fail,.failed{color:#ff6b6b}.healed{color:#ffd166}
.blocked{color:#c77dff}.skipped,.cancelled{color:#aaa}
.shot{position:relative;display:inline-block;max-width:100%}
.shot img.thumb{max-width:220px;max-height:140px;cursor:zoom-in;border-radius:6px;border:1px solid #444}
.marker{position:absolute;width:12px;height:12px;border:2px solid #ff6b6b;border-radius:50%}
.summary{display:flex;gap:16px;flex-wrap:wrap;margin:12px 0}
.summary span,.filters button{
padding:6px 10px;border:1px solid #333;border-radius:6px;background:#1a1a1a;color:#eee;cursor:pointer}
.filters{display:flex;gap:8px;margin:12px 0;flex-wrap:wrap}
.filters button.active{border-color:#3ddc97;color:#3ddc97}
.diff{background:#1c1c1c;border-left:3px solid #ffd166;padding:8px 12px;margin-top:8px}
.badge{display:inline-block;padding:2px 8px;border-radius:999px;background:#333;margin-right:6px}
.lightbox{display:none;position:fixed;inset:0;background:rgba(0,0,0,.85);z-index:99;align-items:center;justify-content:center}
.lightbox.open{display:flex}
.lightbox img{max-width:92vw;max-height:92vh}
.hidden{display:none!important}
</style></head><body>
<h1>AI QA Test Studio Report</h1>
<p>Project: {{ report.project_name }} · Run: {{ report.run_id }}</p>
<p>{{ report.started_at }} → {{ report.finished_at }}</p>
<div class="summary">
{% for status, count in summary_counts.items() %}
<span class="{{ status|lower }}">{{ status }}: {{ count }}</span>
{% endfor %}
<span>Total: {{ report.cases|length }}</span>
</div>
<div class="filters" role="toolbar" aria-label="Lọc trạng thái">
<button type="button" class="active" data-filter="all">All</button>
<button type="button" data-filter="passed">Passed</button>
<button type="button" data-filter="healed">Healed</button>
<button type="button" data-filter="failed">Failed</button>
</div>
{% for case in report.cases %}
<section class="card case" data-status="{{ case.status|lower }}" id="case-{{ case.case_id }}">
<h2>{{ case.case_code }} — {{ case.title }}
<span class="{{ case.status|lower }}">{{ case.status }}</span></h2>
{% if case.skip_reason %}<p>{{ case.skip_reason }}</p>{% endif %}
{% for step in case.steps %}
<div class="card step" data-status="{{ step.filter_status }}">
<strong>#{{ step.step_no }} {{ step.action }}</strong>
<span class="{{ step.filter_status }}">{{ step.display_status }}</span>
<em>{{ step.duration_ms }} ms</em>
<p>{{ step.message }}</p>
{% if step.console_logs %}<pre>{{ step.console_logs }}</pre>{% endif %}
{% if step.heal_diff %}
<div class="diff">
<span class="badge">confidence {{ step.heal_diff.confidence }}</span>
<strong>{{ step.heal_diff.note }}</strong>
<p><b>Previous Selector:</b> {{ step.heal_diff.previous }}</p>
<p><b>Healed Selector:</b> {{ step.heal_diff.healed }}</p>
</div>
{% endif %}
{% if step.img_src %}
<div class="shot">
<img class="thumb" src="{{ step.img_src }}" alt="step {{ step.step_no }}" onclick="openLightbox(this.src)"/>
{% if step.click_x is not none and step.click_y is not none %}
<span class="marker" style="left:{{ step.click_x }}px;top:{{ step.click_y }}px"></span>
{% endif %}
</div>
{% endif %}
</div>
{% endfor %}
</section>
{% endfor %}
{% if report.healing_audit %}
<h2>Self-Healing Audit</h2>
{% for a in report.healing_audit %}
<div class="card">Step {{ a.step_no }}: {{ a.previous }} → {{ a.healed }} ({{ a.reason }})</div>
{% endfor %}
{% endif %}
<div id="lightbox" class="lightbox" onclick="this.classList.remove('open')">
<img id="lightbox-img" alt="zoom"/>
</div>
<script>
function openLightbox(src){
  const box=document.getElementById('lightbox');
  document.getElementById('lightbox-img').src=src;
  box.classList.add('open');
}
document.querySelectorAll('.filters button').forEach(btn=>{
  btn.addEventListener('click',()=>{
    document.querySelectorAll('.filters button').forEach(b=>b.classList.remove('active'));
    btn.classList.add('active');
    const f=btn.dataset.filter;
    document.querySelectorAll('.step').forEach(el=>{
      const st=el.dataset.status;
      const show=f==='all'||st===f||(f==='passed'&&(st==='pass'||st==='passed'));
      el.classList.toggle('hidden',!show);
    });
  });
});
</script>
</body></html>"""
)


def _escape(text: str) -> str:
    return html.escape(text, quote=True)


def _img_src(step: RunStepResult, evidence_dir: Path | None) -> str:
    if step.screenshot_path:
        path = Path(step.screenshot_path)
        if path.is_file():
            raw = path.read_bytes()
            b64 = base64.b64encode(raw).decode("ascii")
            return f"data:image/png;base64,{b64}"
        # Relative path next to report — keep as file reference for folder exports.
        if evidence_dir is not None:
            rel = path.name
            return _escape(rel)
        return _escape(str(path))
    if step.screenshot_b64:
        return f"data:image/png;base64,{step.screenshot_b64}"
    return ""


def _filter_status(step: RunStepResult) -> str:
    if step.step_status is not None:
        return step.step_status.value.lower()
    mapping = {
        "PASS": "passed",
        "FAIL": "failed",
        "HEALED": "healed",
        "BLOCKED": "blocked",
        "SKIPPED": "skipped",
        "CANCELLED": "skipped",
    }
    return mapping.get(str(step.status), str(step.status).lower())


def _heal_payload(
    step: RunStepResult,
    audits: dict[int, StepAuditEntry],
    masker: SecretMasker,
) -> dict[str, str] | None:
    audit = step.audit or audits.get(step.step_no)
    status = _filter_status(step)
    if status != "healed" and audit is None:
        return None
    if audit is None:
        return None
    conf = float(audit.confidence_score or step.confidence_score or 0.0)
    if conf >= HEAL_PERSIST_THRESHOLD or audit.persisted:
        note = "Đã tự động cập nhật ElementFingerprint bền vững vào YAML"
    elif conf >= HEAL_RUN_THRESHOLD:
        note = "Heal tạm thời để chạy tiếp — Chưa ghi đè YAML"
    else:
        note = "Heal tạm thời để chạy tiếp — Chưa ghi đè YAML"
    prev = audit.previous_selector or audit.old_locator or step.previous_selector or "—"
    healed = audit.healed_selector or audit.new_locator or "—"
    return {
        "confidence": f"{conf:.2f}",
        "note": _escape(note),
        "previous": _escape(masker.mask_text(prev)),
        "healed": _escape(masker.mask_text(healed)),
    }


class HtmlReporter:
    def __init__(self, masker: SecretMasker | None = None) -> None:
        self._masker = masker or get_secret_masker()

    def write(self, report: RunReport, output: Path) -> Path:
        output.parent.mkdir(parents=True, exist_ok=True)
        counts = Counter(case.status for case in report.cases)
        audits = {a.step_no: a for a in report.healing_audit}
        evidence_dir = output.parent
        view_cases = []
        for case in report.cases:
            steps_view = []
            for step in case.steps:
                console = "\n".join(step.console_logs)
                steps_view.append(
                    {
                        "step_no": step.step_no,
                        "action": _escape(step.action),
                        "display_status": _escape(
                            step.step_status.value if step.step_status else str(step.status)
                        ),
                        "filter_status": _filter_status(step),
                        "duration_ms": step.duration_ms,
                        "message": _escape(self._masker.mask_text(step.message or "")),
                        "console_logs": _escape(self._masker.mask_text(console)) if console else "",
                        "img_src": _img_src(step, evidence_dir),
                        "click_x": step.click_x,
                        "click_y": step.click_y,
                        "heal_diff": _heal_payload(step, audits, self._masker),
                    }
                )
            view_cases.append(
                {
                    "case_id": _escape(case.case_id),
                    "case_code": _escape(case.case_code),
                    "title": _escape(self._masker.mask_text(case.title)),
                    "status": case.status,
                    "skip_reason": _escape(self._masker.mask_text(case.skip_reason or ""))
                    if case.skip_reason
                    else "",
                    "steps": steps_view,
                }
            )
        healing_view = [
            {
                "step_no": a.step_no,
                "previous": _escape(
                    self._masker.mask_text(a.previous_selector or a.old_locator or "")
                ),
                "healed": _escape(
                    self._masker.mask_text(a.healed_selector or a.new_locator or "")
                ),
                "reason": _escape(self._masker.mask_text(a.reason)),
            }
            for a in report.healing_audit
        ]
        html_body = _TEMPLATE.render(
            report={
                "run_id": _escape(report.run_id),
                "project_name": _escape(self._masker.mask_text(report.project_name)),
                "started_at": _escape(report.started_at),
                "finished_at": _escape(report.finished_at),
                "cases": view_cases,
                "healing_audit": healing_view,
            },
            summary_counts=dict(counts),
        )
        output.write_text(html_body, encoding="utf-8")
        return output
