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
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.model.ClinicPractitioner;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPractitionerRepository;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * FIX: "no consultation summary/after-visit note PDF" gap — a printable
 * visit summary distinct from the medical certificate (fitness for work)
 * and prescription (medication only). Standard GP output; this codebase
 * captures rich consultation data (vitals, diagnosis, ICD-10, treatment
 * plan) with no export of it at all before this.
 * <p>
 * Deliberately a new, standalone file rather than adding a method to the
 * existing ClinicPdfService — this session only has partial visibility
 * into that file's full contents (only the medical-certificate/
 * prescription sections were seen directly), and blind-editing a large
 * file it can't fully see is exactly the kind of guess this session has
 * consistently avoided. Same visual family (StandardFonts, same brand
 * colors) as every other Clinic PDF built this session, so it reads as
 * part of the same document set even though it lives in its own class.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicConsultationSummaryPdfService {

    private final ClinicConsultationRepository consultationRepo;
    private final ClinicPatientRepository patientRepo;
    private final ClinicPractitionerRepository practitionerRepo;
    private final TenantFacade tenantFacade;

    private static final DeviceRgb BRAND_DARK = new DeviceRgb(15, 76, 92);
    private static final DeviceRgb TEAL       = new DeviceRgb(13, 148, 136);
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(120, 130, 140);
    private static final DeviceRgb LIGHT_BG   = new DeviceRgb(240, 250, 246);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    public byte[] generate(TenantId tenantId, UUID consultationId) {
        ClinicConsultation consultation = consultationRepo.findActiveById(tenantId, consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));
        ClinicPatient patient = patientRepo.findActiveById(tenantId, consultation.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", consultation.getPatientId().toString()));
        ClinicPractitioner practitioner = consultation.getPractitionerId() != null
                ? practitionerRepo.findActiveById(tenantId, consultation.getPractitionerId()).orElse(null)
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
            addPatientInfo(document, consultation, patient, practitioner, bold, regular);
            addVitals(document, consultation, bold, regular);
            addClinicalSummary(document, consultation, bold, regular);
            addFooter(document, regular);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate consultation summary PDF for consultation={}: {}", consultationId, e.getMessage(), e);
            throw new RuntimeException("Consultation summary PDF generation failed", e);
        }
    }

    private void addHeader(Document doc, String companyName, String logoUrl, PdfFont bold, PdfFont regular) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
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
                log.warn("Could not load logo for visit summary: {}", ex.getMessage());
            }
        }
        if (!logoAdded) {
            left.addCell(cellOf(new Paragraph(companyName).setFont(bold).setFontSize(14)));
        } else {
            left.addCell(cellOf(new Paragraph(companyName).setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginTop(4)));
        }
        header.addCell(cellOf(left));
        header.addCell(cellOf(new Paragraph("VISIT SUMMARY").setFont(bold).setFontSize(18)
                .setFontColor(TEAL).setTextAlignment(TextAlignment.RIGHT)));
        doc.add(header);
        doc.add(new LineSeparator(new SolidLine(1f)).setMarginTop(8).setMarginBottom(14).setFontColor(TEAL));
    }

    private void addPatientInfo(Document doc, ClinicConsultation c, ClinicPatient patient,
                                ClinicPractitioner practitioner, PdfFont bold, PdfFont regular) {
        LocalDate visitDate = c.getConsultedAt() != null
                ? c.getConsultedAt().atZone(ZoneId.systemDefault()).toLocalDate() : LocalDate.now();

        Table info = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth().setMarginBottom(16);
        info.addCell(cellOf(new Paragraph("Patient: " + patient.getFullName()).setFont(regular).setFontSize(10)));
        info.addCell(cellOf(new Paragraph("Visit date: " + visitDate.format(DATE_FMT))
                .setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
        if (patient.getDateOfBirth() != null) {
            info.addCell(cellOf(new Paragraph("Date of birth: " + patient.getDateOfBirth().format(DATE_FMT)).setFont(regular).setFontSize(10)));
        } else {
            info.addCell(cellOf(new Paragraph("")));
        }
        if (practitioner != null) {
            info.addCell(cellOf(new Paragraph("Practitioner: " + drName(practitioner.getFullName()))
                    .setFont(regular).setFontSize(10).setTextAlignment(TextAlignment.RIGHT)));
        } else {
            info.addCell(cellOf(new Paragraph("")));
        }
        doc.add(info);
    }

    private void addVitals(Document doc, ClinicConsultation c, PdfFont bold, PdfFont regular) {
        boolean hasVitals = c.getWeightKg() != null || c.getHeightCm() != null || c.getBloodPressure() != null
                || c.getPulseBpm() != null || c.getTemperatureC() != null || c.getOxygenSatPct() != null;
        if (!hasVitals) return;

        doc.add(new Paragraph("VITALS").setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginBottom(6));
        Table vitals = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1, 1})).useAllAvailableWidth()
                .setBackgroundColor(LIGHT_BG).setMarginBottom(16);
        addVital(vitals, "Weight", c.getWeightKg() != null ? c.getWeightKg() + " kg" : "—", bold, regular);
        addVital(vitals, "Height", c.getHeightCm() != null ? c.getHeightCm() + " cm" : "—", bold, regular);
        addVital(vitals, "Blood pressure", c.getBloodPressure() != null ? c.getBloodPressure() : "—", bold, regular);
        addVital(vitals, "Pulse", c.getPulseBpm() != null ? c.getPulseBpm() + " bpm" : "—", bold, regular);
        addVital(vitals, "Temperature", c.getTemperatureC() != null ? c.getTemperatureC() + " °C" : "—", bold, regular);
        addVital(vitals, "O2 saturation", c.getOxygenSatPct() != null ? c.getOxygenSatPct() + " %" : "—", bold, regular);
        doc.add(vitals);
    }

    private void addVital(Table t, String label, String value, PdfFont bold, PdfFont regular) {
        Cell c = new Cell().setBorder(Border.NO_BORDER).setPadding(8);
        c.add(new Paragraph(label).setFont(regular).setFontSize(8).setFontColor(TEXT_MUTED));
        c.add(new Paragraph(value).setFont(bold).setFontSize(12).setFontColor(BRAND_DARK).setMarginTop(2));
        t.addCell(c);
    }

    private void addClinicalSummary(Document doc, ClinicConsultation c, PdfFont bold, PdfFont regular) {
        addSection(doc, "CHIEF COMPLAINT", c.getChiefComplaint(), bold, regular);
        addSection(doc, "DIAGNOSIS", c.getDiagnosis(), bold, regular);

        List<String> icd10 = c.getIcd10Codes();
        if (icd10 != null && !icd10.isEmpty()) {
            addSection(doc, "ICD-10 CODES", String.join(", ", icd10), bold, regular);
        }

        addSection(doc, "TREATMENT PLAN", c.getTreatmentPlan(), bold, regular);

        if (c.getFollowUpDays() != null && c.getFollowUpDays() > 0) {
            doc.add(new Paragraph("FOLLOW-UP").setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginBottom(4));
            doc.add(new Paragraph("Follow-up recommended in " + c.getFollowUpDays() + " day"
                    + (c.getFollowUpDays() == 1 ? "" : "s") + ".")
                    .setFont(regular).setFontSize(10).setMarginBottom(14));
        }
    }

    private void addSection(Document doc, String title, String content, PdfFont bold, PdfFont regular) {
        if (content == null || content.isBlank()) return;
        doc.add(new Paragraph(title).setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginBottom(4));
        doc.add(new Paragraph(content).setFont(regular).setFontSize(10).setMarginBottom(14));
    }

    private void addFooter(Document doc, PdfFont regular) {
        doc.add(new Paragraph("This summary is provided for the patient's own records. It does not replace advice from your treating practitioner.")
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