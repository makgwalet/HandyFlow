package za.co.handyflow.platform.catalogue.dto;

import jakarta.validation.constraints.*;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItemStatus;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItemType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Create or edit an item. Everything after vatRate is optional, so older callers keep working.
 * On edit, send back the {@code version} you loaded: if someone else saved in between, the edit is refused
 * instead of silently overwriting their change.
 */
public record CreateItemRequest(
        @NotBlank(message = "Item name is required")
        @Size(max = 255)
        String name,

        String description,

        UUID categoryId,

        @NotBlank(message = "Unit is required")
        @Size(max = 50)
        String unit,

        @NotNull(message = "Default price is required")
        @DecimalMin(value = "0.00", message = "Price cannot be negative")
        BigDecimal defaultPrice,

        @DecimalMin(value = "0.00")
        @DecimalMax(value = "100.00")
        BigDecimal vatRate,

        @Size(max = 50) String sku,
        @Size(max = 100) String barcode,
        @Size(max = 100) String brand,
        CatalogueItemType itemType,
        @DecimalMin(value = "0.00", message = "Cost cannot be negative") BigDecimal costPrice,
        Boolean vatExempt,
        /** Only used on create: DRAFT or ACTIVE (default ACTIVE). */
        CatalogueItemStatus status,
        /** Only used on edit. */
        Long version,
        /**
         * Attribute values keyed by definition id. Null leaves the stored values alone; a map replaces them all
         * (an empty or blank value clears that attribute).
         */
        Map<UUID, String> attributes
) {
    /** The shape before attributes existed, so older callers keep compiling. */
    public CreateItemRequest(String name, String description, UUID categoryId, String unit, BigDecimal defaultPrice,
                             BigDecimal vatRate, String sku, String barcode, String brand, CatalogueItemType itemType,
                             BigDecimal costPrice, Boolean vatExempt, CatalogueItemStatus status, Long version) {
        this(name, description, categoryId, unit, defaultPrice, vatRate, sku, barcode, brand, itemType, costPrice,
                vatExempt, status, version, null);
    }
}
