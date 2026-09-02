package za.co.handyflow.platform.bookkeeping.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.AccountResponse;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.bookkeeping.domain.model.BkClient;
import za.co.handyflow.platform.bookkeeping.domain.model.BkInvoice;
import za.co.handyflow.platform.bookkeeping.domain.model.BkServiceAgreement;
import za.co.handyflow.platform.bookkeeping.domain.model.BkTimeEntry;
import za.co.handyflow.platform.bookkeeping.domain.repository.*;
import za.co.handyflow.platform.bookkeeping.dto.BkInvoiceResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The billing centerpiece — a DIRECT PORT of {@code FmBillingService}'s
 * own resolution logic (per the build brief, confirmed identical in shape
 * to {@code TrainProvBillingService} too): this practice genuinely bills
 * external clients, so it follows the same {@code findAccountByCode} +
 * {@code AccountingFacade} revenue-posting pattern every other
 * provider module in this codebase uses — posting the PRACTICE's OWN
 * revenue, never touching a client's own books (those live entirely in
 * this module's own {@code Bk}-prefixed tables).
 * <p>
 * BILLING RESOLUTION (per {@code generateInvoice} call, one client/one
 * period at a time):
 * <ol>
 *   <li>Look up the client's agreements ACTIVE as of {@code periodStart}
 *       ({@link BkServiceAgreementRepository#findActiveAsOfDate}). If one
 *       of them is a RETAINER, the invoice subtotal is that agreement's
 *       flat {@code monthlyFee} and no time entry is touched.</li>
 *   <li>Otherwise, the subtotal is the sum of {@code lineTotal()} across
 *       the client's UNBILLED, billable {@link BkTimeEntry} records dated
 *       within {@code [periodStart, periodEnd]} — each is then marked
 *       {@code BILLED} against the new invoice.</li>
 * </ol>
 * DELIBERATE SIMPLIFICATIONS (flagged, not silently guessed):
 * <ul>
 *   <li>VAT is left at zero — same open question flagged on every other
 *       provider module's own invoice in this codebase.</li>
 *   <li>Payment terms are a flat 30 days from issue date.</li>
 *   <li>If more than one RETAINER agreement is somehow active for a client
 *       at the same time, the first one returned is used — a data-hygiene
 *       issue for {@code BkServiceAgreementService}'s callers to prevent,
 *       not something this service reconciles.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BkBillingService {

    private static final String AR_ACCOUNT_CODE = "1100";
    private static final String REVENUE_ACCOUNT_CODE = "4000";
    private static final int DEFAULT_PAYMENT_TERMS_DAYS = 30;

    private final BkInvoiceRepository invoiceRepository;
    private final BkClientRepository clientRepository;
    private final BkServiceAgreementRepository serviceAgreementRepository;
    private final BkTimeEntryRepository timeEntryRepository;
    private final BkNumberGenerator numberGenerator;
    private final AccountingFacade accountingFacade;

    @Transactional(readOnly = true)
    public Page<BkInvoiceResponse> getInvoices(TenantId tenantId, UUID clientId, Pageable pageable) {
        return invoiceRepository.findAllActiveForClient(tenantId, clientId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BkInvoiceResponse getInvoice(TenantId tenantId, UUID id) {
        return toResponse(findInvoice(tenantId, id));
    }

    @Transactional
    public BkInvoiceResponse generateInvoice(TenantId tenantId, UUID clientId, LocalDate periodStart, LocalDate periodEnd) {
        BkClient client = clientRepository.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("BkClient", clientId.toString()));
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("periodEnd must not be before periodStart");
        }

        BkServiceAgreement retainerAgreement = serviceAgreementRepository.findActiveAsOfDate(tenantId, clientId, periodStart)
                .stream().filter(BkServiceAgreement::isRetainer).findFirst().orElse(null);

        BigDecimal subtotal;
        List<BkTimeEntry> billableTimeEntries;

        if (retainerAgreement != null) {
            subtotal = retainerAgreement.getMonthlyFee();
            billableTimeEntries = List.of();
        } else {
            billableTimeEntries = timeEntryRepository.findUnbilledInRange(tenantId, clientId, periodStart, periodEnd);
            subtotal = billableTimeEntries.stream().map(BkTimeEntry::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        if (subtotal == null || subtotal.signum() <= 0) {
            throw new IllegalStateException("Nothing to bill for this client in this period");
        }

        String invoiceNumber = numberGenerator.nextInvoiceNumber(tenantId);
        LocalDate issueDate = LocalDate.now();
        BkInvoice invoice = BkInvoice.create(tenantId, clientId, invoiceNumber, periodStart, periodEnd,
                issueDate, issueDate.plusDays(DEFAULT_PAYMENT_TERMS_DAYS), subtotal, BigDecimal.ZERO);
        invoice = invoiceRepository.save(invoice);

        for (BkTimeEntry entry : billableTimeEntries) {
            entry.markBilled(invoice.getId());
            timeEntryRepository.save(entry);
        }

        postRevenueJournal(tenantId, invoice, client);

        log.info("Bookkeeping invoice generated number={} client={} tenant={} subtotal={} timeEntries={}",
                invoiceNumber, clientId, tenantId.getValue(), subtotal, billableTimeEntries.size());
        return toResponse(invoice);
    }

    @Transactional
    public BkInvoiceResponse sendInvoice(TenantId tenantId, UUID invoiceId) {
        BkInvoice invoice = findInvoice(tenantId, invoiceId);
        invoice.markSent();
        invoiceRepository.save(invoice);
        return toResponse(invoice);
    }

    @Transactional
    public BkInvoiceResponse recordPayment(TenantId tenantId, UUID invoiceId, BigDecimal amount) {
        BkInvoice invoice = findInvoice(tenantId, invoiceId);
        invoice.recordPayment(amount);
        invoiceRepository.save(invoice);
        log.info("Bookkeeping payment recorded invoice={} tenant={} amount={} newStatus={}",
                invoice.getInvoiceNumber(), tenantId.getValue(), amount, invoice.getStatus());
        return toResponse(invoice);
    }

    BkInvoice findInvoice(TenantId tenantId, UUID id) {
        return invoiceRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("BkInvoice", id.toString()));
    }

    /**
     * Same {@code findAccountByCode}+try/catch-swallow pattern every other
     * revenue-posting provider module in this codebase uses — a posting
     * failure must never undo or block the invoice itself, which is
     * already saved by the time this runs.
     */
    private void postRevenueJournal(TenantId tenantId, BkInvoice invoice, BkClient client) {
        try {
            List<AccountResponse> accounts = accountingFacade.getAccounts(tenantId);
            UUID arAccountId = findAccountByCode(accounts, AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountByCode(accounts, REVENUE_ACCOUNT_CODE);

            if (arAccountId == null || revenueAccountId == null) {
                log.warn("Chart of Accounts missing account {} or {} for tenant={} — bookkeeping invoice={} not posted",
                        AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, tenantId.getValue(), invoice.getInvoiceNumber());
                return;
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(
                            arAccountId, "Bookkeeping invoice " + invoice.getInvoiceNumber() + " — " + client.getTradingName(),
                            invoice.getTotal(), null),
                    new CreateJournalEntryRequest.JournalLineRequest(
                            revenueAccountId, "Bookkeeping services revenue — " + client.getTradingName(),
                            null, invoice.getTotal()));

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Bookkeeping invoice " + invoice.getInvoiceNumber(),
                    invoice.getInvoiceNumber(), "MANUAL", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("Posted revenue journal for bookkeeping invoice={} tenant={} amount={}",
                    invoice.getInvoiceNumber(), tenantId.getValue(), invoice.getTotal());
        } catch (Exception e) {
            log.error("Failed to post revenue journal for bookkeeping invoice={} tenant={}: {}",
                    invoice.getInvoiceNumber(), tenantId.getValue(), e.getMessage(), e);
        }
    }

    private UUID findAccountByCode(List<AccountResponse> accounts, String code) {
        return accounts.stream()
                .filter(a -> code.equals(a.accountCode()))
                .map(AccountResponse::id)
                .findFirst()
                .orElse(null);
    }

    private BkInvoiceResponse toResponse(BkInvoice i) {
        return new BkInvoiceResponse(i.getId(), i.getClientId(), i.getInvoiceNumber(), i.getPeriodStart(), i.getPeriodEnd(),
                i.getIssueDate(), i.getDueDate(), i.getSubtotal(), i.getVatAmount(), i.getTotal(), i.getAmountPaid(),
                i.balance(), i.getStatus(), i.getCreatedAt());
    }
}
