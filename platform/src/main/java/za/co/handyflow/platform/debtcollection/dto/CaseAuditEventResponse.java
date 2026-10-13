package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.CaseAuditEvent;

import java.time.Instant;
import java.util.UUID;

public record CaseAuditEventResponse(UUID id, String eventType, String detail, UUID actorUserId, String actorUserName,
                                     Instant createdAt) {
    public static CaseAuditEventResponse of(CaseAuditEvent e) {
        return new CaseAuditEventResponse(e.getId(), e.getEventType(), e.getDetail(), e.getActorUserId(),
                e.getActorUserName(), e.getCreatedAt());
    }
}
