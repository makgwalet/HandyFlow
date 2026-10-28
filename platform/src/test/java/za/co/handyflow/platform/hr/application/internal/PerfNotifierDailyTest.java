package za.co.handyflow.platform.hr.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import za.co.handyflow.platform.hr.domain.repository.HrEmployeeRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** A morning reminder goes to a person once a day for the same words, however many times the job runs. */
class PerfNotifierDailyTest {

    final TenantId tenant = TenantId.of(UUID.randomUUID());
    final UUID ref = UUID.randomUUID();
    NotificationService notifications;
    ReminderLedger ledger;
    PerfNotifier notifier;
    final Recipient ann = Recipient.external("Ann", "ann@x.co.za", null);
    final Recipient bob = Recipient.external("Bob", "bob@x.co.za", null);

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationService.class);
        ledger = mock(ReminderLedger.class);
        notifier = new PerfNotifier(notifications, mock(HrEmployeeRepository.class));
        notifier.ledger = ledger;
    }

    private java.util.List<Recipient> sentTo() {
        ArgumentCaptor<NotificationRequest> c = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications, atLeast(0)).send(c.capture());
        return c.getAllValues().stream().flatMap(r -> r.recipients().stream()).toList();
    }

    @Test
    void aFirstReminderIsSentAndClaimed() {
        when(ledger.claim(any(), any(), any())).thenReturn(true);
        notifier.send(tenant, NotificationType.ER_CASE_DUE, "t", "m", ref, "/u", ann);
        assertThat(sentTo()).containsExactly(ann);
        verify(ledger).claim(eq(tenant), any(LocalDate.class), any());
    }

    @Test
    void theSameReminderAgainTodayIsNotSent() {
        when(ledger.claim(any(), any(), any())).thenReturn(false);
        notifier.send(tenant, NotificationType.ER_CASE_DUE, "t", "m", ref, "/u", ann);
        verifyNoInteractions(notifications);
    }

    @Test
    void onlyThePeopleNotYetToldAreSent() {
        when(ledger.claim(any(), any(), any())).thenReturn(false, true);
        notifier.send(tenant, NotificationType.LEAVE_AT_RISK, "t", "m", ref, "/u", ann, bob);
        assertThat(sentTo()).containsExactly(bob);
    }

    @Test
    void aFailedSendGivesTheClaimBack() {
        when(ledger.claim(any(), any(), any())).thenReturn(true);
        doThrow(new RuntimeException("down")).when(notifications).send(any());
        notifier.send(tenant, NotificationType.ER_CASE_DUE, "t", "m", ref, "/u", ann);
        verify(ledger).release(eq(tenant), any(LocalDate.class), any());
    }

    @Test
    void anEventNotificationIsNeverHeldBack() {
        notifier.send(tenant, NotificationType.PERF_GOAL_DECIDED, "t", "m", ref, "/u", ann);
        notifier.send(tenant, NotificationType.PERF_GOAL_DECIDED, "t", "m", ref, "/u", ann);
        verify(notifications, times(2)).send(any());
        verifyNoInteractions(ledger);
    }

    @Test
    void ifTheLedgerIsDownTheReminderIsStillSent() {
        when(ledger.claim(any(), any(), any())).thenThrow(new RuntimeException("db"));
        notifier.send(tenant, NotificationType.ER_CASE_DUE, "t", "m", ref, "/u", ann);
        assertThat(sentTo()).containsExactly(ann);
    }
}
