package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicPatient;
import za.co.handyflow.platform.clinic.domain.model.ClinicPractitioner;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPatientRepository;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPractitionerRepository;
import za.co.handyflow.platform.clinic.dto.RecallResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Patients due for a follow-up, worked out from their most recent finished consultation.
 * <ul>
 *   <li>Only the latest signed, locked or doctor-completed visit counts. If that visit set no follow-up,
 *       an older visit's follow-up no longer applies (they have been seen since).</li>
 *   <li>Drafts and abandoned visits are ignored.</li>
 *   <li>A recall clears itself once the patient has a live appointment after that visit (cancelled and
 *       no-show bookings do not count, and neither does the visit's own appointment).</li>
 *   <li>Dates are South African calendar days, not the server's zone.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ClinicRecallService {

    private static final String LATEST_SQL =
            "SELECT * FROM ("
                    + " SELECT DISTINCT ON (c.patient_id) c.id, c.patient_id, c.practitioner_id, c.consulted_at,"
                    + "        c.follow_up_days, c.diagnosis, c.appointment_id"
                    + " FROM clinic_consultations c"
                    + " JOIN clinic_patients p ON p.id = c.patient_id AND p.deleted_at IS NULL"
                    + " WHERE c.tenant_id = ? AND c.deleted_at IS NULL AND c.status IN ('SIGNED','LOCKED','DOCTOR_COMPLETED')"
                    + " ORDER BY c.patient_id, c.consulted_at DESC, c.id"
                    + ") l WHERE l.follow_up_days IS NOT NULL"
                    + " AND NOT EXISTS (SELECT 1 FROM clinic_appointments a WHERE a.tenant_id = ? AND a.patient_id = l.patient_id"
                    + "   AND a.deleted_at IS NULL AND a.status NOT IN ('CANCELLED','NO_SHOW')"
                    + "   AND a.scheduled_at > l.consulted_at AND (l.appointment_id IS NULL OR a.id <> l.appointment_id))"
                    + " ORDER BY l.consulted_at";

    private record Latest(UUID id, UUID patientId, UUID practitionerId, Instant consultedAt, int followUpDays, String diagnosis) {}

    private final JdbcTemplate jdbc;
    private final ClinicPatientRepository patientRepo;
    private final ClinicPractitionerRepository practitionerRepo;

    @Transactional(readOnly = true)
    public List<RecallResponse> getDueRecalls(TenantId tenantId) {
        return getDueRecalls(tenantId, Instant.now());
    }

    /** Same as {@link #getDueRecalls(TenantId)} for a given moment. */
    @Transactional(readOnly = true)
    public List<RecallResponse> getDueRecalls(TenantId tenantId, Instant now) {
        LocalDate today = now.atZone(RecallRules.CLINIC_ZONE).toLocalDate();
        List<Latest> due = jdbc.query(LATEST_SQL, (rs, i) -> new Latest(
                        rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getObject("practitioner_id", UUID.class), rs.getTimestamp("consulted_at").toInstant(),
                        rs.getInt("follow_up_days"), rs.getString("diagnosis")),
                        tenantId.getValue(), tenantId.getValue()).stream()
                .filter(c -> RecallRules.isDue(RecallRules.dueDate(c.consultedAt(), c.followUpDays(), RecallRules.CLINIC_ZONE), today))
                .sorted(Comparator.comparing(c -> RecallRules.dueDate(c.consultedAt(), c.followUpDays(), RecallRules.CLINIC_ZONE)))
                .toList();

        Set<UUID> patientIds = due.stream().map(Latest::patientId).collect(Collectors.toSet());
        Set<UUID> practIds = due.stream().map(Latest::practitionerId).filter(Objects::nonNull).collect(Collectors.toSet());

        List<ClinicPatient> patients = patientIds.isEmpty() ? List.of() : patientRepo.findAllByIds(tenantId, patientIds);
        Map<UUID, String> patientNames = patients.stream().collect(Collectors.toMap(ClinicPatient::getId, ClinicPatient::getFullName));
        Map<UUID, String> patientPhones = patients.stream()
                .collect(Collectors.toMap(ClinicPatient::getId, p -> p.getPhone() != null ? p.getPhone() : ""));
        Map<UUID, String> practNames = practIds.isEmpty() ? Map.of()
                : practitionerRepo.findAllByIds(tenantId, practIds).stream()
                .collect(Collectors.toMap(ClinicPractitioner::getId, ClinicPractitioner::getFullName));

        return due.stream()
                .map(c -> {
                    LocalDate dd = RecallRules.dueDate(c.consultedAt(), c.followUpDays(), RecallRules.CLINIC_ZONE);
                    return new RecallResponse(
                            c.id(), c.patientId(),
                            patientNames.getOrDefault(c.patientId(), "Patient"),
                            patientPhones.get(c.patientId()),
                            c.practitionerId(),
                            c.practitionerId() != null ? practNames.get(c.practitionerId()) : null,
                            c.consultedAt(), c.followUpDays(), dd, RecallRules.overdueDays(dd, today),
                            c.diagnosis());
                })
                .toList();
    }
}
