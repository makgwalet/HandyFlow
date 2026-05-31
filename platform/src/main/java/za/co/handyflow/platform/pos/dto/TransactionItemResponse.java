package za.co.handyflow.platform.pos.dto;
import java.math.BigDecimal;
import java.util.UUID;
public record TransactionItemResponse(
        UUID       id, UUID catalogueItemId, String itemName, String sku,
        BigDecimal qty, BigDecimal unitPrice, BigDecimal vatRate,
        BigDecimal vatAmount, BigDecimal discountPct, BigDecimal discountAmount,
        BigDecimal lineTotal
) {}