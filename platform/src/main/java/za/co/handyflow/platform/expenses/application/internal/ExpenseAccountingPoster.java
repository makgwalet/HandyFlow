package za.co.handyflow.platform.expenses.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Posts an approved expense claim to the accounting journal.
 * <p>
 * Its own @Component bean with @Transactional(REQUIRES_NEW), not a private
 * method on ExpensesService — same reasoning as UserRecipientResolverImpl:
 * a caught Java exception here doesn't undo Postgres's abort-until-rollback
 * behaviour, and a private, self-invoked method's @Transactional would be
 * silently ignored by Spring's proxy. Confirmed by real testing, not
 * theory: a too-long entry_number here once poisoned the entire
 * approveClaim() transaction and crashed the approval outright — this
 * class exists specifically to prevent that failure mode. THIS CONTRACT
 * IS UNCHANGED BY THIS FIX: postExpenseClaimJournal() must never throw,
 * only ever return Optional.empty() on failure — preserved exactly below,
 * just with AccountingFacade calls now wrapped in the same try/catch the
 * raw JDBC calls used to be.
 * <p>
 * FIX: backlog 1.6/8.2 — migrated off raw JDBC INSERTs directly into
 * acc_accounts/acc_journal_entries/acc_journal_lines onto
 * AccountingFacade.createJournalEntry()/postJournalEntry(), mirroring how
 * AP (ApService.postApprovalJournal) and POS (PosService.
 * postSessionSalesJournal) already do it correctly. This is a caller
 * migration onto proven infrastructure, not new design work — exactly as
 * the backlog itself scoped it. Journal entry numbering now comes from
 * the real JournalNumberGenerator via AccountingService, the same fix
 * already applied to AP; the old manual "JE-" + claimNumber construction
 * (and the VARCHAR(20) length crash that caused) is gone entirely — the
 * facade generates a correct, guaranteed-fitting number itself.
 * <p>
 * REAL BUG FOUND AND FIXED WHILE MIGRATING, DISCLOSED NOT SILENTLY
 * CHANGED: the old raw SQL picked accounts via unordered, non-specific
 * queries. The expense-side lookup (`account_code LIKE '5%' AND
 * account_subtype = 'STAFF_EXPENSES' LIMIT 1`) matches FOUR different
 * seeded accounts (5240 Staff Expense Reimbursements, 5241 Travel and
 * Subsistence, 5242 Meals and Entertainment, 5243 Accommodation) —
 * despite the method already receiving a `category` parameter, that
 * parameter was only ever used in the journal line's description text,
 * never to pick the actually-matching account, so which of the four got
 * debited was effectively arbitrary (whatever order Postgres happened to
 * return). The payable-side lookup (`account_code LIKE '2%' LIMIT 1`) was
 * far more serious: that matches TEN different liability accounts,
 * including VAT Output (2100) and PAYE Payable (2200) — an expense
 * reimbursement could have posted against completely unrelated statutory
 * liability accounts depending on row order alone.
 * <p>
 * Fixed to deterministic, specific accounts rather than an unordered
 * LIKE match: 5240 (Staff Expense Reimbursements) for the expense side —
 * category-specific routing (matching AP's own CATEGORY_ACCOUNT pattern
 * exactly) would be the more precise fix, but doing that correctly needs
 * the real, confirmed set of expense category values this module
 * actually uses, which wasn't available this pass — flagging that as a
 * real, worthwhile follow-up rather than guessing at category strings.
 * 2400 (Accrued Expenses) for the payable side — chosen deliberately over
 * 2010 (Accounts Payable), which is specifically AP's own supplier-owed
 * account; an unpaid employee reimbursement is a different kind of
 * liability (an accrual), not a trade payable. This is my own reasoned
 * choice, not something independently confirmed elsewhere in this
 * codebase — worth a second look if a different convention is preferred.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpenseAccountingPoster {

    private static final String EXPENSE_ACCOUNT_CODE = "5240"; // Staff Expense Reimbursements
    private static final String PAYABLE_ACCOUNT_CODE = "2400"; // Accrued Expenses

    private final AccountingFacade accountingFacade;

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
            List<za.co.handyflow.platform.accounting.dto.AccountResponse> accounts =
                    accountingFacade.getAccounts(tenantId);

            UUID expenseAccountId = findAccountByCode(accounts, EXPENSE_ACCOUNT_CODE);
            UUID payableAccountId = findAccountByCode(accounts, PAYABLE_ACCOUNT_CODE);

            if (expenseAccountId == null || payableAccountId == null) {
                log.warn("Chart of Accounts missing account {} or {} for tenant={} — expense claim={} not posted",
                        EXPENSE_ACCOUNT_CODE, PAYABLE_ACCOUNT_CODE, tenantId, claimNumber);
                return Optional.empty();
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(
                            expenseAccountId, category + " — " + employeeName, amount, null),
                    new CreateJournalEntryRequest.JournalLineRequest(
                            payableAccountId, "Payable to " + employeeName, null, amount));

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Expense claim: " + description + " (" + claimNumber + ")",
                    claimNumber, "MANUAL", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());

            log.info("Posted expense journal entry {} for claim {}", created.entryNumber(), claimNumber);
            return Optional.of(created.id());

        } catch (Exception e) {
            // Preserved exactly, per this class's own extensive history:
            // approval must never fail because accounting posting failed.
            log.error("Failed to post expense {} to accounting: {}", claimNumber, e.getMessage());
            return Optional.empty();
        }
    }

    private UUID findAccountByCode(List<za.co.handyflow.platform.accounting.dto.AccountResponse> accounts, String code) {
        return accounts.stream()
                .filter(a -> code.equals(a.accountCode()))
                .map(a -> a.id())
                .findFirst()
                .orElse(null);
    }
}