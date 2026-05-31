package za.co.handyflow.platform.bookings.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record CreateServiceRequest(
        @NotBlank String name,
        String description,
        @Min(5) int durationMinutes,
        BigDecimal price,
        String color
) {}