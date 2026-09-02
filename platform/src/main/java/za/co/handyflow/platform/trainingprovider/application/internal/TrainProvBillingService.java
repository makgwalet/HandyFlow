package za.co.handyflow.platform.trainingprovider.application.internal;

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
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The billing centerpiece. Unlike Module 4a (which deliberately does
 * NOT post training cost to the GL), this variant genuinely bills
 * external clients, so it follows the same
 * {@code findAccountByCode}+{@code AccountingFacade} revenue-posting
 * pattern every other provider module in this codebase uses.
 * <p>
 * DELIBERATE SIMPLIFICATIONS (flagged, not silently guessed — see
 * status doc for the full list):
 * <ul>
 *   <li>Billing is per-DELEGATE-ENROLLMENT at the course's current
 *       {@code pricePerDelegate}, snapshotted onto the invoice as a
 *       total only — no separate invoice-line-item entity, same flat-
 *       entity shape {@code WhseBillingInvoice}/
 *       {@code CollAgencyCommissionInvoice} both use.</li>
 *   <li>An enrollment becomes billable once its session has STARTED
 *       (not once it's COMPLETED) — a no-show or an in-progress course
 *       is still billed, since the provider reserved the seat and ran
 *       the session regardless of whether the delegate finished it.
 *       Only a CANCELLED enrollment (withdrawn before the session)
 *       is excluded.</li>
 *   <li>VAT is left at zero — same open question flagged on every
 *       other provider module's own invoice in this codebase.</li>
 *   <li>First invoice's period start, with no prior invoice for this
 *       client, defaults to a one-month lookback from periodEnd —
 *       flagged as a fallback, not a confirmed business rule, same
 *       caveat Warehousing's own billing service carries.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainProvBillingService {

    private static final String AR_ACCOUNT_CODE = "1100";
    private static final String REVENUE_ACCOUNT_CODE = "4000";
    private static final int DEFAULT_PAYMENT_TERMS_DAYS = 30;

    private final TrainProvInvoiceRepository invoiceRepository;
    private final TrainProvEnrollmentRepository enrollmentRepository;
    private final TrainProvSessionRepository sessionRepository;
    private final TrainProvCourseRepository courseRepository;
    private final TrainProvClientRepository clientRepository;
    private final TrainProvNumberGenerator numberGenerator;
    private final AccountingFacade accountingFacade;

    @Transactional
    public TrainProvInvoice generateInvoice(TenantId tenantId, UUID clientId, LocalDate periodEnd) {
        TrainProvClient client = clientRepository.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvClient", clientId.toString()));

        LocalDate periodStart = resolvePeriodStart(tenantId, clientId, periodEnd);

        List<TrainProvEnrollment> billable = enrollmentRepository.findBillableForClient(tenantId, clientId, periodEnd);
        if (billable.isEmpty()) {
            throw new IllegalStateException("No billable enrollments found for client " + clientId + " as of " + periodEnd);
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        for (TrainProvEnrollment enrollment : billable) {
            TrainProvSession session = sessionRepository.findByTenantAndId(tenantId, enrollment.getSessionId())
                    .orElseThrow(() -> new ResourceNotFoundException("TrainProvSession", enrollment.getSessionId().toString()));
            TrainProvCourse course = courseRepository.findActiveById(tenantId, session.getCourseId())
                    .orElseThrow(() -> new ResourceNotFoundException("TrainProvCourse", session.getCourseId().toString()));
            subtotal = subtotal.add(course.getPricePerDelegate());
        }

        String invoiceNumber = numberGenerator.nextInvoiceNumber(tenantId);
        TrainProvInvoice invoice = TrainProvInvoice.create(tenantId, clientId, invoiceNumber, periodStart, periodEnd,
                LocalDate.now(), LocalDate.now().plusDays(DEFAULT_PAYMENT_TERMS_DAYS), billable.size(), subtotal, BigDecimal.ZERO);
        invoice = invoiceRepository.save(invoice);

        for (TrainProvEnrollment enrollment : billable) {
            enrollment.markInvoiced();
            enrollmentRepository.save(enrollment);
        }

        postRevenueJournal(tenantId, invoice, client);

        log.info("Generated invoice={} for client={} tenant={} delegates={} subtotal={}",
                invoiceNumber, clientId, tenantId.getValue(), billable.size(), subtotal);
        return invoice;
    }

    @Transactional
    public TrainProvInvoice markSent(TenantId tenantId, UUID id) {
        TrainProvInvoice invoice = get(tenantId, id);
        invoice.markSent();
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public TrainProvInvoice recordPayment(TenantId tenantId, UUID id, BigDecimal amount) {
        TrainProvInvoice invoice = get(tenantId, id);
        invoice.recordPayment(amount);
        return invoiceRepository.save(invoice);
    }

    public TrainProvInvoice get(TenantId tenantId, UUID id) {
        return invoiceRepository.findByTenantAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainProvInvoice", id.toString()));
    }

    public Page<TrainProvInvoice> list(TenantId tenantId, UUID clientId, Pageable pageable) {
        return invoiceRepository.findAllForClient(tenantId, clientId, pageable);
    }

    private LocalDate resolvePeriodStart(TenantId tenantId, UUID clientId, LocalDate periodEnd) {
        return invoiceRepository.findAllForClientList(tenantId, clientId).stream()
                .findFirst() // list is ordered by periodEnd DESC — the most recent invoice
                .map(TrainProvInvoice::getPeriodEnd)
                .orElseGet(() -> periodEnd.minusMonths(1));
    }

    /**
     * Same {@code findAccountByCode}+try/catch-swallow pattern every
     * other revenue-posting provider module in this codebase uses
     * (ClinicBillingService, ExpenseAccountingPoster, POS) — a posting
     * failure must never undo or block the invoice itself, which is
     * already saved by the time this runs.
     */
    private void postRevenueJournal(TenantId tenantId, TrainProvInvoice invoice, TrainProvClient client) {
        try {
            List<AccountResponse> accounts = accountingFacade.getAccounts(tenantId);
            UUID arAccountId = findAccountByCode(accounts, AR_ACCOUNT_CODE);
            UUID revenueAccountId = findAccountByCode(accounts, REVENUE_ACCOUNT_CODE);

            if (arAccountId == null || revenueAccountId == null) {
                log.warn("Chart of Accounts missing account {} or {} for tenant={} — invoice={} not posted",
                        AR_ACCOUNT_CODE, REVENUE_ACCOUNT_CODE, tenantId.getValue(), invoice.getInvoiceNumber());
                return;
            }

            List<CreateJournalEntryRequest.JournalLineRequest> lines = List.of(
                    new CreateJournalEntryRequest.JournalLineRequest(
                            arAccountId, "Training invoice " + invoice.getInvoiceNumber() + " — " + client.getTradingName(),
                            invoice.getTotal(), null),
                    new CreateJournalEntryRequest.JournalLineRequest(
                            revenueAccountId, "Training revenue — " + client.getTradingName(),
                            null, invoice.getTotal()));

            CreateJournalEntryRequest req = new CreateJournalEntryRequest(
                    LocalDate.now(), "Training provider invoice " + invoice.getInvoiceNumber(),
                    invoice.getInvoiceNumber(), "MANUAL", lines);

            JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, req);
            accountingFacade.postJournalEntry(tenantId, created.id());
            log.info("Posted revenue journal for invoice={} tenant={} amount={}",
                    invoice.getInvoiceNumber(), tenantId.getValue(), invoice.getTotal());
        } catch (Exception e) {
            log.error("Failed to post revenue journal for invoice={} tenant={}: {}",
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
}
