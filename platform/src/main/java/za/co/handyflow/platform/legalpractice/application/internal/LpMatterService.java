package za.co.handyflow.platform.legalpractice.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatter;
import za.co.handyflow.platform.legalpractice.domain.repository.LpMatterRepository;
import za.co.handyflow.platform.legalpractice.domain.repository.LpTrustTransactionRepository;
import za.co.handyflow.platform.legalpractice.dto.CloseMatterRequest;
import za.co.handyflow.platform.legalpractice.dto.CreateLpMatterRequest;
import za.co.handyflow.platform.legalpractice.dto.LpMatterResponse;
import za.co.handyflow.platform.legalpractice.dto.UpdateLpMatterRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * CRUD plus the full OPEN/ON_HOLD/CLOSED/ARCHIVED state machine — every
 * transition delegates straight to {@link LpMatter}'s own guarded methods,
 * letting {@code IllegalStateException} propagate to the controller layer
 * unchanged (mapped to 409 by {@code GlobalExceptionHandler}). The one
 * rule enforced here rather than on the entity — {@code fixedFeeAmount}
 * required when {@code billingType == FIXED_FEE} — matches the entity's
 * own Javadoc comment ("required when billingType = FIXED_FEE, enforced
 * in the service layer").
 * <p>
 * A matter can only be opened from a conflict check that cleared the client (see {@link LpConflictCheckService}).
 * A member of staff barred by an ethical wall cannot see or change the matter, and does not find it in lists (see {@link LpWallGuard}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LpMatterService {

    private final LpMatterRepository matterRepo;
    private final LpTrustTransactionRepository trustRepo;
    private final LpConflictCheckService conflictService;
    private final LpWallGuard walls;
    private final LpKycGate kyc;

    @Transactional(readOnly = true)
    public Page<LpMatterResponse> listForClient(TenantId tenantId, UUID clientId, Pageable pageable) {
        walls.requireClientAccess(tenantId, clientId);
        var barred = walls.barred(tenantId);
        return matterRepo.findAllActiveForClientExcluding(tenantId, clientId, barred.matterIds(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<LpMatterResponse> listForFirm(TenantId tenantId, Pageable pageable) {
        var barred = walls.barred(tenantId);
        return matterRepo.findAllActiveForFirmExcluding(tenantId, barred.matterIds(), barred.clientIds(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public LpMatterResponse getMatter(TenantId tenantId, UUID matterId) {
        return toResponse(findOwn(tenantId, matterId));
    }

    @Transactional
    public LpMatterResponse createMatter(TenantId tenantId, CreateLpMatterRequest req) {
        requireFixedFeeAmountWhenFixedFee(req.billingType(), req.fixedFeeAmount());
        // When the firm has switched the gate on: no matter for a client whose due diligence is not verified and in date.
        kyc.requireCleared(tenantId, req.clientId(), "open a matter");
        // No matter without a conflict check that cleared this client: unused, in date, and not declined.
        var check = conflictService.requireUsableForMatter(tenantId, req.conflictCheckId(), req.clientId());
        LpMatter matter = LpMatter.create(tenantId, req.clientId(), req.attorneyId(), req.matterNumber(),
                req.matterType(), req.matterName(), req.description(), req.billingType(),
                req.fixedFeeAmount(), req.openedDate(), req.notes());
        matterRepo.save(matter);
        conflictService.consume(tenantId, check, matter.getId());
        log.info("Created legal practice matter={} number={} tenant={}", matter.getId(), matter.getMatterNumber(), tenantId);
        return toResponse(matter);
    }

    @Transactional
    public LpMatterResponse updateMatter(TenantId tenantId, UUID matterId, UpdateLpMatterRequest req) {
        requireFixedFeeAmountWhenFixedFee(req.billingType(), req.fixedFeeAmount());
        LpMatter matter = findOwn(tenantId, matterId);
        matter.update(req.attorneyId(), req.matterName(), req.description(),
                req.billingType(), req.fixedFeeAmount(), req.notes());
        matterRepo.save(matter);
        return toResponse(matter);
    }

    @Transactional
    public LpMatterResponse putOnHold(TenantId tenantId, UUID matterId) {
        LpMatter matter = findOwn(tenantId, matterId);
        matter.putOnHold();
        matterRepo.save(matter);
        return toResponse(matter);
    }

    @Transactional
    public LpMatterResponse reopen(TenantId tenantId, UUID matterId) {
        LpMatter matter = findOwn(tenantId, matterId);
        matter.reopen();
        matterRepo.save(matter);
        return toResponse(matter);
    }

    @Transactional
    public LpMatterResponse close(TenantId tenantId, UUID matterId, CloseMatterRequest req) {
        LpMatter matter = findOwn(tenantId, matterId);
        // Client money recorded against this matter must be paid out, transferred or refunded before the file closes.
        BigDecimal held = trustRepo.netHeldForMatter(tenantId, matterId);
        if (held != null && held.signum() > 0) {
            throw new IllegalStateException("This matter still has " + held.toPlainString()
                    + " held in trust. Transfer it, pay it out or refund it before closing the matter.");
        }
        matter.close(req.closedDate());
        matterRepo.save(matter);
        return toResponse(matter);
    }

    @Transactional
    public LpMatterResponse archive(TenantId tenantId, UUID matterId) {
        LpMatter matter = findOwn(tenantId, matterId);
        matter.archive();
        matterRepo.save(matter);
        return toResponse(matter);
    }

    LpMatter findOwn(TenantId tenantId, UUID matterId) {
        LpMatter matter = matterRepo.findActiveById(tenantId, matterId)
                .orElseThrow(() -> new ResourceNotFoundException("LpMatter", matterId.toString()));
        walls.requireAccess(tenantId, matter);
        return matter;
    }

    private void requireFixedFeeAmountWhenFixedFee(String billingType, BigDecimal fixedFeeAmount) {
        if ("FIXED_FEE".equals(billingType) && (fixedFeeAmount == null || fixedFeeAmount.signum() <= 0)) {
            throw new IllegalArgumentException("fixedFeeAmount is required and must be positive when billingType is FIXED_FEE");
        }
    }

    private LpMatterResponse toResponse(LpMatter m) {
        return new LpMatterResponse(m.getId(), m.getClientId(), m.getAttorneyId(), m.getMatterNumber(),
                m.getMatterType(), m.getMatterName(), m.getDescription(), m.getBillingType(),
                m.getFixedFeeAmount(), m.getStatus(), m.getOpenedDate(), m.getClosedDate(), m.getNotes(),
                m.getCreatedAt(), m.getUpdatedAt());
    }
}
