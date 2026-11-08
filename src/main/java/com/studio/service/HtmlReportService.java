package com.studio.service;

import com.studio.model.AssertResult;
import com.studio.model.CaseResult;
import com.studio.model.ExportLanguage;
import com.studio.model.HealedStepMeta;
import com.studio.model.RunReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * Service to generate standalone, self-contained HTML test reports with embedded
 * screenshots, assertion breakdowns, interactive filters, and execution stats.
 */
public class HtmlReportService {

    public static void export(RunReport report, Path targetHtmlFile) throws IOException {
        export(report, targetHtmlFile, ExportLanguage.VIETNAMESE);
    }

    public static void export(RunReport report, Path targetHtmlFile, ExportLanguage lang) throws IOException {
        String html = generateHtml(report, lang);
        if (targetHtmlFile.getParent() != null) {
            Files.createDirectories(targetHtmlFile.getParent());
        }
        Files.writeString(targetHtmlFile, html);
    }

    public static String generateHtml(RunReport report) {
        return generateHtml(report, ExportLanguage.VIETNAMESE);
    }

    public static String generateHtml(RunReport report, ExportLanguage lang) {
        ExportLanguage effectiveLang = lang != null ? lang : ExportLanguage.VIETNAMESE;

        if (report == null) {
            return "<html><body><h1>" + I18nService.get(effectiveLang, "export.html.no_data") + "</h1></body></html>";
        }

        int total = report.getTotal();
        int passed = report.getPassed();
        int failed = report.getFailed();
        int skipped = report.getSkipped();
        int passRate = total > 0 ? (int) Math.round((double) passed / total * 100) : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"").append(effectiveLang.getCode()).append("\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>").append(I18nService.get(effectiveLang, "export.html.report_title", escapeHtml(report.getRunId()))).append("</title>\n");
        sb.append("<style>\n");
        sb.append("""
            :root {
                --bg-main: #1a1b26;
                --bg-card: #16161e;
                --bg-subtle: #24283b;
                --text-main: #c0caf5;
                --text-muted: #7982a9;
                --border: #2a2b3d;
                --accent: #7aa2f7;
                --pass: #a6e3a1;
                --fail: #f38ba8;
                --skip: #f9e2af;
                --info: #89dceb;
            }
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                background-color: var(--bg-main);
                color: var(--text-main);
                line-height: 1.5;
                padding: 24px;
            }
            .container { max-width: 1200px; margin: 0 auto; }
            header {
                display: flex;
                flex-wrap: wrap;
                align-items: center;
                justify-content: space-between;
                gap: 16px;
                padding-bottom: 20px;
                border-bottom: 1px solid var(--border);
                margin-bottom: 24px;
            }
            h1 { font-size: 24px; font-weight: 700; color: #fff; display: flex; align-items: center; gap: 8px; }
            .meta-tags { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 6px; }
            .tag {
                background: var(--bg-subtle);
                border: 1px solid var(--border);
                border-radius: 6px;
                padding: 4px 10px;
                font-size: 12px;
                color: var(--text-muted);
            }
            .stats-grid {
                display: grid;
                grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
                gap: 14px;
                margin-bottom: 24px;
            }
            .stat-card {
                background: var(--bg-card);
                border: 1px solid var(--border);
                border-radius: 10px;
                padding: 16px;
                text-align: center;
            }
            .stat-value { font-size: 32px; font-weight: bold; margin-bottom: 4px; }
            .stat-label { font-size: 12px; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.5px; }
            .stat-card.pass .stat-value { color: var(--pass); }
            .stat-card.fail .stat-value { color: var(--fail); }
            .stat-card.skip .stat-value { color: var(--skip); }
            .stat-card.rate .stat-value { color: var(--accent); }
            .progress-bar-wrap {
                background: #101118;
                border-radius: 8px;
                height: 12px;
                overflow: hidden;
                display: flex;
                margin-bottom: 28px;
                border: 1px solid var(--border);
            }
            .bar-segment { height: 100%; transition: width 0.3s; }
            .filter-bar {
                display: flex;
                align-items: center;
                gap: 10px;
                margin-bottom: 18px;
                flex-wrap: wrap;
            }
            .btn-filter {
                background: var(--bg-subtle);
                border: 1px solid var(--border);
                color: var(--text-main);
                padding: 6px 14px;
                border-radius: 6px;
                cursor: pointer;
                font-size: 13px;
                transition: all 0.2s;
            }
            .btn-filter:hover, .btn-filter.active {
                background: var(--accent);
                color: #1a1b26;
                font-weight: 600;
            }
            .case-card {
                background: var(--bg-card);
                border: 1px solid var(--border);
                border-radius: 10px;
                margin-bottom: 16px;
                overflow: hidden;
            }
            .case-header {
                padding: 14px 18px;
                display: flex;
                justify-content: space-between;
                align-items: center;
                cursor: pointer;
                background: rgba(255, 255, 255, 0.02);
                border-bottom: 1px solid transparent;
            }
            .case-header:hover { background: rgba(255, 255, 255, 0.05); }
            .case-title-row { display: flex; align-items: center; gap: 12px; }
            .badge {
                font-size: 11px;
                font-weight: 700;
                padding: 4px 10px;
                border-radius: 12px;
                text-transform: uppercase;
            }
            .badge.passed { background: rgba(166, 227, 161, 0.2); color: var(--pass); border: 1px solid var(--pass); }
            .badge.failed { background: rgba(243, 139, 168, 0.2); color: var(--fail); border: 1px solid var(--fail); }
            .badge.skipped { background: rgba(249, 226, 175, 0.2); color: var(--skip); border: 1px solid var(--skip); }
            .badge.error { background: rgba(243, 139, 168, 0.3); color: var(--fail); border: 1px solid var(--fail); }
            .case-name { font-weight: 600; font-size: 15px; color: #fff; }
            .case-suite { font-size: 12px; color: var(--text-muted); }
            .case-duration { font-size: 12px; color: var(--text-muted); }
            .case-body { padding: 18px; border-top: 1px solid var(--border); display: block; }
            .log-box {
                background: #111218;
                border: 1px solid #232433;
                border-radius: 6px;
                padding: 12px;
                font-family: monospace;
                font-size: 12px;
                color: #e0af68;
                white-space: pre-wrap;
                margin-top: 10px;
                max-height: 240px;
                overflow-y: auto;
            }
            table {
                width: 100%;
                border-collapse: collapse;
                margin-top: 12px;
                font-size: 13px;
            }
            th, td {
                padding: 10px 12px;
                text-align: left;
                border-bottom: 1px solid var(--border);
            }
            th { background: #13141d; color: var(--text-muted); font-weight: 600; }
            .evidence-box {
                margin-top: 16px;
                padding: 12px;
                background: #13141d;
                border-radius: 8px;
                border: 1px solid var(--border);
            }
            .evidence-title { font-size: 13px; font-weight: 600; margin-bottom: 8px; color: var(--info); }
            .screenshot-img {
                max-width: 100%;
                height: auto;
                border-radius: 6px;
                border: 1px solid #333;
                cursor: pointer;
                transition: transform 0.2s;
            }
            .screenshot-img:hover { transform: scale(1.01); }
            footer {
                text-align: center;
                margin-top: 40px;
                padding-top: 20px;
                border-top: 1px solid var(--border);
                font-size: 12px;
                color: var(--text-muted);
            }
        """);
        sb.append("</style>\n</head>\n<body>\n<div class=\"container\">\n");

