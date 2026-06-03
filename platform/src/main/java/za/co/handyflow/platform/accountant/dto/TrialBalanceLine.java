package za.co.handyflow.platform.accountant.dto;

import java.math.BigDecimal;

public record TrialBalanceLine(
        String accountCode,
        String accountName,
        String accountType,
        BigDecimal openingBalance,
        BigDecimal periodDebits,
        BigDecimal periodCredits,
        BigDecimal closingBalance
) {
}
