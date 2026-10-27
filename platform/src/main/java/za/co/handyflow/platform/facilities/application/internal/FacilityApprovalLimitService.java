package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityApprovalLimit;
import za.co.handyflow.platform.facilities.domain.repository.FacilityApprovalLimitRepository;
import za.co.handyflow.platform.facilities.dto.ApprovalLimitResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Who may approve purchase orders up to what value. Changes are audited because they control spending. */
@Service
@RequiredArgsConstructor
public class FacilityApprovalLimitService {

    private final FacilityApprovalLimitRepository limitRepository;
    private final FacilityAuditService auditService;

    @Transactional(readOnly = true)
    public List<ApprovalLimitResponse> list(TenantId tenantId) {
        return limitRepository.findAllForTenant(tenantId).stream().map(FacilityApprovalLimitService::toResponse).toList();
    }

    @Transactional
    public ApprovalLimitResponse set(TenantId tenantId, UUID userId, BigDecimal max) {
        UUID by = TenantContext.getCurrentUserId();
        FacilityApprovalLimit limit = limitRepository.findForUser(tenantId, userId).orElse(null);
        BigDecimal before = limit == null ? null : limit.getMaxOrderValue();
        if (limit == null) limit = FacilityApprovalLimit.create(tenantId, userId, max, by);
        else limit.change(max, by);
        limitRepository.save(limit);
        auditService.record(tenantId, "APPROVAL_LIMIT_SET", "APPROVAL_LIMIT", userId,
                "user=" + userId + " limit: " + (before == null ? "none" : before.toPlainString()) + " -> " + limit.getMaxOrderValue().toPlainString());
        return toResponse(limit);
    }

    @Transactional
    public void remove(TenantId tenantId, UUID userId) {
        FacilityApprovalLimit limit = limitRepository.findForUser(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("FacilityApprovalLimit", userId.toString()));
        limitRepository.delete(limit);
        auditService.record(tenantId, "APPROVAL_LIMIT_REMOVED", "APPROVAL_LIMIT", userId,
                "user=" + userId + " limit was " + limit.getMaxOrderValue().toPlainString());
    }

    private static ApprovalLimitResponse toResponse(FacilityApprovalLimit l) {
        return new ApprovalLimitResponse(l.getUserId(), l.getMaxOrderValue(), l.getSetByUserId(), l.getUpdatedAt());
    }
}
