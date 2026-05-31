package za.co.handyflow.platform.pos.dto;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
public record CreateStockItemRequest(
        @NotNull UUID       catalogueItemId,
        BigDecimal qtyOnHand,
        BigDecimal reorderLevel,
        BigDecimal reorderQty,
        BigDecimal costPrice,
        String     location
) {}