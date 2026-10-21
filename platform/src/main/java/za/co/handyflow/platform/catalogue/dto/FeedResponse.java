package za.co.handyflow.platform.catalogue.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What a channel is selling, in a form another system can read: active items published to the channel, priced from the
 * channel's price list. Cost and margin are never included. {@code pictureUrl} is a path on this API (it needs the same sign-in).
 */
public record FeedResponse(String channel, String kind, String priceList, int page, int size, long total, List<Entry> items) {

    public record Entry(UUID id, String sku, String barcode, String name, String description, String brand, String category, String unit,
                        BigDecimal vatRate, boolean vatExempt, BigDecimal price, BigDecimal priceInclVat, String pictureUrl, Instant updatedAt,
                        List<Variant> variants) {}

    public record Variant(UUID id, String sku, String barcode, String label, BigDecimal price, BigDecimal priceInclVat) {}
}
