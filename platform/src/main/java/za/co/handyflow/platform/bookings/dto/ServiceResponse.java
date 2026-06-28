package za.co.handyflow.platform.bookings.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ServiceResponse(
        UUID       id,
        String     name,
        String     description,
        int        durationMinutes,
        BigDecimal price,
        String     currency,
        String     color,
        boolean    active,
        int        bufferBeforeMinutes,
        int        bufferAfterMinutes,
        int        minLeadTimeMinutes,
        int        maxAdvanceDays,
        Instant    createdAt
) {}
