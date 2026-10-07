package za.co.handyflow.platform.compliancetender.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TenderRateResponse(
        UUID id, String category, String itemRef, String description, String unit, BigDecimal unitCost, String supplier, String notes,
        boolean active, BigDecimal previousUnitCost, Instant priceChangedAt, String source, Instant updatedAt
) {}
