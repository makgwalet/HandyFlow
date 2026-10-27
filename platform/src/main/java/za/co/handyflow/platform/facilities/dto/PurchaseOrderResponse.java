package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id, String poNumber, UUID vendorId, String vendorName, String status, LocalDate expectedDate, String notes,
        BigDecimal totalValue, UUID createdByUserId, UUID approvedByUserId, Instant approvedAt, String closedReason,
        Instant createdAt, List<Line> lines
) {
    public record Line(UUID id, int lineNo, UUID itemId, String description, BigDecimal orderedQty, BigDecimal receivedQty,
                       BigDecimal outstandingQty, BigDecimal unitCost, BigDecimal lineTotal) {}
}
