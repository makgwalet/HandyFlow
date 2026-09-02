package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.AccountResponse;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Posts the firm's own earned-revenue GL entries — the AR-debit(1100)/
 * Revenue-credit(4000) journal, exactly the pattern {@code ExpenseAccountingPoster}
 * / {@code ClinicBillingService} / {@code FmBillingService} / {@code TrainProvBillingService}
 * all use, confirmed by direct read of {@code AccountingFacade} and
 * {@code ExpenseAccountingPoster} this session. Called from exactly two
 * places, per the module's own scope decision: {@code LpBillingService.recordPayment()}
 * (an ordinary business-account payment against a sent invoice) and
 * {@code LpTrustTransactionService.transferToBusiness()} (the firm drawing
 * its own earned fees out of a client's trust deposit to settle the same
 * kind of invoice). Both represent the firm recognising real earned
 * revenue against an invoice — RECEIPT/DISBURSEMENT_PAYMENT/REFUND trust
 * movements never call this, because that money was never the firm's own
 * revenue.
 * <p>
 * Never throws — a GL posting failure (missing Chart of Accounts entries,
 * a transient accounting-service error) must never block or roll back the
 * business operation that triggered it. Every failure is caught and
 * logged, matching {@code ExpenseAccountingPoster}'s own documented
 * contract.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LpAccountingPoster {

    private static final String AR_ACCOUNT_CODE = "1100";
    private static final String REVENUE_ACCOUNT_CODE = "4000";

    private final AccountingFacade accountingFacade;

    public void postInvoiceRevenue(TenantId tenantId, String invoiceNumber, BigDecimal amount) {
        try {
            List<AccountResponse> accounts = accountingFacade.getAccounts(tenantId);
            UUID arAccountId = findAccountId(accounts, AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountId(accounts, REVENUE_ACCOUNT_CODE);
            if (arAccountId == null || revenueAccountId == null) {
                log.warn("Chart of Accounts missing account {} or {} for tenant={} — invoice={} revenue not posted",
                        AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, tenantId, invoiceNumber);
                return;
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(
                            arAccountId, "Legal fees — " + invoiceNumber, amount, null),
                    new CreateJournalEntryRequest.JournalLineRequest(
                            revenueAccountId, "Legal fees — " + invoiceNumber, null, amount)
            );
            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Legal fees — " + invoiceNumber, invoiceNumber, "MANUAL", lines);

            JournalEntryResponse entry = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, entry.id());
        } catch (Exception e) {
            log.error("Failed to post GL journal for invoice={} tenant={}: {}",
                    invoiceNumber, tenantId, e.getMessage(), e);
        }
    }

    private UUID findAccountId(List<AccountResponse> accounts, String code) {
        return accounts.stream()
                .filter(a -> code.equals(a.accountCode()))
                .map(AccountResponse::id)
                .findFirst()
                .orElse(null);
    }
}
