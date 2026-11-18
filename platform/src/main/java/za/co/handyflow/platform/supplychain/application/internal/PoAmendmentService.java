package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.approvals.dto.ApprovalStepResponse;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.VatRateProvider;
import za.co.handyflow.platform.supplychain.application.internal.PoAmendmentRules.Change;
import za.co.handyflow.platform.supplychain.domain.enums.InvoiceStatus;
import za.co.handyflow.platform.supplychain.domain.enums.PoStatus;
import za.co.handyflow.platform.supplychain.domain.model.*;
import za.co.handyflow.platform.supplychain.domain.repository.*;
import za.co.handyflow.platform.supplychain.dto.CreatePoAmendmentRequest;
import za.co.handyflow.platform.supplychain.dto.PoAmendmentResponse;
import za.co.handyflow.platform.supplychain.dto.UpdateDraftLineRequest;

import java.math.BigDecimal;
import java.util.*;

/**
 * Controlled changes to purchase orders.
 * <p>
 * DRAFT POs: lines can be edited/removed freely (previously a mistake in a draft line could
 * only be fixed by abandoning the PO).
 * Approved POs: lines and the amount can NEVER change directly. A change is raised as an
 * amendment; if it is material (any line change, or delivery terms when configured) it goes
 * through the shared approval engine and is only applied once approved — so the approved
 * amount can't be altered behind the approver's back. Every applied change writes an
 * immutable revision snapshot (PoVersionService) and re-notifies the supplier if the PO had
 * already been sent.
 * <p>
 * Receiving is not blocked while an amendment is pending: goods keep arriving against the
 * current revision, and the amendment is re-validated against reality when approved.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PoAmendmentService {

    private static final String APPROVALS_MODULE = "supplychain";
    private static final String APPROVALS_ENTITY_TYPE = "PO_AMENDMENT";
    private static final Set<PoStatus> AMENDABLE = EnumSet.of(
            PoStatus.APPROVED, PoStatus.SENT, PoStatus.ACKNOWLEDGED, PoStatus.PARTIALLY_RECEIVED);

    private final ScmSeparationOfDuties sod;
    private final ScPurchaseOrderRepository poRepo;
    private final ScPoLineRepository poLineRepo;
    private final ScPoAmendmentRepository amendmentRepo;
    private final ScPoAmendmentLineRepository amendmentLineRepo;
    private final ScSupplierInvoiceLineRepository invoiceLineRepo;
    private final ScGrLineRepository grLineRepo;
    private final ScStockLocationRepository locationRepo;
    private final ScSupplierRepository supplierRepo;
    private final PoVersionService versionService;
    // NEW (chunk 7): an amendment that raises the PO total is re-checked against budgets and the commitment is restated.
    private final BudgetService budgetService;
    private final ScmService scmService;              // PDF generation only
    private final ApprovalFacade approvalFacade;
    private final EmailService emailService;
    private final VatRateProvider vatRateProvider;
    private final org.springframework.transaction.PlatformTransactionManager txManager;


    /** app.scm.amend.delivery-terms-material=true -> changing the required-by date or delivery location needs approval. */
    @Value("${app.scm.amend.delivery-terms-material:true}")
    private boolean deliveryTermsMaterial;

    // ── DRAFT line editing ────────────────────────────────────────────────────

    @Transactional
    public ScPurchaseOrder updateDraftLine(TenantId tenantId, UUID poId, UUID lineId, UpdateDraftLineRequest req) {
        ScPurchaseOrder po = requireDraft(tenantId, poId);
        ScPoLine line = poLineRepo.findByIdAndTenantId(lineId, tenantId.getValue())
                .filter(l -> l.getPurchaseOrderId().equals(poId))
                .orElseThrow(() -> new HandyFlowException("PO line not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (req.qtyOrdered() != null && req.qtyOrdered().signum() <= 0)
            throw bad("Quantity must be greater than zero (delete the line instead)");
        if (req.unitCost() != null && req.unitCost().signum() < 0) throw bad("Unit cost cannot be negative");
        if (req.unitCost() != null && po.getRequisitionId() != null && req.unitCost().compareTo(line.getUnitCost()) != 0)
            throw new HandyFlowException("This order was raised from a requisition, so its price is set (with a reason) when the "
                    + "requisition is converted. Cancel this draft and convert again at the new price.",
                    HttpStatus.CONFLICT, "REQUISITION_PRICE_LOCKED");
        line.amend(req.qtyOrdered(), req.unitCost(), req.vatRate());
        poLineRepo.save(line);
        recalcTotals(po);
        return poRepo.save(po);
    }

    @Transactional
    public ScPurchaseOrder removeDraftLine(TenantId tenantId, UUID poId, UUID lineId) {
        ScPurchaseOrder po = requireDraft(tenantId, poId);
        ScPoLine line = poLineRepo.findByIdAndTenantId(lineId, tenantId.getValue())
                .filter(l -> l.getPurchaseOrderId().equals(poId))
                .orElseThrow(() -> new HandyFlowException("PO line not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        poLineRepo.delete(line);
        recalcTotals(po);
        return poRepo.save(po);
    }

    // ── Raise an amendment ────────────────────────────────────────────────────

    @Transactional
    public PoAmendmentResponse create(TenantId tenantId, UUID userId, String userName, UUID poId,
                                      CreatePoAmendmentRequest req) {
        UUID tid = tenantId.getValue();
        if (req.reason() == null || req.reason().isBlank())
            throw new HandyFlowException("A reason for the amendment is required", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");

        ScPurchaseOrder po = poRepo.lockByTenantIdAndId(tid, poId)
                .orElseThrow(() -> new HandyFlowException("Purchase order not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!AMENDABLE.contains(po.getStatus()))
            throw new HandyFlowException("A PO that is " + po.getStatus() + " cannot be amended"
                    + (po.getStatus() == PoStatus.DRAFT ? " — edit its lines directly" : ""),
                    HttpStatus.CONFLICT, "PO_NOT_AMENDABLE");
        if (amendmentRepo.findOpenForPo(tid, poId).isPresent())
            throw new HandyFlowException("This PO already has an amendment awaiting approval — decide or withdraw it first",
                    HttpStatus.CONFLICT, "AMENDMENT_PENDING");

        if (req.deliverToLocation() != null)
            locationRepo.findActiveByTenantIdAndId(tid, req.deliverToLocation())
                    .orElseThrow(() -> new HandyFlowException("Delivery location not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        // Normalise line changes (resolve the VAT default for ADD up front so what is stored is what is applied).
        List<CreatePoAmendmentRequest.LineChange> reqLines = req.lineChanges() != null ? req.lineChanges() : List.of();
        List<Change> changes = new ArrayList<>();
        for (var c : reqLines) {
            boolean add = c.action() != null && c.action().trim().equalsIgnoreCase(PoAmendmentRules.ADD);
            changes.add(new Change(c.action() == null ? null : c.action().trim().toUpperCase(Locale.ROOT),
                    c.poLineId(), c.catalogueItemId(), c.itemName(), c.supplierSku(), c.qtyOrdered(), c.unitCost(),
                    add && c.vatRate() == null ? vatRateProvider.ratePercent() : c.vatRate()));
        }

        List<ScPoLine> current = poLineRepo.findByPurchaseOrderId(poId);
        PoAmendmentRules.Result result = evaluate(tid, po, current, changes);
        if (!result.errors().isEmpty())
            throw new HandyFlowException(String.join("; ", result.errors()), HttpStatus.BAD_REQUEST, "INVALID_AMENDMENT");

        boolean deliveryTermsChanged =
                (req.requiredByDate() != null && !req.requiredByDate().equals(po.getRequiredByDate()))
                        || (req.deliverToLocation() != null && !req.deliverToLocation().equals(po.getDeliverToLocation()));
        boolean textChanged = (req.notes() != null && !req.notes().equals(po.getNotes()))
                || (req.terms() != null && !req.terms().equals(po.getTerms()));
        if (!result.linesChanged() && !deliveryTermsChanged && !textChanged)
            throw new HandyFlowException("The amendment changes nothing", HttpStatus.BAD_REQUEST, "NO_CHANGES");

        boolean material = result.linesChanged() || (deliveryTermsChanged && deliveryTermsMaterial);

        List<String> parts = new ArrayList<>();
        if (result.linesChanged()) parts.add(result.summary() + ", total R " + po.getTotalAmount().toPlainString()
                + " -> R " + result.total().toPlainString());
        if (deliveryTermsChanged) parts.add("delivery terms changed");
        if (textChanged) parts.add("notes/terms changed");
        String summary = String.join("; ", parts);

        int number = Optional.ofNullable(amendmentRepo.maxNumberForPo(tid, poId)).orElse(0) + 1;
        ScPoAmendment amendment = amendmentRepo.save(ScPoAmendment.create(tid, poId, number, po.getRevisionNumber(),
                material, req.reason().trim(), summary, req.requiredByDate(), req.deliverToLocation(),
                req.notes(), req.terms(), po.getTotalAmount(),
                result.linesChanged() ? result.total() : po.getTotalAmount(), userId, userName));
        for (Change c : changes) {
            amendmentLineRepo.save(ScPoAmendmentLine.create(tid, amendment.getId(), c.action(), c.poLineId(),
                    c.catalogueItemId(), c.itemName(), c.supplierSku(), c.qtyOrdered(), c.unitCost(), c.vatRate()));
        }

        if (!material) {
            // Notes / terms only — nothing the approver signed off on changes, so apply now (still versioned).
            apply(tenantId, po, amendment, userId, userName, "Non-material change — applied without approval");
        } else {
            ApprovalRequestResponse submitted = approvalFacade.submit(tenantId, APPROVALS_MODULE,
                    APPROVALS_ENTITY_TYPE, amendment.getId(), userId, Map.of());
            if ("APPROVED".equals(submitted.status())) {
                // The engine's own rules decided no human step is needed.
                apply(tenantId, po, amendment, null, "Approval policy", "Auto-approved by approval policy");
            }
        }
        log.info("[SCM] PO {} amendment #{} raised by {} (material={}, status={})",
                po.getOrderNumber(), number, userName, material, amendment.getStatus());
        return toResponse(amendment);
    }

    // ── Decide ────────────────────────────────────────────────────────────────

    @Transactional
    public PoAmendmentResponse approve(TenantId tenantId, UUID amendmentId, UUID approverId, String approverName, String note) {
        UUID tid = tenantId.getValue();
        ScPoAmendment a = requirePending(tenantId, amendmentId);
        sod.require(tenantId, "SCM_ADMIN", a.getRequestedBy(), approverId,
                "You cannot approve an amendment you raised yourself", "ScPoAmendment", amendmentId, "PO amendment approval");

        ScPurchaseOrder po = poRepo.lockByTenantIdAndId(tid, a.getPurchaseOrderId())
                .orElseThrow(() -> new HandyFlowException("Purchase order not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));

        var existing = approvalFacade.getLatestRequestForEntity(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, amendmentId);
        ApprovalRequestResponse result;
        if (existing.isPresent() && isOpen(existing.get())) {
            ApprovalStepResponse step = firstPending(existing.get())
                    .orElseThrow(() -> new HandyFlowException("This amendment's approval request has no pending step — needs manual review",
                            HttpStatus.CONFLICT, "NO_PENDING_STEP"));
            result = approvalFacade.actOnStep(tenantId, step.id(), approverId, authorities(), "APPROVE", note, null);
        } else {
            result = approvalFacade.submit(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, amendmentId, approverId, Map.of());
            if (isOpen(result)) {
                ApprovalStepResponse step = firstPending(result)
                        .orElseThrow(() -> new IllegalStateException("A freshly-submitted request has no pending step"));
                result = approvalFacade.actOnStep(tenantId, step.id(), approverId, authorities(), "APPROVE", note, null);
            }
        }
        // Multi-step approvals: the amendment stays pending until the engine reports APPROVED.
        if (!"APPROVED".equals(result.status())) {
            log.info("[SCM] Amendment {} step approved by {} — approval status now {}", amendmentId, approverName, result.status());
            return toResponse(a);
        }
        apply(tenantId, po, a, approverId, approverName, note);
        return toResponse(a);
    }

    @Transactional
    public PoAmendmentResponse reject(TenantId tenantId, UUID amendmentId, UUID userId, String userName, String note) {
        ScPoAmendment a = requirePending(tenantId, amendmentId);
        if (note == null || note.isBlank())
            throw new HandyFlowException("A rejection reason is required", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        approvalFacade.getLatestRequestForEntity(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, amendmentId)
                .filter(this::isOpen)
                .flatMap(this::firstPending)
                .ifPresent(step -> approvalFacade.actOnStep(tenantId, step.id(), userId, authorities(), "REJECT", note, null));
        a.markRejected(userId, userName, note);
        amendmentRepo.save(a);
        log.info("[SCM] Amendment {} rejected by {}", amendmentId, userName);
        return toResponse(a);
    }

    @Transactional
    public PoAmendmentResponse withdraw(TenantId tenantId, UUID amendmentId, UUID userId, String userName, String note) {
        ScPoAmendment a = requirePending(tenantId, amendmentId);
        if (a.getRequestedBy() != null && !a.getRequestedBy().equals(userId))
            throw new HandyFlowException("Only the person who raised an amendment can withdraw it (others can reject it)",
                    HttpStatus.FORBIDDEN, "NOT_REQUESTER");
        // Close the open approval request so it leaves approvers' inboxes. The engine may refuse
        // a non-approver, which must not stop the withdrawal itself. It runs in its own transaction:
        // an exception swallowed inside the caller's transaction would mark it rollback-only and the
        // withdrawal would then fail at commit with UnexpectedRollbackException.
        try {
            new TransactionTemplate(txManager, requiresNew()).executeWithoutResult(st ->
                    approvalFacade.getLatestRequestForEntity(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, amendmentId)
                            .filter(this::isOpen)
                            .flatMap(this::firstPending)
                            .ifPresent(step -> approvalFacade.actOnStep(tenantId, step.id(), userId, authorities(),
                                    "REJECT", "Withdrawn by requester", null)));
        } catch (Exception e) {
            log.warn("[SCM] Could not close approval request for withdrawn amendment {}: {}", amendmentId, e.getMessage());
        }
        a.markWithdrawn(userId, userName, note);
        amendmentRepo.save(a);
        return toResponse(a);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PoAmendmentResponse> list(TenantId tenantId, UUID poId) {
        poRepo.findByTenantIdAndId(tenantId.getValue(), poId)
                .orElseThrow(() -> new HandyFlowException("Purchase order not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        return amendmentRepo.findAllForPo(tenantId.getValue(), poId).stream().map(this::toResponse).toList();
    }

    // ── Apply ─────────────────────────────────────────────────────────────────

    private void apply(TenantId tenantId, ScPurchaseOrder po, ScPoAmendment a, UUID byId, String byName, String note) {
        UUID tid = tenantId.getValue();
        if (!AMENDABLE.contains(po.getStatus()))
            throw new HandyFlowException("PO " + po.getOrderNumber() + " is now " + po.getStatus()
                    + " and can no longer be amended — reject or withdraw this amendment", HttpStatus.CONFLICT, "PO_NOT_AMENDABLE");

        List<ScPoAmendmentLine> lineChanges = amendmentLineRepo.findByAmendment(tid, a.getId());
        List<ScPoLine> current = poLineRepo.findByPurchaseOrderId(po.getId());

        // Receipts / invoices may have arrived since the amendment was raised — validate again.
        PoAmendmentRules.Result recheck = evaluate(tid, po, current, toChanges(lineChanges));
        if (!recheck.errors().isEmpty())
            throw new HandyFlowException("The amendment is no longer valid: " + String.join("; ", recheck.errors()),
                    HttpStatus.CONFLICT, "AMENDMENT_NO_LONGER_VALID");

        versionService.ensureBaseline(po, current, byId, byName);

        Map<UUID, ScPoLine> byLineId = new HashMap<>();
        current.forEach(l -> byLineId.put(l.getId(), l));
        for (ScPoAmendmentLine c : lineChanges) {
            switch (c.getAction()) {
                case PoAmendmentRules.UPDATE -> {
                    ScPoLine l = byLineId.get(c.getPoLineId());
                    l.amend(c.getQtyOrdered(), c.getUnitCost(), c.getVatRate());
                    poLineRepo.save(l);
                }
                case PoAmendmentRules.REMOVE -> poLineRepo.delete(byLineId.get(c.getPoLineId()));
                case PoAmendmentRules.ADD -> poLineRepo.save(ScPoLine.create(tid, po.getId(), c.getCatalogueItemId(),
                        c.getItemName(), c.getSupplierSku(), c.getQtyOrdered(), c.getUnitCost(), c.getVatRate()));
                default -> throw new IllegalStateException("Unknown amendment action " + c.getAction());
            }
        }
        po.applyAmendedHeader(a.getNewRequiredByDate(), a.getNewDeliverToLocation(), a.getNewNotes(), a.getNewTerms());

        List<ScPoLine> after = poLineRepo.findByPurchaseOrderId(po.getId());
        recalcTotals(po, after);
        budgetService.adjustCommitment(tenantId, po, byId, byName);
        // Quantity cuts can leave every line fully received.
        if (po.getStatus() == PoStatus.PARTIALLY_RECEIVED && after.stream().allMatch(ScPoLine::isFullyReceived)) {
            po.fullyReceive();
        }
        po.nextRevision();
        poRepo.save(po);
        versionService.record(po, after, a.getId(), a.getReason(), byId, byName);

        a.markApplied(byId, byName, note);
        amendmentRepo.save(a);
        log.info("[SCM] PO {} amended to revision {} (amendment #{}), total now R {}",
                po.getOrderNumber(), po.getRevisionNumber(), a.getAmendmentNumber(), po.getTotalAmount());

        if (po.getSentAt() != null) notifySupplier(tenantId, po, a);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PoAmendmentRules.Result evaluate(UUID tid, ScPurchaseOrder po, List<ScPoLine> current, List<Change> changes) {
        Set<UUID> invoiced = new HashSet<>(invoiceLineRepo.findActivePoLineIds(tid, po.getId(), InvoiceStatus.CANCELLED));
        Set<UUID> receipted = new HashSet<>(grLineRepo.findReceiptedPoLineIds(tid, po.getId(), "REVERSED"));
        return PoAmendmentRules.evaluate(current, changes, invoiced, receipted);
    }

    private List<Change> toChanges(List<ScPoAmendmentLine> lines) {
        return lines.stream().map(l -> new Change(l.getAction(), l.getPoLineId(), l.getCatalogueItemId(),
                l.getItemName(), l.getSupplierSku(), l.getQtyOrdered(), l.getUnitCost(), l.getVatRate())).toList();
    }

    private ScPurchaseOrder requireDraft(TenantId tenantId, UUID poId) {
        ScPurchaseOrder po = poRepo.findByTenantIdAndId(tenantId.getValue(), poId)
                .orElseThrow(() -> new HandyFlowException("Purchase order not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (po.getStatus() != PoStatus.DRAFT)
            throw new HandyFlowException("Lines can only be edited directly on a DRAFT PO — raise an amendment instead",
                    HttpStatus.CONFLICT, "INVALID_STATUS");
        return po;
    }

    private ScPoAmendment requirePending(TenantId tenantId, UUID amendmentId) {
        ScPoAmendment a = amendmentRepo.findByTenantAndId(tenantId.getValue(), amendmentId)
                .orElseThrow(() -> new HandyFlowException("Amendment not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!a.isPending())
            throw new HandyFlowException("Amendment is already " + a.getStatus(), HttpStatus.CONFLICT, "AMENDMENT_CLOSED");
        return a;
    }

    private void recalcTotals(ScPurchaseOrder po) {
        recalcTotals(po, poLineRepo.findByPurchaseOrderId(po.getId()));
    }

    private void recalcTotals(ScPurchaseOrder po, List<ScPoLine> lines) {
        BigDecimal sub = lines.stream().map(ScPoLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal vat = lines.stream().map(ScPoLine::getVatAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        po.recalculateTotals(sub, vat, sub.add(vat));
    }

    private PoAmendmentResponse toResponse(ScPoAmendment a) {
        List<PoAmendmentResponse.Line> lines = amendmentLineRepo.findByAmendment(a.getTenantId(), a.getId()).stream()
                .map(l -> new PoAmendmentResponse.Line(l.getId(), l.getAction(), l.getPoLineId(), l.getItemName(),
                        l.getSupplierSku(), l.getQtyOrdered(), l.getUnitCost(), l.getVatRate())).toList();
        return new PoAmendmentResponse(a.getId(), a.getAmendmentNumber(), a.getBaseRevision(), a.getStatus(),
                a.isMaterial(), a.getReason(), a.getChangeSummary(), a.getPreviousTotal(), a.getProposedTotal(),
                a.getNewRequiredByDate(), a.getNewDeliverToLocation(), a.getNewNotes(), a.getNewTerms(),
                a.getRequestedByName(), a.getCreatedAt(), a.getDecidedByName(), a.getDecidedAt(),
                a.getDecisionNote(), lines);
    }

    /** Re-sends the PO to a supplier who already had an earlier revision. Sent after commit; never blocks the amendment. */
    private void notifySupplier(TenantId tenantId, ScPurchaseOrder po, ScPoAmendment a) {
        try {
            String email = supplierRepo.findByTenantIdAndId(tenantId.getValue(), po.getSupplierId())
                    .map(ScSupplier::getContactEmail).orElse(null);
            if (email == null || email.isBlank()) {
                log.info("[SCM] No supplier contact email — amended PO {} not sent", po.getOrderNumber());
                return;
            }
            byte[] pdf = null;
            try { pdf = scmService.generatePoPdf(tenantId, po.getId()); }
            catch (Exception e) { log.warn("[SCM] Could not render amended PO PDF for {}: {}", po.getOrderNumber(), e.getMessage()); }

            final byte[] pdfBytes = pdf;
            final String subject = "AMENDED Purchase Order " + po.getOrderNumber() + " (Revision " + po.getRevisionNumber() + ")";
            final String html = "<p>Purchase order <strong>" + HtmlUtils.htmlEscape(po.getOrderNumber())
                    + "</strong> has been amended (revision " + po.getRevisionNumber() + ") and supersedes any earlier version.</p>"
                    + "<p><strong>Reason:</strong> " + HtmlUtils.htmlEscape(a.getReason()) + "</p>"
                    + "<p><strong>New total:</strong> R " + po.getTotalAmount().toPlainString() + "</p>"
                    + "<p>Please confirm receipt of the amended order"
                    + (pdfBytes != null ? "; the updated PDF is attached." : ".") + "</p>";
            Runnable send = () -> {
                if (pdfBytes != null) emailService.sendWithAttachment(email, subject, html, po.getOrderNumber() + "-rev" + po.getRevisionNumber() + ".pdf", pdfBytes);
                else emailService.send(email, subject, html);
            };
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { send.run(); }
                });
            } else {
                send.run();
            }
        } catch (Exception e) {
            log.error("[SCM] Failed to notify supplier of amended PO {}: {}", po.getOrderNumber(), e.getMessage());
        }
    }

    private static org.springframework.transaction.support.DefaultTransactionDefinition requiresNew() {
        var def = new org.springframework.transaction.support.DefaultTransactionDefinition();
        def.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return def;
    }

    private boolean isOpen(ApprovalRequestResponse r) {
        return "SUBMITTED".equals(r.status()) || "IN_PROGRESS".equals(r.status());
    }

    private Optional<ApprovalStepResponse> firstPending(ApprovalRequestResponse r) {
        return r.steps().stream().filter(s -> "PENDING".equals(s.status()))
                .min(Comparator.comparingInt(ApprovalStepResponse::stepOrder));
    }

    private List<String> authorities() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return List.of();
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private static HandyFlowException bad(String msg) {
        return new HandyFlowException(msg, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }
}
