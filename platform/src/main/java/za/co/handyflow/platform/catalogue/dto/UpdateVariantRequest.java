package za.co.handyflow.platform.catalogue.dto;

import java.math.BigDecimal;

/** What can be edited on one variant. A price change follows the same approval rule as any other item. */
public record UpdateVariantRequest(String sku, String barcode, BigDecimal defaultPrice, BigDecimal costPrice, Long version) {}
