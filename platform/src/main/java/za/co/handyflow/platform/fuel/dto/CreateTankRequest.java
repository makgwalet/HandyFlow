package za.co.handyflow.platform.fuel.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
public record CreateTankRequest(
        @NotBlank String name, @NotBlank String fuelType,
        @NotNull @jakarta.validation.constraints.Positive(message = "Capacity must be greater than zero") BigDecimal capacityLitres, String location,
        // FIX: closes the confirmed "lowThresholdPct silently dropped"
        // gap. Nullable/optional — null means "use the 20% platform
        // default", matching FuelTank.isLow()'s own fallback exactly.
        @jakarta.validation.constraints.DecimalMin(value = "0", message = "Low threshold cannot be negative") @jakarta.validation.constraints.DecimalMax(value = "100", message = "Low threshold cannot exceed 100%") BigDecimal lowThresholdPct
) {}