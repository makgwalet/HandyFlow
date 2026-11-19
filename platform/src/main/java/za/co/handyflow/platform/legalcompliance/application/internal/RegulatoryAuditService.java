package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryEvent;
import za.co.handyflow.platform.legalcompliance.domain.repository.RegulatoryEventRepository;

import java.util.UUID;

/** Append-only. Written in the caller's transaction, so an event exists if and only if its change committed. */
@Component
@RequiredArgsConstructor
public class RegulatoryAuditService {

    private final RegulatoryEventRepository events;

    public void record(UUID tenantId, String entityType, UUID entityId, String eventType, String detail, UUID actorId, String actorName) {
        events.save(RegulatoryEvent.of(tenantId, entityType, entityId, eventType, detail, actorId, actorName));
    }
}
