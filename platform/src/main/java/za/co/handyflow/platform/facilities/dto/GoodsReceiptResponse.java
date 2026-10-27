package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record GoodsReceiptResponse(
        UUID id, String grnNumber, UUID purchaseOrderId, UUID vendorId, String deliveryNote, LocalDate receivedOn,
        BigDecimal totalValue, UUID receivedByUserId, Instant createdAt, List<Line> lines
) {
    public record Line(UUID poLineId, UUID itemId, String description, BigDecimal quantity, BigDecimal unitCost, BigDecimal lineValue) {}
}
