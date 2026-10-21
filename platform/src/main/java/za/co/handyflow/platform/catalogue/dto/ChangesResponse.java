package za.co.handyflow.platform.catalogue.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What changed since a cursor. Each item appears once, with its latest state ({@code UPSERT}) or as {@code DELETE} when it no
 * longer exists. Keep {@code cursor} and send it back as {@code since}; when {@code hasMore} is true ask again straight away.
 * Prices are null for callers who may not see them.
 */
public record ChangesResponse(long cursor, boolean hasMore, List<Change> changes) {

    public record Change(UUID itemId, String change, String action, Instant at, Item item) {}

    public record Item(UUID id, UUID parentId, String sku, String barcode, String name, String status, BigDecimal price, Instant updatedAt) {}
}
