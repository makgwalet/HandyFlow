package za.co.handyflow.platform.debtcollection.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.debtcollection.domain.model.CaseAuditEvent;
import za.co.handyflow.platform.debtcollection.domain.repository.CaseAuditEventRepository;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Append-only audit trail for case changes. The acting user is read from the request's TenantContext when there is
 * one; outside a request (schedulers) there is none and the event is attributed to "system". Recording joins the
 * caller's transaction, so a change and its audit entry commit or roll back together — an audit failure fails the change.
 */
@Service
@RequiredArgsConstructor
public class CaseAuditService {

    private final CaseAuditEventRepository repository;

    @Transactional
    public void record(TenantId tenantId, UUID caseId, String eventType, String detail) {
        UUID actor = currentUserId();
        String name = actor == null ? "system" : currentUserName();
        repository.save(CaseAuditEvent.of(tenantId, caseId, eventType, detail, actor, name));
    }

    @Transactional(readOnly = true)
    public List<CaseAuditEvent> list(TenantId tenantId, UUID caseId) {
        return repository.findByCaseId(tenantId, caseId);
    }

    private static UUID currentUserId() {
        try {
            return TenantContext.getCurrentUserId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String currentUserName() {
        try {
            return TenantContext.getCurrentUserName();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
