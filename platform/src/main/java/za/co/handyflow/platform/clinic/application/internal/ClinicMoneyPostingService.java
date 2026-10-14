package za.co.handyflow.platform.clinic.application.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.application.PostingKeys;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.clinic.ClaimMoneyEvent;
import za.co.handyflow.platform.clinic.dto.billing.MoneyPostingDtos.*;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Takes claim money events to the general ledger (patch 0185). The module that owns the event does the posting through
 * {@link AccountingFacade}, because Accounting may not depend on Clinic (Clinic already depends on Accounting). Every event is recorded in
 * {@code clinic_money_posting} first, so nothing is lost if posting fails or waits: a scheme payment waits for a bank account, a write-off
 * posts straight away, and anything that fails keeps its reason.
 */
@Slf4j
@Service
public class ClinicMoneyPostingService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final JdbcTemplate jdbc;
    private final AccountingFacade accounting;
    private final TransactionTemplate separately;

    public ClinicMoneyPostingService(JdbcTemplate jdbc, AccountingFacade accounting, PlatformTransactionManager tm) {
        this.jdbc = jdbc;
        this.accounting = accounting;
        this.separately = new TransactionTemplate(tm);
        this.separately.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Records the event once. Returns the new posting's id, or null when the event is not one we post or was already recorded. */
    public UUID record(ClaimMoneyEvent e) {
        if (e == null || !MoneyPostingRules.handles(e.type()) || e.amount() == null || e.amount().signum() <= 0) return null;
        UUID id = UUID.randomUUID();
        LocalDate day = (e.occurredOn() == null ? java.time.Instant.now() : e.occurredOn()).atZone(SAST).toLocalDate();
        Integer n = separately.execute(s -> jdbc.update("""
            INSERT INTO clinic_money_posting (id, tenant_id, claim_id, event_type, amount, reference, occurred_on, event_key)
            VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (tenant_id, event_key) DO NOTHING""",
                id, e.tenantId().getValue(), e.claimId(), e.type(), e.amount(), blankToNull(e.reference()), Date.valueOf(day),
                MoneyPostingRules.eventKey(e.type(), e.claimId(), e.amount(), e.occurredOn())));
        return n != null && n == 1 ? id : null;
    }

    private record Row(UUID id, UUID claimId, String type, BigDecimal amount, String reference, LocalDate on, String status) {}

    /**
     * Posts one recorded event. Throws with a plain-words reason when it cannot be posted yet (no bank account, account missing);
     * nothing is changed in that case. Already-posted rows are left alone.
     */
    public void post(TenantId tenant, UUID postingId, UUID bankAccountId, UUID by) {
        separately.executeWithoutResult(s -> {
            List<Row> rows = jdbc.query("""
                SELECT id, claim_id, event_type, amount, reference, occurred_on, status FROM clinic_money_posting
                WHERE id = ? AND tenant_id = ? FOR UPDATE""",
                (rs, i) -> new Row((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getBigDecimal(4),
                        rs.getString(5), rs.getDate(6).toLocalDate(), rs.getString(7)), postingId, tenant.getValue());
            if (rows.isEmpty()) throw new ResourceNotFoundException("Money posting", postingId.toString());
            Row r = rows.get(0);
            if (MoneyPostingRules.POSTED.equals(r.status())) return;

            UUID ar = accountId(tenant, MoneyPostingRules.AR_CODE);
            UUID badDebt = accountId(tenant, MoneyPostingRules.BAD_DEBT_CODE);
            UUID bankGl = null;
            if (MoneyPostingRules.needsBank(r.type())) {
                if (bankAccountId == null) throw new IllegalStateException(MoneyPostingRules.blocker(r.type(), r.amount(), ar, badDebt, null));
                Optional<UUID> gl = accounting.resolveBankAccountGL(tenant, bankAccountId);
                bankGl = gl.orElse(null);
            }
            String blocker = MoneyPostingRules.blocker(r.type(), r.amount(), ar, badDebt, bankGl);
            if (blocker != null) throw new IllegalStateException(blocker);

            String memo = MoneyPostingRules.description(r.type(), r.claimId(), r.reference());
            var lines = MoneyPostingRules.lines(r.type(), r.amount(), ar, badDebt, bankGl).stream()
                    .map(l -> new CreateJournalEntryRequest.JournalLineRequest(l.accountId(), memo, l.debit(), l.credit())).toList();
            // Finance patch F3: one journal per posting record, so a retry after a half-finished attempt cannot post twice.
            var created = accounting.postOnce(tenant, PostingKeys.of("clinic", "money-posting", postingId),
                    new CreateJournalEntryRequest(r.on(), memo, r.claimId().toString(), "MANUAL", lines));
            jdbc.update("""
                UPDATE clinic_money_posting SET status = 'POSTED', reason = NULL, bank_account_id = ?, journal_entry_id = ?, posted_at = now(), posted_by = ?
                WHERE id = ? AND tenant_id = ?""", bankAccountId, created.id(), by, postingId, tenant.getValue());
            log.info("Posted {} {} for claim={} tenant={} journal={}", r.type(), r.amount(), r.claimId(), tenant, created.id());
        });
    }

    /** Keeps the reason a posting failed, in its own transaction so it survives the failed attempt. */
    public void markFailed(TenantId tenant, UUID postingId, String reason) {
        String why = reason == null || reason.isBlank() ? "Posting failed." : (reason.length() > 300 ? reason.substring(0, 300) : reason);
        separately.executeWithoutResult(s -> jdbc.update(
                "UPDATE clinic_money_posting SET status = 'FAILED', reason = ? WHERE id = ? AND tenant_id = ? AND status <> 'POSTED'",
                why, postingId, tenant.getValue()));
    }

    /** Money still to post: scheme payments waiting for a bank account, and anything whose posting failed. */
    public List<PostingView> waiting(TenantId tenant) {
        return jdbc.query("""
            SELECT p.id, p.claim_id, pt.full_name, p.event_type, p.amount, p.reference, p.occurred_on, p.status, p.reason
            FROM clinic_money_posting p
            LEFT JOIN clinic_claims c ON c.id = p.claim_id AND c.tenant_id = p.tenant_id
            LEFT JOIN clinic_patients pt ON pt.id = c.patient_id AND pt.tenant_id = p.tenant_id
            WHERE p.tenant_id = ? AND p.status IN ('PENDING','FAILED')
            ORDER BY p.created_at, p.id""",
            (rs, i) -> new PostingView((UUID) rs.getObject(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5),
                    rs.getString(6), rs.getDate(7).toLocalDate(), rs.getString(8), rs.getString(9), MoneyPostingRules.needsBank(rs.getString(4))),
            tenant.getValue());
    }

    /** Bank accounts a scheme payment can be posted to: active and linked to a ledger account. */
    public List<BankChoice> banks(TenantId tenant) {
        return accounting.getBankAccounts(tenant).stream().filter(b -> b.accountId() != null)
                .map(b -> new BankChoice(b.id(), (b.bankName() == null ? "" : b.bankName()) + (b.accountName() == null ? "" : " - " + b.accountName())
                        + (b.accountNumber() == null || b.accountNumber().length() < 4 ? "" : " (…" + b.accountNumber().substring(b.accountNumber().length() - 4) + ")")))
                .toList();
    }

    /** Posts the chosen items (everything waiting when none are chosen). One failing does not stop the rest; each failure keeps its reason. */
    public PostResult postWaiting(TenantId tenant, UUID bankAccountId, List<UUID> chosen, UUID by) {
        List<UUID> ids = chosen == null || chosen.isEmpty() ? waiting(tenant).stream().map(PostingView::id).toList() : chosen;
        int posted = 0;
        List<PostFailure> failed = new ArrayList<>();
        for (UUID id : ids) {
            try {
                post(tenant, id, bankAccountId, by);
                posted++;
            } catch (Exception e) {
                failed.add(new PostFailure(id, e.getMessage() == null ? "Posting failed." : e.getMessage()));
            }
        }
        return new PostResult(posted, failed);
    }

    private UUID accountId(TenantId tenant, String code) {
        return accounting.getAccounts(tenant).stream().filter(a -> code.equals(a.accountCode())).map(a -> a.id()).findFirst().orElse(null);
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
