package za.co.handyflow.platform.supplychain.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SupplierInvoiceLineResponse(
        UUID id,
        int lineNumber,
        UUID poLineId,
        String itemName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal vatRate,
        BigDecimal lineTotal,
        BigDecimal vatAmount
) {}
