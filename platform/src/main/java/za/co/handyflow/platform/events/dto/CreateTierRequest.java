package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateTierRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 2000) String description,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal price,
        @Min(1) @Max(1_000_000) int quantity,
        LocalDateTime saleStart,
        LocalDateTime saleEnd
) {}
