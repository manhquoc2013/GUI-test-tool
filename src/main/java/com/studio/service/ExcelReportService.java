package com.studio.service;

import com.studio.model.AssertResult;
import com.studio.model.CaseResult;
import com.studio.model.RunReport;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Exports a RunReport to a styled Apache POI .xlsx workbook (Summary, Cases, Asserts).
 */
public class ExcelReportService {

    private static final byte[] NAVY = hex("1F2A44");
    private static final byte[] NAVY_SOFT = hex("2A3654");
    private static final byte[] HEADER_BG = hex("2C3E6B");
    private static final byte[] LABEL_BG = hex("EEF2F7");
    private static final byte[] ZEBRA = hex("F7F9FC");
    private static final byte[] PASS_BG = hex("E8F8EF");
    private static final byte[] PASS_FG = hex("1B7A45");
    private static final byte[] FAIL_BG = hex("FDECEC");
    private static final byte[] FAIL_FG = hex("C0392B");
    private static final byte[] SKIP_BG = hex("F2F3F5");
    private static final byte[] SKIP_FG = hex("5D6D7E");
    private static final byte[] RUN_BG = hex("EAF2FB");
    private static final byte[] RUN_FG = hex("2471A3");
    private static final byte[] ACTIVE_BG = hex("EEF4FF");
    private static final byte[] ACTIVE_FG = hex("2C3E6B");
    private static final byte[] LINK_FG = hex("1A5FB4");

    private static final DateTimeFormatter DISPLAY_DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Character widths → POI units; leave room for autofilter dropdown. */
    private static final int[] CASES_WIDTHS = {
            6, 22, 16, 14, 12, 16, 14, 28, 28
    };
    private static final int[] ASSERTS_WIDTHS = {
            18, 12, 16, 24, 16, 16, 36
    };

    private ExcelReportService() {}

