package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceItem;
import za.co.handyflow.platform.fuel.domain.model.FuelComplianceReminder;
import za.co.handyflow.platform.fuel.domain.repository.FuelComplianceItemRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelComplianceReminderRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelComplianceNotificationSchedulerTest {

    static final TenantId T1 = TenantId.of(UUID.randomUUID());
    static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    @Mock FuelComplianceItemRepository items;
    @Mock FuelComplianceReminderRepository reminders;
    @Mock NotificationService notifications;
    @Mock TenantAdminRecipients admins;
    @InjectMocks FuelComplianceNotificationScheduler scheduler;

    Recipient admin = new Recipient(UUID.randomUUID(), "Admin", "admin@acme.co.za", null);

    @BeforeEach
    void setUp() {
        when(admins.resolveTenantAdmins(any(TenantId.class))).thenReturn(List.of(admin));
        when(reminders.findByItemIds(any())).thenReturn(List.of());
    }

    private FuelComplianceItem item(TenantId t, String title, LocalDate expires) {
        return FuelComplianceItem.create(t, "CERTIFICATE", title, "REF-1", "COMPANY", null, "Acme Fuels", TODAY.minusYears(1), expires, null);
    }

    @Test @DisplayName("the stage is the most urgent one that applies; far-off and long-expired items get none")
    void stages() {
        assertNull(FuelComplianceNotificationScheduler.stageFor(TODAY.plusDays(31), TODAY));
        assertEquals("D30", FuelComplianceNotificationScheduler.stageFor(TODAY.plusDays(30), TODAY));
        assertEquals("D30", FuelComplianceNotificationScheduler.stageFor(TODAY.plusDays(8), TODAY));
        assertEquals("D7", FuelComplianceNotificationScheduler.stageFor(TODAY.plusDays(7), TODAY));
        assertEquals("D7", FuelComplianceNotificationScheduler.stageFor(TODAY, TODAY));
        assertEquals("EXPIRED", FuelComplianceNotificationScheduler.stageFor(TODAY.minusDays(1), TODAY));
        assertEquals("EXPIRED", FuelComplianceNotificationScheduler.stageFor(TODAY.minusDays(30), TODAY));
        assertNull(FuelComplianceNotificationScheduler.stageFor(TODAY.minusDays(31), TODAY));
    }

    @Test @DisplayName("several due items for one tenant go out as ONE digest, and the stages are recorded")
    void oneDigestPerTenant() {
        FuelComplianceItem a = item(T1, "SANAS calibration", TODAY.plusDays(20));
        FuelComplianceItem b = item(T1, "Site licence", TODAY.plusDays(3));
        when(items.findExpiringBetweenAllTenants(any(), any())).thenReturn(List.of(a, b));
        assertEquals(1, scheduler.sweep(TODAY));
        ArgumentCaptor<NotificationRequest> cap = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, times(1)).send(cap.capture());
        assertEquals(NotificationType.FUEL_COMPLIANCE_EXPIRING, cap.getValue().type());
        assertTrue(cap.getValue().message().contains("SANAS calibration"));
        assertTrue(cap.getValue().message().contains("Site licence"));
        // a: D30 recorded. b: D30 and D7 recorded (milder stage marked done so it never follows)
        verify(reminders, times(3)).save(any(FuelComplianceReminder.class));
    }

    @Test @DisplayName("an expired item makes the digest an 'expired' notification")
    void expiredTypeWins() {
        when(items.findExpiringBetweenAllTenants(any(), any())).thenReturn(List.of(item(T1, "Licence", TODAY.minusDays(2)), item(T1, "Cert", TODAY.plusDays(10))));
        scheduler.sweep(TODAY);
        ArgumentCaptor<NotificationRequest> cap = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(cap.capture());
        assertEquals(NotificationType.FUEL_COMPLIANCE_EXPIRED, cap.getValue().type());
        assertTrue(cap.getValue().message().contains("expired 2 days ago"));
    }

    @Test @DisplayName("a stage already sent for the same expiry date is not sent again; a renewed date starts afresh")
    void noRepeats() {
        FuelComplianceItem a = item(T1, "Cert", TODAY.plusDays(20));
        when(items.findExpiringBetweenAllTenants(any(), any())).thenReturn(List.of(a));
        when(reminders.findByItemIds(any())).thenReturn(List.of(FuelComplianceReminder.of(T1, a.getId(), a.getExpiresOn(), "D30")));
        assertEquals(0, scheduler.sweep(TODAY));
        verify(notifications, never()).send(any());
        // renewed: the earlier reminder was for a different expiry date, so this date is new and is sent
        when(reminders.findByItemIds(any())).thenReturn(List.of(FuelComplianceReminder.of(T1, a.getId(), a.getExpiresOn().minusDays(365), "D30")));
        assertEquals(1, scheduler.sweep(TODAY));
    }

    @Test @DisplayName("a tenant with no admin to tell is skipped and nothing is recorded")
    void noRecipients() {
        when(admins.resolveTenantAdmins(any(TenantId.class))).thenReturn(List.of());
        when(items.findExpiringBetweenAllTenants(any(), any())).thenReturn(List.of(item(T1, "Cert", TODAY.plusDays(5))));
        assertEquals(0, scheduler.sweep(TODAY));
        verify(notifications, never()).send(any());
        verify(reminders, never()).save(any());
    }

    @Test @DisplayName("a failed send for one tenant does not stop the others and records nothing for it")
    void failureIsolated() {
        TenantId t2 = TenantId.of(UUID.randomUUID());
        when(items.findExpiringBetweenAllTenants(any(), any())).thenReturn(List.of(item(T1, "A", TODAY.plusDays(5)), item(t2, "B", TODAY.plusDays(5))));
        doThrow(new RuntimeException("smtp down")).doNothing().when(notifications).send(any());
        assertEquals(1, scheduler.sweep(TODAY));
        verify(reminders, atLeastOnce()).save(any(FuelComplianceReminder.class));
    }
}
