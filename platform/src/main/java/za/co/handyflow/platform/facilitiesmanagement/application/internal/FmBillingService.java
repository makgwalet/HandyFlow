package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoicePayment;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmServiceAgreement;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmWorkOrder;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.*;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmGlExceptionResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmInvoicePaymentResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmInvoiceResponse;
import za.co.handyflow.platform.facilitiesmanagement.dto.RecordFmPaymentRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Billing (Chunk 7). An ACTIVE RETAINER covering {@code periodStart} bills its flat {@code monthlyFee};
 * otherwise the subtotal is the sum of the client's billable work-order charges in the period (only work that
 * is truly COMPLETED, i.e. verified where verification applies). Amounts are VAT-EXCLUSIVE; VAT is added on top
 * when the company is VAT-registered, using the rate in its billing settings, and the rate used is kept on the
 * invoice.
 * <p>
 * Workflow: <b>prepare</b> (DRAFT) -> <b>approve</b> (APPROVED, posts to the ledger) -> <b>send</b> -> payments.
 * The person who prepares an invoice cannot approve it; a FACILITIESMANAGEMENT_ADMIN may, and that override is
 * audited as its own entry. A DRAFT can be cancelled, which releases its work orders for re-billing; anything
 * approved needs a credit note (not yet implemented). Payments are recorded individually and posted to the
 * ledger (DR bank / CR receivables). Ledger posting runs after commit with automatic retry
 * ({@link FmGlPostingService}), so a ledger problem never loses an invoice or a payment.
 * <p>
 * Duplicate protection: a retainer cannot be invoiced twice for an overlapping period (a cancelled draft frees
 * the period); work orders already invoiced are never picked up again.
 * <p>
 * STILL OPEN: credit notes; rate-card pricing from agreements (Chunk 8); client VAT number on invoices;
 * double-submit protection on payment recording.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmBillingService {

    private final FmInvoiceRepository invoiceRepository;
    private final FmInvoicePaymentRepository paymentRepository;
    private final FmClientRepository clientRepository;
    private final FmServiceAgreementRepository serviceAgreementRepository;
    private final FmWorkOrderRepository workOrderRepository;
    private final FmProfileRepository profileRepository;
    private final FmNumberGenerator numberGenerator;
    private final FmGlPostingService glPostingService;
    private final FmAccessPolicy accessPolicy;
    private final FmAuditService auditService;

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<FmInvoiceResponse> getInvoices(TenantId tenantId, UUID clientId, Pageable pageable) {
        accessPolicy.scope(tenantId).requireClient(clientId);
        return invoiceRepository.findAllActiveForClient(tenantId, clientId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public FmInvoiceResponse getInvoice(TenantId tenantId, UUID id) {
        return toResponse(findInvoice(tenantId, id, accessPolicy.scope(tenantId)));
    }

    @Transactional(readOnly = true)
    public List<FmInvoicePaymentResponse> getPayments(TenantId tenantId, UUID invoiceId) {
        findInvoice(tenantId, invoiceId, accessPolicy.scope(tenantId));
        return paymentRepository.findForInvoice(tenantId, invoiceId).stream().map(this::toResponse).toList();
    }

    /** Throws NOT FOUND unless the invoice exists and the caller may see its client (used by the PDF endpoint). */
    @Transactional(readOnly = true)
    public void requireAccessible(TenantId tenantId, UUID id) {
        findInvoice(tenantId, id, accessPolicy.scope(tenantId));
    }

    // ── Prepare ──────────────────────────────────────────────────────────────

    /**
     * Prepare a DRAFT invoice. Billing basis:
     * <ul>
     *   <li><b>RETAINER</b> — the flat monthly fee of ONE retainer (named by {@code agreementId} when the client has
     *       several), once per period per retainer. Work covered by it is INCLUDED and never itemised or billed.</li>
     *   <li><b>WORK_ORDERS</b> — the charge on completed, uninvoiced work that is CHARGEABLE: extras outside any
     *       retainer, and all work for time-and-materials clients. INCLUDED work is never picked up.</li>
     * </ul>
     * With no basis given, a client with exactly one active retainer is billed that retainer and a client with none is
     * billed for chargeable work (the behaviour before Chunk 8); several retainers force an explicit choice.
     */
    @Transactional
    public FmInvoiceResponse generateInvoice(TenantId tenantId, UUID clientId, LocalDate periodStart, LocalDate periodEnd,
                                             UUID agreementId, String basis) {
        accessPolicy.scope(tenantId).requireClient(clientId);
        clientRepository.findActiveById(tenantId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("FmClient", clientId.toString()));
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("periodEnd must not be before periodStart");
        }
        FmBillingSettings settings = FmBillingSettings.resolve(profileRepository.findByTenant(tenantId));

        List<FmServiceAgreement> retainers = serviceAgreementRepository.findActiveAsOfDate(tenantId, clientId, periodStart)
                .stream().filter(FmServiceAgreement::isRetainer).toList();
        String resolvedBasis = resolveBasis(basis, agreementId, retainers);

        FmServiceAgreement retainer = null;
        BigDecimal subtotal;
        List<FmWorkOrder> billableWorkOrders;

        if ("RETAINER".equals(resolvedBasis)) {
            retainer = pickRetainer(agreementId, retainers);
            if (invoiceRepository.existsOverlappingForAgreement(tenantId, retainer.getId(), periodStart, periodEnd)) {
                throw new IllegalStateException("This retainer already has an invoice covering part of "
                        + periodStart + " to " + periodEnd + " — a retainer cannot be invoiced twice for the same period");
            }
            subtotal = retainer.getMonthlyFee();
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

        BigDecimal vatAmount = settings.vatOn(subtotal);
        String invoiceNumber = numberGenerator.nextInvoiceNumber(tenantId);
        LocalDate issueDate = LocalDate.now();
        FmInvoice invoice = FmInvoice.create(tenantId, clientId, invoiceNumber, periodStart, periodEnd, issueDate,
                issueDate.plusDays(settings.paymentTermsDays()), subtotal, vatAmount, settings.effectiveVatRate(),
                TenantContext.getCurrentUserId(), retainer != null ? retainer.getId() : null);
        invoice = invoiceRepository.save(invoice);

        for (FmWorkOrder wo : billableWorkOrders) {
            wo.markInvoiced(invoice.getId());
            workOrderRepository.save(wo);
        }

        auditService.record(tenantId, "INVOICE_PREPARED", "INVOICE", invoice.getId(),
                invoiceNumber + " client=" + clientId + " period=" + periodStart + ".." + periodEnd
                        + " subtotal=" + subtotal + " vat=" + vatAmount + " total=" + invoice.getTotal()
                        + " basis=" + (retainer != null
                        ? "RETAINER(" + (retainer.getName() != null ? retainer.getName() : retainer.getId()) + ")"
                        : "WORK_ORDERS(" + billableWorkOrders.size() + ")"));
        log.info("FM invoice prepared number={} client={} tenant={} subtotal={} vat={} workOrders={}",
                invoiceNumber, clientId, tenantId.getValue(), subtotal, vatAmount, billableWorkOrders.size());
        return toResponse(invoice);
    }

    private String resolveBasis(String basis, UUID agreementId, List<FmServiceAgreement> retainers) {
        String b = basis == null || basis.isBlank() ? null : basis.trim().toUpperCase();
        if (b != null && !b.equals("RETAINER") && !b.equals("WORK_ORDERS")) {
            throw new IllegalArgumentException("basis must be RETAINER or WORK_ORDERS");
        }
        if (b == null) {
            if (agreementId != null) return "RETAINER";
            if (retainers.isEmpty()) return "WORK_ORDERS";
            if (retainers.size() == 1) return "RETAINER";
            throw new IllegalStateException("This client has several active retainers — give agreementId to bill one of them, "
                    + "or basis=WORK_ORDERS to bill chargeable work");
        }
        if (b.equals("WORK_ORDERS") && agreementId != null) {
            throw new IllegalArgumentException("agreementId only applies to RETAINER billing");
        }
        return b;
    }

    private FmServiceAgreement pickRetainer(UUID agreementId, List<FmServiceAgreement> retainers) {
        if (agreementId != null) {
            return retainers.stream().filter(a -> a.getId().equals(agreementId)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "That agreement is not an active retainer for this client at the start of the period"));
        }
        if (retainers.size() == 1) return retainers.get(0);
        if (retainers.isEmpty()) throw new IllegalStateException("This client has no active retainer for the period");
        throw new IllegalStateException("This client has several active retainers — give agreementId");
    }

    @Transactional
    public FmInvoiceResponse cancelDraft(TenantId tenantId, UUID invoiceId, String reason) {
        FmInvoice invoice = findInvoice(tenantId, invoiceId, accessPolicy.scope(tenantId));
        invoice.cancelDraft(reason);
        invoiceRepository.save(invoice);

        List<FmWorkOrder> released = workOrderRepository.findByInvoiceId(tenantId, invoiceId);
        for (FmWorkOrder wo : released) {
            wo.releaseFromInvoice();
            workOrderRepository.save(wo);
        }
        auditService.record(tenantId, "INVOICE_CANCELLED", "INVOICE", invoiceId,
                invoice.getInvoiceNumber() + " reason=" + reason + " releasedWorkOrders=" + released.size());
        return toResponse(invoice);
    }

    // ── Approve, send, pay ───────────────────────────────────────────────────

    @Transactional
    public FmInvoiceResponse approveInvoice(TenantId tenantId, UUID invoiceId) {
        FmInvoice invoice = findInvoice(tenantId, invoiceId, accessPolicy.scope(tenantId));
        UUID approver = TenantContext.getCurrentUserId();
        enforceSegregation(tenantId, invoice, approver);

        invoice.approve(approver);
        invoiceRepository.save(invoice);
        auditService.record(tenantId, "INVOICE_APPROVED", "INVOICE", invoiceId,
                invoice.getInvoiceNumber() + " client=" + invoice.getClientId() + " total=" + invoice.getTotal()
                        + " glStatus=" + invoice.getGlStatus());
        if ("PENDING".equals(invoice.getGlStatus())) {
            glPostingService.postInvoiceAfterCommit(tenantId, invoiceId);
        }
        return toResponse(invoice);
    }

    @Transactional
    public FmInvoiceResponse sendInvoice(TenantId tenantId, UUID invoiceId) {
        FmInvoice invoice = findInvoice(tenantId, invoiceId, accessPolicy.scope(tenantId));
        invoice.markSent();
        invoiceRepository.save(invoice);
        auditService.record(tenantId, "INVOICE_SENT", "INVOICE", invoiceId,
                invoice.getInvoiceNumber() + " client=" + invoice.getClientId() + " total=" + invoice.getTotal());
        return toResponse(invoice);
    }

    @Transactional
    public FmInvoiceResponse recordPayment(TenantId tenantId, UUID invoiceId, RecordFmPaymentRequest req) {
        FmInvoice invoice = findInvoice(tenantId, invoiceId, accessPolicy.scope(tenantId));
        BigDecimal applied = invoice.recordPayment(req.amount());
        invoiceRepository.save(invoice);

        FmInvoicePayment payment = FmInvoicePayment.create(tenantId, invoiceId, applied, req.paymentDate(),
                req.method(), req.reference(), TenantContext.getCurrentUserId());
        paymentRepository.save(payment);

        auditService.record(tenantId, "PAYMENT_RECORDED", "INVOICE", invoiceId,
                invoice.getInvoiceNumber() + " amount=" + applied + " date=" + payment.getPaymentDate()
                        + " method=" + payment.getMethod() + " newStatus=" + invoice.getStatus() + " balance=" + invoice.balance());
        log.info("FM payment recorded invoice={} tenant={} amount={} newStatus={}",
                invoice.getInvoiceNumber(), tenantId.getValue(), applied, invoice.getStatus());

        glPostingService.postPaymentAfterCommit(tenantId, payment.getId());
        return toResponse(invoice);
    }

    // ── Ledger exceptions ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<FmGlExceptionResponse> glExceptions(TenantId tenantId) {
        requireUnrestricted(tenantId);
        return glPostingService.exceptions(tenantId);
    }

    @Transactional
    public int retryGlExceptions(TenantId tenantId) {
        requireUnrestricted(tenantId);
        int queued = glPostingService.resetFailedAndRetry(tenantId);
        auditService.record(tenantId, "GL_RETRY_REQUESTED", "BILLING", null, "requeued=" + queued);
        return queued;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Ledger exceptions span every client, so they are only available to callers without a client restriction. */
    private void requireUnrestricted(TenantId tenantId) {
        if (!accessPolicy.scope(tenantId).unrestricted()) {
            throw new HandyFlowException("Ledger exceptions cover all clients and are not available to client-restricted users",
                    HttpStatus.FORBIDDEN, "SCOPE_RESTRICTED");
        }
    }

    /** The preparer may not approve their own invoice. A FACILITIESMANAGEMENT_ADMIN may; that override is audited. */
    private void enforceSegregation(TenantId tenantId, FmInvoice invoice, UUID approver) {
        UUID preparer = invoice.getPreparedByUserId();
        if (preparer == null || !preparer.equals(approver)) return;
        if (accessPolicy.hasAuthority("FACILITIESMANAGEMENT_ADMIN")) {
            auditService.record(tenantId, "INVOICE_SELF_APPROVED", "INVOICE", invoice.getId(),
                    invoice.getInvoiceNumber() + " — administrator override of preparer/approver separation");
            return;
        }
        throw new HandyFlowException("You cannot approve an invoice you prepared — another person must approve it",
                HttpStatus.FORBIDDEN, "SEGREGATION_OF_DUTIES");
    }

    FmInvoice findInvoice(TenantId tenantId, UUID id, FmAccessScope scope) {
        FmInvoice invoice = invoiceRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmInvoice", id.toString()));
        if (!scope.canSeeClient(invoice.getClientId())) {
            throw new ResourceNotFoundException("FmInvoice", id.toString());
        }
        return invoice;
    }

    private FmInvoicePaymentResponse toResponse(FmInvoicePayment p) {
        return new FmInvoicePaymentResponse(p.getId(), p.getInvoiceId(), p.getAmount(), p.getPaymentDate(), p.getMethod(),
                p.getReference(), p.getRecordedByUserId(), p.getCreatedAt(), p.getGlStatus());
    }

    private FmInvoiceResponse toResponse(FmInvoice i) {
        return new FmInvoiceResponse(i.getId(), i.getClientId(), i.getInvoiceNumber(), i.getPeriodStart(), i.getPeriodEnd(),
                i.getIssueDate(), i.getDueDate(), i.getSubtotal(), i.getVatAmount(), i.getTotal(), i.getAmountPaid(),
                i.balance(), i.getStatus(), i.getCreatedAt(),
                i.getVatRate(), i.getPreparedByUserId(), i.getApprovedByUserId(), i.getApprovedAt(),
                i.getGlStatus(), i.getGlLastError(), i.getAgreementId());
    }
}