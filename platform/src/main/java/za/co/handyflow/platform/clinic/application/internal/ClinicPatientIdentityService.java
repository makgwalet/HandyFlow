package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.model.SaIdNumber;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Patient identity rules (S1-7): SA ID validation, duplicate detection, per-tenant patient numbers. */
@Service
@RequiredArgsConstructor
public class ClinicPatientIdentityService {

    public record Candidate(UUID id, String patientNumber, String fullName, LocalDate dateOfBirth, String matchReason) {}

    private final ClinicPatientRepository patientRepo;
    private final JdbcTemplate            jdbc;

    /**
     * Throws if the ID is a malformed SA ID, disagrees with a supplied date of birth, or already belongs
     * to an active patient. Non-13-digit values (passports etc.) are only checked for duplicates.
     */
    @Transactional(readOnly = true)
    public void assertCanRegister(TenantId tenantId, String idNumber, LocalDate dateOfBirth) {
        if (idNumber == null || idNumber.isBlank()) return;
        String id = idNumber.trim();
        if (SaIdNumber.looksLikeSaId(id)) {
            String problem = SaIdNumber.problem(id);
            if (problem != null) throw new IllegalArgumentException("Invalid ID number: " + problem);
            SaIdNumber parsed = SaIdNumber.parseOrNull(id);
            if (dateOfBirth != null && !parsed.matchesDateOfBirth(dateOfBirth)) {
                throw new IllegalArgumentException("The date of birth does not match the ID number.");
            }
        }
        List<ClinicPatient> same = patientRepo.findActiveByIdNumber(tenantId, id);
        if (!same.isEmpty()) {
            ClinicPatient p = same.get(0);
            throw new ConflictException("A patient with this ID number already exists: "
                    + p.getFirstName() + " " + p.getLastName()
                    + (p.getPatientNumber() != null ? " (" + p.getPatientNumber() + ")" : "") + ".");
        }
    }

    /** Candidates to show the receptionist before creating a patient. */
    @Transactional(readOnly = true)
    public List<Candidate> findCandidates(TenantId tenantId, String idNumber, String firstName,
                                          String lastName, LocalDate dateOfBirth) {
        List<Candidate> out = new ArrayList<>();
        List<UUID> seen = new ArrayList<>();
        if (idNumber != null && !idNumber.isBlank()) {
            for (ClinicPatient p : patientRepo.findActiveByIdNumber(tenantId, idNumber.trim())) {
                out.add(toCandidate(p, "Same ID number")); seen.add(p.getId());
            }
        }
        if (firstName != null && lastName != null && dateOfBirth != null
                && !firstName.isBlank() && !lastName.isBlank()) {
            for (ClinicPatient p : patientRepo.findActiveByNameAndDob(tenantId, firstName.trim(), lastName.trim(), dateOfBirth)) {
                if (!seen.contains(p.getId())) out.add(toCandidate(p, "Same name and date of birth"));
            }
        }
        return out;
    }

    /** Atomically hands out the next patient number for the tenant, e.g. P000042. */
    @Transactional
    public String nextPatientNumber(TenantId tenantId) {
        Long n = jdbc.queryForObject("""
            INSERT INTO clinic_patient_counters (tenant_id, next_value) VALUES (?, 2)
            ON CONFLICT (tenant_id) DO UPDATE SET next_value = clinic_patient_counters.next_value + 1
            RETURNING next_value - 1""", Long.class, tenantId.getValue());
        return format(n == null ? 1 : n);
    }

    static String format(long n) { return String.format("P%06d", n); }

    private static Candidate toCandidate(ClinicPatient p, String reason) {
        return new Candidate(p.getId(), p.getPatientNumber(), p.getFirstName() + " " + p.getLastName(),
                p.getDateOfBirth(), reason);
    }
}
