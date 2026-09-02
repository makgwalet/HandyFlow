package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cost rollup for one individually-tracked animal. All-time totals, not
 * date-ranged — same scope decision as {@code VehicleCostSummaryResponse},
 * for the same reason: most useful as a comparison between animals (which
 * one is expensive to keep) rather than a period metric.
 */
public record AnimalCostSummaryResponse(
        UUID animalId,
        String tagNumber,
        UUID farmId,
        BigDecimal acquisitionCost,
        BigDecimal totalHealthCost,
        BigDecimal totalFeedCost,
        BigDecimal totalCost,
        BigDecimal currentWeightKg,
        BigDecimal costPerKgLiveweight // null if currentWeightKg is unknown or zero — "no data yet", not "free"
) {}
