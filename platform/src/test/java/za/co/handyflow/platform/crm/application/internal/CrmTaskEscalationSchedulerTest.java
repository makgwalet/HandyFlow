package za.co.handyflow.platform.crm.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.model.CustomerFollowUp;
import za.co.handyflow.platform.crm.domain.repository.CustomerFollowUpRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrmTaskEscalationSchedulerTest {

    @Mock private CustomerFollowUpRepository followUps;
    @Mock private CustomerRepository customers;
    @Mock private TenantAdminRecipients admins;
    @Mock private NotificationService notifications;

    private final TenantId tenant = TenantId.generate();
    private final LocalDate today = LocalDate.of(2026, 10, 21);

    private CrmTaskEscalationScheduler scheduler() { return new CrmTaskEscalationScheduler(followUps, customers, admins, notifications); }

    private CustomerFollowUp task(UUID customerId, int daysLate) {
        return CustomerFollowUp.create(tenant, customerId, today.minusDays(daysLate), "Call back", UUID.randomUUID(), UUID.randomUUID());
    }

    private Customer customer(String name) {
        Customer c = mock(Customer.class);
        when(c.getName()).thenReturn(name);
        return c;
    }

    @Test @DisplayName("nothing late means nothing sent")
    void nothingLate() {
        when(followUps.findForEscalation(tenant, today.minusDays(3))).thenReturn(List.of());
        assertThat(scheduler().escalateForTenant(tenant, today)).isZero();
        verify(notifications, never()).send(any());
    }

    @Test @DisplayName("late tasks are marked and reported in one notification to the admins")
    void reportsOnce() {
        UUID cid = UUID.randomUUID();
        var a = task(cid, 5);
        var b = task(cid, 3);
        when(followUps.findForEscalation(tenant, today.minusDays(3))).thenReturn(List.of(a, b));
        when(admins.resolveTenantAdmins(tenant)).thenReturn(List.of(new Recipient(UUID.randomUUID(), "Boss", "boss@x.co", null)));
        when(customers.findActiveById(tenant, cid)).thenReturn(Optional.of(customer("Acme")));
        assertThat(scheduler().escalateForTenant(tenant, today)).isEqualTo(2);
        assertThat(a.getEscalationSentAt()).isNotNull();
        assertThat(b.getEscalationSentAt()).isNotNull();
        verify(notifications).send(any(NotificationRequest.class));
    }

    @Test @DisplayName("with no admin to tell, tasks are left unmarked so they are reported once an admin exists")
    void noAdmins() {
        var a = task(UUID.randomUUID(), 5);
        when(followUps.findForEscalation(tenant, today.minusDays(3))).thenReturn(List.of(a));
        when(admins.resolveTenantAdmins(tenant)).thenReturn(List.of());
        assertThat(scheduler().escalateForTenant(tenant, today)).isZero();
        assertThat(a.getEscalationSentAt()).isNull();
        verify(notifications, never()).send(any());
    }

    @Test @DisplayName("a deleted customer's task is marked but not reported")
    void deletedCustomer() {
        UUID cid = UUID.randomUUID();
        var a = task(cid, 6);
        when(followUps.findForEscalation(tenant, today.minusDays(3))).thenReturn(List.of(a));
        when(admins.resolveTenantAdmins(tenant)).thenReturn(List.of(new Recipient(UUID.randomUUID(), "Boss", "boss@x.co", null)));
        when(customers.findActiveById(tenant, cid)).thenReturn(Optional.empty());
        assertThat(scheduler().escalateForTenant(tenant, today)).isZero();
        assertThat(a.getEscalationSentAt()).isNotNull();
        verify(notifications, never()).send(any());
    }

    @Test @DisplayName("rescheduling a task makes it reportable again")
    void rescheduleClears() {
        var a = task(UUID.randomUUID(), 5);
        a.markEscalated();
        a.moveDueDate(today.plusDays(1));
        assertThat(a.getEscalationSentAt()).isNull();
    }
}
