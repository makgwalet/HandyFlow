package za.co.handyflow.platform.billing.api;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        String planName,
        String planDisplayName,
        String status,
        Instant pilotEndsAt,
        Long pilotDaysRemaining,
        Instant currentPeriodEnd,
        int priceInRands
) {
}
