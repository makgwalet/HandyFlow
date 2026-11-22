package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.notifications.domain.model.NotificationSeverity;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.security.domain.model.AlarmEvent;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Set;

/**
 * Alerts web users (control room / admins) when a duress or CRITICAL alarm is
 * raised. NEVER throws: a failed notification must not roll back or fail the
 * alarm itself.
 *
 * Recipients come from TenantAdminRecipients (first 5 active users of the
 * tenant), not from a control-room role. SMS needs a configured SmsSender.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SecurityAlertNotifier {

    private static final Set<NotificationChannel> CHANNELS =
            Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL, NotificationChannel.SMS);

    private final NotificationService notificationService;
    private final TenantAdminRecipients recipients;
    private final GuardRepository guardRepository;

    void duress(TenantId tenantId, AlarmEvent event) {
        try {
            String first = null;
            if (event.getTriggeredByGuardId() != null) {
                first = guardRepository.findActiveById(tenantId, event.getTriggeredByGuardId())
                        .map(g -> g.getFirstName()).orElse(null);
            }
            AlarmAlertText.Text t = AlarmAlertText.duress(first, null);
            send(tenantId, event.getId().toString(), NotificationType.DURESS_TRIGGERED, t);
        } catch (Exception e) {
            log.error("[Security] Duress alert failed for event={}: {}", event.getId(), e.getMessage(), e);
        }
    }

    /** CRITICAL alarm from any source other than DURESS (which has its own alert). */
    void criticalAlarm(TenantId tenantId, AlarmEvent event) {
        try {
            if (event.getSeverity() != AlarmEvent.AlarmSeverity.CRITICAL
                    || event.getSource() == AlarmEvent.AlarmSource.DURESS) return;
            AlarmAlertText.Text t = AlarmAlertText.critical(event.getSource().name(), event.getDescription());
            send(tenantId, event.getId().toString(), NotificationType.ALARM_EVENT_RAISED, t);
        } catch (Exception e) {
            log.error("[Security] Critical alarm alert failed for event={}: {}", event.getId(), e.getMessage(), e);
        }
    }

    /** A CRITICAL incident report (from a guard or the control room). */
    void criticalIncident(TenantId tenantId, java.util.UUID incidentId, String title, String siteName) {
        try {
            AlarmAlertText.Text t = AlarmAlertText.incident(title, siteName);
            send(tenantId, incidentId.toString(), NotificationType.INCIDENT_REPORTED, t);
        } catch (Exception e) {
            log.error("[Security] Critical incident alert failed for incident={}: {}", incidentId, e.getMessage(), e);
        }
    }

    /** A firearm reported lost or stolen. */
    void firearmLost(TenantId tenantId, java.util.UUID armouryId, String serial, boolean hadHolder) {
        try {
            AlarmAlertText.Text t = AlarmAlertText.firearmLost(serial, hadHolder);
            send(tenantId, armouryId.toString(), NotificationType.INCIDENT_REPORTED, t);
        } catch (Exception e) {
            log.error("[Security] Lost firearm alert failed for armoury={}: {}", armouryId, e.getMessage(), e);
        }
    }

    /** A lost firearm has gone 24 hours without a SAPS case number. */
    void sapsOverdue(TenantId tenantId, java.util.UUID armouryId, String serial) {
        try {
            AlarmAlertText.Text t = AlarmAlertText.sapsOverdue(serial);
            send(tenantId, armouryId.toString(), NotificationType.INCIDENT_REPORTED, t);
        } catch (Exception e) {
            log.error("[Security] SAPS overdue alert failed for armoury={}: {}", armouryId, e.getMessage(), e);
        }
    }

    private void send(TenantId tenantId, String entityId, NotificationType type, AlarmAlertText.Text t) {
        List<Recipient> to = SecurityRecipients.forAlerts(recipients, tenantId);
        if (to.isEmpty()) {
            log.warn("[Security] No recipients to alert for entity={} tenant={}", entityId, tenantId.getValue());
            return;
        }
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(type)
                .severity(NotificationSeverity.CRITICAL)
                .channels(CHANNELS)
                .title(t.title())
                .message(t.message())
                .actionUrl("/security")
                .sourceModule("security")
                .sourceEntityId(entityId)
                .recipients(to)
                .build());
    }
}
