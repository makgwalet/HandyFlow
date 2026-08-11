// security/application/internal/CpCompliancePdfService.java

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
import za.co.handyflow.platform.security.domain.model.DeclinedPrincipal;
import za.co.handyflow.platform.security.domain.model.Principal;
import za.co.handyflow.platform.security.domain.model.PrincipalVetting;
import za.co.handyflow.platform.security.domain.repository.DeclinedPrincipalRepository;
import za.co.handyflow.platform.security.domain.repository.PrincipalRepository;
import za.co.handyflow.platform.security.domain.repository.PrincipalVettingRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

/**
 * CpCompliancePdfService — exportable Part 9.6 vetting compliance record for
 * a principal (audit gap: "no CP/vetting compliance PDF... no export path
 * for a compliance officer needing a paper record").
 *
 * DELIBERATELY EXCLUDES Principal.medicalNotes / Principal.knownThreats.
 * This is a vetting-compliance document ("was this person checked, what did
 * we find, did we decline"), not a full principal dossier -- pulling in the
 * encrypted operational fields would widen the exposure surface of a
 * document that's likely to be printed, emailed, or filed, for no benefit
 * to its actual purpose. Same reasoning as VettingService's notifyVettingHit()
 * keeping sensitive detail out of the notification body -- Part 9.3
 * confidentiality applies to every exportable artifact this module produces,
 * not just the live API responses.
 *
 * Similarly excludes DeclinedPrincipal.encryptedDetail (the sensitive
 * intelligence behind a declination) -- that field's own javadoc already
 * calls it "more restricted than even the principal record itself." Only
 * the declination's existence, date, and stated reason are shown.
 *
 * Loads directly from repositories rather than going through
 * CloseProtectionService/PrincipalResponse -- avoids depending on
 * PrincipalResponse's exact DTO shape (not available at the time this was
 * written) and keeps this service's confidentiality boundary self-contained.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CpCompliancePdfService {

    private static final DeviceRgb BRAND_PURPLE = new DeviceRgb(124, 58, 237); // matches CloseProtectionTab's accent
    private static final DeviceRgb LIGHT_GREY   = new DeviceRgb(247, 247, 247);
    private static final DeviceRgb MID_GREY     = new DeviceRgb(200, 200, 200);
    private static final DeviceRgb FLAG_RED_BG  = new DeviceRgb(254, 242, 242);
    private static final DeviceRgb FLAG_RED_TXT = new DeviceRgb(153, 27, 27);

    private final SecurityPdfBrandingHelper   brandingHelper;
    private final PrincipalRepository         principalRepository;
    private final PrincipalVettingRepository  vettingRepository;
    private final DeclinedPrincipalRepository declinedRepository;

    public byte[] vettingCompliancePdf(TenantId tenantId, UUID principalId) {
        Principal principal = principalRepository.findByTenantAndId(tenantId, principalId)
                .orElseThrow(() -> new ResourceNotFoundException("Principal", principalId.toString()));

        List<PrincipalVetting> history = vettingRepository.findByPrincipal(tenantId, principalId);
        DeclinedPrincipal declined = declinedRepository
                .findByTenantIdAndPrincipalId(tenantId, principalId).orElse(null);

        TenantDetails tenant = brandingHelper.resolveTenant(tenantId);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc  = new Document(pdf, PageSize.A4)) {

            doc.setMargins(36, 36, 36, 36);

            brandingHelper.addBrandedHeader(doc, "CP Vetting Compliance Record",
                    principal.getFullName() + " (" + principal.getAliasCodename() + ")",
                    "Status: " + principal.getVettingStatus(), tenant, BRAND_PURPLE);

            addSummaryTable(doc, principal);
            if (declined != null) {
                addDeclinedNotice(doc, declined);
            }
            addVettingHistoryTable(doc, history);

            brandingHelper.addBrandedFooter(doc, tenant);
        } catch (Exception e) {
            log.error("[Reporting] CP compliance PDF generation failed principalId={}: {}",
                    principalId, e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    private void addSummaryTable(Document doc, Principal p) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth()
                .setMarginBottom(16);

        addRow(table, "Alias / Codename", p.getAliasCodename());
        addRow(table, "Threat Level", p.getThreatLevel().name());
        addRow(table, "Vetting Status", p.getVettingStatus());
        addRow(table, "Record Active", p.isActive() ? "Yes" : "No (deactivated)");

        doc.add(table);
    }

    private void addDeclinedNotice(Document doc, DeclinedPrincipal d) {
        Table box = new Table(new float[]{1})
                .useAllAvailableWidth()
                .setMarginBottom(16)
                .setBackgroundColor(FLAG_RED_BG);
        box.addCell(new Cell()
                .setBorder(new SolidBorder(FLAG_RED_TXT, 1))
                .setPadding(10)
                .add(new Paragraph("DECLINED ENGAGEMENT ON RECORD")
                        .setFontSize(10).setBold().setFontColor(FLAG_RED_TXT).setMarginBottom(4))
                .add(new Paragraph("Declined: " + d.getDeclinedAt())
                        .setFontSize(9).setFontColor(FLAG_RED_TXT))
                .add(new Paragraph("Reason: " + d.getReason())
                        .setFontSize(9).setFontColor(FLAG_RED_TXT)));
        doc.add(box);
    }

    private void addVettingHistoryTable(Document doc, List<PrincipalVetting> history) {
        doc.add(new Paragraph("Vetting Check History")
                .setFontSize(11).setBold()
                .setFontColor(BRAND_PURPLE)
                .setMarginTop(8).setMarginBottom(4));

        if (history.isEmpty()) {
            doc.add(new Paragraph("No vetting checks recorded.").setFontSize(10));
            return;
        }

        Table table = new Table(UnitValue.createPercentArray(new float[]{2, 1, 2, 2, 2}))
                .useAllAvailableWidth();

        for (String h : new String[]{"Type", "Result", "Conducted By", "Conducted At", "Next Review"}) {
            table.addCell(new Cell()
                    .add(new Paragraph(h).setFontSize(8).setBold().setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(BRAND_PURPLE)
                    .setBorder(new SolidBorder(BRAND_PURPLE, 0.5f))
                    .setPadding(5));
        }

        boolean alt = false;
        for (PrincipalVetting v : history) {
            Color bg = alt ? LIGHT_GREY : ColorConstants.WHITE;
            table.addCell(cell(v.getVettingType().name(), bg));
            table.addCell(cell(v.getResult().name(), bg,
                    v.getResult() == PrincipalVetting.VettingResult.HIT ? FLAG_RED_TXT : null));
            table.addCell(cell(v.getConductedBy() != null ? v.getConductedBy() : "—", bg));
            table.addCell(cell(v.getConductedAt() != null ? v.getConductedAt().toString() : "—", bg));
            table.addCell(cell(v.getNextReviewAt() != null ? v.getNextReviewAt().toString() : "—", bg));
            alt = !alt;
        }
        doc.add(table);
    }

    private Cell cell(String text, Color bg) {
        return cell(text, bg, null);
    }

    private Cell cell(String text, Color bg, DeviceRgb textColor) {
        Paragraph p = new Paragraph(text).setFontSize(9);
        if (textColor != null) p.setBold().setFontColor(textColor);
        return new Cell()
                .add(p)
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
}