package za.co.handyflow.platform.catalogue.dto;

import java.math.BigDecimal;
import java.util.List;

/** One variant to create: one value per option, in option order. A missing price takes the parent's. */
public record VariantRowRequest(List<String> values, String sku, String barcode, BigDecimal defaultPrice, BigDecimal costPrice) {}
