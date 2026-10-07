package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.model.GuardCompetency;
import za.co.handyflow.platform.security.domain.model.GuardCompetency.Type;
import za.co.handyflow.platform.security.domain.repository.GuardCompetencyRepository;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Daily competency expiry digest. */
class GuardCompetencyExpirySchedulerTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);

    private final GuardCompetencyRepository competencies = mock(GuardCompetencyRepository.class);
    private final GuardRepository guards = mock(GuardRepository.class);
    private final SiteRepository sites = mock(SiteRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final TenantAdminRecipients recipients = mock(TenantAdminRecipients.class);
    private final GuardCompetencyExpiryScheduler scheduler = new GuardCompetencyExpiryScheduler(competencies, guards, sites, notifications, recipients);

    private GuardCompetency competency(Guard g, Type type, String title, LocalDate expiry) {
        return GuardCompetency.create(TENANT, g.getId(), type, title, null, null, expiry, null, true, null, null);
    }

    @Test @DisplayName("Looks 30 days ahead and sends one digest naming the guard and the competency")
    void sendsDigest() {
        Guard g = Guard.create(TENANT, "Gerhard", "Botha", "P1", "id", "082", "C", null, null, null);
        var due = List.of(competency(g, Type.FIRST_AID, null, TODAY.plusDays(12)), competency(g, Type.DRIVER, "Code 10 licence", TODAY.plusDays(30)));
        when(competencies.findExpiringBetween(TENANT, TODAY, TODAY.plusDays(30))).thenReturn(due);
        when(guards.findActiveById(TENANT, g.getId())).thenReturn(Optional.of(g));
        when(recipients.resolveTenantAdmins(TENANT)).thenReturn(List.of(Recipient.external("Admin", "a@x.co.za", null)));

        scheduler.checkForTenant(TENANT, TODAY);

        var captor = org.mockito.ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.GUARD_COMPETENCY_EXPIRING);
        assertThat(captor.getValue().message()).contains("Gerhard Botha (first aid, expires " + TODAY.plusDays(12) + ")")
                .contains("Code 10 licence");
    }

    @Test @DisplayName("Sends nothing when nothing is due, or when no administrator can be reached")
    void quiet() {
        when(competencies.findExpiringBetween(TENANT, TODAY, TODAY.plusDays(30))).thenReturn(List.of());
        scheduler.checkForTenant(TENANT, TODAY);
        Guard g = Guard.create(TENANT, "A", "B", "P1", "id", "082", "C", null, null, null);
        when(competencies.findExpiringBetween(TENANT, TODAY, TODAY.plusDays(30))).thenReturn(List.of(competency(g, Type.CCTV, null, TODAY.plusDays(3))));
        when(recipients.resolveTenantAdmins(TENANT)).thenReturn(List.of());
        scheduler.checkForTenant(TENANT, TODAY);
        verify(notifications, never()).send(any());
    }
}
