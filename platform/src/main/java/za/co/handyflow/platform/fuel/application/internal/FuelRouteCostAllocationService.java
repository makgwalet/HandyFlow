package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelCustomerSite;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelRoute;
import za.co.handyflow.platform.fuel.domain.model.FuelRouteCostEntry;
import za.co.handyflow.platform.fuel.domain.repository.FuelCustomerSiteRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelRouteCostEntryRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelRouteRepository;
import za.co.handyflow.platform.fuel.dto.RouteCostAllocationDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Phase 8a: spreads the actual cost the office recorded against each route across the customers on that route.
 * <ul>
 *   <li>Only delivered stops share the cost; cancelled and reversed stops do not.</li>
 *   <li>The method is the caller's choice per report (litres, equal per stop, or site distance). Nothing is stored and no rate is built in.</li>
 *   <li>A route's shares add up to its actual total to the cent: the rounding difference goes to the largest share.</li>
 *   <li>If the chosen method gives every stop zero weight (no litres recorded, or no distances), that route is split equally per stop and counted in fallbackRoutes.</li>
 *   <li>A route with no recorded actuals is skipped and counted, never filled with an estimate.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FuelRouteCostAllocationService {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelRouteRepository routes;
    private final FuelRouteCostEntryRepository entries;
    private final FuelDeliveryRepository deliveries;
    private final FuelCustomerSiteRepository sites;
    private final CrmFacade crm;

    @Transactional(readOnly = true)
    public AllocationReport report(TenantId tenantId, LocalDate from, LocalDate to, Method method) {
        Method m = method == null ? Method.LITRES : method;
        LocalDate end = to == null ? LocalDate.now(SAST) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) throw new IllegalArgumentException("The start date is after the end date.");
        if (start.plusDays(366).isBefore(end)) throw new IllegalArgumentException("Pick a period of one year or less.");

        List<FuelRoute> inRange = new ArrayList<>(routes.findBetween(tenantId, start, end));
        inRange.removeIf(r -> !r.isOpen());
        if (inRange.isEmpty()) return new AllocationReport(start, end, m, 0, 0, 0, BigDecimal.ZERO, List.of());

        List<UUID> ids = inRange.stream().map(FuelRoute::getId).toList();
        Map<UUID, List<FuelDelivery>> stopsByRoute = new HashMap<>();
        for (FuelDelivery d : deliveries.findByRoutes(tenantId, ids)) stopsByRoute.computeIfAbsent(d.getRouteId(), k -> new ArrayList<>()).add(d);
        Map<UUID, BigDecimal> actualByRoute = new HashMap<>();
        for (FuelRouteCostEntry e : entries.findActiveByRoutes(tenantId, ids)) actualByRoute.merge(e.getRouteId(), e.getAmount(), BigDecimal::add);

        Map<UUID, BigDecimal> distance = new HashMap<>();
        if (m == Method.DISTANCE) {
            Set<UUID> siteIds = new HashSet<>();
            stopsByRoute.values().forEach(l -> l.forEach(d -> { if (d.getSiteId() != null) siteIds.add(d.getSiteId()); }));
            for (FuelCustomerSite site : sites.findAllById(siteIds)) {
                if (site.getDistanceKm() != null && tenantId.equals(site.getTenantId())) distance.put(site.getId(), site.getDistanceKm());
            }
        }

        Map<UUID, Acc> byCustomer = new LinkedHashMap<>();
        int allocated = 0, skipped = 0, fallback = 0;
        BigDecimal allocatedTotal = BigDecimal.ZERO;
        for (FuelRoute r : inRange) {
            List<FuelDelivery> done = stopsByRoute.getOrDefault(r.getId(), List.of()).stream().filter(d -> "DELIVERED".equals(d.getStatus())).toList();
            if (done.isEmpty()) continue;
            BigDecimal total = actualByRoute.get(r.getId());
            if (total == null || total.signum() <= 0) { skipped++; continue; }

            List<BigDecimal> weights = new ArrayList<>();
            for (FuelDelivery d : done) weights.add(weight(m, d, distance));
            boolean usable = weights.stream().anyMatch(w -> w.signum() > 0);
            if (!usable) { weights = done.stream().map(d -> BigDecimal.ONE).toList(); fallback++; }
            List<BigDecimal> shares = split(total, weights);

            Set<UUID> countedOnRoute = new HashSet<>();
            for (int i = 0; i < done.size(); i++) {
                FuelDelivery d = done.get(i);
                Acc a = byCustomer.computeIfAbsent(d.getCustomerId(), k -> new Acc());
                a.stops++;
                if (d.getLitresDelivered() != null) a.litres = a.litres.add(d.getLitresDelivered());
                a.allocated = a.allocated.add(shares.get(i));
                if (countedOnRoute.add(d.getCustomerId())) a.routes++;
            }
            allocated++;
            allocatedTotal = allocatedTotal.add(total);
        }

        Map<UUID, String> names = new HashMap<>();
        List<CustomerShare> out = new ArrayList<>();
        byCustomer.forEach((customerId, a) -> {
            String name = customerId == null ? "(no customer)"
                    : names.computeIfAbsent(customerId, id -> crm.findCustomerById(tenantId, id).map(CustomerSummary::name).orElse("(customer removed)"));
            BigDecimal perLitre = a.litres.signum() > 0 ? a.allocated.divide(a.litres, 4, RoundingMode.HALF_UP) : null;
            out.add(new CustomerShare(customerId, name, a.routes, a.stops, a.litres.setScale(2, RoundingMode.HALF_UP), a.allocated.setScale(2, RoundingMode.HALF_UP), perLitre));
        });
        out.sort(Comparator.comparing(CustomerShare::allocated).reversed().thenComparing(CustomerShare::customerName));
        return new AllocationReport(start, end, m, allocated, skipped, fallback, allocatedTotal.setScale(2, RoundingMode.HALF_UP), out);
    }

    static BigDecimal weight(Method m, FuelDelivery d, Map<UUID, BigDecimal> distance) {
        return switch (m) {
            case STOPS -> BigDecimal.ONE;
            case DISTANCE -> d.getSiteId() == null ? BigDecimal.ZERO : distance.getOrDefault(d.getSiteId(), BigDecimal.ZERO);
            case LITRES -> d.getLitresDelivered() == null ? BigDecimal.ZERO : d.getLitresDelivered();
        };
    }

    /** Splits total in proportion to weights, in cents, so the shares add up to the total exactly (the largest share takes the rounding difference). */
    static List<BigDecimal> split(BigDecimal total, List<BigDecimal> weights) {
        BigDecimal sum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> shares = new ArrayList<>();
        BigDecimal given = BigDecimal.ZERO;
        int largest = 0;
        for (int i = 0; i < weights.size(); i++) {
            BigDecimal s = total.multiply(weights.get(i)).divide(sum, 2, RoundingMode.HALF_UP);
            shares.add(s);
            given = given.add(s);
            if (weights.get(i).compareTo(weights.get(largest)) > 0) largest = i;
        }
        BigDecimal diff = total.setScale(2, RoundingMode.HALF_UP).subtract(given);
        if (diff.signum() != 0) shares.set(largest, shares.get(largest).add(diff));
        return shares;
    }

    private static final class Acc {
        int routes, stops;
        BigDecimal litres = BigDecimal.ZERO, allocated = BigDecimal.ZERO;
    }
}
