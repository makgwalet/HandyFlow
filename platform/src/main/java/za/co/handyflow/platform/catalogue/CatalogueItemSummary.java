package za.co.handyflow.platform.catalogue;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * WHY at module root?
 * Invoicing needs to reference catalogue items when creating quote lines.
 * This summary DTO is what invoicing sees — not the entity.
 */
public record CatalogueItemSummary(
        UUID id,
        String name,
        String description,
        String unit,
        BigDecimal defaultPrice,
        BigDecimal vatRate,
        String categoryName
) {}
