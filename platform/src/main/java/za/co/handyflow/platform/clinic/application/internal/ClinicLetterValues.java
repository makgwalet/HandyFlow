package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Loads what a letter's merge fields are filled from: the patient, the visit (optional), the doctor, the practice and the recipient (patch 0168). */
@Service
@RequiredArgsConstructor
public class ClinicLetterValues {

    private final ClinicConsultationRepository   consultationRepo;
    private final ClinicPatientRepository        patientRepo;
    private final ClinicPatientProfileRepository profileRepo;
    private final ClinicPractitionerRepository   practitionerRepo;
    private final TenantFacade                   tenantFacade;

    /** The patient comes from the visit when there is one; otherwise {@code patientId} is required. A visit of another patient is refused. */
    @Transactional(readOnly = true)
    public Loaded load(TenantId t, UUID patientId, UUID consultationId, String recipientName, String recipientCompany) {
        ClinicConsultation c = null;
        if (consultationId != null) {
            c = consultationRepo.findActiveById(t, consultationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Consultation", consultationId.toString()));
            if (patientId != null && !patientId.equals(c.getPatientId())) throw new IllegalArgumentException("That visit belongs to a different patient");
            patientId = c.getPatientId();
        }
        if (patientId == null) throw new IllegalArgumentException("Choose a patient or a visit");
        UUID pid = patientId;
        ClinicPatient p = patientRepo.findActiveById(t, pid).orElseThrow(() -> new ResourceNotFoundException("Patient", pid.toString()));
        ClinicPractitioner dr = c == null || c.getPractitionerId() == null ? null : practitionerRepo.findActiveById(t, c.getPractitionerId()).orElse(null);
        ClinicPatientProfile profile = profileRepo.findOne(t, pid).orElse(null);
        LocalDate today = LocalDate.now(AppointmentRules.CLINIC_ZONE);
        var src = new LetterMerge.Source(p.getFirstName(), p.getLastName(), p.getDateOfBirth(), p.getIdNumber(), p.getPhone(), address(profile),
                c == null || c.getConsultedAt() == null ? null : c.getConsultedAt().atZone(AppointmentRules.CLINIC_ZONE).toLocalDate(),
                c == null ? null : c.getChiefComplaint(), c == null ? null : c.getDiagnosis(), c == null ? null : c.getTreatmentPlan(),
                dr == null ? null : dr.getFullName(), dr == null ? null : dr.getHpcsaNumber(), dr == null ? null : dr.getPracticeNumber(),
                tenantFacade.findTenantDetails(t).map(d -> d.companyName()).orElse(null), today, recipientName, recipientCompany);
        return new Loaded(p, c, dr, LetterMerge.values(src));
    }

    public record Loaded(ClinicPatient patient, ClinicConsultation visit, ClinicPractitioner doctor, Map<String, String> values) {}

    private static String address(ClinicPatientProfile x) {
        if (x == null) return null;
        String s = Stream.of(x.getAddressLine1(), x.getAddressLine2(), x.getSuburb(), x.getCity(), x.getPostalCode())
                .filter(v -> v != null && !v.isBlank()).collect(Collectors.joining(", "));
        return s.isEmpty() ? null : s;
    }
}
