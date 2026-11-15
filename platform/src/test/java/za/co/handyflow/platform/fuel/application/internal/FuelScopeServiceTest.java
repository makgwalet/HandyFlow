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
import za.co.handyflow.platform.fuel.dto.ScopeDtos.SaveScopeRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static za.co.handyflow.platform.fuel.domain.model.FuelDivision.*;

/** Phase 8c (G01): users limited to some divisions and depots. No rows means unrestricted. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelScopeServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID(), DEPOT_A = UUID.randomUUID(), DEPOT_B = UUID.randomUUID();

    @Mock FuelUserScopeRepository scopes;
    @Mock FuelTankRepository tanks;
    @Mock FuelDepotRepository depots;
    FuelScopeService svc;

    @BeforeEach void setUp() { svc = new FuelScopeService(scopes, tanks, depots); }

    private void rows(FuelUserScope... r) { when(scopes.findByUser(T, USER)).thenReturn(List.of(r)); }

    @Test @DisplayName("a user with no rows is unrestricted, so existing users keep their access")
    void noRowsUnrestricted() {
        rows();
        assertFalse(svc.scopeOf(T, USER).restricted());
        assertDoesNotThrow(() -> svc.requireDivision(T, USER, Set.of(DISTRIBUTION)));
        assertNull(svc.allowedTankIds(T, USER));
        assertNull(svc.allowedDepotIds(T, USER));
        assertFalse(svc.mine(T, USER).restricted());
    }

    @Test @DisplayName("a missing user id (no identified user) is unrestricted here; the security chain still applies")
    void nullUser() { assertFalse(svc.scopeOf(T, null).restricted()); }

    @Test @DisplayName("division limit allows a matching division and refuses the others with 403")
    void divisionLimit() {
        rows(FuelUserScope.division(T, USER, DISTRIBUTION, null));
        assertDoesNotThrow(() -> svc.requireDivision(T, USER, Set.of(DISTRIBUTION)));
        assertDoesNotThrow(() -> svc.requireDivision(T, USER, Set.of(WHOLESALE_SUPPLY, DISTRIBUTION)));
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> svc.requireDivision(T, USER, Set.of(RETAIL_FORECOURT)));
        assertEquals("FUEL_SCOPE_DIVISION", e.getErrorCode());
    }

    @Test @DisplayName("shared foundation routes (no division requirement) are never blocked by a division limit")
    void sharedFoundation() {
        rows(FuelUserScope.division(T, USER, DISTRIBUTION, null));
        assertDoesNotThrow(() -> svc.requireDivision(T, USER, Set.of()));
    }

    @Test @DisplayName("a depot-limited user may use a tank in their depot and is refused one in another depot")
    void tankDepotLimit() {
        rows(FuelUserScope.depot(T, USER, DEPOT_A, null));
        UUID inA = UUID.randomUUID(), inB = UUID.randomUUID(), loose = UUID.randomUUID();
        FuelTank a = mock(FuelTank.class), b = mock(FuelTank.class), l = mock(FuelTank.class);
        when(a.getDepotId()).thenReturn(DEPOT_A); when(b.getDepotId()).thenReturn(DEPOT_B); when(l.getDepotId()).thenReturn(null);
        when(tanks.findActiveById(T, inA)).thenReturn(Optional.of(a));
        when(tanks.findActiveById(T, inB)).thenReturn(Optional.of(b));
        when(tanks.findActiveById(T, loose)).thenReturn(Optional.of(l));
        assertDoesNotThrow(() -> svc.requireTarget(T, USER, new FuelScopeService.Target(FuelScopeService.Target.Kind.TANK, inA)));
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> svc.requireTarget(T, USER, new FuelScopeService.Target(FuelScopeService.Target.Kind.TANK, inB)));
        assertEquals("FUEL_SCOPE_DEPOT", e.getErrorCode());
        assertThrows(HandyFlowException.class, () -> svc.requireTarget(T, USER, new FuelScopeService.Target(FuelScopeService.Target.Kind.TANK, loose)));
    }

    @Test @DisplayName("a tank that does not exist is left for the controller to report as 404, not turned into 403")
    void unknownTank() {
        rows(FuelUserScope.depot(T, USER, DEPOT_A, null));
        when(tanks.findActiveById(eq(T), any())).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> svc.requireTarget(T, USER, new FuelScopeService.Target(FuelScopeService.Target.Kind.TANK, UUID.randomUUID())));
    }

    @Test @DisplayName("a depot path is checked against the user's depots directly")
    void depotPath() {
        rows(FuelUserScope.depot(T, USER, DEPOT_A, null));
        assertDoesNotThrow(() -> svc.requireTarget(T, USER, new FuelScopeService.Target(FuelScopeService.Target.Kind.DEPOT, DEPOT_A)));
        assertThrows(HandyFlowException.class, () -> svc.requireTarget(T, USER, new FuelScopeService.Target(FuelScopeService.Target.Kind.DEPOT, DEPOT_B)));
    }

    @Test @DisplayName("a division-only user has no depot limit, so tank checks pass")
    void divisionOnlyNoDepotLimit() {
        rows(FuelUserScope.division(T, USER, DISTRIBUTION, null));
        assertDoesNotThrow(() -> svc.requireTarget(T, USER, new FuelScopeService.Target(FuelScopeService.Target.Kind.TANK, UUID.randomUUID())));
        assertNull(svc.allowedTankIds(T, USER));
    }

    @Test @DisplayName("allowedTankIds lists only the tanks in the user's depots")
    void allowedTanks() {
        rows(FuelUserScope.depot(T, USER, DEPOT_A, null));
        FuelTank a = mock(FuelTank.class), b = mock(FuelTank.class), l = mock(FuelTank.class);
        UUID ida = UUID.randomUUID();
        when(a.getId()).thenReturn(ida); when(a.getDepotId()).thenReturn(DEPOT_A);
        when(b.getId()).thenReturn(UUID.randomUUID()); when(b.getDepotId()).thenReturn(DEPOT_B);
        when(l.getId()).thenReturn(UUID.randomUUID()); when(l.getDepotId()).thenReturn(null);
        when(tanks.findAllActive(T)).thenReturn(List.of(a, b, l));
        assertEquals(Set.of(ida), svc.allowedTankIds(T, USER));
        assertEquals(Set.of(DEPOT_A), svc.allowedDepotIds(T, USER));
    }

    @Test @DisplayName("targetOf finds a tank or depot id in the path, including under forecourt, and ignores other segments")
    void targetOf() {
        UUID id = UUID.randomUUID();
        assertEquals(new FuelScopeService.Target(FuelScopeService.Target.Kind.TANK, id), FuelScopeService.targetOf("/api/v1/fuel/tanks/" + id + "/receive").orElseThrow());
        assertEquals(new FuelScopeService.Target(FuelScopeService.Target.Kind.TANK, id), FuelScopeService.targetOf("/api/v1/fuel/forecourt/tanks/" + id + "/reconciliation").orElseThrow());
        assertEquals(new FuelScopeService.Target(FuelScopeService.Target.Kind.DEPOT, id), FuelScopeService.targetOf("/api/v1/fuel/depots/" + id).orElseThrow());
        assertTrue(FuelScopeService.targetOf("/api/v1/fuel/tanks/utilization-forecast").isEmpty());
        assertTrue(FuelScopeService.targetOf("/api/v1/fuel/tanks").isEmpty());
        assertTrue(FuelScopeService.targetOf("/api/v1/fuel/orders/" + id).isEmpty());
        assertTrue(FuelScopeService.targetOf("/other/tanks/" + id).isEmpty());
        assertTrue(FuelScopeService.targetOf(null).isEmpty());
    }

    @Test @DisplayName("replace swaps the whole set, de-duplicates, and checks every depot exists")
    void replaceSet() {
        when(depots.findActiveById(T, DEPOT_A)).thenReturn(Optional.of(mock(FuelDepot.class)));
        var out = svc.replace(T, USER, new SaveScopeRequest(List.of("DISTRIBUTION", "DISTRIBUTION"), List.of(DEPOT_A, DEPOT_A)), UUID.randomUUID());
        verify(scopes).deleteByUser(T, USER);
        verify(scopes).saveAll(argThat(l -> ((Collection<?>) l).size() == 2));
        assertEquals(List.of("DISTRIBUTION"), out.divisions());
        assertEquals(List.of(DEPOT_A), out.depotIds());
    }

    @Test @DisplayName("replace with empty lists removes every limit and saves nothing")
    void replaceEmpty() {
        var out = svc.replace(T, USER, new SaveScopeRequest(null, null), null);
        verify(scopes).deleteByUser(T, USER);
        verify(scopes, never()).saveAll(any());
        assertTrue(out.divisions().isEmpty());
        assertTrue(out.depotIds().isEmpty());
    }

    @Test @DisplayName("replace rejects an unknown division or depot and changes nothing")
    void replaceRejects() {
        assertThrows(IllegalArgumentException.class, () -> svc.replace(T, USER, new SaveScopeRequest(List.of("NOPE"), List.of()), null));
        when(depots.findActiveById(eq(T), any())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> svc.replace(T, USER, new SaveScopeRequest(List.of(), List.of(UUID.randomUUID())), null));
        verify(scopes, never()).deleteByUser(any(), any());
    }

    @Test @DisplayName("the scope is read once inside the cache window and again after it")
    void cache() {
        rows(FuelUserScope.depot(T, USER, DEPOT_A, null));
        long[] clock = {0};
        svc.nanos = () -> clock[0];
        svc.scopeOf(T, USER); svc.scopeOf(T, USER); svc.scopeOf(T, USER);
        verify(scopes, times(1)).findByUser(T, USER);
        clock[0] = FuelScopeService.CACHE_NANOS + 1;
        svc.scopeOf(T, USER);
        verify(scopes, times(2)).findByUser(T, USER);
    }

    @Test @DisplayName("saving a user's limits takes effect at once on this node, not after the cache window")
    void cacheEvictedOnSave() {
        long[] clock = {0};
        svc.nanos = () -> clock[0];
        rows();
        assertFalse(svc.scopeOf(T, USER).restricted());           // cached as unrestricted
        when(depots.findActiveById(T, DEPOT_A)).thenReturn(Optional.of(mock(FuelDepot.class)));
        when(scopes.saveAll(any())).thenAnswer(i -> i.getArgument(0));
        svc.replace(T, USER, new SaveScopeRequest(List.of(), List.of(DEPOT_A)), null);
        rows(FuelUserScope.depot(T, USER, DEPOT_A, null));
        assertTrue(svc.scopeOf(T, USER).restricted());            // same instant, no waiting
    }

    @Test @DisplayName("one user's cached scope is never served to another user or tenant")
    void cacheKeyed() {
        UUID other = UUID.randomUUID();
        rows(FuelUserScope.depot(T, USER, DEPOT_A, null));
        when(scopes.findByUser(T, other)).thenReturn(List.of());
        assertTrue(svc.scopeOf(T, USER).restricted());
        assertFalse(svc.scopeOf(T, other).restricted());
        TenantId t2 = TenantId.of(UUID.randomUUID());
        when(scopes.findByUser(t2, USER)).thenReturn(List.of());
        assertFalse(svc.scopeOf(t2, USER).restricted());
    }

    @Test @DisplayName("a shift is visible to a depot-limited user only when every pump read in it is on their tanks")
    void shiftVisibility() {
        UUID mine = UUID.randomUUID(), theirs = UUID.randomUUID();
        assertTrue(FuelScopeService.shiftVisible(null, List.of(theirs)));          // no limit
        assertTrue(FuelScopeService.shiftVisible(Set.of(mine), List.of(mine)));
        assertFalse(FuelScopeService.shiftVisible(Set.of(mine), List.of(mine, theirs)));
        assertFalse(FuelScopeService.shiftVisible(Set.of(mine), List.of()));      // nothing to place it by
        assertFalse(FuelScopeService.shiftVisible(Set.of(), List.of(mine)));
    }
}
