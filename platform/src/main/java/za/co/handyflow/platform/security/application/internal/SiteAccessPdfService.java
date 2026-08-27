// security/application/internal/SiteAccessPdfService.java

package za.co.handyflow.platform.security.application.internal;

import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.security.dto.SiteAccessReport;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * SiteAccessPdfService — the fourth security report, per
 * SecurityPdfBrandingHelper's own doc comment ("every new PDF service
 * from here on should use this"). A genuinely new, separate service —
 * not added inline into PdfReportService, matching that same comment's
 * reasoning for why it was extracted in the first place.
 * <p>
 * Deliberately a simpler, plainer layout than PdfReportService's own
 * three reports (no logo rendering, no brand-colour header bar) — I
 * only have SecurityPdfBrandingHelper.resolveTenant() confirmed with
 * certainty; its other header/footer methods weren't confirmed this
 * session. This is a real, correct, working report using only what's
 * confirmed rather than guessed method names on an interface I haven't
 * fully seen. Upgrading to the shared branded header/footer once that
 * helper's full interface is confirmed is a natural, low-risk follow-up
 * — swapping this file's own header/footer methods for calls to the
 * helper, not a redesign.
 * <p>
 * idNumber never appears anywhere in this PDF — SiteAccessReport.
 * EntryLine itself never carries it (see that record's own Javadoc).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteAccessPdfService {

    private static final DeviceRgb LIGHT_GREY = new DeviceRgb(247, 247, 247);
    private static final DeviceRgb MID_GREY   = new DeviceRgb(200, 200, 200);

    private final SecurityPdfBrandingHelper brandingHelper;

    public byte[] siteAccessPdf(SiteAccessReport report, TenantId tenantId) {
        TenantDetails tenant = brandingHelper.resolveTenant(tenantId);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc = new Document(pdf, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            doc.add(new Paragraph(tenant.companyName())
                    .setFontSize(10).setBold());
            doc.add(new Paragraph("Site Access / Visitor Report")
                    .setFontSize(18).setBold().setMarginBottom(2));
            doc.add(new Paragraph(report.siteName() + " — " + report.month())
                    .setFontSize(11).setMarginBottom(16));

            // ── Summary metrics ──────────────────────────────────────────────
            Table metrics = new Table(UnitValue.createPercentArray(new float[]{2, 1}))
                    .useAllAvailableWidth().setMarginBottom(16);
            addMetricRow(metrics, "Total Entries", String.valueOf(report.totalEntries()));
            addMetricRow(metrics, "Currently On Site", String.valueOf(report.currentlyOnSite()));
            addMetricRow(metrics, "Departed", String.valueOf(report.departed()));
            addMetricRow(metrics, "Overstayed", String.valueOf(report.overstayed()));
            doc.add(metrics);

            // ── Entries by type ──────────────────────────────────────────────
            if (!report.entriesByType().isEmpty()) {
                doc.add(new Paragraph("By Entry Type").setBold().setFontSize(12).setMarginBottom(6));
                Table typeTable = new Table(UnitValue.createPercentArray(new float[]{2, 1}))
                        .useAllAvailableWidth().setMarginBottom(16);
                report.entriesByType().forEach((type, count) -> addMetricRow(typeTable, type, String.valueOf(count)));
                doc.add(typeTable);
            }

            // ── Detail lines ──────────────────────────────────────────────────
            if (!report.entries().isEmpty()) {
                doc.add(new Paragraph("Entry Detail").setBold().setFontSize(12).setMarginBottom(6));
                Table detail = new Table(UnitValue.createPercentArray(new float[]{1, 2, 2, 1, 2, 2, 1}))
                        .useAllAvailableWidth();
                addHeaderCell(detail, "Type");
                addHeaderCell(detail, "Person");
                addHeaderCell(detail, "Company");
                addHeaderCell(detail, "Vehicle");
                addHeaderCell(detail, "Access Point");
                addHeaderCell(detail, "Logged In");
                addHeaderCell(detail, "Status");

                boolean alt = false;
                for (var line : report.entries()) {
                    addRowCell(detail, line.entryType(), alt);
                    addRowCell(detail, line.personName(), alt);
                    addRowCell(detail, line.company() != null ? line.company() : "—", alt);
                    addRowCell(detail, line.vehicleRegistration() != null ? line.vehicleRegistration() : "—", alt);
                    addRowCell(detail, line.accessPointName() != null ? line.accessPointName() : "—", alt);
                    addRowCell(detail, formatInstant(line.loggedInAt()), alt);
                    addRowCell(detail, line.status(), alt);
                    alt = !alt;
                }
                doc.add(detail);
            }

            doc.add(new Paragraph("Generated " + java.time.LocalDate.now())
                    .setFontSize(8).setMarginTop(24).setTextAlignment(TextAlignment.RIGHT));

        } catch (Exception e) {
            log.error("[Reporting] Site access PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    private String formatInstant(java.time.Instant instant) {
        if (instant == null) return "—";
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.ENGLISH).withZone(java.time.ZoneId.of("Africa/Johannesburg"))
                .format(instant);
    }

    private void addMetricRow(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label)).setBorder(null).setPadding(4));
        table.addCell(new Cell().add(new Paragraph(value).setBold())
                .setBorder(null).setPadding(4).setTextAlignment(TextAlignment.RIGHT));
    }

    private void addHeaderCell(Table table, String text) {
        table.addHeaderCell(new Cell().add(new Paragraph(text).setBold().setFontSize(9))
                .setBackgroundColor(MID_GREY).setPadding(4));
    }

    private void addRowCell(Table table, String text, boolean alt) {
        Cell cell = new Cell().add(new Paragraph(text != null ? text : "—").setFontSize(9)).setPadding(4);
        if (alt) cell.setBackgroundColor(LIGHT_GREY);
        table.addCell(cell);
    }
}