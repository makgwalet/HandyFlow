package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelRoute;
import za.co.handyflow.platform.fuel.domain.model.FuelTanker;
import za.co.handyflow.platform.fuel.domain.model.FuelTankerCompartment;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelRouteRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankerCompartmentRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelTankerRepository;
import za.co.handyflow.platform.fuel.dto.AllocationRequest;
import za.co.handyflow.platform.fuel.dto.RouteDtos.*;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * C04 part 2: a route is one tanker and one driver carrying several loads (stops) in an order on one day.
 * A stop is an ordinary delivery that points at the route, so statuses, stock, invoicing and variance are untouched.
 * Adding a stop allocates the load to the route's tanker and driver with the usual compliance, compartment and capacity checks;
 * the tanker is filled once for the whole route, so compartments are checked across stops.
 * Taking a stop off a route (or cancelling the route) leaves the load allocated to the same tanker and driver.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuelRouteService {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final FuelRouteRepository routes;
    private final FuelDeliveryRepository deliveries;
    private final FuelTankerRepository tankers;
    private final FuelTankerCompartmentRepository compartments;
    private final FuelTransportService transport;
    private final FuelComplianceService compliance;
    private final HrFacade hr;
    private final CrmFacade crm;

    @Transactional(readOnly = true)
    public List<RouteView> list(TenantId tenantId, LocalDate date) {
        List<FuelRoute> day = routes.findByDate(tenantId, date);
        if (day.isEmpty()) return List.of();
        Map<UUID, List<FuelDelivery>> stops = new HashMap<>();
        for (FuelDelivery d : deliveries.findByRoutes(tenantId, day.stream().map(FuelRoute::getId).toList())) stops.computeIfAbsent(d.getRouteId(), k -> new ArrayList<>()).add(d);
        Map<UUID, String> names = new HashMap<>();
        return day.stream().map(r -> view(tenantId, r, stops.getOrDefault(r.getId(), List.of()), names)).toList();
    }

    @Transactional(readOnly = true)
    public RouteView get(TenantId tenantId, UUID id) {
        FuelRoute r = routes.findOne(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Route", id.toString()));
        return view(tenantId, r, deliveries.findByRoute(tenantId, id), new HashMap<>());
    }

    @Transactional
    public RouteView create(TenantId tenantId, CreateRouteRequest req, UUID by) {
        FuelTanker tanker = tankers.findActiveById(tenantId, req.tankerId()).orElseThrow(() -> new ResourceNotFoundException("Tanker", req.tankerId().toString()));
        EmployeeSummary driver = hr.findEmployeeById(tenantId, req.driverEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver", req.driverEmployeeId().toString()));
        if (driver.status() != null && driver.status().toUpperCase().startsWith("TERMINATED")) throw new IllegalStateException(driver.fullName() + " is no longer employed.");
        compliance.requireCompliantForAllocation(tenantId, tanker.getId(), tanker.getRegistration(), driver.id(), driver.fullName());
        FuelRoute r = routes.save(FuelRoute.create(tenantId, req.routeDate(), req.label(), tanker.getId(), driver.id(), by));
        log.info("Fuel route {} created for {} on {}", r.getId(), tanker.getRegistration(), r.getRouteDate());
        return view(tenantId, r, List.of(), new HashMap<>());
    }

    /** Puts a scheduled load on the route as the last stop, allocating it to the route's tanker and driver with the given compartment split. */
    @Transactional
    public RouteView addStop(TenantId tenantId, UUID routeId, AddStopRequest req) {
        FuelRoute r = open(tenantId, routeId);
        FuelDelivery d = deliveries.findActiveByIdForUpdate(tenantId, req.deliveryId()).orElseThrow(() -> new ResourceNotFoundException("Delivery", req.deliveryId().toString()));
        if (d.getRouteId() != null) throw new IllegalStateException("This load is already a stop on a route.");
        if (!"SCHEDULED".equals(d.getStatus())) throw new IllegalStateException("Only a scheduled load can be added to a route (this one is " + d.getStatus().toLowerCase().replace('_', ' ') + ").");
        LocalDate day = d.getScheduledAt().atZone(SAST).toLocalDate();
        if (!day.equals(r.getRouteDate())) throw new IllegalStateException("This load is scheduled for " + day + ", but the route is for " + r.getRouteDate() + ".");
        List<FuelDelivery> current = deliveries.findByRoute(tenantId, routeId);
        transport.allocate(tenantId, d.getId(), new AllocationRequest(r.getTankerId(), r.getDriverEmployeeId(), req.loads()), routeId);
        // allocate() saved and flushed the delivery; reload so the route fields go on the same managed row
        FuelDelivery fresh = deliveries.findActiveByIdForUpdate(tenantId, d.getId()).orElseThrow();
        int next = current.stream().mapToInt(x -> x.getRouteSequence() == null ? 0 : x.getRouteSequence()).max().orElse(0) + 1;
        fresh.joinRoute(routeId, next);
        deliveries.save(fresh);
        return view(tenantId, r, deliveries.findByRoute(tenantId, routeId), new HashMap<>());
    }

    @Transactional
    public RouteView removeStop(TenantId tenantId, UUID routeId, UUID deliveryId) {
        FuelRoute r = open(tenantId, routeId);
        FuelDelivery d = deliveries.findActiveByIdForUpdate(tenantId, deliveryId).orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId.toString()));
        if (!routeId.equals(d.getRouteId())) throw new IllegalStateException("This load is not a stop on this route.");
        d.leaveRoute(); // refuses unless the load is still scheduled
        deliveries.save(d);
        // close the gap so positions stay 1..n
        List<FuelDelivery> rest = deliveries.findByRoute(tenantId, routeId);
        for (int i = 0; i < rest.size(); i++) rest.get(i).moveInRoute(i + 1);
        deliveries.saveAll(rest);
        return view(tenantId, r, rest, new HashMap<>());
    }

    /** Puts the stops in a new order. A stop that is on the road or delivered keeps its place; only scheduled stops move. */
    @Transactional
    public RouteView reorder(TenantId tenantId, UUID routeId, List<UUID> orderedDeliveryIds) {
        FuelRoute r = open(tenantId, routeId);
        List<FuelDelivery> current = deliveries.findByRoute(tenantId, routeId);
        Set<UUID> have = new HashSet<>();
        current.forEach(d -> have.add(d.getId()));
        if (orderedDeliveryIds.size() != current.size() || !have.equals(new HashSet<>(orderedDeliveryIds))) {
            throw new IllegalArgumentException("List every stop on the route exactly once.");
        }
        for (int i = 0; i < current.size(); i++) {
            FuelDelivery d = current.get(i);
            if (!"SCHEDULED".equals(d.getStatus()) && !d.getId().equals(orderedDeliveryIds.get(i))) {
                throw new IllegalStateException("A stop that is already on the road or delivered cannot be moved.");
            }
        }
        Map<UUID, FuelDelivery> byId = new HashMap<>();
        current.forEach(d -> byId.put(d.getId(), d));
        for (int i = 0; i < orderedDeliveryIds.size(); i++) byId.get(orderedDeliveryIds.get(i)).moveInRoute(i + 1);
        deliveries.saveAll(current);
        return view(tenantId, r, deliveries.findByRoute(tenantId, routeId), new HashMap<>());
    }

    /** Only while every stop is still scheduled. The loads stay allocated to the same tanker and driver, off the route. */
    @Transactional
    public RouteView cancel(TenantId tenantId, UUID routeId, String reason) {
        FuelRoute r = routes.findOneForUpdate(tenantId, routeId).orElseThrow(() -> new ResourceNotFoundException("Route", routeId.toString()));
        List<FuelDelivery> stops = deliveries.findByRoute(tenantId, routeId);
        if (stops.stream().anyMatch(d -> !"SCHEDULED".equals(d.getStatus()))) throw new IllegalStateException("A route with a stop that is on the road or delivered cannot be cancelled.");
        r.cancel(reason);
        routes.save(r);
        for (FuelDelivery d : stops) d.leaveRoute();
        deliveries.saveAll(stops);
        return view(tenantId, r, List.of(), new HashMap<>());
    }

    private FuelRoute open(TenantId tenantId, UUID id) {
        FuelRoute r = routes.findOneForUpdate(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Route", id.toString()));
        if (!r.isOpen()) throw new IllegalStateException("This route is cancelled.");
        return r;
    }

    private RouteView view(TenantId tenantId, FuelRoute r, List<FuelDelivery> stops, Map<UUID, String> names) {
        FuelTanker t = tankers.findActiveById(tenantId, r.getTankerId()).orElse(null);
        BigDecimal capacity = compartments.findByTanker(tenantId, r.getTankerId()).stream().map(FuelTankerCompartment::getCapacityLitres).reduce(BigDecimal.ZERO, BigDecimal::add);
        String driver = hr.findEmployeeById(tenantId, r.getDriverEmployeeId()).map(EmployeeSummary::fullName).orElse(null);
        List<StopView> views = stops.stream().map(d -> new StopView(d.getId(), d.getRouteSequence() == null ? 0 : d.getRouteSequence(), d.getCustomerId(),
                d.getCustomerId() == null ? null : names.computeIfAbsent(d.getCustomerId(), id -> crm.findCustomerById(tenantId, id).map(CustomerSummary::name).orElse("(customer removed)")),
                d.getFuelType(), d.getLitresOrdered(), d.getStatus(), d.getScheduledAt(), d.getWindowStart(), d.getWindowEnd(), d.getFailedAttempts())).toList();
        BigDecimal planned = stops.stream().filter(d -> !"CANCELLED".equals(d.getStatus())).map(FuelDelivery::getLitresOrdered).reduce(BigDecimal.ZERO, BigDecimal::add);
        long delivered = stops.stream().filter(d -> "DELIVERED".equals(d.getStatus())).count();
        return new RouteView(r.getId(), r.getRouteDate(), r.getLabel(), r.getStatus(), state(r, stops, delivered), r.getTankerId(), t == null ? null : t.getRegistration(),
                r.getDriverEmployeeId(), driver, capacity, planned, stops.size(), (int) delivered, r.getCancelReason(), views);
    }

    static String state(FuelRoute r, List<FuelDelivery> stops, long delivered) {
        if (!r.isOpen()) return "CANCELLED";
        if (stops.isEmpty()) return "PLANNED";
        boolean allDone = stops.stream().allMatch(d -> "DELIVERED".equals(d.getStatus()) || "CANCELLED".equals(d.getStatus()) || "REVERSED".equals(d.getStatus()));
        if (allDone) return "COMPLETED";
        boolean started = delivered > 0 || stops.stream().anyMatch(d -> "IN_TRANSIT".equals(d.getStatus()));
        return started ? "IN_PROGRESS" : "PLANNED";
    }
}