        // Header
        sb.append("<header>\n<div>\n");
        sb.append("<h1>🎭 Test Studio</h1>\n");
        sb.append("<div class=\"meta-tags\">\n");
        sb.append("<span class=\"tag\">").append(I18nService.get(effectiveLang, "export.html.run_id")).append(" ")
          .append("<strong>").append(escapeHtml(report.getRunId())).append("</strong></span>\n");
        sb.append("<span class=\"tag\">").append(I18nService.get(effectiveLang, "export.html.scope")).append(" ")
          .append(escapeHtml(report.getScope())).append(" (").append(escapeHtml(report.getScopeName())).append(")</span>\n");
        sb.append("<span class=\"tag\">").append(I18nService.get(effectiveLang, "export.html.browser")).append(" ")
          .append(escapeHtml(report.getBrowser())).append(" (").append(report.isHeadless() ? "Headless" : "Headed").append(")</span>\n");
        if (report.getStartedAt() != null) {
            sb.append("<span class=\"tag\">").append(I18nService.get(effectiveLang, "export.html.started")).append(" ")
              .append(escapeHtml(report.getStartedAt())).append("</span>\n");
        }
        if (report.getFinishedAt() != null) {
            sb.append("<span class=\"tag\">").append(I18nService.get(effectiveLang, "export.html.finished")).append(" ")
              .append(escapeHtml(report.getFinishedAt())).append("</span>\n");
        }
        sb.append("</div>\n</div>\n</header>\n");

