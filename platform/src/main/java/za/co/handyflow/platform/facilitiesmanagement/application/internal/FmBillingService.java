package za.co.handyflow.platform.facilitiesmanagement.application.internal;

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
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClient;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmServiceAgreement;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmInvoiceRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmServiceAgreementRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmWorkOrderRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmInvoiceResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * The billing centerpiece — THE key differentiator over Module 5a (which
 * deliberately posts nothing to the GL): this variant genuinely bills
 * external clients, so it follows the same {@code findAccountByCode}
 * + {@code AccountingFacade} revenue-posting pattern every other
 * provider module in this codebase uses (confirmed by direct source
 * read of {@code TrainProvBillingService}).
 * <p>
 * BILLING RESOLUTION (per {@code generateInvoice} call, one client/one
 * period at a time):
 * <ol>
 *   <li>Look up the client's agreements ACTIVE as of {@code periodStart}
 *       ({@link FmServiceAgreementRepository#findActiveAsOfDate}). If one
 *       of them is a RETAINER, the invoice subtotal is that agreement's
 *       flat {@code monthlyFee} and no work order is touched.</li>
 *   <li>Otherwise (no active agreement, or only an active
 *       TIME_AND_MATERIALS one), the subtotal is the sum of {@code cost}
 *       across the client's billable work orders completed within
 *       {@code [periodStart, periodEnd]} — each is then marked
 *       {@code invoiced}.</li>
 * </ol>
 * DELIBERATE SIMPLIFICATIONS (flagged, not silently guessed):
 * <ul>
 *   <li>VAT is left at zero — same open question flagged on every other
 *       provider module's own invoice in this codebase.</li>
 *   <li>Payment terms are a flat 30 days from issue date — not configurable
 *       per client/agreement in this first pass.</li>
 *   <li>If more than one RETAINER agreement is somehow active for a client
 *       at the same time (the repository doesn't enforce uniqueness — see
 *       its own Javadoc), the first one returned is used; this is treated
 *       as a data-hygiene issue for {@code FmServiceAgreementService}'s
 *       callers to prevent, not something this service reconciles.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmBillingService {

    private static final String AR_ACCOUNT_CODE = "1100";
    private static final String REVENUE_ACCOUNT_CODE = "4000";
    private static final int DEFAULT_PAYMENT_TERMS_DAYS = 30;

    private final FmInvoiceRepository invoiceRepository;
    private final FmClientRepository clientRepository;
    private final FmServiceAgreementRepository serviceAgreementRepository;
    private final FmWorkOrderRepository workOrderRepository;
    private final FmNumberGenerator numberGenerator;
    private final AccountingFacade accountingFacade;

    @Transactional(readOnly = true)
    public Page<FmInvoiceResponse> getInvoices(TenantId tenantId, UUID clientId, Pageable pageable) {
        return invoiceRepository.findAllActiveForClient(tenantId, clientId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FmInvoiceResponse getInvoice(TenantId tenantId, UUID id) {
        return toResponse(findInvoice(tenantId, id));
    }

    @Transactional
    public FmInvoiceResponse generateInvoice(TenantId tenantId, UUID clientId, LocalDate periodStart, LocalDate periodEnd) {
        FmClient client = clientRepository.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("FmClient", clientId.toString()));
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("periodEnd must not be before periodStart");
        }

        FmServiceAgreement retainerAgreement = serviceAgreementRepository.findActiveAsOfDate(tenantId, clientId, periodStart)
                .stream().filter(FmServiceAgreement::isRetainer).findFirst().orElse(null);

        BigDecimal subtotal;
        List<FmWorkOrder> billableWorkOrders;

        if (retainerAgreement != null) {
            subtotal = retainerAgreement.getMonthlyFee();
            billableWorkOrders = List.of();
        } else {
            Instant periodStartInstant = periodStart.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant periodEndExclusive = periodEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            billableWorkOrders = workOrderRepository.findBillableForClient(tenantId, clientId, periodStartInstant, periodEndExclusive);
            subtotal = billableWorkOrders.stream().map(FmWorkOrder::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        if (subtotal == null || subtotal.signum() <= 0) {
            throw new IllegalStateException("Nothing to bill for this client in this period");
        }

        String invoiceNumber = numberGenerator.nextInvoiceNumber(tenantId);
        LocalDate issueDate = LocalDate.now();
        FmInvoice invoice = FmInvoice.create(tenantId, clientId, invoiceNumber, periodStart, periodEnd,
                issueDate, issueDate.plusDays(DEFAULT_PAYMENT_TERMS_DAYS), subtotal, BigDecimal.ZERO);
        invoice = invoiceRepository.save(invoice);

        for (FmWorkOrder wo : billableWorkOrders) {
            wo.markInvoiced();
            workOrderRepository.save(wo);
        }

        postRevenueJournal(tenantId, invoice, client);

        log.info("FM invoice generated number={} client={} tenant={} subtotal={} workOrders={}",
                invoiceNumber, clientId, tenantId.getValue(), subtotal, billableWorkOrders.size());
        return toResponse(invoice);
    }

    @Transactional
    public FmInvoiceResponse sendInvoice(TenantId tenantId, UUID invoiceId) {
        FmInvoice invoice = findInvoice(tenantId, invoiceId);
        invoice.markSent();
        invoiceRepository.save(invoice);
        return toResponse(invoice);
    }

    @Transactional
    public FmInvoiceResponse recordPayment(TenantId tenantId, UUID invoiceId, BigDecimal amount) {
        FmInvoice invoice = findInvoice(tenantId, invoiceId);
        invoice.recordPayment(amount);
        invoiceRepository.save(invoice);
        log.info("FM payment recorded invoice={} tenant={} amount={} newStatus={}",
                invoice.getInvoiceNumber(), tenantId.getValue(), amount, invoice.getStatus());
        return toResponse(invoice);
    }

    FmInvoice findInvoice(TenantId tenantId, UUID id) {
        return invoiceRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmInvoice", id.toString()));
    }

    /**
     * Same {@code findAccountByCode}+try/catch-swallow pattern every other
     * revenue-posting provider module in this codebase uses
     * (TrainProvBillingService, ClinicBillingService, ExpenseAccountingPoster,
     * POS) — a posting failure must never undo or block the invoice itself,
     * which is already saved by the time this runs.
     */
    private void postRevenueJournal(TenantId tenantId, FmInvoice invoice, FmClient client) {
        try {
            List<AccountResponse> accounts = accountingFacade.getAccounts(tenantId);
            UUID arAccountId = findAccountByCode(accounts, AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountByCode(accounts, REVENUE_ACCOUNT_CODE);

            if (arAccountId == null || revenueAccountId == null) {
                log.warn("Chart of Accounts missing account {} or {} for tenant={} — FM invoice={} not posted",
                        AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, tenantId.getValue(), invoice.getInvoiceNumber());
                return;
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(
                            arAccountId, "FM invoice " + invoice.getInvoiceNumber() + " — " + client.getTradingName(),
                            invoice.getTotal(), null),
                    new CreateJournalEntryRequest.JournalLineRequest(
                            revenueAccountId, "Facilities management revenue — " + client.getTradingName(),
                            null, invoice.getTotal()));

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Facilities management invoice " + invoice.getInvoiceNumber(),
                    invoice.getInvoiceNumber(), "MANUAL", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("Posted revenue journal for FM invoice={} tenant={} amount={}",
                    invoice.getInvoiceNumber(), tenantId.getValue(), invoice.getTotal());
        } catch (Exception e) {
            log.error("Failed to post revenue journal for FM invoice={} tenant={}: {}",
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

    private FmInvoiceResponse toResponse(FmInvoice i) {
        return new FmInvoiceResponse(i.getId(), i.getClientId(), i.getInvoiceNumber(), i.getPeriodStart(), i.getPeriodEnd(),
                i.getIssueDate(), i.getDueDate(), i.getSubtotal(), i.getVatAmount(), i.getTotal(), i.getAmountPaid(),
                i.balance(), i.getStatus(), i.getCreatedAt());
    }
}
