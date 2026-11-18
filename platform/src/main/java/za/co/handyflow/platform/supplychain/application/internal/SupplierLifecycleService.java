package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.controls.application.ControlExceptionFacade;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.domain.enums.PoStatus;
import za.co.handyflow.platform.supplychain.domain.enums.SupplierStatus;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplier;
import za.co.handyflow.platform.supplychain.domain.repository.ScPurchaseOrderRepository;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierRepository;
import za.co.handyflow.platform.supplychain.dto.SupplierStatusResponse;

import java.util.List;
import java.util.UUID;

/**
 * The only way a supplier's status changes. Every move needs a legal transition, a reason
 * (except approve), an actor, and writes an audit event; suspend/block also surface on the
 * shared control-exception board with the number of open POs affected.
 * <p>
 * Segregation of duties (app.scm.sod.enforce=true): the person who registered a supplier
 * cannot approve it, and the person who blocked a supplier cannot be the one to lift the block.
 * app.scm.supplier.require-approval=true makes new suppliers start PENDING_APPROVAL.
 * Blocking does not cancel existing POs — it stops NEW ones and stops "send to supplier"
 * (see ScmService); open POs are counted so the buyer can decide what to do with them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierLifecycleService {

    private static final List<PoStatus> OPEN_PO_STATUSES =
            List.of(PoStatus.APPROVED, PoStatus.SENT, PoStatus.ACKNOWLEDGED, PoStatus.PARTIALLY_RECEIVED);

    private final ScmSeparationOfDuties sod;
    private final ScSupplierRepository supplierRepo;
    private final ScPurchaseOrderRepository poRepo;
    private final SupplierAuditService audit;
    private final ControlExceptionFacade controlExceptionFacade;
    // NEW (chunk 5): required-document policy — no approval/reinstatement while non-compliant.
    private final SupplierComplianceService compliance;


    @Value("${app.scm.supplier.require-approval:false}")
    private boolean requireApproval;

    @Transactional
    public SupplierStatusResponse approve(TenantId tenantId, UUID id, UUID actor, String actorName, String note) {
        ScSupplier s = load(tenantId, id);
        compliance.requireCompliant(tenantId, id);
        sod.require(tenantId, "SCM_SUPPLIER_APPROVE", s.getCreatedBy(), actor,
                "You cannot approve a supplier you registered yourself", "ScSupplier", id, "Supplier approval");
        return move(tenantId, s, SupplierStatus.ACTIVE, "APPROVED", note, false, actor, actorName);
    }

    @Transactional
    public SupplierStatusResponse reject(TenantId tenantId, UUID id, UUID actor, String actorName, String reason) {
        ScSupplier s = load(tenantId, id);
        if (s.getStatus() != SupplierStatus.PENDING_APPROVAL)
            throw conflict("Only suppliers awaiting approval can be rejected — this one is " + s.getStatus());
        return move(tenantId, s, SupplierStatus.INACTIVE, "REJECTED", reason, true, actor, actorName);
    }

    @Transactional
    public SupplierStatusResponse suspend(TenantId tenantId, UUID id, UUID actor, String actorName, String reason) {
        return move(tenantId, load(tenantId, id), SupplierStatus.SUSPENDED, "SUSPENDED", reason, true, actor, actorName);
    }

    @Transactional
    public SupplierStatusResponse block(TenantId tenantId, UUID id, UUID actor, String actorName, String reason) {
        return move(tenantId, load(tenantId, id), SupplierStatus.BLACKLISTED, "BLOCKED", reason, true, actor, actorName);
    }

    /** Lifts a suspension or a block. */
    @Transactional
    public SupplierStatusResponse reinstate(TenantId tenantId, UUID id, UUID actor, String actorName, String reason) {
        ScSupplier s = load(tenantId, id);
        if (s.getStatus() != SupplierStatus.SUSPENDED && s.getStatus() != SupplierStatus.BLACKLISTED)
            throw conflict("Only suspended or blocked suppliers can be reinstated — this one is " + s.getStatus());
        if (s.getStatus() == SupplierStatus.BLACKLISTED)
            sod.require(tenantId, "SCM_SUPPLIER_BLOCK", s.getStatusChangedBy(), actor,
                    "The person who blocked a supplier cannot lift the block", "ScSupplier", id, "Supplier block lifted");
        compliance.requireCompliant(tenantId, id);
        SupplierStatusResponse r = move(tenantId, s, SupplierStatus.ACTIVE, "REINSTATED", reason, true, actor, actorName);
        controlExceptionFacade.resolveForEntity(tenantId, "ScSupplier", id, actor, actorName, "Reinstated: " + reason);
        return r;
    }

    @Transactional
    public SupplierStatusResponse deactivate(TenantId tenantId, UUID id, UUID actor, String actorName, String reason) {
        return move(tenantId, load(tenantId, id), SupplierStatus.INACTIVE, "DEACTIVATED", reason, true, actor, actorName);
    }

    /** Brings an INACTIVE supplier back: straight to ACTIVE, or via approval if the tenant requires it. */
    @Transactional
    public SupplierStatusResponse reopen(TenantId tenantId, UUID id, UUID actor, String actorName, String reason) {
        SupplierStatus target = requireApproval ? SupplierStatus.PENDING_APPROVAL : SupplierStatus.ACTIVE;
        if (target == SupplierStatus.ACTIVE) compliance.requireCompliant(tenantId, id);
        return move(tenantId, load(tenantId, id), target, "REOPENED", reason, true, actor, actorName);
    }

    @Transactional(readOnly = true)
    public SupplierStatusResponse status(TenantId tenantId, UUID id) {
        return toResponse(tenantId, load(tenantId, id));
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private SupplierStatusResponse move(TenantId tenantId, ScSupplier s, SupplierStatus target, String event,
                                        String reason, boolean reasonRequired, UUID actor, String actorName) {
        if (reasonRequired && (reason == null || reason.isBlank()))
            throw new HandyFlowException("A reason is required", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        SupplierStatus from = s.getStatus();
        if (!from.canTransitionTo(target))
            throw conflict("A " + from + " supplier cannot move to " + target + " (allowed: " + from.allowedNext() + ")");

        s.transitionTo(target, reason != null && !reason.isBlank() ? reason.trim() : null, actor);
        supplierRepo.save(s);
        audit.record(tenantId.getValue(), s.getId(), event, from.name(), target.name(), reason, actor, actorName);

        SupplierStatusResponse r = toResponse(tenantId, s);
        if ((target == SupplierStatus.SUSPENDED || target == SupplierStatus.BLACKLISTED)) {
            controlExceptionFacade.raise(tenantId, "supplychain", "SUPPLIER_" + (target == SupplierStatus.BLACKLISTED ? "BLOCKED" : "SUSPENDED"),
                    "ScSupplier", s.getId(), "WARNING",
                    "Supplier " + s.getName() + " " + event.toLowerCase() + ": " + reason
                            + " (" + r.openPurchaseOrders() + " open purchase order(s))");
        }
        log.info("[SCM] Supplier {} {} -> {} by {} (reason: {})", s.getName(), from, target, actorName, reason);
        return r;
    }

    private SupplierStatusResponse toResponse(TenantId tenantId, ScSupplier s) {
        long open = poRepo.countBySupplierAndStatusIn(tenantId.getValue(), s.getId(), OPEN_PO_STATUSES);
        return new SupplierStatusResponse(s.getId(), s.getName(), s.getStatus(), s.getStatusReason(),
                s.getStatusChangedAt(), open);
    }

    private ScSupplier load(TenantId tenantId, UUID id) {
        return supplierRepo.findByTenantIdAndId(tenantId.getValue(), id)
                .orElseThrow(() -> new HandyFlowException("Supplier not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private HandyFlowException conflict(String m) { return new HandyFlowException(m, HttpStatus.CONFLICT, "INVALID_SUPPLIER_STATE"); }
    private HandyFlowException forbidden(String m) { return new HandyFlowException(m, HttpStatus.FORBIDDEN, "SEGREGATION_OF_DUTIES"); }
}
