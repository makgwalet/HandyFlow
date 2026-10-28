package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterApprovalRules;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Reminds the approvers of a job or an offer that has waited for a decision: on the 2nd day and every 3rd day after, until someone decides it
 * or the request is withdrawn. Runs once a day, so the day-count rule is what stops it repeating. The reminder goes to the company's
 * administrators other than the person who asked, as the first request did. Best effort: one bad row never stops the rest.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecruiterApprovalReminderScheduler {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final JdbcTemplate jdbc;
    private final RecruiterApprovalNotifier notifier;

    /** One request waiting for a decision. */
    record Waiting(UUID tenantId, boolean job, UUID refId, String jobTitle, UUID submitter, Instant since) {}

    /** The requests whose waiting time calls for a reminder today, with the days they have waited. */
    static List<long[]> dueIndexes(List<Waiting> waiting, LocalDate today) {
        java.util.ArrayList<long[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < waiting.size(); i++) {
            Waiting w = waiting.get(i);
            if (w.since() == null) continue;
            long days = ChronoUnit.DAYS.between(w.since().atZone(SAST).toLocalDate(), today);
            if (RecruiterApprovalRules.reminderDue(days)) out.add(new long[]{i, days});
        }
        return out;
    }

    @Scheduled(cron = "0 30 7 * * *", zone = "Africa/Johannesburg")
    public void remindApprovers() {
        List<Waiting> waiting = jdbc.query("""
                SELECT tenant_id, TRUE AS job, id AS ref, title, submitted_by AS who, submitted_at AS since
                  FROM rec_jobs WHERE approval_status = 'SUBMITTED'
                UNION ALL
                SELECT a.tenant_id, FALSE, a.id, j.title, a.offer_submitted_by, a.offer_submitted_at
                  FROM rec_applications a JOIN rec_jobs j ON j.id = a.job_id
                 WHERE a.offer_approval_status = 'SUBMITTED' AND a.offer_letter_sent_at IS NULL
                   AND a.stage NOT IN ('REJECTED', 'WITHDRAWN')
                """, (rs, n) -> new Waiting((UUID) rs.getObject("tenant_id"), rs.getBoolean("job"), (UUID) rs.getObject("ref"),
                rs.getString("title"), (UUID) rs.getObject("who"),
                rs.getTimestamp("since") == null ? null : rs.getTimestamp("since").toInstant()));
        int sent = 0;
        for (long[] d : dueIndexes(waiting, LocalDate.now(SAST))) {
            Waiting w = waiting.get((int) d[0]);
            try {
                notifier.reminder(TenantId.of(w.tenantId()), w.job(), w.refId(), w.jobTitle(), w.submitter(), d[1]);
                sent++;
            } catch (RuntimeException e) {
                log.warn("Approval reminder for {} failed: {}", w.refId(), e.getMessage());
            }
        }
        if (sent > 0) log.info("Sent {} recruiter approval reminder(s)", sent);
    }
}
