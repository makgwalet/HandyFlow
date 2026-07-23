package za.co.handyflow.platform.accounting.dto;

import java.util.List;

public record ImportBankTransactionsResponse(
        int imported,
        int skippedDuplicates,
        int failed,
        List<String> errors,
        java.math.BigDecimal newBalance
) {}