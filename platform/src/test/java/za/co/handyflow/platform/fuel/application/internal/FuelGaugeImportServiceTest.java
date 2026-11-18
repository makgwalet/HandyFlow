package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.DipReadingRequest;
import za.co.handyflow.platform.fuel.dto.DipReadingResponse;
import za.co.handyflow.platform.fuel.dto.GaugeImportDtos.*;
import za.co.handyflow.platform.fuel.dto.PosImportDtos.IngestResult;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Tank-gauge feed: staged on receipt, a person accepts it as a dip reading. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelGaugeImportServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();
    static BigDecimal d(String s) { return new BigDecimal(s); }

    @Mock FuelPosImportService keyAuth;
    @Mock FuelGaugeReadingRepository readings;
    @Mock FuelImportTankAliasRepository aliases;
    @Mock FuelTankRepository tanks;
    @Mock FuelService fuel;
    @Mock FeatureGuard featureGuard;
    FuelGaugeImportService svc;
    FuelImportKey key;
    FuelTank tank;

    @BeforeEach void setUp() {
        svc = new FuelGaugeImportService(keyAuth, readings, aliases, tanks, fuel, featureGuard);
        key = FuelImportKey.create(T, "Gauge", FuelPosImportService.hashKey("hf_fuel_k"), "hf_fuel_k", USER);
        when(keyAuth.authenticate("hf_fuel_k")).thenReturn(key);
        when(featureGuard.hasModuleFor(T, "fuel")).thenReturn(true);
        tank = mock(FuelTank.class);
        when(tank.getId()).thenReturn(UUID.randomUUID()); when(tank.getName()).thenReturn("Diesel 1");
        when(tanks.findAllActive(T)).thenReturn(List.of(tank));
        when(tanks.findActiveById(T, tank.getId())).thenReturn(Optional.of(tank));
        when(readings.findByRef(eq(T), any(), any())).thenReturn(Optional.empty());
        when(readings.save(any(FuelGaugeReading.class))).thenAnswer(i -> i.getArgument(0));
    }

    private IncomingGauge gauge(String ref, String tankLabel, String litres) {
        return new IncomingGauge("ACME-GAUGE", ref, tankLabel, d(litres), Instant.now().minusSeconds(60), "Probe 7");
    }

    @Test @DisplayName("a reading for a known tank is staged as PENDING and records nothing")
    void stages() {
        IngestResult r = svc.ingest("hf_fuel_k", gauge("G1", "diesel 1", "8200"));
        assertEquals("PENDING", r.status());
        assertTrue(r.problems().isEmpty());
        verifyNoInteractions(fuel);
    }

    @Test @DisplayName("an unknown tank is staged with a problem listed")
    void unknownTank() {
        IngestResult r = svc.ingest("hf_fuel_k", gauge("G2", "T-9", "100"));
        assertEquals(1, r.problems().size());
        assertTrue(r.problems().get(0).contains("T-9"));
    }

    @Test @DisplayName("a label mapped for the source finds the tank")
    void aliasFindsTank() {
        var hoisted1 = List.of(FuelImportTankAlias.of(T, "acme-gauge", "T-9", tank.getId(), USER)); when(aliases.findBySource(T, "acme-gauge")).thenReturn(hoisted1);
        assertTrue(svc.ingest("hf_fuel_k", gauge("G3", "t-9", "100")).problems().isEmpty());
    }

    @Test @DisplayName("a reading stamped well in the future is refused")
    void future() {
        var in = new IncomingGauge("ACME-GAUGE", "G4", "Diesel 1", d("100"), Instant.now().plusSeconds(3600), null);
        assertThrows(IllegalArgumentException.class, () -> svc.ingest("hf_fuel_k", in));
        verify(readings, never()).save(any());
    }

    @Test @DisplayName("the same reading again returns the original; different content under the same id is a 409")
    void idempotent() {
        ArgumentCaptor<FuelGaugeReading> cap = ArgumentCaptor.forClass(FuelGaugeReading.class);
        var in = gauge("G5", "Diesel 1", "8200");
        svc.ingest("hf_fuel_k", in);
        verify(readings).save(cap.capture());
        var hoisted2 = Optional.of(cap.getValue()); when(readings.findByRef(T, "ACME-GAUGE", "G5")).thenReturn(hoisted2);
        assertTrue(svc.ingest("hf_fuel_k", in).duplicate());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> svc.ingest("hf_fuel_k", new IncomingGauge("ACME-GAUGE", "G5", "Diesel 1", d("9000"), in.readAt(), "Probe 7")));
        assertEquals(409, e.getStatus().value());
    }

    @Test @DisplayName("accepting records an ordinary dip reading with the sender's level and time, and marks the reading accepted")
    void accepts() {
        FuelGaugeReading g = FuelGaugeReading.receive(T, key.getId(), "acme-gauge", "G6", "Diesel 1", d("8200"), Instant.parse("2026-11-17T08:00:00Z"), "Probe 7", "h", null);
        when(readings.findOwned(T, g.getId())).thenReturn(Optional.of(g));
        DipReadingResponse dip = mock(DipReadingResponse.class); UUID dipId = UUID.randomUUID(); when(dip.id()).thenReturn(dipId);
        UUID tankIdForDip = tank.getId();
        when(fuel.recordDipReading(eq(T), eq(tankIdForDip), any(DipReadingRequest.class))).thenReturn(dip);
        GaugeView v = svc.accept(T, g.getId(), new AcceptRequest("checked on site"), USER);
        assertEquals("ACCEPTED", v.status());
        assertEquals(dipId, v.dipReadingId());
        ArgumentCaptor<DipReadingRequest> cap = ArgumentCaptor.forClass(DipReadingRequest.class);
        verify(fuel).recordDipReading(eq(T), eq(tank.getId()), cap.capture());
        assertEquals(0, d("8200").compareTo(cap.getValue().actualLitres()));
        assertEquals(Instant.parse("2026-11-17T08:00:00Z"), cap.getValue().readAt());
    }

    @Test @DisplayName("accepting with an unknown tank is refused and the reading stays pending; a decided reading cannot be decided again")
    void acceptRefused() {
        FuelGaugeReading g = FuelGaugeReading.receive(T, key.getId(), "acme-gauge", "G7", "T-9", d("1"), Instant.now(), null, "h", null);
        when(readings.findOwned(T, g.getId())).thenReturn(Optional.of(g));
        assertThrows(IllegalArgumentException.class, () -> svc.accept(T, g.getId(), null, USER));
        assertTrue(g.isPending());
        verifyNoInteractions(fuel);
        svc.reject(T, g.getId(), "wrong site", USER);
        assertThrows(IllegalStateException.class, () -> svc.accept(T, g.getId(), null, USER));
        assertThrows(IllegalStateException.class, () -> svc.reject(T, g.getId(), "again please", USER));
    }

    @Test @DisplayName("a rejection needs a reason of at least 5 characters")
    void rejectReason() {
        FuelGaugeReading g = FuelGaugeReading.receive(T, key.getId(), "acme-gauge", "G8", "Diesel 1", d("1"), Instant.now(), null, "h", null);
        when(readings.findOwned(T, g.getId())).thenReturn(Optional.of(g));
        assertThrows(IllegalArgumentException.class, () -> svc.reject(T, g.getId(), "no", USER));
    }

    @Test @DisplayName("tank labels need an active tank and a label not already mapped; removing a missing one is not-found")
    void labels() {
        var v = svc.addAlias(T, USER, new AliasRequest(" ACME-GAUGE ", " T-9 ", tank.getId()));
        assertEquals("acme-gauge", v.source()); assertEquals("t-9", v.alias()); assertEquals("Diesel 1", v.tankName());
        assertThrows(ResourceNotFoundException.class, () -> svc.addAlias(T, USER, new AliasRequest("acme-gauge", "x", UUID.randomUUID())));
        var hoisted3 = Optional.of(mock(FuelImportTankAlias.class)); when(aliases.findOne(T, "acme-gauge", "t-9")).thenReturn(hoisted3);
        assertThrows(IllegalStateException.class, () -> svc.addAlias(T, USER, new AliasRequest("acme-gauge", "T-9", tank.getId())));
        when(aliases.findOwned(eq(T), any())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> svc.removeAlias(T, UUID.randomUUID()));
    }
}
