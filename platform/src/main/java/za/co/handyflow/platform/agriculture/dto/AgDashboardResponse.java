package za.co.handyflow.platform.agriculture.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Tenant-wide Agriculture dashboard: every ACTIVE farm in one response, so the client does not have to call a
 * per-farm endpoint once per farm. Only facts this module actually records are included: there is no revenue,
 * labour-cost, equipment-cost or weather data in Agriculture, so none appears here.
 */
public record AgDashboardResponse(
        LocalDate asOf,
        Totals totals,
        List<FarmTypeCount> farmTypes,
        List<FarmSummary> farms,
        CropSummary crops,
        List<SpeciesCount> livestock,
        AttentionSummary attention
) {
    /** "In production" means crop cycles that are PLANTED, GROWING or HARVESTING. */
    public record Totals(
            int farmCount,
            BigDecimal totalHectares,        // sum of the farms that have a size recorded
            int farmsWithoutHectares,        // farms with no size recorded, so totalHectares is understated by these
            long cropCyclesInProduction,
            BigDecimal hectaresInProduction,
            long plannedCropCycles,
            long animalCount,                // ACTIVE individually tracked animals (sold, deceased and culled excluded)
            long groupCount,                 // ACTIVE groups
            long groupHead,                  // head count across ACTIVE groups
            long totalHead                   // animalCount + groupHead
    ) {}

    public record FarmTypeCount(String farmType, int farmCount, BigDecimal hectares) {}

    public record FarmSummary(
            UUID id, String name, String farmType, String province, String region,
            Double latitude, Double longitude,   // null when the farm has no GPS position
            BigDecimal totalHectares,
            BigDecimal hectaresInProduction,
            long cropCyclesInProduction,
            long animalCount,
            long groupHead,
            int attentionCount,
            int urgentCount                      // CRITICAL, OVERDUE or DUE_TODAY
    ) {}

    public record CropSummary(List<StatusCount> byStatus, List<CropArea> inProduction) {}

    public record StatusCount(String status, long cycles, BigDecimal hectares) {}

    public record CropArea(UUID cropTypeId, String cropName, long cycles, BigDecimal hectares) {}

    public record SpeciesCount(UUID speciesId, String name, String category, long animals, long groupHead, long totalHead) {}

    public record AttentionSummary(int total, List<SeverityCount> bySeverity, List<AttentionItemResponse> items) {}

    public record SeverityCount(String severity, int count) {}
}
