package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmAuditLog;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmAuditLogRepository;
import za.co.handyflow.platform.facilitiesmanagement.dto.FmAuditLogResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Records sensitive actions. {@link #record} joins the caller's transaction and is deliberately NOT
 * wrapped in try/catch: if the audit row can't be written the action rolls back, so a sensitive change
 * never happens without its trail. If the platform already has a shared audit facility, swap this
 * class's internals for it and keep the call sites.
 */
@Service
@RequiredArgsConstructor
public class FmAuditService {

    private final FmAuditLogRepository auditRepository;

    @Transactional
    public void record(TenantId tenantId, String action, String entityType, UUID entityId, String details) {
        auditRepository.save(FmAuditLog.of(tenantId, TenantContext.getCurrentUserId(),
                TenantContext.getCurrentUserName(), action, entityType, entityId, details));
    }

    /**
     * For actions taken by a client through the portal. A portal token carries no staff user and no tenant context,
     * so the actor is the portal user's id with a fixed label.
     */
    @Transactional
    public void recordPortal(TenantId tenantId, UUID portalUserId, String action, String entityType, UUID entityId, String details) {
        auditRepository.save(FmAuditLog.of(tenantId, portalUserId, "Client portal", action, entityType, entityId, details));
    }

    /** For actions taken by the platform itself (a scheduled sweep): there is no signed-in user. */
    @Transactional
    public void recordSystem(TenantId tenantId, String action, String entityType, UUID entityId, String details) {
        auditRepository.save(FmAuditLog.of(tenantId, null, "System", action, entityType, entityId, details));
    }

    @Transactional(readOnly = true)
    public Page<FmAuditLogResponse> list(TenantId tenantId, String entityType, UUID entityId, Pageable pageable) {
        if (entityId != null && (entityType == null || entityType.isBlank())) {
            throw new IllegalArgumentException("entityType is required when filtering by entityId");
        }
        Page<FmAuditLog> page = entityId != null
                ? auditRepository.findForEntity(tenantId, entityType.toUpperCase(), entityId, pageable)
                : auditRepository.findAllForTenant(tenantId, entityType != null && !entityType.isBlank() ? entityType.toUpperCase() : null, pageable);
        return page.map(a -> new FmAuditLogResponse(a.getId(), a.getActorUserId(), a.getActorName(), a.getAction(),
                a.getEntityType(), a.getEntityId(), a.getDetails(), a.getCreatedAt()));
    }
}