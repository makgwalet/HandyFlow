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
import za.co.handyflow.platform.fuel.domain.model.FuelDivision;
import za.co.handyflow.platform.fuel.domain.model.FuelDivisionActivation;
import za.co.handyflow.platform.fuel.domain.model.FuelOperatingProfile;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.FuelProfileDtos.ProfileView;
import za.co.handyflow.platform.fuel.dto.FuelProfileDtos.WizardRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static za.co.handyflow.platform.fuel.domain.model.FuelDivision.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelOperatingProfileServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID BY = UUID.randomUUID();

    @Mock FuelOperatingProfileRepository profiles;
    @Mock FuelDivisionActivationRepository activations;
    @Mock FuelDivisionGuard guard;
    @Mock FuelLegalEntityRepository legalEntities;
    @Mock FuelTankRepository tanks;
    @Mock FuelTankerRepository tankers;
    @Mock FuelPumpRepository pumps;
    @Mock FuelShiftRepository shifts;
    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelSalesOrderRepository orders;
    @Mock FuelReservationRepository reservations;
    @Mock FuelPurchaseOrderRepository purchaseOrders;
    @Mock FuelTruckStopStayRepository truckStopStays;
    @Mock FuelTruckStopService truckStop;
    @InjectMocks FuelOperatingProfileService service;

    private final List<FuelDivisionActivation> saved = new ArrayList<>();
    private FuelOperatingProfile stored;

    @BeforeEach
    void wire() {
        stored = null; saved.clear();
        when(profiles.findByTenant(T)).thenAnswer(i -> Optional.ofNullable(stored));
        when(profiles.save(any())).thenAnswer(i -> { stored = i.getArgument(0); return stored; });
        when(activations.findByTenant(T)).thenAnswer(i -> new ArrayList<>(saved));
        when(activations.save(any())).thenAnswer(i -> { FuelDivisionActivation a = i.getArgument(0); if (!saved.contains(a)) saved.add(a); return a; });
        when(guard.activeDivisions(T)).thenAnswer(i -> {
            if (stored == null) return EnumSet.noneOf(FuelDivision.class);
            Set<FuelDivision> s = EnumSet.noneOf(FuelDivision.class);
            for (FuelDivisionActivation a : saved) if (a.isActive()) s.add(a.getDivision());
            return s;
        });
        when(legalEntities.findAllActive(T)).thenReturn(List.of());
        when(tanks.findAllActive(T)).thenReturn(List.of());
        when(reservations.findLive(any(), any())).thenReturn(List.of());
    }

    @Test @DisplayName("a tenant with nothing saved is NOT_STARTED with no division, and viewing writes nothing")
    void notStarted() {
        ProfileView v = service.view(T);
        assertEquals("NOT_STARTED", v.status());
        assertTrue(v.activeDivisions().isEmpty());
        assertEquals(4, v.divisions().size());
        verify(profiles, never()).save(any());
    }

    @Test @DisplayName("saving wizard progress moves to IN_PROGRESS, activates nothing, and resumes where it stopped")
    void saveAndResume() {
        service.saveWizard(T, new WizardRequest("MEDIUM", "ONE_ENTITY", 3, "{\"divisions\":[\"DISTRIBUTION\"]}"));
        ProfileView v = service.view(T);
        assertEquals("IN_PROGRESS", v.status());
        assertEquals(3, v.wizardStep());
        assertEquals("MEDIUM", v.businessSize());
        assertTrue(v.activeDivisions().isEmpty());
        assertTrue(v.wizardAnswers().contains("DISTRIBUTION"));
    }

    @Test @DisplayName("invalid size, structure or step is rejected")
    void wizardValidation() {
        assertThrows(IllegalArgumentException.class, () -> service.saveWizard(T, new WizardRequest("HUGE", null, 1, null)));
        assertThrows(IllegalArgumentException.class, () -> service.saveWizard(T, new WizardRequest(null, "MANY", 1, null)));
        assertThrows(IllegalArgumentException.class, () -> service.saveWizard(T, new WizardRequest(null, null, 99, null)));
    }

    @Test @DisplayName("activating needs at least one division and turns exactly those on")
    void activate() {
        assertThrows(IllegalArgumentException.class, () -> service.activate(T, List.of(), BY));
        ProfileView v = service.activate(T, List.of(WHOLESALE_SUPPLY, TRUCK_STOP), BY);
        assertEquals("ACTIVE", v.status());
        assertFalse(v.legacyMode());
        assertEquals(Set.of(WHOLESALE_SUPPLY, TRUCK_STOP), new HashSet<>(v.activeDivisions()));
        assertEquals(2, saved.size());
    }

    @Test @DisplayName("activating twice with the same divisions changes nothing")
    void activateIdempotent() {
        service.activate(T, List.of(DISTRIBUTION), BY);
        service.activate(T, List.of(DISTRIBUTION), BY);
        assertEquals(1, saved.size());
        assertTrue(saved.get(0).isActive());
    }

    @Test @DisplayName("adding a division later keeps the existing one and the history")
    void addLater() {
        service.activate(T, List.of(WHOLESALE_SUPPLY), BY);
        service.activateDivision(T, DISTRIBUTION, BY);
        assertEquals(Set.of(WHOLESALE_SUPPLY, DISTRIBUTION), new HashSet<>(service.view(T).activeDivisions()));
        service.activateDivision(T, DISTRIBUTION, BY); // already on
        assertEquals(2, saved.size());
    }

    @Test @DisplayName("divisions cannot be switched one at a time before setup is completed")
    void needsSetup() {
        assertThrows(IllegalStateException.class, () -> service.activateDivision(T, RETAIL_FORECOURT, BY));
        assertThrows(IllegalStateException.class, () -> service.deactivateDivision(T, RETAIL_FORECOURT, "not needed here", BY));
    }

    @Test @DisplayName("deactivation needs a reason, and the last division cannot be turned off")
    void deactivateRules() {
        service.activate(T, List.of(WHOLESALE_SUPPLY, DISTRIBUTION), BY);
        assertThrows(IllegalArgumentException.class, () -> service.deactivateDivision(T, DISTRIBUTION, "no", BY));
        service.deactivateDivision(T, DISTRIBUTION, "We stopped delivering ourselves", BY);
        assertEquals(Set.of(WHOLESALE_SUPPLY), new HashSet<>(service.view(T).activeDivisions()));
        assertThrows(IllegalStateException.class, () -> service.deactivateDivision(T, WHOLESALE_SUPPLY, "close everything", BY));
        // history kept: the row is still there, inactive, with who and why
        FuelDivisionActivation d = saved.stream().filter(a -> a.getDivision() == DISTRIBUTION).findFirst().orElseThrow();
        assertFalse(d.isActive());
        assertNotNull(d.getDeactivatedAt());
        assertEquals("We stopped delivering ourselves", d.getDeactivationReason());
    }

    @Test @DisplayName("Retail cannot be switched off while a shift is open")
    void retailBlockedByOpenShift() {
        service.activate(T, List.of(WHOLESALE_SUPPLY, RETAIL_FORECOURT), BY);
        when(shifts.countOpen(T)).thenReturn(2L);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.deactivateDivision(T, RETAIL_FORECOURT, "closing the site", BY));
        assertTrue(e.getMessage().contains("2 forecourt shift"));
        when(shifts.countOpen(T)).thenReturn(0L);
        assertDoesNotThrow(() -> service.deactivateDivision(T, RETAIL_FORECOURT, "closing the site", BY));
    }

    @Test @DisplayName("open deliveries and orders only block when neither Wholesale nor Distribution would remain")
    void sharedCommercialWork() {
        service.activate(T, List.of(WHOLESALE_SUPPLY, DISTRIBUTION), BY);
        when(deliveries.countOpen(T)).thenReturn(3L);
        when(orders.countByStatus(any(), any())).thenReturn(1L);
        assertDoesNotThrow(() -> service.deactivateDivision(T, DISTRIBUTION, "outsourced delivery", BY));   // Wholesale remains, so orders and deliveries still have a home
        service.activateDivision(T, RETAIL_FORECOURT, BY);
        // now leaving Wholesale as well would strand them: Retail alone does not run orders
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.deactivateDivision(T, WHOLESALE_SUPPLY, "retail only now", BY));
        assertTrue(e.getMessage().contains("delivery"));
        assertTrue(e.getMessage().contains("order"));
    }

    @Test @DisplayName("purchase orders waiting for delivery block leaving procurement; reservations block leaving commercial work")
    void procurementAndReservations() {
        service.activate(T, List.of(WHOLESALE_SUPPLY, TRUCK_STOP), BY);
        when(purchaseOrders.countAwaitingDelivery(T)).thenReturn(1L);
        assertThrows(IllegalStateException.class, () -> service.deactivateDivision(T, WHOLESALE_SUPPLY, "truck stop only", BY));
        when(purchaseOrders.countAwaitingDelivery(T)).thenReturn(0L);
        when(reservations.findLive(any(), any())).thenReturn(List.of(mock(za.co.handyflow.platform.fuel.domain.model.FuelReservation.class)));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.deactivateDivision(T, WHOLESALE_SUPPLY, "truck stop only", BY));
        assertTrue(e.getMessage().contains("reservation"));
    }

    @Test @DisplayName("completing setup for a legacy tenant refuses to drop a division that still has open work")
    void leavingLegacyChecksOpenWork() {
        stored = FuelOperatingProfile.forTenant(T);
        try { var f = FuelOperatingProfile.class.getDeclaredField("legacyMode"); f.setAccessible(true); f.set(stored, true); } catch (Exception e) { throw new RuntimeException(e); }
        when(guard.activeDivisions(T)).thenReturn(EnumSet.allOf(FuelDivision.class));
        when(shifts.countOpen(T)).thenReturn(1L);
        assertThrows(IllegalStateException.class, () -> service.activate(T, List.of(WHOLESALE_SUPPLY, DISTRIBUTION), BY));
        assertTrue(stored.isLegacyMode(), "nothing changed on refusal");
        when(shifts.countOpen(T)).thenReturn(0L);
        service.activate(T, List.of(WHOLESALE_SUPPLY, DISTRIBUTION), BY);
        assertFalse(stored.isLegacyMode());
    }

    @Test @DisplayName("the checklist shows what is left to set up for the active divisions only")
    void checklist() {
        service.activate(T, List.of(DISTRIBUTION), BY);
        var keys = service.view(T).checklist().stream().map(c -> c.key()).toList();
        assertTrue(keys.contains("tankers"));
        assertFalse(keys.contains("pumps"));
        assertTrue(service.view(T).checklist().stream().filter(c -> c.key().equals("divisions")).findFirst().orElseThrow().done());
        assertFalse(service.view(T).checklist().stream().filter(c -> c.key().equals("legal-entity")).findFirst().orElseThrow().done());
    }

    @Test @DisplayName("Truck Stop cannot be switched off while a stay is booked or in the yard, or a reservation is open")
    void truckStopBlockedByOpenWork() {
        service.activate(T, List.of(WHOLESALE_SUPPLY, TRUCK_STOP), BY);
        when(truckStopStays.countOpen(T)).thenReturn(2L);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.deactivateDivision(T, TRUCK_STOP, "closing the yard", BY));
        assertTrue(e.getMessage().contains("2 overnight stay(s)"));
        when(truckStopStays.countOpen(T)).thenReturn(0L);
        when(truckStop.openReservationCount(T)).thenReturn(1);
        e = assertThrows(IllegalStateException.class, () -> service.deactivateDivision(T, TRUCK_STOP, "closing the yard", BY));
        assertTrue(e.getMessage().contains("1 truck stop reservation(s)"));
    }

    @Test @DisplayName("Truck Stop switches off once nothing is open")
    void truckStopOffWhenClear() {
        service.activate(T, List.of(WHOLESALE_SUPPLY, TRUCK_STOP), BY);
        when(truckStopStays.countOpen(T)).thenReturn(0L);
        when(truckStop.openReservationCount(T)).thenReturn(0);
        assertFalse(service.deactivateDivision(T, TRUCK_STOP, "closing the yard", BY).activeDivisions().contains(TRUCK_STOP));
    }
}
