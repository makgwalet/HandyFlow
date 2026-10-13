package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.application.internal.ClinicIssuedDocumentService.Issued;
import za.co.handyflow.platform.clinic.application.internal.document.ClinicalDocumentRules;
import za.co.handyflow.platform.clinic.application.internal.document.ClinicalDocumentRules.Built;
import za.co.handyflow.platform.clinic.application.internal.document.ClinicalDocumentRules.Person;
import za.co.handyflow.platform.clinic.application.internal.document.ClinicalDocumentRules.Signer;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentModel.Letterhead;
import za.co.handyflow.platform.clinic.application.internal.document.DocumentRenderer;
import za.co.handyflow.platform.clinic.domain.model.ClinicConsultation;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.model.ClinicPractitioner;
import za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPractitionerRepository;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The sick note and the referral letter on Document Studio (patch 0200, CLINIC-DEC-023 to 026): printed on the practice's letterhead by
 * the configured renderer and issued with a number, a hash and a public verification code. What the page says is decided by
 * {@link ClinicalDocumentRules}. The number is taken inside the same transaction as the PDF, so a document that cannot be made uses no number.
 */
@Service
@RequiredArgsConstructor
public class ClinicClinicalDocumentService {

    private final ClinicConsultationRepository consultationRepo;
    private final ClinicPatientRepository patientRepo;
    private final ClinicPractitionerRepository practitionerRepo;
    private final TenantFacade tenantFacade;
    private final ClinicLetterheadService letterheads;
    private final ClinicIssuedDocumentService issued;
    private final DocumentRenderer renderer;
    private final za.co.handyflow.platform.clinic.domain.repository.ClinicPrescriptionRepository prescriptionRepo;
    private final za.co.handyflow.platform.clinic.domain.repository.ClinicConsultationAddendumRepository addendumRepo;
    private final za.co.handyflow.platform.clinic.domain.repository.ClinicPatientConsentRepository consentRepo;
    private final ClinicStaffNames staffNames;

    private record Source(ClinicConsultation visit, ClinicPatient patient, ClinicPractitioner doctor) {}

    private Source load(TenantId t, UUID consultationId) {
        ClinicConsultation c = consultationRepo.findActiveById(t, consultationId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));
        ClinicPatient p = patientRepo.findActiveById(t, c.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", c.getPatientId().toString()));
        ClinicPractitioner dr = c.getPractitionerId() == null ? null : practitionerRepo.findActiveById(t, c.getPractitionerId()).orElse(null);
        return new Source(c, p, dr);
    }

    private static Person person(ClinicPatient p) {
        return new Person(p.getFullName() != null ? p.getFullName() : (p.getFirstName() + " " + p.getLastName()).trim(), p.getIdNumber(), p.getDateOfBirth(), p.getGender());
    }

    private static Signer signer(ClinicPractitioner dr) {
        if (dr == null) return null;
        List<String> bits = new ArrayList<>();
        if (dr.getHpcsaNumber() != null && !dr.getHpcsaNumber().isBlank()) bits.add("HPCSA " + dr.getHpcsaNumber().trim());
        if (dr.getPracticeNumber() != null && !dr.getPracticeNumber().isBlank()) bits.add("Practice no. " + dr.getPracticeNumber().trim());
        if (dr.getSpecialty() != null && !dr.getSpecialty().isBlank()) bits.add(dr.getSpecialty().trim());
        return new Signer(ClinicalDocumentRules.drName(dr.getFullName()), bits.isEmpty() ? null : String.join(" · ", bits));
    }

    /** The chosen or default letterhead; if none is set up yet, the practice name and phone, as these documents always had. */
    private ClinicLetterheadService.Resolved head(TenantId t, UUID letterheadId) {
        var found = letterheads.resolve(t.getValue(), letterheadId, null);
        if (found.isPresent()) return found.get();
        var d = tenantFacade.findTenantDetails(t);
        List<String> lines = new ArrayList<>();
        d.map(x -> x.phone()).filter(v -> v != null && !v.isBlank()).ifPresent(v -> lines.add("Tel: " + v));
        return new ClinicLetterheadService.Resolved(null, new Letterhead(d.map(x -> x.companyName()).orElse(""), lines, null));
    }

