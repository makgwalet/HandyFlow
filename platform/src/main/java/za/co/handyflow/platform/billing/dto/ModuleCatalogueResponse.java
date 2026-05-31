package za.co.handyflow.platform.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ModuleCatalogueResponse(
        UUID id,
        String key,
        String name,
        String description,
        BigDecimal monthlyPrice,
        String currency,
        String icon,
        String category,
        int sortOrder
) {}