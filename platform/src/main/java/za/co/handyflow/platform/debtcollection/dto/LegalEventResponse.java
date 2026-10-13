package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.LegalEvent;

import java.time.Instant;
import java.util.UUID;

public record LegalEventResponse(UUID id, String eventType, String detail, UUID actorUserId, String actorUserName,
                                 Instant createdAt) {
    public static LegalEventResponse of(LegalEvent e) {
        return new LegalEventResponse(e.getId(), e.getEventType(), e.getDetail(), e.getActorUserId(),
                e.getActorUserName(), e.getCreatedAt());
    }
}
