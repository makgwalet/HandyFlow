package za.co.handyflow.platform.security.dto;

import za.co.handyflow.platform.security.domain.model.ResourceCustody;

import java.time.Instant;
import java.util.UUID;

/** What the guard app needs to know about an item in its own custody; nothing about witnesses or other people. */
public record GuardCustodyView(
        UUID id,
        String resourceType,
        String resourceRef,
        Instant checkedOutAt,
        Instant checkedInAt,
        String conditionOnReturn
) {
    public static GuardCustodyView of(ResourceCustody c) {
        return new GuardCustodyView(c.getId(), c.getResourceType().name(), c.getResourceRef(), c.getCheckedOutAt(),
                c.getCheckedInAt(), c.getConditionOnReturn() == null ? null : c.getConditionOnReturn().name());
    }
}
