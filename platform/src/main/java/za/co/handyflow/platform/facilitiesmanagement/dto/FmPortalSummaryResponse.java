package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * A client's at-a-glance position. {@code workOrders} counts by the portal's own status names
 * (SUBMITTED, OPEN, ASSIGNED, IN_PROGRESS, ON_HOLD, COMPLETED, DECLINED, CANCELLED); {@code openWork} is everything
 * not finished. Money is what the client owes on invoices staff have sent.
 */
public record FmPortalSummaryResponse(
        Map<String, Long> workOrders, long openWork, long slaAtRisk, long slaBreached,
        BigDecimal outstandingBalance, long overdueInvoices, BigDecimal overdueBalance
) {}
