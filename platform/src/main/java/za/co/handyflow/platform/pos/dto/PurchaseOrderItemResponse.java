package za.co.handyflow.platform.pos.dto;
import java.math.BigDecimal;
import java.util.UUID;
public record PurchaseOrderItemResponse(
        UUID id, UUID catalogueItemId, String itemName,
        BigDecimal qtyOrdered, BigDecimal qtyReceived,
        BigDecimal unitCost, BigDecimal vatRate, BigDecimal lineTotal,
        boolean fullyReceived
) {}