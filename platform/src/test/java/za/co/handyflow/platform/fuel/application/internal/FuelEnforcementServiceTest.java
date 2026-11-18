package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.audittrail.application.AuditTrailFacade;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.EnforcementDtos.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FuelEnforcementServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final String C = "ARRIVAL_CHECK";
    static final UUID USER = UUID.randomUUID(), DEPOT = UUID.randomUUID(), TANK = UUID.randomUUID();
    static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");

    FuelEnforcementRepository states = mock(FuelEnforcementRepository.class);
    FuelEnforcementObservationRepository obs = mock(FuelEnforcementObservationRepository.class);
    FuelDeliveryRepository deliveries = mock(FuelDeliveryRepository.class);
    FuelTankRepository tanks = mock(FuelTankRepository.class);
    FuelDepotRepository depots = mock(FuelDepotRepository.class);
    FuelSettingsService settings = mock(FuelSettingsService.class);
    AuditTrailFacade audit = mock(AuditTrailFacade.class);
    FuelEnforcementService service;
    FuelEnforcement row;

    @BeforeEach
    void setUp() {
        service = new FuelEnforcementService(states, obs, deliveries, tanks, depots, settings, audit);
        when(states.save(any())).thenAnswer(i -> i.getArgument(0));
        when(settings.current(T)).thenReturn(FuelSettings.forTenant(T));
        FuelDepot depot = mock(FuelDepot.class);
        when(depots.findActiveById(eq(T), eq(DEPOT))).thenReturn(Optional.of(depot));
        FuelTank tank = mock(FuelTank.class);
        when(tank.getDepotId()).thenReturn(DEPOT);
        when(tanks.findActiveById(eq(T), eq(TANK))).thenReturn(Optional.of(tank));
    }

    private void in(String state, UUID... pilotDepots) { in(state, NOW.minusSeconds(10L * 86400), pilotDepots); }

    private void in(String state, Instant since, UUID... pilotDepots) {
        row = FuelEnforcement.off(T, C);
        row.change(state, List.of(pilotDepots), "setup", USER, since);
        when(states.find(T, C)).thenReturn(Optional.of(row));
    }

    private static ChangeRequest to(String state, UUID... depots) { return new ChangeRequest(state, List.of(depots), "Rolling out the arrival check"); }

    @Test @DisplayName("no row means off; monitor watches; enforced enforces; a pilot enforces only at its depots and watches elsewhere")
    void modes() {
        when(states.find(T, C)).thenReturn(Optional.empty());
        assertEquals(FuelEnforcementService.Mode.OFF, service.mode(T, C, TANK));
        in("MONITOR");
        assertEquals(FuelEnforcementService.Mode.MONITOR, service.mode(T, C, TANK));
        in("ENFORCED");
        assertEquals(FuelEnforcementService.Mode.ENFORCE, service.mode(T, C, TANK));
        in("PILOT", DEPOT);
        assertEquals(FuelEnforcementService.Mode.ENFORCE, service.mode(T, C, TANK));
        assertEquals(FuelEnforcementService.Mode.MONITOR, service.mode(T, C, UUID.randomUUID()));
        assertEquals(FuelEnforcementService.Mode.MONITOR, service.mode(T, C, null));
        in("PILOT", UUID.randomUUID());
        assertEquals(FuelEnforcementService.Mode.MONITOR, service.mode(T, C, TANK));
    }

    @Test @DisplayName("a control is rolled out one stage at a time: off cannot jump to enforced")
    void oneStageAtATime() {
        when(states.find(T, C)).thenReturn(Optional.empty());
        Blockers b = service.blockers(T, C, "ENFORCED", null, "Because", NOW);
        assertFalse(b.allowed());
        assertTrue(b.blockers().get(0).contains("Move to MONITOR first"));
        assertThrows(IllegalStateException.class, () -> service.change(T, USER, C, to("ENFORCED"), NOW));
        verify(states, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("off to monitor is allowed with a reason, and is audited with before and after")
    void startMonitoring() {
        when(states.find(T, C)).thenReturn(Optional.empty());
        ControlState out = service.change(T, USER, C, to("MONITOR"), NOW);
        assertEquals("MONITOR", out.state());
        verify(audit).record(eq(T), eq("FUEL_ENFORCEMENT_CHANGED"), eq("FuelEnforcement"), any(), any(), any());
    }

    @Test @DisplayName("a reason is always needed")
    void reasonNeeded() {
        when(states.find(T, C)).thenReturn(Optional.empty());
        assertTrue(service.blockers(T, C, "MONITOR", null, " ", NOW).blockers().stream().anyMatch(m -> m.contains("reason")));
    }

    @Test @DisplayName("a stage must have run for the minimum time before the next, and a pilot needs real depots")
    void minimumTimeAndScope() {
        in("MONITOR", NOW.minusSeconds(2L * 86400));
        Blockers early = service.blockers(T, C, "PILOT", List.of(DEPOT), "Start pilot", NOW);
        assertTrue(early.blockers().stream().anyMatch(m -> m.contains("at least 7 days")));
        in("MONITOR");
        assertTrue(service.blockers(T, C, "PILOT", List.of(), "Start pilot", NOW).blockers().stream().anyMatch(m -> m.contains("Choose the depots")));
        assertTrue(service.blockers(T, C, "PILOT", List.of(UUID.randomUUID()), "Start pilot", NOW).blockers().stream().anyMatch(m -> m.contains("not found")));
        assertTrue(service.blockers(T, C, "PILOT", List.of(DEPOT), "Start pilot", NOW).allowed());
    }

    @Test @DisplayName("enforcing is blocked while too many recent deliveries would have been refused")
    void refusalShare() {
        in("PILOT", DEPOT);
        when(deliveries.countDeliveredSince(eq(T), any())).thenReturn(100L);
        when(obs.countSince(eq(T), eq(C), any())).thenReturn(20L);
        Blockers b = service.blockers(T, C, "ENFORCED", null, "Go live", NOW);
        assertFalse(b.allowed());
        assertTrue(b.blockers().get(0).contains("20 deliveries would have been refused"));
        when(obs.countSince(eq(T), eq(C), any())).thenReturn(3L);
        assertTrue(service.blockers(T, C, "ENFORCED", null, "Go live", NOW).allowed());
    }

    @Test @DisplayName("enforcing sets the older switch; rolling back always works and clears it")
    void enforceAndRollBack() {
        in("PILOT", DEPOT);
        when(deliveries.countDeliveredSince(eq(T), any())).thenReturn(100L);
        when(obs.countSince(eq(T), eq(C), any())).thenReturn(0L);
        service.change(T, USER, C, to("ENFORCED"), NOW);
        verify(settings).updateArrivalPolicy(eq(T), argThat(r -> r.enforced()));

        FuelSettings on = FuelSettings.forTenant(T); on.updateArrivalCheckPolicy(null, true);
        when(settings.current(T)).thenReturn(on);
        ControlState back = service.change(T, USER, C, new ChangeRequest("MONITOR", null, "Too many refusals at the gate"), NOW);
        assertEquals("MONITOR", back.state());
        verify(settings).updateArrivalPolicy(eq(T), argThat(r -> !r.enforced()));
    }

    @Test @DisplayName("an unknown control or state is refused")
    void unknowns() {
        assertFalse(service.blockers(T, "NOPE", "MONITOR", null, "Because", NOW).allowed());
        when(states.find(T, C)).thenReturn(Optional.empty());
        assertFalse(service.blockers(T, C, "SOMETIMES", null, "Because", NOW).allowed());
    }
}
