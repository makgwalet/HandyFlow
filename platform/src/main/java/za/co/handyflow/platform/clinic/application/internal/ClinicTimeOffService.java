package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.application.internal.TimeOffRules.Block;
import za.co.handyflow.platform.clinic.domain.repository.ClinicPractitionerRepository;
import za.co.handyflow.platform.clinic.dto.TimeOffDtos.CreateTimeOffRequest;
import za.co.handyflow.platform.clinic.dto.TimeOffDtos.TimeOffResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Leave, training and other blocks of time when a practitioner cannot be booked. Blocks are cancelled, never deleted. */
@Service
@RequiredArgsConstructor
public class ClinicTimeOffService {

    private final JdbcTemplate jdbc;
    private final ClinicPractitionerRepository practitionerRepo;

    /** Current and future blocks for a practitioner (anything that has not already ended), soonest first. */
    @Transactional(readOnly = true)
    public List<TimeOffResponse> upcoming(TenantId t, UUID practitionerId, Instant now) {
        return jdbc.query("SELECT id, practitioner_id, starts_at, ends_at, reason, created_at FROM clinic_practitioner_time_off "
                        + "WHERE tenant_id = ? AND practitioner_id = ? AND cancelled_at IS NULL AND ends_at > ? ORDER BY starts_at",
                (rs, i) -> new TimeOffResponse(rs.getObject("id", UUID.class), rs.getObject("practitioner_id", UUID.class),
                        rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(),
                        rs.getString("reason"), rs.getTimestamp("created_at").toInstant()),
                t.getValue(), practitionerId, Timestamp.from(now));
    }

    @Transactional
    public TimeOffResponse create(TenantId t, UUID practitionerId, CreateTimeOffRequest req) {
        practitionerRepo.findActiveById(t, practitionerId)
                .orElseThrow(() -> new ResourceNotFoundException("Practitioner", practitionerId.toString()));
        String reason = TimeOffRules.validate(req.startsAt(), req.endsAt(), req.reason());
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic_practitioner_time_off (id, tenant_id, practitioner_id, starts_at, ends_at, reason, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, t.getValue(), practitionerId, Timestamp.from(req.startsAt()), Timestamp.from(req.endsAt()), reason, currentUserIdOrNull());
        return new TimeOffResponse(id, practitionerId, req.startsAt(), req.endsAt(), reason, Instant.now());
    }

    /** Cancelling a block that is already cancelled changes nothing. */
    @Transactional
    public void cancel(TenantId t, UUID id) {
        int exists = jdbc.queryForObject("SELECT COUNT(*) FROM clinic_practitioner_time_off WHERE id = ? AND tenant_id = ?",
                Integer.class, id, t.getValue());
        if (exists == 0) throw new ResourceNotFoundException("Time off", id.toString());
        jdbc.update("UPDATE clinic_practitioner_time_off SET cancelled_at = now(), cancelled_by = ? "
                + "WHERE id = ? AND tenant_id = ? AND cancelled_at IS NULL", currentUserIdOrNull(), id, t.getValue());
    }

    /** Blocks overlapping [start, end) for the practitioner, used when booking. */
    @Transactional(readOnly = true)
    List<Block> overlapping(TenantId t, UUID practitionerId, Instant start, Instant end) {
        return jdbc.query("SELECT starts_at, ends_at, reason FROM clinic_practitioner_time_off "
                        + "WHERE tenant_id = ? AND practitioner_id = ? AND cancelled_at IS NULL AND starts_at < ? AND ends_at > ? "
                        + "ORDER BY starts_at",
                (rs, i) -> new Block(rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(), rs.getString("reason")),
                t.getValue(), practitionerId, Timestamp.from(end), Timestamp.from(start));
    }

    private static UUID currentUserIdOrNull() {
        try {
            return za.co.handyflow.platform.shared.UserContext.getCurrentUserId();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
