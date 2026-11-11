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
import za.co.handyflow.platform.fuel.api.FuelDivisionInterceptor;
import za.co.handyflow.platform.fuel.domain.model.FuelDivision;
import za.co.handyflow.platform.fuel.domain.model.FuelDivisionActivation;
import za.co.handyflow.platform.fuel.domain.model.FuelOperatingProfile;
import za.co.handyflow.platform.fuel.domain.repository.FuelDivisionActivationRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelOperatingProfileRepository;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static za.co.handyflow.platform.fuel.domain.model.FuelDivision.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelDivisionGuardTest {

    static final UUID TU = UUID.randomUUID();
    static final TenantId T = TenantId.of(TU);

    @Mock FuelOperatingProfileRepository profiles;
    @Mock FuelDivisionActivationRepository activations;
    @InjectMocks FuelDivisionGuard guard;

    @BeforeEach void clearCtx() { TenantContext.clear(); }

    private FuelOperatingProfile profile(boolean legacy, boolean active) {
        FuelOperatingProfile p = FuelOperatingProfile.forTenant(T);
        if (active) p.activate(UUID.randomUUID());
        try { var f = FuelOperatingProfile.class.getDeclaredField("legacyMode"); f.setAccessible(true); f.set(p, legacy); } catch (Exception e) { throw new RuntimeException(e); }
        return p;
    }

    private FuelDivisionActivation on(FuelDivision d) { return FuelDivisionActivation.of(T, d, null); }

    @Test @DisplayName("a tenant with no profile has no division, so division routes are refused with a clear 403")
    void noProfile() {
        when(profiles.findByTenant(T)).thenReturn(Optional.empty());
        assertTrue(guard.activeDivisions(T).isEmpty());
        HandyFlowException e = assertThrows(HandyFlowException.class, () -> guard.require(T, Set.of(RETAIL_FORECOURT)));
        assertEquals(403, e.getStatus().value());
        assertTrue(e.getMessage().contains("Retail & Forecourt"));
    }

    @Test @DisplayName("a legacy tenant keeps every division")
    void legacy() {
        when(profiles.findByTenant(T)).thenReturn(Optional.of(profile(true, true)));
        assertEquals(4, guard.activeDivisions(T).size());
        assertDoesNotThrow(() -> guard.require(T, Set.of(DISTRIBUTION)));
    }

    @Test @DisplayName("a configured tenant has exactly its active divisions; inactive rows do not count")
    void configured() {
        when(profiles.findByTenant(T)).thenReturn(Optional.of(profile(false, true)));
        FuelDivisionActivation off = on(DISTRIBUTION); off.deactivate(null, "no longer needed");
        when(activations.findByTenant(T)).thenReturn(List.of(on(WHOLESALE_SUPPLY), off));
        assertEquals(Set.of(WHOLESALE_SUPPLY), guard.activeDivisions(T));
        assertDoesNotThrow(() -> guard.require(T, Set.of(WHOLESALE_SUPPLY, DISTRIBUTION)));   // either is enough
        assertThrows(HandyFlowException.class, () -> guard.require(T, Set.of(DISTRIBUTION)));
        assertThrows(HandyFlowException.class, () -> guard.require(T, Set.of(RETAIL_FORECOURT)));
    }

    @Test @DisplayName("shared foundation (empty requirement) always passes")
    void sharedAlwaysPasses() {
        when(profiles.findByTenant(T)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> guard.require(T, Set.of()));
    }

    @Test @DisplayName("the interceptor applies the rules to the request path and ignores requests with no tenant and OPTIONS")
    void interceptor() {
        FuelDivisionInterceptor i = new FuelDivisionInterceptor(guard);
        when(profiles.findByTenant(any())).thenReturn(Optional.empty());
        var req = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/fuel/forecourt/pumps");
        var res = new org.springframework.mock.web.MockHttpServletResponse();
        assertTrue(i.preHandle(req, res, new Object()), "no tenant -> left to the security chain");
        TenantContext.setTenantId(TU.toString());
        assertThrows(HandyFlowException.class, () -> i.preHandle(req, res, new Object()));
        assertTrue(i.preHandle(new org.springframework.mock.web.MockHttpServletRequest("OPTIONS", "/api/v1/fuel/forecourt/pumps"), res, new Object()));
        assertTrue(i.preHandle(new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/fuel/tanks"), res, new Object()), "shared route");
        TenantContext.clear();
    }
}
