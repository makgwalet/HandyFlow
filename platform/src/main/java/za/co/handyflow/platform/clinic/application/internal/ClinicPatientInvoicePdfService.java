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
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicClaim;
import za.co.handyflow.platform.clinic.domain.model.ClinicClaimLine;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.model.ClinicPractitioner;
import za.co.handyflow.platform.clinic.domain.repository.ClinicClaimRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPractitionerRepository;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * FIX: "no patient invoice/receipt PDF" gap — ClinicClaim already tracks
 * patientPortion separately from schemePortion, but there was no printable
 * document for the self-pay/co-pay amount after a claim. Deliberately its
 * own small class (same rationale as Invoicing's ReceiptPdfService): a
 * distinct artifact, self-contained, StandardFonts only rather than
 * depending on embedded font files this class can't verify exist.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicPatientInvoicePdfService {

    private final ClinicClaimRepository claimRepo;
    private final ClinicPatientRepository patientRepo;
    private final ClinicPractitionerRepository practitionerRepo;
    private final TenantFacade tenantFacade;

    private static final DeviceRgb NAVY  = new DeviceRgb(0x1B, 0x3A, 0x6B);
    private static final DeviceRgb TEAL  = new DeviceRgb(0x0D, 0x94, 0x88);
    private static final DeviceRgb GRAY  = new DeviceRgb(0x64, 0x74, 0x8B);
    private static final DeviceRgb LIGHT = new DeviceRgb(0xF8, 0xFA, 0xFC);
    private static final DeviceRgb WHITE = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy");

    @Transactional(readOnly = true)
    public byte[] generate(TenantId tenantId, UUID claimId) {
        ClinicClaim claim = claimRepo.findActiveById(tenantId, claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim", claimId.toString()));
        ClinicPatient patient = patientRepo.findActiveById(tenantId, claim.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", claim.getPatientId().toString()));
        ClinicPractitioner practitioner = claim.getPractitionerId() != null
                ? practitionerRepo.findActiveById(tenantId, claim.getPractitionerId()).orElse(null)
                : null;
        TenantDetails tenant = tenantFacade.findTenantDetails(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId.toString()));

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(36, 40, 36, 40);

            PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

            Table header = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
            // Same logo-loading pattern already confirmed working in
            // CreditNotePdfService/StatementOfAccountPdfService.
            Table left = new Table(1).useAllAvailableWidth();
            boolean logoAdded = false;
            if (tenant.logoUrl() != null && !tenant.logoUrl().isBlank()) {
                try {
                    byte[] imageBytes = decodeLogoBytes(tenant.logoUrl());
                    Image logo = new Image(com.itextpdf.io.image.ImageDataFactory.create(imageBytes))
                            .setMaxHeight(50).setAutoScale(false);
                    left.addCell(cell(logo));
                    logoAdded = true;
                } catch (Exception ex) {
                    log.warn("Could not load logo for patient invoice: {}", ex.getMessage());
                }
            }
            if (!logoAdded) {
                left.addCell(cell(new Paragraph(tenant.companyName()).setFont(bold).setFontSize(14)));
            } else {
                left.addCell(cell(new Paragraph(tenant.companyName()).setFont(bold).setFontSize(10).setFontColor(GRAY).setMarginTop(4)));
            }
            header.addCell(cell(left));
            header.addCell(cell(new Paragraph("PATIENT INVOICE").setFont(bold).setFontSize(20)
                    .setFontColor(TEAL).setTextAlignment(TextAlignment.RIGHT)));
            doc.add(header);
            doc.add(new LineSeparator(new SolidLine(1f)).setMarginTop(8).setMarginBottom(14).setFontColor(TEAL));

            Table info = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth().setMarginBottom(16);
            info.addCell(cell(new Paragraph("Patient: " + patient.getFullName()).setFont(regular).setFontSize(10)));
            info.addCell(cell(new Paragraph("Date: " + LocalDate.now().format(DATE_FMT))
                    .setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
            info.addCell(cell(new Paragraph(patient.getIdNumber() != null ? "ID: " + patient.getIdNumber() : "")
                    .setFont(regular).setFontSize(10)));
            info.addCell(cell(new Paragraph(practitioner != null ? "Practitioner: " + drName(practitioner.getFullName()) : "")
                    .setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
            if (claim.getSchemeName() != null) {
                info.addCell(cell(new Paragraph("Scheme: " + claim.getSchemeName()).setFont(regular).setFontSize(10)));
                info.addCell(cell(new Paragraph("")));
            }
            doc.add(info);

            Table lines = new Table(UnitValue.createPercentArray(new float[]{3, 1, 1, 1})).useAllAvailableWidth().setMarginBottom(16);
            lines.addHeaderCell(headerCell("Description", bold));
            lines.addHeaderCell(headerCell("Gross", bold));
            lines.addHeaderCell(headerCell("Scheme", bold));
            lines.addHeaderCell(headerCell("Patient", bold));
            for (ClinicClaimLine l : claim.getLines()) {
                lines.addCell(cell(new Paragraph(l.getDescription()).setFont(regular).setFontSize(9)));
                lines.addCell(cell(new Paragraph(zar(l.getGrossAmount())).setFont(regular).setFontSize(9)));
                lines.addCell(cell(new Paragraph(zar(l.getSchemePortion())).setFont(regular).setFontSize(9)));
                lines.addCell(cell(new Paragraph(zar(l.getPatientPortion())).setFont(bold).setFontSize(9)));
            }
            doc.add(lines);

            Table totals = new Table(UnitValue.createPercentArray(new float[]{2, 1})).useAllAvailableWidth();
            totals.addCell(cell(new Paragraph("Gross amount").setFont(regular).setFontSize(10)));
            totals.addCell(cell(new Paragraph(zar(claim.getGrossAmount())).setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
            totals.addCell(cell(new Paragraph("Paid by scheme").setFont(regular).setFontSize(10)));
            totals.addCell(cell(new Paragraph(zar(claim.getSchemePortion())).setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
            doc.add(totals);

            Table amountDue = new Table(1).useAllAvailableWidth().setBackgroundColor(LIGHT).setPadding(14).setMarginTop(10);
            amountDue.addCell(cell(new Paragraph("AMOUNT DUE FROM PATIENT").setFont(bold).setFontSize(9).setFontColor(TEAL)));
            amountDue.addCell(cell(new Paragraph(zar(claim.getPatientPortion())).setFont(bold).setFontSize(22).setFontColor(NAVY).setMarginTop(2)));
            doc.add(amountDue);

            doc.add(new Paragraph("This invoice reflects your portion after your medical aid scheme's contribution, if any.")
                    .setFont(regular).setFontSize(8).setFontColor(GRAY)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(24));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate patient invoice PDF for claim={}: {}", claimId, e.getMessage(), e);
            throw new RuntimeException("Patient invoice PDF generation failed", e);
        }
    }

    private Cell cell(IBlockElement content) {
        Cell c = new Cell();
        c.add(content);
        c.setBorder(Border.NO_BORDER);
        return c;
    }

    /**
     * FIX: build failure — Image implements ILeafElement, not
     * IBlockElement, in iText7. CreditNotePdfService (the working
     * reference this logo code was copied from) has this exact same
     * overload; I forgot to carry it over when applying the pattern here.
     */
    private Cell cell(Image image) {
        Cell c = new Cell();
        c.add(image);
        c.setBorder(Border.NO_BORDER);
        return c;
    }

    private Cell headerCell(String text, PdfFont bold) {
        Cell c = new Cell();
        c.add(new Paragraph(text).setFont(bold).setFontSize(9).setFontColor(WHITE));
        c.setBackgroundColor(NAVY);
        c.setBorder(Border.NO_BORDER);
        return c;
    }

    private String zar(BigDecimal v) {
        return "R " + String.format(Locale.US, "%,.2f", v != null ? v : BigDecimal.ZERO);
    }

    /** FIX: confirmed via real testing — see ClinicReferralPdfService for the full explanation. */
    private String drName(String fullName) {
        if (fullName == null) return "";
        String trimmed = fullName.trim();
        return trimmed.toLowerCase(Locale.ROOT).startsWith("dr") ? trimmed : "Dr. " + trimmed;
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
}