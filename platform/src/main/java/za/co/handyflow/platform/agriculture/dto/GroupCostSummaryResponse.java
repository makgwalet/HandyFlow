package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cost rollup for one batch/flock/herd. No acquisition-cost figure —
 * unlike {@link AnimalCostSummaryResponse}, {@code AgGroup} has no
 * acquisition-cost field of its own (Increment 1 never captured what a
 * batch cost to buy in, only how many head), so this total reflects
 * ongoing costs (feed + health) only. Flagged explicitly rather than
 * silently reported as a complete figure — see
 * {@code AgCostReportingService}'s own Javadoc.
 */
public record GroupCostSummaryResponse(
        UUID groupId,
        String batchNumber,
        UUID farmId,
        BigDecimal totalHealthCost,
        BigDecimal totalFeedCost,
        BigDecimal totalCost, // ongoing costs only — see class Javadoc
        int currentCount,
        BigDecimal costPerHead, // null if currentCount is 0
        BigDecimal averageWeightKg,
        BigDecimal costPerKgLiveweight // null if averageWeightKg or currentCount is unknown/zero
) {}
