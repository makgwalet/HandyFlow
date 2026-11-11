package za.co.handyflow.platform.fuel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/** Which depot a tank sits in (null = none) and how much stock must be held back from promises. */
public record TankPlacementRequest(
        UUID depotId,
        @NotNull @PositiveOrZero(message = "Safety stock cannot be negative") BigDecimal safetyStockLitres
) {}
