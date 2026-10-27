package za.co.handyflow.platform.facilities.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * External contractor performance over work orders created in the period and assigned to a vendor. Completion time is
 * created→completed for completed jobs; rework rate = completed jobs sent back at least once ÷ completed jobs; cost is
 * the recorded work-order cost of completed jobs. In-house work has no SLA, so there is no compliance figure.
 */
public record FacilityVendorPerformanceResponse(LocalDate from, LocalDate to, List<Row> vendors) {

    public record Row(UUID vendorId, String vendorName, long jobs, long completed, long cancelled, long open,
                      BigDecimal avgCompletionHours, long reworkJobs, BigDecimal reworkRatePercent,
                      BigDecimal totalCost, BigDecimal avgCost) {}
}
