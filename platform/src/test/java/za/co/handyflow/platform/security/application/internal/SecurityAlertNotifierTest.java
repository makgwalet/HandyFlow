package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.security.domain.model.AlarmEvent;
import za.co.handyflow.platform.security.domain.model.AlarmEvent.AlarmSeverity;
import za.co.handyflow.platform.security.domain.model.AlarmEvent.AlarmSource;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Not run in the authoring sandbox (no Maven jars). */
class SecurityAlertNotifierTest {

    private final NotificationService ns = mock(NotificationService.class);
    private final TenantAdminRecipients rec = mock(TenantAdminRecipients.class);
    private final GuardRepository guards = mock(GuardRepository.class);
    private final SecurityAlertNotifier n = new SecurityAlertNotifier(ns, rec, guards);
    private final TenantId tenant = TenantId.of(UUID.randomUUID());

    private AlarmEvent event(AlarmSource s, AlarmSeverity sev) {
        return AlarmEvent.ingest(tenant, null, s, null, sev, null, null, null, "desc");
    }

    @Test void duressSendsCriticalWithSms() {
        when(rec.resolveTenantAdmins(tenant)).thenReturn(List.of(Recipient.user(UUID.randomUUID(), "A B", "a@b.c", "0820000000")));
        n.duress(tenant, event(AlarmSource.DURESS, AlarmSeverity.CRITICAL));
        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(ns).send(c.capture());
        assertEquals(NotificationType.DURESS_TRIGGERED, c.getValue().type());
        assertTrue(c.getValue().channels().contains(NotificationChannel.SMS));
    }

    @Test void criticalNonDuressSends() {
        when(rec.resolveTenantAdmins(tenant)).thenReturn(List.of(Recipient.user(UUID.randomUUID(), "A B", "a@b.c", null)));
        n.criticalAlarm(tenant, event(AlarmSource.ALARM_PANEL, AlarmSeverity.CRITICAL));
        verify(ns).send(any());
    }

    @Test void highAndDuressSourceAreSkippedByCriticalPath() {
        n.criticalAlarm(tenant, event(AlarmSource.ALARM_PANEL, AlarmSeverity.HIGH));
        n.criticalAlarm(tenant, event(AlarmSource.DURESS, AlarmSeverity.CRITICAL));
        verifyNoInteractions(ns);
    }

    @Test void noRecipientsDoesNotThrowOrSend() {
        when(rec.resolveTenantAdmins(tenant)).thenReturn(List.of());
        assertDoesNotThrow(() -> n.duress(tenant, event(AlarmSource.DURESS, AlarmSeverity.CRITICAL)));
        verify(ns, never()).send(any());
    }

    @Test void sendFailureIsSwallowed() {
        when(rec.resolveTenantAdmins(tenant)).thenReturn(List.of(Recipient.user(UUID.randomUUID(), "A B", "a@b.c", null)));
        doThrow(new RuntimeException("smtp down")).when(ns).send(any());
        assertDoesNotThrow(() -> n.duress(tenant, event(AlarmSource.DURESS, AlarmSeverity.CRITICAL)));
    }

    @Test void criticalIncidentSendsIncidentReportedWithSms() {
        when(rec.resolveTenantAdmins(tenant)).thenReturn(List.of(Recipient.user(UUID.randomUUID(), "A B", "a@b.c", "0820000000")));
        n.criticalIncident(tenant, UUID.randomUUID(), "Armed robbery", "Sandton Mall");
        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(ns).send(c.capture());
        assertEquals(NotificationType.INCIDENT_REPORTED, c.getValue().type());
        assertTrue(c.getValue().channels().contains(NotificationChannel.SMS));
    }
}
