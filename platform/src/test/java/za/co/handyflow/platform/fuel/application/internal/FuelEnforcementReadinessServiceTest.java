package za.co.handyflow.platform.fuel.application.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.fuel.dto.EnforcementDtos.Readiness;
import za.co.handyflow.platform.shared.TenantId;

/** Written, not compiled or run. */
class FuelEnforcementReadinessServiceTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    FuelDeliveryRepository deliveries = mock(FuelDeliveryRepository.class);
    FuelSettingsService settings = mock(FuelSettingsService.class);
    FuelSettings current = mock(FuelSettings.class);
    FuelEnforcementReadinessService service = new FuelEnforcementReadinessService(deliveries, settings);

    FuelEnforcementReadinessServiceTest() { when(settings.current(T)).thenReturn(current); }

    @Test @DisplayName("reports the counts and the current switch positions, looking back 30 days by default")
    void countsAndSwitches() {
        when(current.isArrivalCheckEnforced()).thenReturn(false);
        when(current.isPodPolicyEnforced()).thenReturn(true);
        when(deliveries.countDeliveredSince(eq(T), any())).thenReturn(120L);
        when(deliveries.countDeliveredWithoutReceiverSince(eq(T), any())).thenReturn(7L);
        when(deliveries.countDeliveredWithoutArrivalCheckSince(eq(T), any())).thenReturn(95L);
        Readiness r = service.readiness(T, null, NOW);
        assertEquals(new Readiness(30, false, true, 120, 7, 95), r);
        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(deliveries).countDeliveredSince(eq(T), since.capture());
        assertEquals(NOW.minusSeconds(30L * 86400), since.getValue());
    }

    @Test @DisplayName("the window is kept between 1 and 180 days")
    void windowIsClamped() {
        assertEquals(1, service.readiness(T, 0, NOW).days());
        assertEquals(1, service.readiness(T, -5, NOW).days());
        assertEquals(180, service.readiness(T, 9999, NOW).days());
        assertEquals(60, service.readiness(T, 60, NOW).days());
    }

    @Test @DisplayName("it only reads: no switch is changed")
    void readOnly() {
        service.readiness(T, 30, NOW);
        verify(settings, never()).updateArrivalPolicy(any(), any());
    }
}
