package za.co.handyflow.platform.expenses.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Posts an approved expense claim to the accounting journal (acc_accounts /
 * acc_journal_entries / acc_journal_lines).
 * <p>
 * Its own @Component bean with @Transactional(REQUIRES_NEW), not a private
 * method on ExpensesService — same reasoning as UserRecipientResolverImpl:
 * the code this replaces already had a try/catch around this exact block,
 * with a comment stating "Approval should not fail just because accounting
 * lookup fails." That intent never actually held, for the same underlying
 * reason resolveRecipient() didn't: a caught Java exception here doesn't
 * undo Postgres's abort-until-rollback behaviour, and this was a private,
 * self-invoked method, so even an @Transactional annotation on it would
 * have been silently ignored by Spring's proxy. Confirmed by real testing,
 * not theory: a too-long entry_number here poisoned the entire
 * approveClaim() transaction and crashed the approval outright — exactly
 * the failure this class exists to prevent, most importantly for the
 * failure mode this method was originally written to guard against: a
 * tenant with no STAFF_EXPENSES-subtype account configured yet.
 * <p>
 * FIX: journal entry number was "JE-EXP-" + claimNumber. Claim numbers are
 * formatted EXP-YYYY-NNNNN (14 chars); with the "JE-EXP-" prefix (7 chars)
 * that's 21 characters into what confirmed via a live crash is a
 * VARCHAR(20) entry_number column. Shortened to "JE-" + claimNumber (17
 * chars), safely under the limit — not widening the column, since it's
 * unknown what other journal-entry formats already rely on its current
 * width elsewhere in the accountant module.
 * <p>
 * NOTE on module boundaries: this still reaches into acc_accounts /
 * acc_journal_entries / acc_journal_lines via raw JDBC from inside the
 * expenses module, same as the code it replaces — that's a pre-existing
 * cross-module data access pattern, not something introduced or fixed
 * here. The clean DDD fix would be accounting exposing a proper posting
 * facade (mirroring TenantAdminRecipients/BillingRecipients), but that's
 * a larger, separate piece of work — out of scope for closing tonight's
 * crash, and not bundled in here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpenseAccountingPoster {

    private final JdbcTemplate jdbc;

    /**
     * @return the new journal entry's id if posting succeeded, empty if
     *         any step failed — including a missing chart-of-accounts row
     *         for this tenant, which is expected to happen for tenants
     *         who haven't configured one yet. Never throws.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<UUID> postExpenseClaimJournal(TenantId tenantId, UUID claimId, String claimNumber,
                                                  String description, String category,
                                                  String employeeName, BigDecimal amount) {
        try {
            var expenseAccountRow = jdbc.queryForMap(
                    "SELECT id FROM acc_accounts WHERE tenant_id = ? AND account_code LIKE '5%' AND account_subtype = 'STAFF_EXPENSES' AND active = true LIMIT 1",
                    tenantId.getValue());
            var payableAccountRow = jdbc.queryForMap(
                    "SELECT id FROM acc_accounts WHERE tenant_id = ? AND account_code LIKE '2%' AND active = true LIMIT 1",
                    tenantId.getValue());

            UUID expenseAccountId = UUID.fromString(expenseAccountRow.get("id").toString());
            UUID payableAccountId = UUID.fromString(payableAccountRow.get("id").toString());

            UUID jeId = UUID.randomUUID();
            String jeNumber = "JE-" + claimNumber;
            LocalDate today = LocalDate.now();

            jdbc.update("""
                INSERT INTO acc_journal_entries
                (id, tenant_id, entry_number, entry_date, description, status, posted_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'POSTED', NOW(), NOW(), NOW())
                """,
                    jeId, tenantId.getValue(), jeNumber, today,
                    "Expense claim: " + description + " (" + claimNumber + ")");

            jdbc.update("""
                INSERT INTO acc_journal_lines
                (id, journal_entry_id, account_id, description, debit_amount, credit_amount, created_at)
                VALUES (?, ?, ?, ?, ?, 0, NOW())
                """,
                    UUID.randomUUID(), jeId, expenseAccountId,
                    category + " — " + employeeName, amount);

            jdbc.update("""
                INSERT INTO acc_journal_lines
                (id, journal_entry_id, account_id, description, debit_amount, credit_amount, created_at)
                VALUES (?, ?, ?, ?, 0, ?, NOW())
                """,
                    UUID.randomUUID(), jeId, payableAccountId,
                    "Payable to " + employeeName, amount);

            log.info("Posted expense journal entry {} for claim {}", jeNumber, claimNumber);
            return Optional.of(jeId);

        } catch (Exception e) {
            log.error("Failed to post expense {} to accounting: {}", claimNumber, e.getMessage());
            return Optional.empty();
        }
    }
}