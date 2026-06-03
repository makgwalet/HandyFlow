package za.co.handyflow.platform.pos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockAdjustmentResponse(
        UUID   id,
        String adjustmentNumber,
        String reason,
        String notes,
        String status,
        List<AdjustmentLineResponse> lines,
        UUID    createdBy,
        UUID    appliedBy,
        Instant createdAt,
        Instant appliedAt
) {
    public record AdjustmentLineResponse(
            UUID       id,
            UUID       stockItemId,
            String     itemName,
            BigDecimal qtySystem,
            BigDecimal qtyActual,
            BigDecimal qtyDifference
    ) {}
}
