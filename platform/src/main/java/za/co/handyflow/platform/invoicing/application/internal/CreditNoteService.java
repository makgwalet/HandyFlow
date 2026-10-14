package za.co.handyflow.platform.invoicing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.approvals.dto.ApprovalStepResponse;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.invoicing.CreditNoteIssuedEvent;
import za.co.handyflow.platform.invoicing.domain.model.CreditNote;
import za.co.handyflow.platform.invoicing.domain.model.Invoice;
import za.co.handyflow.platform.invoicing.domain.repository.CreditNoteRepository;
import za.co.handyflow.platform.invoicing.domain.repository.InvoiceRepository;
import za.co.handyflow.platform.invoicing.dto.CreateCreditNoteRequest;
import za.co.handyflow.platform.invoicing.dto.CreditNoteResponse;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

/** FIX: "no credit note PDF" gap. See CreditNote for the design rationale. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditNoteService {

    private final CreditNoteRepository creditNoteRepo;
    private final InvoiceRepository invoiceRepo;
    private final CreditNoteNumberGenerator numberGenerator;
    private final CreditNotePdfService pdfService;
    private final CrmFacade crmFacade;
    private final EmailService emailService;
    // FIX (VAT sweep, module 2): CreateCreditNoteRequest.vatRate() is
    // genuinely nullable ("defaults to 15% if null", per that DTO's own
    // comment) and was falling through to CreditNote.create()'s
    // hardcoded new BigDecimal("15.00") — a genuinely reachable gap, not
    // dead defensive code, same shape as the earlier
    // PosPurchaseOrderItem finding. Resolved here instead.
    private final VatRateProvider vatRateProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final ReceiptService receiptService;
    // Finance patch F9: credit notes above the approval limit wait for approval.
    private final ApprovalFacade approvalFacade;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private static final String MODULE = "invoicing", ENTITY = "CREDIT_NOTE";

    /** @deprecated finance patch F9: pass who raises it. */
    @Deprecated
    @Transactional
    public CreditNoteResponse createCreditNote(TenantId tenantId, UUID invoiceId, CreateCreditNoteRequest req) {
        return createCreditNote(tenantId, invoiceId, req, null);
    }

    /**
     * Raises a credit note (finance patch F9). It goes to the approval engine: below the approval limit (platform default
     * R 5,000 incl. VAT) it is issued at once; above it, it waits for someone with CREDIT_NOTE_APPROVE, who is never the
     * person who raised it unless nobody else can approve. Until issued it does not touch the invoice, is not emailed
     * and is not posted. Credit notes on an invoice, waiting or issued, never add up to more than it was for.
     */
    @Transactional
    public CreditNoteResponse createCreditNote(TenantId tenantId, UUID invoiceId, CreateCreditNoteRequest req, UUID raisedBy) {
        Invoice invoice = invoiceRepo.findActiveByIdWithLineItems(tenantId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId.toString()));

        if (invoice.getIssuedAt() == null) {
            throw new IllegalStateException(
                    "Cannot issue a credit note against a draft invoice — issue the invoice first");
        }

        String number = numberGenerator.next(tenantId);
        BigDecimal vatRate = req.vatRate() != null ? req.vatRate() : vatRateProvider.ratePercent();
        CreditNote creditNote = CreditNote.create(
                tenantId, invoiceId, number, req.reason(), req.description(),
                req.amount(), vatRate, invoice.getCurrency());
        creditNote.raisedBy(raisedBy);

        BigDecimal alreadyCredited = creditNoteRepo.findByInvoice(tenantId, invoiceId).stream()
                .filter(cn -> !"REJECTED".equals(cn.getStatus()))
                .map(CreditNote::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        String tooMuch = ReceiptRules.creditNoteProblem(invoice.getTotal(), alreadyCredited, creditNote.getTotal());
        if (tooMuch != null) throw new IllegalStateException(tooMuch);
        creditNoteRepo.save(creditNote);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("totalAmount", creditNote.getTotal());
        metadata.put("invoiceId", invoiceId.toString());
        if (raisedBy != null && otherHolders(tenantId, "CREDIT_NOTE_APPROVE", raisedBy) > 0) {
            metadata.put("excludedActors", List.of(raisedBy.toString()));
        }
        ApprovalRequestResponse request = approvalFacade.submit(tenantId, MODULE, ENTITY, creditNote.getId(), raisedBy, metadata);
        if ("APPROVED".equals(request.status())) {
            issueNow(tenantId, creditNote, invoice, null);
        } else {
            log.info("Credit note={} invoice={} total={} waiting for approval", number, invoice.getInvoiceNumber(), creditNote.getTotal());
        }
        return toResponse(creditNote, invoice.getInvoiceNumber());
    }

    /** Gives this person's approval; the credit note is issued when the last step is done (finance patch F9). */
    @Transactional
    public CreditNoteResponse approve(TenantId tenantId, UUID id, UUID by) {
        CreditNote cn = pending(tenantId, id);
        ApprovalRequestResponse result = actOnPendingStep(tenantId, cn, by, "APPROVE", null);
        if ("APPROVED".equals(result.status())) completeApproval(tenantId, id, by);
        return getCreditNote(tenantId, id);
    }

    /** Rejects a credit note waiting for approval, with the reason (finance patch F9). */
    @Transactional
    public CreditNoteResponse reject(TenantId tenantId, UUID id, UUID by, String reason) {
        if (reason == null || reason.trim().length() < 5)
            throw new IllegalStateException("Say why the credit note is rejected (at least 5 characters)");
        CreditNote cn = pending(tenantId, id);
        actOnPendingStep(tenantId, cn, by, "REJECT", reason.trim());
        rejectFromEngine(tenantId, id, by, reason.trim());
        return getCreditNote(tenantId, id);
    }

    /** Issues an approved credit note, once (also called when approval finishes in the Approvals inbox). */
    @Transactional
    public void completeApproval(TenantId tenantId, UUID id, UUID by) {
        CreditNote cn = creditNoteRepo.findActiveById(tenantId, id).orElse(null);
        if (cn == null || !"PENDING_APPROVAL".equals(cn.getStatus())) return;
        Invoice invoice = invoiceRepo.findActiveByIdWithLineItems(tenantId, cn.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", cn.getInvoiceId().toString()));
        issueNow(tenantId, cn, invoice, by);
    }

    /** Rejects, once (also called when rejection happens in the Approvals inbox). */
    @Transactional
    public void rejectFromEngine(TenantId tenantId, UUID id, UUID by, String reason) {
        CreditNote cn = creditNoteRepo.findActiveById(tenantId, id).orElse(null);
        if (cn == null || !"PENDING_APPROVAL".equals(cn.getStatus())) return;
        cn.reject(by, reason != null ? reason : rejectionComment(tenantId, id));
        creditNoteRepo.save(cn);
        log.info("Credit note={} rejected by={}", cn.getCreditNoteNumber(), by);
    }

    /** Applies the credit note to its invoice, puts any rest on the customer's account, posts it and emails it. */
    private void issueNow(TenantId tenantId, CreditNote creditNote, Invoice invoice, UUID by) {
        creditNote.issue(by);
        creditNoteRepo.save(creditNote);
        String number = creditNote.getCreditNoteNumber();

        BigDecimal[] split = ReceiptRules.splitCredit(creditNote.getTotal(), invoice.getBalanceDue());
        invoice.applyCredit(split[0]);
        invoiceRepo.save(invoice);
        if (split[1].signum() > 0) {
            // A walk-in client has no account, so their credit is kept against the credit note (customer left empty) and
            // can only be refunded.
            receiptService.creditFromCreditNote(tenantId, invoice.getCustomerId(), creditNote.getId(), number, split[1], by);
        }
        log.info("Issued credit note={} invoice={} total={}", number, invoice.getInvoiceNumber(), creditNote.getTotal());

        // Finance patch F1: post the credit note to the ledger (reverses AR, Revenue and VAT output).
        eventPublisher.publishEvent(CreditNoteIssuedEvent.of(
                tenantId, creditNote.getId(), number, invoice.getId(), invoice.getInvoiceNumber(),
                creditNote.getSubtotal(), creditNote.getVatTotal(), creditNote.getTotal()));

        try {
            String clientEmail = resolveClientEmail(invoice, tenantId);
            if (clientEmail != null && !clientEmail.isBlank()) {
                String clientName = resolveClientName(invoice, tenantId);
                byte[] pdfBytes = pdfService.generateCreditNotePdf(creditNote.getId(), tenantId);
                String amountStr = "R " + String.format(Locale.US, "%,.2f", creditNote.getTotal());
                emailService.sendWithAttachment(
                        clientEmail,
                        "Credit note " + number + " — " + invoice.getInvoiceNumber(),
                        "<p>Dear " + clientName + ",</p>"
                                + "<p>A credit note has been issued against invoice " + invoice.getInvoiceNumber() + ".</p>"
                                + "<p>Amount: " + amountStr + "</p>"
                                + "<p>Reason: " + (creditNote.getReason() != null ? creditNote.getReason() : "—") + "</p>",
                        number + ".pdf",
                        pdfBytes
                );
            } else {
                log.warn("Credit note={} has no resolvable client email (invoice={}) — not emailed", number, invoice.getId());
            }
        } catch (Exception e) {
            log.warn("Credit note email not sent for credit note={}: {}", creditNote.getId(), e.getMessage());
        }
    }

    private CreditNote pending(TenantId tenantId, UUID id) {
        CreditNote cn = creditNoteRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("CreditNote", id.toString()));
        if (!"PENDING_APPROVAL".equals(cn.getStatus()))
            throw new IllegalStateException("Only a credit note waiting for approval can be approved or rejected");
        return cn;
    }

    private ApprovalRequestResponse actOnPendingStep(TenantId tenantId, CreditNote cn, UUID actor, String decision, String comment) {
        ApprovalRequestResponse request = approvalFacade.getLatestRequestForEntity(tenantId, MODULE, ENTITY, cn.getId())
                .filter(r -> "SUBMITTED".equals(r.status()) || "IN_PROGRESS".equals(r.status()))
                .orElseThrow(() -> new IllegalStateException("This credit note has no approval waiting"));
        ApprovalStepResponse step = request.steps().stream()
                .filter(st -> "PENDING".equals(st.status()))
                .min(java.util.Comparator.comparingInt(ApprovalStepResponse::stepOrder))
                .orElseThrow(() -> new IllegalStateException("This credit note's approval has no step waiting"));
        var auth = SecurityContextHolder.getContext().getAuthentication();
        List<String> authorities = auth == null ? List.of() : auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        return approvalFacade.actOnStep(tenantId, step.id(), actor, authorities, decision, comment, null);
    }

    private String rejectionComment(TenantId tenantId, UUID id) {
        return approvalFacade.getLatestRequestForEntity(tenantId, MODULE, ENTITY, id)
                .flatMap(r -> r.steps().stream().filter(st -> "REJECTED".equals(st.status())).findFirst())
                .map(ApprovalStepResponse::comment).orElse(null);
    }

    /** Active people, other than {@code actor}, who hold {@code permission}: when none, the raiser may approve. */
    private long otherHolders(TenantId tenantId, String permission, UUID actor) {
        Long n = jdbc.queryForObject("""
                SELECT count(DISTINCT u.id) FROM users u
                  JOIN user_roles ur ON ur.user_id = u.id
                  JOIN role_permissions rp ON rp.role_id = ur.role_id
                  JOIN permissions p ON p.id = rp.permission_id
                 WHERE u.tenant_id = ? AND u.status = 'ACTIVE' AND p.name = ? AND u.id <> ?""",
                Long.class, tenantId.getValue(), permission, actor);
        return n != null ? n : 0;
    }

    @Transactional(readOnly = true)
    public Page<CreditNoteResponse> getCreditNotes(TenantId tenantId, Pageable pageable) {
        // NOTE: resolves invoiceNumber per row rather than batch-loading —
        // an accepted N+1 here given credit notes are a low-volume document
        // type, not a hot list like invoices/claims. Worth batching if this
        // list ever gets large.
        return creditNoteRepo.findAllActive(tenantId, pageable)
                .map(cn -> toResponseResolvingInvoiceNumber(tenantId, cn));
    }

    @Transactional(readOnly = true)
    public List<CreditNoteResponse> getCreditNotesForInvoice(TenantId tenantId, UUID invoiceId) {
        Invoice invoice = invoiceRepo.findActiveByIdWithLineItems(tenantId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId.toString()));
        return creditNoteRepo.findByInvoice(tenantId, invoiceId).stream()
                .map(cn -> toResponse(cn, invoice.getInvoiceNumber()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CreditNoteResponse getCreditNote(TenantId tenantId, UUID id) {
        CreditNote cn = creditNoteRepo.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("CreditNote", id.toString()));
        return toResponseResolvingInvoiceNumber(tenantId, cn);
    }

    private CreditNoteResponse toResponseResolvingInvoiceNumber(TenantId tenantId, CreditNote cn) {
        String invoiceNumber = invoiceRepo.findActiveByIdWithLineItems(tenantId, cn.getInvoiceId())
                .map(Invoice::getInvoiceNumber).orElse(null);
        return toResponse(cn, invoiceNumber);
    }

    private CreditNoteResponse toResponse(CreditNote cn, String invoiceNumber) {
        return new CreditNoteResponse(cn.getId(), cn.getCreditNoteNumber(), cn.getInvoiceId(), invoiceNumber,
                cn.getReason(), cn.getDescription(), cn.getSubtotal(), cn.getVatTotal(), cn.getTotal(),
                cn.getCurrency(), cn.getIssuedAt(), cn.getCreatedAt(),
                cn.getStatus(), cn.getCreatedBy(), cn.getDecidedAt(), cn.getRejectionReason());
    }

    /** Same resolution pattern as InvoiceService/QuoteService. */
    private String resolveClientEmail(Invoice invoice, TenantId tenantId) {
        if (invoice.getCustomerId() != null) {
            return crmFacade.findCustomerById(tenantId, invoice.getCustomerId())
                    .map(c -> c.email()).orElse(null);
        }
        return invoice.getWalkinClientEmail();
    }

    private String resolveClientName(Invoice invoice, TenantId tenantId) {
        if (invoice.getCustomerId() != null) {
            return crmFacade.findCustomerById(tenantId, invoice.getCustomerId())
                    .map(c -> c.name()).orElse("Customer");
        }
        return invoice.getWalkinClientName() != null ? invoice.getWalkinClientName() : "Client";
    }
}