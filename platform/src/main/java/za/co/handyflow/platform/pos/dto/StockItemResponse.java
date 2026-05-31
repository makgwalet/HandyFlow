package za.co.handyflow.platform.pos.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public record StockItemResponse(
        UUID       id, UUID catalogueItemId,
        String     itemName, String sku, String barcode,
        BigDecimal qtyOnHand, BigDecimal qtyReserved, BigDecimal availableQty,
        BigDecimal reorderLevel, BigDecimal reorderQty,
        BigDecimal costPrice, BigDecimal sellingPrice,
        String     location, boolean lowStock,
        Instant    updatedAt
) {}