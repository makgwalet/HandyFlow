package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ordered vs received vs priced, for one purchase order. Variance is receipt minus order, so positive means we paid more than the order said. */
public record PurchaseOrderMatchResponse(PurchaseOrderResponse order, BigDecimal receivedValue, BigDecimal valueVariance,
                                         BigDecimal litresVariance, List<Line> receipts) {
    public record Line(UUID receiptId, Instant receivedAt, UUID tankId, BigDecimal litres, BigDecimal pricePerLitre, BigDecimal priceVariance,
                       BigDecimal totalCost, String deliveryNote, String invoiceRef, boolean reversed, boolean priceDiffers) {}
}
