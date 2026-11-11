package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
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

    @Test @DisplayName("allocating sets tanker, driver and plate and stores the compartment split")
    void allocates() {
        service.allocate(T, load.getId(), alloc("10000", "2000"));
        assertTrue(load.isAllocated());
        assertEquals("GP 12 AB", load.getVehicleReg());
        assertEquals("Moses Sithole", load.getDriverName());
        verify(loadSplits).deleteByDelivery(T, load.getId());
        verify(loadSplits).saveAll(any());
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
        verify(fuelService, never()).completeDelivery(any(), any(), any());
        // allocated to me
        FuelDelivery mine = FuelDelivery.create(T, UUID.randomUUID(), null, Map.of(), "DIESEL", bd("100"), bd("22"), Instant.now(), null, null);
        mine.allocate(tanker.getId(), "GP 12 AB", driver.id(), driver.fullName());
        when(deliveries.findActiveById(eq(T), eq(mine.getId()))).thenReturn(Optional.of(mine));
        service.myComplete(T, me, mine.getId(), new CompleteDeliveryRequest(bd("100"), "R", null, null, null, false, null, null, UUID.randomUUID()));
        verify(fuelService).completeDelivery(eq(T), eq(mine.getId()), any());
    }

    @Test @DisplayName("a login not linked to an employee gets a clear message")
    void unlinkedLogin() {
        when(hr.findEmployeeByUserId(T, me)).thenReturn(Optional.empty());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.myLoads(T, me));
        assertTrue(e.getMessage().contains("linked to an employee"));
    }
}
