package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Create or edit a DRAFT RFQ. With a requisitionId, lines come from that (approved) requisition and
 * {@code lines} may narrow them (requisitionLineId + quantity); without one, lines are free-form.
 * Weights default to 50/15/15/10/10 (price, lead time, technical, performance, B-BBEE) and must total 100.
 */
public record CreateRfqRequest(
        String title,
        String description,
        UUID requisitionId,
        UUID deliverToLocation,
        LocalDate requiredByDate,
        Instant closesAt,
        String currency,
        Weights weights,
        List<Line> lines,
        List<UUID> supplierIds
) {
    public record Weights(Integer price, Integer leadTime, Integer technical, Integer performance, Integer bbbee) {}

    public record Line(
            UUID requisitionLineId,
            UUID catalogueItemId,
            String itemName,
            String description,
            BigDecimal quantity,
            BigDecimal estimatedUnitCost
    ) {}
}
