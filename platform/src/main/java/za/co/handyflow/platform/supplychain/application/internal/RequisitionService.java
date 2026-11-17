package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.dto.ApprovalRequestResponse;
import za.co.handyflow.platform.approvals.dto.ApprovalStepResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.shared.TenantSequenceService;
import za.co.handyflow.platform.shared.VatRateProvider;
import za.co.handyflow.platform.supplychain.application.internal.RequisitionRules.Availability;
import za.co.handyflow.platform.supplychain.application.internal.RequisitionRules.LineInput;
import za.co.handyflow.platform.supplychain.domain.enums.PoStatus;
import za.co.handyflow.platform.supplychain.domain.enums.RequisitionStatus;
import za.co.handyflow.platform.supplychain.domain.enums.RequisitionType;
import za.co.handyflow.platform.supplychain.domain.model.*;
import za.co.handyflow.platform.supplychain.domain.repository.*;
import za.co.handyflow.platform.supplychain.dto.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Purchase requisitions: a controlled "I need to buy this" step before a PO exists.
 * <p>
 * Flow: DRAFT -> submit (stock / open-PO / duplicate-demand check, snapshot for the approver)
 * -> shared approval engine -> APPROVED -> an authorised buyer converts all or part of it into
 * DRAFT purchase order(s), one supplier at a time. The PO then runs through its own approval.
 * Converted quantity is derived from non-cancelled POs, so cancelling a PO re-opens the quantity.
 * <p>
 * Visibility: users who can read Supply Chain (SCM_READ / SCM_ORDER / SCM_ADMIN /
 * SCM_REQUISITION_APPROVE) see everything; a plain requester (SCM_REQUISITION_CREATE only) sees
 * only their own. Only the requester (or SCM_ADMIN) can edit / submit / withdraw / cancel.
 * Segregation of duties (app.scm.sod.enforce=true): you cannot approve your own requisition.
 * Budget checks are NOT here yet — department / cost centre / project are captured for them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequisitionService {

    private static final String APPROVALS_MODULE = "supplychain";
    private static final String APPROVALS_ENTITY_TYPE = "REQUISITION";
    private static final Set<String> SEE_ALL = Set.of("SCM_READ", "SCM_ORDER", "SCM_ADMIN", "SCM_REQUISITION_APPROVE");

    private final ScRequisitionRepository reqRepo;
    private final ScRequisitionLineRepository lineRepo;
    private final ScRequisitionConversionRepository convRepo;
    private final ScPurchaseOrderRepository poRepo;
    private final ScStockLocationRepository locationRepo;
    private final RequisitionAvailabilityService availability;
    private final ScmService scmService;                       // PO creation, so supplier/sequence rules apply
    private final ApprovalFacade approvalFacade;
    private final TenantSequenceService sequenceService;
    private final VatRateProvider vatRateProvider;
    // NEW (chunk 7): budget check at submit and approve.
    private final BudgetService budgetService;
    private final org.springframework.transaction.PlatformTransactionManager txManager;

    @Value("${app.scm.sod.enforce:false}")
    private boolean enforceSegregationOfDuties;

    // ── Create / edit ─────────────────────────────────────────────────────────

    @Transactional
    public RequisitionResponse create(TenantId tenantId, UUID userId, String userName, CreateRequisitionRequest req) {
        UUID tid = tenantId.getValue();
        RequisitionType type = RequisitionRules.parseType(req.type());
        List<ScRequisitionLine> lines = buildLines(tid, null, req);
        validate(type, req, lines);
        checkLocation(tid, req.deliverToLocation());

        String number = "PR-" + String.format("%05d", sequenceService.nextValue(tenantId, "PR"));
        ScRequisition r = ScRequisition.create(tid, number, type, req.title().trim(), req.businessReason().trim(),
                trim(req.department()), trim(req.costCentre()), trim(req.projectRef()), req.deliverToLocation(),
                req.requiredByDate(), req.currency(), userId, userName);
        reqRepo.save(r);
        saveLines(r, req);
        return detail(r);
    }

    @Transactional
    public RequisitionResponse update(TenantId tenantId, UUID id, UUID userId, CreateRequisitionRequest req) {
        UUID tid = tenantId.getValue();
        ScRequisition r = requireOwned(tenantId, id, userId);
        if (!r.isEditable())
            throw conflict("Only a DRAFT requisition can be edited — this one is " + r.getStatus()
                    + (r.getStatus() == RequisitionStatus.PENDING_APPROVAL ? " (withdraw it first)" : ""));
        RequisitionType type = RequisitionRules.parseType(req.type());
        validate(type, req, buildLines(tid, r.getId(), req));
        checkLocation(tid, req.deliverToLocation());

        r.edit(type, req.title().trim(), req.businessReason().trim(), trim(req.department()), trim(req.costCentre()),
                trim(req.projectRef()), req.deliverToLocation(), req.requiredByDate(), req.currency());
        lineRepo.deleteByRequisition(tid, r.getId());
        saveLines(r, req);
        return detail(reqRepo.save(r));
    }

    // ── Workflow ──────────────────────────────────────────────────────────────

    @Transactional
    public RequisitionResponse submit(TenantId tenantId, UUID id, UUID userId, String userName) {
        UUID tid = tenantId.getValue();
        requireOwned(tenantId, id, userId);
        // Lock so a double-click / retry can't submit twice and open two approval requests.
        ScRequisition r = reqRepo.lockByTenantAndId(tid, id).orElseThrow(this::notFound);
        List<ScRequisitionLine> lines = lineRepo.findByRequisition(tid, id);
        if (lines.isEmpty()) throw bad("Add at least one line before submitting");
        List<String> errors = RequisitionRules.validate(r.getType(), r.getTitle(), r.getBusinessReason(), r.getProjectRef(),
                lines.stream().map(this::toInput).toList());
        if (!errors.isEmpty()) throw bad(String.join("; ", errors));
        budgetService.checkRequisition(tenantId, r, lines, true);

        // Snapshot what the approver will see: stock, open orders, other open requisitions.
        for (ScRequisitionLine l : lines) {
            Availability a = availability.forItem(tid, l.getCatalogueItemId(), r.getId());
            l.snapshot(a.onHand(), a.onOrder(), a.alreadyRequested());
            lineRepo.save(l);
        }
        r.submit();
        reqRepo.save(r);

        ApprovalRequestResponse submitted = approvalFacade.submit(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE,
                r.getId(), userId, Map.of());
        if ("APPROVED".equals(submitted.status())) {
            r.approve(null, "Approval policy", "Auto-approved by approval policy");   // engine decided no human step is needed
            reqRepo.save(r);
        }
        log.info("[SCM] Requisition {} submitted by {} (status {})", r.getRequisitionNumber(), userName, r.getStatus());
        return detail(r);
    }

    @Transactional
    public RequisitionResponse approve(TenantId tenantId, UUID id, UUID approverId, String approverName, String note) {
        ScRequisition r = reqRepo.lockByTenantAndId(tenantId.getValue(), id).orElseThrow(this::notFound);
        if (r.getStatus() != RequisitionStatus.PENDING_APPROVAL)
            throw conflict("Only a PENDING_APPROVAL requisition can be approved — this one is " + r.getStatus());
        budgetService.checkRequisition(tenantId, r, lineRepo.findByRequisition(tenantId.getValue(), id), false);
        if (enforceSegregationOfDuties && r.getRequestedBy().equals(approverId))
            throw new HandyFlowException("Segregation of duties: you cannot approve a requisition you raised yourself",
                    HttpStatus.FORBIDDEN, "SEGREGATION_OF_DUTIES");

        var existing = approvalFacade.getLatestRequestForEntity(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, id);
        ApprovalRequestResponse result;
        if (existing.isPresent() && isOpen(existing.get())) {
            ApprovalStepResponse step = firstPending(existing.get())
                    .orElseThrow(() -> new HandyFlowException("This requisition's approval request has no pending step — needs manual review",
                            HttpStatus.CONFLICT, "NO_PENDING_STEP"));
            result = approvalFacade.actOnStep(tenantId, step.id(), approverId, authorities(), "APPROVE", note, null);
        } else {
            result = approvalFacade.submit(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, id, approverId, Map.of());
            if (isOpen(result)) {
                ApprovalStepResponse step = firstPending(result)
                        .orElseThrow(() -> new IllegalStateException("A freshly-submitted request has no pending step"));
                result = approvalFacade.actOnStep(tenantId, step.id(), approverId, authorities(), "APPROVE", note, null);
            }
        }
        if ("APPROVED".equals(result.status())) {
            r.approve(approverId, approverName, note);
            reqRepo.save(r);
            log.info("[SCM] Requisition {} approved by {}", r.getRequisitionNumber(), approverName);
        } else {
            log.info("[SCM] Requisition {} step approved by {} — approval status {}", r.getRequisitionNumber(), approverName, result.status());
        }
        return detail(r);
    }

    @Transactional
    public RequisitionResponse reject(TenantId tenantId, UUID id, UUID userId, String note) {
        if (note == null || note.isBlank())
            throw new HandyFlowException("A rejection reason is required", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        ScRequisition r = reqRepo.lockByTenantAndId(tenantId.getValue(), id).orElseThrow(this::notFound);
        if (r.getStatus() != RequisitionStatus.PENDING_APPROVAL)
            throw conflict("Only a PENDING_APPROVAL requisition can be rejected — this one is " + r.getStatus());
        closeOpenApproval(tenantId, id, userId, "REJECT", note, true);
        r.reject(note.trim());
        return detail(reqRepo.save(r));
    }

    @Transactional
    public RequisitionResponse withdraw(TenantId tenantId, UUID id, UUID userId) {
        ScRequisition r = requireOwned(tenantId, id, userId);
        closeOpenApproval(tenantId, id, userId, "REJECT", "Withdrawn by requester", false);
        r.withdraw();
        return detail(reqRepo.save(r));
    }

    @Transactional
    public RequisitionResponse reopen(TenantId tenantId, UUID id, UUID userId) {
        ScRequisition r = requireOwned(tenantId, id, userId);
        r.reopen();
        return detail(reqRepo.save(r));
    }

    @Transactional
    public RequisitionResponse cancel(TenantId tenantId, UUID id, UUID userId, String reason) {
        if (reason == null || reason.isBlank())
            throw new HandyFlowException("A cancellation reason is required", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        UUID tid = tenantId.getValue();
        requireOwned(tenantId, id, userId);
        ScRequisition r = reqRepo.lockByTenantAndId(tid, id).orElseThrow(this::notFound);
        if (r.getStatus() == RequisitionStatus.APPROVED && !orderedByLine(tid, id).isEmpty())
            throw conflict("Purchase orders have already been raised from this requisition — cancel them first");
        closeOpenApproval(tenantId, id, userId, "REJECT", "Cancelled by requester", false);
        r.cancel(reason.trim());
        return detail(reqRepo.save(r));
    }

    // ── Convert to purchase order ─────────────────────────────────────────────

    @Transactional
    public RequisitionResponse convertToPo(TenantId tenantId, UUID id, UUID userId, String userName,
                                           ConvertRequisitionRequest req) {
        UUID tid = tenantId.getValue();
        ScRequisition r = reqRepo.lockByTenantAndId(tid, id).orElseThrow(this::notFound);
        if (r.getStatus() != RequisitionStatus.APPROVED)
            throw conflict("Only an APPROVED requisition can be converted — this one is " + r.getStatus());
        if (req.supplierId() == null) throw bad("supplierId is required");
        if (req.lines() == null || req.lines().isEmpty()) throw bad("Choose at least one line to order");

        Map<UUID, ScRequisitionLine> lines = new LinkedHashMap<>();
        lineRepo.findByRequisition(tid, id).forEach(l -> lines.put(l.getId(), l));
        Map<UUID, BigDecimal> ordered = orderedByLine(tid, id);

        record Pick(ScRequisitionLine line, BigDecimal qty, BigDecimal cost, BigDecimal vat) {}
        List<Pick> picks = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (ConvertRequisitionRequest.Line c : req.lines()) {
            ScRequisitionLine l = c.requisitionLineId() == null ? null : lines.get(c.requisitionLineId());
            if (l == null) throw bad("Line " + c.requisitionLineId() + " is not on this requisition");
            if (!seen.add(l.getId())) throw bad(l.getItemName() + " appears twice in the request");
            BigDecimal remaining = l.getQtyRequested().subtract(ordered.getOrDefault(l.getId(), BigDecimal.ZERO));
            BigDecimal qty = c.quantity() != null ? c.quantity() : remaining;
            if (qty.signum() <= 0) throw bad(l.getItemName() + ": nothing left to order (" + plain(remaining) + " remaining)");
            if (qty.compareTo(remaining) > 0)
                throw conflict(l.getItemName() + ": " + plain(qty) + " exceeds the " + plain(remaining) + " still to be ordered");
            BigDecimal cost = c.unitCost() != null ? c.unitCost() : l.getEstimatedUnitCost();
            if (cost.signum() < 0) throw bad(l.getItemName() + ": unit cost cannot be negative");
            picks.add(new Pick(l, qty, cost, c.vatRate() != null ? c.vatRate() : l.getVatRate()));
        }

        // Reuses ScmService so the supplier-orderable rule, PO numbering and VAT handling stay in one place.
        ScPurchaseOrder po = scmService.createPurchaseOrder(tenantId, userId, userName, new CreatePurchaseOrderRequest(
                req.supplierId(), req.deliverToLocation() != null ? req.deliverToLocation() : r.getDeliverToLocation(),
                LocalDate.now(), req.requiredByDate() != null ? req.requiredByDate() : r.getRequiredByDate(),
                r.getCurrency(), r.getProjectRef(), req.notes(), "Raised from requisition " + r.getRequisitionNumber()));
        UUID poId = po.getId();
        for (Pick p : picks) {
            scmService.addPurchaseOrderLine(tenantId, poId, new AddPoLineRequest(
                    p.line().getItemName(), null, p.qty(), p.cost(), p.vat(), p.line().getCatalogueItemId()));
            convRepo.save(ScRequisitionConversion.create(tid, id, p.line().getId(), poId, p.qty(), p.cost(), userId, userName));
        }
        ScPurchaseOrder linked = poRepo.findByTenantIdAndId(tid, poId).orElseThrow();
        linked.linkRequisition(id);
        poRepo.save(linked);

        log.info("[SCM] Requisition {} -> PO {} ({} line(s)) by {}", r.getRequisitionNumber(), linked.getOrderNumber(), picks.size(), userName);
        return detail(r);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RequisitionResponse get(TenantId tenantId, UUID id, UUID userId) {
        ScRequisition r = reqRepo.findByTenantAndId(tenantId.getValue(), id).orElseThrow(this::notFound);
        if (!canSeeAll() && !r.getRequestedBy().equals(userId))
            throw new HandyFlowException("You can only view your own requisitions", HttpStatus.FORBIDDEN, "FORBIDDEN");
        return detail(r);
    }

    @Transactional(readOnly = true)
    public Page<RequisitionResponse> list(TenantId tenantId, UUID userId, String status, boolean mine, Pageable pageable) {
        UUID tid = tenantId.getValue();
        boolean onlyMine = mine || !canSeeAll();
        RequisitionStatus st = null;
        if (status != null && !status.isBlank()) {
            try { st = RequisitionStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw bad("Unknown status '" + status + "'"); }
        }
        Page<ScRequisition> page = onlyMine
                ? (st == null ? reqRepo.findByRequester(tid, userId, pageable) : reqRepo.findByRequesterAndStatus(tid, userId, st, pageable))
                : (st == null ? reqRepo.findAllForTenant(tid, pageable) : reqRepo.findByStatus(tid, st, pageable));
        return page.map(r -> toResponse(r, List.of(), List.of(), List.of()));
    }

    @Transactional(readOnly = true)
    public List<RequisitionSuggestionResponse> suggestions(TenantId tenantId) {
        return availability.suggestions(tenantId.getValue());
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private RequisitionResponse detail(ScRequisition r) {
        UUID tid = r.getTenantId();
        List<ScRequisitionLine> lines = lineRepo.findByRequisition(tid, r.getId());
        Map<UUID, BigDecimal> ordered = orderedByLine(tid, r.getId());
        boolean live = r.getStatus() == RequisitionStatus.DRAFT || r.getStatus() == RequisitionStatus.PENDING_APPROVAL;

        List<RequisitionResponse.Line> out = new ArrayList<>();
        List<String> advisories = new ArrayList<>();
        for (ScRequisitionLine l : lines) {
            Availability a = live ? availability.forItem(tid, l.getCatalogueItemId(), r.getId())
                    : new Availability(nz(l.getStockSnapshot()), nz(l.getOnOrderSnapshot()), nz(l.getAlreadyRequestedSnapshot()));
            BigDecimal ord = ordered.getOrDefault(l.getId(), BigDecimal.ZERO);
            out.add(new RequisitionResponse.Line(l.getId(), l.getLineNumber(), l.getCatalogueItemId(), l.getItemName(),
                    l.getDescription(), l.getQtyRequested(), ord, l.getQtyRequested().subtract(ord).max(BigDecimal.ZERO),
                    l.getEstimatedUnitCost(), l.getVatRate(), l.getSuggestedSupplierId(),
                    a.onHand(), a.onOrder(), a.alreadyRequested()));
            if (l.getCatalogueItemId() != null) advisories.addAll(RequisitionRules.advisories(l.getItemName(), l.getQtyRequested(), a));
        }

        List<RequisitionResponse.Conversion> conv = new ArrayList<>();
        for (ScRequisitionConversion c : convRepo.findByRequisition(tid, r.getId())) {
            ScPurchaseOrder po = poRepo.findByTenantIdAndId(tid, c.getPurchaseOrderId()).orElse(null);
            conv.add(new RequisitionResponse.Conversion(c.getPurchaseOrderId(), po != null ? po.getOrderNumber() : null,
                    po != null ? po.getStatus().name() : null, c.getRequisitionLineId(), c.getQty(), c.getUnitCost(), c.getCreatedAt()));
        }
        return toResponse(r, out, conv, advisories);
    }

    private RequisitionResponse toResponse(ScRequisition r, List<RequisitionResponse.Line> lines,
                                           List<RequisitionResponse.Conversion> conv, List<String> advisories) {
        String fulfilment = null;
        if (r.getStatus() == RequisitionStatus.APPROVED && !lines.isEmpty()) {
            fulfilment = RequisitionRules.fulfilment(lines.stream()
                    .map(l -> new BigDecimal[]{l.qtyRequested(), l.qtyOrdered()}).toList()).name();
        }
        return new RequisitionResponse(r.getId(), r.getRequisitionNumber(), r.getType().name(), r.getStatus().name(),
                fulfilment, r.getTitle(), r.getBusinessReason(), r.getDepartment(), r.getCostCentre(), r.getProjectRef(),
                r.getDeliverToLocation(), r.getRequiredByDate(), r.getEstimatedTotal(), r.getCurrency(),
                r.getRequestedByName(), r.getCreatedAt(), r.getSubmittedAt(), r.getApprovedByName(), r.getApprovedAt(),
                r.getDecisionNote(), lines, conv, advisories);
    }

    private List<ScRequisitionLine> buildLines(UUID tid, UUID requisitionId, CreateRequisitionRequest req) {
        List<ScRequisitionLine> out = new ArrayList<>();
        if (req.lines() == null) return out;
        int n = 1;
        for (CreateRequisitionRequest.Line l : req.lines()) {
            out.add(ScRequisitionLine.create(tid, requisitionId, n++, l.catalogueItemId(),
                    l.itemName() == null ? null : l.itemName().trim(), trim(l.description()), l.quantity(),
                    l.estimatedUnitCost(), l.vatRate() != null ? l.vatRate() : vatRateProvider.ratePercent(),
                    l.suggestedSupplierId()));
        }
        return out;
    }

    private void saveLines(ScRequisition r, CreateRequisitionRequest req) {
        List<ScRequisitionLine> lines = buildLines(r.getTenantId(), r.getId(), req);
        lineRepo.saveAll(lines);
        r.setEstimatedTotal(RequisitionRules.estimatedTotal(lines.stream().map(this::toInput).toList()));
        reqRepo.save(r);
    }

    private void validate(RequisitionType type, CreateRequisitionRequest req, List<ScRequisitionLine> lines) {
        List<String> errors = RequisitionRules.validate(type, req.title(), req.businessReason(), req.projectRef(),
                lines.stream().map(this::toInput).toList());
        if (!errors.isEmpty()) throw new HandyFlowException(String.join("; ", errors), HttpStatus.BAD_REQUEST, "INVALID_REQUISITION");
    }

    private LineInput toInput(ScRequisitionLine l) {
        return new LineInput(l.getItemName(), l.getQtyRequested(), l.getEstimatedUnitCost(), l.getVatRate());
    }

    private void checkLocation(UUID tid, UUID locationId) {
        if (locationId != null)
            locationRepo.findActiveByTenantIdAndId(tid, locationId)
                    .orElseThrow(() -> new HandyFlowException("Delivery location not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private Map<UUID, BigDecimal> orderedByLine(UUID tid, UUID requisitionId) {
        Map<UUID, BigDecimal> m = new HashMap<>();
        for (Object[] row : convRepo.sumActiveByLine(tid, requisitionId, PoStatus.CANCELLED)) m.put((UUID) row[0], (BigDecimal) row[1]);
        return m;
    }

    /** The requester, or SCM_ADMIN, may act on the requisition. */
    private ScRequisition requireOwned(TenantId tenantId, UUID id, UUID userId) {
        ScRequisition r = reqRepo.findByTenantAndId(tenantId.getValue(), id).orElseThrow(this::notFound);
        if (!r.getRequestedBy().equals(userId) && !authorities().contains("SCM_ADMIN"))
            throw new HandyFlowException("Only the requester can do this to a requisition", HttpStatus.FORBIDDEN, "NOT_REQUESTER");
        return r;
    }

    /**
     * Closes the open approval step so it leaves approvers' inboxes; non-approvers may be refused, which mustn't block.
     * The best-effort variant runs in its own transaction: swallowing an exception thrown inside the caller's
     * transaction would mark it rollback-only and the withdrawal/cancel would fail at commit.
     */
    private void closeOpenApproval(TenantId tenantId, UUID id, UUID userId, String action, String note, boolean mustSucceed) {
        Runnable close = () -> approvalFacade.getLatestRequestForEntity(tenantId, APPROVALS_MODULE, APPROVALS_ENTITY_TYPE, id)
                .filter(this::isOpen).flatMap(this::firstPending)
                .ifPresent(step -> approvalFacade.actOnStep(tenantId, step.id(), userId, authorities(), action, note, null));
        if (mustSucceed) { close.run(); return; }
        try {
            var def = new org.springframework.transaction.support.DefaultTransactionDefinition();
            def.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            new org.springframework.transaction.support.TransactionTemplate(txManager, def).executeWithoutResult(st -> close.run());
        } catch (RuntimeException e) {
            log.warn("[SCM] Could not close approval request for requisition {}: {}", id, e.getMessage());
        }
    }

    private boolean canSeeAll() {
        List<String> a = authorities();
        return SEE_ALL.stream().anyMatch(a::contains);
    }

    private boolean isOpen(ApprovalRequestResponse r) { return "SUBMITTED".equals(r.status()) || "IN_PROGRESS".equals(r.status()); }

    private Optional<ApprovalStepResponse> firstPending(ApprovalRequestResponse r) {
        return r.steps().stream().filter(s -> "PENDING".equals(s.status())).min(Comparator.comparingInt(ApprovalStepResponse::stepOrder));
    }

    private List<String> authorities() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? List.of() : auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private HandyFlowException notFound() { return new HandyFlowException("Requisition not found", HttpStatus.NOT_FOUND, "NOT_FOUND"); }
    private HandyFlowException conflict(String m) { return new HandyFlowException(m, HttpStatus.CONFLICT, "INVALID_REQUISITION_STATE"); }
    private HandyFlowException bad(String m) { return new HandyFlowException(m, HttpStatus.BAD_REQUEST, "INVALID_REQUISITION"); }
    private static String trim(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static String plain(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }
    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
