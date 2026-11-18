package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.fuel.FleetVehicleLookup;
import za.co.handyflow.platform.fuel.FleetVehicleLookup.VehicleRef;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.EligibilityResult;
import za.co.handyflow.platform.fuel.dto.EligibilityDtos.ReviewAuthority;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/** Stage 3: tankers with compartments, putting a tanker and driver on a load, the day's planning board, and the driver's own loads. */
@Service
@RequiredArgsConstructor
public class FuelTransportService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    /** A tanker or driver cannot be on two open loads scheduled closer together than this. */
    static final Duration TURNAROUND = Duration.ofHours(4);

    private final FuelTankerRepository tankers;
    private final FuelTankerCompartmentRepository compartments;
    private final FuelDeliveryCompartmentRepository loadSplits;
    private final FuelDeliveryRepository deliveries;
    private final FuelService fuelService;
    private final HrFacade hr;
    private final CrmFacade crm;
    private final FuelComplianceService compliance;
    private final FuelEligibilityService eligibility;
    private final ObjectProvider<FleetVehicleLookup> fleet;
    private final FuelPushService push;
    private final FuelArrivalCheckService arrivalChecks;

    // ── Tankers ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TankerResponse> listTankers(TenantId tenantId) {
        List<FuelTanker> list = tankers.findAllActive(tenantId);
        Map<UUID, List<FuelTankerCompartment>> byTanker = list.isEmpty() ? Map.of()
                : compartments.findByTankers(tenantId, list.stream().map(FuelTanker::getId).toList())
                        .stream().collect(Collectors.groupingBy(FuelTankerCompartment::getTankerId));
        Map<UUID, VehicleRef> fleetById = fleetIndex(tenantId, list);
        return list.stream().map(t -> toResponse(tenantId, t, byTanker.getOrDefault(t.getId(), List.of()), fleetById)).toList();
    }

    @Transactional
    public TankerResponse createTanker(TenantId tenantId, TankerRequest req) {
        FuelTanker t = FuelTanker.create(tenantId, req.registration(), req.name());
        if (req.fleetVehicleId() != null) linkToFleet(tenantId, t, req.fleetVehicleId());
        if (tankers.registrationTaken(tenantId, t.getRegistration(), t.getId())) {
            throw new ConflictException("A tanker with registration " + t.getRegistration() + " already exists.");
        }
        tankers.save(t);
        List<FuelTankerCompartment> list = new ArrayList<>();
        int n = 1;
        for (BigDecimal cap : req.compartmentCapacities()) list.add(FuelTankerCompartment.create(tenantId, t.getId(), n++, cap));
        compartments.saveAll(list);
        return toResponse(tenantId, t, list);
    }

    /** Registration and name only; compartments are fixed (deactivate and recreate if the tanker is rebuilt). */
    @Transactional
    public TankerResponse updateTanker(TenantId tenantId, UUID id, String registration, String name) {
        return updateTanker(tenantId, id, registration, name, null, false);
    }

    /** As above, and optionally links to a Fleet vehicle, or (unlink true) removes the link. Null/false leaves the link alone. */
    @Transactional
    public TankerResponse updateTanker(TenantId tenantId, UUID id, String registration, String name, UUID fleetVehicleId, boolean unlink) {
        FuelTanker t = findTanker(tenantId, id);
        if (fleetVehicleId != null && unlink) throw new IllegalArgumentException("Choose a vehicle to link, or unlink, not both.");
        // While linked, Fleet owns the registration: the typed one is ignored so the two can never disagree.
        t.update(t.getFleetVehicleId() != null && fleetVehicleId == null && !unlink ? t.getRegistration() : registration, name);
        if (fleetVehicleId != null) linkToFleet(tenantId, t, fleetVehicleId);
        else if (unlink) t.linkFleetVehicle(null, null);
        if (tankers.registrationTaken(tenantId, t.getRegistration(), t.getId())) {
            throw new ConflictException("A tanker with registration " + t.getRegistration() + " already exists.");
        }
        tankers.save(t);
        return toResponse(tenantId, t, compartments.findByTanker(tenantId, id));
    }

    @Transactional
    public void deactivateTanker(TenantId tenantId, UUID id) {
        FuelTanker t = findTanker(tenantId, id);
        if (deliveries.countOpenForTanker(tenantId, id) > 0) {
            throw new IllegalStateException("This tanker is allocated to loads that have not been delivered. Reallocate them first.");
        }
        t.deactivate();
        tankers.save(t);
    }

    /** Fleet vehicles the tenant could link a tanker to; each says which tanker already uses it. Empty when Fleet is not available. */
    @Transactional(readOnly = true)
    public List<FleetVehicleOption> fleetVehicles(TenantId tenantId) {
        FleetVehicleLookup lookup = fleet == null ? null : fleet.getIfAvailable();
        if (lookup == null) return List.of();
        Map<UUID, String> used = new HashMap<>();
        for (FuelTanker t : tankers.findAllActive(tenantId)) if (t.getFleetVehicleId() != null) used.put(t.getFleetVehicleId(), t.getRegistration());
        return lookup.list(tenantId).stream().map(v -> new FleetVehicleOption(v.id(), v.registration(), v.label(), used.get(v.id()))).toList();
    }

    @Transactional(readOnly = true)
    public List<DriverResponse> drivers(TenantId tenantId) {
        return hr.activeEmployees(tenantId).stream()
                .map(e -> new DriverResponse(e.id(), e.fullName(), e.jobTitle(), e.phone())).toList();
    }

    // ── Allocation ───────────────────────────────────────────────────────────

    @Transactional
    public DeliveryResponse allocate(TenantId tenantId, UUID deliveryId, AllocationRequest req) {
        return allocate(tenantId, deliveryId, req, null);
    }

    @Transactional
    public DeliveryResponse allocate(TenantId tenantId, UUID deliveryId, AllocationRequest req, UUID routeId) {
        return allocate(tenantId, deliveryId, req, routeId, ReviewAuthority.NONE);
    }

    /**
     * What allocating this tanker and driver to this load would run into, without changing anything: Blocks, Reviews and information for
     * the screen. Allocation makes the same check, so a load that previews clean is not turned away later by eligibility.
     */
    @Transactional(readOnly = true)
    public EligibilityResult previewEligibility(TenantId tenantId, UUID deliveryId, AllocationRequest req) {
        FuelDelivery d = deliveries.findActiveById(tenantId, deliveryId).orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId.toString()));
        FuelTanker tanker = findTanker(tenantId, req.tankerId());
        EmployeeSummary driver = hr.findEmployeeById(tenantId, req.driverEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver", req.driverEmployeeId().toString()));
        return eligibility.evaluate(tenantId, d, tanker, driver.id(), driver.fullName(), compartments.findByTanker(tenantId, tanker.getId()), req.loads());
    }

    /**
     * Allocates a load. With a route id the load is a stop on that route: the tanker is shared with the route's other stops, so
     * the time-conflict check ignores them, a compartment may be used by several stops as long as together they fit and carry
     * one fuel type, and a load already on a route can only be changed from that route.
     */
    @Transactional
    public DeliveryResponse allocate(TenantId tenantId, UUID deliveryId, AllocationRequest req, UUID routeId, ReviewAuthority review) {
        FuelDelivery d = deliveries.findActiveByIdForUpdate(tenantId, deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId.toString()));
        if (routeId == null && d.getRouteId() != null) {
            throw new IllegalStateException("This load is a stop on a route. Change it from the route.");
        }
        if (!"SCHEDULED".equals(d.getStatus())) {
            throw new IllegalStateException("Only a scheduled load can be allocated (this one is " + d.getStatus().toLowerCase().replace('_', ' ') + ").");
        }
        FuelTanker tanker = findTanker(tenantId, req.tankerId());
        EmployeeSummary driver = hr.findEmployeeById(tenantId, req.driverEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Driver", req.driverEmployeeId().toString()));
        if (driver.status() != null && driver.status().toUpperCase().startsWith("TERMINATED")) {
            throw new IllegalStateException(driver.fullName() + " is no longer employed.");
        }

        compliance.requireCompliantForAllocation(tenantId, tanker.getId(), tanker.getRegistration(), driver.id(), driver.fullName());
        compliance.requireLoadingSiteLicensed(tenantId, d.getTankId());

        Map<UUID, FuelTankerCompartment> comps = compartments.findByTanker(tenantId, tanker.getId()).stream()
                .collect(Collectors.toMap(FuelTankerCompartment::getId, c -> c));
        BigDecimal total = BigDecimal.ZERO;
        Set<UUID> seen = new HashSet<>();
        for (AllocationRequest.CompartmentLoad l : req.loads()) {
            FuelTankerCompartment c = comps.get(l.compartmentId());
            if (c == null) throw new IllegalArgumentException("That compartment does not belong to " + tanker.getRegistration() + ".");
            if (!seen.add(c.getId())) throw new IllegalArgumentException("Compartment " + c.getNumber() + " is listed twice.");
            if (l.litres().compareTo(c.getCapacityLitres()) > 0) {
                throw new IllegalArgumentException("Compartment " + c.getNumber() + " holds " + c.getCapacityLitres().stripTrailingZeros().toPlainString() + " L; you asked for " + l.litres().stripTrailingZeros().toPlainString() + " L.");
            }
            total = total.add(l.litres());
        }
        if (total.compareTo(d.getLitresOrdered()) != 0) {
            throw new IllegalArgumentException("The compartments add up to " + total.stripTrailingZeros().toPlainString() + " L but the load is "
                    + d.getLitresOrdered().stripTrailingZeros().toPlainString() + " L.");
        }

        if (routeId != null) checkRouteCompartments(tenantId, routeId, d, req, comps);

        Instant from = d.getScheduledAt().minus(TURNAROUND), to = d.getScheduledAt().plus(TURNAROUND);
        if (deliveries.findTankerConflicts(tenantId, tanker.getId(), d.getId(), from, to).stream().anyMatch(c -> !sameRoute(c, routeId))) {
            throw new IllegalStateException(tanker.getRegistration() + " is already on another load within " + TURNAROUND.toHours() + " hours of this one.");
        }
        if (deliveries.findDriverConflicts(tenantId, req.driverEmployeeId(), d.getId(), from, to).stream().anyMatch(c -> !sameRoute(c, routeId))) {
            throw new IllegalStateException(driver.fullName() + " is already on another load within " + TURNAROUND.toHours() + " hours of this one.");
        }

        // Last, so a review acknowledgement is only recorded for an allocation that goes ahead.
        eligibility.enforce(tenantId, d, tanker, driver.id(),
                eligibility.evaluate(tenantId, d, tanker, driver.id(), driver.fullName(), comps.values(), req.loads()), req.reviewReason(), review);

        d.allocate(tanker.getId(), tanker.getRegistration(), driver.id(), driver.fullName());
        deliveries.save(d);
        loadSplits.deleteByDelivery(tenantId, d.getId());
        loadSplits.saveAll(req.loads().stream().map(l -> FuelDeliveryCompartment.create(tenantId, d.getId(), l.compartmentId(), l.litres())).toList());
        // The driver's phone, if they use the app. After commit, and a failure here never undoes the allocation.
        push.notifyUser(tenantId, driver.userId(), "New load", "You have a " + d.getFuelType() + " load for " + d.getScheduledAt().atZone(java.time.ZoneId.of("Africa/Johannesburg")).toLocalDate() + ".",
                Map.of("screen", "LoadDetail", "loadId", String.valueOf(d.getId())));
        return fuelService.deliveriesForOrderOrSelf(tenantId, d.getId());
    }

    private static boolean sameRoute(FuelDelivery other, UUID routeId) {
        return routeId != null && routeId.equals(other.getRouteId());
    }

    /** Across all the stops on a route the tanker is filled once: each compartment holds one fuel type and its total cannot pass its capacity. */
    private void checkRouteCompartments(TenantId tenantId, UUID routeId, FuelDelivery self, AllocationRequest req, Map<UUID, FuelTankerCompartment> comps) {
        Map<UUID, BigDecimal> used = new HashMap<>();
        Map<UUID, String> fuel = new HashMap<>();
        for (FuelDelivery sibling : deliveries.findByRoute(tenantId, routeId)) {
            if (sibling.getId().equals(self.getId())) continue;
            for (FuelDeliveryCompartment s : loadSplits.findByDelivery(tenantId, sibling.getId())) {
                used.merge(s.getCompartmentId(), s.getLitres(), BigDecimal::add);
                fuel.put(s.getCompartmentId(), sibling.getFuelType());
            }
        }
        for (AllocationRequest.CompartmentLoad l : req.loads()) {
            FuelTankerCompartment c = comps.get(l.compartmentId());
            String other = fuel.get(c.getId());
            if (other != null && !other.equals(self.getFuelType())) {
                throw new IllegalArgumentException("Compartment " + c.getNumber() + " already carries " + other + " for another stop on this route.");
            }
            BigDecimal total = used.getOrDefault(c.getId(), BigDecimal.ZERO).add(l.litres());
            if (total.compareTo(c.getCapacityLitres()) > 0) {
                throw new IllegalArgumentException("Compartment " + c.getNumber() + " holds " + c.getCapacityLitres().stripTrailingZeros().toPlainString()
                        + " L and other stops on this route already use " + used.getOrDefault(c.getId(), BigDecimal.ZERO).stripTrailingZeros().toPlainString() + " L of it.");
            }
        }
    }

    // ── Planning board ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PlanningResponse planning(TenantId tenantId, LocalDate date) {
        Instant from = date.atStartOfDay(SAST).toInstant(), to = date.plusDays(1).atStartOfDay(SAST).toInstant();
        List<FuelDelivery> day = deliveries.findScheduledBetween(tenantId, from, to);
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, FuelTanker> tankerById = tankers.findAllActive(tenantId).stream().collect(Collectors.toMap(FuelTanker::getId, t -> t));
        List<PlanningResponse.PlanLoad> loads = new ArrayList<>();
        Map<UUID, int[]> count = new HashMap<>();
        Map<UUID, BigDecimal> litres = new HashMap<>();
        for (FuelDelivery d : day) {
            String customer = d.getCustomerId() == null ? null : names.computeIfAbsent(d.getCustomerId(),
                    id -> crm.findCustomerById(tenantId, id).map(CustomerSummary::name).orElse("(customer removed)"));
            FuelTanker t = d.getTankerId() == null ? null : tankerById.get(d.getTankerId());
            loads.add(new PlanningResponse.PlanLoad(d.getId(), d.getStatus(), d.getScheduledAt(), d.getCustomerId(), customer, d.getFuelType(),
                    d.getLitresOrdered(), d.getTankerId(), t != null ? t.getRegistration() : d.getVehicleReg(),
                    d.getDriverEmployeeId(), d.getDriverName(), d.isAllocated(),
                    d.getWindowStart(), d.getWindowEnd(), d.getFailedAttempts(), d.getRouteId(), d.getRouteSequence()));
            if (d.getTankerId() != null) {
                count.computeIfAbsent(d.getTankerId(), k -> new int[1])[0]++;
                litres.merge(d.getTankerId(), d.getLitresOrdered(), BigDecimal::add);
            }
        }
        List<FuelTankerCompartment> comps = tankerById.isEmpty() ? List.of() : compartments.findByTankers(tenantId, tankerById.keySet());
        Map<UUID, BigDecimal> capacity = new HashMap<>();
        comps.forEach(c -> capacity.merge(c.getTankerId(), c.getCapacityLitres(), BigDecimal::add));
        List<PlanningResponse.TankerDay> days = tankerById.values().stream()
                .map(t -> new PlanningResponse.TankerDay(t.getId(), t.getRegistration(), capacity.getOrDefault(t.getId(), BigDecimal.ZERO),
                        count.getOrDefault(t.getId(), new int[1])[0], litres.getOrDefault(t.getId(), BigDecimal.ZERO)))
                .sorted(Comparator.comparing(PlanningResponse.TankerDay::registration)).toList();
        return new PlanningResponse(date, loads, days);
    }

    /** The day's loads for one tanker, split by compartment, cancelled and reversed loads left out. Read-only, for the compartment view. */
    @Transactional(readOnly = true)
    public List<za.co.handyflow.platform.fuel.dto.CompartmentLoadView> compartmentLoads(TenantId tenantId, UUID tankerId, LocalDate date) {
        tankers.findActiveById(tenantId, tankerId).orElseThrow(() -> new ResourceNotFoundException("Tanker", tankerId.toString()));
        Instant from = date.atStartOfDay(SAST).toInstant(), to = date.plusDays(1).atStartOfDay(SAST).toInstant();
        Map<UUID, String> names = new HashMap<>();
        List<za.co.handyflow.platform.fuel.dto.CompartmentLoadView> out = new ArrayList<>();
        for (FuelDelivery d : deliveries.findScheduledBetween(tenantId, from, to)) {
            if (!tankerId.equals(d.getTankerId()) || "CANCELLED".equals(d.getStatus()) || "REVERSED".equals(d.getStatus())) continue;
            String customer = d.getCustomerId() == null ? null : names.computeIfAbsent(d.getCustomerId(),
                    id -> crm.findCustomerById(tenantId, id).map(CustomerSummary::name).orElse("(customer removed)"));
            for (FuelDeliveryCompartment s : loadSplits.findByDelivery(tenantId, d.getId())) {
                out.add(new za.co.handyflow.platform.fuel.dto.CompartmentLoadView(d.getId(), d.getStatus(), d.getScheduledAt(), customer, d.getFuelType(), s.getCompartmentId(), s.getLitres()));
            }
        }
        out.sort(Comparator.comparing(za.co.handyflow.platform.fuel.dto.CompartmentLoadView::scheduledAt));
        return out;
    }

    // ── Driver ───────────────────────────────────────────────────────────────

    /** The loads allocated to the signed-in driver that are still open. */
    @Transactional(readOnly = true)
    public List<DeliveryResponse> myLoads(TenantId tenantId, UUID userId) {
        EmployeeSummary me = requireDriver(tenantId, userId);
        return deliveries.findOpenForDriver(tenantId, me.id()).stream().map(d -> fuelService.toResponse(d)).toList();
    }

    @Transactional
    public DeliveryResponse myDispatch(TenantId tenantId, UUID userId, UUID deliveryId) {
        FuelDelivery d = ownLoad(tenantId, userId, deliveryId);
        return fuelService.dispatchDelivery(tenantId, d.getId(), new DispatchDeliveryRequest(
                d.getDriverName() == null ? "Driver" : d.getDriverName(), d.getVehicleReg() == null ? "-" : d.getVehicleReg()));
    }

    @Transactional
    public DeliveryResponse myComplete(TenantId tenantId, UUID userId, UUID deliveryId, CompleteDeliveryRequest req) {
        FuelDelivery d = ownLoad(tenantId, userId, deliveryId);
        // Offloading is complete and the delivery is final: the server asks for a clear arrival check itself (when the company enforces it).
        arrivalChecks.requireClear(tenantId, d.getId());
        return fuelService.completeDelivery(tenantId, d.getId(), req);
    }

    private FuelDelivery ownLoad(TenantId tenantId, UUID userId, UUID deliveryId) {
        EmployeeSummary me = requireDriver(tenantId, userId);
        FuelDelivery d = deliveries.findActiveById(tenantId, deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId.toString()));
        // Same message as a missing load so a driver cannot probe other drivers' loads.
        if (!me.id().equals(d.getDriverEmployeeId())) throw new ResourceNotFoundException("Delivery", deliveryId.toString());
        return d;
    }

    private EmployeeSummary requireDriver(TenantId tenantId, UUID userId) {
        if (userId == null) throw new IllegalStateException("You are not signed in as a user.");
        return hr.findEmployeeByUserId(tenantId, userId)
                .orElseThrow(() -> new IllegalStateException("Your login is not linked to an employee record, so no loads can be allocated to you. Ask HR to link it."));
    }

    /** Checks the vehicle is one of this tenant's in Fleet and not already another tanker, then links and takes its registration. */
    private void linkToFleet(TenantId tenantId, FuelTanker t, UUID vehicleId) {
        FleetVehicleLookup lookup = fleet == null ? null : fleet.getIfAvailable();
        if (lookup == null) throw new IllegalStateException("Fleet is not available, so a vehicle cannot be linked.");
        VehicleRef v = lookup.find(tenantId, vehicleId).orElseThrow(() -> new IllegalArgumentException("That vehicle is not in your Fleet."));
        if (tankers.fleetVehicleTaken(tenantId, vehicleId, t.getId())) {
            throw new ConflictException(v.registration() + " is already linked to another tanker.");
        }
        t.linkFleetVehicle(vehicleId, v.registration());
    }

    /** One Fleet lookup for a whole list (not one per tanker); empty when Fleet is not there or nothing needs it. */
    private Map<UUID, VehicleRef> fleetIndex(TenantId tenantId, List<FuelTanker> list) {
        FleetVehicleLookup lookup = fleet == null ? null : fleet.getIfAvailable();
        if (lookup == null || list.isEmpty()) return Map.of();
        try {
            return lookup.list(tenantId).stream().collect(Collectors.toMap(VehicleRef::id, v -> v, (a, b) -> a));
        } catch (RuntimeException e) {
            return Map.of();   // Fleet being slow or down must not stop the tanker list loading
        }
    }

    private FuelTanker findTanker(TenantId tenantId, UUID id) {
        return tankers.findActiveById(tenantId, id).orElseThrow(() -> new ResourceNotFoundException("Tanker", id.toString()));
    }

    private TankerResponse toResponse(TenantId tenantId, FuelTanker t, List<FuelTankerCompartment> comps) {
        return toResponse(tenantId, t, comps, t.getFleetVehicleId() == null ? Map.of() : fleetIndex(tenantId, List.of(t)));
    }

    private TankerResponse toResponse(TenantId tenantId, FuelTanker t, List<FuelTankerCompartment> comps, Map<UUID, VehicleRef> fleetById) {
        VehicleRef linked = t.getFleetVehicleId() == null ? null : fleetById.get(t.getFleetVehicleId());
        // Unlinked and the same registration exists in Fleet: say so, it is probably the same vehicle entered twice.
        VehicleRef suggestion = t.getFleetVehicleId() != null ? null : fleetById.values().stream()
                .filter(v -> v.registration() != null && v.registration().trim().equalsIgnoreCase(t.getRegistration())).findFirst().orElse(null);
        BigDecimal total = comps.stream().map(FuelTankerCompartment::getCapacityLitres).reduce(BigDecimal.ZERO, BigDecimal::add);
        String registration = linked != null ? linked.registration().trim().toUpperCase() : t.getRegistration();
        String linkLabel = t.getFleetVehicleId() == null ? null : linked != null ? linked.label() : "(vehicle removed from Fleet)";
        return new TankerResponse(t.getId(), registration, t.getName(), t.isActive(), total,
                comps.stream().map(c -> new TankerResponse.CompartmentResponse(c.getId(), c.getNumber(), c.getCapacityLitres())).toList(),
                deliveries.countOpenForTanker(tenantId, t.getId()), t.getFleetVehicleId(), linkLabel,
                suggestion == null ? null : suggestion.id(), suggestion == null ? null : suggestion.label());
    }
}
