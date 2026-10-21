package za.co.handyflow.platform.catalogue.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One item as the catalogue screens see it. costPrice and marginPercent are null unless the caller may see prices. */
public record CatalogueItemResponse(
        UUID id, String name, String description, String sku, String barcode, String brand, String itemType,
        String status, String statusReason, Instant statusChangedAt, String unit, BigDecimal defaultPrice,
        BigDecimal vatRate, boolean vatExempt, UUID categoryId, String categoryName,
        BigDecimal costPrice, BigDecimal marginPercent, Long version, Instant createdAt, Instant updatedAt
) {}
