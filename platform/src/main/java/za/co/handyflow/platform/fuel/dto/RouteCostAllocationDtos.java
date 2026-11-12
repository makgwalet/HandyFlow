package za.co.handyflow.platform.fuel.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Phase 8a: a route's recorded actual cost spread across the customers it delivered to. */
public final class RouteCostAllocationDtos {
    private RouteCostAllocationDtos() {}

    /** LITRES (default): by litres delivered. STOPS: equal per delivered stop. DISTANCE: by the site's distance in km. */
    public enum Method { LITRES, STOPS, DISTANCE }

    /**
     * One customer. allocated = their share of the actual cost of the routes they were on (all categories, tolls and other included).
     * costPerLitre is null when no litres were delivered to them.
     */
    public record CustomerShare(UUID customerId, String customerName, int routes, int stops, BigDecimal litres, BigDecimal allocated, BigDecimal costPerLitre) {}

    /**
     * routesAllocated = routes with recorded actual costs and at least one delivered stop. routesSkipped = delivered routes with no actual
     * costs yet (nothing to allocate). fallbackRoutes = routes where the chosen method had no usable weights, so stops were split equally.
     */
    public record AllocationReport(LocalDate from, LocalDate to, Method method, int routesAllocated, int routesSkipped, int fallbackRoutes,
                                   BigDecimal allocatedTotal, List<CustomerShare> customers) {}
}