    public static void export(RunReport report, Path outputPath) throws Exception {
        Files.createDirectories(outputPath.getParent());

        try (Workbook wb = new XSSFWorkbook()) {
            Styles styles = Styles.create(wb);
            CreationHelper helper = wb.getCreationHelper();

            Sheet summary = wb.createSheet("Summary");
            createSummarySheet(summary, report, styles);
            tintSheet(summary, IndexedColors.DARK_BLUE);

            Sheet cases = wb.createSheet("Cases");
            createCasesSheet(cases, report, styles, helper);
            tintSheet(cases, IndexedColors.DARK_TEAL);

            Sheet asserts = wb.createSheet("Asserts");
            createAssertsSheet(asserts, report, styles);
            tintSheet(asserts, IndexedColors.GREY_50_PERCENT);

            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                wb.write(fos);
            }
        }
    }

    private static void createSummarySheet(Sheet sheet, RunReport report, Styles styles) {
        sheet.setDisplayGridlines(false);

        Row title = sheet.createRow(0);
        title.setHeightInPoints(32);
        Cell titleA = title.createCell(0);
        titleA.setCellValue("Test Run Report");
        titleA.setCellStyle(styles.title);
        Cell titleB = title.createCell(1);
        titleB.setCellStyle(styles.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

        Row subtitle = sheet.createRow(1);
        subtitle.setHeightInPoints(20);
        Cell subA = subtitle.createCell(0);
        subA.setCellValue("Run ID  ·  " + nullToEmpty(report.getRunId()));
        subA.setCellStyle(styles.subtitleBar);
        Cell subB = subtitle.createCell(1);
        subB.setCellStyle(styles.subtitleBar);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 1));

        int r = 3;
        r = summaryPair(sheet, r, "Run ID", nullToEmpty(report.getRunId()), styles);
        r = summaryPair(sheet, r, "Scope", nullToEmpty(report.getScope()), styles);
        r = summaryPair(sheet, r, "Target", report.getScopeName() != null ? report.getScopeName() : "ALL", styles);
        r = summaryPair(sheet, r, "Browser", nullToEmpty(report.getBrowser()), styles);
        r = summaryPair(sheet, r, "Headless", yesNo(report.isHeadless()), styles);
        r = summaryPair(sheet, r, "Share Context", yesNo(report.isShareContext()), styles);
        r = summaryPair(sheet, r, "Stop on First Failure", yesNo(report.isStopOnFirstFailure()), styles);
        r = summaryPair(sheet, r, "Started", formatDateTime(report.getStartedAt()), styles);
        r = summaryPair(sheet, r, "Finished", formatDateTime(report.getFinishedAt()), styles);

        r += 1;
        Row kpiHeader = sheet.createRow(r++);
        kpiHeader.setHeightInPoints(20);
        Cell kpiTitle = kpiHeader.createCell(0);
        kpiTitle.setCellValue("Results");
        kpiTitle.setCellStyle(styles.section);
        Cell kpiTitleB = kpiHeader.createCell(1);
        kpiTitleB.setCellStyle(styles.section);
        sheet.addMergedRegion(new CellRangeAddress(r - 1, r - 1, 0, 1));

        r = summaryNumber(sheet, r, "Total Cases", report.getTotal(), styles);
        r = summaryKpi(sheet, r, "Passed", report.getPassed(), "PASSED", styles);
        r = summaryKpi(sheet, r, "Failed", report.getFailed(), "FAILED", styles);
        r = summaryKpi(sheet, r, "Skipped", report.getSkipped(), "SKIPPED", styles);

        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 52 * 256);
        sheet.createFreezePane(0, 3);
    }

    private static int summaryPair(Sheet sheet, int rowIdx, String label, String value, Styles styles) {
        Row row = sheet.createRow(rowIdx);
        row.setHeightInPoints(18);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.label);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(styles.value);
        return rowIdx + 1;
    }

    private static int summaryNumber(Sheet sheet, int rowIdx, String label, int value, Styles styles) {
        Row row = sheet.createRow(rowIdx);
        row.setHeightInPoints(18);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.label);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(styles.valueNumber);
        return rowIdx + 1;
    }

    private static int summaryKpi(Sheet sheet, int rowIdx, String label, int value, String statusKey, Styles styles) {
        Row row = sheet.createRow(rowIdx);
        row.setHeightInPoints(18);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.label);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(styles.statusStyle(statusKey));
        return rowIdx + 1;
    }

    private static void createCasesSheet(Sheet sheet, RunReport report, Styles styles, CreationHelper helper) {
        sheet.setDisplayGridlines(false);
        String[] cols = {"#", "Case Name", "Suite", "Group", "Status",
                "Skip Reason", "Duration (ms)", "Screenshot", "Failure Reason"};
        Row header = sheet.createRow(0);
        header.setHeightInPoints(22);
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(styles.tableHeader);
        }

        int r = 1;
        for (CaseResult cr : report.getCases()) {
            boolean zebra = r % 2 == 0;
            Row row = sheet.createRow(r++);
            row.setHeightInPoints(18);
            writeNumber(row, 0, displayIndex(cr.getIndex()), styles, zebra, true);
            writeText(row, 1, nullToEmpty(cr.getTestCaseName()), styles, zebra, false);
            writeText(row, 2, nullToEmpty(cr.getSuiteName()), styles, zebra, false);
            writeText(row, 3, nullToEmpty(cr.getGroupPath()), styles, zebra, false);
            writeStatus(row, 4, cr.getStatus(), styles);
            writeText(row, 5, nullToEmpty(cr.getSkipReason()), styles, zebra, false);
            writeDuration(row, 6, cr.getDurationMs(), styles, zebra);
            writeScreenshot(row, 7, cr.getScreenshotPath(), styles, zebra, helper);
            writeText(row, 8, nullToEmpty(cr.getFailureReason()), styles, zebra, false);
        }

        int lastCol = cols.length - 1;
        int lastRow = Math.max(0, r - 1);
        if (lastRow >= 0) {
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(1, lastRow), 0, lastCol));
        }
        sheet.createFreezePane(0, 1);
        applyWidths(sheet, CASES_WIDTHS);
        applyTableBorder(sheet, 0, Math.max(1, lastRow), 0, lastCol);
    }

    private static void createAssertsSheet(Sheet sheet, RunReport report, Styles styles) {
        sheet.setDisplayGridlines(false);
        String[] cols = {"Case", "Assert #", "Type", "Detail",
                "Config Status", "Result Status", "Message"};
        Row header = sheet.createRow(0);
        header.setHeightInPoints(22);
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(styles.tableHeader);
        }

        int r = 1;
        for (CaseResult cr : report.getCases()) {
            if (cr.getAssertResults() == null) continue;
            for (AssertResult ar : cr.getAssertResults()) {
                boolean zebra = r % 2 == 0;
                Row row = sheet.createRow(r++);
                row.setHeightInPoints(18);
                writeText(row, 0, nullToEmpty(cr.getTestCaseName()), styles, zebra, false);
                writeNumber(row, 1, displayIndex(ar.getIndex()), styles, zebra, true);
                writeText(row, 2, nullToEmpty(ar.getType()), styles, zebra, false);
                writeText(row, 3, nullToEmpty(ar.getDetail()), styles, zebra, false);
                writeStatus(row, 4, ar.getAssertStatus(), styles);
                writeStatus(row, 5, ar.getStatus(), styles);
                writeText(row, 6, nullToEmpty(ar.getMessage()), styles, zebra, false);
            }
        }

        int lastCol = cols.length - 1;
        int lastRow = Math.max(0, r - 1);
        if (lastRow >= 0) {
            sheet.setAutoFilter(new CellRangeAddress(0, Math.max(1, lastRow), 0, lastCol));
        }
        sheet.createFreezePane(0, 1);
        applyWidths(sheet, ASSERTS_WIDTHS);
        applyTableBorder(sheet, 0, Math.max(1, lastRow), 0, lastCol);
    }

    private static void writeText(Row row, int col, String value, Styles styles, boolean zebra, boolean center) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(center ? styles.dataCenter(zebra) : styles.data(zebra));
    }

    private static void writeNumber(Row row, int col, long value, Styles styles, boolean zebra, boolean center) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(center ? styles.dataCenter(zebra) : styles.data(zebra));
    }

    private static void writeDuration(Row row, int col, long ms, Styles styles, boolean zebra) {
        Cell cell = row.createCell(col);
        cell.setCellValue(ms);
        cell.setCellStyle(styles.duration(zebra));
    }

    private static void writeScreenshot(Row row, int col, String path, Styles styles, boolean zebra,
                                        CreationHelper helper) {
        Cell cell = row.createCell(col);
        String raw = nullToEmpty(path);
        if (raw.isEmpty()) {
            cell.setCellValue("");
            cell.setCellStyle(styles.data(zebra));
            return;
        }
        String label = Path.of(raw).getFileName().toString();
        cell.setCellValue(label);
        cell.setCellStyle(styles.link(zebra));
        try {
            Hyperlink link = helper.createHyperlink(HyperlinkType.URL);
            link.setAddress(Path.of(raw).toAbsolutePath().toUri().toString());
            cell.setHyperlink(link);
        } catch (Exception ignored) {
            // Keep filename text if hyperlink cannot be created
        }
    }

    private static void writeStatus(Row row, int col, String status, Styles styles) {
        Cell cell = row.createCell(col);
        String text = nullToEmpty(status);
        cell.setCellValue(text);
        cell.setCellStyle(styles.statusStyle(text));
    }

    private static void applyWidths(Sheet sheet, int[] charWidths) {
        for (int i = 0; i < charWidths.length; i++) {
            sheet.setColumnWidth(i, charWidths[i] * 256);
        }
    }

    private static void applyTableBorder(Sheet sheet, int firstRow, int lastRow, int firstCol, int lastCol) {
        CellRangeAddress region = new CellRangeAddress(firstRow, lastRow, firstCol, lastCol);
        RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
    }

    private static int displayIndex(int zeroBased) {
        return zeroBased + 1;
    }

    private static String yesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    private static String formatDateTime(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String text = raw.trim();
        try {
            return OffsetDateTime.parse(text).toLocalDateTime().format(DISPLAY_DT);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(text).format(DISPLAY_DT);
        } catch (DateTimeParseException ignored) {
        }
        // Truncate fractional seconds if present: 2026-07-25T23:24:49.527468
        int t = text.indexOf('T');
        if (t > 0 && text.length() >= t + 9) {
            String date = text.substring(0, t);
            String time = text.substring(t + 1);
            int dot = time.indexOf('.');
            if (dot > 0) {
                time = time.substring(0, Math.min(8, dot));
            } else if (time.length() > 8) {
                time = time.substring(0, 8);
            }
            if (time.length() >= 8) {
                return date + " " + time.substring(0, 8);
            }
        }
        return text;
    }

    private static void tintSheet(Sheet sheet, IndexedColors color) {
        if (sheet instanceof XSSFSheet xssf) {
            xssf.setTabColor(new XSSFColor(color, new DefaultIndexedColorMap()));
        }
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static byte[] hex(String rgb) {
        return new byte[]{
                (byte) Integer.parseInt(rgb.substring(0, 2), 16),
                (byte) Integer.parseInt(rgb.substring(2, 4), 16),
                (byte) Integer.parseInt(rgb.substring(4, 6), 16)
        };
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle subtitleBar;
        final CellStyle section;
        final CellStyle label;
        final CellStyle value;
        final CellStyle valueNumber;
        final CellStyle tableHeader;
        final CellStyle dataOdd;
        final CellStyle dataEven;
        final CellStyle dataCenterOdd;
        final CellStyle dataCenterEven;
        final CellStyle durationOdd;
        final CellStyle durationEven;
        final CellStyle linkOdd;
        final CellStyle linkEven;
        final CellStyle statusPass;
        final CellStyle statusFail;
        final CellStyle statusSkip;
        final CellStyle statusRun;
        final CellStyle statusActive;
        final CellStyle statusDefault;

        private Styles(CellStyle title, CellStyle subtitleBar, CellStyle section, CellStyle label,
                       CellStyle value, CellStyle valueNumber, CellStyle tableHeader,
                       CellStyle dataOdd, CellStyle dataEven, CellStyle dataCenterOdd, CellStyle dataCenterEven,
                       CellStyle durationOdd, CellStyle durationEven, CellStyle linkOdd, CellStyle linkEven,
                       CellStyle statusPass, CellStyle statusFail, CellStyle statusSkip,
                       CellStyle statusRun, CellStyle statusActive, CellStyle statusDefault) {
            this.title = title;
            this.subtitleBar = subtitleBar;
            this.section = section;
            this.label = label;
            this.value = value;
            this.valueNumber = valueNumber;
            this.tableHeader = tableHeader;
            this.dataOdd = dataOdd;
            this.dataEven = dataEven;
            this.dataCenterOdd = dataCenterOdd;
            this.dataCenterEven = dataCenterEven;
            this.durationOdd = durationOdd;
            this.durationEven = durationEven;
            this.linkOdd = linkOdd;
            this.linkEven = linkEven;
            this.statusPass = statusPass;
            this.statusFail = statusFail;
            this.statusSkip = statusSkip;
            this.statusRun = statusRun;
            this.statusActive = statusActive;
            this.statusDefault = statusDefault;
        }

        static Styles create(Workbook wb) {
            Font titleFont = wb.createFont();
            titleFont.setFontName("Calibri");
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 18);
            titleFont.setColor(IndexedColors.WHITE.getIndex());

            Font subtitleFont = wb.createFont();
            subtitleFont.setFontName("Calibri");
            subtitleFont.setFontHeightInPoints((short) 11);
            subtitleFont.setColor(IndexedColors.WHITE.getIndex());

            Font sectionFont = wb.createFont();
            sectionFont.setFontName("Calibri");
            sectionFont.setBold(true);
            sectionFont.setFontHeightInPoints((short) 12);
            sectionFont.setColor(IndexedColors.DARK_BLUE.getIndex());

            Font labelFont = wb.createFont();
            labelFont.setFontName("Calibri");
            labelFont.setBold(true);
            labelFont.setFontHeightInPoints((short) 11);

            Font bodyFont = wb.createFont();
            bodyFont.setFontName("Calibri");
            bodyFont.setFontHeightInPoints((short) 11);

            Font headerFont = wb.createFont();
            headerFont.setFontName("Calibri");
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            XSSFFont linkFont = (XSSFFont) wb.createFont();
            linkFont.setFontName("Calibri");
            linkFont.setFontHeightInPoints((short) 11);
            linkFont.setUnderline(Font.U_SINGLE);
            linkFont.setColor(new XSSFColor(LINK_FG, new DefaultIndexedColorMap()));

            CellStyle title = solid(wb, NAVY);
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.LEFT);
            title.setVerticalAlignment(VerticalAlignment.CENTER);
            title.setIndention((short) 1);

            CellStyle subtitleBar = solid(wb, NAVY_SOFT);
            subtitleBar.setFont(subtitleFont);
            subtitleBar.setVerticalAlignment(VerticalAlignment.CENTER);
            subtitleBar.setIndention((short) 1);

            CellStyle section = wb.createCellStyle();
            section.setFont(sectionFont);
            section.setVerticalAlignment(VerticalAlignment.CENTER);

            CellStyle label = boxed(wb, labelFont, LABEL_BG, false);
            CellStyle value = boxed(wb, bodyFont, null, false);
            CellStyle valueNumber = boxed(wb, bodyFont, null, true);

            CellStyle tableHeader = solid(wb, HEADER_BG);
            tableHeader.setFont(headerFont);
            tableHeader.setAlignment(HorizontalAlignment.CENTER);
            tableHeader.setVerticalAlignment(VerticalAlignment.CENTER);
            tableHeader.setWrapText(true);
            fullBorder(tableHeader);

            CellStyle dataOdd = body(wb, bodyFont, null, false);
            CellStyle dataEven = body(wb, bodyFont, ZEBRA, false);
            CellStyle dataCenterOdd = body(wb, bodyFont, null, true);
            CellStyle dataCenterEven = body(wb, bodyFont, ZEBRA, true);

            CellStyle durationOdd = body(wb, bodyFont, null, true);
            durationOdd.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            CellStyle durationEven = body(wb, bodyFont, ZEBRA, true);
            durationEven.setDataFormat(wb.createDataFormat().getFormat("#,##0"));

            CellStyle linkOdd = body(wb, linkFont, null, false);
            CellStyle linkEven = body(wb, linkFont, ZEBRA, false);

            return new Styles(
                    title, subtitleBar, section, label, value, valueNumber, tableHeader,
                    dataOdd, dataEven, dataCenterOdd, dataCenterEven,
                    durationOdd, durationEven, linkOdd, linkEven,
                    status(wb, PASS_BG, PASS_FG),
                    status(wb, FAIL_BG, FAIL_FG),
                    status(wb, SKIP_BG, SKIP_FG),
                    status(wb, RUN_BG, RUN_FG),
                    status(wb, ACTIVE_BG, ACTIVE_FG),
                    body(wb, bodyFont, null, true)
            );
        }

        CellStyle data(boolean zebra) {
            return zebra ? dataEven : dataOdd;
        }

        CellStyle dataCenter(boolean zebra) {
            return zebra ? dataCenterEven : dataCenterOdd;
        }

        CellStyle duration(boolean zebra) {
            return zebra ? durationEven : durationOdd;
        }

        CellStyle link(boolean zebra) {
            return zebra ? linkEven : linkOdd;
        }

        CellStyle statusStyle(String raw) {
            String key = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            return switch (key) {
                case "PASSED", "PASS", "OK" -> statusPass;
                case "FAILED", "FAIL", "ERROR" -> statusFail;
                case "SKIPPED", "SKIP" -> statusSkip;
                case "RUNNING", "RUN" -> statusRun;
                case "ACTIVE", "ENABLED" -> statusActive;
                default -> statusDefault;
            };
        }

        private static CellStyle solid(Workbook wb, byte[] rgb) {
            XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
            style.setFillForegroundColor(new XSSFColor(rgb, new DefaultIndexedColorMap()));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }

        private static CellStyle boxed(Workbook wb, Font font, byte[] bg, boolean center) {
            XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
            style.setFont(font);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            if (center) {
                style.setAlignment(HorizontalAlignment.CENTER);
            }
            if (bg != null) {
                style.setFillForegroundColor(new XSSFColor(bg, new DefaultIndexedColorMap()));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            fullBorder(style);
            return style;
        }

        private static CellStyle body(Workbook wb, Font font, byte[] bg, boolean center) {
            XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
            style.setFont(font);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            if (center) {
                style.setAlignment(HorizontalAlignment.CENTER);
            }
            if (bg != null) {
                style.setFillForegroundColor(new XSSFColor(bg, new DefaultIndexedColorMap()));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            fullBorder(style);
            return style;
        }

        private static CellStyle status(Workbook wb, byte[] bg, byte[] fg) {
            XSSFFont font = (XSSFFont) wb.createFont();
            font.setFontName("Calibri");
            font.setBold(true);
            font.setFontHeightInPoints((short) 11);
            font.setColor(new XSSFColor(fg, new DefaultIndexedColorMap()));
            XSSFCellStyle style = (XSSFCellStyle) wb.createCellStyle();
            style.setFont(font);
            style.setFillForegroundColor(new XSSFColor(bg, new DefaultIndexedColorMap()));
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            fullBorder(style);
            return style;
        }

        private static void fullBorder(CellStyle style) {
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
            style.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
            style.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
            style.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        }
    }
}
