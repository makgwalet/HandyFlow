package za.co.handyflow.platform.debtcollection.dto;

import za.co.handyflow.platform.debtcollection.domain.model.PtpEvent;

import java.time.Instant;
import java.util.UUID;

public record PtpEventResponse(UUID id, String eventType, String detail, UUID actorUserId, String actorUserName,
                               Instant createdAt) {
    public static PtpEventResponse of(PtpEvent e) {
        return new PtpEventResponse(e.getId(), e.getEventType(), e.getDetail(), e.getActorUserId(),
                e.getActorUserName(), e.getCreatedAt());
    }
}
