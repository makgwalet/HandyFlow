package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PriceRequest(
        @NotBlank @Size(max = 30) String fuelType,
        UUID accountId,   // empty = standard price list; set = contract price for that customer
        @NotNull @PositiveOrZero(message = "Price per litre cannot be negative") BigDecimal pricePerLitre,
        @PositiveOrZero(message = "The volume break cannot be negative") BigDecimal minLitres,
        @NotNull LocalDate validFrom,
        LocalDate validTo,
        @Size(max = 200) String note
) {}
