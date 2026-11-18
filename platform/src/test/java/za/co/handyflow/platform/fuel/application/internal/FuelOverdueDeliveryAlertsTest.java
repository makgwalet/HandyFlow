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
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.domain.repository.FuelDeliveryRepository;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelOverdueDeliveryAlertsTest {

    @Mock FuelDeliveryRepository deliveries;
    @Mock FuelSettingsService settings;
    @Mock FeatureGuard featureGuard;
    @Mock HrFacade hr;
    @Mock FuelAlertRecipientService alertRecipients;
    @Mock FuelPushEventService pushEvents;

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static final UUID DRIVER_USER = UUID.randomUUID();
    static final UUID DISPATCH = UUID.randomUUID();
    static final Instant SCHEDULED = Instant.parse("2026-11-18T06:00:00Z");

    FuelOverdueDeliveryAlerts alerts;
    FuelDelivery load;
    FuelSettings cfg;

    @BeforeEach
    void setUp() {
        alerts = new FuelOverdueDeliveryAlerts(deliveries, settings, featureGuard, hr, alertRecipients, pushEvents);
        UUID employee = UUID.randomUUID();
        load = FuelDelivery.create(T, UUID.randomUUID(), UUID.randomUUID(), Map.of(), "DIESEL", new BigDecimal("1000"), new BigDecimal("20"), SCHEDULED, null, null);
        load.allocate(UUID.randomUUID(), "GP 1", employee, "Driver");
        cfg = FuelSettings.forTenant(T);   // 120 minutes grace, 60 minutes escalation
        when(settings.current(T)).thenReturn(cfg);
        when(featureGuard.hasModuleFor(T, "fuel")).thenReturn(true);
        when(deliveries.findOpenScheduledBeforeAnyTenant(any())).thenReturn(List.of(load));
        when(hr.findEmployeeById(eq(T), any())).thenReturn(Optional.of(new EmployeeSummary(employee, "E1", "A", "B", "A B", null, null, "Driver", null, "ACTIVE", DRIVER_USER)));
        when(alertRecipients.recipientsFor(T, FuelAlertRecipientService.DELIVERY_OVERDUE)).thenReturn(List.of(new Recipient(DISPATCH, "d@x.co.za", "Dispatch", null), new Recipient(DRIVER_USER, "x@x.co.za", "Driver", null)));
        when(pushEvents.publish(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    @Test @DisplayName("before the grace is over nothing is sent")
    void notYetOverdue() {
        assertEquals(0, alerts.sweep(SCHEDULED.plus(Duration.ofMinutes(119))));
        verifyNoInteractions(pushEvents);
    }

    @Test @DisplayName("once overdue only the driver is told; dispatch waits for the escalation delay")
    void driverFirst() {
        alerts.sweep(SCHEDULED.plus(Duration.ofMinutes(125)));
        ArgumentCaptor<Collection<UUID>> to = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(pushEvents, times(1)).publish(eq(T), eq(FuelPushEventService.OVERDUE_DELIVERY), key.capture(), to.capture(), any(), any(), any());
        assertEquals(List.of(DRIVER_USER), List.copyOf(to.getValue()));
        assertTrue(key.getValue().endsWith(":L1"));
    }

    @Test @DisplayName("after the escalation delay dispatch is told too, without the driver a second time")
    void escalates() {
        alerts.sweep(SCHEDULED.plus(Duration.ofMinutes(181)));
        ArgumentCaptor<Collection<UUID>> to = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(pushEvents, times(2)).publish(eq(T), any(), key.capture(), to.capture(), any(), any(), any());
        assertTrue(key.getAllValues().get(0).endsWith(":L1"));
        assertTrue(key.getAllValues().get(1).endsWith(":L2"));
        assertEquals(List.of(DISPATCH), List.copyOf(to.getAllValues().get(1)));
    }

    @Test @DisplayName("a load with no driver on the app goes straight to dispatch")
    void noDriverUser() {
        when(hr.findEmployeeById(eq(T), any())).thenReturn(Optional.empty());
        alerts.sweep(SCHEDULED.plus(Duration.ofMinutes(125)));
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(pushEvents, times(1)).publish(eq(T), any(), key.capture(), any(), any(), any(), any());
        assertTrue(key.getValue().endsWith(":L2"));
    }

    @Test @DisplayName("a company's own grace is used, and a company without Fuel is skipped")
    void companyGraceAndModule() {
        cfg.updateOverduePolicy(30, 15);
        alerts.sweep(SCHEDULED.plus(Duration.ofMinutes(31)));
        verify(pushEvents, times(1)).publish(any(), any(), any(), any(), any(), any(), any());
        reset(pushEvents);
        when(featureGuard.hasModuleFor(T, "fuel")).thenReturn(false);
        assertEquals(0, new FuelOverdueDeliveryAlerts(deliveries, settings, featureGuard, hr, alertRecipients, pushEvents).sweep(SCHEDULED.plus(Duration.ofDays(2))));
        verifyNoInteractions(pushEvents);
    }

    @Test @DisplayName("the grace and escalation have sensible limits")
    void policyLimits() {
        assertThrows(IllegalArgumentException.class, () -> cfg.updateOverduePolicy(5, 60));
        assertThrows(IllegalArgumentException.class, () -> cfg.updateOverduePolicy(120, 2000));
    }

    @Test @DisplayName("one load failing does not stop the sweep")
    void oneFailureDoesNotStopSweep() {
        when(settings.current(T)).thenThrow(new RuntimeException("boom"));
        assertEquals(0, alerts.sweep(SCHEDULED.plus(Duration.ofDays(1))));
    }
}
