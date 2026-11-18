package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * fulfilment (NOT_ORDERED | PARTIALLY_ORDERED | ORDERED) is derived and only set once APPROVED.
 * Line availability fields are live while DRAFT/PENDING, and the snapshot taken at submission afterwards.
 * lines / conversions / advisories are empty in list responses.
 */
public record RequisitionResponse(
        UUID id,
        String requisitionNumber,
        String type,
        String status,
        String fulfilment,
        String title,
        String businessReason,
        String department,
        String costCentre,
        String projectRef,
        UUID deliverToLocation,
        LocalDate requiredByDate,
        BigDecimal estimatedTotal,
        String currency,
        String requestedByName,
        Instant createdAt,
        Instant submittedAt,
        String approvedByName,
        Instant approvedAt,
        String decisionNote,
        List<Line> lines,
        List<Conversion> conversions,
        List<String> advisories
) {
    public record Line(UUID id, int lineNumber, UUID catalogueItemId, String itemName, String description,
                       BigDecimal qtyRequested, BigDecimal qtyOrdered, BigDecimal qtyRemaining,
                       BigDecimal estimatedUnitCost, BigDecimal vatRate, UUID suggestedSupplierId,
                       BigDecimal stockOnHand, BigDecimal onOrderQty, BigDecimal alreadyRequestedQty) {}

    public record Conversion(UUID purchaseOrderId, String orderNumber, String poStatus, UUID requisitionLineId,
                             BigDecimal quantity, BigDecimal unitCost, Instant createdAt,
                             BigDecimal requisitionUnitCost, String priceStatus, String priceChangeReason,
                             BigDecimal savingAmount) {}

    public record PriceOverride(UUID id, UUID requisitionId, String requisitionNumber, UUID purchaseOrderId, String orderNumber,
                                String itemName, BigDecimal quantity, BigDecimal requisitionUnitCost, BigDecimal unitCost,
                                String reason, String note, String quotationRef, String status, String convertedByName,
                                Instant createdAt) {}
}
