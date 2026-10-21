package za.co.handyflow.platform.catalogue.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** valueCount is how many items hold a value for it; null where it was not worked out. */
public record CatalogueAttributeResponse(UUID id, UUID categoryId, String categoryPath, String code, String label, String type,
                                         boolean required, List<String> options, BigDecimal min, BigDecimal max,
                                         Integer maxLength, int sortOrder, boolean active, Long valueCount) {}
