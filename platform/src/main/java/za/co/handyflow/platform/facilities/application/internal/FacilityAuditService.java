package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilities.domain.model.FacilityAuditLog;
import za.co.handyflow.platform.facilities.domain.repository.FacilityAuditLogRepository;
import za.co.handyflow.platform.facilities.dto.AuditLogResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Records sensitive actions. {@link #record} joins the caller's transaction
 * and is deliberately NOT wrapped in try/catch: if the audit row can't be
 * written the action rolls back, so a sensitive change never happens
 * without its trail. If the platform already has a shared audit facility,
 * swap this class's internals for it and keep the call sites.
 */
@Service
@RequiredArgsConstructor
public class FacilityAuditService {

    private final FacilityAuditLogRepository auditRepository;

    @Transactional
    public void record(TenantId tenantId, String action, String entityType, UUID entityId, String details) {
        auditRepository.save(FacilityAuditLog.of(tenantId, TenantContext.getCurrentUserId(),
                TenantContext.getCurrentUserName(), action, entityType, entityId, details));
    }

    /** For scheduled jobs, where there is no signed-in user: recorded with no actor and the name "System". */
    @Transactional
    public void recordSystem(TenantId tenantId, String action, String entityType, UUID entityId, String details) {
        auditRepository.save(FacilityAuditLog.of(tenantId, null, "System", action, entityType, entityId, details));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(TenantId tenantId, String entityType, UUID entityId, Pageable pageable) {
        if (entityId != null && (entityType == null || entityType.isBlank())) {
            throw new IllegalArgumentException("entityType is required when filtering by entityId");
        }
        Page<FacilityAuditLog> page = entityId != null
                ? auditRepository.findForEntity(tenantId, entityType.toUpperCase(), entityId, pageable)
                : auditRepository.findAllForTenant(tenantId, entityType != null && !entityType.isBlank() ? entityType.toUpperCase() : null, pageable);
        return page.map(a -> new AuditLogResponse(a.getId(), a.getActorUserId(), a.getActorName(), a.getAction(),
                a.getEntityType(), a.getEntityId(), a.getDetails(), a.getCreatedAt()));
    }
}