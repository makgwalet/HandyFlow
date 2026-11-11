package za.co.handyflow.platform.events.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.events.domain.model.EventAuditRecord;
import za.co.handyflow.platform.events.domain.repository.EventAuditRepository;
import za.co.handyflow.platform.events.dto.AuditEntryResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Writes audit lines INSIDE the caller's transaction (default REQUIRED
 * propagation): the audit entry commits or rolls back together with the
 * business change it describes, so history can never show an action that
 * didn't happen, or miss one that did.
 * <p>
 * The actor comes from TenantContext. Outside a request (a scheduler, say)
 * there is no user in context, and the entry is recorded as "System".
 * <p>
 * PRIVACY: summaries reference ticket numbers, not guest names or emails —
 * the audit log is long-lived and should not become a second copy of
 * personal information.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventAuditService {

    private final EventAuditRepository repo;

    @Transactional
    public void record(TenantId tenantId, UUID eventId, String action, String entityType,
                       UUID entityId, String summary, List<String> details) {
        String detailText = (details == null || details.isEmpty()) ? null : String.join("\n", details);
        repo.save(EventAuditRecord.create(tenantId.getValue(), eventId, action, entityType,
                entityId, summary, detailText, currentActorId(), currentActorName()));
    }

    @Transactional
    public void record(TenantId tenantId, UUID eventId, String action, String entityType,
                       UUID entityId, String summary) {
        record(tenantId, eventId, action, entityType, entityId, summary, null);
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> getTrail(TenantId tenantId, UUID eventId, Pageable pageable) {
        return repo.findTrail(tenantId.getValue(), eventId, pageable)
                .map(a -> new AuditEntryResponse(a.getId(), a.getAction(), a.getEntityType(),
                        a.getEntityId(), a.getSummary(), a.getDetails(),
                        a.getActorName(), a.getOccurredAt()));
    }

    private UUID currentActorId() {
        String raw = TenantContext.getUserId();
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String currentActorName() {
        return TenantContext.getUserId() == null ? "System" : TenantContext.getCurrentUserName();
    }
}