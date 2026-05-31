package za.co.handyflow.platform.fuel.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
public record CreateTankRequest(
        @NotBlank String name, @NotBlank String fuelType,
        @NotNull BigDecimal capacityLitres, String location
) {}