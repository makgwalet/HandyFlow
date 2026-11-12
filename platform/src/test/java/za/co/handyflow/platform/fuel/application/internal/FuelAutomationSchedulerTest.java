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
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.domain.model.FuelPurchaseOrder;
import za.co.handyflow.platform.fuel.domain.repository.FuelAutomationRuleRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelSettingsRepository;
import za.co.handyflow.platform.fuel.dto.AutomationDtos.RunResult;
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
class FuelAutomationSchedulerTest {

    static final TenantId A = TenantId.of(UUID.randomUUID()), B = TenantId.of(UUID.randomUUID());

    @Mock FuelAutomationRuleRepository rules;
    @Mock FuelSettingsRepository settings;
    @Mock FuelAutomationService automation;
    @Mock FeatureGuard featureGuard;
    @Mock NotificationService notifications;
    @Mock TenantAdminRecipients adminRecipients;
    final Recipient admin = Recipient.user(UUID.randomUUID(), "Admin", "a@x.co", null);
    @InjectMocks FuelAutomationScheduler scheduler;

    private FuelPurchaseOrder po(String number) {
        FuelPurchaseOrder p = mock(FuelPurchaseOrder.class);
        when(p.getId()).thenReturn(UUID.randomUUID()); when(p.getPoNumber()).thenReturn(number);
        return p;
    }

    private void common() {
        when(featureGuard.hasModuleFor(any(), eq("fuel"))).thenReturn(true);
        when(adminRecipients.resolveTenantAdmins(any(TenantId.class))).thenReturn(List.of(admin));
        when(settings.findTenantsAlertingOverduePurchaseOrders()).thenReturn(List.of());
    }

    @Test @DisplayName("drafts are announced once, saying nothing was submitted or ordered and naming the requisitions")
    void announcesDrafts() {
        common();
        when(rules.findTenantsWithEnabledRules()).thenReturn(List.of(A));
        when(automation.runRules(A)).thenReturn(new RunResult(2, 2, 0, 0, List.of("REQ-2026-00001", "REQ-2026-00002")));
        assertEquals(1, scheduler.sweep());
        ArgumentCaptor<NotificationRequest> cap = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(cap.capture());
        assertEquals(NotificationType.FUEL_REORDER_DRAFTED, cap.getValue().type());
        assertTrue(cap.getValue().message().contains("REQ-2026-00001") && cap.getValue().message().contains("Nothing has been submitted or ordered"));
    }

    @Test @DisplayName("no drafts, no notice")
    void quiet() {
        common();
        when(rules.findTenantsWithEnabledRules()).thenReturn(List.of(A));
        when(automation.runRules(A)).thenReturn(new RunResult(1, 0, 1, 0, List.of()));
        assertEquals(0, scheduler.sweep());
        verifyNoInteractions(notifications);
    }

    @Test @DisplayName("a tenant that no longer has Fuel is skipped")
    void skipsTenantsWithoutFuel() {
        common();
        when(featureGuard.hasModuleFor(A, "fuel")).thenReturn(false);
        when(rules.findTenantsWithEnabledRules()).thenReturn(List.of(A));
        assertEquals(0, scheduler.sweep());
        verify(automation, never()).runRules(any());
    }

    @Test @DisplayName("overdue orders are alerted once for tenants that opted in, and marked only after the notice is sent")
    void overdueAlert() {
        common();
        when(rules.findTenantsWithEnabledRules()).thenReturn(List.of());
        when(settings.findTenantsAlertingOverduePurchaseOrders()).thenReturn(List.of(A));
        when(automation.runRules(A)).thenReturn(new RunResult(0, 0, 0, 0, List.of()));
        FuelPurchaseOrder p1 = po("PO-2026-00007");
        when(automation.overdueOrders(A)).thenReturn(List.of(p1));
        assertEquals(1, scheduler.sweep());
        ArgumentCaptor<NotificationRequest> cap = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(cap.capture());
        assertEquals(NotificationType.FUEL_PO_OVERDUE, cap.getValue().type()); assertTrue(cap.getValue().message().contains("PO-2026-00007"));
        verify(automation).markOverdueAlerted(A, List.of(p1.getId()));
    }

    @Test @DisplayName("if the notice cannot be sent, the order is not marked, so tomorrow's run tries again; other tenants still run")
    void failedSendIsRetried() {
        common();
        when(rules.findTenantsWithEnabledRules()).thenReturn(List.of());
        when(settings.findTenantsAlertingOverduePurchaseOrders()).thenReturn(List.of(A, B));
        when(automation.runRules(any())).thenReturn(new RunResult(0, 0, 0, 0, List.of()));
        when(automation.overdueOrders(any())).thenReturn(List.of(po("PO-1")));
        doThrow(new IllegalStateException("mail down")).doNothing().when(notifications).send(any());
        assertEquals(1, scheduler.sweep());
        verify(automation, times(1)).markOverdueAlerted(any(), any());
    }
}
