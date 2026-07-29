package za.co.handyflow.platform.clinic.application.internal;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.IBlockElement;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.clinic.domain.model.ClinicClaim;
import za.co.handyflow.platform.clinic.domain.model.ClinicClaimLine;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.model.ClinicPractitioner;
import za.co.handyflow.platform.clinic.domain.repository.ClinicClaimRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPractitionerRepository;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * FIX: "no claim submission form/EDI record" gap — a printable record of
 * exactly what was submitted to the scheme, useful for dispute resolution
 * when a scheme's own portal disagrees with what the practice actually
 * sent. Only meaningful for claims that have actually been submitted —
 * "what was submitted" has no answer for a DRAFT claim.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicClaimSubmissionPdfService {

    private final ClinicClaimRepository claimRepo;
    private final ClinicPatientRepository patientRepo;
    private final ClinicPractitionerRepository practitionerRepo;
    private final TenantFacade tenantFacade;

    private static final DeviceRgb BRAND_DARK = new DeviceRgb(15, 76, 92);
    private static final DeviceRgb TEAL       = new DeviceRgb(13, 148, 136);
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(120, 130, 140);
    private static final DeviceRgb WHITE      = new DeviceRgb(255, 255, 255);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    public byte[] generate(TenantId tenantId, UUID claimId) {
        ClinicClaim claim = claimRepo.findActiveById(tenantId, claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId.toString()));

        if ("DRAFT".equals(claim.getStatus())) {
            throw new IllegalStateException(
                    "This claim hasn't been submitted yet — there's nothing to record");
        }

        ClinicPatient patient = patientRepo.findActiveById(tenantId, claim.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", claim.getPatientId().toString()));
        ClinicPractitioner practitioner = claim.getPractitionerId() != null
                ? practitionerRepo.findActiveById(tenantId, claim.getPractitionerId()).orElse(null)
                : null;
        String companyName = tenantFacade.findTenantDetails(tenantId).map(t -> t.companyName()).orElse("");
        String logoUrl = tenantFacade.findTenantDetails(tenantId).map(t -> t.logoUrl()).orElse(null);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(baos));
            Document document = new Document(pdfDoc, PageSize.A4);
            document.setMargins(36, 40, 36, 40);

            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            addHeader(document, companyName, logoUrl, bold, regular);
            addSubmissionDetails(document, claim, patient, practitioner, bold, regular);
            addLinesTable(document, claim, bold, regular);
            addFooter(document, regular);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate claim submission PDF for claim={}: {}", claimId, e.getMessage(), e);
            throw new RuntimeException("Claim submission PDF generation failed", e);
        }
    }

    private void addHeader(Document doc, String companyName, String logoUrl, PdfFont bold, PdfFont regular) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
        // Same logo-loading pattern already confirmed working in
        // CreditNotePdfService/StatementOfAccountPdfService — logoUrl is a
        // data: URI or a fetchable URL; falls back to text-only company
        // name if absent or unloadable.
        Table left = new Table(1).useAllAvailableWidth();
        boolean logoAdded = false;
        if (logoUrl != null && !logoUrl.isBlank()) {
            try {
                byte[] imageBytes = decodeLogoBytes(logoUrl);
                Image logo = new Image(com.itextpdf.io.image.ImageDataFactory.create(imageBytes))
                        .setMaxHeight(50).setAutoScale(false);
                left.addCell(cellOf(logo));
                logoAdded = true;
            } catch (Exception ex) {
                log.warn("Could not load logo for claim submission record: {}", ex.getMessage());
            }
        }
        if (!logoAdded) {
            left.addCell(cellOf(new Paragraph(companyName).setFont(bold).setFontSize(14)));
        } else {
            left.addCell(cellOf(new Paragraph(companyName).setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginTop(4)));
        }
        header.addCell(cellOf(left));
        header.addCell(cellOf(new Paragraph("CLAIM SUBMISSION RECORD").setFont(bold).setFontSize(16)
                .setFontColor(TEAL).setTextAlignment(TextAlignment.RIGHT)));
        doc.add(header);
        doc.add(new LineSeparator(new SolidLine(1f)).setMarginTop(8).setMarginBottom(14).setFontColor(TEAL));
        doc.add(new Paragraph("This is a record of what was submitted to the medical aid scheme, generated from the practice's own claim data — not a copy of the scheme's own acknowledgement.")
                .setFont(regular).setFontSize(9).setFontColor(TEXT_MUTED).setMarginBottom(14));
    }

    /** Same fix as InvoicePdfService/QuotePdfService/ReceiptPdfService/CreditNotePdfService — logoUrl is a data: URI. */
    private byte[] decodeLogoBytes(String logoUrl) throws Exception {
        if (logoUrl.startsWith("data:")) {
            int commaIdx = logoUrl.indexOf(',');
            if (commaIdx < 0) throw new IllegalArgumentException("Malformed data URI");
            return java.util.Base64.getDecoder().decode(logoUrl.substring(commaIdx + 1));
        }
        try (var in = new java.net.URL(logoUrl).openStream()) {
            return in.readAllBytes();
        }
    }

    private void addSubmissionDetails(Document doc, ClinicClaim claim, ClinicPatient patient,
                                      ClinicPractitioner practitioner, PdfFont bold, PdfFont regular) {
        Table info = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth().setMarginBottom(16);
        info.addCell(cellOf(new Paragraph("Patient: " + patient.getFullName()).setFont(regular).setFontSize(10)));
        info.addCell(cellOf(new Paragraph("Submitted: " + (claim.getSubmittedAt() != null
                ? claim.getSubmittedAt().atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT) : "—"))
                .setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
        info.addCell(cellOf(new Paragraph("Scheme: " + (claim.getSchemeName() != null ? claim.getSchemeName() : "—"))
                .setFont(regular).setFontSize(10)));
        info.addCell(cellOf(new Paragraph("Reference: " + (claim.getReferenceNumber() != null ? claim.getReferenceNumber() : "—"))
                .setFont(bold).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
        info.addCell(cellOf(new Paragraph("Member number: " + (claim.getMemberNumber() != null ? claim.getMemberNumber() : "—"))
                .setFont(regular).setFontSize(10)));
        info.addCell(cellOf(new Paragraph("Status: " + claim.getStatus())
                .setFont(bold).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
        if (claim.getDependentCode() != null) {
            info.addCell(cellOf(new Paragraph("Dependent code: " + claim.getDependentCode()).setFont(regular).setFontSize(10)));
            info.addCell(cellOf(new Paragraph("")));
        }
        if (practitioner != null) {
            info.addCell(cellOf(new Paragraph("Practitioner: " + drName(practitioner.getFullName())).setFont(regular).setFontSize(10)));
            if (practitioner.getHpcsaNumber() != null) {
                info.addCell(cellOf(new Paragraph("HPCSA: " + practitioner.getHpcsaNumber())
                        .setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
            }
        }
        doc.add(info);

        if (claim.getRejectionReason() != null && !claim.getRejectionReason().isBlank()) {
            doc.add(new Paragraph("Rejection reason: " + claim.getRejectionReason())
                    .setFont(regular).setFontSize(10).setFontColor(new DeviceRgb(180, 60, 50)).setMarginBottom(12));
        }
    }

    private void addLinesTable(Document doc, ClinicClaim claim, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("CLAIM LINES").setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginBottom(6));
        Table table = new Table(UnitValue.createPercentArray(new float[]{1.2f, 1, 1, 3, 0.8f, 1, 1.2f})).useAllAvailableWidth();
        for (String h : new String[]{"Type", "Tariff", "ICD-10", "Description", "Qty", "Unit price", "Gross"}) {
            table.addHeaderCell(headerCell(h, bold));
        }
        List<ClinicClaimLine> lines = claim.getLines();
        for (ClinicClaimLine l : lines) {
            String code = l.getTariffCode() != null ? l.getTariffCode() : (l.getNappiCode() != null ? l.getNappiCode() : "—");
            table.addCell(cellOf(new Paragraph(l.getLineType() != null ? l.getLineType() : "—").setFont(regular).setFontSize(8)));
            table.addCell(cellOf(new Paragraph(code).setFont(regular).setFontSize(8)));
            table.addCell(cellOf(new Paragraph(l.getIcd10Code() != null ? l.getIcd10Code() : "—").setFont(regular).setFontSize(8)));
            table.addCell(cellOf(new Paragraph(l.getDescription() != null ? l.getDescription() : "").setFont(regular).setFontSize(8)));
            table.addCell(cellOf(new Paragraph(l.getQuantity() != null ? l.getQuantity().toPlainString() : "—").setFont(regular).setFontSize(8)));
            table.addCell(cellOf(new Paragraph(zar(l.getUnitPrice())).setFont(regular).setFontSize(8)));
            table.addCell(cellOf(new Paragraph(zar(l.getGrossAmount())).setFont(bold).setFontSize(8)));
        }
        doc.add(table);

        Table totals = new Table(UnitValue.createPercentArray(new float[]{2, 1})).useAllAvailableWidth().setMarginTop(12);
        addTotalRow(totals, "Gross amount", zar(claim.getGrossAmount()), regular);
        addTotalRow(totals, "Scheme portion", zar(claim.getSchemePortion()), regular);
        addTotalRow(totals, "Patient portion", zar(claim.getPatientPortion()), bold);
        doc.add(totals);
    }

    private void addTotalRow(Table t, String label, String value, PdfFont font) {
        t.addCell(cellOf(new Paragraph(label).setFont(font).setFontSize(10)));
        t.addCell(cellOf(new Paragraph(value).setFont(font).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
    }

    private void addFooter(Document doc, PdfFont regular) {
        doc.add(new Paragraph("Generated from the practice's records at the time of printing. Contact the practice for any discrepancy with the scheme's own records.")
                .setFont(regular).setFontSize(8).setFontColor(TEXT_MUTED).setTextAlignment(TextAlignment.CENTER).setMarginTop(24));
    }

    private Cell cellOf(IBlockElement content) {
        Cell c = new Cell();
        c.add(content);
        c.setBorder(Border.NO_BORDER);
        return c;
    }

    /** FIX: build failure — Image implements ILeafElement, not IBlockElement, in iText7. See ClinicPatientInvoicePdfService for the full explanation. */
    private Cell cellOf(Image image) {
        Cell c = new Cell();
        c.add(image);
        c.setBorder(Border.NO_BORDER);
        return c;
    }

    private Cell headerCell(String text, PdfFont bold) {
        Cell c = new Cell();
        c.add(new Paragraph(text).setFont(bold).setFontSize(8).setFontColor(WHITE));
        c.setBackgroundColor(BRAND_DARK);
        c.setBorder(Border.NO_BORDER);
        return c;
    }

    private String zar(BigDecimal amount) {
        return "R " + String.format(Locale.US, "%,.2f", amount != null ? amount : BigDecimal.ZERO);
    }

    /** FIX: confirmed via real testing — see ClinicReferralPdfService for the full explanation. */
    private String drName(String fullName) {
        if (fullName == null) return "";
        String trimmed = fullName.trim();
        return trimmed.toLowerCase(Locale.ROOT).startsWith("dr") ? trimmed : "Dr. " + trimmed;
    }
}