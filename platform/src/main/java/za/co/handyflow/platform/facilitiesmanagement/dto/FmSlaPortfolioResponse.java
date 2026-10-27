package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * SLA compliance across every client the caller may see, over work orders created in the period that carry an SLA
 * (cancelled and rejected excluded). Same definitions as the per-client report. Worst response-or-resolution
 * compliance first; clients with nothing concluded sort last.
 */
public record FmSlaPortfolioResponse(LocalDate from, LocalDate to, long workOrdersWithSla,
                                     BigDecimal responseCompliancePercent, BigDecimal resolutionCompliancePercent,
                                     List<ClientRow> clients) {

    public record ClientRow(UUID clientId, String clientName, long workOrdersWithSla,
                            long responseMet, long responseBreached, BigDecimal responseCompliancePercent,
                            long resolutionMet, long resolutionBreached, BigDecimal resolutionCompliancePercent) {}
}
