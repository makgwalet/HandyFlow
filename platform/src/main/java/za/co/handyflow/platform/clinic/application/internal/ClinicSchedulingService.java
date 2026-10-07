package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.application.internal.AppointmentRules.Clash;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Finds bookings that would clash with a new one, for the same practitioner or the same patient. */
@Service
@RequiredArgsConstructor
public class ClinicSchedulingService {

    private final JdbcTemplate jdbc;

    /** Live (not cancelled, no-show or deleted) bookings for the practitioner overlapping [start, start + minutes). */
    @Transactional(readOnly = true)
    List<Clash> findClashes(TenantId t, UUID practitionerId, Instant start, int minutes, UUID ignoreAppointmentId) {
        Timestamp from = Timestamp.from(start);
        Timestamp to = Timestamp.from(start.plus(Duration.ofMinutes(minutes)));
        return jdbc.query("SELECT p.full_name AS patient_name, a.scheduled_at, a.duration_minutes "
                        + "FROM clinic_appointments a JOIN clinic_patients p ON p.id = a.patient_id "
                        + "WHERE a.tenant_id = ? AND a.practitioner_id = ? AND a.deleted_at IS NULL "
                        + "AND a.status NOT IN ('CANCELLED','NO_SHOW') "
                        + "AND a.scheduled_at < ? AND a.scheduled_at + a.duration_minutes * INTERVAL '1 minute' > ? "
                        + "AND (?::uuid IS NULL OR a.id <> ?::uuid) ORDER BY a.scheduled_at",
                (rs, i) -> new Clash(rs.getString("patient_name"), rs.getTimestamp("scheduled_at").toInstant(),
                        rs.getInt("duration_minutes")),
                t.getValue(), practitionerId, to, from, ignoreAppointmentId, ignoreAppointmentId);
    }

    /**
     * Live bookings the PATIENT already has overlapping [start, start + minutes), with whom. The clash's name field
     * holds the practitioner's name (null when the booking has no practitioner).
     */
    @Transactional(readOnly = true)
    List<Clash> findPatientClashes(TenantId t, UUID patientId, Instant start, int minutes, UUID ignoreAppointmentId) {
        Timestamp from = Timestamp.from(start);
        Timestamp to = Timestamp.from(start.plus(Duration.ofMinutes(minutes)));
        return jdbc.query("SELECT pr.full_name AS practitioner_name, a.scheduled_at, a.duration_minutes "
                        + "FROM clinic_appointments a LEFT JOIN clinic_practitioners pr ON pr.id = a.practitioner_id "
                        + "WHERE a.tenant_id = ? AND a.patient_id = ? AND a.deleted_at IS NULL "
                        + "AND a.status NOT IN ('CANCELLED','NO_SHOW') "
                        + "AND a.scheduled_at < ? AND a.scheduled_at + a.duration_minutes * INTERVAL '1 minute' > ? "
                        + "AND (?::uuid IS NULL OR a.id <> ?::uuid) ORDER BY a.scheduled_at",
                (rs, i) -> new Clash(rs.getString("practitioner_name"), rs.getTimestamp("scheduled_at").toInstant(),
                        rs.getInt("duration_minutes")),
                t.getValue(), patientId, to, from, ignoreAppointmentId, ignoreAppointmentId);
    }
}
