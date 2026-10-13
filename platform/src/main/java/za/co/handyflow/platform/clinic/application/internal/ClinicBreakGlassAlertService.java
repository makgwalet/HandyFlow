package za.co.handyflow.platform.clinic.application.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.clinic.application.internal.BreakGlassAlertRules.Reviewer;
import za.co.handyflow.platform.clinic.dto.TaskDtos.CreateTaskRequest;
import za.co.handyflow.platform.shared.EmailService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tells the people who review break-glass access that someone has broken the glass (patch 0189, CLINIC-DEC-009): an email to each
 * active user who holds CLINIC_BREAK_GLASS_REVIEW, and one open clinic task per session that closes when the session is acknowledged.
 * Nothing here can fail the break-glass itself: it runs after the session is saved and every step is on its own.
 */
@Service
public class ClinicBreakGlassAlertService {

    static final String SOURCE = "BREAK_GLASS";
    private static final Logger log = LoggerFactory.getLogger(ClinicBreakGlassAlertService.class);

    private final JdbcTemplate jdbc;
    private final EmailService email;
    private final ClinicTaskService tasks;

    public ClinicBreakGlassAlertService(JdbcTemplate jdbc, EmailService email, ClinicTaskService tasks) {
        this.jdbc = jdbc; this.email = email; this.tasks = tasks;
    }

    /** Called once the session has been saved. Returns how many people were emailed. */
    public int alert(UUID tenantId, UUID sessionId, UUID actor, Instant startedAt, Instant expiresAt) {
        String actorName = nameOf(tenantId, actor);
        int sent = 0;
        try {
            for (Reviewer r : BreakGlassAlertRules.recipients(reviewers(tenantId), actor)) {
                try {
                    email.send(r.email(), BreakGlassAlertRules.subject(), BreakGlassAlertRules.emailBody(r.name(), actorName, startedAt, expiresAt));
                    sent++;
                } catch (RuntimeException e) {
                    log.warn("Break-glass alert email to a reviewer failed: {}", e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            log.warn("Could not look up break-glass reviewers: {}", e.getMessage());
        }
        try {
            tasks.create(tenantId, actor, new CreateTaskRequest(null, null, "GENERAL", BreakGlassAlertRules.taskTitle(actorName),
                    BreakGlassAlertRules.taskDetail(startedAt), null, SOURCE, sessionId));
        } catch (RuntimeException e) {
            log.warn("Could not create the break-glass review task for session {}: {}", sessionId, e.getMessage());
        }
        return sent;
    }

    /** Closes the review task of a session once it has been acknowledged. */
    public void acknowledged(UUID tenantId, UUID sessionId, UUID by) {
        try {
            List<UUID> open = jdbc.query("SELECT id FROM clinic_tasks WHERE tenant_id = ? AND source_type = ? AND source_id = ? AND status = 'OPEN'",
                    (rs, i) -> (UUID) rs.getObject(1), tenantId, SOURCE, sessionId);
            for (UUID id : open) tasks.complete(tenantId, by, id, "Acknowledged in Break-glass review");
        } catch (RuntimeException e) {
            log.warn("Could not close the break-glass review task for session {}: {}", sessionId, e.getMessage());
        }
    }

    private List<Reviewer> reviewers(UUID tenantId) {
        return jdbc.query("""
                SELECT DISTINCT u.id, u.email, NULLIF(TRIM(CONCAT(u.first_name, ' ', u.last_name)), '') AS name
                FROM users u
                JOIN user_roles ur ON ur.user_id = u.id
                JOIN role_permissions rp ON rp.role_id = ur.role_id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE u.tenant_id = ? AND u.status = 'ACTIVE' AND p.name = 'CLINIC_BREAK_GLASS_REVIEW'""",
                (rs, i) -> new Reviewer((UUID) rs.getObject(1), rs.getString(2), rs.getString(3)), tenantId);
    }

    private String nameOf(UUID tenantId, UUID userId) {
        if (userId == null) return null;
        List<String> n = jdbc.query("SELECT NULLIF(TRIM(CONCAT(first_name, ' ', last_name)), '') FROM users WHERE tenant_id = ? AND id = ?",
                (rs, i) -> rs.getString(1), tenantId, userId);
        return n.isEmpty() ? null : n.get(0);
    }
}
