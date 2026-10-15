package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Hire revenue per customer for a period, in rand excluding VAT. {@code sharePercent} is the customer's part of all hire revenue. */
public record CustomerRevenueResponse(LocalDate from, LocalDate to, Row totals, List<Row> customers) {
    public record Row(UUID customerId, String customer, int charges, BigDecimal revenue, BigDecimal invoiced, BigDecimal notInvoiced, BigDecimal sharePercent) {}
}
