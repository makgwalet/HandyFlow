package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioDashboardResponse(
        int totalClients,
        int activeClients,
        int overdueFilings,
        int pendingFilingsNext30Days,
        int deadlinesThisMonth,
        BigDecimal totalWip,
        BigDecimal totalOutstandingInvoices,
        List<TaxDeadlineResponse> urgentDeadlines,   // next 7 days
        List<FeeNoteResponse> outstandingInvoices,
        int highRiskClients,
        int ficaIncompleteClients
) {
}