        // Stats Cards
        sb.append("<div class=\"stats-grid\">\n");
        sb.append("<div class=\"stat-card\"><div class=\"stat-value\">").append(total)
          .append("</div><div class=\"stat-label\">").append(I18nService.get(effectiveLang, "export.html.total")).append("</div></div>\n");
        sb.append("<div class=\"stat-card pass\"><div class=\"stat-value\">").append(passed)
          .append("</div><div class=\"stat-label\">").append(I18nService.get(effectiveLang, "export.html.passed")).append("</div></div>\n");
        sb.append("<div class=\"stat-card fail\"><div class=\"stat-value\">").append(failed)
          .append("</div><div class=\"stat-label\">").append(I18nService.get(effectiveLang, "export.html.failed")).append("</div></div>\n");
        sb.append("<div class=\"stat-card skip\"><div class=\"stat-value\">").append(skipped)
          .append("</div><div class=\"stat-label\">").append(I18nService.get(effectiveLang, "export.html.skipped")).append("</div></div>\n");
        sb.append("<div class=\"stat-card rate\"><div class=\"stat-value\">").append(passRate)
          .append("%</div><div class=\"stat-label\">").append(I18nService.get(effectiveLang, "export.html.pass_rate")).append("</div></div>\n");
        sb.append("</div>\n");

        // Progress Bar
        sb.append("<div class=\"progress-bar-wrap\">\n");
        if (total > 0) {
            double pPct = (double) passed / total * 100;
            double fPct = (double) failed / total * 100;
            double sPct = (double) skipped / total * 100;
            sb.append(String.format("<div class=\"bar-segment\" style=\"width: %.1f%%; background: var(--pass);\"></div>\n", pPct));
            sb.append(String.format("<div class=\"bar-segment\" style=\"width: %.1f%%; background: var(--fail);\"></div>\n", fPct));
            sb.append(String.format("<div class=\"bar-segment\" style=\"width: %.1f%%; background: var(--skip);\"></div>\n", sPct));
        }
        sb.append("</div>\n");

        // Filter buttons
        sb.append("<div class=\"filter-bar\">\n");
        sb.append("<span style=\"font-size:13px; color:var(--text-muted); margin-right:4px;\">")
          .append(I18nService.get(effectiveLang, "export.html.filter")).append("</span>\n");
        sb.append("<button class=\"btn-filter active\" onclick=\"filterCases('all', this)\">")
          .append(I18nService.get(effectiveLang, "export.html.filter_all")).append(" (").append(total).append(")</button>\n");
        sb.append("<button class=\"btn-filter\" onclick=\"filterCases('passed', this)\">")
          .append(I18nService.get(effectiveLang, "export.html.filter_passed")).append(" (").append(passed).append(")</button>\n");
        sb.append("<button class=\"btn-filter\" onclick=\"filterCases('failed', this)\">")
          .append(I18nService.get(effectiveLang, "export.html.filter_failed")).append(" (").append(failed).append(")</button>\n");
        if (skipped > 0) {
            sb.append("<button class=\"btn-filter\" onclick=\"filterCases('skipped', this)\">")
              .append(I18nService.get(effectiveLang, "export.html.filter_skipped")).append(" (").append(skipped).append(")</button>\n");
        }
        sb.append("</div>\n");

