package za.co.handyflow.platform.fleet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Create or (full-replace) update a technician. {@code active} defaults to true. */
public record SaveTechnicianRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 50) String phone,
        @Size(max = 255) String email,
        @PositiveOrZero BigDecimal hourlyRate,
        Boolean active
) {}