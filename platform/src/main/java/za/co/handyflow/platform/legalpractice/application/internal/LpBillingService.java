package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpClient;
import za.co.handyflow.platform.legalpractice.domain.model.LpDisbursement;
import za.co.handyflow.platform.legalpractice.domain.model.LpInvoice;
import za.co.handyflow.platform.legalpractice.domain.model.LpTimeEntry;
import za.co.handyflow.platform.legalpractice.domain.repository.*;
import za.co.handyflow.platform.legalpractice.dto.GenerateInvoiceRequest;
import za.co.handyflow.platform.legalpractice.dto.LpInvoiceResponse;
import za.co.handyflow.platform.legalpractice.dto.RecordInvoicePaymentRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Turns billable work into an {@link LpInvoice}, and records what a client
 * pays against one — the module's other centerpiece service alongside
 * {@code LpTrustTransactionService}, mirroring
 * {@code AccountantService.generateFeeNote()}/{@code recordPayment()}'s own
 * shape (confirmed by direct source read).
 * <p>
 * {@code generateInvoice()} deliberately does NOT post to the GL — an
 * issued invoice is a receivable, not yet realised cash; only
 * {@code recordPayment()} (an ordinary business-account payment) and
 * {@code LpTrustTransactionService.transferToBusiness()} (the trust-funded
 * equivalent) post the AR/Revenue journal, per the module's own scope
 * decision. A firm may collect its fees either way — both are real, both
 * exist as separate methods on separate services, neither implies the
 * other.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpBillingService {

    private final LpInvoiceRepository invoiceRepo;
    private final LpTimeEntryRepository timeEntryRepo;
    private final LpDisbursementRepository disbursementRepo;
    private final LpClientRepository clientRepo;
    private final LpAccountingPoster accountingPoster;
    // FIX (VAT sweep, module 2): replaces LpInvoice's own private
    // VAT_RATE constant (flat 0.15, not configurable) — see LpInvoice's
    // own Javadoc for the fuller reasoning.
    private final VatRateProvider vatRateProvider;

    @Transactional(readOnly = true)
    public Page<LpInvoiceResponse> listForClient(TenantId tenantId, UUID clientId, Pageable pageable) {
        return invoiceRepo.findAllForClient(tenantId, clientId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<LpInvoiceResponse> listForFirm(TenantId tenantId, Pageable pageable) {
        return invoiceRepo.findAllForFirm(tenantId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public LpInvoiceResponse getInvoice(TenantId tenantId, UUID invoiceId) {
        return toResponse(findOwn(tenantId, invoiceId));
    }

    /**
     * Loads and validates every referenced {@link LpTimeEntry}/
     * {@link LpDisbursement} (must be UNBILLED, must belong to the given
     * matter), sums {@code lineTotal()}/{@code amount} into a subtotal
     * (falling back to {@code req.fixedFeeAmount()} when both id lists are
     * empty — a FIXED_FEE matter or a retainer-only invoice), calls
     * {@link LpInvoice#create}, persists it, then calls
     * {@code markBilled(invoice.getId())} on every one of those rows in
     * this same transaction.
     */
    @Transactional
    public LpInvoiceResponse generateInvoice(TenantId tenantId, GenerateInvoiceRequest req) {
        LpClient client = clientRepo.findActiveById(tenantId, req.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("LpClient", req.clientId().toString()));

        List<UUID> timeEntryIds = req.timeEntryIds() != null ? req.timeEntryIds() : List.of();
        List<UUID> disbursementIds = req.disbursementIds() != null ? req.disbursementIds() : List.of();

        List<LpTimeEntry> timeEntries = timeEntryIds.stream()
                .map(id -> requireUnbilledTimeEntry(tenantId, id, req.matterId()))
                .toList();
        List<LpDisbursement> disbursements = disbursementIds.stream()
                .map(id -> requireUnbilledDisbursement(tenantId, id, req.matterId()))
                .toList();

        BigDecimal subtotal;
        if (!timeEntries.isEmpty() || !disbursements.isEmpty()) {
            subtotal = timeEntries.stream().map(LpTimeEntry::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .add(disbursements.stream().map(LpDisbursement::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        } else if (req.fixedFeeAmount() != null && req.fixedFeeAmount().signum() > 0) {
            subtotal = req.fixedFeeAmount();
        } else {
            throw new IllegalArgumentException(
                    "Nothing to bill — supply timeEntryIds/disbursementIds, or a positive fixedFeeAmount");
        }

        String invoiceNumber = nextInvoiceNumber(tenantId);
        LpInvoice invoice = LpInvoice.create(tenantId, req.clientId(), req.matterId(), invoiceNumber,
                req.description(), LocalDate.now(), req.dueDate(), subtotal, req.notes(),
                vatRateProvider.rateFraction());
        invoiceRepo.save(invoice);

        for (LpTimeEntry entry : timeEntries) {
            entry.markBilled(invoice.getId());
            timeEntryRepo.save(entry);
        }
        for (LpDisbursement disbursement : disbursements) {
            disbursement.markBilled(invoice.getId());
            disbursementRepo.save(disbursement);
        }

        log.info("Generated legal practice invoice={} number={} client={} subtotal={} tenant={}",
                invoice.getId(), invoiceNumber, req.clientId(), subtotal, tenantId);
        return toResponse(invoice);
    }

    @Transactional
    public LpInvoiceResponse markSent(TenantId tenantId, UUID invoiceId) {
        LpInvoice invoice = findOwn(tenantId, invoiceId);
        invoice.markSent();
        invoiceRepo.save(invoice);
        return toResponse(invoice);
    }

    /**
     * An ordinary business-account payment — separate from
     * {@code LpTrustTransactionService.transferToBusiness()}, which
     * settles an invoice from the client's trust deposit instead. Applies
     * the payment, then posts the AR/Revenue journal via
     * {@link LpAccountingPoster} (try/catch, non-blocking).
     */
    @Transactional
    public LpInvoiceResponse recordPayment(TenantId tenantId, UUID invoiceId, RecordInvoicePaymentRequest req) {
        LpInvoice invoice = findOwn(tenantId, invoiceId);
        invoice.applyPayment(req.amount());
        invoiceRepo.save(invoice);

        accountingPoster.postInvoiceRevenue(tenantId, invoice.getInvoiceNumber(), req.amount());

        log.info("Recorded payment of {} against legal practice invoice={} tenant={}", req.amount(), invoiceId, tenantId);
        return toResponse(invoice);
    }

    @Transactional
    public LpInvoiceResponse writeOff(TenantId tenantId, UUID invoiceId) {
        LpInvoice invoice = findOwn(tenantId, invoiceId);
        invoice.writeOff();
        invoiceRepo.save(invoice);
        return toResponse(invoice);
    }

    private LpTimeEntry requireUnbilledTimeEntry(TenantId tenantId, UUID id, UUID matterId) {
        LpTimeEntry entry = timeEntryRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("LpTimeEntry", id.toString()));
        if (!"UNBILLED".equals(entry.getStatus())) {
            throw new IllegalStateException("Time entry " + id + " is not UNBILLED, current status: " + entry.getStatus());
        }
        if (matterId != null && !entry.getMatterId().equals(matterId)) {
            throw new IllegalArgumentException("Time entry " + id + " does not belong to matter " + matterId);
        }
        return entry;
    }

    private LpDisbursement requireUnbilledDisbursement(TenantId tenantId, UUID id, UUID matterId) {
        LpDisbursement disbursement = disbursementRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("LpDisbursement", id.toString()));
        if (!"UNBILLED".equals(disbursement.getStatus())) {
            throw new IllegalStateException("Disbursement " + id + " is not UNBILLED, current status: " + disbursement.getStatus());
        }
        if (matterId != null && !disbursement.getMatterId().equals(matterId)) {
            throw new IllegalArgumentException("Disbursement " + id + " does not belong to matter " + matterId);
        }
        return disbursement;
    }

    private String nextInvoiceNumber(TenantId tenantId) {
        long seq = invoiceRepo.countForTenant(tenantId) + 1;
        return "INV-%d-%05d".formatted(LocalDate.now().getYear(), seq);
    }

    private LpInvoice findOwn(TenantId tenantId, UUID invoiceId) {
        return invoiceRepo.findActiveById(tenantId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("LpInvoice", invoiceId.toString()));
    }

    private LpInvoiceResponse toResponse(LpInvoice i) {
        return new LpInvoiceResponse(i.getId(), i.getClientId(), i.getMatterId(), i.getInvoiceNumber(),
                i.getDescription(), i.getIssueDate(), i.getDueDate(), i.getSubtotal(), i.getVatAmount(),
                i.getTotalAmount(), i.getAmountPaid(), i.getOutstandingBalance(), i.getStatus(), i.getNotes(),
                i.getCreatedAt(), i.getUpdatedAt());
    }
}
