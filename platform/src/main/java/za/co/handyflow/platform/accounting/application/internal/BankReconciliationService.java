package za.co.handyflow.platform.accounting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.internal.BankReconRules.Line;
import za.co.handyflow.platform.accounting.application.internal.BankReconRules.Rule;
import za.co.handyflow.platform.accounting.domain.model.AccBankAccount;
import za.co.handyflow.platform.accounting.domain.model.AccBankReconciliation;
import za.co.handyflow.platform.accounting.domain.model.AccBankRule;
import za.co.handyflow.platform.accounting.domain.model.AccBankTransaction;
import za.co.handyflow.platform.accounting.domain.repository.AccBankAccountRepository;
import za.co.handyflow.platform.accounting.domain.repository.AccBankReconciliationRepository;
import za.co.handyflow.platform.accounting.domain.repository.AccBankRuleRepository;
import za.co.handyflow.platform.accounting.domain.repository.AccBankTransactionRepository;
import za.co.handyflow.platform.accounting.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Bank reconciliation report, sign-off and matching rules (finance patch F12, 2026-10-14). See {@link BankReconRules}
 * for the arithmetic and the sign-off rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankReconciliationService {

    private static final String SIGNOFF = "ACCOUNTING_RECON_SIGNOFF";
    private static final int MAX_APPLY = 200;

    private final AccBankAccountRepository        bankAccountRepo;
    private final AccBankTransactionRepository    txRepo;
    private final AccBankRuleRepository           ruleRepo;
    private final AccBankReconciliationRepository recRepo;
    private final AccountingService               accountingService;
    private final JdbcTemplate                    jdbc;

    // ── Report ────────────────────────────────────────────────────────────────

    /** The statement against the ledger as at {@code asAt} (today when empty), with the items only on one side. */
    @Transactional(readOnly = true)
    public BankReconciliationReport report(TenantId tenantId, UUID bankAccountId, LocalDate asAt) {
        AccBankAccount bank = bank(tenantId, bankAccountId);
        LocalDate date = asAt != null ? asAt : LocalDate.now();
        return build(tenantId, bank, date, null);
    }

    private BankReconciliationReport build(TenantId tenantId, AccBankAccount bank, LocalDate asAt, UUID signer) {
        UUID glId = linkedAccount(bank);
        UUID bankAccountId = bank.getId();
        LocalDate today = LocalDate.now();

        List<AccBankTransaction> latest = txRepo.findLatestUpTo(tenantId, bankAccountId, asAt, PageRequest.of(0, 1));
        BigDecimal statementBalance = latest.isEmpty() || latest.get(0).getBalanceAfter() == null
                ? BigDecimal.ZERO : latest.get(0).getBalanceAfter();

        BigDecimal bookBalance = jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.debit_amount - l.credit_amount), 0)
                  FROM acc_journal_lines l JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                 WHERE e.tenant_id = ? AND l.account_id = ? AND e.status IN ('POSTED', 'REVERSED')
                   AND e.deleted_at IS NULL AND e.entry_date <= ?""",
                BigDecimal.class, tenantId.getValue(), glId, java.sql.Date.valueOf(asAt));

        List<AccBankTransaction> unmatched = txRepo.findUnreconciledUpTo(tenantId, bankAccountId, asAt);
        List<BankReconciliationReport.StatementItem> statementOnly = new ArrayList<>();
        BigDecimal statementOnlyNet = BigDecimal.ZERO;
        for (AccBankTransaction t : unmatched) {
            BigDecimal signed = "CREDIT".equals(t.getTransactionType()) ? t.getAmount() : t.getAmount().negate();
            statementOnlyNet = statementOnlyNet.add(signed);
            statementOnly.add(new BankReconciliationReport.StatementItem(t.getId(), t.getTransactionDate(),
                    t.getDescription(), t.getReference(), signed, ChronoUnit.DAYS.between(t.getTransactionDate(), today)));
        }

        List<BankReconciliationReport.BookItem> booksOnly = jdbc.query("""
                SELECT l.id, e.id AS entry_id, e.entry_number, e.entry_date, COALESCE(l.description, e.description) AS descr,
                       l.debit_amount - l.credit_amount AS amt
                  FROM acc_journal_lines l JOIN acc_journal_entries e ON e.id = l.journal_entry_id
                 WHERE e.tenant_id = ? AND l.account_id = ? AND e.status IN ('POSTED', 'REVERSED')
                   AND e.deleted_at IS NULL AND e.entry_date <= ? AND l.debit_amount - l.credit_amount <> 0
                   AND NOT EXISTS (SELECT 1 FROM acc_bank_transactions t WHERE t.journal_line_id = l.id)
                 ORDER BY e.entry_date, e.entry_number""",
                (rs, i) -> {
                    LocalDate d = rs.getDate("entry_date").toLocalDate();
                    return new BankReconciliationReport.BookItem(
                            (UUID) rs.getObject("id"), (UUID) rs.getObject("entry_id"), rs.getString("entry_number"), d,
                            rs.getString("descr"), rs.getBigDecimal("amt"), ChronoUnit.DAYS.between(d, today));
                },
                tenantId.getValue(), glId, java.sql.Date.valueOf(asAt));
        BigDecimal bookOnlyNet = booksOnly.stream().map(BankReconciliationReport.BookItem::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal difference = BankReconRules.difference(statementBalance, bookBalance, statementOnlyNet, bookOnlyNet);

        LocalDate signedThrough = recRepo.signedOffThrough(tenantId, bankAccountId);
        LocalDate after = signedThrough != null ? signedThrough : LocalDate.of(1900, 1, 1);
        Map<UUID, String> ruleNames = new HashMap<>();
        ruleRepo.findAllForTenant(tenantId).forEach(r -> ruleNames.put(r.getId(), r.getName()));
        List<BankReconciliationReport.ReviewItem> review = txRepo.findBookedFromLines(tenantId, bankAccountId, after, asAt).stream()
                .map(t -> new BankReconciliationReport.ReviewItem(t.getId(), t.getTransactionDate(), t.getDescription(),
                        "CREDIT".equals(t.getTransactionType()) ? t.getAmount() : t.getAmount().negate(),
                        t.getReconcileKind(), t.getRuleId(), t.getRuleId() == null ? null : ruleNames.get(t.getRuleId()),
                        t.getReconciledBy()))
                .toList();

        String blocker = BankReconRules.signOffProblem(statementOnly.size(), difference, null, Set.of(), 0);
        return new BankReconciliationReport(bankAccountId, bank.getAccountName(), asAt,
                BankReconRules.money(statementBalance), BankReconRules.money(bookBalance),
                BankReconRules.money(statementOnlyNet), BankReconRules.money(bookOnlyNet),
                difference, BankReconRules.balanced(difference), signedThrough, blocker,
                statementOnly, booksOnly, review);
    }

    // ── Sign-off ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BankReconciliationResponse> history(TenantId tenantId, UUID bankAccountId) {
        bank(tenantId, bankAccountId);
        return recRepo.findByAccount(tenantId, bankAccountId).stream().map(this::toResponse).toList();
    }

    /**
     * Signs off the account as at {@code periodEnd}: every statement line up to then is matched, the ledger agrees with the
     * statement, and the signer did not make the journals being signed off unless nobody else can sign. Afterwards no
     * statement line dated on or before that date can be added.
     */
    @Transactional
    public BankReconciliationResponse signOff(TenantId tenantId, UUID bankAccountId, LocalDate periodEnd, String note, UUID signer) {
        AccBankAccount bank = bank(tenantId, bankAccountId);
        LocalDate signedThrough = recRepo.signedOffThrough(tenantId, bankAccountId);
        String period = BankReconRules.periodProblem(periodEnd, LocalDate.now(), signedThrough);
        if (period != null) throw new HandyFlowException(period, HttpStatus.BAD_REQUEST, "RECON_PERIOD_INVALID");

        BankReconciliationReport r = build(tenantId, bank, periodEnd, signer);
        Set<UUID> makers = new HashSet<>();
        r.bookedFromLines().forEach(i -> { if (i.reconciledBy() != null) makers.add(i.reconciledBy()); });
        long others = signer == null ? 0 : otherSigners(tenantId, signer);
        String problem = BankReconRules.signOffProblem(r.onStatementOnly().size(), r.difference(), signer, makers, others);
        if (problem != null) {
            boolean selfReview = r.onStatementOnly().isEmpty() && r.balanced();
            throw new HandyFlowException(problem, selfReview ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST,
                    selfReview ? "SAME_PERSON" : "RECON_NOT_READY");
        }
        if (BankReconRules.soleSigner(signer, makers, others)) {
            log.warn("[Accounting] Bank reconciliation signed off by the person who made its journals; nobody else holds {}: tenant={} bank={} user={}",
                    SIGNOFF, tenantId, bankAccountId, signer);
        }
        AccBankReconciliation rec = AccBankReconciliation.signOff(tenantId, bankAccountId, periodEnd,
                r.statementBalance(), r.bookBalance(), r.inBooksOnly().size(), r.bookOnlyNet(), r.difference(), note, signer);
        recRepo.save(rec);
        log.info("Bank reconciliation signed off bank={} through={} by={}", bankAccountId, periodEnd, signer);
        return toResponse(rec);
    }

    /** Reopens the latest sign-off (earlier ones stay until the later ones are reopened), with a reason. */
    @Transactional
    public BankReconciliationResponse reopen(TenantId tenantId, UUID id, String reason, UUID by) {
        if (reason == null || reason.trim().length() < 5)
            throw new HandyFlowException("Say why the sign-off is reopened (at least 5 characters)", HttpStatus.BAD_REQUEST, "REOPEN_REASON_REQUIRED");
        AccBankReconciliation rec = recRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("BankReconciliation", id.toString()));
        LocalDate latest = recRepo.signedOffThrough(tenantId, rec.getBankAccountId());
        if (!"SIGNED_OFF".equals(rec.getStatus()))
            throw new HandyFlowException("This sign-off is already reopened", HttpStatus.CONFLICT, "ALREADY_REOPENED");
        if (latest != null && rec.getPeriodEnd().isBefore(latest))
            throw new HandyFlowException("Reopen the later sign-off (through " + latest + ") first", HttpStatus.CONFLICT, "NOT_LATEST");
        rec.reopen(by, reason.trim());
        recRepo.save(rec);
        log.info("Bank reconciliation reopened id={} bank={} by={}", id, rec.getBankAccountId(), by);
        return toResponse(rec);
    }

    // ── Matching rules ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BankRuleResponse> rules(TenantId tenantId) {
        return ruleRepo.findAllForTenant(tenantId).stream().map(r -> toResponse(tenantId, r)).toList();
    }

    @Transactional
    public BankRuleResponse createRule(TenantId tenantId, BankRuleRequest req, UUID by) {
        AccBankRule rule = AccBankRule.create(tenantId, by);
        define(tenantId, rule, req);
        ruleRepo.save(rule);
        return toResponse(tenantId, rule);
    }

    @Transactional
    public BankRuleResponse updateRule(TenantId tenantId, UUID id, BankRuleRequest req) {
        AccBankRule rule = ruleRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("BankRule", id.toString()));
        define(tenantId, rule, req);
        if (req.active() != null) { if (req.active()) rule.activate(); else rule.deactivate(); }
        ruleRepo.save(rule);
        return toResponse(tenantId, rule);
    }

    /** Rules that have been used are kept (deactivated), because journals refer to them. */
    @Transactional
    public void deactivateRule(TenantId tenantId, UUID id) {
        AccBankRule rule = ruleRepo.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("BankRule", id.toString()));
        rule.deactivate();
        ruleRepo.save(rule);
    }

    private void define(TenantId tenantId, AccBankRule rule, BankRuleRequest req) {
        String problem = BankReconRules.ruleProblem(req.name(), req.direction(), req.matchText(), req.minAmount(), req.maxAmount(), req.otherAccountId());
        if (problem != null) throw new HandyFlowException(problem, HttpStatus.BAD_REQUEST, "RULE_INVALID");
        if (req.matchField() != null && !Set.of("DESCRIPTION", "REFERENCE", "EITHER").contains(req.matchField()))
            throw new HandyFlowException("Look in the description, the reference or either", HttpStatus.BAD_REQUEST, "RULE_INVALID");
        if (accountName(tenantId, req.otherAccountId()) == null)
            throw new HandyFlowException("That account does not exist or is not active", HttpStatus.BAD_REQUEST, "RULE_INVALID");
        if (req.bankAccountId() != null) bank(tenantId, req.bankAccountId());
        rule.define(req.name(), req.bankAccountId(), req.direction(), req.matchText(), req.matchField(),
                req.minAmount(), req.maxAmount(), req.otherAccountId(), req.description(),
                req.priority() != null ? req.priority() : 100);
    }

    /** What each active rule would do to the unmatched statement lines. Posts nothing. */
    @Transactional(readOnly = true)
    public List<RuleSuggestionResponse> preview(TenantId tenantId, UUID bankAccountId) {
        bank(tenantId, bankAccountId);
        List<Rule> rules = activeRules(tenantId);
        Map<UUID, String> names = new HashMap<>();
        List<RuleSuggestionResponse> out = new ArrayList<>();
        for (AccBankTransaction t : txRepo.findUnreconciledUpTo(tenantId, bankAccountId, LocalDate.now())) {
            Rule rule = BankReconRules.firstMatch(rules, lineOf(t));
            if (rule == null) continue;
            String other = names.computeIfAbsent(rule.otherAccountId(), a -> accountName(tenantId, a));
            out.add(new RuleSuggestionResponse(t.getId(), t.getTransactionDate(), t.getDescription(), t.getReference(),
                    t.getAmount(), t.getTransactionType(), rule.id(), rule.name(), rule.otherAccountId(), other));
        }
        return out;
    }

    /**
     * Books the chosen statement lines by their rules: each gets a journal (bank against the rule's account), posted at
     * once and marked as made by a rule so a reviewer sees it at sign-off. All or nothing: if one line cannot be booked
     * (for example its date is in a locked period) none are.
     */
    @Transactional
    public ApplyBankRulesResponse apply(TenantId tenantId, UUID bankAccountId, List<UUID> transactionIds, UUID by) {
        bank(tenantId, bankAccountId);
        if (transactionIds == null || transactionIds.isEmpty())
            throw new HandyFlowException("Choose the lines to book", HttpStatus.BAD_REQUEST, "NOTHING_CHOSEN");
        if (transactionIds.size() > MAX_APPLY)
            throw new HandyFlowException("Book at most " + MAX_APPLY + " lines at a time", HttpStatus.BAD_REQUEST, "TOO_MANY");
        Map<UUID, AccBankRule> byId = new HashMap<>();
        ruleRepo.findActive(tenantId).forEach(r -> byId.put(r.getId(), r));
        List<Rule> rules = activeRules(tenantId);

        int applied = 0;
        for (UUID txId : new LinkedHashSet<>(transactionIds)) {
            AccBankTransaction t = txRepo.findById(txId)
                    .filter(x -> x.getBankAccountId().equals(bankAccountId) && x.getTenantId().equals(tenantId.getValue()))
                    .orElseThrow(() -> new ResourceNotFoundException("BankTransaction", txId.toString()));
            if (t.isReconciled()) throw new HandyFlowException("\"" + t.getDescription() + "\" is already matched", HttpStatus.CONFLICT, "ALREADY_MATCHED");
            Rule rule = BankReconRules.firstMatch(rules, lineOf(t));
            if (rule == null) throw new HandyFlowException("No rule fits \"" + t.getDescription() + "\" any more", HttpStatus.CONFLICT, "NO_RULE");
            accountingService.reconcileWithNewJournal(tenantId, bankAccountId, txId,
                    new ReconcileWithNewJournalRequest(rule.otherAccountId(), byId.get(rule.id()).getDescription()), by, rule.id());
            byId.get(rule.id()).applied();
            ruleRepo.save(byId.get(rule.id()));
            applied++;
        }
        log.info("Booked {} bank lines by rule bank={} by={}", applied, bankAccountId, by);
        return new ApplyBankRulesResponse(applied);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AccBankAccount bank(TenantId tenantId, UUID bankAccountId) {
        return bankAccountRepo.findActiveById(tenantId, bankAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("BankAccount", bankAccountId.toString()));
    }

    private UUID linkedAccount(AccBankAccount bank) {
        if (bank.getAccountId() == null)
            throw new HandyFlowException("This bank account is not linked to a Chart of Accounts entry, so it cannot be reconciled",
                    HttpStatus.BAD_REQUEST, "BANK_NOT_LINKED");
        return bank.getAccountId();
    }

    private List<Rule> activeRules(TenantId tenantId) {
        return ruleRepo.findActive(tenantId).stream()
                .map(r -> new Rule(r.getId(), r.getName(), r.getBankAccountId(), r.getDirection(), r.getMatchText(),
                        r.getMatchField(), r.getMinAmount(), r.getMaxAmount(), r.getPriority(), r.isActive(), r.getOtherAccountId()))
                .toList();
    }

    private static Line lineOf(AccBankTransaction t) {
        return new Line(t.getBankAccountId(), t.getTransactionType(), t.getDescription(), t.getReference(), t.getAmount());
    }

    /** Active people, other than {@code signer}, who may sign off a reconciliation. */
    private long otherSigners(TenantId tenantId, UUID signer) {
        Long n = jdbc.queryForObject("""
                SELECT count(DISTINCT u.id)
                  FROM users u
                  JOIN user_roles ur ON ur.user_id = u.id
                  JOIN role_permissions rp ON rp.role_id = ur.role_id
                  JOIN permissions p ON p.id = rp.permission_id
                 WHERE u.tenant_id = ? AND u.status = 'ACTIVE' AND p.name = ? AND u.id <> ?""",
                Long.class, tenantId.getValue(), SIGNOFF, signer);
        return n != null ? n : 0;
    }

    private String accountName(TenantId tenantId, UUID accountId) {
        if (accountId == null) return null;
        List<String> found = jdbc.queryForList(
                "SELECT account_code || ' ' || account_name FROM acc_accounts WHERE tenant_id = ? AND id = ? AND active = true",
                String.class, tenantId.getValue(), accountId);
        return found.isEmpty() ? null : found.get(0);
    }

    private BankRuleResponse toResponse(TenantId tenantId, AccBankRule r) {
        return new BankRuleResponse(r.getId(), r.getName(), r.getBankAccountId(), r.getDirection(), r.getMatchText(),
                r.getMatchField(), r.getMinAmount(), r.getMaxAmount(), r.getOtherAccountId(),
                accountName(tenantId, r.getOtherAccountId()), r.getDescription(), r.getPriority(), r.isActive(), r.getTimesApplied());
    }

    private BankReconciliationResponse toResponse(AccBankReconciliation r) {
        return new BankReconciliationResponse(r.getId(), r.getBankAccountId(), r.getPeriodEnd(), r.getStatementBalance(),
                r.getBookBalance(), r.getUnmatchedBookCount(), r.getUnmatchedBookNet(), r.getDifference(), r.getNote(),
                r.getStatus(), r.getSignedBy(), r.getSignedAt(), r.getReopenedBy(), r.getReopenedAt(), r.getReopenReason());
    }
}
