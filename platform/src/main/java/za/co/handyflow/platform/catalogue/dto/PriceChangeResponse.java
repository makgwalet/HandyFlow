package za.co.handyflow.platform.catalogue.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PriceChangeResponse(UUID id, UUID itemId, String itemName, UUID priceListId, String priceListName,
                                  BigDecimal oldPrice, BigDecimal newPrice, BigDecimal changePercent, LocalDate effectiveFrom,
                                  String reason, String status, boolean proposedByMe, Instant proposedAt, Instant decidedAt,
                                  String decisionNote, Instant appliedAt) {}
