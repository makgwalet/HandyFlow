package za.co.handyflow.platform.catalogue.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record VariantRowResponse(UUID id, String name, String label, List<String> values, String sku, String barcode, String status,
                                 BigDecimal defaultPrice, BigDecimal costPrice, Long version) {}
