package za.co.handyflow.platform.pos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// POST /pos/adjustments  (creates in DRAFT)
// POST /pos/adjustments/{id}/apply  (locks and fires movements)
public record CreateStockAdjustmentRequest(
        @NotBlank String      reason,    // STOCK_COUNT|DAMAGE|THEFT|EXPIRY|CORRECTION|OTHER
        String                notes,
        @NotEmpty List<AdjustmentLine> lines
) {
    public record AdjustmentLine(
            @jakarta.validation.constraints.NotNull UUID stockItemId,
            BigDecimal qtyActual    // what you physically counted
    ) {}
}