        // Cases List
        List<CaseResult> cases = report.getCases() != null ? report.getCases() : List.of();
        for (CaseResult cr : cases) {
            String status = cr.getStatus() != null ? cr.getStatus().toUpperCase() : "UNKNOWN";
            String badgeClass = switch (status) {
                case "PASSED" -> "passed";
                case "FAILED" -> "failed";
                case "SKIPPED" -> "skipped";
                default -> "error";
            };

            sb.append("<div class=\"case-card\" data-status=\"").append(status.toLowerCase()).append("\">\n");
            sb.append("<div class=\"case-header\" onclick=\"toggleCaseBody(this)\">\n");
            sb.append("<div class=\"case-title-row\">\n");
            sb.append("<span class=\"badge ").append(badgeClass).append("\">").append(status).append("</span>\n");
            if (cr.getHealedSteps() != null && !cr.getHealedSteps().isEmpty()) {
                sb.append("<span class=\"badge\" style=\"background:#0284c7; color:#fff;\">✨ Auto-Healed (")
                  .append(cr.getHealedSteps().size()).append(")</span>\n");
            }
            sb.append("<span class=\"case-name\">").append(escapeHtml(cr.getTestCaseName())).append("</span>\n");
            if (cr.getSuiteName() != null && !cr.getSuiteName().isBlank()) {
                sb.append("<span class=\"case-suite\">📁 ").append(escapeHtml(cr.getSuiteName())).append("</span>\n");
            }
            sb.append("</div>\n");
            sb.append("<div class=\"case-duration\">⏱️ ").append(cr.getDurationMs()).append("ms</div>\n");
            sb.append("</div>\n"); // end case-header

            sb.append("<div class=\"case-body\">\n");

            // Failure reason / Skip reason / log
            if (cr.getFailureReason() != null && !cr.getFailureReason().isBlank()) {
                sb.append("<div style=\"color:var(--fail); font-weight:600; margin-bottom:8px;\">")
                  .append(I18nService.get(effectiveLang, "export.html.failure")).append(" ")
                  .append(escapeHtml(cr.getFailureReason())).append("</div>\n");
            }
            if (cr.getSkipReason() != null && !cr.getSkipReason().isBlank()) {
                sb.append("<div style=\"color:var(--skip); font-weight:600; margin-bottom:8px;\">")
                  .append(I18nService.get(effectiveLang, "export.html.skip_reason")).append(" ")
                  .append(escapeHtml(cr.getSkipReason())).append("</div>\n");
            }
            if (cr.getLogExcerpt() != null && !cr.getLogExcerpt().isBlank()) {
                sb.append("<div class=\"log-box\">").append(escapeHtml(cr.getLogExcerpt())).append("</div>\n");
            }

            // Assertions table
            List<AssertResult> asserts = cr.getAssertResults();
            if (asserts != null && !asserts.isEmpty()) {
                sb.append("<h4 style=\"margin-top:14px; font-size:13px; color:var(--text-main);\">")
                  .append(I18nService.get(effectiveLang, "export.html.assertions_title", asserts.size())).append("</h4>\n");
                sb.append("<table><thead><tr><th>")
                  .append(I18nService.get(effectiveLang, "export.html.asserts_col_num")).append("</th><th>")
                  .append(I18nService.get(effectiveLang, "export.html.asserts_col_type")).append("</th><th>")
                  .append(I18nService.get(effectiveLang, "export.html.asserts_col_detail")).append("</th><th>")
                  .append(I18nService.get(effectiveLang, "export.html.asserts_col_result")).append("</th><th>")
                  .append(I18nService.get(effectiveLang, "export.html.asserts_col_msg")).append("</th></tr></thead><tbody>\n");
                int idx = 1;
                for (AssertResult ar : asserts) {
                    String aStatus = ar.getStatus() != null ? ar.getStatus().toUpperCase() : "SKIPPED";
                    String aBadge = switch (aStatus) {
                        case "PASSED" -> "passed";
                        case "FAILED" -> "failed";
                        default -> "skipped";
                    };
                    sb.append("<tr>");
                    sb.append("<td>").append(idx++).append("</td>");
                    sb.append("<td><code>").append(escapeHtml(ar.getType())).append("</code></td>");
                    String detail = (ar.getDetail() != null && !ar.getDetail().isBlank()) ? ar.getDetail() : "-";
                    sb.append("<td>").append(escapeHtml(detail)).append("</td>");
                    sb.append("<td><span class=\"badge ").append(aBadge).append("\">").append(aStatus).append("</span></td>");
                    String msg = (ar.getMessage() != null && !ar.getMessage().isBlank()) ? ar.getMessage() : "";
                    sb.append("<td>").append(escapeHtml(msg)).append("</td>");
                    sb.append("</tr>\n");
                }
                sb.append("</tbody></table>\n");
            }

            // Self-Healing Audit Table
            List<HealedStepMeta> healed = cr.getHealedSteps();
            if (healed != null && !healed.isEmpty()) {
                sb.append("<h4 style=\"margin-top:14px; font-size:13px; color:var(--primary);\">")
                  .append(I18nService.get(effectiveLang, "export.html.healing_title", healed.size())).append("</h4>\n");
                sb.append("<table><thead><tr><th>")
                  .append(I18nService.get(effectiveLang, "export.html.healing_col_action")).append("</th><th>")
                  .append(I18nService.get(effectiveLang, "export.html.healing_col_orig")).append("</th><th>")
                  .append(I18nService.get(effectiveLang, "export.html.healing_col_repaired")).append("</th><th>")
                  .append(I18nService.get(effectiveLang, "export.html.healing_col_method")).append("</th><th>")
                  .append(I18nService.get(effectiveLang, "export.html.healing_col_confidence")).append("</th><th>")
                  .append(I18nService.get(effectiveLang, "export.html.healing_col_time")).append("</th></tr></thead><tbody>\n");
                for (HealedStepMeta hm : healed) {
                    sb.append("<tr>");
                    sb.append("<td><code>").append(escapeHtml(hm.getActionDescription())).append("</code></td>");
                    sb.append("<td><code style=\"color:var(--fail); text-decoration:line-through;\">").append(escapeHtml(hm.getOriginalSelector())).append("</code></td>");
                    sb.append("<td><code style=\"color:var(--pass); font-weight:bold;\">").append(escapeHtml(hm.getRepairedSelector())).append("</code></td>");
                    sb.append("<td><span class=\"badge passed\">").append(escapeHtml(hm.getMethod())).append("</span></td>");
                    sb.append("<td>").append(String.format("%.0f%%", hm.getConfidence() * 100)).append("</td>");
                    sb.append("<td>").append(escapeHtml(hm.getTimestamp())).append("</td>");
                    sb.append("</tr>\n");
                }
                sb.append("</tbody></table>\n");
            }

            // Evidence Screenshot (Embedded Base64)
            String screenshotPath = cr.getScreenshotPath();
            if (screenshotPath != null && !screenshotPath.isBlank()) {
                Path shotFile = Path.of(screenshotPath);
                if (Files.exists(shotFile)) {
                    try {
                        byte[] bytes = Files.readAllBytes(shotFile);
                        String b64 = Base64.getEncoder().encodeToString(bytes);
                        sb.append("<div class=\"evidence-box\">\n");
                        sb.append("<div class=\"evidence-title\">")
                          .append(I18nService.get(effectiveLang, "export.html.evidence_title"))
                          .append("</div>\n");
                        sb.append("<img src=\"data:image/png;base64,").append(b64)
                          .append("\" alt=\"Evidence for ").append(escapeHtml(cr.getTestCaseName()))
                          .append("\" class=\"screenshot-img\" onclick=\"window.open(this.src)\" title=\"")
                          .append(I18nService.get(effectiveLang, "export.html.click_to_view")).append("\"/>\n");
                        sb.append("</div>\n");
                    } catch (Exception ignored) {
                    }
                }
            }

            sb.append("</div>\n"); // end case-body
            sb.append("</div>\n"); // end case-card
        }

        // Footer
        sb.append("<footer>")
          .append(I18nService.get(effectiveLang, "export.html.footer"))
          .append("</footer>\n");

        // JavaScript for accordion & filtering
        sb.append("<script>\n");
        sb.append("""
            function toggleCaseBody(header) {
                var body = header.nextElementSibling;
                body.style.display = (body.style.display === 'none') ? 'block' : 'none';
            }
            function filterCases(status, btn) {
                document.querySelectorAll('.btn-filter').forEach(function(b) { b.classList.remove('active'); });
                btn.classList.add('active');
                var cards = document.querySelectorAll('.case-card');
                cards.forEach(function(card) {
                    if (status === 'all' || card.getAttribute('data-status') === status) {
                        card.style.display = 'block';
                    } else {
                        card.style.display = 'none';
                    }
                });
            }
        """);
        sb.append("</script>\n");

        sb.append("</div>\n</body>\n</html>\n");
        return sb.toString();
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}

