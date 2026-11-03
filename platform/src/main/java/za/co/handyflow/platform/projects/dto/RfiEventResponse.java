package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectRfiEvent;

import java.time.Instant;

public record RfiEventResponse(String eventType, String comment, String actorName, Instant occurredAt) {
    public static RfiEventResponse of(ProjectRfiEvent e) {
        return new RfiEventResponse(e.getEventType(), e.getComment(), e.getActorName(), e.getOccurredAt());
    }
}