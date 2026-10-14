package za.co.handyflow.platform.accounting.application.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.internal.RecurringJournalRules.Line;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.accounting.dto.RecurringJournalDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Recurring journals, accruals and prepayments (finance F17). Each due occurrence becomes a DRAFT journal in the name of
 * the person who set the schedule up, so a different person must post it (the usual maker-checker rule) and the month-end
 * checklist counts it until they do. An accrual also makes a draft reversal dated the first of the next month. A date in a
 * locked period is not made: the schedule stops at that occurrence and says why until the period is reopened.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringJournalService {

    private final JdbcTemplate jdbc;
    private final AccountingService accounting;
    private final ObjectMapper mapper;
    private final PlatformTransactionManager txManager;

    // ── reading ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Response> list(TenantId tenantId) {
        return jdbc.query("SELECT id FROM acc_recurring_journals WHERE tenant_id = ? ORDER BY status, name",
                (rs, n) -> rs.getObject(1, UUID.class), tenantId.getValue()).stream().map(id -> get(tenantId, id)).toList();
    }

    @Transactional(readOnly = true)
    public Response get(TenantId tenantId, UUID id) {
        Row r = row(tenantId, id);
        List<RunSummary> runs = jdbc.query("""
                SELECT run_index, run_date, journal_id, reversal_journal_id, amount FROM acc_recurring_journal_runs
                 WHERE tenant_id = ? AND recurring_id = ? ORDER BY run_index DESC LIMIT 24""",
                (rs, n) -> new RunSummary(rs.getInt(1), rs.getDate(2).toLocalDate(), rs.getObject(3, UUID.class),
                        rs.getObject(4, UUID.class), rs.getBigDecimal(5)), tenantId.getValue(), id);
        List<LocalDate> upcoming = "ACTIVE".equals(r.status)
                ? RecurringJournalRules.upcoming(r.start, r.frequency, r.runsDone, r.runsTotal, r.end, 6) : List.of();
        return new Response(r.id, r.name, r.kind, r.frequency, r.start, r.end, r.runsTotal, r.runsDone, r.status, r.description,
                r.reference, readLines(r.linesJson), r.total, r.expense, r.prepaid,
                upcoming.isEmpty() ? null : upcoming.get(0), upcoming, r.lastError, runs);
    }

    // ── changing ───────────────────────────────────────────────────────────

    @Transactional
    public Response create(TenantId tenantId, CreateRequest req, UUID by) {
        List<Line> lines = toLines(req.lines());
        String problem = RecurringJournalRules.createProblem(req.name(), req.kind(), req.frequency(), req.startDate(), req.endDate(),
                req.runs(), lines, req.totalAmount(), req.expenseAccountId(), req.prepaidAccountId());
        if (problem != null) throw new IllegalArgumentException(problem);
        if (req.description() == null || req.description().isBlank()) throw new IllegalArgumentException("Enter the journal description.");
        UUID id = UUID.randomUUID();
        boolean prepay = "PREPAYMENT".equals(req.kind());
        jdbc.update("""
                INSERT INTO acc_recurring_journals (id, tenant_id, name, kind, frequency, start_date, end_date, runs_total, description,
                       reference, lines, total_amount, expense_account_id, prepaid_account_id, created_by)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                id, tenantId.getValue(), req.name().trim(), req.kind(), req.frequency(), Date.valueOf(req.startDate()),
                req.endDate() == null ? null : Date.valueOf(req.endDate()), req.runs(), req.description().trim(), req.reference(),
                prepay ? null : writeLines(req.lines()), prepay ? req.totalAmount() : null,
                prepay ? req.expenseAccountId() : null, prepay ? req.prepaidAccountId() : null, by);
        return get(tenantId, id);
    }

    @Transactional
    public Response setStatus(TenantId tenantId, UUID id, String status) {
        Row r = row(tenantId, id);
        if ("ENDED".equals(r.status)) throw new IllegalStateException("This schedule has ended.");
        if (!"ACTIVE".equals(status) && !"PAUSED".equals(status) && !"ENDED".equals(status)) throw new IllegalArgumentException("Unknown status.");
        jdbc.update("UPDATE acc_recurring_journals SET status = ?, updated_at = now() WHERE id = ? AND tenant_id = ?", status, id, tenantId.getValue());
        return get(tenantId, id);
    }

    // ── making the journals ────────────────────────────────────────────────

    /**
     * Makes every occurrence due on or before {@code today} (at most 12) for one schedule. Used by the daily job and "Run now".
     * Each occurrence is its own transaction, so one that fails (a locked period, a closed account) leaves the earlier ones made.
     */
    public RunResult runDue(TenantId tenantId, UUID id, LocalDate today) {
        Row r = row(tenantId, id);
        if (!"ACTIVE".equals(r.status)) return new RunResult(0, List.of());
        List<Integer> due = RecurringJournalRules.dueIndexes(r.start, r.frequency, r.runsDone, r.runsTotal, r.end, today);
        TransactionTemplate each = new TransactionTemplate(txManager);
        each.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        int made = 0;
        List<String> problems = new ArrayList<>();
        for (int idx : due) {
            try {
                each.executeWithoutResult(status -> makeOccurrence(tenantId, r, idx));
                made++;
            } catch (HandyFlowException | IllegalArgumentException | IllegalStateException e) {
                problems.add(RecurringJournalRules.runDate(r.start, r.frequency, idx) + ": " + e.getMessage());
                break;
            }
        }
        jdbc.update("UPDATE acc_recurring_journals SET last_error = ?, updated_at = now() WHERE id = ?",
                problems.isEmpty() ? null : problems.get(0), id);
        endIfFinished(tenantId, id);
        return new RunResult(made, problems);
    }

    /** Schedules with something due, across tenants (tenant id, schedule id). */
    @Transactional(readOnly = true)
    public List<UUID[]> dueSchedules(LocalDate today) {
        List<UUID[]> out = new ArrayList<>();
        jdbc.query("SELECT id, tenant_id, start_date, frequency, runs_done FROM acc_recurring_journals WHERE status = 'ACTIVE'", rs -> {
            LocalDate start = rs.getDate(3).toLocalDate();
            LocalDate next = RecurringJournalRules.runDate(start, rs.getString(4), rs.getInt(5));
            if (!next.isAfter(today)) out.add(new UUID[] { rs.getObject(2, UUID.class), rs.getObject(1, UUID.class) });
        });
        return out;
    }

    private void makeOccurrence(TenantId tenantId, Row r, int index) {
        LocalDate date = RecurringJournalRules.runDate(r.start, r.frequency, index);
        List<Line> lines;
        BigDecimal amount = null;
        if ("PREPAYMENT".equals(r.kind)) {
            amount = RecurringJournalRules.spread(r.total, r.runsTotal).get(index);
            lines = RecurringJournalRules.prepaymentLines(r.expense, r.prepaid, amount, r.description);
        } else {
            lines = toLines(readLines(r.linesJson));
        }
        String type = "RECURRING".equals(r.kind) ? "MANUAL" : "ADJUSTMENT";
        String label = r.description + " (" + r.name + ", " + date + ")";
        JournalEntryResponse main = accounting.createJournalEntry(tenantId, request(date, label, r.reference, type, lines), r.createdBy);
        UUID reversalId = null;
        if ("ACCRUAL".equals(r.kind)) {
            LocalDate back = RecurringJournalRules.reversalDate(date);
            reversalId = accounting.createJournalEntry(tenantId,
                    request(back, "Reversal of accrual: " + label, r.reference, type, RecurringJournalRules.reversal(lines)), r.createdBy).id();
        }
        jdbc.update("""
                INSERT INTO acc_recurring_journal_runs (id, tenant_id, recurring_id, run_index, run_date, journal_id, reversal_journal_id, amount)
                VALUES (?,?,?,?,?,?,?,?)""", UUID.randomUUID(), tenantId.getValue(), r.id, index, Date.valueOf(date), main.id(), reversalId, amount);
        jdbc.update("UPDATE acc_recurring_journals SET runs_done = ?, last_run_at = ?, updated_at = now() WHERE id = ?",
                index + 1, Timestamp.from(java.time.Instant.now()), r.id);
    }

    private void endIfFinished(TenantId tenantId, UUID id) {
        Row r = row(tenantId, id);
        LocalDate next = RecurringJournalRules.runDate(r.start, r.frequency, r.runsDone);
        if (RecurringJournalRules.finished(r.runsDone, r.runsTotal, next, r.end)) {
            jdbc.update("UPDATE acc_recurring_journals SET status = 'ENDED', updated_at = now() WHERE id = ?", id);
        }
    }

    // ── plumbing ───────────────────────────────────────────────────────────

    private static CreateJournalEntryRequest request(LocalDate date, String description, String reference, String type, List<Line> lines) {
        return new CreateJournalEntryRequest(date, description, reference, type, lines.stream()
                .map(l -> new CreateJournalEntryRequest.JournalLineRequest(l.accountId(), l.description(), l.debit(), l.credit())).toList());
    }

    private static List<Line> toLines(List<LineInput> in) {
        if (in == null) return List.of();
        return in.stream().map(l -> new Line(l.accountId(), l.description(), l.debit(), l.credit())).toList();
    }

    private List<LineInput> readLines(String json) {
        if (json == null) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<List<LineInput>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("The saved journal lines could not be read.", e);
        }
    }

    private String writeLines(List<LineInput> lines) {
        try {
            return mapper.writeValueAsString(lines);
        } catch (Exception e) {
            throw new IllegalArgumentException("The journal lines could not be saved.");
        }
    }

    private Row row(TenantId tenantId, UUID id) {
        return jdbc.query("""
                SELECT id, name, kind, frequency, start_date, end_date, runs_total, runs_done, status, description, reference, lines,
                       total_amount, expense_account_id, prepaid_account_id, last_error, created_by
                  FROM acc_recurring_journals WHERE id = ? AND tenant_id = ?""",
                (rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getDate(5).toLocalDate(), rs.getDate(6) == null ? null : rs.getDate(6).toLocalDate(),
                        (Integer) rs.getObject(7), rs.getInt(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12),
                        rs.getBigDecimal(13), rs.getObject(14, UUID.class), rs.getObject(15, UUID.class), rs.getString(16),
                        rs.getObject(17, UUID.class)), id, tenantId.getValue())
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("RecurringJournal", id.toString()));
    }

    private record Row(UUID id, String name, String kind, String frequency, LocalDate start, LocalDate end, Integer runsTotal,
                       int runsDone, String status, String description, String reference, String linesJson, BigDecimal total,
                       UUID expense, UUID prepaid, String lastError, UUID createdBy) {}
}
