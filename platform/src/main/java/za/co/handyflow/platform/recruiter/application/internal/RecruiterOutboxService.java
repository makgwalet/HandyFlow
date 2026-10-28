package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.recruiter.domain.model.RecruiterOutboxRules;
import za.co.handyflow.platform.recruiter.dto.RecruiterOutboxDtos.*;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Emails to candidates. {@link #enqueue} runs inside the caller's transaction, so an email exists only if the change that caused it was saved.
 * {@link #processDue} (run once a minute by RecruiterOutboxScheduler) sends them, retrying with a growing wait and giving up after five tries;
 * the ones given up on are listed for an administrator, who can try them again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecruiterOutboxService {

    private static final int BATCH = 20;
    private final JdbcTemplate jdbc;
    private final EmailService emailService;

    /** Joins the caller's transaction. An address that cannot be used is dropped here rather than failing the caller. */
    @Transactional
    public void enqueue(TenantId tenantId, UUID applicationId, String kind, String to, String subject, String bodyHtml) {
        enqueue(tenantId, applicationId, kind, to, subject, bodyHtml, null, null);
    }

    @Transactional
    public void enqueue(TenantId tenantId, UUID applicationId, String kind, String to, String subject, String bodyHtml, String attachmentName, byte[] attachment) {
        if (!RecruiterOutboxRules.deliverable(to)) {
            log.warn("Email not queued: no usable address for application={} kind={}", applicationId, kind);
            return;
        }
        jdbc.update("INSERT INTO rec_outbox (id, tenant_id, application_id, kind, to_email, subject, body_html, attachment_name, attachment) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), tenantId.getValue(), applicationId, kind, to, subject.length() > 300 ? subject.substring(0, 300) : subject, bodyHtml, attachmentName, attachment);
    }

    /** Sends what is due. Returns how many were sent. Rows are locked while they are worked on, so two instances never send the same email. */
    @Transactional
    public int processDue() {
        List<Object[]> due = jdbc.query("""
                SELECT id, to_email, subject, body_html, attachment_name, attachment, attempts FROM rec_outbox
                WHERE status = 'PENDING' AND next_attempt_at <= NOW()
                ORDER BY next_attempt_at LIMIT ? FOR UPDATE SKIP LOCKED
                """, (rs, i) -> new Object[]{rs.getObject("id", UUID.class), rs.getString("to_email"), rs.getString("subject"), rs.getString("body_html"),
                rs.getString("attachment_name"), rs.getBytes("attachment"), rs.getInt("attempts")}, BATCH);
        int sent = 0;
        for (Object[] row : due) {
            UUID id = (UUID) row[0];
            int attempts = (Integer) row[6] + 1;
            try {
                emailService.sendNow((String) row[1], (String) row[2], (String) row[3], (String) row[4], (byte[]) row[5]);
                jdbc.update("UPDATE rec_outbox SET status = 'SENT', attempts = ?, sent_at = NOW(), last_error = NULL, attachment = NULL WHERE id = ?", attempts, id);
                sent++;
            } catch (Exception e) {
                String status = RecruiterOutboxRules.statusAfter(attempts, false);
                jdbc.update("UPDATE rec_outbox SET status = ?, attempts = ?, last_error = ?, next_attempt_at = NOW() + (? * interval '1 minute') WHERE id = ?",
                        status, attempts, RecruiterOutboxRules.errorText(e), RecruiterOutboxRules.delayMinutes(attempts), id);
                if (RecruiterOutboxRules.FAILED.equals(status)) log.error("Recruiter email {} given up on after {} attempts: {}", id, attempts, e.getMessage());
                else log.warn("Recruiter email {} failed (attempt {}): {}", id, attempts, e.getMessage());
            }
        }
        return sent;
    }

    @Transactional(readOnly = true)
    public OutboxView view(TenantId tenantId) {
        int pending = count(tenantId, "PENDING"), sent = count(tenantId, "SENT"), failed = count(tenantId, "FAILED");
        List<FailedEmail> failures = jdbc.query("SELECT id, application_id, kind, to_email, subject, attempts, last_error, created_at FROM rec_outbox "
                        + "WHERE tenant_id = ? AND status = 'FAILED' ORDER BY created_at DESC LIMIT 100",
                (rs, i) -> new FailedEmail(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class), rs.getString("kind"), rs.getString("to_email"),
                        rs.getString("subject"), rs.getInt("attempts"), rs.getString("last_error"), rs.getTimestamp("created_at").toInstant()), tenantId.getValue());
        return new OutboxView(pending, sent, failed, failures);
    }

    private int count(TenantId tenantId, String status) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM rec_outbox WHERE tenant_id = ? AND status = ? AND created_at > NOW() - INTERVAL '90 days'", Integer.class,
                tenantId.getValue(), status);
        return n == null ? 0 : n;
    }

    @Transactional
    public OutboxView retry(TenantId tenantId, UUID id) {
        List<String> status = jdbc.queryForList("SELECT status FROM rec_outbox WHERE id = ? AND tenant_id = ?", String.class, id, tenantId.getValue());
        if (status.isEmpty()) throw new ResourceNotFoundException("Email", id.toString());
        String problem = RecruiterOutboxRules.retryProblem(status.get(0));
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "NOT_FAILED");
        jdbc.update("UPDATE rec_outbox SET status = 'PENDING', attempts = 0, next_attempt_at = NOW(), last_error = NULL WHERE id = ?", id);
        return view(tenantId);
    }

    /** Old sent emails are not kept for ever: they hold a candidate's address and the text we sent them. */
    @Transactional
    public int purgeOld() {
        return jdbc.update("DELETE FROM rec_outbox WHERE status = 'SENT' AND sent_at < NOW() - INTERVAL '90 days'");
    }
}
