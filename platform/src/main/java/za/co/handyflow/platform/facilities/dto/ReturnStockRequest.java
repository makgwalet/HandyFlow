package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record ReturnStockRequest(
        UUID workOrderId, @NotNull @DecimalMin("0.001") BigDecimal quantity, @NotBlank @Size(max = 300) String reason
) {}
