package za.co.handyflow.platform.fuel.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.time.Instant;
import java.util.Map; import java.util.UUID;
public record CreateDeliveryRequest(
        @NotNull UUID tankId, UUID customerId,
        Map<String, String> deliveryAddress,
        @NotBlank String fuelType,
        @NotNull BigDecimal litresOrdered,
        @NotNull BigDecimal pricePerLitre,
        @NotNull Instant scheduledAt,
        String driverName, String vehicleReg
) {}