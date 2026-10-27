package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.handyflow.platform.accounting.application.AccountingFacade;
import za.co.handyflow.platform.accounting.dto.AccountResponse;
import za.co.handyflow.platform.accounting.dto.CreateJournalEntryRequest;
import za.co.handyflow.platform.accounting.dto.JournalEntryResponse;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmClient;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmCreditNote;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoicePayment;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmClientRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmCreditNoteRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmInvoicePaymentRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmInvoiceRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmProfileRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmGlExceptionResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Posts approved invoices and recorded payments to the general ledger — reliably.
 * <ul>
 *   <li><b>Invoice</b> (on approval): DR accounts receivable (total); CR revenue (subtotal); CR output VAT (VAT, if any).</li>
 *   <li><b>Credit note</b> (on approval): DR revenue (net); DR output VAT (VAT, if any); CR accounts receivable (total).</li>
 *   <li><b>Payment</b>: DR bank (amount); CR accounts receivable (amount).</li>
 * </ul>
 * Account codes come from the tenant's billing settings.
 * <p>
 * Reliability model (replaces "log and forget"): the first attempt runs right after the business transaction
 * commits, in its own transaction, so a ledger problem can never undo an invoice or payment. Each attempt is
 * atomic — either the journal is created, posted and the document marked POSTED, or nothing is — and a failure
 * is recorded on the document (status FAILED, error text, attempt count). {@link FmGlRetryJob} retries failures
 * automatically (up to {@link #MAX_AUTO_ATTEMPTS}); anything still failing shows up as a ledger exception that
 * finance can see, fix (typically a missing account in the chart of accounts) and re-queue.
 * <p>
 * LEGACY documents (created before this process existed) are never touched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmGlPostingService {

    public static final int MAX_AUTO_ATTEMPTS = 10;
    private static final Duration RETRY_DELAY = Duration.ofMinutes(5);

    private final FmInvoiceRepository invoiceRepository;
    private final FmInvoicePaymentRepository paymentRepository;
    private final FmCreditNoteRepository creditNoteRepository;
    private final FmClientRepository clientRepository;
    private final FmProfileRepository profileRepository;
    private final AccountingFacade accountingFacade;
    private final PlatformTransactionManager transactionManager;

    // ── Scheduling ───────────────────────────────────────────────────────────

    /** Attempt the posting once the surrounding transaction has committed (or immediately when there isn't one). */
    public void postInvoiceAfterCommit(TenantId tenantId, UUID invoiceId) {
        afterCommit(() -> attemptInvoice(tenantId, invoiceId));
    }

    public void postCreditNoteAfterCommit(TenantId tenantId, UUID creditNoteId) {
        afterCommit(() -> attemptCreditNote(tenantId, creditNoteId));
    }

    public void postPaymentAfterCommit(TenantId tenantId, UUID paymentId) {
        afterCommit(() -> attemptPayment(tenantId, paymentId));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    // ── Attempts ─────────────────────────────────────────────────────────────

    public void attemptInvoice(TenantId tenantId, UUID invoiceId) {
        try {
            requiresNew().executeWithoutResult(s -> doPostInvoice(tenantId, invoiceId));
        } catch (Exception e) {
            String error = describe(e);
            log.error("Ledger posting failed for FM invoice={} tenant={}: {}", invoiceId, tenantId.getValue(), error, e);
            recordFailure(() -> invoiceRepository.findActiveById(tenantId, invoiceId).ifPresent(i -> {
                i.markGlFailed(error);
                invoiceRepository.save(i);
            }));
        }
    }

    public void attemptCreditNote(TenantId tenantId, UUID creditNoteId) {
        try {
            requiresNew().executeWithoutResult(s -> doPostCreditNote(tenantId, creditNoteId));
        } catch (Exception e) {
            String error = describe(e);
            log.error("Ledger posting failed for FM credit note={} tenant={}: {}", creditNoteId, tenantId.getValue(), error, e);
            recordFailure(() -> creditNoteRepository.findActiveById(tenantId, creditNoteId).ifPresent(c -> {
                c.markGlFailed(error);
                creditNoteRepository.save(c);
            }));
        }
    }

    public void attemptPayment(TenantId tenantId, UUID paymentId) {
        try {
            requiresNew().executeWithoutResult(s -> doPostPayment(tenantId, paymentId));
        } catch (Exception e) {
            String error = describe(e);
            log.error("Ledger posting failed for FM payment={} tenant={}: {}", paymentId, tenantId.getValue(), error, e);
            recordFailure(() -> paymentRepository.findActiveById(tenantId, paymentId).ifPresent(p -> {
                p.markGlFailed(error);
                paymentRepository.save(p);
            }));
        }
    }

    private void doPostInvoice(TenantId tenantId, UUID invoiceId) {
        FmInvoice invoice = invoiceRepository.findActiveById(tenantId, invoiceId).orElse(null);
        if (invoice == null || "CANCELLED".equals(invoice.getStatus())) return;
        if (!"PENDING".equals(invoice.getGlStatus()) && !"FAILED".equals(invoice.getGlStatus())) return; // posted, legacy or not due

        FmBillingSettings settings = FmBillingSettings.resolve(profileRepository.findByTenant(tenantId));
        List<AccountResponse> accounts = accountingFacade.getAccounts(tenantId);
        UUID ar = requireAccount(accounts, settings.arAccountCode(), "accounts receivable");
        UUID revenue = requireAccount(accounts, settings.revenueAccountCode(), "revenue");

        String clientName = clientName(tenantId, invoice.getClientId());
        UUID vat = invoice.getVatAmount().signum() > 0 ? requireAccount(accounts, settings.vatAccountCode(), "output VAT") : null;
        List<CreateJournalEntryRequest.JournalLineRequest> lines = FmGlJournals.invoiceLines(invoice, clientName, ar, revenue, vat);

        LocalDate date = invoice.getApprovedAt() != null
                ? FmTime.toLocalDate(invoice.getApprovedAt()) : FmTime.today();
        JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, new CreateJournalEntryRequest(
                date, "Facilities management invoice " + invoice.getInvoiceNumber(), invoice.getInvoiceNumber(), "MANUAL", lines));
        accountingFacade.postJournalEntry(tenantId, created.id());

        invoice.markGlPosted(created.id());
        invoiceRepository.save(invoice);
        log.info("Posted FM invoice={} to ledger tenant={} total={} vat={}",
                invoice.getInvoiceNumber(), tenantId.getValue(), invoice.getTotal(), invoice.getVatAmount());
    }

    /** DR revenue (net) and output VAT; CR accounts receivable (total) — the mirror image of the invoice posting. */
    private void doPostCreditNote(TenantId tenantId, UUID creditNoteId) {
        FmCreditNote note = creditNoteRepository.findActiveById(tenantId, creditNoteId).orElse(null);
        if (note == null || !"APPROVED".equals(note.getStatus())) return;
        if (!"PENDING".equals(note.getGlStatus()) && !"FAILED".equals(note.getGlStatus())) return;

        FmInvoice invoice = invoiceRepository.findActiveById(tenantId, note.getInvoiceId())
                .orElseThrow(() -> new IllegalStateException("Invoice for credit note " + creditNoteId + " not found"));
        if (!invoice.isInLedger()) {
            // Never credit a receivable that isn't in the ledger yet — the invoice must post first.
            throw new IllegalStateException("Invoice " + invoice.getInvoiceNumber() + " has not been posted to the ledger yet");
        }

        FmBillingSettings settings = FmBillingSettings.resolve(profileRepository.findByTenant(tenantId));
        List<AccountResponse> accounts = accountingFacade.getAccounts(tenantId);
        UUID ar = requireAccount(accounts, settings.arAccountCode(), "accounts receivable");
        UUID revenue = requireAccount(accounts, settings.revenueAccountCode(), "revenue");

        String clientName = clientName(tenantId, note.getClientId());
        UUID vat = note.getVatAmount().signum() > 0 ? requireAccount(accounts, settings.vatAccountCode(), "output VAT") : null;
        List<CreateJournalEntryRequest.JournalLineRequest> lines = FmGlJournals.creditNoteLines(note, invoice, clientName, ar, revenue, vat);

        LocalDate date = note.getApprovedAt() != null ? FmTime.toLocalDate(note.getApprovedAt()) : FmTime.today();
        JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, new CreateJournalEntryRequest(
                date, "Facilities management credit note " + note.getCreditNoteNumber(), note.getCreditNoteNumber(), "MANUAL", lines));
        accountingFacade.postJournalEntry(tenantId, created.id());

        note.markGlPosted(created.id());
        creditNoteRepository.save(note);
        log.info("Posted FM credit note={} to ledger tenant={} total={}", note.getCreditNoteNumber(), tenantId.getValue(), note.getTotal());
    }

    private void doPostPayment(TenantId tenantId, UUID paymentId) {
        FmInvoicePayment payment = paymentRepository.findActiveById(tenantId, paymentId).orElse(null);
        if (payment == null) return;
        if (!"PENDING".equals(payment.getGlStatus()) && !"FAILED".equals(payment.getGlStatus())) return;

        FmInvoice invoice = invoiceRepository.findActiveById(tenantId, payment.getInvoiceId())
                .orElseThrow(() -> new IllegalStateException("Invoice for payment " + paymentId + " not found"));
        if ("PENDING".equals(invoice.getGlStatus()) || "FAILED".equals(invoice.getGlStatus())) {
            // Never book cash against a receivable that isn't in the ledger yet — that invoice must post first.
            throw new IllegalStateException("Invoice " + invoice.getInvoiceNumber() + " has not been posted to the ledger yet");
        }

        FmBillingSettings settings = FmBillingSettings.resolve(profileRepository.findByTenant(tenantId));
        if (settings.bankAccountCode() == null) {
            throw new IllegalStateException("No bank account code is configured in the billing settings");
        }
        List<AccountResponse> accounts = accountingFacade.getAccounts(tenantId);
        UUID bank = requireAccount(accounts, settings.bankAccountCode(), "bank");
        UUID ar = requireAccount(accounts, settings.arAccountCode(), "accounts receivable");

        String clientName = clientName(tenantId, invoice.getClientId());
        List<CreateJournalEntryRequest.JournalLineRequest> lines = FmGlJournals.paymentLines(payment, invoice, clientName, bank, ar);

        JournalEntryResponse created = accountingFacade.createJournalEntry(tenantId, new CreateJournalEntryRequest(
                payment.getPaymentDate(), "Payment received for facilities management invoice " + invoice.getInvoiceNumber(),
                invoice.getInvoiceNumber() + "-PMT-" + paymentId.toString().substring(0, 8), "MANUAL", lines));
        accountingFacade.postJournalEntry(tenantId, created.id());

        payment.markGlPosted(created.id());
        paymentRepository.save(payment);
        log.info("Posted FM payment={} to ledger tenant={} amount={}", paymentId, tenantId.getValue(), payment.getAmount());
    }

    // ── Retry & exceptions ───────────────────────────────────────────────────

    /** Retry everything that is due (cross-tenant). Invoices first, so payments see their receivable posted. */
    public int retryDue() {
        Instant cutoff = Instant.now().minus(RETRY_DELAY);
        int attempted = 0;
        for (FmInvoice invoice : invoiceRepository.findDueForGlRetry(cutoff, MAX_AUTO_ATTEMPTS)) {
            attemptInvoice(invoice.getTenantId(), invoice.getId());
            attempted++;
        }
        for (FmCreditNote note : creditNoteRepository.findDueForGlRetry(cutoff, MAX_AUTO_ATTEMPTS)) {
            attemptCreditNote(note.getTenantId(), note.getId());
            attempted++;
        }
        for (FmInvoicePayment payment : paymentRepository.findDueForGlRetry(cutoff, MAX_AUTO_ATTEMPTS)) {
            attemptPayment(payment.getTenantId(), payment.getId());
            attempted++;
        }
        return attempted;
    }

    /** Put every FAILED posting for the tenant back in the queue with a fresh attempt budget, and try again right after commit. */
    @Transactional
    public int resetFailedAndRetry(TenantId tenantId) {
        List<UUID> invoiceIds = new ArrayList<>();
        for (FmInvoice i : invoiceRepository.findGlExceptions(tenantId)) {
            i.resetGlRetry();
            invoiceRepository.save(i);
            invoiceIds.add(i.getId());
        }
        List<UUID> creditNoteIds = new ArrayList<>();
        for (FmCreditNote c : creditNoteRepository.findGlExceptions(tenantId)) {
            c.resetGlRetry();
            creditNoteRepository.save(c);
            creditNoteIds.add(c.getId());
        }
        List<UUID> paymentIds = new ArrayList<>();
        for (FmInvoicePayment p : paymentRepository.findGlExceptions(tenantId)) {
            p.resetGlRetry();
            paymentRepository.save(p);
            paymentIds.add(p.getId());
        }
        afterCommit(() -> {
            invoiceIds.forEach(id -> attemptInvoice(tenantId, id));
            creditNoteIds.forEach(id -> attemptCreditNote(tenantId, id));
            paymentIds.forEach(id -> attemptPayment(tenantId, id));
        });
        return invoiceIds.size() + creditNoteIds.size() + paymentIds.size();
    }

    @Transactional(readOnly = true)
    public List<FmGlExceptionResponse> exceptions(TenantId tenantId) {
        List<FmGlExceptionResponse> result = new ArrayList<>();
        for (FmInvoice i : invoiceRepository.findGlExceptions(tenantId)) {
            result.add(new FmGlExceptionResponse("INVOICE", i.getId(), i.getId(), i.getInvoiceNumber(), i.getTotal(),
                    i.getGlAttempts(), i.getGlLastError(), i.getGlLastAttemptAt()));
        }
        for (FmCreditNote c : creditNoteRepository.findGlExceptions(tenantId)) {
            result.add(new FmGlExceptionResponse("CREDIT_NOTE", c.getId(), c.getInvoiceId(), c.getCreditNoteNumber(), c.getTotal(),
                    c.getGlAttempts(), c.getGlLastError(), c.getGlLastAttemptAt()));
        }
        for (FmInvoicePayment p : paymentRepository.findGlExceptions(tenantId)) {
            String ref = invoiceRepository.findActiveById(tenantId, p.getInvoiceId())
                    .map(FmInvoice::getInvoiceNumber).orElse(p.getInvoiceId().toString());
            result.add(new FmGlExceptionResponse("PAYMENT", p.getId(), p.getInvoiceId(), ref, p.getAmount(),
                    p.getGlAttempts(), p.getGlLastError(), p.getGlLastAttemptAt()));
        }
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UUID requireAccount(List<AccountResponse> accounts, String code, String purpose) {
        if (code == null || code.isBlank()) {
            throw new IllegalStateException("No " + purpose + " account code is configured in the billing settings");
        }
        return accounts.stream()
                .filter(a -> code.equals(a.accountCode()))
                .map(AccountResponse::id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "The chart of accounts has no account " + code + " (" + purpose + ") — add it, then retry"));
    }

    private String clientName(TenantId tenantId, UUID clientId) {
        return clientRepository.findActiveById(tenantId, clientId).map(FmClient::getTradingName).orElse(clientId.toString());
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void recordFailure(Runnable update) {
        try {
            requiresNew().executeWithoutResult(s -> update.run());
        } catch (Exception e) {
            log.error("Could not record ledger-posting failure: {}", e.getMessage(), e);
        }
    }

    private String describe(Exception e) {
        String m = e.getMessage();
        return m != null && !m.isBlank() ? m : e.getClass().getSimpleName();
    }
}