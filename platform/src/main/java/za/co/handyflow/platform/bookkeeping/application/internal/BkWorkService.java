package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.bookkeeping.application.internal.BkWorkRules.Line;
import za.co.handyflow.platform.bookkeeping.application.internal.BkWorkRules.Rule;
import za.co.handyflow.platform.bookkeeping.domain.model.BkPeriod;
import za.co.handyflow.platform.bookkeeping.dto.BkWorkDtos.*;
import za.co.handyflow.platform.bookkeeping.dto.ReconcileBkTransactionWithNewJournalRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Bookkeeping's rules, bulk categorising, client queries and handover (finance F19). Rules only suggest: nothing is booked
 * until a person ticks lines and applies them, and the whole apply succeeds or fails together.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BkWorkService {

    private static final String ADMIN = "BOOKKEEPING_ADMIN";

    private final JdbcTemplate jdbc;
    private final BkBankTransactionService transactions;
    private final BkPeriodService periods;

    // ── rules ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<RuleResponse> rules(TenantId tenantId, UUID clientId) {
        return jdbc.query("""
                SELECT id, name, bank_account_id, direction, match_text, match_field, min_amount, max_amount, priority, active, contra_account_id
                  FROM bk_bank_rules WHERE tenant_id = ? AND client_id = ? ORDER BY priority, name""",
                (rs, n) -> new RuleResponse(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getInt(9), rs.getBoolean(10),
                        rs.getObject(11, UUID.class)), tenantId.getValue(), clientId);
    }

    @Transactional
    public List<RuleResponse> addRule(TenantId tenantId, UUID clientId, RuleRequest req, UUID by) {
        String dir = req.direction() == null ? "ANY" : req.direction();
        String problem = BkWorkRules.ruleProblem(req.name(), dir, req.text(), req.min(), req.max(), req.contraAccountId());
        if (problem != null) throw new IllegalArgumentException(problem);
        requireClient(tenantId, clientId);
        jdbc.update("""
                INSERT INTO bk_bank_rules (id, tenant_id, client_id, name, bank_account_id, direction, match_text, match_field, min_amount,
                       max_amount, priority, contra_account_id, created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                UUID.randomUUID(), tenantId.getValue(), clientId, req.name().trim(), req.bankAccountId(), dir,
                req.text() == null || req.text().isBlank() ? null : req.text().trim(),
                req.field() == null ? "EITHER" : req.field(), req.min(), req.max(), req.priority() == null ? 100 : req.priority(),
                req.contraAccountId(), by);
        return rules(tenantId, clientId);
    }

    @Transactional
    public List<RuleResponse> setRuleActive(TenantId tenantId, UUID clientId, UUID ruleId, boolean active) {
        jdbc.update("UPDATE bk_bank_rules SET active = ? WHERE id = ? AND tenant_id = ? AND client_id = ?", active, ruleId, tenantId.getValue(), clientId);
        return rules(tenantId, clientId);
    }

    @Transactional
    public List<RuleResponse> deleteRule(TenantId tenantId, UUID clientId, UUID ruleId) {
        jdbc.update("DELETE FROM bk_bank_rules WHERE id = ? AND tenant_id = ? AND client_id = ?", ruleId, tenantId.getValue(), clientId);
        return rules(tenantId, clientId);
    }

    /** Uncategorised lines with the account the first matching rule would book them to (null when no rule fits). */
    @Transactional(readOnly = true)
    public List<Suggestion> suggestions(TenantId tenantId, UUID clientId, UUID bankAccountId) {
        List<Rule> rules = jdbc.query("""
                SELECT id, name, bank_account_id, direction, match_text, match_field, min_amount, max_amount, priority, active, contra_account_id
                  FROM bk_bank_rules WHERE tenant_id = ? AND client_id = ? AND active""",
                (rs, n) -> new Rule(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getInt(9), rs.getBoolean(10), rs.getObject(11, UUID.class)),
                tenantId.getValue(), clientId);
        List<Suggestion> out = new ArrayList<>();
        jdbc.query("""
                SELECT id, transaction_date, description, reference, amount, transaction_type, bank_account_id FROM bk_bank_transactions
                 WHERE tenant_id = ? AND client_id = ? AND NOT reconciled AND (?::uuid IS NULL OR bank_account_id = ?::uuid)
                 ORDER BY transaction_date, created_at LIMIT 500""",
                rs -> {
                    Line line = new Line(rs.getObject(7, UUID.class), rs.getString(6), rs.getString(3), rs.getString(4), rs.getBigDecimal(5));
                    Rule hit = BkWorkRules.firstMatch(rules, line);
                    out.add(new Suggestion(rs.getObject(1, UUID.class), rs.getDate(2).toLocalDate(), rs.getString(3), rs.getBigDecimal(5),
                            rs.getString(6), hit == null ? null : hit.id(), hit == null ? null : hit.name(), hit == null ? null : hit.contraAccountId()));
                }, tenantId.getValue(), clientId, bankAccountId, bankAccountId);
        return out;
    }

    /**
     * Books each chosen line to the account given, as a posted two-line journal (the same path as categorising one line).
     * All or nothing: a locked period or a missing account stops the lot and names the line.
     */
    @Transactional
    public CategoriseResponse categorise(TenantId tenantId, UUID clientId, CategoriseRequest req, UUID by) {
        List<CategoriseItem> items = req.items() == null ? List.of() : req.items();
        String problem = BkWorkRules.bulkProblem(items.size());
        if (problem != null) throw new IllegalArgumentException(problem);
        Set<UUID> seen = new HashSet<>();
        for (CategoriseItem item : items) {
            if (item.transactionId() == null || item.contraAccountId() == null) throw new IllegalArgumentException("Every line needs an account.");
            if (!seen.add(item.transactionId())) throw new IllegalArgumentException("A line was chosen twice.");
        }
        int done = 0;
        for (CategoriseItem item : items) {
            try {
                transactions.reconcileWithNewJournal(tenantId, clientId, item.transactionId(), by,
                        new ReconcileBkTransactionWithNewJournalRequest(item.contraAccountId(), null));
                done++;
            } catch (RuntimeException e) {
                throw new IllegalStateException("Line " + (done + 1) + " of " + items.size() + " could not be categorised, so none were: " + e.getMessage(), e);
            }
        }
        log.info("[Bookkeeping] {} lines categorised in bulk: tenant={} client={} user={}", done, tenantId, clientId, by);
        return new CategoriseResponse(done);
    }

    // ── queries ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<QueryResponse> queries(TenantId tenantId, UUID clientId, String status) {
        return jdbc.query("""
                SELECT id, client_id, transaction_id, kind, question, status, answer, document_ref, asked_at, answered_at
                  FROM bk_client_queries WHERE tenant_id = ? AND client_id = ? AND (?::text IS NULL OR status = ?::text)
                 ORDER BY asked_at DESC LIMIT 200""",
                (rs, n) -> new QueryResponse(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getTimestamp(9).toInstant(),
                        rs.getTimestamp(10) == null ? null : rs.getTimestamp(10).toInstant(),
                        "OPEN".equals(rs.getString(6)) ? ChronoUnit.DAYS.between(rs.getTimestamp(9).toInstant(), java.time.Instant.now()) : 0),
                tenantId.getValue(), clientId, status, status);
    }

    @Transactional
    public List<QueryResponse> ask(TenantId tenantId, UUID clientId, QueryRequest req, UUID by) {
        String problem = BkWorkRules.queryProblem(req.kind(), req.question());
        if (problem != null) throw new IllegalArgumentException(problem);
        requireClient(tenantId, clientId);
        if (req.transactionId() != null) {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM bk_bank_transactions WHERE id = ? AND tenant_id = ? AND client_id = ?",
                    Integer.class, req.transactionId(), tenantId.getValue(), clientId);
            if (n == null || n == 0) throw new ResourceNotFoundException("BkBankTransaction", req.transactionId().toString());
        }
        jdbc.update("INSERT INTO bk_client_queries (id, tenant_id, client_id, transaction_id, kind, question, asked_by) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), tenantId.getValue(), clientId, req.transactionId(), req.kind(), req.question().trim(), by);
        return queries(tenantId, clientId, null);
    }

    @Transactional
    public List<QueryResponse> answer(TenantId tenantId, UUID clientId, UUID queryId, AnswerRequest req, UUID by) {
        String status = queryStatus(tenantId, clientId, queryId);
        String problem = BkWorkRules.answerProblem(status, req.answer(), req.documentRef());
        if (problem != null) throw new IllegalArgumentException(problem);
        jdbc.update("""
                UPDATE bk_client_queries SET status = 'ANSWERED', answer = ?, document_ref = ?, answered_by = ?, answered_at = now()
                 WHERE id = ? AND tenant_id = ?""", trim(req.answer()), trim(req.documentRef()), by, queryId, tenantId.getValue());
        return queries(tenantId, clientId, null);
    }

    @Transactional
    public List<QueryResponse> close(TenantId tenantId, UUID clientId, UUID queryId) {
        String problem = BkWorkRules.closeProblem(queryStatus(tenantId, clientId, queryId));
        if (problem != null) throw new IllegalArgumentException(problem);
        jdbc.update("UPDATE bk_client_queries SET status = 'CLOSED', closed_at = now() WHERE id = ? AND tenant_id = ?", queryId, tenantId.getValue());
        return queries(tenantId, clientId, null);
    }

    /** One row per active client with what is waiting, busiest first. */
    @Transactional(readOnly = true)
    public List<WorkQueueRow> workQueue(TenantId tenantId) {
        return jdbc.query("""
                SELECT c.id, c.trading_name,
                       (SELECT count(*) FROM bk_bank_transactions t WHERE t.client_id = c.id AND NOT t.reconciled),
                       (SELECT min(t.transaction_date) FROM bk_bank_transactions t WHERE t.client_id = c.id AND NOT t.reconciled),
                       (SELECT count(*) FROM bk_client_queries q WHERE q.client_id = c.id AND q.status = 'OPEN'),
                       (SELECT count(*) FROM bk_client_queries q WHERE q.client_id = c.id AND q.status = 'OPEN' AND q.kind = 'MISSING_DOCUMENT'),
                       (SELECT count(*) FROM bk_journal_entries j WHERE j.client_id = c.id AND j.status = 'DRAFT' AND j.deleted_at IS NULL)
                  FROM bk_clients c WHERE c.tenant_id = ? AND c.deleted_at IS NULL ORDER BY 3 DESC, 5 DESC, c.trading_name""",
                (rs, n) -> new WorkQueueRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3),
                        rs.getDate(4) == null ? null : rs.getDate(4).toLocalDate(), rs.getLong(5), rs.getLong(6), rs.getLong(7)),
                tenantId.getValue());
    }

    // ── handover ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<HandoverResponse> handovers(TenantId tenantId, UUID clientId) {
        return jdbc.query("""
                SELECT id, client_id, period_end, status, open_queries, note, sent_at, responded_at, response_note
                  FROM bk_handovers WHERE tenant_id = ? AND client_id = ? ORDER BY period_end DESC, sent_at DESC LIMIT 50""",
                (rs, n) -> new HandoverResponse(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getDate(3).toLocalDate(),
                        rs.getString(4), rs.getInt(5), rs.getString(6), rs.getTimestamp(7).toInstant(),
                        rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant(), rs.getString(9),
                        BkWorkRules.handoverWarnings(rs.getInt(5))), tenantId.getValue(), clientId);
    }

    @Transactional
    public List<HandoverResponse> sendHandover(TenantId tenantId, UUID clientId, HandoverRequest req, UUID by) {
        requireClient(tenantId, clientId);
        UUID t = tenantId.getValue();
        LocalDate end = req.periodEnd();
        long unreconciled = end == null ? 0 : count("SELECT count(*) FROM bk_bank_transactions WHERE tenant_id = ? AND client_id = ? AND NOT reconciled AND transaction_date <= ?",
                t, clientId, Date.valueOf(end));
        long drafts = end == null ? 0 : count("SELECT count(*) FROM bk_journal_entries WHERE tenant_id = ? AND client_id = ? AND status = 'DRAFT' AND deleted_at IS NULL AND entry_date <= ?",
                t, clientId, Date.valueOf(end));
        boolean open = count("SELECT count(*) FROM bk_handovers WHERE tenant_id = ? AND client_id = ? AND status = 'SENT'", t, clientId) > 0;
        String problem = BkWorkRules.handoverProblem(end, LocalDate.now(), unreconciled, drafts, open);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "HANDOVER_REFUSED");
        long openQueries = count("SELECT count(*) FROM bk_client_queries WHERE tenant_id = ? AND client_id = ? AND status = 'OPEN'", t, clientId);
        jdbc.update("INSERT INTO bk_handovers (id, tenant_id, client_id, period_end, open_queries, note, sent_by) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), t, clientId, Date.valueOf(end), (int) openQueries, trim(req.note()), by);
        return handovers(tenantId, clientId);
    }

    /** Accepting also closes the month the handover runs to, so nothing more is posted into it. */
    @Transactional
    public List<HandoverResponse> respond(TenantId tenantId, UUID handoverId, String action, String note, UUID by) {
        record H(UUID id, UUID clientId, LocalDate periodEnd, String status, UUID sentBy) {}
        H h = jdbc.query("SELECT id, client_id, period_end, status, sent_by FROM bk_handovers WHERE id = ? AND tenant_id = ?",
                (rs, n) -> new H(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getDate(3).toLocalDate(), rs.getString(4), rs.getObject(5, UUID.class)),
                handoverId, tenantId.getValue()).stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("BkHandover", handoverId.toString()));
        long others = othersWithAdmin(tenantId, by);
        String problem = BkWorkRules.respondProblem(h.status(), action, note, h.sentBy(), by, others);
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "HANDOVER_REFUSED");
        if (BkWorkRules.soleResponder(h.sentBy(), by, others))
            log.warn("[Bookkeeping] Handover answered by the person who sent it; nobody else holds {}: tenant={} handover={} user={}", ADMIN, tenantId, handoverId, by);
        boolean accept = "ACCEPT".equals(action);
        jdbc.update("UPDATE bk_handovers SET status = ?, responded_by = ?, responded_at = now(), response_note = ? WHERE id = ? AND tenant_id = ?",
                accept ? "ACCEPTED" : "RETURNED", by, trim(note), handoverId, tenantId.getValue());
        if (accept) {
            BkPeriod period = periods.resolveOrCreate(tenantId, h.clientId(), h.periodEnd());
            if ("OPEN".equals(period.getStatus())) periods.close(tenantId, period.getId(), by);
        }
        return handovers(tenantId, h.clientId());
    }

    // ── plumbing ───────────────────────────────────────────────────────────

    private long othersWithAdmin(TenantId tenantId, UUID user) {
        Long n = jdbc.queryForObject("""
                SELECT count(DISTINCT u.id) FROM users u JOIN user_roles ur ON ur.user_id = u.id
                  JOIN role_permissions rp ON rp.role_id = ur.role_id JOIN permissions p ON p.id = rp.permission_id
                 WHERE u.tenant_id = ? AND u.status = 'ACTIVE' AND p.name = ? AND u.id <> ?""", Long.class, tenantId.getValue(), ADMIN, user);
        return n == null ? 0 : n;
    }

    private String queryStatus(TenantId tenantId, UUID clientId, UUID queryId) {
        return jdbc.query("SELECT status FROM bk_client_queries WHERE id = ? AND tenant_id = ? AND client_id = ?",
                (rs, n) -> rs.getString(1), queryId, tenantId.getValue(), clientId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("BkClientQuery", queryId.toString()));
    }

    private void requireClient(TenantId tenantId, UUID clientId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM bk_clients WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL", Integer.class, clientId, tenantId.getValue());
        if (n == null || n == 0) throw new ResourceNotFoundException("BkClient", clientId.toString());
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
