package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.WorkItemEvent;

import java.time.Instant;
import java.util.UUID;

public record WorkItemEventResponse(UUID id, String eventType, String detail, UUID actorUserId, String actorUserName,
                                    Instant createdAt) {
    public static WorkItemEventResponse of(WorkItemEvent e) {
        return new WorkItemEventResponse(e.getId(), e.getEventType(), e.getDetail(), e.getActorUserId(),
                e.getActorUserName(), e.getCreatedAt());
    }
}
