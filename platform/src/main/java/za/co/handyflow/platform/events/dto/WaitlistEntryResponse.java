package za.co.handyflow.platform.events.dto;

import java.time.Instant;
import java.util.UUID;

public record WaitlistEntryResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        String company,
        UUID tierId,
        String tierName,
        String status,          // WAITING | PROMOTED | REMOVED
        Integer position,       // 1-based place in the queue; null unless status is WAITING
        Instant createdAt,
        UUID promotedGuestId
) {}