"""Self-contained HTML report with Visual Action Replay markers."""

from __future__ import annotations

from pathlib import Path

from jinja2 import Template

from ai_qa_test_studio.core.models import RunReport

_TEMPLATE = Template(
    """<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"/>
<title>Run {{ report.run_id }}</title>
<style>
body{font-family:system-ui,sans-serif;margin:24px;background:#111;color:#eee}
.card{border:1px solid #333;padding:12px;margin:8px 0;border-radius:8px}
.pass{color:#3ddc97}.fail{color:#ff6b6b}.healed{color:#ffd166}
.shot{position:relative;display:inline-block;max-width:100%}
.marker{position:absolute;width:12px;height:12px;border:2px solid #ff6b6b;border-radius:50%}
</style></head><body>
<h1>AI QA Test Studio Report</h1>
<p>Project: {{ report.project_name }} · Run: {{ report.run_id }}</p>
<p>{{ report.started_at }} → {{ report.finished_at }}</p>
{% for case in report.cases %}
<section class="card">
<h2>{{ case.case_code }} — {{ case.title }}
<span class="{{ case.status|lower }}">{{ case.status }}</span></h2>
{% for step in case.steps %}
<div class="card">
<strong>#{{ step.step_no }} {{ step.action }}</strong>
<span class="{{ step.status|lower }}">{{ step.status }}</span>
<em>{{ step.duration_ms }} ms</em>
<p>{{ step.message }}</p>
{% if step.screenshot_b64 %}
<div class="shot">
<img src="data:image/png;base64,{{ step.screenshot_b64 }}" alt="step"/>
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
<div class="card">Step {{ a.step_no }}: {{ a.old_locator }} → {{ a.new_locator }} ({{ a.reason }})</div>
{% endfor %}
{% endif %}
</body></html>"""
)


class HtmlReporter:
    def write(self, report: RunReport, output: Path) -> Path:
        output.parent.mkdir(parents=True, exist_ok=True)
        html = _TEMPLATE.render(report=report)
        output.write_text(html, encoding="utf-8")
        return output
