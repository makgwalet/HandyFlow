package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Pre-fill data for the "Receive stock" form when triggered from a low-stock
 * alert — suggestedLitres tops the tank back up to capacity, and the
 * lastSupplier* fields carry forward whoever supplied this tank last time,
 * so the form isn't a blank slate.
 * <p>
 * lastSupplierId/lastSupplierName/lastPricePerLitre are all null if the tank
 * has never had a receipt recorded (a supplier is a suggestion, not a
 * requirement — the person filling the form can always pick a different one).
 */
public record ReorderSuggestionResponse(
        UUID tankId,
        String tankName,
        BigDecimal capacityLitres,
        BigDecimal currentLitres,
        BigDecimal suggestedLitres,
        UUID lastSupplierId,
        String lastSupplierName,
        BigDecimal lastPricePerLitre
) {
}