package za.co.handyflow.platform.billing.api;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
        UUID    id,
        String  planName,
        String  planDisplayName,
        String  status,                  // PILOT | ACTIVE | PAST_DUE | SUSPENDED | CANCELLED
        Instant pilotEndsAt,
        Long    pilotDaysRemaining,
        Instant currentPeriodEnd,
        int     priceInRands,

        // B5: Grace period fields — used by frontend to show payment warning banner
        Instant pastDueSince,            // non-null when PAST_DUE
        Long    graceDaysRemaining,      // days left before suspension (null if not past due)
        boolean suspended                // true when SUSPENDED — frontend shows hard block
) {}
