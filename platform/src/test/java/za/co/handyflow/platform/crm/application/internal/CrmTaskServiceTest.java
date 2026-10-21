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
import za.co.handyflow.platform.crm.dto.UpdateTaskRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrmTaskServiceTest {

    @Mock private CustomerFollowUpRepository followUps;
    @Mock private CustomerRepository customers;

    private final TenantId tenant = TenantId.generate();
    private final UUID user = UUID.randomUUID();

    private CrmTaskService service() { return new CrmTaskService(followUps, customers); }

    private Customer customer(UUID id, String name, TenantId owner, boolean deleted) {
        Customer c = mock(Customer.class);
        lenient().when(c.getId()).thenReturn(id);
        lenient().when(c.getName()).thenReturn(name);
        lenient().when(c.getTenantId()).thenReturn(owner);
        lenient().when(c.getDeletedAt()).thenReturn(deleted ? java.time.Instant.now() : null);
        return c;
    }

    private CustomerFollowUp task(UUID customerId, LocalDate due) {
        return CustomerFollowUp.create(tenant, customerId, due, "Call back", user, user);
    }

    @Test @DisplayName("the queue shows the customer's name and how late a task is")
    void listsWithNamesAndLateness() {
        UUID cid = UUID.randomUUID();
        when(followUps.findOpen(any(), any())).thenReturn(List.of(task(cid, LocalDate.now().minusDays(4)), task(cid, LocalDate.now().plusDays(2))));
        when(customers.findAllById(any())).thenReturn(List.of(customer(cid, "Acme Mining", tenant, false)));
        var result = service().list(tenant, false);
        assertThat(result.tasks()).hasSize(2);
        assertThat(result.tasks().get(0).customerName()).isEqualTo("Acme Mining");
        assertThat(result.tasks().get(0).overdue()).isTrue();
        assertThat(result.tasks().get(0).daysOverdue()).isEqualTo(4);
        assertThat(result.tasks().get(1).overdue()).isFalse();
        assertThat(result.truncated()).isFalse();
    }

    @Test @DisplayName("tasks of deleted customers, or of another tenant's customers, are not shown")
    void hidesDeletedAndForeign() {
        UUID live = UUID.randomUUID(), gone = UUID.randomUUID(), foreign = UUID.randomUUID();
        when(followUps.findOpen(any(), any())).thenReturn(List.of(task(live, LocalDate.now()), task(gone, LocalDate.now()), task(foreign, LocalDate.now())));
        when(customers.findAllById(any())).thenReturn(List.of(customer(live, "Live", tenant, false), customer(gone, "Gone", tenant, true), customer(foreign, "Other", TenantId.generate(), false)));
        assertThat(service().list(tenant, false).tasks()).extracting(t -> t.customerName()).containsExactly("Live");
    }

    @Test @DisplayName("more tasks than the limit are cut and flagged")
    void truncates() {
        UUID cid = UUID.randomUUID();
        List<CustomerFollowUp> many = new ArrayList<>();
        for (int i = 0; i < CrmTaskService.OPEN_LIMIT + 1; i++) many.add(task(cid, LocalDate.now()));
        when(followUps.findOpen(any(), any())).thenReturn(many);
        when(customers.findAllById(any())).thenReturn(List.of(customer(cid, "Acme", tenant, false)));
        var result = service().list(tenant, false);
        assertThat(result.truncated()).isTrue();
        assertThat(result.tasks()).hasSize(CrmTaskService.OPEN_LIMIT);
    }

    @Test @DisplayName("an open task can be snoozed, reassigned and re-prioritised")
    void updates() {
        UUID cid = UUID.randomUUID(), other = UUID.randomUUID();
        var t = task(cid, LocalDate.now());
        when(followUps.findByIdAndTenant(any(), any())).thenReturn(Optional.of(t));
        when(customers.findAllById(any())).thenReturn(List.of(customer(cid, "Acme", tenant, false)));
        var out = service().update(tenant, UUID.randomUUID(), new UpdateTaskRequest(other, LocalDate.now().plusDays(7), CustomerFollowUp.Priority.HIGH));
        assertThat(out.dueDate()).isEqualTo(LocalDate.now().plusDays(7));
        assertThat(out.assignedTo()).isEqualTo(other);
        assertThat(out.priority()).isEqualTo("HIGH");
    }

    @Test @DisplayName("a past due date is refused, and a finished task cannot be changed")
    void updateRules() {
        var open = task(UUID.randomUUID(), LocalDate.now());
        when(followUps.findByIdAndTenant(any(), any())).thenReturn(Optional.of(open));
        assertThatThrownBy(() -> service().update(tenant, UUID.randomUUID(), new UpdateTaskRequest(null, LocalDate.now().minusDays(1), null)))
                .isInstanceOf(IllegalArgumentException.class);
        open.complete(CustomerFollowUp.Outcome.COMPLETED, user);
        assertThatThrownBy(() -> service().update(tenant, UUID.randomUUID(), new UpdateTaskRequest(null, null, CustomerFollowUp.Priority.LOW)))
                .isInstanceOf(IllegalStateException.class);
    }
}
