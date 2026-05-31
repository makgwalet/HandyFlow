package za.co.handyflow.platform.events.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TierResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        int quantity,
        int quantitySold,
        int quantityCheckedIn,
        int available,
        LocalDateTime saleStart,
        LocalDateTime saleEnd,
        boolean active
) {}