    @Transactional
    public Issued issueSickNote(TenantId t, UUID consultationId, LocalDate unfitFrom, LocalDate unfitTo, String notes, UUID letterheadId, UUID by) {
        Source s = load(t, consultationId);
        var head = head(t, letterheadId);
        LocalDate examined = s.visit().getConsultedAt() == null ? null : s.visit().getConsultedAt().atZone(AppointmentRules.CLINIC_ZONE).toLocalDate();
        Built b = ClinicalDocumentRules.sickNote(head.letterhead(), person(s.patient()), signer(s.doctor()), examined, unfitFrom, unfitTo,
                s.visit().getDiagnosis(), notes, LocalDate.now(AppointmentRules.CLINIC_ZONE));
        return issue("SICK_NOTE", t, s, head, b, by);
    }

    @Transactional
    public Issued issueReferral(TenantId t, UUID consultationId, String specialistName, String specialty, String reason, String urgency,
                                String additionalNotes, UUID letterheadId, UUID by) {
        Source s = load(t, consultationId);
        var head = head(t, letterheadId);
        ClinicConsultation c = s.visit();
        Built b = ClinicalDocumentRules.referral(head.letterhead(), person(s.patient()), signer(s.doctor()), specialistName, specialty, reason, urgency,
                c.getChiefComplaint(), c.getDiagnosis(), c.getIcd10Codes(), c.getTreatmentPlan(), additionalNotes, LocalDate.now(AppointmentRules.CLINIC_ZONE));
        return issue("REFERRAL", t, s, head, b, by);
    }

    // ── Patch 0203: prescription, visit summary, consent record ────────────────────────────────────────────────────

    /** A visit counts as signed when its record is closed: SIGNED or LOCKED. Anything else prints a not-issued copy with no number. */
    static boolean signed(ClinicConsultation c) {
        return "SIGNED".equals(c.getStatus()) || "LOCKED".equals(c.getStatus());
    }

    private static LocalDate visitDate(ClinicConsultation c) {
        return c.getConsultedAt() == null ? null : c.getConsultedAt().atZone(AppointmentRules.CLINIC_ZONE).toLocalDate();
    }

    /**
     * The visit's prescription. One numbered copy stands per visit: downloading again returns it. If the medicines changed since it was
     * issued, it is refused until someone reissues it with {@code reissueReason}, which withdraws the earlier copy (two valid scripts for one
     * visit must never be in circulation). An unsigned visit prints a copy marked not issued, with no number.
     */
    @Transactional
    public Issued prescription(TenantId t, UUID consultationId, UUID letterheadId, String reissueReason, UUID by) {
        Source s = load(t, consultationId);
        var head = head(t, letterheadId);
        ClinicPractitioner dr = s.doctor();
        var prescriber = dr == null ? null : new ClinicalDocumentRules.Prescriber(dr.getFullName(), dr.getHpcsaNumber(), dr.getPracticeNumber(), dr.getSpecialty());
        List<ClinicalDocumentRules.RxLine> lines = prescriptionRepo.findByConsultation(t, consultationId).stream()
                .map(r -> new ClinicalDocumentRules.RxLine(r.getMedicationName(), r.getSchedule(), r.getNappiCode(), r.getDosage(), r.getFrequency(),
                        r.getDuration(), r.getInstructions(), r.getQuantity(), r.getRepeats()))
                .toList();
        boolean ok = signed(s.visit());
        Built b = ClinicalDocumentRules.prescription(head.letterhead(), person(s.patient()), prescriber, visitDate(s.visit()), lines, ok,
                LocalDate.now(AppointmentRules.CLINIC_ZONE));
        if (!ok) return draft(b);
        return issueOnce("PRESCRIPTION", consultationId.toString(), t, s, head, b, by, IssueOnceRules.WhenChanged.ASK_FOR_REASON, reissueReason);
    }

