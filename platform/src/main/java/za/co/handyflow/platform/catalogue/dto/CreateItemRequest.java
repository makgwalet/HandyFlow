package za.co.handyflow.platform.catalogue.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateItemRequest(
        @NotBlank(message = "Item name is required")
        @Size(max = 255)
        String name,

        String description,

        UUID categoryId,

        @NotBlank(message = "Unit is required")
        String unit,

        @NotNull(message = "Default price is required")
        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        BigDecimal defaultPrice,

        @DecimalMin(value = "0.00")
        @DecimalMax(value = "100.00")
        BigDecimal vatRate
) {}
