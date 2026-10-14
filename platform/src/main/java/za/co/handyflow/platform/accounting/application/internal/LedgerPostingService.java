package za.co.handyflow.platform.accounting.application.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.accounting.dto.PostingFailureResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Posts business events to the ledger without endangering the business record, and keeps every failure where people can
 * see and retry it (finance patch F4, 2026-10-14).
 * <p>
 * Each attempt runs in its own transaction. Before this, modules called the posting inside their own transaction and
 * caught the exception; Spring had already marked the outer transaction rollback-only by then, so a posting failure could
 * undo the invoice or claim it was meant to record. Now a failed attempt rolls back alone, the failure is written to
 * {@code acc_posting_failures} in a second separate transaction, and the exception is rethrown for the caller to log.
 * <p>
 * The cost: when the attempt succeeds but the caller's transaction later rolls back, the journal stays. Posting is the last
 * step in every caller, and the source key means a retried business action reuses that journal rather than adding one.
 */
@Slf4j
@Service
public class LedgerPostingService {

    private final AccountingService accountingService;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate separately;
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public LedgerPostingService(AccountingService accountingService, JdbcTemplate jdbc, PlatformTransactionManager tm) {
        this.accountingService = accountingService;
        this.jdbc = jdbc;
        this.separately = new TransactionTemplate(tm);
        this.separately.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Posts once (see AccountingService.postOnce) in a transaction of its own. On success, closes any open failure for the
     * same event. On failure, records it and rethrows.
     */
    public JournalEntryResponse postOnceOrRecord(TenantId tenantId, String sourceKey, CreateJournalEntryRequest req) {
        try {
            return separately.execute(status -> {
                JournalEntryResponse posted = accountingService.postOnce(tenantId, sourceKey, req);
                jdbc.update("""
                        UPDATE acc_posting_failures
                           SET status = 'RESOLVED', resolved_at = now(), journal_entry_id = ?,
                               resolution_note = 'Posted on a later attempt'
                         WHERE tenant_id = ? AND source_key = ? AND status = 'OPEN'""",
                        posted.id(), tenantId.getValue(), sourceKey);
                return posted;
            });
        } catch (RuntimeException e) {
            record(tenantId, sourceKey, req != null ? req.description() : null, req, e);
            throw e;
        }
    }

    /** Records a failure that happened before a journal could be built (for example a missing account). Never throws. */
    public void recordFailure(TenantId tenantId, String sourceKey, String summary, String reason) {
        record(tenantId, sourceKey, summary, null, new IllegalStateException(reason));
    }

    private void record(TenantId tenantId, String sourceKey, String summary, CreateJournalEntryRequest req, Throwable cause) {
        try {
            String requestJson = req != null ? json.writeValueAsString(req) : null;
            separately.executeWithoutResult(status -> jdbc.update("""
                    INSERT INTO acc_posting_failures (tenant_id, source_key, summary, request_json, reason)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (tenant_id, source_key) WHERE status = 'OPEN'
                    DO UPDATE SET attempts = acc_posting_failures.attempts + 1, last_failed_at = now(),
                                  reason = EXCLUDED.reason, summary = EXCLUDED.summary,
                                  request_json = COALESCE(EXCLUDED.request_json, acc_posting_failures.request_json)""",
                    tenantId.getValue(), sourceKey, PostingFailureRules.summary(summary), requestJson,
                    PostingFailureRules.reason(cause)));
            log.warn("[Accounting] Posting failed and was queued: source={} tenant={} reason={}",
                    sourceKey, tenantId, PostingFailureRules.reason(cause));
        } catch (Exception recordError) {
            // The queue itself failed; the log is the last record left, so make it complete.
            log.error("[Accounting] Posting failed AND could not be queued: source={} tenant={} reason={} queueError={}",
                    sourceKey, tenantId, PostingFailureRules.reason(cause), recordError.getMessage(), recordError);
        }
    }

    public List<PostingFailureResponse> list(TenantId tenantId, String status) {
        String s = status == null || status.isBlank() ? "OPEN" : status.trim().toUpperCase();
        return jdbc.query("""
                SELECT * FROM acc_posting_failures WHERE tenant_id = ? AND (? = 'ALL' OR status = ?)
                ORDER BY last_failed_at DESC LIMIT 500""", this::row, tenantId.getValue(), s, s);
    }

    public long openCount(TenantId tenantId) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM acc_posting_failures WHERE tenant_id = ? AND status = 'OPEN'",
                Long.class, tenantId.getValue());
        return n != null ? n : 0;
    }

    /** Posts the stored journal again. Closes the failure on success; adds an attempt and refuses with the reason otherwise. */
    public PostingFailureResponse retry(TenantId tenantId, UUID id, UUID by) {
        Row r = find(tenantId, id);
        String problem = PostingFailureRules.retryProblem(r.status, r.requestJson != null);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.CONFLICT, "NOT_RETRYABLE");
        CreateJournalEntryRequest req;
        try {
            req = json.readValue(r.requestJson, CreateJournalEntryRequest.class);
        } catch (Exception e) {
            throw new HandyFlowException("The stored journal could not be read: " + e.getMessage(),
                    HttpStatus.CONFLICT, "NOT_RETRYABLE");
        }
        try {
            JournalEntryResponse posted = postOnceOrRecord(tenantId, r.sourceKey, req);
            jdbc.update("""
                    UPDATE acc_posting_failures SET resolved_by = ?, resolution_note = 'Retried and posted'
                     WHERE id = ? AND tenant_id = ? AND journal_entry_id = ?""", by, id, tenantId.getValue(), posted.id());
            log.info("[Accounting] Posting retried and posted: source={} journal={} by={}", r.sourceKey, posted.entryNumber(), by);
        } catch (RuntimeException e) {
            throw new HandyFlowException("Still could not post: " + PostingFailureRules.reason(e),
                    HttpStatus.CONFLICT, "POSTING_FAILED");
        }
        return toResponse(find(tenantId, id));
    }

    /** Closes a failure that should not be posted, with the reason; the ledger is not changed. */
    public PostingFailureResponse dismiss(TenantId tenantId, UUID id, String note, UUID by) {
        Row r = find(tenantId, id);
        String problem = PostingFailureRules.dismissProblem(r.status, note);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "NOT_DISMISSIBLE");
        jdbc.update("""
                UPDATE acc_posting_failures SET status = 'DISMISSED', resolved_at = now(), resolved_by = ?, resolution_note = ?
                 WHERE id = ? AND tenant_id = ? AND status = 'OPEN'""", by, note.trim(), id, tenantId.getValue());
        log.info("[Accounting] Posting problem dismissed: source={} by={} note={}", r.sourceKey, by, note.trim());
        return toResponse(find(tenantId, id));
    }

    private Row find(TenantId tenantId, UUID id) {
        return jdbc.query("SELECT * FROM acc_posting_failures WHERE id = ? AND tenant_id = ?", this::rawRow, id, tenantId.getValue())
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("PostingFailure", id.toString()));
    }

    private record Row(UUID id, String sourceKey, String summary, String requestJson, String reason, String status,
                       int attempts, Instant firstFailedAt, Instant lastFailedAt, Instant resolvedAt, UUID resolvedBy,
                       String resolutionNote, UUID journalEntryId) {}

    private Row rawRow(ResultSet rs, int n) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getString("source_key"), rs.getString("summary"),
                rs.getString("request_json"), rs.getString("reason"), rs.getString("status"), rs.getInt("attempts"),
                instant(rs, "first_failed_at"), instant(rs, "last_failed_at"), instant(rs, "resolved_at"),
                rs.getObject("resolved_by", UUID.class), rs.getString("resolution_note"),
                rs.getObject("journal_entry_id", UUID.class));
    }

    private PostingFailureResponse row(ResultSet rs, int n) throws SQLException {
        return toResponse(rawRow(rs, n));
    }

    private PostingFailureResponse toResponse(Row r) {
        return new PostingFailureResponse(r.id, r.sourceKey, PostingFailureRules.module(r.sourceKey), r.summary, r.reason,
                r.status, r.attempts, "OPEN".equals(r.status) && r.requestJson != null, r.firstFailedAt, r.lastFailedAt,
                r.resolvedAt, r.resolvedBy, r.resolutionNote, r.journalEntryId);
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t != null ? t.toInstant() : null;
    }
}