    /** The visit summary (medical report). One standing copy per visit; after an addendum or any change it is replaced by an updated one. */
    @Transactional
    public Issued visitSummary(TenantId t, UUID consultationId, UUID letterheadId, UUID by) {
        Source s = load(t, consultationId);
        var head = head(t, letterheadId);
        ClinicConsultation c = s.visit();
        var vitals = new ClinicalDocumentRules.Vitals(unit(c.getWeightKg(), " kg"), unit(c.getHeightCm(), " cm"), c.getBloodPressure(),
                c.getPulseBpm() == null ? null : c.getPulseBpm() + " bpm", unit(c.getTemperatureC(), " °C"), unit(c.getOxygenSatPct(), " %"));
        var notes = addendumRepo.findByConsultation(t, consultationId);
        java.util.Set<UUID> authors = new java.util.HashSet<>();
        notes.forEach(a -> { if (a.getAuthorUserId() != null) authors.add(a.getAuthorUserId()); });
        var names = authors.isEmpty() ? java.util.Map.<UUID, String>of() : staffNames.names(t.getValue(), authors);
        java.time.format.DateTimeFormatter when = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", java.util.Locale.ENGLISH)
                .withZone(AppointmentRules.CLINIC_ZONE);
        List<ClinicalDocumentRules.Addendum> addenda = notes.stream()
                .map(a -> new ClinicalDocumentRules.Addendum(AddendumRules.label(a.getKind()), a.getCreatedAt() == null ? "—" : when.format(a.getCreatedAt()),
                        names.get(a.getAuthorUserId()), a.getText()))
                .toList();
        boolean ok = signed(c);
        Built b = ClinicalDocumentRules.visitSummary(head.letterhead(), person(s.patient()), s.doctor() == null ? null : s.doctor().getFullName(),
                visitDate(c), vitals, c.getChiefComplaint(), c.getDiagnosis(), c.getIcd10Codes(), c.getTreatmentPlan(), c.getFollowUpDays(),
                addenda, ok, LocalDate.now(AppointmentRules.CLINIC_ZONE));
        if (!ok) return draft(b);
        return issueOnce("MEDICAL_REPORT", consultationId.toString(), t, s, head, b, by, IssueOnceRules.WhenChanged.SUPERSEDE, null);
    }

    /** A record of one consent decision. The decision never changes (the consent log is append-only), so one copy stands per decision. */
    @Transactional
    public Issued consentRecord(TenantId t, UUID patientId, UUID consentEventId, UUID letterheadId, UUID by) {
        ClinicPatient p = patientRepo.findActiveById(t, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId.toString()));
        var event = consentRepo.findByPatient(t, patientId).stream().filter(e -> e.getId().equals(consentEventId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Consent", consentEventId.toString()));
        var head = head(t, letterheadId);
        java.time.format.DateTimeFormatter when = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm", java.util.Locale.ENGLISH)
                .withZone(AppointmentRules.CLINIC_ZONE);
        var fact = new ClinicalDocumentRules.ConsentFact(event.getConsentType(), event.getAction(), event.getMethod(), event.getCapturedByName(),
                event.getCreatedAt() == null ? null : when.format(event.getCreatedAt()), event.getNotes());
        Built b = ClinicalDocumentRules.consentRecord(head.letterhead(), person(p), fact, LocalDate.now(AppointmentRules.CLINIC_ZONE));
        DocumentModel m = b.model();
        return issued.issueOnce(t.getValue(), "CONSENT_FORM", consentEventId.toString(), patientId, null, head.id(),
                head.letterhead().name() == null ? "" : head.letterhead().name(), renderer.name(), b.snapshot(), by,
                IssueOnceRules.WhenChanged.SUPERSEDE, null, stamp -> renderer.render(ClinicLetterPdfService.withStamp(m, stamp)));
    }

    private static String unit(java.math.BigDecimal v, String unit) { return v == null ? null : v.toPlainString() + unit; }

    /** An unsigned visit's copy: rendered, never numbered, never kept. */
    private Issued draft(Built b) {
        DocumentModel m = b.model();
        return new Issued(null, null, renderer.render(new DocumentModel(m.title(), m.letterhead(), m.blocks(), "Not issued: the visit is not signed", true)));
    }

    private Issued issueOnce(String type, String sourceRef, TenantId t, Source s, ClinicLetterheadService.Resolved head, Built b, UUID by,
                             IssueOnceRules.WhenChanged policy, String reissueReason) {
        DocumentModel m = b.model();
        return issued.issueOnce(t.getValue(), type, sourceRef, s.patient().getId(), s.visit().getId(), head.id(),
                head.letterhead().name() == null ? "" : head.letterhead().name(), renderer.name(), b.snapshot(), by, policy, reissueReason,
                stamp -> renderer.render(ClinicLetterPdfService.withStamp(m, stamp)));
    }

    private Issued issue(String type, TenantId t, Source s, ClinicLetterheadService.Resolved head, Built b, UUID by) {
        DocumentModel m = b.model();
        return issued.issue(t.getValue(), type, s.patient().getId(), s.visit().getId(), null, head.id(),
                head.letterhead().name() == null ? "" : head.letterhead().name(), renderer.name(), b.snapshot(), by,
                stamp -> renderer.render(ClinicLetterPdfService.withStamp(m, stamp)));
    }
}
