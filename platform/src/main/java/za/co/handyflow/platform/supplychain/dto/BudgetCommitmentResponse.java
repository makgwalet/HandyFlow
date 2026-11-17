package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetCommitmentResponse(
        UUID purchaseOrderId,
        String orderNumber,
        String supplierName,
        String poStatus,
        BigDecimal committed,
        BigDecimal actual,
        BigDecimal used,
        LocalDate spendDate
) {}
