package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Worst-performing assets over the period, from reactive (non-PPM, non-inspection) work orders that were not cancelled.
 * MTBF = (period hours − recorded downtime) ÷ failures; MTTR = average created→completed of completed failures; a
 * repeat failure is a failure within 30 days of the previous one on the same asset. Only assets with at least one
 * failure are listed, worst first. A period running into the future is measured only up to now.
 */
public record FacilityAssetReliabilityResponse(LocalDate from, LocalDate to, int limit, List<Row> assets) {

    public record Row(UUID assetId, String assetTag, String assetName, UUID siteId, long failures, long repeatFailures,
                      long downtimeMinutes, BigDecimal mttrHours, BigDecimal mtbfHours, String topFailureCause,
                      BigDecimal totalCost) {}
}
