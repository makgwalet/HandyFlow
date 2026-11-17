package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelDepot;
import za.co.handyflow.platform.fuel.domain.model.FuelShiftVariancePolicy;
import za.co.handyflow.platform.fuel.domain.model.FuelToleranceOverride;
import za.co.handyflow.platform.fuel.domain.repository.FuelDepotRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelShiftVariancePolicyRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelToleranceOverrideRepository;
import za.co.handyflow.platform.fuel.dto.ShiftControlDtos.OverrideRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Written, not compiled or run. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelToleranceServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }
    @Mock FuelToleranceOverrideRepository overrides;
    @Mock FuelShiftVariancePolicyRepository policies;
    @Mock FuelDepotRepository depots;
    @InjectMocks FuelToleranceService service;

    private void depotTol(UUID depot, String amount) {
        when(overrides.findOne(T, "DEPOT", depot.toString())).thenReturn(Optional.of(FuelToleranceOverride.of(T, "DEPOT", depot.toString(), bd(amount), null)));
    }

    @Test @DisplayName("with no depot override the company-wide tolerance applies")
    void defaultApplies() {
        assertEquals(0, bd("5").compareTo(service.cashToleranceFor(T, Set.of(UUID.randomUUID()), bd("5"))));
        assertEquals(0, bd("5").compareTo(service.cashToleranceFor(T, Set.of(), bd("5"))));
    }

    @Test @DisplayName("a depot's own tolerance replaces the company-wide number")
    void depotWins() {
        UUID d = UUID.randomUUID(); depotTol(d, "50");
        assertEquals(0, bd("50").compareTo(service.cashToleranceFor(T, Set.of(d), bd("5"))));
    }

    @Test @DisplayName("a shift drawing on two depots with their own numbers uses the smaller, never looser than either site")
    void smallestWins() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(); depotTol(a, "50"); depotTol(b, "20");
        assertEquals(0, bd("20").compareTo(service.cashToleranceFor(T, Set.of(a, b), bd("5"))));
    }

    @Test @DisplayName("a lookup failure falls back to the company-wide number rather than failing the close")
    void failureFallsBack() {
        when(overrides.findOne(any(), any(), any())).thenThrow(new RuntimeException("db"));
        assertEquals(0, bd("5").compareTo(service.cashToleranceFor(T, Set.of(UUID.randomUUID()), bd("5"))));
        assertEquals(0, bd("500").compareTo(service.gaugeLimitFor(T, "DIESEL", bd("500"))));
    }

    @Test @DisplayName("a fuel type's own gauge limit replaces the company-wide limit, matched without regard to case")
    void productGaugeLimit() {
        when(overrides.findOne(T, "PRODUCT", "DIESEL")).thenReturn(Optional.of(FuelToleranceOverride.of(T, "PRODUCT", "DIESEL", bd("200"), null)));
        assertEquals(0, bd("200").compareTo(service.gaugeLimitFor(T, "diesel", bd("500"))));
        assertEquals(0, bd("500").compareTo(service.gaugeLimitFor(T, "PETROL", bd("500"))));
        assertEquals(0, bd("500").compareTo(service.gaugeLimitFor(T, null, bd("500"))));
    }

    @Test @DisplayName("saving a depot override needs an active depot of this company; an unknown scope or a negative number is refused")
    void saveValidates() {
        UUID d = UUID.randomUUID();
        when(depots.findActiveById(T, d)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.save(T, new OverrideRequest("DEPOT", d.toString(), bd("10")), null));
        assertThrows(ResourceNotFoundException.class, () -> service.save(T, new OverrideRequest("DEPOT", "not-an-id", bd("10")), null));
        assertThrows(IllegalArgumentException.class, () -> service.save(T, new OverrideRequest("SITE", "x", bd("10")), null));
        assertThrows(IllegalArgumentException.class, () -> FuelToleranceOverride.of(T, "PRODUCT", "DIESEL", bd("-1"), null));
    }

    @Test @DisplayName("saving a fuel type stores it in upper case and updates an existing row instead of adding a second")
    void saveProductUpserts() {
        FuelToleranceOverride existing = FuelToleranceOverride.of(T, "PRODUCT", "DIESEL", bd("200"), null);
        when(overrides.findOne(T, "PRODUCT", "DIESEL")).thenReturn(Optional.of(existing));
        var v = service.save(T, new OverrideRequest("product", "diesel", bd("300")), null);
        assertEquals("DIESEL", v.key());
        assertEquals(0, bd("300").compareTo(existing.getAmount()));
        verify(overrides, never()).save(any());
    }

    @Test @DisplayName("the repeated-variance rule needs at least 2 shifts and a number of days; no count switches it off")
    void policyRules() {
        when(policies.findById(T.getValue())).thenReturn(Optional.empty());
        when(policies.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThrows(IllegalArgumentException.class, () -> service.savePolicy(T, 1, 7, null));
        assertThrows(IllegalArgumentException.class, () -> service.savePolicy(T, 3, null, null));
        var p = service.savePolicy(T, 3, 7, null);
        assertEquals(3, p.repeatCount()); assertEquals(7, p.repeatDays());
        FuelShiftVariancePolicy row = FuelShiftVariancePolicy.of(T, 3, 7, null);
        when(policies.findById(T.getValue())).thenReturn(Optional.of(row));
        assertNull(service.savePolicy(T, null, null, null).repeatCount());
        verify(policies).delete(row);
    }
}
