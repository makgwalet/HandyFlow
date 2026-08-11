// security/application/internal/ArmouryPdfService.java

package za.co.handyflow.platform.security.application.internal;

import com.itextpdf.kernel.colors.Color;
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
import za.co.handyflow.platform.security.domain.model.ArmouryLog;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.dto.ArmouryResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ArmouryPdfService — exportable chain-of-custody PDF for a single firearm
 * (audit gap: "ArmouryLog's witnessed chain-of-custody is described as 'the
 * immutable audit trail' but there's no exportable document version, despite
 * this being exactly the kind of record a SAPS Firearms Control Act audit
 * would ask for").
 *
 * Renders the firearm's register details plus the full ISSUE/RETURN history
 * from ArmouryLog, including witness on every entry — the two-person
 * verification is the whole point of the record, so it's shown on every row,
 * not summarized away.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArmouryPdfService {

    private static final DeviceRgb BRAND_NAVY  = new DeviceRgb(27, 58, 107);
    private static final DeviceRgb LIGHT_GREY  = new DeviceRgb(247, 247, 247);
    private static final DeviceRgb MID_GREY    = new DeviceRgb(200, 200, 200);

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.of("Africa/Johannesburg"));

    private final SecurityPdfBrandingHelper brandingHelper;
    private final GuardRepository           guardRepository;

    public byte[] chainOfCustodyPdf(ArmouryResponse firearm, List<ArmouryLog> history, TenantId tenantId) {
        TenantDetails tenant = brandingHelper.resolveTenant(tenantId);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc  = new Document(pdf, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            brandingHelper.addBrandedHeader(doc, "Firearm Chain of Custody",
                    firearm.firearmSerial() + " — " + firearm.firearmType(),
                    "SAPS Lic: " + firearm.sapsLicenseNumber(), tenant, BRAND_NAVY);

            addRegisterDetails(doc, firearm);
            addHistoryTable(doc, history, tenantId);

            brandingHelper.addBrandedFooter(doc, tenant);
        } catch (Exception e) {
            log.error("[Reporting] Armoury chain-of-custody PDF generation failed serial={}: {}",
                    firearm.firearmSerial(), e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    private void addRegisterDetails(Document doc, ArmouryResponse f) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(16);

        addRow(table, "Make/Model", f.makeModel() != null ? f.makeModel() : "—");
        addRow(table, "SAPS License", f.sapsLicenseNumber());
        addRow(table, "License Expiry", f.licenseExpiry() != null ? f.licenseExpiry().toString() : "—");
        addRow(table, "Current Status", f.status());
        addRow(table, "Currently Assigned To", f.assignedGuardName() != null ? f.assignedGuardName() : "In armoury");
        addRow(table, "Last Serviced", f.lastServiceAt() != null ? f.lastServiceAt().toString() : "Never recorded");

        doc.add(table);
    }

    private void addHistoryTable(Document doc, List<ArmouryLog> history, TenantId tenantId) {
        doc.add(new Paragraph("Issue / Return History")
                .setFontSize(11).setBold()
                .setFontColor(BRAND_NAVY)
                .setMarginTop(8).setMarginBottom(4));

        if (history.isEmpty()) {
            doc.add(new Paragraph("No issue/return events recorded.").setFontSize(10));
            return;
        }

        Table table = new Table(UnitValue.createPercentArray(new float[]{2, 2, 2, 2, 3}))
                .useAllAvailableWidth();

        for (String h : new String[]{"Date/Time", "Action", "Guard", "Witness", "Condition Notes"}) {
            table.addCell(new Cell()
                    .add(new Paragraph(h).setFontSize(8).setBold().setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(BRAND_NAVY)
                    .setBorder(new SolidBorder(BRAND_NAVY, 0.5f))
                    .setPadding(5));
        }

        boolean alt = false;
        for (ArmouryLog log : history) {
            Color bg = alt ? LIGHT_GREY : ColorConstants.WHITE;
            table.addCell(cell(TS_FMT.format(log.getOccurredAt()), bg));
            table.addCell(cell(log.getAction().name(), bg));
            table.addCell(cell(guardName(tenantId, log.getGuardId()), bg));
            table.addCell(cell(guardName(tenantId, log.getWitnessedByGuardId()), bg));
            table.addCell(cell(log.getConditionNotes() != null ? log.getConditionNotes() : "—", bg));
            alt = !alt;
        }
        doc.add(table);
    }

    private Cell cell(String text, Color bg) {
        return new Cell()
                .add(new Paragraph(text).setFontSize(9))
                .setBackgroundColor(bg)
                .setBorder(new SolidBorder(MID_GREY, 0.5f))
                .setPadding(5);
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

    private String guardName(TenantId tenantId, java.util.UUID guardId) {
        if (guardId == null) return "—";
        return guardRepository.findActiveById(tenantId, guardId)
                .map(Guard::getFullName).orElse("Unknown");
    }
}