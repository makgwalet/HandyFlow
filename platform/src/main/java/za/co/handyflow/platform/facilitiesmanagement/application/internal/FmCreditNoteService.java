package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmCreditNote;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmInvoice;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmCreditNoteRepository;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmInvoiceRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.CreateFmCreditNoteRequest;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmCreditNoteResponse;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Credit notes against issued invoices. Same controls as invoices: one person prepares, a different person approves
 * (an administrator may do both; that override is audited), everything is audited, and an approved credit note
 * reduces the invoice balance and posts to the ledger after commit (DR revenue and output VAT / CR receivables).
 * Limited to the caller's client scope.
 * <p>
 * Not covered: refunding money the client has already paid. A credit note only ever reduces what is still owed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FmCreditNoteService {

    private final FmCreditNoteRepository creditNoteRepository;
    private final FmInvoiceRepository invoiceRepository;
    private final FmNumberGenerator numberGenerator;
    private final FmGlPostingService glPostingService;
    private final FmAccessPolicy accessPolicy;
    private final FmAuditService auditService;
    private final FmAlerts alerts;

    @Transactional(readOnly = true)
    public List<FmCreditNoteResponse> forInvoice(TenantId tenantId, UUID invoiceId) {
        FmInvoice invoice = findInvoice(tenantId, invoiceId);
        return creditNoteRepository.findForInvoice(tenantId, invoiceId).stream().map(c -> toResponse(c, invoice)).toList();
    }

    @Transactional(readOnly = true)
    public FmCreditNoteResponse get(TenantId tenantId, UUID id) {
        FmCreditNote note = findNote(tenantId, id);
        return toResponse(note, findInvoice(tenantId, note.getInvoiceId()));
    }

    /** Throws NOT FOUND unless the credit note exists and the caller may see its client (used by the PDF endpoint). */
    @Transactional(readOnly = true)
    public void requireAccessible(TenantId tenantId, UUID id) {
        findNote(tenantId, id);
    }

    @Transactional
    public FmCreditNoteResponse create(TenantId tenantId, UUID invoiceId, CreateFmCreditNoteRequest req) {
        FmInvoice invoice = findInvoice(tenantId, invoiceId);
        boolean full = Boolean.TRUE.equals(req.creditFullBalance());
        if (full == (req.subtotal() != null)) {
            throw new IllegalArgumentException("Give either a subtotal to credit, or creditFullBalance=true — not both, not neither");
        }
        java.util.function.Supplier<String> number = () -> numberGenerator.nextCreditNoteNumber(tenantId);
        UUID me = TenantContext.getCurrentUserId();
        FmCreditNote note = full
                ? FmCreditNote.forFullBalance(invoice, number, FmTime.today(), req.reason(), me)
                : FmCreditNote.forSubtotal(invoice, number, FmTime.today(), req.subtotal(), req.reason(), me);
        creditNoteRepository.save(note);

        auditService.record(tenantId, "CREDIT_NOTE_PREPARED", "CREDIT_NOTE", note.getId(),
                note.getCreditNoteNumber() + " invoice=" + invoice.getInvoiceNumber() + " subtotal=" + note.getSubtotal() + " vat=" + note.getVatAmount()
                        + " total=" + note.getTotal() + " reason=" + note.getReason());
        log.info("FM credit note prepared number={} invoice={} tenant={} total={}",
                note.getCreditNoteNumber(), invoice.getInvoiceNumber(), tenantId.getValue(), note.getTotal());
        alerts.toAdmins(tenantId, NotificationType.FM_CREDIT_NOTE_AWAITING_APPROVAL,
                "Credit note awaiting approval: " + note.getCreditNoteNumber(),
                note.getCreditNoteNumber() + " for R " + note.getTotal() + " against " + invoice.getInvoiceNumber()
                        + " needs approving by someone other than its preparer.",
                "/facilitiesmanagement/credit-notes/" + note.getId(), note.getId().toString());
        return toResponse(note, invoice);
    }

    @Transactional
    public FmCreditNoteResponse approve(TenantId tenantId, UUID id) {
        FmCreditNote note = findNote(tenantId, id);
        FmInvoice invoice = findInvoice(tenantId, note.getInvoiceId());
        UUID approver = TenantContext.getCurrentUserId();
        // Check the state first: applying the credit to the invoice before the note rejects an already-approved or
        // cancelled note would leave the invoice mutated if anything downstream ever stopped relying on rollback.
        if (!"DRAFT".equals(note.getStatus())) {
            throw new IllegalStateException("Only a DRAFT credit note can be approved (this one is " + note.getStatus() + ")");
        }
        enforceSegregation(tenantId, note, approver);

        // The invoice's open balance may have moved since the draft was written (payments, other credits): applyCredit re-checks.
        invoice.applyCredit(note.getTotal());
        note.approve(approver);
        invoiceRepository.save(invoice);
        creditNoteRepository.save(note);

        auditService.record(tenantId, "CREDIT_NOTE_APPROVED", "CREDIT_NOTE", id,
                note.getCreditNoteNumber() + " invoice=" + invoice.getInvoiceNumber() + " total=" + note.getTotal()
                        + " invoiceStatus=" + invoice.getStatus() + " invoiceBalance=" + invoice.balance());
        glPostingService.postCreditNoteAfterCommit(tenantId, id);
        return toResponse(note, invoice);
    }

    @Transactional
    public FmCreditNoteResponse cancel(TenantId tenantId, UUID id, String reason) {
        FmCreditNote note = findNote(tenantId, id);
        FmInvoice invoice = findInvoice(tenantId, note.getInvoiceId());
        note.cancelDraft(reason);
        creditNoteRepository.save(note);
        auditService.record(tenantId, "CREDIT_NOTE_CANCELLED", "CREDIT_NOTE", id,
                note.getCreditNoteNumber() + " invoice=" + invoice.getInvoiceNumber() + " reason=" + reason);
        return toResponse(note, invoice);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void enforceSegregation(TenantId tenantId, FmCreditNote note, UUID approver) {
        UUID preparer = note.getPreparedByUserId();
        if (preparer == null || !preparer.equals(approver)) return;
        if (accessPolicy.hasAuthority("FACILITIESMANAGEMENT_ADMIN")) {
            auditService.record(tenantId, "CREDIT_NOTE_SELF_APPROVED", "CREDIT_NOTE", note.getId(),
                    note.getCreditNoteNumber() + " — administrator override of preparer/approver separation");
            return;
        }
        throw new HandyFlowException("You cannot approve a credit note you prepared — another person must approve it",
                HttpStatus.FORBIDDEN, "SEGREGATION_OF_DUTIES");
    }

    private FmInvoice findInvoice(TenantId tenantId, UUID invoiceId) {
        FmAccessScope scope = accessPolicy.scope(tenantId);
        FmInvoice invoice = invoiceRepository.findActiveById(tenantId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("FmInvoice", invoiceId.toString()));
        if (!scope.canSeeClient(invoice.getClientId())) {
            throw new ResourceNotFoundException("FmInvoice", invoiceId.toString());
        }
        return invoice;
    }

    private FmCreditNote findNote(TenantId tenantId, UUID id) {
        FmAccessScope scope = accessPolicy.scope(tenantId);
        FmCreditNote note = creditNoteRepository.findActiveById(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("FmCreditNote", id.toString()));
        if (!scope.canSeeClient(note.getClientId())) {
            throw new ResourceNotFoundException("FmCreditNote", id.toString());
        }
        return note;
    }

    private FmCreditNoteResponse toResponse(FmCreditNote c, FmInvoice invoice) {
        return new FmCreditNoteResponse(c.getId(), c.getCreditNoteNumber(), c.getInvoiceId(), invoice.getInvoiceNumber(),
                c.getClientId(), c.getIssueDate(), c.getReason(), c.getSubtotal(), c.getVatRate(), c.getVatAmount(),
                c.getTotal(), c.getStatus(), c.getPreparedByUserId(), c.getApprovedByUserId(), c.getApprovedAt(),
                c.getGlStatus(), c.getGlLastError(), c.getCreatedAt());
    }
}
