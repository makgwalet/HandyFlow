package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StockMovementResponse(
        UUID id,
        UUID inventoryItemId,
        String movementType,
        LocalDate movementDate,
        BigDecimal quantity,
        BigDecimal unitCost,
        BigDecimal totalCost,
        String referenceType,
        UUID referenceId,
        UUID performedBy,
        String performedByName,
        String notes,
        Instant createdAt,
        UUID supplierId,       // the supplier a RECEIPT was bought from, if recorded
        String supplierName,
        BigDecimal packSizeKg  // kg per bag used when this was posted; null for kg items
) {
    public StockMovementResponse(UUID id, UUID inventoryItemId, String movementType, LocalDate movementDate, BigDecimal quantity,
                                 BigDecimal unitCost, BigDecimal totalCost, String referenceType, UUID referenceId, UUID performedBy,
                                 String performedByName, String notes, Instant createdAt, UUID supplierId, String supplierName) {
        this(id, inventoryItemId, movementType, movementDate, quantity, unitCost, totalCost, referenceType, referenceId,
                performedBy, performedByName, notes, createdAt, supplierId, supplierName, null);
    }
}
