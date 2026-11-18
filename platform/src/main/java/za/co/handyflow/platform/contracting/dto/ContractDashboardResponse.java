package za.co.handyflow.platform.contracting.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Everything the Contracting dashboard shows, calculated on the server so the browser does not load every contract. */
public record ContractDashboardResponse(
        Map<String, Long> statusCounts,           // by status, all contracts
        Map<String, Long> typeCounts,             // by contract type, all contracts
        long totalContracts,
        BigDecimal activeValue,                   // total value of SIGNED contracts
        BigDecimal pendingValue,                  // total value of contracts SENT for signing
        List<ContractSummaryResponse> expiringSoon,        // SIGNED, ending within 30 days, soonest first (max 20)
        List<ContractSummaryResponse> awaitingSignature    // SENT, newest first (max 10)
) {}
