package za.co.handyflow.platform.compliancetender.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceDeadline;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceRegistration;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceDeadlineRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceRegistrationRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationSeverity;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceExpirySchedulerTest {

    @Mock private ComplianceRegistrationRepository registrationRepository;
    @Mock private ComplianceDeadlineRepository deadlineRepository;
    @Mock private NotificationService notificationService;
    @Mock private TenantAdminRecipients tenantAdminRecipients;

    private ComplianceExpiryScheduler scheduler() {
        return new ComplianceExpiryScheduler(registrationRepository, deadlineRepository,
                notificationService, tenantAdminRecipients);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    private static List<Recipient> oneAdmin() {
        return List.of(Recipient.user(UUID.randomUUID(), "Admin", "admin@example.com", null));
    }

    @Test
    @DisplayName("an already-expired registration sends a CRITICAL COMPLIANCE_REGISTRATION_EXPIRED alert")
    void expiredRegistration_sendsCriticalAlert() {
        ComplianceRegistration expired = ComplianceRegistration.create(TENANT, "PSIRA", "Business Registration",
                null, LocalDate.now().minusYears(1), LocalDate.now().minusDays(1), null, USER);
        when(tenantAdminRecipients.resolveTenantAdmins(TENANT)).thenReturn(oneAdmin());

        int alerts = scheduler().checkForTenant(TENANT, LocalDate.now(), List.of(expired), List.of());

        assertThat(alerts).isEqualTo(1);
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.COMPLIANCE_REGISTRATION_EXPIRED);
        assertThat(captor.getValue().severity()).isEqualTo(NotificationSeverity.CRITICAL);
    }

    @Test
    @DisplayName("a registration expiring soon (not yet expired) sends a WARNING alert, not CRITICAL")
    void expiringRegistration_sendsWarningAlert() {
        ComplianceRegistration expiringSoon = ComplianceRegistration.create(TENANT, "CIDB", "Grade 4GB",
                null, LocalDate.now().minusYears(2), LocalDate.now().plusDays(10), null, USER);
        when(tenantAdminRecipients.resolveTenantAdmins(TENANT)).thenReturn(oneAdmin());

        int alerts = scheduler().checkForTenant(TENANT, LocalDate.now(), List.of(expiringSoon), List.of());

        assertThat(alerts).isEqualTo(1);
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.COMPLIANCE_REGISTRATION_EXPIRING);
        assertThat(captor.getValue().severity()).isEqualTo(NotificationSeverity.WARNING);
    }

    @Test
    @DisplayName("a due deadline sends a separate COMPLIANCE_DEADLINE_DUE alert from any registration alert")
    void dueDeadline_sendsSeparateAlert() {
        ComplianceDeadline deadline = ComplianceDeadline.create(TENANT, null, "ANNUAL_RETURN",
                "CIPC annual return", LocalDate.now().plusDays(5), USER);
        when(tenantAdminRecipients.resolveTenantAdmins(TENANT)).thenReturn(oneAdmin());

        int alerts = scheduler().checkForTenant(TENANT, LocalDate.now(), List.of(), List.of(deadline));

        assertThat(alerts).isEqualTo(1);
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.COMPLIANCE_DEADLINE_DUE);
    }

    @Test
    @DisplayName("nothing due sends no notification at all and returns 0")
    void nothingDue_sendsNothing() {
        int alerts = scheduler().checkForTenant(TENANT, LocalDate.now(), List.of(), List.of());

        assertThat(alerts).isEqualTo(0);
        verify(notificationService, never()).send(any());
    }

    @Test
    @DisplayName("no resolvable admin recipients still counts the issues, but sends no notification")
    void noAdmins_countsButDoesNotSend() {
        ComplianceRegistration expired = ComplianceRegistration.create(TENANT, "SARS", "Income Tax",
                null, LocalDate.now().minusYears(1), LocalDate.now().minusDays(1), null, USER);
        when(tenantAdminRecipients.resolveTenantAdmins(TENANT)).thenReturn(List.of());

        int alerts = scheduler().checkForTenant(TENANT, LocalDate.now(), List.of(expired), List.of());

        assertThat(alerts).isEqualTo(1);
        verify(notificationService, never()).send(any());
    }

    @Test
    @DisplayName("both an expired registration and a due deadline together send two separate alerts")
    void expiredRegistrationAndDueDeadline_sendsTwoAlerts() {
        ComplianceRegistration expired = ComplianceRegistration.create(TENANT, "PSIRA", "Business Registration",
                null, LocalDate.now().minusYears(1), LocalDate.now().minusDays(1), null, USER);
        ComplianceDeadline deadline = ComplianceDeadline.create(TENANT, null, "ANNUAL_RETURN",
                null, LocalDate.now().plusDays(5), USER);
        when(tenantAdminRecipients.resolveTenantAdmins(TENANT)).thenReturn(oneAdmin());

        int alerts = scheduler().checkForTenant(TENANT, LocalDate.now(), List.of(expired), List.of(deadline));

        assertThat(alerts).isEqualTo(2);
        verify(notificationService, org.mockito.Mockito.times(2)).send(any());
    }
}
