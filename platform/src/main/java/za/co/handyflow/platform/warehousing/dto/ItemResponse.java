package za.co.handyflow.platform.warehousing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ItemResponse(
        UUID id, UUID clientId, String sku, String description, String uom, BigDecimal storageRatePerUnitPerMonth,
        boolean active
) {}
