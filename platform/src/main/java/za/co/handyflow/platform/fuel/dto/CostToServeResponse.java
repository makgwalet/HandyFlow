package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Margin less what it cost to get the fuel there: driver time, vehicle running cost, a fixed per-delivery cost and an overhead share.
 * Only deliveries that have a cost-at-sale are included in the money columns (the rest are counted in {@code deliveriesWithoutCost}).
 * The service costs are estimates from the rates in Fuel settings, the site distances staff entered and the loaded-to-delivered time.
 */
public record CostToServeResponse(LocalDate from, LocalDate to, boolean ratesConfigured, int deliveries, int deliveriesWithoutCost,
                                  int deliveriesWithoutDistance, int deliveriesWithEstimatedHours,
                                  BigDecimal revenue, BigDecimal margin, BigDecimal driverCost, BigDecimal vehicleCost,
                                  BigDecimal fixedCost, BigDecimal overheadCost, BigDecimal netMargin, List<Row> customers) {

    public record Row(UUID customerId, String customerName, int deliveries, BigDecimal litres, BigDecimal revenue, BigDecimal margin,
                      BigDecimal driverCost, BigDecimal vehicleCost, BigDecimal fixedCost, BigDecimal overheadCost,
                      BigDecimal netMargin, BigDecimal netPerLitre, int deliveriesWithoutDistance, int deliveriesWithEstimatedHours) {}
}
