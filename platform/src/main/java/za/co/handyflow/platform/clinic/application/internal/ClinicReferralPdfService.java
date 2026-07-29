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
import za.co.handyflow.platform.shared.EmailService;
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
 * FIX: "no referral letter" gap — a very standard GP output (referring a
 * patient to a specialist) with no equivalent in this codebase before this.
 * <p>
 * Not backed by a persisted "Referral" entity — same convention as the
 * existing medical certificate PDF (ClinicPdfService.generateMedicalCertificate
 * takes free-text params at generation time, no separate MedicalCertificate
 * entity exists either). Specialist name/specialty/reason/urgency are
 * supplied per-generation, not stored, matching that established pattern
 * rather than introducing a new persistence concept for this one document.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicReferralPdfService {

    private final ClinicConsultationRepository consultationRepo;
    private final ClinicPatientRepository patientRepo;
    private final ClinicPractitionerRepository practitionerRepo;
    private final TenantFacade tenantFacade;
    private final EmailService emailService;

    private static final DeviceRgb BRAND_DARK = new DeviceRgb(15, 76, 92);
    private static final DeviceRgb TEAL       = new DeviceRgb(13, 148, 136);
    private static final DeviceRgb AMBER      = new DeviceRgb(180, 120, 30);
    private static final DeviceRgb RED        = new DeviceRgb(180, 60, 50);
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(120, 130, 140);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    public byte[] generate(TenantId tenantId, UUID consultationId,
                           String specialistName, String specialty, String reason,
                           String urgency, String additionalNotes) {
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
            PdfFont italic  = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);

            addHeader(document, companyName, logoUrl, practitioner, bold, regular);
            addSalutation(document, specialistName, specialty, patient, bold, regular);
            addClinicalBody(document, consultation, reason, urgency, additionalNotes, bold, regular, italic);
            addSignOff(document, practitioner, bold, regular);

            document.close();
            byte[] pdfBytes = baos.toByteArray();

            // FIX: "no PDF is ever emailed" gap — referral letters were
            // download-only. Fires on every generate() call rather than
            // tracking "already sent" state, since referral letters aren't
            // persisted (no entity — same convention as the medical
            // certificate this mirrors); every call is effectively issuing
            // a fresh letter, so there's no re-send concern to guard
            // against structurally.
            sendReferralEmail(patient, practitioner, pdfBytes, specialistName, specialty);

            return pdfBytes;
        } catch (Exception e) {
            log.error("Failed to generate referral letter for consultation={}: {}", consultationId, e.getMessage(), e);
            throw new RuntimeException("Referral letter generation failed", e);
        }
    }

    private void addHeader(Document doc, String companyName, String logoUrl, ClinicPractitioner practitioner, PdfFont bold, PdfFont regular) {
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
                log.warn("Could not load logo for referral letter: {}", ex.getMessage());
            }
        }
        if (!logoAdded) {
            left.addCell(cellOf(new Paragraph(companyName).setFont(bold).setFontSize(14)));
        } else {
            left.addCell(cellOf(new Paragraph(companyName).setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginTop(4)));
        }
        if (practitioner != null) {
            left.addCell(cellOf(new Paragraph(drName(practitioner.getFullName())
                    + (practitioner.getHpcsaNumber() != null ? " · HPCSA " + practitioner.getHpcsaNumber() : ""))
                    .setFont(regular).setFontSize(9).setFontColor(TEXT_MUTED)));
        }
        header.addCell(cellOf(left));
        header.addCell(cellOf(new Paragraph("REFERRAL LETTER").setFont(bold).setFontSize(18)
                .setFontColor(TEAL).setTextAlignment(TextAlignment.RIGHT)));
        doc.add(header);
        doc.add(new LineSeparator(new SolidLine(1f)).setMarginTop(8).setMarginBottom(16).setFontColor(TEAL));
    }

    private void addSalutation(Document doc, String specialistName, String specialty, ClinicPatient patient,
                               PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph(LocalDate.now().format(DATE_FMT)).setFont(regular).setFontSize(10).setMarginBottom(14));

        String toLine = (specialistName != null && !specialistName.isBlank() ? specialistName : "Dear Colleague")
                + (specialty != null && !specialty.isBlank() ? " (" + specialty + ")" : "");
        doc.add(new Paragraph(toLine).setFont(bold).setFontSize(11).setMarginBottom(4));

        String dobStr = patient.getDateOfBirth() != null ? patient.getDateOfBirth().format(DATE_FMT) : "—";
        doc.add(new Paragraph("Re: " + patient.getFullName() + " (DOB " + dobStr + ")")
                .setFont(bold).setFontSize(11).setFontColor(BRAND_DARK).setMarginBottom(16));
    }

    private void addClinicalBody(Document doc, ClinicConsultation c, String reason, String urgency,
                                 String additionalNotes, PdfFont bold, PdfFont regular, PdfFont italic) {
        if (urgency != null && !urgency.isBlank()) {
            DeviceRgb urgencyColor = "URGENT".equalsIgnoreCase(urgency) ? RED
                    : "SEMI_URGENT".equalsIgnoreCase(urgency) ? AMBER : TEXT_MUTED;
            doc.add(new Paragraph("Urgency: " + urgency.replace("_", " ")).setFont(bold).setFontSize(10)
                    .setFontColor(urgencyColor).setMarginBottom(12));
        }

        doc.add(new Paragraph("I am referring the above patient for your assessment and management.")
                .setFont(regular).setFontSize(10).setMarginBottom(14));

        addSection(doc, "REASON FOR REFERRAL", reason, bold, regular);
        addSection(doc, "CHIEF COMPLAINT", c.getChiefComplaint(), bold, regular);
        addSection(doc, "DIAGNOSIS", c.getDiagnosis(), bold, regular);

        List<String> icd10 = c.getIcd10Codes();
        if (icd10 != null && !icd10.isEmpty()) {
            addSection(doc, "ICD-10 CODES", String.join(", ", icd10), bold, regular);
        }

        addSection(doc, "TREATMENT TO DATE", c.getTreatmentPlan(), bold, regular);
        addSection(doc, "ADDITIONAL NOTES", additionalNotes, bold, regular);

        doc.add(new Paragraph("Please do not hesitate to contact the practice for any further clinical information.")
                .setFont(italic).setFontSize(10).setFontColor(TEXT_MUTED).setMarginTop(6).setMarginBottom(24));
    }

    private void addSection(Document doc, String title, String content, PdfFont bold, PdfFont regular) {
        if (content == null || content.isBlank()) return;
        doc.add(new Paragraph(title).setFont(bold).setFontSize(10).setFontColor(TEXT_MUTED).setMarginBottom(4));
        doc.add(new Paragraph(content).setFont(regular).setFontSize(10).setMarginBottom(14));
    }

    private void addSignOff(Document doc, ClinicPractitioner practitioner, PdfFont bold, PdfFont regular) {
        doc.add(new Paragraph("Kind regards,").setFont(regular).setFontSize(10).setMarginBottom(30));
        if (practitioner != null) {
            doc.add(new Paragraph(drName(practitioner.getFullName())).setFont(bold).setFontSize(11));
            if (practitioner.getHpcsaNumber() != null) {
                doc.add(new Paragraph("HPCSA " + practitioner.getHpcsaNumber()).setFont(regular).setFontSize(9).setFontColor(TEXT_MUTED));
            }
            if (practitioner.getSpecialty() != null) {
                doc.add(new Paragraph(practitioner.getSpecialty()).setFont(regular).setFontSize(9).setFontColor(TEXT_MUTED));
            }
        }
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

    /**
     * FIX: confirmed via real testing — a real referral letter showed
     * "Dr. Dr Sarah Mokoena." "Dr. " was being unconditionally prepended
     * to practitioner names; this practitioner's stored fullName already
     * includes "Dr" (a pre-existing data inconsistency — other
     * practitioners, e.g. "Sarah Khumalo" seen on a different document,
     * don't have it stored that way). Same fix applied everywhere this
     * codebase builds a "Dr. {name}" display string — see
     * ClinicAppointmentReminderService, ClinicService,
     * ClinicClaimSubmissionPdfService, ClinicConsultationSummaryPdfService,
     * and ClinicPatientInvoicePdfService.
     */
    private String drName(String fullName) {
        if (fullName == null) return "";
        String trimmed = fullName.trim();
        return trimmed.toLowerCase(Locale.ROOT).startsWith("dr") ? trimmed : "Dr. " + trimmed;
    }

    /** Same fix as InvoicePdfService/QuotePdfService/ReceiptPdfService/CreditNotePdfService — logoUrl is a data: URI. */
    private void sendReferralEmail(ClinicPatient patient, ClinicPractitioner practitioner,
                                   byte[] pdfBytes, String specialistName, String specialty) {
        try {
            if (patient.getEmail() == null || patient.getEmail().isBlank()) {
                return;
            }
            String greetingName = patient.getFirstName() != null ? patient.getFirstName() : "there";
            String referredTo = (specialistName != null && !specialistName.isBlank() ? specialistName : "a specialist")
                    + (specialty != null && !specialty.isBlank() ? " (" + specialty + ")" : "");
            String html = "<p>Dear " + greetingName + ",</p>"
                    + "<p>You have been referred to " + referredTo + ". A copy of the referral letter is attached "
                    + "for your records — please bring it to your appointment.</p>"
                    + "<p>If you have any questions, please contact the practice.</p>";
            emailService.sendWithAttachment(patient.getEmail(), "Your referral letter", html,
                    "referral-letter.pdf", pdfBytes);
            log.info("Sent referral letter patient={}", patient.getId());
        } catch (Exception e) {
            log.warn("Referral letter not emailed for patient={}: {}", patient.getId(), e.getMessage());
        }
    }

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