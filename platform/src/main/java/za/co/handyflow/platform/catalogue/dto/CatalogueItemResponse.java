package za.co.handyflow.platform.catalogue.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One item as the catalogue screens see it. costPrice and marginPercent are null unless the caller may see prices; attributes is null in lists. */
public record CatalogueItemResponse(
        UUID id, String name, String description, String sku, String barcode, String brand, String itemType,
        String status, String statusReason, Instant statusChangedAt, String unit, BigDecimal defaultPrice,
        BigDecimal vatRate, boolean vatExempt, UUID categoryId, String categoryName,
        BigDecimal costPrice, BigDecimal marginPercent, Long version, Instant createdAt, Instant updatedAt,
        List<CatalogueAttributeValueResponse> attributes,
        UUID parentId, String variantLabel, long variantCount
) {
    /** With attribute values but no variant information. */
    public CatalogueItemResponse(UUID id, String name, String description, String sku, String barcode, String brand, String itemType,
                                 String status, String statusReason, Instant statusChangedAt, String unit, BigDecimal defaultPrice,
                                 BigDecimal vatRate, boolean vatExempt, UUID categoryId, String categoryName,
                                 BigDecimal costPrice, BigDecimal marginPercent, Long version, Instant createdAt, Instant updatedAt,
                                 List<CatalogueAttributeValueResponse> attributes) {
        this(id, name, description, sku, barcode, brand, itemType, status, statusReason, statusChangedAt, unit, defaultPrice,
                vatRate, vatExempt, categoryId, categoryName, costPrice, marginPercent, version, createdAt, updatedAt, attributes, null, null, 0L);
    }

    /** Without attribute values (lists do not load them). */
    public CatalogueItemResponse(UUID id, String name, String description, String sku, String barcode, String brand, String itemType,
                                 String status, String statusReason, Instant statusChangedAt, String unit, BigDecimal defaultPrice,
                                 BigDecimal vatRate, boolean vatExempt, UUID categoryId, String categoryName,
                                 BigDecimal costPrice, BigDecimal marginPercent, Long version, Instant createdAt, Instant updatedAt) {
        this(id, name, description, sku, barcode, brand, itemType, status, statusReason, statusChangedAt, unit, defaultPrice,
                vatRate, vatExempt, categoryId, categoryName, costPrice, marginPercent, version, createdAt, updatedAt, null, null, null, 0L);
    }

    public CatalogueItemResponse withAttributes(List<CatalogueAttributeValueResponse> values) {
        return new CatalogueItemResponse(id, name, description, sku, barcode, brand, itemType, status, statusReason, statusChangedAt,
                unit, defaultPrice, vatRate, vatExempt, categoryId, categoryName, costPrice, marginPercent, version, createdAt, updatedAt, values,
                parentId, variantLabel, variantCount);
    }

    /** For a parent: how many live variants it has. */
    public CatalogueItemResponse withVariantCount(long count) {
        return new CatalogueItemResponse(id, name, description, sku, barcode, brand, itemType, status, statusReason, statusChangedAt,
                unit, defaultPrice, vatRate, vatExempt, categoryId, categoryName, costPrice, marginPercent, version, createdAt, updatedAt, attributes,
                parentId, variantLabel, count);
    }
}
