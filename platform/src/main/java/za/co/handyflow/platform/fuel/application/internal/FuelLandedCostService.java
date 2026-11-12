package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelRoute;
import za.co.handyflow.platform.fuel.domain.model.FuelRouteCostEntry;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.LandedCostDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Phase 6c, extending F02: the landed cost of the fuel per litre, and the margin left, by route, tanker and depot.
 * Landed cost = what the fuel cost to buy (cost at sale on each delivery) + what it actually cost to carry (the costs recorded
 * against the route in F02). Nothing is estimated: a route with no recorded transport cost, or a delivery with no cost at sale, is
 * shown and flagged but left out of the per-litre figures and rollups, so a floor is never shown as the true cost.
 * By depot, a route's figures are shared across depots by litres delivered from each depot's tanks.
 */
@Service
@RequiredArgsConstructor
public class FuelLandedCostService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelRouteRepository routes;
    private final FuelRouteCostEntryRepository entries;
    private final FuelDeliveryRepository deliveries;
    private final FuelTankerRepository tankers;
    private final FuelTankRepository tanks;
    private final FuelDepotRepository depots;

    @Transactional(readOnly = true)
    public Report report(TenantId tenantId, LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(SAST) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) throw new IllegalArgumentException("The start date is after the end date.");
        if (start.plusDays(366).isBefore(end)) throw new IllegalArgumentException("Pick a period of one year or less.");

        List<FuelRoute> inRange = new ArrayList<>(routes.findBetween(tenantId, start, end));
        inRange.removeIf(r -> !r.isOpen());
        if (inRange.isEmpty()) return new Report(start, end, 0, 0, List.of(), zero("Whole fleet", "All"), List.of(), List.of());

        List<UUID> ids = inRange.stream().map(FuelRoute::getId).toList();
        Map<UUID, List<FuelDelivery>> stopsByRoute = new HashMap<>();
        for (FuelDelivery d : deliveries.findByRoutes(tenantId, ids)) stopsByRoute.computeIfAbsent(d.getRouteId(), k -> new ArrayList<>()).add(d);
        Map<UUID, List<FuelRouteCostEntry>> actualsByRoute = new HashMap<>();
        for (FuelRouteCostEntry e : entries.findActiveByRoutes(tenantId, ids)) actualsByRoute.computeIfAbsent(e.getRouteId(), k -> new ArrayList<>()).add(e);

        Map<UUID, String> tankerNames = new HashMap<>();
        tankers.findAllActive(tenantId).forEach(t -> tankerNames.put(t.getId(), t.getRegistration()));
        Map<UUID, UUID> tankDepot = new HashMap<>();
        tanks.findAllActive(tenantId).forEach(t -> { if (t.getDepotId() != null) tankDepot.put(t.getId(), t.getDepotId()); });
        Map<UUID, String> depotNames = new HashMap<>();
        depots.findAllActive(tenantId).forEach(d -> depotNames.put(d.getId(), d.getName()));

        List<Calc> calcs = new ArrayList<>();
        for (FuelRoute r : inRange) {
            List<FuelDelivery> stops = stopsByRoute.getOrDefault(r.getId(), List.of());
            if (stops.stream().noneMatch(d -> "DELIVERED".equals(d.getStatus()))) continue;
            calcs.add(calc(r, stops, actualsByRoute.getOrDefault(r.getId(), List.of())));
        }

        List<RouteRow> rows = new ArrayList<>();
        for (Calc c : calcs) rows.add(c.row(tankerNames.get(c.route.getTankerId())));
        List<Calc> complete = calcs.stream().filter(c -> c.complete).toList();

        Acc all = new Acc();
        for (Calc c : complete) all.add(c, BigDecimal.ONE, c.litres);
        return new Report(start, end, calcs.size(), calcs.size() - complete.size(), rows, all.toRollup("all", "Whole fleet", complete.size()), byTanker(complete, tankerNames), byDepot(complete, tankDepot, depotNames));
    }

    // ── calculation ──────────────────────────────────────────────────────────

    static Calc calc(FuelRoute r, List<FuelDelivery> stops, List<FuelRouteCostEntry> actuals) {
        Calc c = new Calc(r);
        boolean missingProductCost = false;
        int live = 0;
        for (FuelDelivery d : stops) {
            if ("CANCELLED".equals(d.getStatus()) || "REVERSED".equals(d.getStatus())) continue;
            live++;
            if (!"DELIVERED".equals(d.getStatus()) || d.getLitresDelivered() == null) continue;
            BigDecimal litres = d.getLitresDelivered();
            c.delivered++; c.litres = c.litres.add(litres);
            c.revenue = c.revenue.add(d.getTotalAmount() != null ? d.getTotalAmount() : d.getPricePerLitre() == null ? BigDecimal.ZERO : litres.multiply(d.getPricePerLitre()));
            if (d.getCostPerLitreAtSale() == null) missingProductCost = true;
            else c.product = c.product.add(litres.multiply(d.getCostPerLitreAtSale()));
            if (d.getTankId() != null) c.litresByTank.merge(d.getTankId(), litres, BigDecimal::add);
        }
        boolean vehicleOrDriver = false;
        for (FuelRouteCostEntry e : actuals) {
            c.transport = c.transport.add(e.getAmount());
            if ("VEHICLE".equals(e.getCategory()) || "DRIVER".equals(e.getCategory())) vehicleOrDriver = true;
        }
        if (missingProductCost) c.flags.add("NO_PRODUCT_COST");
        if (!vehicleOrDriver) c.flags.add("NO_TRANSPORT_COST");
        if (c.delivered < live) c.flags.add("PARTIAL_DELIVERY");
        if (c.litres.signum() <= 0) c.flags.add("NO_LITRES");
        // A partly delivered route is still shown, but the carry cost covers the whole trip, so its per-litre figure would be too high.
        c.complete = !missingProductCost && vehicleOrDriver && c.litres.signum() > 0 && c.delivered == live;
        return c;
    }

    private static List<Rollup> byTanker(List<Calc> complete, Map<UUID, String> names) {
        Map<UUID, List<Calc>> groups = new LinkedHashMap<>();
        for (Calc c : complete) groups.computeIfAbsent(c.route.getTankerId(), k -> new ArrayList<>()).add(c);
        List<Rollup> out = new ArrayList<>();
        groups.forEach((tankerId, list) -> {
            Acc a = new Acc();
            for (Calc c : list) a.add(c, BigDecimal.ONE, c.litres);
            out.add(a.toRollup(String.valueOf(tankerId), names.getOrDefault(tankerId, "(tanker removed)"), list.size()));
        });
        out.sort(Comparator.comparing(Rollup::label));
        return out;
    }

    private static List<Rollup> byDepot(List<Calc> complete, Map<UUID, UUID> tankDepot, Map<UUID, String> names) {
        Map<String, Acc> accs = new LinkedHashMap<>();
        Map<String, Set<UUID>> routeSets = new HashMap<>();
        for (Calc c : complete) {
            Map<String, BigDecimal> share = new LinkedHashMap<>();
            for (Map.Entry<UUID, BigDecimal> e : c.litresByTank.entrySet()) share.merge(tankDepot.containsKey(e.getKey()) ? tankDepot.get(e.getKey()).toString() : "", e.getValue(), BigDecimal::add);
            BigDecimal total = share.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.signum() <= 0) continue;
            for (Map.Entry<String, BigDecimal> e : share.entrySet()) {
                accs.computeIfAbsent(e.getKey(), k -> new Acc()).add(c, e.getValue().divide(total, 6, RoundingMode.HALF_UP), e.getValue());
                routeSets.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(c.route.getId());
            }
        }
        List<Rollup> out = new ArrayList<>();
        accs.forEach((key, a) -> out.add(a.toRollup(key, key.isEmpty() ? "No depot" : names.getOrDefault(UUID.fromString(key), "(depot removed)"), routeSets.get(key).size())));
        out.sort(Comparator.comparing(Rollup::label));
        return out;
    }

    private static Rollup zero(String key, String label) {
        BigDecimal z = BigDecimal.ZERO.setScale(2);
        return new Rollup(key, label, 0, z, z, z, z, z, null, null, z);
    }

    private static BigDecimal m(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal per(BigDecimal v, BigDecimal litres) { return litres.signum() <= 0 ? null : v.divide(litres, 4, RoundingMode.HALF_UP); }

    static final class Calc {
        final FuelRoute route;
        int delivered;
        BigDecimal litres = BigDecimal.ZERO, revenue = BigDecimal.ZERO, product = BigDecimal.ZERO, transport = BigDecimal.ZERO;
        final Map<UUID, BigDecimal> litresByTank = new LinkedHashMap<>();
        boolean complete;
        final List<String> flags = new ArrayList<>();

        Calc(FuelRoute r) { this.route = r; }

        RouteRow row(String tanker) {
            BigDecimal landed = product.add(transport);
            // Per-litre figures only for a complete route; a partial figure would pass for the real cost.
            return new RouteRow(route.getId(), route.getRouteDate(), route.getLabel(), tanker, delivered, m(litres), m(revenue), complete ? m(product) : null,
                    m(transport), complete ? m(landed) : null,
                    complete ? per(product, litres) : null, complete ? per(transport, litres) : null, complete ? per(landed, litres) : null, per(revenue, litres),
                    complete ? per(revenue.subtract(landed), litres) : null, complete ? m(revenue.subtract(landed)) : null, complete, List.copyOf(flags));
        }
    }

    private static final class Acc {
        BigDecimal litres = BigDecimal.ZERO, revenue = BigDecimal.ZERO, product = BigDecimal.ZERO, transport = BigDecimal.ZERO;

        void add(Calc c, BigDecimal fraction, BigDecimal litresShare) {
            litres = litres.add(litresShare);
            revenue = revenue.add(c.revenue.multiply(fraction)); product = product.add(c.product.multiply(fraction)); transport = transport.add(c.transport.multiply(fraction));
        }

        Rollup toRollup(String key, String label, int routes) {
            BigDecimal landed = product.add(transport);
            return new Rollup(key, label, routes, m(litres), m(revenue), m(product), m(transport), m(landed), per(landed, litres), per(revenue.subtract(landed), litres), m(revenue.subtract(landed)));
        }
    }
}
