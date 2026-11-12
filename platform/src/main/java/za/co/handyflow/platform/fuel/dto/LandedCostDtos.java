package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Phase 6c: what the fuel really cost to put on the customer's tank, and what was left. */
public final class LandedCostDtos {
    private LandedCostDtos() {}

    /**
     * One route. productCost is the litres delivered times the cost at sale recorded on each delivery (the tank's weighted-average
     * purchase cost at that moment). transportCost is everything recorded against the route (vehicle, driver, tolls, other).
     * landedCost = productCost + transportCost. A route is complete only when every delivered stop has a cost at sale, it delivered
     * litres, and a vehicle or driver cost has been recorded; only complete routes feed the per-litre figures and the rollups.
     */
    public record RouteRow(UUID routeId, LocalDate routeDate, String label, String tankerRegistration, int deliveredStops, BigDecimal litres,
                           BigDecimal revenue, BigDecimal productCost, BigDecimal transportCost, BigDecimal landedCost,
                           BigDecimal productPerLitre, BigDecimal transportPerLitre, BigDecimal landedPerLitre, BigDecimal revenuePerLitre,
                           BigDecimal marginPerLitre, BigDecimal margin, boolean complete, List<String> flags) {}

    public record Rollup(String key, String label, int routes, BigDecimal litres, BigDecimal revenue, BigDecimal productCost, BigDecimal transportCost,
                         BigDecimal landedCost, BigDecimal landedPerLitre, BigDecimal marginPerLitre, BigDecimal margin) {}

    public record Report(LocalDate from, LocalDate to, int routesWithDeliveries, int routesLeftOut, List<RouteRow> routes, Rollup total,
                         List<Rollup> byTanker, List<Rollup> byDepot) {}
}
