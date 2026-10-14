package za.co.handyflow.platform.accounting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.dto.PeriodLockResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The books' lock date (finance patch F5, 2026-10-14). AccountingService checks it before creating or posting any journal,
 * so it holds for every caller: the Journal Entries screen, reversals, bank reconciliation and every module's postings.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeriodLockService {

    private final JdbcTemplate jdbc;

    public LocalDate lockedThrough(TenantId tenantId) {
        return jdbc.query("SELECT locked_through FROM acc_lock_dates WHERE tenant_id = ?",
                        (rs, n) -> {
                            Date d = rs.getDate(1);
                            return d != null ? d.toLocalDate() : null;
                        }, tenantId.getValue())
                .stream().findFirst().orElse(null);
    }

    /** Refuses (409 PERIOD_LOCKED) a journal dated on or before the lock date. */
    public void requireOpen(TenantId tenantId, LocalDate entryDate) {
        String problem = PeriodLockRules.postProblem(entryDate, lockedThrough(tenantId));
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.CONFLICT, "PERIOD_LOCKED");
    }

    @Transactional(readOnly = true)
    public PeriodLockResponse get(TenantId tenantId) {
        var head = jdbc.query("SELECT locked_through, updated_by, updated_at FROM acc_lock_dates WHERE tenant_id = ?",
                (rs, n) -> new Object[] { rs.getDate(1), rs.getObject(2, UUID.class), rs.getTimestamp(3) },
                tenantId.getValue()).stream().findFirst().orElse(null);
        List<PeriodLockResponse.Event> history = jdbc.query("""
                SELECT action, from_date, to_date, note, by_user, at FROM acc_lock_date_events
                 WHERE tenant_id = ? ORDER BY at DESC LIMIT 100""",
                (rs, n) -> new PeriodLockResponse.Event(rs.getString(1), local(rs.getDate(2)), local(rs.getDate(3)),
                        rs.getString(4), rs.getObject(5, UUID.class), rs.getTimestamp(6).toInstant()),
                tenantId.getValue());
        if (head == null) return new PeriodLockResponse(null, null, null, history);
        return new PeriodLockResponse(local((Date) head[0]), (UUID) head[1],
                head[2] != null ? ((Timestamp) head[2]).toInstant() : null, history);
    }

    /** Closes the books up to and including {@code newDate}. */
    @Transactional
    public PeriodLockResponse lock(TenantId tenantId, LocalDate newDate, String note, UUID by) {
        LocalDate current = lockedThroughForUpdate(tenantId);
        Long drafts = newDate == null ? 0L : jdbc.queryForObject("""
                SELECT count(*) FROM acc_journal_entries
                 WHERE tenant_id = ? AND status = 'DRAFT' AND deleted_at IS NULL AND entry_date <= ?""",
                Long.class, tenantId.getValue(), Date.valueOf(newDate));
        String problem = PeriodLockRules.lockProblem(newDate, current, LocalDate.now(), drafts != null ? drafts : 0);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "LOCK_REFUSED");
        save(tenantId, newDate, by);
        event(tenantId, "LOCK", current, newDate, trimmed(note), by);
        log.info("[Accounting] Books closed through {} (was {}) tenant={} by={}", newDate, current, tenantId, by);
        return get(tenantId);
    }

    /** Moves the lock date back to {@code newDate} (null opens everything), with the reason. */
    @Transactional
    public PeriodLockResponse reopen(TenantId tenantId, LocalDate newDate, String reason, UUID by) {
        LocalDate current = lockedThroughForUpdate(tenantId);
        String problem = PeriodLockRules.reopenProblem(newDate, current, reason);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "REOPEN_REFUSED");
        save(tenantId, newDate, by);
        event(tenantId, "REOPEN", current, newDate, reason.trim(), by);
        log.warn("[Accounting] Books REOPENED from {} to {} tenant={} by={} reason={}", current, newDate, tenantId, by, reason.trim());
        return get(tenantId);
    }

    private LocalDate lockedThroughForUpdate(TenantId tenantId) {
        jdbc.update("INSERT INTO acc_lock_dates (tenant_id, locked_through) VALUES (?, NULL) ON CONFLICT (tenant_id) DO NOTHING",
                tenantId.getValue());
        return jdbc.query("SELECT locked_through FROM acc_lock_dates WHERE tenant_id = ? FOR UPDATE",
                (rs, n) -> local(rs.getDate(1)), tenantId.getValue()).stream().findFirst().orElse(null);
    }

    private void save(TenantId tenantId, LocalDate date, UUID by) {
        jdbc.update("UPDATE acc_lock_dates SET locked_through = ?, updated_by = ?, updated_at = now() WHERE tenant_id = ?",
                date != null ? Date.valueOf(date) : null, by, tenantId.getValue());
    }

    private void event(TenantId tenantId, String action, LocalDate from, LocalDate to, String note, UUID by) {
        jdbc.update("INSERT INTO acc_lock_date_events (tenant_id, action, from_date, to_date, note, by_user) VALUES (?, ?, ?, ?, ?, ?)",
                tenantId.getValue(), action, from != null ? Date.valueOf(from) : null, to != null ? Date.valueOf(to) : null, note, by);
    }

    private static String trimmed(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > 500 ? t.substring(0, 500) : t;
    }

    private static LocalDate local(Date d) {
        return d != null ? d.toLocalDate() : null;
    }
}
