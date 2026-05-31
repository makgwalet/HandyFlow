package za.co.handyflow.platform.property.dto;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal; import java.util.Map; import java.util.UUID;
public record CreatePropertyRequest(
        @NotBlank String name, @NotBlank String propertyType,
        @NotNull Map<String, String> address, String description,
        UUID customerId, BigDecimal purchasePrice, BigDecimal marketValue
) {}