package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;

public record UpdateSpeciesRequest(
        String name,
        String defaultUnitOfMeasure,
        String trackingMode,
        Integer gestationDays,
        BigDecimal maturityWeightKg
) {}
