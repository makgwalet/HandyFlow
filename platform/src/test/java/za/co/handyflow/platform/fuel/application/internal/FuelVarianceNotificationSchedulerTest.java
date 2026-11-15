package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryVariance;
import za.co.handyflow.platform.fuel.domain.model.FuelSettings;
import za.co.handyflow.platform.fuel.domain.repository.FuelSettingsRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FuelVarianceNotificationSchedulerTest {

    static final TenantId A = TenantId.of(UUID.randomUUID()), B = TenantId.of(UUID.randomUUID());

    @Mock FuelSettingsRepository settings;
    @Mock FuelVarianceService variances;
    @Mock FeatureGuard featureGuard;
    @Mock NotificationService notifications;
    @Mock TenantAdminRecipients adminRecipients;
    final Recipient admin = Recipient.user(UUID.randomUUID(), "Admin", "a@x.co", null);
    @InjectMocks FuelVarianceNotificationScheduler scheduler;

    @Test @DisplayName("a tenant with new variances is alerted once, with the count and a pointer to the list")
    void alertsOnNewVariances() {
        when(settings.findTenantsWithVarianceTolerance()).thenReturn(List.of(A));
        when(featureGuard.hasModuleFor(A, "fuel")).thenReturn(true);
        when(variances.raiseNew(A)).thenReturn(3);
        when(adminRecipients.resolveTenantAdmins(A)).thenReturn(List.of(admin));
        assertEquals(1, scheduler.sweep());
        var cap = org.mockito.ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(cap.capture());
        assertEquals(NotificationType.FUEL_VARIANCE_RAISED, cap.getValue().type());
        assertTrue(cap.getValue().message().startsWith("3 delivery variances are"));
    }

    @Test @DisplayName("nothing new means no alert")
    void quietWhenNothingNew() {
        when(settings.findTenantsWithVarianceTolerance()).thenReturn(List.of(A));
        when(featureGuard.hasModuleFor(A, "fuel")).thenReturn(true);
        when(variances.raiseNew(A)).thenReturn(0);
        assertEquals(0, scheduler.sweep());
        verifyNoInteractions(notifications);
    }

    @Test @DisplayName("a tenant whose plan no longer includes Fuel is not scanned")
    void skipsUnentitled() {
        when(settings.findTenantsWithVarianceTolerance()).thenReturn(List.of(A));
        when(featureGuard.hasModuleFor(A, "fuel")).thenReturn(false);
        assertEquals(0, scheduler.sweep());
        verify(variances, never()).raiseNew(any());
    }

    @Test @DisplayName("no administrators to tell is not an error")
    void noAdmins() {
        when(settings.findTenantsWithVarianceTolerance()).thenReturn(List.of(A));
        when(featureGuard.hasModuleFor(A, "fuel")).thenReturn(true);
        when(variances.raiseNew(A)).thenReturn(1);
        when(adminRecipients.resolveTenantAdmins(A)).thenReturn(List.of());
        assertEquals(0, scheduler.sweep());
        verifyNoInteractions(notifications);
    }

    @Test @DisplayName("one tenant failing does not stop the others")
    void isolatesFailures() {
        when(settings.findTenantsWithVarianceTolerance()).thenReturn(List.of(A, B));
        when(featureGuard.hasModuleFor(any(TenantId.class), any())).thenReturn(true);
        when(variances.raiseNew(A)).thenThrow(new IllegalStateException("boom"));
        when(variances.raiseNew(B)).thenReturn(1);
        when(adminRecipients.resolveTenantAdmins(B)).thenReturn(List.of(admin));
        assertEquals(1, scheduler.sweep());
        verify(notifications, times(1)).send(any());
    }

    @Test @DisplayName("a notification failure is contained")
    void notificationFailureContained() {
        when(settings.findTenantsWithVarianceTolerance()).thenReturn(List.of(A));
        when(featureGuard.hasModuleFor(A, "fuel")).thenReturn(true);
        when(variances.raiseNew(A)).thenReturn(1);
        when(adminRecipients.resolveTenantAdmins(A)).thenReturn(List.of(admin));
        doThrow(new RuntimeException("mail down")).when(notifications).send(any());
        assertEquals(0, scheduler.sweep());
    }

    // ── Variances left unresolved too long ───────────────────────────────────

    private static FuelSettings reviewAfter(TenantId t, Integer days) { var s = FuelSettings.forTenant(t); s.updateVarianceReviewDays(days); return s; }
    private static FuelDeliveryVariance variance(TenantId t) {
        return FuelDeliveryVariance.ifOutside(t, UUID.randomUUID(), new java.math.BigDecimal("1000"), new java.math.BigDecimal("900"), new java.math.BigDecimal("1"));
    }

    @Test @DisplayName("overdue variances alert the administrators once and are then marked, so they are not repeated")
    void overdueAlertsOnceAndMarks() {
        when(settings.findWithVarianceReviewDays()).thenReturn(List.of(reviewAfter(A, 7)));
        when(featureGuard.hasModuleFor(A, "fuel")).thenReturn(true);
        var v1 = variance(A); var v2 = variance(A);
        when(variances.overdueNotAlerted(A, 7)).thenReturn(List.of(v1, v2));
        when(adminRecipients.resolveTenantAdmins(A)).thenReturn(List.of(admin));
        assertEquals(1, scheduler.sweepOverdue());
        var cap = org.mockito.ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(cap.capture());
        assertEquals(NotificationType.FUEL_VARIANCE_OVERDUE, cap.getValue().type());
        assertTrue(cap.getValue().message().startsWith("2 delivery variances have been open for more than 7 days"));
        verify(variances).markAlerted(A, List.of(v1.getId(), v2.getId()));
    }

    @Test @DisplayName("a single overdue variance reads in the singular")
    void overdueSingular() {
        when(settings.findWithVarianceReviewDays()).thenReturn(List.of(reviewAfter(A, 1)));
        when(featureGuard.hasModuleFor(A, "fuel")).thenReturn(true);
        when(variances.overdueNotAlerted(A, 1)).thenReturn(List.of(variance(A)));
        when(adminRecipients.resolveTenantAdmins(A)).thenReturn(List.of(admin));
        scheduler.sweepOverdue();
        var cap = org.mockito.ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(cap.capture());
        assertTrue(cap.getValue().message().startsWith("1 delivery variance has been open for more than 1 day."));
    }

    @Test @DisplayName("nothing overdue means no alert and nothing is marked")
    void overdueQuiet() {
        when(settings.findWithVarianceReviewDays()).thenReturn(List.of(reviewAfter(A, 7)));
        when(featureGuard.hasModuleFor(A, "fuel")).thenReturn(true);
        when(variances.overdueNotAlerted(A, 7)).thenReturn(List.of());
        assertEquals(0, scheduler.sweepOverdue());
        verifyNoInteractions(notifications);
        verify(variances, never()).markAlerted(any(), any());
    }

    @Test @DisplayName("if the alert cannot be sent the variances are not marked, so tomorrow's sweep tries again")
    void overdueNotMarkedWhenSendFails() {
        when(settings.findWithVarianceReviewDays()).thenReturn(List.of(reviewAfter(A, 7)));
        when(featureGuard.hasModuleFor(A, "fuel")).thenReturn(true);
        when(variances.overdueNotAlerted(A, 7)).thenReturn(List.of(variance(A)));
        when(adminRecipients.resolveTenantAdmins(A)).thenReturn(List.of(admin));
        doThrow(new RuntimeException("mail down")).when(notifications).send(any());
        assertEquals(0, scheduler.sweepOverdue());
        verify(variances, never()).markAlerted(any(), any());
    }

    @Test @DisplayName("a tenant whose plan no longer includes Fuel is skipped, and a failing tenant does not stop the next")
    void overdueSkipsAndIsolates() {
        when(settings.findWithVarianceReviewDays()).thenReturn(List.of(reviewAfter(A, 7), reviewAfter(B, 7)));
        when(featureGuard.hasModuleFor(A, "fuel")).thenReturn(false);
        when(featureGuard.hasModuleFor(B, "fuel")).thenReturn(true);
        when(variances.overdueNotAlerted(B, 7)).thenReturn(List.of(variance(B)));
        when(adminRecipients.resolveTenantAdmins(B)).thenReturn(List.of(admin));
        assertEquals(1, scheduler.sweepOverdue());
        verify(variances, never()).overdueNotAlerted(eq(A), anyInt());
    }

    @Test @DisplayName("the number of days must be 1 to 365, or empty for off")
    void reviewDaysValidation() {
        var s = FuelSettings.forTenant(A);
        assertThrows(IllegalArgumentException.class, () -> s.updateVarianceReviewDays(0));
        assertThrows(IllegalArgumentException.class, () -> s.updateVarianceReviewDays(366));
        s.updateVarianceReviewDays(30);
        assertEquals(30, s.getVarianceReviewDays());
        s.updateVarianceReviewDays(null);
        assertNull(s.getVarianceReviewDays());
        assertNull(FuelSettings.forTenant(A).getVarianceReviewDays(), "no default: the alert is off until the company chooses a number");
    }
}
