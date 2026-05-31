package za.co.handyflow.platform.pos.dto;
import java.math.BigDecimal;
public record UpdateStockItemRequest(
        BigDecimal reorderLevel,
        BigDecimal reorderQty,
        BigDecimal costPrice,
        String     location
) {}