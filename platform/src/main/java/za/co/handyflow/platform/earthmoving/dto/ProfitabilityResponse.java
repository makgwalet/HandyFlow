package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Hire revenue against maintenance cost per machine. Amounts are in rand and exclude VAT. {@code revenue} is hire charges not
 * voided, {@code invoiced} the part already on an invoice. {@code marginPercent} is null when there was no revenue.
 */
public record ProfitabilityResponse(LocalDate from, LocalDate to, Row totals, List<Row> machines) {
    public record Row(UUID assetId, String machine, int charges, BigDecimal revenue, BigDecimal invoiced, BigDecimal notInvoiced,
                      BigDecimal maintenanceCost, BigDecimal margin, BigDecimal marginPercent) {}
}
