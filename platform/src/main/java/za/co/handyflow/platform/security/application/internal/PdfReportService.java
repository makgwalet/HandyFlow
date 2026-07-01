// security/application/internal/PdfReportService.java

package za.co.handyflow.platform.security.application.internal;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.security.dto.*;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * PdfReportService — renders the three security reports as PDF using iText 7.
 *
 * WHY not a template engine (Thymeleaf, Jasper)?
 * iText 7 is already in the project (pom.xml) for contracting module PDFs.
 * Adding a second templating dependency for a few report PDFs would bloat
 * the build unnecessarily. iText's layout API is verbose but predictable —
 * the output is the same across JVM versions, which matters for a compliance
 * artifact that might be stored/compared over years.
 *
 * Each report follows the same layout:
 *   Header bar (site/guard name + month)
 *   Key metrics table (2-column)
 *   Detail breakdown table (where applicable)
 *   Footer with generation timestamp
 *
 * The HandyFlow brand colour (#2B6CB0, a blue used in the frontend) is used
 * for header bars and table headers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfReportService {

    // HandyFlow brand blue — matches the frontend primary colour
    private static final DeviceRgb BRAND_BLUE   = new DeviceRgb(43, 108, 176);
    private static final DeviceRgb LIGHT_GREY   = new DeviceRgb(247, 247, 247);
    private static final DeviceRgb MID_GREY     = new DeviceRgb(200, 200, 200);

    // ── Site Coverage PDF ──────────────────────────────────────────────────────

    public byte[] siteCoveragePdf(SiteCoverageReport report) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc  = new Document(pdf, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            addReportHeader(doc, "Site Coverage Report",
                    report.siteName(), report.month());

            addMetricsTable(doc, new String[][]{
                    {"Total Shifts", String.valueOf(report.totalShifts())},
                    {"Completed",    report.completedShifts() + " (" + report.shiftCompletionRatePct() + "%)"},
                    {"Missed",       String.valueOf(report.missedShifts())},
                    {"Cancelled",    String.valueOf(report.cancelledShifts())},
                    {"Guard Hours",  report.totalGuardHours() + " hrs"},
                    {"Patrol Rounds Expected",  String.valueOf(report.patrolRoundsExpected())},
                    {"Patrol Rounds Completed", String.valueOf(report.patrolRoundsCompleted())},
                    {"Patrol Rounds Missed",    String.valueOf(report.patrolRoundsMissed())},
                    {"Checkpoint Scans",        String.valueOf(report.checkpointScans())},
                    {"Total Incidents",         String.valueOf(report.totalIncidents())},
            });

            if (!report.incidentsBySeverity().isEmpty()) {
                addSectionHeader(doc, "Incidents by Severity");
                addIncidentSeverityTable(doc, report.incidentsBySeverity());
            }

            addFooter(doc);
        } catch (Exception e) {
            log.error("[Reporting] Site coverage PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    // ── Guard Attendance PDF ───────────────────────────────────────────────────

    public byte[] guardAttendancePdf(GuardAttendanceReport report) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc  = new Document(pdf, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            addReportHeader(doc, "Guard Attendance Report",
                    report.guardName(), report.month());

            addMetricsTable(doc, new String[][]{
                    {"Total Shifts",    String.valueOf(report.totalShifts())},
                    {"Completed",       report.completedShifts() + " (" + report.attendanceRatePct() + "%)"},
                    {"Missed",          String.valueOf(report.missedShifts())},
                    {"Cancelled",       String.valueOf(report.cancelledShifts())},
                    {"Hours Worked",    report.totalHoursWorked() + " hrs"},
                    {"Checkpoint Scans", String.valueOf(report.checkpointScans())},
                    {"Incidents Logged", String.valueOf(report.incidentsLogged())},
            });

            if (!report.siteBreakdown().isEmpty()) {
                addSectionHeader(doc, "Breakdown by Site");
                Table table = new Table(UnitValue.createPercentArray(new float[]{3, 1, 1, 1, 2}))
                        .useAllAvailableWidth();
                addTableHeader(table, "Site", "Total", "Done", "Missed", "Hours");
                boolean alt = false;
                for (var row : report.siteBreakdown()) {
                    addTableRow(table, alt,
                            row.siteName(),
                            String.valueOf(row.totalShifts()),
                            String.valueOf(row.completedShifts()),
                            String.valueOf(row.missedShifts()),
                            row.hoursWorked() + " hrs");
                    alt = !alt;
                }
                doc.add(table);
            }

            addFooter(doc);
        } catch (Exception e) {
            log.error("[Reporting] Guard attendance PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    // ── Monthly Summary PDF ────────────────────────────────────────────────────

    public byte[] monthlySummaryPdf(MonthlySummaryReport report) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc  = new Document(pdf, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            addReportHeader(doc, "Monthly Security Summary", "All Sites", report.month());

            addMetricsTable(doc, new String[][]{
                    {"Total Shifts",       String.valueOf(report.totalShifts())},
                    {"Completed",          report.completedShifts() + " (" + report.overallCompletionRatePct() + "%)"},
                    {"Missed",             String.valueOf(report.missedShifts())},
                    {"Total Guard Hours",  report.totalGuardHours() + " hrs"},
                    {"Active Guards",      String.valueOf(report.activeGuards())},
                    {"Total Incidents",    String.valueOf(report.totalIncidents())},
            });

            if (!report.incidentsBySeverity().isEmpty()) {
                addSectionHeader(doc, "Incidents by Severity");
                addIncidentSeverityTable(doc, report.incidentsBySeverity());
            }

            if (!report.siteSummaries().isEmpty()) {
                addSectionHeader(doc, "Site-by-Site Coverage");
                Table table = new Table(UnitValue.createPercentArray(new float[]{3, 1, 1, 1, 2, 1}))
                        .useAllAvailableWidth();
                addTableHeader(table, "Site", "Shifts", "Done", "Missed", "Hours", "Incidents");
                boolean alt = false;
                for (var row : report.siteSummaries()) {
                    addTableRow(table, alt,
                            row.siteName(),
                            String.valueOf(row.totalShifts()),
                            String.valueOf(row.completedShifts()),
                            String.valueOf(row.missedShifts()),
                            row.guardHours() + " hrs",
                            String.valueOf(row.incidents()));
                    alt = !alt;
                }
                doc.add(table);
            }

            addFooter(doc);
        } catch (Exception e) {
            log.error("[Reporting] Monthly summary PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    // ── Shared layout helpers ──────────────────────────────────────────────────

    private void addReportHeader(Document doc, String title, String subject, String period) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
                .useAllAvailableWidth()
                .setBackgroundColor(BRAND_BLUE)
                .setMarginBottom(16);

        Cell titleCell = new Cell(1, 1)
                .add(new Paragraph("HandyFlow Security").setFontSize(8)
                        .setFontColor(ColorConstants.WHITE))
                .add(new Paragraph(title).setFontSize(16).setBold()
                        .setFontColor(ColorConstants.WHITE))
                .add(new Paragraph(subject).setFontSize(11)
                        .setFontColor(ColorConstants.WHITE))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(12);

        Cell periodCell = new Cell(1, 1)
                .add(new Paragraph(period).setFontSize(14).setBold()
                        .setFontColor(ColorConstants.WHITE)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(12);

        header.addCell(titleCell);
        header.addCell(periodCell);
        doc.add(header);
    }

    private void addMetricsTable(Document doc, String[][] rows) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{2, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(16);

        boolean alt = false;
        for (String[] row : rows) {
            Cell label = new Cell().add(new Paragraph(row[0]).setFontSize(10))
                    .setBackgroundColor(alt ? LIGHT_GREY : ColorConstants.WHITE)
                    .setBorder(new SolidBorder(MID_GREY, 0.5f))
                    .setPadding(6);
            Cell value = new Cell().add(new Paragraph(row[1]).setFontSize(10).setBold())
                    .setBackgroundColor(alt ? LIGHT_GREY : ColorConstants.WHITE)
                    .setBorder(new SolidBorder(MID_GREY, 0.5f))
                    .setPadding(6)
                    .setTextAlignment(TextAlignment.RIGHT);
            table.addCell(label);
            table.addCell(value);
            alt = !alt;
        }
        doc.add(table);
    }

    private void addIncidentSeverityTable(Document doc, Map<String, Long> bySeverity) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{2, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(16);

        addTableHeader(table, "Severity", "Count");
        boolean alt = false;
        for (var entry : bySeverity.entrySet()) {
            addTableRow(table, alt, entry.getKey(), String.valueOf(entry.getValue()));
            alt = !alt;
        }
        doc.add(table);
    }

    private void addSectionHeader(Document doc, String text) {
        doc.add(new Paragraph(text)
                .setFontSize(11).setBold()
                .setFontColor(BRAND_BLUE)
                .setMarginTop(12)
                .setMarginBottom(4));
    }

    private void addTableHeader(Table table, String... headers) {
        for (String h : headers) {
            table.addCell(new Cell()
                    .add(new Paragraph(h).setFontSize(9).setBold()
                            .setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(BRAND_BLUE)
                    .setBorder(new SolidBorder(BRAND_BLUE, 0.5f))
                    .setPadding(5));
        }
    }

    private void addTableRow(Table table, boolean alt, String... values) {
        for (String v : values) {
            table.addCell(new Cell()
                    .add(new Paragraph(v).setFontSize(9))
                    .setBackgroundColor(alt ? LIGHT_GREY : ColorConstants.WHITE)
                    .setBorder(new SolidBorder(MID_GREY, 0.5f))
                    .setPadding(5));
        }
    }

    private void addFooter(Document doc) {
        doc.add(new Paragraph(
                "Generated by HandyFlow Security Platform — " +
                        java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")))
                .setFontSize(7)
                .setFontColor(new DeviceRgb(120, 120, 120))
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginTop(20));
    }
}