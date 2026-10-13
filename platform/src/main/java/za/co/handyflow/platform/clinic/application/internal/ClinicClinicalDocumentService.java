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

    private Issued issue(String type, TenantId t, Source s, ClinicLetterheadService.Resolved head, Built b, UUID by) {
        DocumentModel m = b.model();
        return issued.issue(t.getValue(), type, s.patient().getId(), s.visit().getId(), null, head.id(),
                head.letterhead().name() == null ? "" : head.letterhead().name(), renderer.name(), b.snapshot(), by,
                stamp -> renderer.render(ClinicLetterPdfService.withStamp(m, stamp)));
    }
}
