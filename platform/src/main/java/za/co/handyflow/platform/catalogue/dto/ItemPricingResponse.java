package za.co.handyflow.platform.catalogue.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Everything the pricing screen of one item needs. Only sent to users who may see prices. */
public record ItemPricingResponse(UUID itemId, String itemName, String unit, BigDecimal minPrice, BigDecimal costPrice,
                                  BigDecimal vatRate, boolean vatExempt, boolean approvalRequired, List<ListPrice> lists) {

    /** One row per active price list. {@code price} is null where the list has no price for the item yet. */
    public record ListPrice(UUID priceListId, String code, String name, boolean defaultList, BigDecimal price,
                            BigDecimal priceInclVat, BigDecimal marginPercent, List<PriceChangeResponse> open) {}
}
