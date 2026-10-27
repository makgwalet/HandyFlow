package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Subcontractor performance over work orders created in the period and assigned to a vendor (cancelled jobs
 * counted separately). Resolution time is created→completed for completed jobs. Rework rate = completed jobs sent
 * back at least once ÷ completed jobs. SLA compliance is met ÷ (met + breached) of the resolution target.
 * {@code totalCost}/{@code avgCost} are internal cost — null unless the caller holds a cost/billing authority.
 */
public record FmVendorPerformanceResponse(LocalDate from, LocalDate to, List<Row> vendors) {

    public record Row(UUID vendorId, String vendorName, long jobs, long completed, long cancelled, long open,
                      BigDecimal avgResolutionHours, long reworkJobs, BigDecimal reworkRatePercent,
                      long slaMet, long slaBreached, BigDecimal slaCompliancePercent,
                      BigDecimal totalCost, BigDecimal avgCost) {}
}
