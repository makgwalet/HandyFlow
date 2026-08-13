package za.co.handyflow.platform.bookingagency.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OfferingResponse(
        UUID id, UUID clientId, String name, int durationMinutes,
        int bufferMinutes, BigDecimal price, boolean active
) {}