package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;

public record UpdateSpeciesRequest(
        String name,
        String defaultUnitOfMeasure,
        String trackingMode,
        Integer gestationDays,
        BigDecimal maturityWeightKg,
        /** Days before the permanent-tag reminder; empty clears it. Like gestationDays, it is replaced on every update. */
        Integer tagReminderDays
) {
    /** Without the reminder setting (callers written before it existed): like gestationDays, an update without it clears it. */
    public UpdateSpeciesRequest(String name, String defaultUnitOfMeasure, String trackingMode,
                                Integer gestationDays, BigDecimal maturityWeightKg) {
        this(name, defaultUnitOfMeasure, trackingMode, gestationDays, maturityWeightKg, null);
    }
}
