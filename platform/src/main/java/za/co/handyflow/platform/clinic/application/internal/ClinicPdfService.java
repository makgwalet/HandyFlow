package za.co.handyflow.platform.clinic.application.internal;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Generates clinic PDFs:
 * 1. Medical Certificate — SA DoH / HPCSA compliant fields
 * 2. Prescription — Medicines Act Section 22A compliant fields
 *
 * Both are legal documents. Layout is clean/professional — A4 letterhead style
 * with practice details at top, patient details in body, signature block at foot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicPdfService {

    private final ClinicConsultationRepository  consultationRepo;
    private final ClinicPatientRepository       patientRepo;
    private final ClinicPractitionerRepository  practitionerRepo;
    private final ClinicPrescriptionRepository  prescriptionRepo;
    private final TenantFacade                  tenantFacade;

    private static final DeviceRgb NAVY     = new DeviceRgb(0x1B, 0x3A, 0x6B);
    private static final DeviceRgb TEAL     = new DeviceRgb(0x0D, 0x94, 0x88);
    private static final DeviceRgb WHITE    = new DeviceRgb(0xFF, 0xFF, 0xFF);
    private static final DeviceRgb LIGHT_BG = new DeviceRgb(0xF8, 0xFA, 0xFC);
    private static final DeviceRgb MID_GRAY = new DeviceRgb(0xE2, 0xE8, 0xF0);
    private static final DeviceRgb TEXT     = new DeviceRgb(0x0F, 0x17, 0x2A);
    private static final DeviceRgb GRAY     = new DeviceRgb(0x64, 0x74, 0x8B);
    private static final DeviceRgb RED      = new DeviceRgb(0xDC, 0x26, 0x26);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy");

    // ── Medical Certificate ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateMedicalCertificate(TenantId tenantId, UUID consultationId,
                                             LocalDate unfitFrom, LocalDate unfitTo,
                                             String notes) {
        ClinicConsultation c = consultationRepo.findActiveById(tenantId, consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));
        ClinicPatient patient = patientRepo.findActiveById(tenantId, c.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", c.getPatientId().toString()));
        ClinicPractitioner practitioner = c.getPractitionerId() != null
                ? practitionerRepo.findActiveById(tenantId, c.getPractitionerId()).orElse(null)
                : null;
        TenantDetails tenant = tenantFacade.findTenantDetails(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId.toString()));

        return buildMedicalCertificate(tenant, patient, practitioner, c, unfitFrom, unfitTo, notes);
    }

    private byte[] buildMedicalCertificate(TenantDetails tenant, ClinicPatient patient,
                                           ClinicPractitioner practitioner,
                                           ClinicConsultation consultation,
                                           LocalDate unfitFrom, LocalDate unfitTo,
                                           String notes) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
            Document doc   = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 50, 50, 50);

            PdfFont regular = font("LiberationSans-Regular.ttf");
            PdfFont bold    = font("LiberationSans-Bold.ttf");

            // ── Letterhead ─────────────────────────────────────────────────────
            Table header = new Table(UnitValue.createPercentArray(new float[]{65, 35}))
                    .useAllAvailableWidth().setMarginBottom(20);
            Cell left = new Cell().setBorder(Border.NO_BORDER).setPadding(16)
                    .setBackgroundColor(NAVY);
            left.add(new Paragraph(tenant.companyName())
                    .setFont(bold).setFontSize(16).setFontColor(WHITE).setMarginBottom(4));
            if (tenant.address() != null)
                left.add(new Paragraph(formatAddress(tenant.address()))
                        .setFont(regular).setFontSize(9).setFontColor(WHITE));
            if (tenant.phone() != null)
                left.add(new Paragraph("Tel: " + tenant.phone())
                        .setFont(regular).setFontSize(9).setFontColor(WHITE));

            Cell right = new Cell().setBorder(Border.NO_BORDER).setPadding(16)
                    .setBackgroundColor(TEAL).setTextAlignment(TextAlignment.RIGHT);
            right.add(new Paragraph("MEDICAL CERTIFICATE")
                    .setFont(bold).setFontSize(14).setFontColor(WHITE).setMarginBottom(6));
            right.add(new Paragraph("Date: " + LocalDate.now().format(DATE_FMT))
                    .setFont(regular).setFontSize(9).setFontColor(WHITE));
            header.addCell(left);
            header.addCell(right);
            doc.add(header);

            // ── Practitioner details ───────────────────────────────────────────
            if (practitioner != null) {
                doc.add(sectionHeading("ATTENDING PRACTITIONER", bold));
                Table practTable = twoColTable();
                addRow(practTable, "Practitioner", drName(practitioner.getFirstName() + " " + practitioner.getLastName()), regular, bold);
                if (practitioner.getSpecialty() != null)
                    addRow(practTable, "Specialty", practitioner.getSpecialty(), regular, bold);
                if (practitioner.getHpcsaNumber() != null)
                    addRow(practTable, "HPCSA Reg. No.", practitioner.getHpcsaNumber(), regular, bold);
                if (practitioner.getPracticeNumber() != null)
                    addRow(practTable, "Practice No.", practitioner.getPracticeNumber(), regular, bold);
                doc.add(practTable);
            }

            // ── Patient details ────────────────────────────────────────────────
            doc.add(sectionHeading("PATIENT DETAILS", bold));
            Table patTable = twoColTable();
            addRow(patTable, "Full Name",   patient.getFirstName() + " " + patient.getLastName(), regular, bold);
            if (patient.getIdNumber() != null)
                addRow(patTable, "ID Number", patient.getIdNumber(), regular, bold);
            if (patient.getDateOfBirth() != null)
                addRow(patTable, "Date of Birth", patient.getDateOfBirth().format(DATE_FMT), regular, bold);
            if (patient.getGender() != null)
                addRow(patTable, "Gender", patient.getGender(), regular, bold);
            doc.add(patTable);

            // ── Certificate body ───────────────────────────────────────────────
            doc.add(sectionHeading("CERTIFICATE OF ILLNESS", bold));
            doc.add(new Paragraph(
                    "This is to certify that the above-named patient was examined on "
                            + consultation.getConsultedAt().atZone(ZoneId.of("Africa/Johannesburg"))
                            .toLocalDate().format(DATE_FMT)
                            + " and was found to be unfit for duty.")
                    .setFont(regular).setFontSize(11).setFontColor(TEXT)
                    .setMarginBottom(12));

            // Unfit dates
            Table unfitTable = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                    .useAllAvailableWidth().setMarginBottom(12);
            for (String[] cell : new String[][]{
                    {"UNFIT FROM", unfitFrom != null ? unfitFrom.format(DATE_FMT) : "—"},
                    {"UNFIT UNTIL", unfitTo   != null ? unfitTo.format(DATE_FMT)  : "—"},
            }) {
                unfitTable.addCell(new Cell().setBackgroundColor(LIGHT_BG)
                        .setBorder(new SolidBorder(MID_GRAY, 1)).setPadding(14)
                        .add(new Paragraph(cell[0]).setFont(bold).setFontSize(9)
                                .setFontColor(GRAY).setCharacterSpacing(0.5f).setMarginBottom(4))
                        .add(new Paragraph(cell[1]).setFont(bold).setFontSize(16).setFontColor(NAVY)));
            }
            doc.add(unfitTable);

            if (consultation.getDiagnosis() != null) {
                doc.add(new Paragraph("Diagnosis / Clinical impression:")
                        .setFont(bold).setFontSize(10).setFontColor(TEXT).setMarginBottom(4));
                doc.add(new Paragraph(consultation.getDiagnosis())
                        .setFont(regular).setFontSize(11).setFontColor(TEXT).setMarginBottom(12));
            }
            if (notes != null && !notes.isBlank()) {
                doc.add(new Paragraph("Notes:")
                        .setFont(bold).setFontSize(10).setFontColor(TEXT).setMarginBottom(4));
                doc.add(new Paragraph(notes)
                        .setFont(regular).setFontSize(10).setFontColor(GRAY).setMarginBottom(12));
            }

            // ── Signature block ────────────────────────────────────────────────
            doc.add(new Paragraph("\n\n"));
            Table sigTable = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                    .useAllAvailableWidth().setMarginTop(20);
            sigTable.addCell(new Cell().setBorder(Border.NO_BORDER)
                    .add(new Paragraph("_______________________________")
                            .setFont(regular).setFontSize(10).setFontColor(GRAY))
                    .add(new Paragraph("Signature of Attending Practitioner")
                            .setFont(regular).setFontSize(9).setFontColor(GRAY)));
            sigTable.addCell(new Cell().setBorder(Border.NO_BORDER)
                    .add(new Paragraph("_______________________________")
                            .setFont(regular).setFontSize(10).setFontColor(GRAY))
                    .add(new Paragraph("Date")
                            .setFont(regular).setFontSize(9).setFontColor(GRAY)));
            doc.add(sigTable);

            footer(doc, tenant, regular);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate medical certificate", e);
        }
    }

    // ── Prescription PDF ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generatePrescription(TenantId tenantId, UUID consultationId) {
        ClinicConsultation c = consultationRepo.findActiveById(tenantId, consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));
        ClinicPatient patient = patientRepo.findActiveById(tenantId, c.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", c.getPatientId().toString()));
        ClinicPractitioner practitioner = c.getPractitionerId() != null
                ? practitionerRepo.findActiveById(tenantId, c.getPractitionerId()).orElse(null)
                : null;
        List<ClinicPrescription> prescriptions =
                prescriptionRepo.findByConsultation(tenantId, consultationId);
        TenantDetails tenant = tenantFacade.findTenantDetails(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId.toString()));

        return buildPrescription(tenant, patient, practitioner, c, prescriptions);
    }

    private byte[] buildPrescription(TenantDetails tenant, ClinicPatient patient,
                                     ClinicPractitioner practitioner,
                                     ClinicConsultation consultation,
                                     List<ClinicPrescription> prescriptions) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
            Document doc   = new Document(pdf, PageSize.A4);
            doc.setMargins(40, 50, 50, 50);

            PdfFont regular = font("LiberationSans-Regular.ttf");
            PdfFont bold    = font("LiberationSans-Bold.ttf");

            // ── Letterhead ─────────────────────────────────────────────────────
            Table header = new Table(UnitValue.createPercentArray(new float[]{65, 35}))
                    .useAllAvailableWidth().setMarginBottom(20);
            Cell left = new Cell().setBorder(Border.NO_BORDER).setPadding(16)
                    .setBackgroundColor(TEAL);
            left.add(new Paragraph(tenant.companyName())
                    .setFont(bold).setFontSize(16).setFontColor(WHITE).setMarginBottom(4));
            if (tenant.address() != null)
                left.add(new Paragraph(formatAddress(tenant.address()))
                        .setFont(regular).setFontSize(9).setFontColor(WHITE));
            if (tenant.phone() != null)
                left.add(new Paragraph("Tel: " + tenant.phone())
                        .setFont(regular).setFontSize(9).setFontColor(WHITE));

            Cell right = new Cell().setBorder(Border.NO_BORDER).setPadding(16)
                    .setBackgroundColor(NAVY).setTextAlignment(TextAlignment.RIGHT);
            right.add(new Paragraph("PRESCRIPTION")
                    .setFont(bold).setFontSize(16).setFontColor(WHITE).setMarginBottom(6));
            right.add(new Paragraph("Rx")
                    .setFont(bold).setFontSize(28).setFontColor(new DeviceRgb(0x93, 0xC5, 0xFD))
                    .setMarginBottom(4));
            right.add(new Paragraph(LocalDate.now().format(DATE_FMT))
                    .setFont(regular).setFontSize(9).setFontColor(WHITE));
            header.addCell(left);
            header.addCell(right);
            doc.add(header);

            // ── Practitioner + Practice ────────────────────────────────────────
            if (practitioner != null) {
                doc.add(sectionHeading("PRESCRIBER", bold));
                Table practTable = twoColTable();
                addRow(practTable, "Name",         drName(practitioner.getFirstName() + " " + practitioner.getLastName()), regular, bold);
                if (practitioner.getHpcsaNumber() != null)
                    addRow(practTable, "HPCSA No.", practitioner.getHpcsaNumber(), regular, bold);
                if (practitioner.getPracticeNumber() != null)
                    addRow(practTable, "Practice No.", practitioner.getPracticeNumber(), regular, bold);
                if (practitioner.getSpecialty() != null)
                    addRow(practTable, "Specialty", practitioner.getSpecialty(), regular, bold);
                doc.add(practTable);
            }

            // ── Patient ────────────────────────────────────────────────────────
            doc.add(sectionHeading("PATIENT", bold));
            Table patTable = twoColTable();
            addRow(patTable, "Name",    patient.getFirstName() + " " + patient.getLastName(), regular, bold);
            if (patient.getIdNumber() != null)
                addRow(patTable, "ID No.",  patient.getIdNumber(), regular, bold);
            if (patient.getDateOfBirth() != null)
                addRow(patTable, "DOB",  patient.getDateOfBirth().format(DATE_FMT), regular, bold);
            doc.add(patTable);

            // ── Medicines ──────────────────────────────────────────────────────
            doc.add(sectionHeading("MEDICINES PRESCRIBED", bold));

            if (prescriptions.isEmpty()) {
                doc.add(new Paragraph("No medicines prescribed for this consultation.")
                        .setFont(regular).setFontSize(11).setFontColor(GRAY));
            } else {
                for (int i = 0; i < prescriptions.size(); i++) {
                    ClinicPrescription rx = prescriptions.get(i);
                    Table rxTable = new Table(UnitValue.createPercentArray(new float[]{100}))
                            .useAllAvailableWidth().setMarginBottom(10);
                    Cell rxCell = new Cell().setBackgroundColor(LIGHT_BG)
                            .setBorder(new SolidBorder(MID_GRAY, 1))
                            .setBorderLeft(new SolidBorder(TEAL, 4)).setPadding(14);

                    // Medicine name + schedule badge
                    Table nameRow = new Table(UnitValue.createPercentArray(new float[]{80, 20}))
                            .useAllAvailableWidth();
                    nameRow.addCell(new Cell().setBorder(Border.NO_BORDER)
                            .add(new Paragraph((i + 1) + ". " + rx.getMedicationName())
                                    .setFont(bold).setFontSize(13).setFontColor(NAVY)));
                    if (rx.getSchedule() != null) {
                        nameRow.addCell(new Cell().setBorder(Border.NO_BORDER)
                                .setTextAlignment(TextAlignment.RIGHT)
                                .add(new Paragraph("Sch " + rx.getSchedule())
                                        .setFont(bold).setFontSize(10)
                                        .setFontColor(rx.getSchedule() >= 5 ? RED : TEAL)));
                    } else {
                        nameRow.addCell(new Cell().setBorder(Border.NO_BORDER));
                    }
                    rxCell.add(nameRow);

                    if (rx.getNappiCode() != null)
                        rxCell.add(new Paragraph("NAPPI: " + rx.getNappiCode())
                                .setFont(regular).setFontSize(9).setFontColor(GRAY).setMarginTop(2));

                    // Dosage details
                    String details = buildDosageString(rx);
                    rxCell.add(new Paragraph(details)
                            .setFont(regular).setFontSize(11).setFontColor(TEXT).setMarginTop(6));

                    if (rx.getInstructions() != null)
                        rxCell.add(new Paragraph("Instructions: " + rx.getInstructions())
                                .setFont(regular).setFontSize(10)
                                .setFontColor(GRAY).setItalic().setMarginTop(4));

                    Table detRow = new Table(UnitValue.createPercentArray(new float[]{33, 33, 34}))
                            .useAllAvailableWidth().setMarginTop(8);
                    for (String[] kv : new String[][]{
                            {"Qty", rx.getQuantity() != null ? String.valueOf(rx.getQuantity()) : "—"},
                            {"Repeats", String.valueOf(rx.getRepeats())},
                            {"Dispensed", rx.isDispensed() ? "Yes" : "No"},
                    }) {
                        detRow.addCell(new Cell().setBorder(Border.NO_BORDER)
                                .add(new Paragraph(kv[0]).setFont(bold).setFontSize(8)
                                        .setFontColor(GRAY).setCharacterSpacing(0.4f))
                                .add(new Paragraph(kv[1]).setFont(bold).setFontSize(11)
                                        .setFontColor(TEXT)));
                    }
                    rxCell.add(detRow);
                    rxTable.addCell(rxCell);
                    doc.add(rxTable);
                }
            }

            // ── Signature ──────────────────────────────────────────────────────
            doc.add(new Paragraph("\n"));
            Table sigTable = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                    .useAllAvailableWidth().setMarginTop(16);
            sigTable.addCell(new Cell().setBorder(Border.NO_BORDER)
                    .add(new Paragraph("_______________________________")
                            .setFont(regular).setFontSize(10).setFontColor(GRAY))
                    .add(new Paragraph("Prescriber's Signature")
                            .setFont(regular).setFontSize(9).setFontColor(GRAY)));
            sigTable.addCell(new Cell().setBorder(Border.NO_BORDER)
                    .add(new Paragraph("_______________________________")
                            .setFont(regular).setFontSize(10).setFontColor(GRAY))
                    .add(new Paragraph("Date")
                            .setFont(regular).setFontSize(9).setFontColor(GRAY)));
            doc.add(sigTable);

            doc.add(new Paragraph(
                    "This prescription is valid for 30 days from the date of issue. "
                            + "Issued under the Medicines and Related Substances Act (Act 101 of 1965).")
                    .setFont(regular).setFontSize(8).setFontColor(GRAY).setMarginTop(16));

            footer(doc, tenant, regular);
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate prescription PDF", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildDosageString(ClinicPrescription rx) {
        StringBuilder sb = new StringBuilder();
        if (rx.getDosage()    != null) sb.append(rx.getDosage()).append("  ");
        if (rx.getFrequency() != null) sb.append(rx.getFrequency()).append("  ");
        if (rx.getDuration()  != null) sb.append("for ").append(rx.getDuration());
        return sb.toString().trim();
    }

    private Paragraph sectionHeading(String title, PdfFont bold) {
        return new Paragraph(title)
                .setFont(bold).setFontSize(8).setFontColor(TEAL)
                .setCharacterSpacing(1f).setMarginBottom(6).setMarginTop(14);
    }

    private Table twoColTable() {
        return new Table(UnitValue.createPercentArray(new float[]{30, 70}))
                .useAllAvailableWidth().setMarginBottom(4);
    }

    private void addRow(Table t, String label, String value, PdfFont regular, PdfFont bold) {
        boolean even = t.getNumberOfRows() % 2 == 0;
        DeviceRgb bg = even ? LIGHT_BG : WHITE;
        t.addCell(new Cell().setBackgroundColor(bg).setBorder(Border.NO_BORDER).setPadding(7)
                .add(new Paragraph(label).setFont(bold).setFontSize(9).setFontColor(GRAY)));
        t.addCell(new Cell().setBackgroundColor(bg).setBorder(Border.NO_BORDER).setPadding(7)
                .add(new Paragraph(value).setFont(regular).setFontSize(10).setFontColor(TEXT)));
    }

    /**
     * FIX: confirmed via real testing — a real prescription PDF showed
     * "Name: Dr. Dr Sarah Mokoena." This practitioner's stored firstName
     * already includes "Dr" (a pre-existing data inconsistency — other
     * practitioners' names don't have it stored that way). Same fix
     * already applied across six other Clinic PDFs this session
     * (ClinicReferralPdfService, ClinicClaimSubmissionPdfService, etc.) —
     * this file just hadn't been touched yet since only partial visibility
     * into it was available until now.
     */
    private String drName(String fullName) {
        if (fullName == null) return "";
        String trimmed = fullName.trim();
        return trimmed.toLowerCase(java.util.Locale.ROOT).startsWith("dr") ? trimmed : "Dr. " + trimmed;
    }

    private void footer(Document doc, TenantDetails tenant, PdfFont regular) {
        doc.add(new Paragraph(
                "Generated by HandyFlow  •  " + tenant.companyName()
                        + "  •  " + LocalDate.now().format(DATE_FMT))
                .setFont(regular).setFontSize(7).setFontColor(GRAY)
                .setTextAlignment(TextAlignment.CENTER).setMarginTop(24));
    }

    private PdfFont font(String name) {
        try {
            return PdfFontFactory.createFont(
                    Objects.requireNonNull(
                            ClinicPdfService.class.getResourceAsStream("/fonts/" + name)
                    ).readAllBytes(),
                    PdfEncodings.WINANSI, EmbeddingStrategy.FORCE_EMBEDDED);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load font: " + name, e);
        }
    }

    private String formatAddress(java.util.Map<String, String> address) {
        if (address == null) return "";
        return java.util.stream.Stream.of(
                        address.get("street"), address.get("suburb"),
                        address.get("city"),   address.get("postalCode"))
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
    }

}