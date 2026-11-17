package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.fuel.domain.repository.*;
import za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.MatchingReport;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Matching a forecourt's receipts to the dispatches that supplied it. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelDispatchMatchServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelDispatchRepository dispatches;
    @Mock FuelReceiptRepository receipts;
    @Mock FuelDispatchReceiptMatchRepository matches;
    @Mock FuelTankRepository tanks;
    @Mock FuelDepotRepository depots;
    FuelDispatchMatchService svc;

    FuelDepot terminal, fc;
    FuelTank tTerminal, tFc;

    @BeforeEach void setUp() {
        svc = new FuelDispatchMatchService(dispatches, receipts, matches, tanks, depots);
        terminal = depot("Terminal"); fc = depot("Forecourt");
        tTerminal = tank(terminal, "Terminal diesel", "DIESEL"); tFc = tank(fc, "FC diesel", "DIESEL");
        when(depots.findAllActive(T)).thenReturn(List.of(terminal, fc));
        when(tanks.findAllActive(T)).thenReturn(List.of(tTerminal, tFc));
        when(dispatches.findToDepotsBetween(eq(T), any(), any(), any())).thenReturn(List.of());
        when(receipts.findReceivedBetween(eq(T), any(), any())).thenReturn(List.of());
        when(matches.findForAny(eq(T), any(), any())).thenReturn(List.of());
        when(matches.findByReceipt(eq(T), any())).thenReturn(Optional.empty());
    }

    private FuelDepot depot(String name) {
        FuelDepot d = mock(FuelDepot.class);
        when(d.getId()).thenReturn(UUID.randomUUID()); when(d.getName()).thenReturn(name);
        return d;
    }
    private FuelTank tank(FuelDepot d, String name, String fuel) {
        FuelTank t = mock(FuelTank.class);
        UUID id = UUID.randomUUID();
        when(t.getId()).thenReturn(id); when(t.getDepotId()).thenReturn(d.getId()); when(t.getName()).thenReturn(name); when(t.getFuelType()).thenReturn(fuel);
        when(tanks.findActiveById(T, id)).thenReturn(Optional.of(t));
        return t;
    }
    private FuelDispatch dispatch(String litres, UUID dest) {
        FuelDispatch d = mock(FuelDispatch.class);
        UUID id = UUID.randomUUID();
        when(d.getId()).thenReturn(id); when(d.getTankId()).thenReturn(tTerminal.getId()); when(d.getLitresDispensed()).thenReturn(bd(litres));
        when(d.getDestinationDepotId()).thenReturn(dest); when(d.getDispatchedAt()).thenReturn(Instant.now().minusSeconds(7200)); when(d.isReversed()).thenReturn(false);
        when(dispatches.findActiveById(T, id)).thenReturn(Optional.of(d));
        return d;
    }
    private FuelReceipt receipt(FuelTank t, String litres) {
        FuelReceipt r = mock(FuelReceipt.class);
        UUID id = UUID.randomUUID();
        when(r.getId()).thenReturn(id); when(r.getTankId()).thenReturn(t.getId()); when(r.getLitresReceived()).thenReturn(bd(litres));
        when(r.getReceivedAt()).thenReturn(Instant.now().minusSeconds(3600)); when(r.getReversedAt()).thenReturn(null);
        when(receipts.findActiveById(T, id)).thenReturn(Optional.of(r));
        return r;
    }
    private FuelDispatchReceiptMatch link(FuelDispatch d, FuelReceipt r) {
        FuelDispatchReceiptMatch m = mock(FuelDispatchReceiptMatch.class);
        when(m.getId()).thenReturn(UUID.randomUUID()); when(m.getDispatchId()).thenReturn(d.getId()); when(m.getReceiptId()).thenReturn(r.getId());
        return m;
    }

    @Test @DisplayName("a linked dispatch shows litres sent, litres received and the difference; open dispatches and receipts are listed apart")
    void report() {
        FuelDispatch linked = dispatch("10000", fc.getId()), open = dispatch("5000", fc.getId());
        FuelReceipt got = receipt(tFc, "9900"), stray = receipt(tFc, "300");
        when(dispatches.findToDepotsBetween(eq(T), any(), any(), any())).thenReturn(List.of(linked, open));
        when(receipts.findReceivedBetween(eq(T), any(), any())).thenReturn(List.of(got, stray));
        when(matches.findForAny(eq(T), any(), any())).thenReturn(List.of(link(linked, got)));
        MatchingReport r = svc.report(T, 30, null);
        assertEquals(1, r.forecourts().size());
        var f = r.forecourts().get(0);
        assertEquals(1, f.matched().size());
        assertEquals(0, bd("100").compareTo(f.matched().get(0).difference()));
        assertEquals(1, f.openDispatches().size());
        assertEquals(0, bd("5000").compareTo(f.openDispatches().get(0).litres()));
        assertEquals(1, f.openReceipts().size());
        assertEquals(0, bd("300").compareTo(f.openReceipts().get(0).litres()));
        assertEquals(0, bd("15000").compareTo(f.dispatchedLitres()));
        assertEquals(0, bd("9900").compareTo(f.receivedLitres()));
    }

    @Test @DisplayName("a depot limit hides forecourts the user may not see")
    void limited() {
        FuelDispatch d = dispatch("100", fc.getId());
        when(dispatches.findToDepotsBetween(eq(T), eq(Set.of(terminal.getId())), any(), any())).thenReturn(List.of());
        assertTrue(svc.report(T, 30, Set.of(terminal.getId())).forecourts().isEmpty());
        assertTrue(svc.report(T, 30, Set.of()).forecourts().isEmpty());
    }

    @Test @DisplayName("matching saves a link when destination, tank and fuel type agree")
    void matchOk() {
        FuelDispatch d = dispatch("1000", fc.getId()); FuelReceipt r = receipt(tFc, "990");
        svc.match(T, d.getId(), r.getId(), UUID.randomUUID(), null);
        verify(matches).save(any(FuelDispatchReceiptMatch.class));
    }

    @Test @DisplayName("matching is refused for another forecourt, another fuel, a linked receipt or a reversed side")
    void matchRefused() {
        FuelDispatch d = dispatch("1000", fc.getId());
        FuelTank otherTank = tank(terminal, "Other", "DIESEL");
        assertThrows(IllegalArgumentException.class, () -> svc.match(T, d.getId(), receipt(otherTank, "10").getId(), null, null));
        FuelTank petrol = tank(fc, "FC petrol", "PETROL");
        assertThrows(IllegalArgumentException.class, () -> svc.match(T, d.getId(), receipt(petrol, "10").getId(), null, null));
        FuelReceipt linked = receipt(tFc, "10");
        when(matches.findByReceipt(T, linked.getId())).thenReturn(Optional.of(mock(FuelDispatchReceiptMatch.class)));
        assertThrows(IllegalStateException.class, () -> svc.match(T, d.getId(), linked.getId(), null, null));
        FuelReceipt rev = receipt(tFc, "10"); when(rev.getReversedAt()).thenReturn(Instant.now());
        assertThrows(IllegalStateException.class, () -> svc.match(T, d.getId(), rev.getId(), null, null));
        FuelDispatch noDest = dispatch("5", null);
        assertThrows(IllegalArgumentException.class, () -> svc.match(T, noDest.getId(), receipt(tFc, "5").getId(), null, null));
        verify(matches, never()).save(any());
    }

    @Test @DisplayName("a limited user cannot match for a forecourt outside their depots")
    void scope() {
        FuelDispatch d = dispatch("1000", fc.getId()); FuelReceipt r = receipt(tFc, "990");
        assertThrows(za.co.handyflow.platform.shared.HandyFlowException.class, () -> svc.match(T, d.getId(), r.getId(), null, Set.of(terminal.getId())));
        svc.match(T, d.getId(), r.getId(), null, Set.of(fc.getId()));
        verify(matches, times(1)).save(any());
    }

    @Test @DisplayName("a dispatch delivered by stock transfer counts as received in full, needs no receipt and is flagged byTransfer")
    void deliveredByTransferIsAlreadyReceived() {
        FuelDispatch d = dispatch("1000", fc.getId());
        when(d.isDeliveredByTransfer()).thenReturn(true);
        when(dispatches.findToDepotsBetween(eq(T), any(), any(), any())).thenReturn(List.of(d));
        var f = svc.report(T, 7, null).forecourts().stream().filter(x -> x.name().equals("Forecourt")).findFirst().orElseThrow();
        assertEquals(1, f.matched().size());
        assertTrue(f.matched().get(0).byTransfer());
        assertEquals(0, bd("1000").compareTo(f.receivedLitres()));
        assertEquals(0, BigDecimal.ZERO.compareTo(f.matched().get(0).difference()));
        assertTrue(f.openDispatches().isEmpty());
    }

    @Test @DisplayName("a receipt cannot be linked to a dispatch that was already delivered by transfer, or the fuel would count twice")
    void cannotLinkReceiptToTransferDelivery() {
        FuelDispatch d = dispatch("1000", fc.getId());
        when(d.isDeliveredByTransfer()).thenReturn(true);
        FuelReceipt r = receipt(tFc, "1000");
        assertThrows(IllegalStateException.class, () -> svc.match(T, d.getId(), r.getId(), UUID.randomUUID(), null));
        verify(matches, never()).save(any());
    }
}
