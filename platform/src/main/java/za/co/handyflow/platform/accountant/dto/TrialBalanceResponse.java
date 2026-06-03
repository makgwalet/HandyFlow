package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TrialBalanceResponse(
        UUID clientId,
        int periodYear,
        int periodMonth,
        List<TrialBalanceLine> lines,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        boolean balanced
) {
}
