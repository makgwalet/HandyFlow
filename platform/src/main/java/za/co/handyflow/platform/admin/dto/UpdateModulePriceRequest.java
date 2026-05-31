package za.co.handyflow.platform.admin.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
public record UpdateModulePriceRequest(
        @NotBlank          String     moduleKey,
        @Positive          BigDecimal newPrice
) {}
