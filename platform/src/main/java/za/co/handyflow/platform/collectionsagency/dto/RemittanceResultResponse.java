package za.co.handyflow.platform.collectionsagency.dto;

import java.math.BigDecimal;

public record RemittanceResultResponse(
        TrustTransactionResponse transaction, CommissionInvoiceResponse invoice, BigDecimal netPaidToClient,
        BigDecimal commissionRetained
) {}
