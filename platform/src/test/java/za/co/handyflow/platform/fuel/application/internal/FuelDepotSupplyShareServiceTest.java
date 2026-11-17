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
import za.co.handyflow.platform.fuel.domain.model.FuelDepot;
import za.co.handyflow.platform.fuel.domain.model.FuelDepotSupplyShare;
import za.co.handyflow.platform.fuel.domain.repository.FuelDepotRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelDepotSupplyShareRepository;
import za.co.handyflow.platform.fuel.dto.ForecourtSupplyDtos.ShareLine;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A forecourt's supply split: percentages across several supplying depots. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelDepotSupplyShareServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    @Mock FuelDepotSupplyShareRepository shares;
    @Mock FuelDepotRepository depots;
    FuelDepotSupplyShareService svc;
    FuelDepot fc, a, b;

    @BeforeEach void setUp() {
        svc = new FuelDepotSupplyShareService(shares, depots);
        fc = depot("Forecourt", null); a = depot("Depot A", null); b = depot("Depot B", null);
        when(depots.findAllActive(T)).thenReturn(List.of(fc, a, b));
        when(shares.findByDepot(eq(T), any())).thenReturn(List.of());
    }
    private static <X> X eq(X x) { return org.mockito.ArgumentMatchers.eq(x); }
    private static <X> X any() { return org.mockito.ArgumentMatchers.any(); }

    private FuelDepot depot(String name, UUID supplier) {
        FuelDepot d = mock(FuelDepot.class);
        UUID id = UUID.randomUUID();
        when(d.getId()).thenReturn(id); when(d.getName()).thenReturn(name); when(d.getSupplyingDepotId()).thenReturn(supplier);
        when(depots.findActiveById(T, id)).thenReturn(Optional.of(d));
        return d;
    }

    @Test @DisplayName("a split totalling 100 replaces the old one")
    void saves() {
        svc.replace(T, fc.getId(), List.of(new ShareLine(a.getId(), bd("60")), new ShareLine(b.getId(), bd("40"))));
        verify(shares).deleteByDepot(T, fc.getId());
        ArgumentCaptor<FuelDepotSupplyShare> cap = ArgumentCaptor.forClass(FuelDepotSupplyShare.class);
        verify(shares, times(2)).save(cap.capture());
        assertEquals(0, bd("60").compareTo(cap.getAllValues().get(0).getPercentage()));
    }

    @Test @DisplayName("percentages that do not total 100 are refused and nothing is deleted")
    void mustTotal100() {
        var ex = assertThrows(IllegalArgumentException.class, () -> svc.replace(T, fc.getId(), List.of(new ShareLine(a.getId(), bd("60")), new ShareLine(b.getId(), bd("30")))));
        assertTrue(ex.getMessage().contains("total 100"));
        verify(shares, never()).deleteByDepot(any(), any());
    }

    @Test @DisplayName("a depot cannot supply itself, appear twice, or be a depot that is supplied by this one")
    void rules() {
        assertThrows(IllegalArgumentException.class, () -> svc.replace(T, fc.getId(), List.of(new ShareLine(fc.getId(), bd("100")))));
        assertThrows(IllegalArgumentException.class, () -> svc.replace(T, fc.getId(), List.of(new ShareLine(a.getId(), bd("50")), new ShareLine(a.getId(), bd("50")))));
        FuelDepot c = depot("Depot C", fc.getId()); // C is supplied by the forecourt, so the forecourt cannot take fuel from C
        assertThrows(IllegalArgumentException.class, () -> svc.replace(T, fc.getId(), List.of(new ShareLine(c.getId(), bd("100")))));
    }

    @Test @DisplayName("an empty list removes the split")
    void clears() {
        svc.replace(T, fc.getId(), List.of());
        verify(shares).deleteByDepot(T, fc.getId());
        verify(shares, never()).save(any());
    }
}
