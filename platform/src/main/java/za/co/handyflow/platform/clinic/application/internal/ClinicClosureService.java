package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.application.internal.ClosureRules.Closure;
import za.co.handyflow.platform.clinic.dto.ClosureDtos.ClosureResponse;
import za.co.handyflow.platform.clinic.dto.ClosureDtos.CreateClosureRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Whole days when the clinic does not take bookings. Closures are cancelled, never deleted. */
@Service
@RequiredArgsConstructor
public class ClinicClosureService {

    private final JdbcTemplate jdbc;

    /** Closures that have not finished yet (last day today or later), soonest first. */
    @Transactional(readOnly = true)
    public List<ClosureResponse> upcoming(TenantId t, LocalDate today) {
        return jdbc.query("SELECT id, first_day, last_day, reason FROM clinic_closures "
                        + "WHERE tenant_id = ? AND cancelled_at IS NULL AND last_day >= ? ORDER BY first_day",
                (rs, i) -> new ClosureResponse(rs.getObject("id", UUID.class), rs.getDate("first_day").toLocalDate(),
                        rs.getDate("last_day").toLocalDate(), rs.getString("reason")),
                t.getValue(), Date.valueOf(today));
    }

    @Transactional
    public ClosureResponse create(TenantId t, CreateClosureRequest req) {
        String reason = ClosureRules.validate(req.firstDay(), req.lastDay(), req.reason());
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO clinic_closures (id, tenant_id, first_day, last_day, reason, created_by) VALUES (?, ?, ?, ?, ?, ?)",
                id, t.getValue(), Date.valueOf(req.firstDay()), Date.valueOf(req.lastDay()), reason, currentUserIdOrNull());
        return new ClosureResponse(id, req.firstDay(), req.lastDay(), reason);
    }

    /** Cancelling a closure that is already cancelled changes nothing. */
    @Transactional
    public void cancel(TenantId t, UUID id) {
        int exists = jdbc.queryForObject("SELECT COUNT(*) FROM clinic_closures WHERE id = ? AND tenant_id = ?",
                Integer.class, id, t.getValue());
        if (exists == 0) throw new ResourceNotFoundException("Closure", id.toString());
        jdbc.update("UPDATE clinic_closures SET cancelled_at = now(), cancelled_by = ? WHERE id = ? AND tenant_id = ? AND cancelled_at IS NULL",
                currentUserIdOrNull(), id, t.getValue());
    }

    /** Closures touching any day from firstDay to lastDay inclusive, used when booking. */
    @Transactional(readOnly = true)
    List<Closure> overlapping(TenantId t, LocalDate firstDay, LocalDate lastDay) {
        return jdbc.query("SELECT first_day, last_day, reason FROM clinic_closures "
                        + "WHERE tenant_id = ? AND cancelled_at IS NULL AND first_day <= ? AND last_day >= ? ORDER BY first_day",
                (rs, i) -> new Closure(rs.getDate("first_day").toLocalDate(), rs.getDate("last_day").toLocalDate(), rs.getString("reason")),
                t.getValue(), Date.valueOf(lastDay), Date.valueOf(firstDay));
    }

    private static UUID currentUserIdOrNull() {
        try {
            return za.co.handyflow.platform.shared.UserContext.getCurrentUserId();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
