package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Used for both create and (full-replace) update of a DRAFT.
 * type: STOCK_REPLENISHMENT | OPERATIONAL | CAPEX | PROJECT | EMERGENCY | SERVICE | CONTRACT_RENEWAL | RECURRING
 * Line catalogueItemId is optional (services / non-stock); it enables the stock and open-order checks.
 * vatRate null = standard rate.
 */
public record CreateRequisitionRequest(
        String type,
        String title,
        String businessReason,
        String department,
        String costCentre,
        String projectRef,
        UUID deliverToLocation,
        LocalDate requiredByDate,
        String currency,
        List<Line> lines
) {
    public record Line(UUID catalogueItemId, String itemName, String description, BigDecimal quantity,
                       BigDecimal estimatedUnitCost, BigDecimal vatRate, UUID suggestedSupplierId) {}
}
