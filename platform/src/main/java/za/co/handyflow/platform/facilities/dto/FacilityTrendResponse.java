package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Work over time. {@code raised} counts work orders created in the bucket; the other figures describe work completed
 * in the bucket (completion time is created→completed). Empty buckets are included so a chart has no gaps.
 */
public record FacilityTrendResponse(LocalDate from, LocalDate to, String bucket, List<Point> points) {

    public record Point(LocalDate start, LocalDate end, long raised, long completed, BigDecimal avgCompletionHours,
                        long reworked, long downtimeMinutes, BigDecimal cost) {}
}
