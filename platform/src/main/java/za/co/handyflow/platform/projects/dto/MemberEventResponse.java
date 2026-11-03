package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectMemberEvent;

import java.time.Instant;
import java.util.UUID;

public record MemberEventResponse(
        UUID userId, String action, String oldRole, String newRole,
        UUID actorId, String actorName, Instant occurredAt
) {
    public static MemberEventResponse of(ProjectMemberEvent e) {
        return new MemberEventResponse(
                e.getUserId(), e.getAction(),
                e.getOldRole() != null ? e.getOldRole().name() : null,
                e.getNewRole() != null ? e.getNewRole().name() : null,
                e.getActorId(), e.getActorName(), e.getOccurredAt());
    }
}