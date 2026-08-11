// security/application/internal/PdfReportService.java

package za.co.handyflow.platform.security.application.internal;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
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
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.security.dto.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.Map;

/**
 * PdfReportService — renders the three security reports as PDF using iText 7.
 *
 * CHANGE (V212): tenant logo branding, added the same way QuotePdfService/
 * InvoicePdfService already do it in the Invoicing module -- not a new
 * pattern, just this module catching up to an existing one. Each of the
 * three public methods now takes a TenantId and fetches TenantDetails via
 * TenantFacade (same call this module's own callers already make elsewhere),
 * replacing the hardcoded "HandyFlow Security" header text with the tenant's
 * actual company name + logo when available.
 *
 * WHY decodeLogoBytes() is copy-pasted from QuotePdfService rather than
 * calling a shared helper?
 * There isn't one yet -- QuotePdfService/InvoicePdfService each have their
 * own private copy of this exact method, so this follows the codebase's
 * current (imperfect) convention rather than inventing a shared
 * TenantBrandingService that doesn't exist. If a fourth PDF service ever
 * needs this, that's the point to actually extract it.
 *
 * CRITICAL: TenantService.uploadLogo() stores logoUrl as a
 * "data:<mime>;base64,<data>" URI, NOT a real HTTP(S) URL. Calling
 * `new URL(tenant.logoUrl())` directly throws MalformedURLException
 * ("unknown protocol: data") on every call -- this is a real, previously-hit
 * bug in the Invoicing module, documented in QuotePdfService's own comments.
 * decodeLogoBytes() below handles the data URI case first and only falls
 * back to treating it as a real URL if it isn't one.
 *
 * WHY a white box around the logo rather than dropping it straight onto the
 * BRAND_BLUE header bar?
 * Unlike Invoicing's light-gray header, this module's header background is
 * a solid navy brand color. A tenant's logo may not have been designed
 * against a dark background (transparent PNG with dark text, for instance)
 * and would become unreadable. A small white card behind the logo guarantees
 * contrast regardless of what the tenant uploaded, at the cost of a visible
 * white rectangle for tenants whose logo already assumes a dark background.
 * Acceptable trade-off for a first pass; revisit if it looks wrong in
 * practice against real tenant logos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfReportService {

    private static final DeviceRgb BRAND_BLUE   = new DeviceRgb(43, 108, 176);
    private static final DeviceRgb LIGHT_GREY   = new DeviceRgb(247, 247, 247);
    private static final DeviceRgb MID_GREY     = new DeviceRgb(200, 200, 200);

    private final TenantFacade tenantFacade;

    // ── Site Coverage PDF ──────────────────────────────────────────────────────

    public byte[] siteCoveragePdf(SiteCoverageReport report, TenantId tenantId) {
        TenantDetails tenant = resolveTenant(tenantId);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc  = new Document(pdf, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            addReportHeader(doc, "Site Coverage Report",
                    report.siteName(), report.month(), tenant);

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

            addFooter(doc, tenant);
        } catch (Exception e) {
            log.error("[Reporting] Site coverage PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    // ── Guard Attendance PDF ───────────────────────────────────────────────────

    public byte[] guardAttendancePdf(GuardAttendanceReport report, TenantId tenantId) {
        TenantDetails tenant = resolveTenant(tenantId);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc  = new Document(pdf, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            addReportHeader(doc, "Guard Attendance Report",
                    report.guardName(), report.month(), tenant);

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

            addFooter(doc, tenant);
        } catch (Exception e) {
            log.error("[Reporting] Guard attendance PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    // ── Monthly Summary PDF ────────────────────────────────────────────────────

    public byte[] monthlySummaryPdf(MonthlySummaryReport report, TenantId tenantId) {
        TenantDetails tenant = resolveTenant(tenantId);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc  = new Document(pdf, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            addReportHeader(doc, "Monthly Security Summary", "All Sites", report.month(), tenant);

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

            addFooter(doc, tenant);
        } catch (Exception e) {
            log.error("[Reporting] Monthly summary PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    // ── Tenant resolution + logo (V212) ───────────────────────────────────────

    private TenantDetails resolveTenant(TenantId tenantId) {
        return tenantFacade.findTenantDetails(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId.getValue().toString()));
    }

    /**
     * See class javadoc — logoUrl is a base64 data URI, not a real URL, for
     * every tenant that has ever uploaded a logo via TenantService.uploadLogo().
     * This must stay in sync with QuotePdfService.decodeLogoBytes() /
     * InvoicePdfService's copy of the same method.
     */
    private byte[] decodeLogoBytes(String logoUrl) throws Exception {
        if (logoUrl.startsWith("data:")) {
            int commaIdx = logoUrl.indexOf(',');
            if (commaIdx < 0) {
                throw new IllegalArgumentException("Malformed data URI — no comma separator found");
            }
            String base64Payload = logoUrl.substring(commaIdx + 1);
            return java.util.Base64.getDecoder().decode(base64Payload);
        }
        try (var in = new URL(logoUrl).openStream()) {
            return in.readAllBytes();
        }
    }

    /** Returns a scaled Image ready to add to the header, or null if no usable logo exists. */
    private Image tryLoadLogoImage(TenantDetails tenant) {
        if (tenant == null || tenant.logoUrl() == null || tenant.logoUrl().isBlank()) {
            return null;
        }
        try {
            byte[] imageBytes = decodeLogoBytes(tenant.logoUrl());
            return new Image(ImageDataFactory.create(imageBytes))
                    .setMaxHeight(36).setAutoScale(false);
        } catch (Exception ex) {
            log.warn("[Reporting] Could not load tenant logo tenant={}: {}",
                    tenant.slug(), ex.getMessage());
            return null;
        }
    }

    // ── Shared layout helpers ──────────────────────────────────────────────────

    private void addReportHeader(Document doc, String title, String subject, String period,
                                 TenantDetails tenant) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{3, 1}))
                .useAllAvailableWidth()
                .setBackgroundColor(BRAND_BLUE)
                .setMarginBottom(16);

        Cell titleCell = new Cell(1, 1)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(12);

        Image logo = tryLoadLogoImage(tenant);
        if (logo != null) {
            // White card behind the logo for contrast against the navy header —
            // see class javadoc for why this trade-off was made.
            Table logoCard = new Table(new float[]{1})
                    .setWidth(UnitValue.createPointValue(90))
                    .setBackgroundColor(ColorConstants.WHITE);
            logoCard.addCell(new Cell()
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setPadding(4)
                    .add(logo));
            titleCell.add(logoCard);
        }

        String companyName = (tenant != null && tenant.companyName() != null)
                ? tenant.companyName() : "HandyFlow Security";

        titleCell.add(new Paragraph(companyName).setFontSize(8)
                .setFontColor(ColorConstants.WHITE).setMarginTop(logo != null ? 6 : 0));
        titleCell.add(new Paragraph(title).setFontSize(16).setBold()
                .setFontColor(ColorConstants.WHITE));
        titleCell.add(new Paragraph(subject).setFontSize(11)
                .setFontColor(ColorConstants.WHITE));

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

    private void addFooter(Document doc, TenantDetails tenant) {
        String companyName = (tenant != null && tenant.companyName() != null)
                ? tenant.companyName() : "HandyFlow Security";
        doc.add(new Paragraph(
                "Generated by " + companyName + " via HandyFlow Security Platform — " +
                        java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")))
                .setFontSize(7)
                .setFontColor(new DeviceRgb(120, 120, 120))
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginTop(20));
    }
}