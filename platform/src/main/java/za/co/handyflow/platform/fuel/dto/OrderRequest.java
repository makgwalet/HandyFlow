package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OrderRequest(
        @NotNull UUID accountId,
        UUID siteId,
        @NotBlank @Size(max = 30) String fuelType,
        @NotNull @Positive(message = "Litres must be greater than zero") BigDecimal litres,
        @NotNull @PositiveOrZero(message = "Price per litre cannot be negative") BigDecimal pricePerLitre,
        LocalDate requestedDate,
        LocalDate quoteValidUntil,
        @Size(max = 60) String customerReference,
        @Size(max = 500) String notes
) {}
