package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.RouteCostDtos.*;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * F02: actual versus estimated cost of routes, by route, tanker and depot.
 * <ul>
 *   <li>Estimate (from the tenant's own settings, never a built-in rate): vehicle = round trip to the farthest stop x cost per km;
 *       driver = typical trip hours x cost per hour. A route whose rates or distances are missing gets no estimate for that part and says so.</li>
 *   <li>Actual: costs the office records against the route (vehicle, driver, tolls, other). Entries are append-only; a wrong one is voided.</li>
 *   <li>Variance compares only vehicle and driver, and only where both an estimate and an actual exist. Tolls and other costs are shown, not compared.</li>
 *   <li>By depot, a route's figures are shared across depots by litres delivered from each depot's tanks.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelRouteCostService {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final FuelRouteRepository routes;
    private final FuelRouteCostEntryRepository entries;
    private final FuelDeliveryRepository deliveries;
    private final FuelCustomerSiteRepository sites;
    private final FuelTankerRepository tankers;
    private final FuelTankRepository tanks;
    private final FuelDepotRepository depots;
    private final FuelSettingsService settingsService;
    private final HrFacade hr;

    @Transactional(readOnly = true)
    public List<CostEntryView> list(TenantId tenantId, UUID routeId) {
        routes.findOne(tenantId, routeId).orElseThrow(() -> new ResourceNotFoundException("Route", routeId.toString()));
        return entries.findByRoute(tenantId, routeId).stream().map(FuelRouteCostService::view).toList();
    }

    @Transactional
    public CostEntryView add(TenantId tenantId, UUID routeId, CostEntryRequest req, UUID by) {
        FuelRoute r = routes.findOne(tenantId, routeId).orElseThrow(() -> new ResourceNotFoundException("Route", routeId.toString()));
        if (!r.isOpen()) throw new IllegalStateException("A cancelled route has no costs.");
        return view(entries.save(FuelRouteCostEntry.create(tenantId, routeId, req.category(), req.amount(), req.quantity(), req.unit(), req.note(), by)));
    }

    @Transactional
    public CostEntryView voidEntry(TenantId tenantId, UUID entryId, String reason, UUID by) {
        FuelRouteCostEntry e = entries.findByIdForUpdate(tenantId, entryId).orElseThrow(() -> new ResourceNotFoundException("Cost entry", entryId.toString()));
        e.voidEntry(reason, by);
        return view(entries.save(e));
    }

    @Transactional(readOnly = true)
    public RouteCostReport report(TenantId tenantId, LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(SAST) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) throw new IllegalArgumentException("The start date is after the end date.");
        if (start.plusDays(366).isBefore(end)) throw new IllegalArgumentException("Pick a period of one year or less.");
        FuelSettings s = settingsService.current(tenantId);
        boolean vehicleRate = s.getVehicleCostPerKm() != null && s.getVehicleCostPerKm().signum() > 0;
        boolean driverRate = s.getDriverCostPerHour() != null && s.getDriverCostPerHour().signum() > 0;

        List<FuelRoute> inRange = new ArrayList<>(routes.findBetween(tenantId, start, end));
        inRange.removeIf(r -> !r.isOpen());
        if (inRange.isEmpty()) return new RouteCostReport(start, end, vehicleRate || driverRate, 0, List.of(), List.of(), List.of());

        List<UUID> ids = inRange.stream().map(FuelRoute::getId).toList();
        Map<UUID, List<FuelDelivery>> stopsByRoute = new HashMap<>();
        for (FuelDelivery d : deliveries.findByRoutes(tenantId, ids)) stopsByRoute.computeIfAbsent(d.getRouteId(), k -> new ArrayList<>()).add(d);
        Map<UUID, List<FuelRouteCostEntry>> actualsByRoute = new HashMap<>();
        for (FuelRouteCostEntry e : entries.findActiveByRoutes(tenantId, ids)) actualsByRoute.computeIfAbsent(e.getRouteId(), k -> new ArrayList<>()).add(e);

        Set<UUID> siteIds = new HashSet<>();
        stopsByRoute.values().forEach(l -> l.forEach(d -> { if (d.getSiteId() != null) siteIds.add(d.getSiteId()); }));
        Map<UUID, BigDecimal> distance = new HashMap<>();
        for (FuelCustomerSite site : sites.findAllById(siteIds)) if (site.getDistanceKm() != null && tenantId.equals(site.getTenantId())) distance.put(site.getId(), site.getDistanceKm());

        Map<UUID, String> tankerNames = new HashMap<>();
        tankers.findAllActive(tenantId).forEach(t -> tankerNames.put(t.getId(), t.getRegistration()));
        Map<UUID, UUID> tankDepot = new HashMap<>();
        tanks.findAllActive(tenantId).forEach(t -> { if (t.getDepotId() != null) tankDepot.put(t.getId(), t.getDepotId()); });
        Map<UUID, String> depotNames = new HashMap<>();
        depots.findAllActive(tenantId).forEach(d -> depotNames.put(d.getId(), d.getName()));
        Map<UUID, String> driverNames = new HashMap<>();

        List<Calc> calcs = new ArrayList<>();
        for (FuelRoute r : inRange) {
            List<FuelDelivery> stops = stopsByRoute.getOrDefault(r.getId(), List.of());
            if (stops.stream().noneMatch(d -> "DELIVERED".equals(d.getStatus()))) continue; // nothing to cost yet
            calcs.add(calc(r, stops, actualsByRoute.getOrDefault(r.getId(), List.of()), distance, s, vehicleRate, driverRate));
        }

        List<RouteCostRow> rows = new ArrayList<>();
        for (Calc c : calcs) {
            String driver = driverNames.computeIfAbsent(c.route.getDriverEmployeeId(), id -> hr.findEmployeeById(tenantId, id).map(EmployeeSummary::fullName).orElse(null));
            rows.add(c.row(tankerNames.get(c.route.getTankerId()), driver));
        }
        int unreconciled = (int) calcs.stream().filter(c -> !c.reconciled).count();
        return new RouteCostReport(start, end, vehicleRate || driverRate, unreconciled, rows, byTanker(calcs, tankerNames), byDepot(calcs, tankDepot, depotNames));
    }

    // ── calculation ──────────────────────────────────────────────────────────

    static Calc calc(FuelRoute r, List<FuelDelivery> stops, List<FuelRouteCostEntry> actuals, Map<UUID, BigDecimal> distance, FuelSettings s, boolean vehicleRate, boolean driverRate) {
        Calc c = new Calc(r);
        List<FuelDelivery> live = stops.stream().filter(d -> !"CANCELLED".equals(d.getStatus()) && !"REVERSED".equals(d.getStatus())).toList();
        c.stops = live.size();
        for (FuelDelivery d : live) {
            if ("DELIVERED".equals(d.getStatus())) {
                c.delivered++;
                if (d.getLitresDelivered() != null) {
                    c.litres = c.litres.add(d.getLitresDelivered());
                    if (d.getTankId() != null) c.litresByTank.merge(d.getTankId(), d.getLitresDelivered(), BigDecimal::add);
                }
            }
        }
        BigDecimal farthest = null;
        for (FuelDelivery d : live) {
            BigDecimal km = d.getSiteId() == null ? null : distance.get(d.getSiteId());
            if (km != null && (farthest == null || km.compareTo(farthest) > 0)) farthest = km;
        }
        if (!vehicleRate || !driverRate) c.flags.add("RATES_NOT_SET");
        if (vehicleRate && farthest == null) c.flags.add("NO_DISTANCE");
        if (vehicleRate && farthest != null) c.estVehicle = farthest.multiply(BigDecimal.valueOf(2)).multiply(s.getVehicleCostPerKm()).setScale(2, RoundingMode.HALF_UP);
        if (driverRate && s.getDefaultTripHours() != null) c.estDriver = s.getDefaultTripHours().multiply(s.getDriverCostPerHour()).setScale(2, RoundingMode.HALF_UP);
        if (c.estVehicle != null || c.estDriver != null) c.estTotal = nz(c.estVehicle).add(nz(c.estDriver));

        for (FuelRouteCostEntry e : actuals) {
            switch (e.getCategory()) {
                case "VEHICLE" -> c.actVehicle = nz(c.actVehicle).add(e.getAmount());
                case "DRIVER" -> c.actDriver = nz(c.actDriver).add(e.getAmount());
                case "TOLLS" -> c.actTolls = nz(c.actTolls).add(e.getAmount());
                default -> c.actOther = nz(c.actOther).add(e.getAmount());
            }
        }
        c.actTotal = nz(c.actVehicle).add(nz(c.actDriver)).add(nz(c.actTolls)).add(nz(c.actOther));
        c.hasAnyActual = !actuals.isEmpty();
        // compare category by category, only where both sides exist
        if (c.estVehicle != null && c.actVehicle != null) { c.cmpEst = c.cmpEst.add(c.estVehicle); c.cmpAct = c.cmpAct.add(c.actVehicle); c.compared = true; }
        if (c.estDriver != null && c.actDriver != null) { c.cmpEst = c.cmpEst.add(c.estDriver); c.cmpAct = c.cmpAct.add(c.actDriver); c.compared = true; }
        c.reconciled = c.actVehicle != null || c.actDriver != null;
        if (!c.hasAnyActual) c.flags.add("NO_ACTUALS");
        if (c.delivered < c.stops) c.flags.add("PARTIAL_DELIVERY");
        return c;
    }

    private static List<Rollup> byTanker(List<Calc> calcs, Map<UUID, String> names) {
        Map<UUID, List<Calc>> groups = new LinkedHashMap<>();
        for (Calc c : calcs) groups.computeIfAbsent(c.route.getTankerId(), k -> new ArrayList<>()).add(c);
        List<Rollup> out = new ArrayList<>();
        groups.forEach((tankerId, list) -> {
            Acc a = new Acc();
            for (Calc c : list) a.add(c, BigDecimal.ONE);
            out.add(a.toRollup(tankerId.toString(), names.getOrDefault(tankerId, "(tanker removed)"), list.size(), (int) list.stream().filter(c -> c.reconciled).count()));
        });
        out.sort(Comparator.comparing(Rollup::label));
        return out;
    }

    private static List<Rollup> byDepot(List<Calc> calcs, Map<UUID, UUID> tankDepot, Map<UUID, String> names) {
        Map<String, Acc> accs = new LinkedHashMap<>(); Map<String, Set<UUID>> routeSets = new HashMap<>(); Map<String, Set<UUID>> reconciledSets = new HashMap<>();
        for (Calc c : calcs) {
            Map<String, BigDecimal> share = new LinkedHashMap<>();
            for (Map.Entry<UUID, BigDecimal> e : c.litresByTank.entrySet()) share.merge(tankDepot.containsKey(e.getKey()) ? tankDepot.get(e.getKey()).toString() : "", e.getValue(), BigDecimal::add);
            BigDecimal total = share.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.signum() <= 0) continue;
            for (Map.Entry<String, BigDecimal> e : share.entrySet()) {
                BigDecimal fraction = e.getValue().divide(total, 6, RoundingMode.HALF_UP);
                accs.computeIfAbsent(e.getKey(), k -> new Acc()).add(c, fraction, e.getValue());
                routeSets.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(c.route.getId());
                if (c.reconciled) reconciledSets.computeIfAbsent(e.getKey(), k -> new HashSet<>()).add(c.route.getId());
            }
        }
        List<Rollup> out = new ArrayList<>();
        accs.forEach((key, a) -> {
            String label = key.isEmpty() ? "No depot" : names.getOrDefault(UUID.fromString(key), "(depot removed)");
            out.add(a.toRollup(key, label, routeSets.get(key).size(), reconciledSets.getOrDefault(key, Set.of()).size()));
        });
        out.sort(Comparator.comparing(Rollup::label));
        return out;
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static CostEntryView view(FuelRouteCostEntry e) {
        return new CostEntryView(e.getId(), e.getRouteId(), e.getCategory(), e.getAmount(), e.getQuantity(), e.getUnit(), e.getNote(), e.getStatus(), e.getVoidReason(), e.getRecordedBy(), e.getRecordedAt());
    }

    /** One route's figures, kept together so the rollups use exactly what the row shows. */
    static final class Calc {
        final FuelRoute route;
        int stops, delivered;
        BigDecimal litres = BigDecimal.ZERO;
        final Map<UUID, BigDecimal> litresByTank = new LinkedHashMap<>();
        BigDecimal estVehicle, estDriver, estTotal, actVehicle, actDriver, actTolls, actOther, actTotal = BigDecimal.ZERO;
        BigDecimal cmpEst = BigDecimal.ZERO, cmpAct = BigDecimal.ZERO;
        boolean compared, reconciled, hasAnyActual;
        final List<String> flags = new ArrayList<>();

        Calc(FuelRoute r) { this.route = r; }

        String state() {
            if (stops > 0 && delivered == stops) return "COMPLETED";
            return delivered > 0 ? "IN_PROGRESS" : "PLANNED";
        }

        RouteCostRow row(String tanker, String driver) {
            BigDecimal variance = compared ? cmpAct.subtract(cmpEst) : null;
            BigDecimal pct = compared && cmpEst.signum() > 0 ? variance.multiply(HUNDRED).divide(cmpEst, 1, RoundingMode.HALF_UP) : null;
            BigDecimal perLitre = hasAnyActual && actTotal.signum() > 0 && litres.signum() > 0 ? actTotal.divide(litres, 4, RoundingMode.HALF_UP) : null;
            return new RouteCostRow(route.getId(), route.getRouteDate(), route.getLabel(), tanker, driver, state(), stops, delivered, litres,
                    estVehicle, estDriver, estTotal, actVehicle, actDriver, actTolls, actOther, hasAnyActual ? actTotal : null,
                    variance, pct, perLitre, reconciled, List.copyOf(flags));
        }
    }

    private static final class Acc {
        BigDecimal litres = BigDecimal.ZERO, est = BigDecimal.ZERO, act = BigDecimal.ZERO, actTotal = BigDecimal.ZERO, litresWithActuals = BigDecimal.ZERO;

        void add(Calc c, BigDecimal fraction) { add(c, fraction, c.litres.multiply(fraction)); }

        void add(Calc c, BigDecimal fraction, BigDecimal litresShare) {
            litres = litres.add(litresShare);
            est = est.add(c.cmpEst.multiply(fraction));
            act = act.add(c.cmpAct.multiply(fraction));
            if (c.hasAnyActual) { actTotal = actTotal.add(c.actTotal.multiply(fraction)); litresWithActuals = litresWithActuals.add(litresShare); }
        }

        Rollup toRollup(String key, String label, int routes, int reconciled) {
            BigDecimal perLitre = actTotal.signum() > 0 && litresWithActuals.signum() > 0 ? actTotal.divide(litresWithActuals, 4, RoundingMode.HALF_UP) : null;
            BigDecimal e = est.setScale(2, RoundingMode.HALF_UP), a = act.setScale(2, RoundingMode.HALF_UP);
            return new Rollup(key, label, routes, reconciled, litres.setScale(2, RoundingMode.HALF_UP), e, a, a.subtract(e), actTotal.setScale(2, RoundingMode.HALF_UP), perLitre);
        }
    }
}
