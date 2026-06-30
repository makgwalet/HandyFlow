package za.co.handyflow.platform.security.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ItineraryStopResponse(
        UUID    id,
        UUID    detailId,
        int     sequence,
        String  locationName,
        String  address,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant scheduledArrival,
        Instant scheduledDeparture,
        Instant actualArrival,
        Instant actualDeparture,
        boolean advanceSurveyRequired,
        String  notes,
        String  status   // PENDING | IN_PROGRESS | COMPLETED
) {}
