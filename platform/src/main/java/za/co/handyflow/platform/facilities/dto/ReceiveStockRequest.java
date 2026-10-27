package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ReceiveStockRequest(
        @NotNull @DecimalMin("0.001") BigDecimal quantity, @NotNull @DecimalMin("0") BigDecimal unitCost, @Size(max = 300) String reference
) {}
