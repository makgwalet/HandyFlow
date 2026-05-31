package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateTierRequest(
        @NotBlank String name,
        String description,
        BigDecimal price,
        @Min(1) int quantity,
        LocalDateTime saleStart,
        LocalDateTime saleEnd
) {}