package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.ObjectProvider;
import za.co.handyflow.platform.fuel.FleetVehicleLookup;
import za.co.handyflow.platform.fuel.FleetVehicleLookup.VehicleRef;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryCompartment;
import za.co.handyflow.platform.fuel.domain.model.FuelTanker;
import za.co.handyflow.platform.fuel.domain.model.FuelTankerCompartment;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.*;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ConflictException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelTransportServiceTest {

    @Mock FuelTankerRepository tankers;
    @Mock FuelTankerCompartmentRepository compartments;
    @Mock FuelDeliveryCompartmentRepository loadSplits;
    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelService fuelService;
    @Mock HrFacade hr;
    @Mock CrmFacade crm;
    @Mock FuelComplianceService compliance;
    @Mock FuelEligibilityService eligibility;
    @Mock ObjectProvider<FleetVehicleLookup> fleetProvider;
    @Mock FleetVehicleLookup fleetLookup;
    @Mock FuelPushService push;
    @Mock FuelArrivalCheckService arrivalChecks;
    @Mock FuelPodPolicyService podPolicy;
    @InjectMocks FuelTransportService service;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    FuelTanker tanker; FuelTankerCompartment c1, c2; FuelDelivery load; EmployeeSummary driver; final UUID me = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tanker = FuelTanker.create(T, "gp 12 ab", "Big Blue");
        c1 = FuelTankerCompartment.create(T, tanker.getId(), 1, bd("10000"));
        c2 = FuelTankerCompartment.create(T, tanker.getId(), 2, bd("6000"));
        load = FuelDelivery.create(T, UUID.randomUUID(), UUID.randomUUID(), Map.of(), "DIESEL", bd("12000"), bd("22"), Instant.now(), null, null);
        driver = new EmployeeSummary(UUID.randomUUID(), "E1", "Moses", "Sithole", "Moses Sithole", null, null, "Driver", null, "ACTIVE");
        when(deliveries.findActiveByIdForUpdate(eq(T), any())).thenReturn(Optional.of(load));
        when(deliveries.findActiveById(eq(T), any())).thenReturn(Optional.of(load));
        when(tankers.findActiveById(eq(T), eq(tanker.getId()))).thenReturn(Optional.of(tanker));
        when(compartments.findByTanker(T, tanker.getId())).thenReturn(List.of(c1, c2));
        when(hr.findEmployeeById(T, driver.id())).thenReturn(Optional.of(driver));
        when(deliveries.findTankerConflicts(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(deliveries.findDriverConflicts(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(fuelService.deliveriesForOrderOrSelf(any(), any())).thenReturn(null);
        when(eligibility.evaluate(any(), any(), any(), any(), any(), any(), any())).thenReturn(za.co.handyflow.platform.fuel.dto.EligibilityDtos.EligibilityResult.NONE);
    }

    private AllocationRequest alloc(String a, String b) {
        return new AllocationRequest(tanker.getId(), driver.id(), List.of(
                new AllocationRequest.CompartmentLoad(c1.getId(), bd(a)), new AllocationRequest.CompartmentLoad(c2.getId(), bd(b))));
    }

    @Test @DisplayName("tanker registration is upper-cased and compartments must have capacity")
    void tankerRules() {
        assertEquals("GP 12 AB", tanker.getRegistration());
        assertThrows(IllegalArgumentException.class, () -> FuelTankerCompartment.create(T, tanker.getId(), 1, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> FuelTanker.create(T, " ", null));
    }

    @Test @DisplayName("a duplicate registration is refused and nothing is saved")
    void duplicateRegistration() {
        when(tankers.registrationTaken(eq(T), any(), any())).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.createTanker(T, new TankerRequest("GP 12 AB", null, List.of(bd("5000")))));
        verify(tankers, never()).save(any());
    }

    // ---- C03: tankers linked to a Fleet vehicle ----

    private VehicleRef vehicle(String reg) { return new VehicleRef(UUID.randomUUID(), reg, "Volvo", "FM"); }

    @Test @DisplayName("C03: linking takes the registration from Fleet, not from what was typed")
    void linkTakesFleetRegistration() {
        VehicleRef v = vehicle("cy 99 zz");
        when(fleetProvider.getIfAvailable()).thenReturn(fleetLookup);
        when(fleetLookup.find(T, v.id())).thenReturn(Optional.of(v));
        when(fleetLookup.list(T)).thenReturn(List.of(v));
        TankerResponse r = service.createTanker(T, new TankerRequest("typed wrongly", "Blue", List.of(bd("5000")), v.id(), null));
        assertEquals("CY 99 ZZ", r.registration());
        assertEquals(v.id(), r.fleetVehicleId());
        assertEquals(v.label(), r.fleetVehicleLabel());
    }

    @Test @DisplayName("C03: a vehicle that is not in this tenant's Fleet is refused and nothing is saved")
    void unknownVehicleRefused() {
        UUID missing = UUID.randomUUID();
        when(fleetProvider.getIfAvailable()).thenReturn(fleetLookup);
        when(fleetLookup.find(T, missing)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.createTanker(T, new TankerRequest("GP 1", null, List.of(bd("5000")), missing, null)));
        verify(tankers, never()).save(any());
    }

    @Test @DisplayName("C03: one Fleet vehicle can back only one tanker")
    void vehicleAlreadyLinked() {
        VehicleRef v = vehicle("CY 99 ZZ");
        when(fleetProvider.getIfAvailable()).thenReturn(fleetLookup);
        when(fleetLookup.find(T, v.id())).thenReturn(Optional.of(v));
        when(tankers.fleetVehicleTaken(eq(T), eq(v.id()), any())).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.createTanker(T, new TankerRequest("GP 1", null, List.of(bd("5000")), v.id(), null)));
        verify(tankers, never()).save(any());
    }

    @Test @DisplayName("C03: when Fleet is not available, linking is refused clearly rather than silently ignored")
    void fleetMissing() {
        when(fleetProvider.getIfAvailable()).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> service.createTanker(T, new TankerRequest("GP 1", null, List.of(bd("5000")), UUID.randomUUID(), null)));
    }

    @Test @DisplayName("C03: a tanker with no link is unaffected, and an unlinked one with a matching plate gets a suggestion")
    void suggestionForMatchingPlate() {
        VehicleRef v = vehicle("gp 12 ab");
        when(fleetProvider.getIfAvailable()).thenReturn(fleetLookup);
        when(fleetLookup.list(T)).thenReturn(List.of(v));
        when(tankers.findAllActive(T)).thenReturn(List.of(tanker));
        when(compartments.findByTankers(eq(T), any())).thenReturn(List.of(c1, c2));
        List<TankerResponse> out = service.listTankers(T);
        assertNull(out.get(0).fleetVehicleId());
        assertEquals(v.id(), out.get(0).fleetSuggestionId());
    }

    @Test @DisplayName("C03: Fleet failing does not stop the tanker list loading")
    void fleetFailureDoesNotBreakList() {
        when(fleetProvider.getIfAvailable()).thenReturn(fleetLookup);
        when(fleetLookup.list(T)).thenThrow(new RuntimeException("fleet down"));
        when(tankers.findAllActive(T)).thenReturn(List.of(tanker));
        when(compartments.findByTankers(eq(T), any())).thenReturn(List.of(c1, c2));
        assertEquals(1, service.listTankers(T).size());
    }

    @Test @DisplayName("C03: choosing a vehicle and unlinking in the same request is refused")
    void linkAndUnlinkTogether() {
        assertThrows(IllegalArgumentException.class, () -> service.updateTanker(T, tanker.getId(), "X", null, UUID.randomUUID(), true));
    }

    @Test @DisplayName("allocating sets tanker, driver and plate and stores the compartment split")
    void allocates() {
        service.allocate(T, load.getId(), alloc("10000", "2000"));
        assertTrue(load.isAllocated());
        assertEquals("GP 12 AB", load.getVehicleReg());
        assertEquals("Moses Sithole", load.getDriverName());
        verify(loadSplits).deleteByDelivery(T, load.getId());
        verify(loadSplits).saveAll(any());
    }

    @Test @DisplayName("allocating alerts the driver's phone when they have a login, and says only the fuel and the day")
    void driverPushed() {
        UUID login = UUID.randomUUID();
        driver = new EmployeeSummary(driver.id(), "E1", "Moses", "Sithole", "Moses Sithole", null, null, "Driver", null, "ACTIVE", null, login);
        when(hr.findEmployeeById(T, driver.id())).thenReturn(Optional.of(driver));
        service.allocate(T, load.getId(), alloc("10000", "2000"));
        verify(push).notifyUser(eq(T), eq(login), eq("New load"), argThat(b -> b.contains("DIESEL") && !b.contains("12000")), eq(Map.of("screen", "LoadDetail", "loadId", String.valueOf(load.getId()))));
    }

    @Test @DisplayName("a driver with no login is simply not alerted")
    void driverWithoutLogin() {
        service.allocate(T, load.getId(), alloc("10000", "2000"));
        verify(push).notifyUser(eq(T), isNull(), any(), any(), any());
    }

    @Test @DisplayName("compartments must add up to the load and never exceed their capacity")
    void compartmentMath() {
        assertThrows(IllegalArgumentException.class, () -> service.allocate(T, load.getId(), alloc("10000", "1000")));  // 11,000 != 12,000
        assertThrows(IllegalArgumentException.class, () -> service.allocate(T, load.getId(), alloc("5000", "7000")));   // c2 holds 6,000
        AllocationRequest dup = new AllocationRequest(tanker.getId(), driver.id(), List.of(
                new AllocationRequest.CompartmentLoad(c1.getId(), bd("6000")), new AllocationRequest.CompartmentLoad(c1.getId(), bd("6000"))));
        assertThrows(IllegalArgumentException.class, () -> service.allocate(T, load.getId(), dup));
        AllocationRequest foreign = new AllocationRequest(tanker.getId(), driver.id(), List.of(new AllocationRequest.CompartmentLoad(UUID.randomUUID(), bd("12000"))));
        assertThrows(IllegalArgumentException.class, () -> service.allocate(T, load.getId(), foreign));
        assertFalse(load.isAllocated());
        verify(loadSplits, never()).saveAll(any());
    }

    @Test @DisplayName("a tanker or driver already on a nearby load cannot be double-booked")
    void doubleBooking() {
        FuelDelivery other = FuelDelivery.create(T, UUID.randomUUID(), null, Map.of(), "DIESEL", bd("1000"), bd("22"), Instant.now(), null, null);
        when(deliveries.findTankerConflicts(any(), any(), any(), any(), any())).thenReturn(List.of(other));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.allocate(T, load.getId(), alloc("10000", "2000")));
        assertTrue(e.getMessage().contains("GP 12 AB"));
        when(deliveries.findTankerConflicts(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(deliveries.findDriverConflicts(any(), any(), any(), any(), any())).thenReturn(List.of(other));
        e = assertThrows(IllegalStateException.class, () -> service.allocate(T, load.getId(), alloc("10000", "2000")));
        assertTrue(e.getMessage().contains("Moses Sithole"));
        assertFalse(load.isAllocated());
    }

    @Test @DisplayName("only a scheduled load can be allocated; a terminated employee cannot be allocated")
    void stateAndDriver() {
        EmployeeSummary gone = new EmployeeSummary(UUID.randomUUID(), "E2", "A", "B", "A B", null, null, "Driver", null, "TERMINATED");
        when(hr.findEmployeeById(T, gone.id())).thenReturn(Optional.of(gone));
        assertThrows(IllegalStateException.class, () -> service.allocate(T, load.getId(),
                new AllocationRequest(tanker.getId(), gone.id(), List.of(new AllocationRequest.CompartmentLoad(c1.getId(), bd("12000"))))));
        load.cancel();
        assertThrows(IllegalStateException.class, () -> service.allocate(T, load.getId(), alloc("10000", "2000")));
    }

    @Test @DisplayName("another tenant's tanker, driver or load is not found")
    void tenantIsolation() {
        TenantId other = TenantId.of(UUID.randomUUID());
        when(deliveries.findActiveByIdForUpdate(eq(other), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.allocate(other, load.getId(), alloc("10000", "2000")));
        when(tankers.findActiveById(eq(other), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.deactivateTanker(other, tanker.getId()));
    }

    @Test @DisplayName("a tanker with open loads cannot be deactivated")
    void deactivate() {
        when(deliveries.countOpenForTanker(T, tanker.getId())).thenReturn(2L);
        assertThrows(IllegalStateException.class, () -> service.deactivateTanker(T, tanker.getId()));
        assertTrue(tanker.isActive());
    }

    @Test @DisplayName("a driver sees and works only their own loads; others look like 'not found'")
    void driverOwnership() {
        when(hr.findEmployeeByUserId(T, me)).thenReturn(Optional.of(driver));
        // load is allocated to someone else
        load.allocate(tanker.getId(), "GP 12 AB", UUID.randomUUID(), "Someone Else");
        assertThrows(ResourceNotFoundException.class, () -> service.myDispatch(T, me, load.getId()));
        assertThrows(ResourceNotFoundException.class, () -> service.myComplete(T, me, load.getId(),
                new CompleteDeliveryRequest(bd("100"), "R", null, null, null, false, null, null, UUID.randomUUID())));
        verify(podPolicy, never()).complete(any(), any(), any(), any());
        verify(arrivalChecks, never()).requireClear(any(), any());
        verify(arrivalChecks, never()).requireEligibleToDispatch(any(), any());
        // allocated to me
        FuelDelivery mine = FuelDelivery.create(T, UUID.randomUUID(), null, Map.of(), "DIESEL", bd("100"), bd("22"), Instant.now(), null, null);
        mine.allocate(tanker.getId(), "GP 12 AB", driver.id(), driver.fullName());
        when(deliveries.findActiveById(eq(T), eq(mine.getId()))).thenReturn(Optional.of(mine));
        service.myDispatch(T, me, mine.getId());
        verify(arrivalChecks).requireEligibleToDispatch(T, mine.getId());
        service.myComplete(T, me, mine.getId(), new CompleteDeliveryRequest(bd("100"), "R", null, null, null, false, null, null, UUID.randomUUID()));
        verify(podPolicy).complete(eq(T), eq(me), eq(mine.getId()), any());
    }

    @Test @DisplayName("a login not linked to an employee gets a clear message")
    void unlinkedLogin() {
        when(hr.findEmployeeByUserId(T, me)).thenReturn(Optional.empty());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.myLoads(T, me));
        assertTrue(e.getMessage().contains("linked to an employee"));
    }

    @Test @DisplayName("Stage 4: an expired certificate stops the allocation before anything is saved")
    void complianceBlocks() {
        doThrow(new IllegalStateException("Cannot allocate: GP 12 AB has an expired Roadworthy (expired 2026-10-01).")).when(compliance)
                .requireCompliantForAllocation(any(), any(), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> service.allocate(T, load.getId(), alloc("10000", "2000")));
        assertFalse(load.isAllocated());
        verify(loadSplits, never()).saveAll(any());
    }

    // ---- C04 part 2: stops on a route ----

    private FuelDelivery sibling(UUID routeId, String fuel, FuelTankerCompartment inComp, String litres) {
        FuelDelivery s = FuelDelivery.create(T, UUID.randomUUID(), UUID.randomUUID(), Map.of(), fuel, bd(litres), bd("22"), Instant.now(), null, null);
        s.joinRoute(routeId, 1);
        when(deliveries.findByRoute(T, routeId)).thenReturn(List.of(s));
        var hoisted1 = List.of(FuelDeliveryCompartment.create(T, s.getId(), inComp.getId(), bd(litres))); when(loadSplits.findByDelivery(T, s.getId())).thenReturn(hoisted1);
        return s;
    }

    @Test @DisplayName("C04: a stop is not in conflict with the other stops on its own route, but is with any other load")
    void routeSiblingsDoNotConflict() {
        UUID route = UUID.randomUUID();
        FuelDelivery s = sibling(route, "DIESEL", c2, "1000");
        when(deliveries.findTankerConflicts(any(), any(), any(), any(), any())).thenReturn(List.of(s));
        when(deliveries.findDriverConflicts(any(), any(), any(), any(), any())).thenReturn(List.of(s));
        service.allocate(T, load.getId(), alloc("10000", "2000"), route);
        verify(deliveries).save(load);
        // the same sibling is a conflict when the load is allocated on its own
        FuelDelivery other = FuelDelivery.create(T, UUID.randomUUID(), UUID.randomUUID(), Map.of(), "DIESEL", bd("500"), bd("22"), Instant.now(), null, null);
        when(deliveries.findTankerConflicts(any(), any(), any(), any(), any())).thenReturn(List.of(other));
        assertThrows(IllegalStateException.class, () -> service.allocate(T, load.getId(), alloc("10000", "2000"), route));
    }

    @Test @DisplayName("C04: stops together cannot overfill a compartment")
    void routeCompartmentCapacity() {
        UUID route = UUID.randomUUID();
        sibling(route, "DIESEL", c1, "4000");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.allocate(T, load.getId(), alloc("7000", "5000"), route));
        assertTrue(e.getMessage().contains("already use"));
        service.allocate(T, load.getId(), alloc("6000", "6000"), route); // 4000 + 6000 = 10000 fits
    }

    @Test @DisplayName("C04: a compartment cannot carry two fuel types on one route")
    void routeNoFuelMixing() {
        UUID route = UUID.randomUUID();
        sibling(route, "PETROL_95", c1, "1000");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.allocate(T, load.getId(), alloc("6000", "6000"), route));
        assertTrue(e.getMessage().contains("already carries PETROL_95"));
    }

    @Test @DisplayName("C04: a load that is a stop on a route cannot be allocated on its own")
    void routedLoadNeedsRoute() {
        load.joinRoute(UUID.randomUUID(), 1);
        assertThrows(IllegalStateException.class, () -> service.allocate(T, load.getId(), alloc("10000", "2000")));
        verify(deliveries, never()).save(any());
    }

    @Test @DisplayName("eligibility is checked last: a block from it leaves the load unallocated")
    void eligibilityBlockStopsAllocation() {
        doThrow(new IllegalStateException("This load cannot be allocated to GP 12 AB: not on the site's list")).when(eligibility).enforce(any(), any(), any(), any(), any(), any(), any());
        var e = assertThrows(IllegalStateException.class, () -> service.allocate(T, load.getId(), alloc("10000", "2000")));
        assertTrue(e.getMessage().contains("not on the site's list"));
        assertFalse(load.isAllocated());
        verify(deliveries, never()).save(any());
        verify(loadSplits, never()).saveAll(any());
    }

    @Test @DisplayName("the review reason and who is allocating are passed to the eligibility check")
    void reviewPassedThrough() {
        var who = new za.co.handyflow.platform.fuel.dto.EligibilityDtos.ReviewAuthority(true, UUID.randomUUID());
        var req = new AllocationRequest(tanker.getId(), driver.id(), List.of(
                new AllocationRequest.CompartmentLoad(c1.getId(), bd("10000")), new AllocationRequest.CompartmentLoad(c2.getId(), bd("2000"))), "Weight confirmed by phone");
        service.allocate(T, load.getId(), req, null, who);
        verify(eligibility).enforce(eq(T), eq(load), eq(tanker), eq(driver.id()), any(), eq("Weight confirmed by phone"), eq(who));
        assertTrue(load.isAllocated());
    }

    @Test @DisplayName("previewing changes nothing and returns what the eligibility check found")
    void preview() {
        var finding = new za.co.handyflow.platform.fuel.dto.EligibilityDtos.EligibilityResult(List.of(new za.co.handyflow.platform.fuel.dto.EligibilityDtos.Finding("X",
                za.co.handyflow.platform.fuel.dto.EligibilityDtos.Severity.REVIEW, "Check the weight")));
        when(eligibility.evaluate(any(), any(), any(), any(), any(), any(), any())).thenReturn(finding);
        assertSame(finding, service.previewEligibility(T, load.getId(), alloc("10000", "2000")));
        assertFalse(load.isAllocated());
        verify(eligibility, never()).enforce(any(), any(), any(), any(), any(), any(), any());
    }

    // ---- compartment view ----

    @Test @DisplayName("compartment loads: only this tanker's open loads for the day, split by compartment, in time order")
    void compartmentLoadsForOneTanker() {
        UUID comp = UUID.randomUUID();
        FuelDelivery mine = mock(FuelDelivery.class), other = mock(FuelDelivery.class), cancelled = mock(FuelDelivery.class), early = mock(FuelDelivery.class);
        for (var d : List.of(mine, other, cancelled, early)) { when(d.getId()).thenReturn(UUID.randomUUID()); when(d.getStatus()).thenReturn("SCHEDULED"); when(d.getFuelType()).thenReturn("DIESEL"); when(d.getScheduledAt()).thenReturn(Instant.parse("2026-11-18T10:00:00Z")); var hoisted2 = tanker.getId(); when(d.getTankerId()).thenReturn(hoisted2); }
        when(other.getTankerId()).thenReturn(UUID.randomUUID());
        when(cancelled.getStatus()).thenReturn("CANCELLED");
        when(early.getScheduledAt()).thenReturn(Instant.parse("2026-11-18T05:00:00Z"));
        when(tankers.findActiveById(T, tanker.getId())).thenReturn(Optional.of(tanker));
        when(deliveries.findScheduledBetween(eq(T), any(), any())).thenReturn(List.of(mine, other, cancelled, early));
        var hoisted3 = List.of(FuelDeliveryCompartment.create(T, mine.getId(), comp, bd("4000"))); when(loadSplits.findByDelivery(T, mine.getId())).thenReturn(hoisted3);
        var hoisted4 = List.of(FuelDeliveryCompartment.create(T, early.getId(), comp, bd("1000"))); when(loadSplits.findByDelivery(T, early.getId())).thenReturn(hoisted4);
        var out = service.compartmentLoads(T, tanker.getId(), java.time.LocalDate.of(2026, 11, 18));
        assertEquals(2, out.size());
        assertEquals(early.getId(), out.get(0).deliveryId());
        assertEquals(0, bd("4000").compareTo(out.get(1).litres()));
        verify(loadSplits, never()).findByDelivery(T, other.getId());
        verify(loadSplits, never()).findByDelivery(T, cancelled.getId());
    }

    @Test @DisplayName("compartment loads: an unknown tanker is refused")
    void compartmentLoadsUnknownTanker() {
        when(tankers.findActiveById(any(), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.compartmentLoads(T, UUID.randomUUID(), java.time.LocalDate.now()));
    }
}
