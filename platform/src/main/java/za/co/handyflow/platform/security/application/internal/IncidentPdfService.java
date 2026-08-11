// security/application/internal/IncidentPdfService.java

package za.co.handyflow.platform.security.application.internal;

import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.security.dto.IncidentResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * IncidentPdfService — single-incident PDF report (audit gap: "no incident
 * report PDF... despite incidents being the most legally/insurance-sensitive
 * record type in the module"). Suitable for SAPS case files, insurance
 * claims, or client correspondence.
 *
 * Deliberately a per-incident document, not a list/date-range report —
 * the use cases named in the audit (case files, insurance claims) all
 * revolve around a single incident, not a batch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentPdfService {

    private static final DeviceRgb BRAND_RED   = new DeviceRgb(185, 28, 28); // distinct from the blue security reports — incidents read as urgent
    private static final DeviceRgb LIGHT_GREY  = new DeviceRgb(247, 247, 247);
    private static final DeviceRgb MID_GREY    = new DeviceRgb(200, 200, 200);

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.of("Africa/Johannesburg"));

    private final SecurityPdfBrandingHelper brandingHelper;

    public byte[] incidentPdf(IncidentResponse incident, TenantId tenantId) {
        TenantDetails tenant = brandingHelper.resolveTenant(tenantId);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc  = new Document(pdf, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            brandingHelper.addBrandedHeader(doc, "Incident Report",
                    incident.title(), "Ref: " + incident.id().toString().substring(0, 8).toUpperCase(),
                    tenant, BRAND_RED);

            addDetailsTable(doc, incident);
            addDescriptionSection(doc, incident);

            brandingHelper.addBrandedFooter(doc, tenant);
        } catch (Exception e) {
            log.error("[Reporting] Incident PDF generation failed id={}: {}", incident.id(), e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    private void addDetailsTable(Document doc, IncidentResponse i) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(16);

        addRow(table, "Site", i.siteName() != null ? i.siteName() : "—");
        addRow(table, "Guard", i.guardName() != null ? i.guardName() : "—");
        addRow(table, "Type", i.type() != null ? i.type() : "GENERAL");
        addRow(table, "Severity", i.severity());
        addRow(table, "Status", i.status());
        addRow(table, "Reported", i.reportedAt() != null ? TS_FMT.format(i.reportedAt()) : "—");
        addRow(table, "Acknowledged", i.acknowledgedAt() != null ? TS_FMT.format(i.acknowledgedAt()) : "Not yet acknowledged");
        addRow(table, "Resolved", i.resolvedAt() != null ? TS_FMT.format(i.resolvedAt()) : "Not yet resolved");
        if (i.latitude() != null && i.longitude() != null) {
            addRow(table, "GPS Location", i.latitude() + ", " + i.longitude());
        }

        doc.add(table);
    }

    private void addRow(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label).setFontSize(10))
                .setBackgroundColor(LIGHT_GREY)
                .setBorder(new SolidBorder(MID_GREY, 0.5f))
                .setPadding(6));
        table.addCell(new Cell().add(new Paragraph(value).setFontSize(10).setBold())
                .setBackgroundColor(ColorConstants.WHITE)
                .setBorder(new SolidBorder(MID_GREY, 0.5f))
                .setPadding(6)
                .setTextAlignment(TextAlignment.RIGHT));
    }

    private void addDescriptionSection(Document doc, IncidentResponse i) {
        doc.add(new Paragraph("Description")
                .setFontSize(11).setBold()
                .setFontColor(BRAND_RED)
                .setMarginTop(8).setMarginBottom(4));
        doc.add(new Paragraph(i.description() != null && !i.description().isBlank()
                ? i.description() : "No description recorded.")
                .setFontSize(10)
                .setMarginBottom(16));
    }
}