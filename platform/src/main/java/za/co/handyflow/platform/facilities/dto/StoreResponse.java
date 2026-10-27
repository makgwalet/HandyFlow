package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** {@code itemLines}: how many different items the store holds; {@code stockValue}: their value at average cost. */
public record StoreResponse(
        UUID id, String code, String name, UUID siteId, boolean defaultStore, boolean active, long itemLines, BigDecimal stockValue
) {}
