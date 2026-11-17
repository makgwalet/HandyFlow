package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GoodsReceiptLineResponse(
        UUID id,
        UUID poLineId,
        UUID catalogueItemId,
        String itemName,
        BigDecimal qtyOrdered,
        BigDecimal qtyReceived,
        BigDecimal qtyRejected,
        BigDecimal qtyAccepted,
        String rejectionReason,
        String condition,
        BigDecimal unitCost,
        String lotNumber,
        LocalDate expiryDate
) {}
