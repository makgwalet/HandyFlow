package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PurchaseOrderRequest(
        @NotNull UUID supplierId,
        @NotBlank String fuelType,
        @NotNull @Positive BigDecimal litresOrdered,
        @NotNull @PositiveOrZero BigDecimal pricePerLitre,
        LocalDate expectedDate,
        @Size(max = 500) String notes
) {}
