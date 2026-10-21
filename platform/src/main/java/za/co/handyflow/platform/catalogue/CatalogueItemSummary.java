package za.co.handyflow.platform.catalogue;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * WHY at module root?
 * Invoicing, POS and others reference catalogue items when creating lines.
 * This summary DTO is what they see, not the entity. Cost price is deliberately not here:
 * consuming modules do not need it and it is commercially sensitive.
 * <p>
 * sku, barcode, itemType, status and vatExempt were added after the first seven fields, so existing
 * accessors keep working. status is the lifecycle state name (ACTIVE, SUSPENDED, ...).
 */
public record CatalogueItemSummary(
        UUID id,
        String name,
        String description,
        String unit,
        BigDecimal defaultPrice,
        BigDecimal vatRate,
        String categoryName,
        String sku,
        String barcode,
        String itemType,
        String status,
        boolean vatExempt
) {}
