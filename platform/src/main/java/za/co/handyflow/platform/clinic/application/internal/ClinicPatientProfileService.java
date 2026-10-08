package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatientProfile;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientProfileRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.clinic.dto.ProfileDtos.*;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.UserContext;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** The patient profile page: details beyond the registration form, and how complete the profile is (patch 0166). */
@Service
@RequiredArgsConstructor
public class ClinicPatientProfileService {

    private final ClinicPatientProfileRepository profileRepo;
    private final ClinicPatientRepository        patientRepo;
    private final ClinicPatientHistoryService    historyService;
    private final ClinicConsentService           consentService;
    private final JdbcTemplate                   jdbc;

    @Transactional(readOnly = true)
    public ProfileResponse get(TenantId t, UUID patientId) {
        ClinicPatient p = patient(t, patientId);
        return respond(t, p, profileRepo.findOne(t, patientId).orElse(null));
    }

    @Transactional
    public ProfileResponse put(TenantId t, UUID patientId, ProfileRequest req) {
        ClinicPatient p = patient(t, patientId);
        ProfileRequest c = ProfileRules.clean(req);
        ClinicPatientProfile x = profileRepo.findOne(t, patientId).orElseGet(() -> ClinicPatientProfile.forPatient(t, patientId));
        x.replace(c.title(), c.idType(), c.nationality(), c.preferredLanguage(), c.preferredContact(), c.addressLine1(), c.addressLine2(),
                c.suburb(), c.city(), c.province(), c.postalCode(), c.emergencyRelationship(), c.secondaryContactName(),
                c.secondaryContactPhone(), c.secondaryContactRelationship(), c.paymentType(), currentUserOrNull());
        return respond(t, p, profileRepo.save(x));
    }

    /** Corrects name, ID, date of birth and sex. An ID already held by another active patient is refused. */
    @Transactional
    public PatientCore updateDemographics(TenantId t, UUID patientId, DemographicsRequest req) {
        ClinicPatient p = patient(t, patientId);
        var d = ProfileRules.demographics(req, LocalDate.now(za.co.handyflow.platform.clinic.application.internal.AppointmentRules.CLINIC_ZONE));
        if (d.idNumber() != null) {
            for (ClinicPatient other : patientRepo.findActiveByIdNumber(t, d.idNumber())) {
                if (!other.getId().equals(patientId)) {
                    throw new ConflictException("Another patient already has this ID number: " + other.getFirstName() + " " + other.getLastName()
                            + (other.getPatientNumber() != null ? " (" + other.getPatientNumber() + ")" : "") + ".");
                }
            }
        }
        var before = new CorrectionRules.Facts(p.getFirstName(), p.getLastName(), p.getIdNumber(), p.getDateOfBirth(), p.getGender(), p.getSexAtBirth());
        p.updateDemographics(d.firstName(), d.lastName(), d.idNumber(), d.dateOfBirth(), d.gender(), d.sexAtBirth());
        var after = new CorrectionRules.Facts(p.getFirstName(), p.getLastName(), p.getIdNumber(), p.getDateOfBirth(), p.getGender(), p.getSexAtBirth());
        PatientCore saved = core(patientRepo.save(p));
        UUID me = currentUserOrNull();
        for (var change : CorrectionRules.diff(before, after)) {
            jdbc.update("INSERT INTO clinic_patient_corrections (id, tenant_id, patient_id, field, old_value, new_value, changed_by) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), t.getValue(), patientId, change.field(), change.oldValue(), change.newValue(), me);
        }
        return saved;
    }

    /** Every correction to name, ID number, date of birth and sex, newest first. */
    @Transactional(readOnly = true)
    public List<CorrectionView> corrections(TenantId t, UUID patientId) {
        patient(t, patientId);
        return jdbc.query("""
                SELECT c.field, c.old_value, c.new_value, c.changed_at,
                       NULLIF(TRIM(CONCAT(u.first_name, ' ', u.last_name)), '') AS name
                  FROM clinic_patient_corrections c
                  LEFT JOIN users u ON u.id = c.changed_by AND u.tenant_id = c.tenant_id
                 WHERE c.tenant_id = ? AND c.patient_id = ?
                 ORDER BY c.changed_at DESC, c.field
                """,
                (rs, i) -> {
                    Timestamp at = rs.getTimestamp("changed_at");
                    return new CorrectionView(rs.getString("field"), rs.getString("old_value"), rs.getString("new_value"),
                            rs.getString("name"), at == null ? null : at.toInstant());
                }, t.getValue(), patientId);
    }

    @Transactional
    public PatientCore updateContact(TenantId t, UUID patientId, ContactRequest req) {
        ClinicPatient p = patient(t, patientId);
        var c = ProfileRules.contact(req);
        p.replaceContact(c.phone(), c.email(), c.emergencyName(), c.emergencyPhone());
        return core(patientRepo.save(p));
    }

    private ProfileResponse respond(TenantId t, ClinicPatient p, ClinicPatientProfile x) {
        boolean aid = historyService.getMedicalAid(t, p.getId()) != null;
        boolean consent = consentService.getConsentStatus(t, p.getId()).stream()
                .anyMatch(s -> "TREATMENT".equals(s.consentType()) && "GRANTED".equals(s.status()));
        var facts = new ProfileRules.Facts(p.getFirstName(), p.getLastName(), p.getDateOfBirth(), p.getSexAtBirth(), p.getIdNumber(),
                p.getPhone(), p.getEmergencyContactName(), p.getEmergencyContactPhone(),
                x == null ? null : x.getAddressLine1(), x == null ? null : x.getCity(), x == null ? null : x.getPaymentType(), aid, consent);
        Completeness comp = ProfileRules.completeness(facts);
        if (x == null) return new ProfileResponse(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, comp);
        return new ProfileResponse(x.getTitle(), x.getIdType(), x.getNationality(), x.getPreferredLanguage(), x.getPreferredContact(),
                x.getAddressLine1(), x.getAddressLine2(), x.getSuburb(), x.getCity(), x.getProvince(), x.getPostalCode(),
                x.getEmergencyRelationship(), x.getSecondaryContactName(), x.getSecondaryContactPhone(), x.getSecondaryContactRelationship(),
                x.getPaymentType(), comp);
    }

    private static PatientCore core(ClinicPatient p) {
        return new PatientCore(p.getId(), p.getFirstName(), p.getLastName(), p.getIdNumber(), p.getDateOfBirth(), p.getGender(),
                p.getSexAtBirth(), p.getPhone(), p.getEmail(), p.getEmergencyContactName(), p.getEmergencyContactPhone());
    }

    private ClinicPatient patient(TenantId t, UUID id) {
        return patientRepo.findActiveById(t, id).orElseThrow(() -> new ResourceNotFoundException("Patient", id.toString()));
    }

    private static UUID currentUserOrNull() {
        try { return UserContext.getCurrentUserId(); } catch (RuntimeException e) { return null; }
    }
}
