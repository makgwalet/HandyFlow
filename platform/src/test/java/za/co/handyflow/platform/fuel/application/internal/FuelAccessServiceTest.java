package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.fuel.domain.model.FuelDivision;
import za.co.handyflow.platform.fuel.dto.FuelAccessDtos.AccessSummary;
import za.co.handyflow.platform.fuel.dto.FuelProfileDtos.ProfileView;
import za.co.handyflow.platform.fuel.dto.FuelSettingsDtos.EmergencyContact;
import za.co.handyflow.platform.fuel.dto.ScopeDtos.MyScope;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FuelAccessServiceTest {

    @Mock FuelOperatingProfileService profile;
    @Mock FuelScopeService scopes;
    @Mock FuelSettingsService settings;
    @InjectMocks FuelAccessService service;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID USER = UUID.randomUUID();

    private static ProfileView view(boolean legacy, List<FuelDivision> active) {
        return new ProfileView("ACTIVE", legacy, null, null, 0, null, active, List.of(), List.of());
    }

    @Test @DisplayName("summarises divisions, the person's own limits and the emergency contact")
    void summarises() {
        when(profile.view(T)).thenReturn(view(false, List.of(FuelDivision.TRUCK_STOP, FuelDivision.RETAIL_FORECOURT)));
        when(scopes.mine(T, USER)).thenReturn(new MyScope(true, List.of("RETAIL_FORECOURT"), List.of()));
        when(settings.emergencyContact(T)).thenReturn(new EmergencyContact("Control room", "+27115550100"));

        AccessSummary a = service.summary(T, USER);

        assertFalse(a.legacyMode());
        assertEquals(List.of("RETAIL_FORECOURT", "TRUCK_STOP"), a.activeDivisions(), "sorted so the app sees a stable order");
        assertTrue(a.scope().restricted());
        assertEquals("+27115550100", a.emergencyContact().phone());
    }

    @Test @DisplayName("a company that has not set up divisions is reported as legacy so the app opens everything")
    void legacy() {
        when(profile.view(T)).thenReturn(view(true, List.of()));
        when(scopes.mine(T, USER)).thenReturn(new MyScope(false, List.of(), List.of()));
        when(settings.emergencyContact(T)).thenReturn(new EmergencyContact(null, null));
        AccessSummary a = service.summary(T, USER);
        assertTrue(a.legacyMode());
        assertTrue(a.activeDivisions().isEmpty());
        assertNull(a.emergencyContact().phone());
    }
}
