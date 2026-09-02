package za.co.handyflow.platform.collectionsagency.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.AccountResponse;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyClient;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyCommissionInvoice;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyDebtorAccount;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyTrustTransaction;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyCommissionInvoiceRepository;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyDebtorAccountRepository;
import za.co.handyflow.platform.collectionsagency.domain.repository.CollAgencyTrustTransactionRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The trust ledger's write path — the heart of this module's confirmed
 * "self-contained module trust ledger" design decision (see package-info
 * and the Collections Agency status doc for the full trade-off
 * discussion before this was built).
 * <p>
 * recordDebtorPayment() — money received from a debtor. NEVER touches
 * the real GL. Increases CollAgencyClient.trustBalance and reduces
 * CollAgencyDebtorAccount.currentBalance. This is the module's only
 * "money in" event.
 * <p>
 * processRemittance() — the periodic (client decides the cadence —
 * commonly monthly) payout cycle. Clears the WHOLE of a client's current
 * trustBalance in one operation: computes commission on that balance at
 * the client's rate (or the agency default), creates and immediately
 * issues a CollAgencyCommissionInvoice for the commission portion (the
 * ONLY GL-touching step in this entire module), and records ONE
 * REMITTANCE trust transaction for the full amount cleared — so
 * sum(RECEIPT) - sum(REMITTANCE) always reconciles to the current
 * trustBalance, with the commission/net split recorded in the
 * transaction's own notes and traceable via the linked commission
 * invoice number. See that transaction/invoice's own Javadoc for why
 * settling the commission invoice itself does not post a second "payment
 * received" GL journal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollAgencyTrustTransactionService {

    private static final String AR_ACCOUNT_CODE = "1100";      // Accounts Receivable — same code every other provider module in this codebase resolves against
    private static final String REVENUE_ACCOUNT_CODE = "4000"; // Revenue/Sales
    private static final String VAT_ACCOUNT_CODE = "2100";     // VAT Output

    private final CollAgencyTrustTransactionRepository trustRepository;
    private final CollAgencyCommissionInvoiceRepository invoiceRepository;
    private final CollAgencyDebtorAccountRepository debtorAccountRepository;
    private final CollAgencyClientService clientService;
    private final CollAgencyProfileService profileService;
    private final CollAgencyNumberGenerator numberGenerator;
    private final AccountingFacade accountingFacade;

    @Transactional
    public CollAgencyTrustTransaction recordDebtorPayment(TenantId tenantId, UUID debtorAccountId,
                                                           BigDecimal amount, LocalDate transactionDate,
                                                           String reference, String notes, UUID recordedByUserId) {
        CollAgencyDebtorAccount account = debtorAccountRepository.findActiveById(tenantId.getValue(), debtorAccountId)
                .orElseThrow(() -> new za.co.handyflow.platform.shared.ResourceNotFoundException(
                        "CollAgencyDebtorAccount", debtorAccountId.toString()));
        CollAgencyClient client = clientService.findActive(tenantId, account.getClientId());

        CollAgencyTrustTransaction txn = CollAgencyTrustTransaction.receipt(tenantId.getValue(), client.getId(),
                debtorAccountId, amount, transactionDate, reference, notes, recordedByUserId);
        txn = trustRepository.save(txn);

        account.applyPayment(amount);
        debtorAccountRepository.save(account);

        client.increaseTrustBalance(amount);
        // clientService has no direct save-only method exposed (its public API is create/update/deactivate/etc.),
        // so the balance change is persisted via the same repository path findActive() came from.
        clientService.saveTrustBalanceChange(client);

        log.info("[CollectionsAgency] Debtor payment recorded tenant={} debtorAccount={} client={} amount={}",
                tenantId.getValue(), debtorAccountId, client.getId(), amount);
        return txn;
    }

    @Transactional
    public RemittanceResult processRemittance(TenantId tenantId, UUID clientId, LocalDate remittanceDate,
                                               BigDecimal commissionRatePctOverride, UUID recordedByUserId) {
        CollAgencyClient client = clientService.findActive(tenantId, clientId);
        BigDecimal totalHeld = client.getTrustBalance();
        if (totalHeld.signum() <= 0) {
            throw new IllegalStateException("No trust balance is currently held for this client — nothing to remit");
        }

        BigDecimal commissionRatePct = resolveCommissionRate(tenantId, client, commissionRatePctOverride);
        BigDecimal commission = totalHeld.multiply(commissionRatePct)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal net = totalHeld.subtract(commission);

        String invoiceNumber = numberGenerator.nextCommissionInvoiceNumber(tenantId, clientId);
        String description = "Commission on remittance " + remittanceDate + " (" + commissionRatePct + "%% of R"
                + totalHeld + " collected)";
        BigDecimal vatAmount = BigDecimal.ZERO; // VAT-on-commission treatment is a real question this session should not guess at — see status doc
        CollAgencyCommissionInvoice invoice = CollAgencyCommissionInvoice.create(tenantId.getValue(), clientId,
                invoiceNumber, description, remittanceDate, remittanceDate.plusDays(30), commission, vatAmount);
        invoice = invoiceRepository.save(invoice);
        invoice.markSent();
        invoiceRepository.save(invoice);
        postCommissionRevenueJournal(tenantId, invoice, commission, vatAmount);

        CollAgencyTrustTransaction remittance = CollAgencyTrustTransaction.remittance(tenantId.getValue(), clientId,
                totalHeld, remittanceDate, invoiceNumber,
                "Net R" + net + " paid to client, commission R" + commission + " (" + commissionRatePct
                        + "%%) retained — see commission invoice " + invoiceNumber,
                recordedByUserId);
        remittance = trustRepository.save(remittance);

        client.decreaseTrustBalance(totalHeld);
        clientService.saveTrustBalanceChange(client);

        log.info("[CollectionsAgency] Remittance processed tenant={} client={} totalHeld={} commission={} net={} invoice={}",
                tenantId.getValue(), clientId, totalHeld, commission, net, invoiceNumber);
        return new RemittanceResult(remittance, invoice, net, commission);
    }

    @Transactional(readOnly = true)
    public List<CollAgencyTrustTransaction> listForClient(TenantId tenantId, UUID clientId) {
        return trustRepository.findByClient(tenantId.getValue(), clientId);
    }

    @Transactional(readOnly = true)
    public List<CollAgencyTrustTransaction> listForDebtorAccount(TenantId tenantId, UUID debtorAccountId) {
        return trustRepository.findByDebtorAccount(tenantId.getValue(), debtorAccountId);
    }

    /** Result of a remittance run — everything a caller/UI needs to confirm what just happened. */
    public record RemittanceResult(CollAgencyTrustTransaction transaction, CollAgencyCommissionInvoice invoice,
                                    BigDecimal netPaidToClient, BigDecimal commissionRetained) {}

    private BigDecimal resolveCommissionRate(TenantId tenantId, CollAgencyClient client,
                                              BigDecimal commissionRatePctOverride) {
        if (commissionRatePctOverride != null) {
            return commissionRatePctOverride;
        }
        if (client.getCommissionRatePct() != null) {
            return client.getCommissionRatePct();
        }
        var profile = profileService.get(tenantId);
        if (profile != null && profile.getDefaultCommissionPct() != null) {
            return profile.getDefaultCommissionPct();
        }
        throw new IllegalStateException(
                "No commission rate is set — not on this client, not as the agency default. Set one before "
                        + "processing a remittance (this is revenue-critical and was deliberately not defaulted to a guessed value)");
    }

    private void postCommissionRevenueJournal(TenantId tenantId, CollAgencyCommissionInvoice invoice,
                                              BigDecimal subtotal, BigDecimal vatAmount) {
        try {
            UUID arAccountId = findAccountByCode(tenantId, AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountByCode(tenantId, REVENUE_ACCOUNT_CODE);
            if (arAccountId == null || revenueAccountId == null) {
                log.warn("[CollectionsAgency] Chart of Accounts missing account {} or {} for tenant={} — commission invoice={} revenue not posted",
                        AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, tenantId.getValue(), invoice.getId());
                return;
            }
            boolean hasVat = vatAmount != null && vatAmount.compareTo(BigDecimal.ZERO) > 0;
            UUID vatAccountId = null;
            if (hasVat) {
                vatAccountId = findAccountByCode(tenantId, VAT_ACCOUNT_CODE);
                if (vatAccountId == null) {
                    log.warn("[CollectionsAgency] Chart of Accounts missing VAT Output ({}) for tenant={} — commission invoice={} revenue not posted",
                            VAT_ACCOUNT_CODE, tenantId.getValue(), invoice.getId());
                    return;
                }
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = new java.util.ArrayList<>();
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    arAccountId, "Commission — " + invoice.getInvoiceNumber(), invoice.getTotal(), null));
            lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                    revenueAccountId, "Commission revenue — " + invoice.getInvoiceNumber(), null, subtotal));
            if (hasVat) {
                lines.add(new CreateJournalEntryRequest.JournalLineRequest(
                        vatAccountId, "VAT output — " + invoice.getInvoiceNumber(), null, vatAmount));
            }

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Collections agency commission: " + invoice.getInvoiceNumber(),
                    invoice.getInvoiceNumber(), "MANUAL", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("[CollectionsAgency] Posted commission revenue journal for invoice={} tenant={}",
                    invoice.getInvoiceNumber(), tenantId.getValue());
        } catch (Exception e) {
            log.error("[CollectionsAgency] Failed to post commission revenue journal for invoice={} tenant={}: {}",
                    invoice.getId(), tenantId.getValue(), e.getMessage(), e);
        }
    }

    private UUID findAccountByCode(TenantId tenantId, String code) {
        List<AccountResponse> accounts = accountingFacade.getAccounts(tenantId);
        return accounts.stream()
                .filter(a -> code.equals(a.accountCode()))
                .map(AccountResponse::id)
                .findFirst()
                .orElse(null);
    }
}
