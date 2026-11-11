package za.co.handyflow.platform.fuel.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// FIX: closes the confirmed "no tank edit" gap. Deliberately does not
// include currentLitres — stock level changes only through
// receiveFuel()/dispatchFuel()/completeDelivery(), never a direct edit,
// same posture as every other stock-tracked entity in this codebase.
public record UpdateTankRequest(
        @NotBlank String name, @NotBlank String fuelType,
        @NotNull @jakarta.validation.constraints.Positive(message = "Capacity must be greater than zero") BigDecimal capacityLitres, String location,
        @jakarta.validation.constraints.DecimalMin(value = "0", message = "Low threshold cannot be negative") @jakarta.validation.constraints.DecimalMax(value = "100", message = "Low threshold cannot exceed 100%") BigDecimal lowThresholdPct
) {}