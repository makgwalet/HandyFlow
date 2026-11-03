package za.co.handyflow.platform.projects.dto;

import za.co.handyflow.platform.projects.domain.model.ProjectMember;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID    userId,
        String  userName,
        String  role,
        boolean canWrite,
        boolean canViewCost,
        boolean canManageMembers,
        Instant addedAt
) {
    public static MemberResponse of(ProjectMember m) {
        return new MemberResponse(
                m.getUserId(), m.getUserName(), m.getRole().name(),
                m.getRole().canWrite(), m.getRole().canViewCost(), m.getRole().canManageMembers(),
                m.getAddedAt());
    }
}