package za.co.handyflow.platform.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Vat201Response(
        LocalDate from,
        LocalDate to,
        int invoiceCount,
        BigDecimal totalSales,       // Box 1 equivalent — gross output
        BigDecimal outputVat,        // Box 4 — VAT on sales
        BigDecimal inputVat,         // Box 15 — VAT on purchases (claimable)
        BigDecimal netVatPayable     // Box 17 — output - input
) {}