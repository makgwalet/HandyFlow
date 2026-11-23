package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SpeciesResponse(
        UUID id,
        String name,
        String category,
        String defaultUnitOfMeasure,
        String trackingMode,
        Integer gestationDays,
        BigDecimal maturityWeightKg,
        String status,
        Instant createdAt,
        /** Days after a birth to remind the farmer to fit the permanent tag; null for no reminder. */
        Integer tagReminderDays
) {
    /** Without the reminder setting (callers written before it existed). */
    public SpeciesResponse(UUID id, String name, String category, String defaultUnitOfMeasure, String trackingMode,
                           Integer gestationDays, BigDecimal maturityWeightKg, String status, Instant createdAt) {
        this(id, name, category, defaultUnitOfMeasure, trackingMode, gestationDays, maturityWeightKg, status, createdAt, null);
    }
}
