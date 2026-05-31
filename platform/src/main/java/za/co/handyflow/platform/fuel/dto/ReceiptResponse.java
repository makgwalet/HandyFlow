package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
public record ReceiptResponse(
        UUID id, UUID tankId, UUID supplierId,
        BigDecimal litresReceived, BigDecimal pricePerLitre, BigDecimal totalCost,
        Instant receivedAt, String deliveryNote, String invoiceRef,
        BigDecimal levelBefore, BigDecimal levelAfter, Instant createdAt
) {}