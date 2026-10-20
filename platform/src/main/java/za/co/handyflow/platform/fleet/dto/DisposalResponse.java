package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DisposalResponse(
        String method,
        String counterpartyName,
        String counterpartyContact,
        LocalDate disposalDate,
        BigDecimal salePrice,
        Integer odometerAtDisposal,
        BigDecimal bookValueAtDisposal,
        BigDecimal gainLoss,               // sale price − book value; positive = gain
        boolean financeCleared,
        BigDecimal financeSettlementAmount,
        boolean registrationTransferred,
        LocalDate registrationTransferDate,
        String accountingReference,
        String notes
) {}