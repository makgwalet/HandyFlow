package za.co.handyflow.platform.warehousing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryResponse(
        UUID id, UUID clientId, UUID itemId, UUID locationId, BigDecimal qtyOnHand, BigDecimal qtyAllocated,
        BigDecimal available
) {}
