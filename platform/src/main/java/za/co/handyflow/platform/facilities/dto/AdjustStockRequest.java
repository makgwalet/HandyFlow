package za.co.handyflow.platform.facilities.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Stocktake: the quantity actually counted. The difference is booked as an adjustment. */
public record AdjustStockRequest(
        @NotNull @DecimalMin("0") BigDecimal countedQuantity, @NotBlank @Size(max = 300) String reason
) {}
