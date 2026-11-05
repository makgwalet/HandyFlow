package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The delay queue for recurring invoices held back by the hard invoice cap. Rows are never deleted. */
@Component
@RequiredArgsConstructor
public class RecurringInvoiceQueue {

    private final JdbcTemplate jdbc;

    public record Entry(UUID id, UUID tenantId, UUID scheduleId, String scheduleTitle, LocalDate scheduledFor, LocalDate periodStart,
                        LocalDate periodEnd, String status, java.time.Instant queuedAt, int attempts, java.time.Instant lastAttemptAt,
                        String blockedReason, java.time.Instant escalatedAt, UUID invoiceId, java.time.Instant issuedAt) {}

    /** Entry plus what the screen needs. */
    public record View(Entry entry, long daysDelayed, boolean overdue) {}

    /** @return false when this occurrence is already queued (nothing is added twice) */
    public boolean enqueue(TenantId tenantId, UUID scheduleId, LocalDate scheduledFor, LocalDate periodStart, LocalDate periodEnd) {
        return jdbc.update("""
                INSERT INTO recurring_invoice_queue (tenant_id, schedule_id, scheduled_for, period_start, period_end)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (schedule_id, scheduled_for) DO NOTHING""",
                tenantId.getValue(), scheduleId, scheduledFor, periodStart, periodEnd) > 0;
    }

    public boolean hasQueued(TenantId tenantId) {
        Boolean b = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM recurring_invoice_queue WHERE tenant_id = ? AND status = 'QUEUED')",
                Boolean.class, tenantId.getValue());
        return Boolean.TRUE.equals(b);
    }

    /** Waiting occurrences, oldest first. */
    public List<UUID> queuedIds() {
        return jdbc.queryForList("SELECT id FROM recurring_invoice_queue WHERE status = 'QUEUED' ORDER BY scheduled_for, queued_at, id", UUID.class);
    }

    /** Locks the row for this transaction. Empty when it is no longer waiting, so it can never be issued twice. */
    public Optional<Entry> lockQueued(UUID id) {
        return jdbc.query(SELECT + " WHERE q.id = ? AND q.status = 'QUEUED' FOR UPDATE OF q", MAPPER, id).stream().findFirst();
    }

    public void attempted(UUID id, String blockedReason) {
        jdbc.update("UPDATE recurring_invoice_queue SET attempts = attempts + 1, last_attempt_at = now(), blocked_reason = ? WHERE id = ?",
                blockedReason, id);
    }

    public void issued(UUID id, UUID invoiceId) {
        jdbc.update("""
                UPDATE recurring_invoice_queue SET status = 'ISSUED', invoice_id = ?, issued_at = now(), attempts = attempts + 1,
                       last_attempt_at = now(), blocked_reason = NULL WHERE id = ? AND status = 'QUEUED'""", invoiceId, id);
    }

    public void escalated(UUID id) { jdbc.update("UPDATE recurring_invoice_queue SET escalated_at = now() WHERE id = ?", id); }

    /** Waiting occurrences that have been waiting longer than the limit and have not yet been escalated. */
    public List<Entry> needingEscalation(LocalDate today, int maxDelayDays) {
        return jdbc.query(SELECT + " WHERE q.status = 'QUEUED' AND q.escalated_at IS NULL AND q.scheduled_for < ? ORDER BY q.scheduled_for",
                MAPPER, today.minusDays(Math.max(0, maxDelayDays)));
    }

    /** Waiting occurrences for the screen, plus those issued in the last 30 days so a late invoice can be traced. */
    public List<View> list(TenantId tenantId, LocalDate today, int maxDelayDays) {
        return jdbc.query(SELECT + " WHERE q.tenant_id = ? AND (q.status = 'QUEUED' OR q.issued_at > now() - interval '30 days') "
                        + "ORDER BY (q.status = 'QUEUED') DESC, q.scheduled_for, q.queued_at", MAPPER, tenantId.getValue())
                .stream().map(e -> new View(e, "QUEUED".equals(e.status()) ? RecurringQueueRules.daysDelayed(e.scheduledFor(), today) : 0,
                        "QUEUED".equals(e.status()) && RecurringQueueRules.overdue(e.scheduledFor(), today, maxDelayDays))).toList();
    }

    private static final String SELECT = """
            SELECT q.id, q.tenant_id, q.schedule_id, COALESCE(s.title, '') AS title, q.scheduled_for, q.period_start, q.period_end, q.status,
                   q.queued_at, q.attempts, q.last_attempt_at, q.blocked_reason, q.escalated_at, q.invoice_id, q.issued_at
              FROM recurring_invoice_queue q LEFT JOIN recurring_schedules s ON s.id = q.schedule_id""";

    private static final org.springframework.jdbc.core.RowMapper<Entry> MAPPER = (rs, n) -> new Entry(
            rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getObject("schedule_id", UUID.class), rs.getString("title"),
            rs.getObject("scheduled_for", LocalDate.class), rs.getObject("period_start", LocalDate.class), rs.getObject("period_end", LocalDate.class),
            rs.getString("status"), ts(rs.getTimestamp("queued_at")), rs.getInt("attempts"), ts(rs.getTimestamp("last_attempt_at")),
            rs.getString("blocked_reason"), ts(rs.getTimestamp("escalated_at")), rs.getObject("invoice_id", UUID.class), ts(rs.getTimestamp("issued_at")));

    private static java.time.Instant ts(java.sql.Timestamp t) { return t == null ? null : t.toInstant(); }
}
