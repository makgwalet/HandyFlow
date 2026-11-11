package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Delivery margin by customer: revenue less the fuel's cost at the time of sale. This is NOT full cost-to-serve:
 * driver time, vehicle running costs and overheads are not captured here, so real profit is lower.
 */
public record MarginResponse(LocalDate from, LocalDate to, BigDecimal revenue, BigDecimal margin, int deliveriesWithoutCost, List<Row> customers) {
    public record Row(UUID customerId, String customerName, int deliveries, BigDecimal litres, BigDecimal revenue, BigDecimal margin, BigDecimal marginPerLitre) {}
}
