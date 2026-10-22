package za.co.handyflow.platform.recruiter.application.internal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RecruiterApprovalNotifierTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID maker = UUID.randomUUID(), other = UUID.randomUUID(), ref = UUID.randomUUID();
    private final NotificationService notifications = mock(NotificationService.class);
    private final TenantAdminRecipients admins = mock(TenantAdminRecipients.class);
    private final UserRecipientResolver users = mock(UserRecipientResolver.class);
    private final RecruiterApprovalNotifier notifier = new RecruiterApprovalNotifier(notifications, admins, users);

    private Recipient user(UUID id, String name) { return Recipient.user(id, name, name.toLowerCase().replace(' ', '.') + "@x.co.za", null); }

    @Test
    void approversAreTheAdministratorsExceptTheSubmitter() {
        Recipient me = user(maker, "Thandi Mokoena"), boss = user(other, "Sam Boss");
        when(admins.resolveTenantAdmins(tenant)).thenReturn(List.of(me, boss));
        when(users.resolveUser(tenant, maker)).thenReturn(Optional.of(me));
        notifier.requested(tenant, true, ref, "Clerk", maker);
        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(c.capture());
        assertThat(c.getValue().type()).isEqualTo(NotificationType.REC_APPROVAL_REQUESTED);
        assertThat(c.getValue().recipients()).containsExactly(boss);
        assertThat(c.getValue().message()).contains("Thandi Mokoena").contains("Clerk");
    }

    @Test
    void nothingIsSentWhenTheSubmitterIsTheOnlyAdministrator() {
        Recipient me = user(maker, "Thandi Mokoena");
        when(admins.resolveTenantAdmins(tenant)).thenReturn(List.of(me));
        when(users.resolveUser(tenant, maker)).thenReturn(Optional.of(me));
        notifier.requested(tenant, false, ref, "Clerk", maker);
        verify(notifications, never()).send(any());
    }

    @Test
    void theSubmitterIsToldTheOutcome() {
        Recipient me = user(maker, "Thandi Mokoena"), boss = user(other, "Sam Boss");
        when(users.resolveUser(tenant, maker)).thenReturn(Optional.of(me));
        when(users.resolveUser(tenant, other)).thenReturn(Optional.of(boss));
        notifier.decided(tenant, false, ref, "Clerk", maker, other, false);
        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(c.capture());
        assertThat(c.getValue().type()).isEqualTo(NotificationType.REC_APPROVAL_DECIDED);
        assertThat(c.getValue().recipients()).containsExactly(me);
        assertThat(c.getValue().title()).isEqualTo("Your offer was not approved");
    }

    @Test
    void aFailureNeverEscapes() {
        when(admins.resolveTenantAdmins(tenant)).thenThrow(new IllegalStateException("down"));
        notifier.requested(tenant, true, ref, "Clerk", maker);
        verify(notifications, never()).send(any());
    }
}
