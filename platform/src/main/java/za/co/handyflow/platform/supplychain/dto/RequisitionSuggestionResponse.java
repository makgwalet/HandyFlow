package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** A replenishment SUGGESTION — nothing is created until a buyer raises a requisition from it. */
public record RequisitionSuggestionResponse(
        UUID catalogueItemId,
        String itemName,
        UUID locationId,
        BigDecimal availableQty,
        BigDecimal onOrderQty,
        BigDecimal reorderPoint,
        BigDecimal suggestedQty,
        UUID suggestedSupplierId,
        BigDecimal suggestedUnitCost
) {}
