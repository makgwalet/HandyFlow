package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * SLA performance for one client over work orders CREATED in the period that carry an SLA. Cancelled and rejected
 * work is excluded. Response is met at first assignment; resolution is met at technician completion. "Open" means
 * not yet met or breached (on track, at risk or paused). Compliance % = met / (met + breached), null when nothing has concluded.
 */
public record FmSlaPerformanceResponse(
        UUID clientId, LocalDate from, LocalDate to, long workOrdersWithSla,
        long responseMet, long responseBreached, long responseOpen, BigDecimal responseCompliancePercent,
        long resolutionMet, long resolutionBreached, long resolutionOpen, BigDecimal resolutionCompliancePercent,
        List<PriorityBreakdown> byPriority
) {
    public record PriorityBreakdown(String priority, long total, long responseBreached, long resolutionBreached) {}
}