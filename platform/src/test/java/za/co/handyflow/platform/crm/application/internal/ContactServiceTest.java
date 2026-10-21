package za.co.handyflow.platform.crm.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.crm.domain.model.Contact;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.repository.ContactRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.crm.dto.ContactRequest;
import za.co.handyflow.platform.shared.TenantId;

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
class ContactServiceTest {

    @Mock private ContactRepository contacts;
    @Mock private CustomerRepository customers;

    private final TenantId tenant = TenantId.generate();
    private final UUID customerId = UUID.randomUUID();
    private Customer customer;
    private final List<Contact> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        customer = mock(Customer.class);
        lenient().when(customer.getId()).thenReturn(customerId);
        lenient().when(customers.findActiveById(tenant, customerId)).thenReturn(Optional.of(customer));
        lenient().when(customers.existsActiveById(tenant, customerId)).thenReturn(true);
        lenient().when(contacts.findActiveByCustomer(tenant, customerId)).thenAnswer(i -> stored.stream().filter(c -> !c.isDeleted()).toList());
        lenient().when(contacts.save(any(Contact.class))).thenAnswer(i -> { Contact c = i.getArgument(0); stored.add(c); return c; });
        lenient().when(contacts.findActiveById(any(), any(), any())).thenAnswer(i ->
                stored.stream().filter(c -> !c.isDeleted() && c.getId().equals(i.getArgument(2))).findFirst());
    }

    private ContactService service() { return new ContactService(contacts, customers); }

    private ContactRequest req(String first, String email, boolean primary) {
        return new ContactRequest(first, "Smith", email, "0821234567", "Buyer", null, primary);
    }

    @Test @DisplayName("the first contact becomes the main one")
    void firstIsMain() {
        assertThat(service().create(tenant, customerId, req("Ann", "ann@x.co", false)).primary()).isTrue();
        assertThat(service().create(tenant, customerId, req("Ben", "ben@x.co", false)).primary()).isFalse();
    }

    @Test @DisplayName("making a new contact main demotes the old one, so there is only ever one")
    void onlyOneMain() {
        service().create(tenant, customerId, req("Ann", "ann@x.co", false));
        service().create(tenant, customerId, req("Ben", "ben@x.co", true));
        assertThat(stored.stream().filter(Contact::isPrimary)).hasSize(1);
        assertThat(stored.stream().filter(Contact::isPrimary).findFirst().orElseThrow().getFirstName()).isEqualTo("Ben");
    }

    @Test @DisplayName("an email already used by another contact of this customer is refused, in any letter case")
    void duplicateEmail() {
        service().create(tenant, customerId, req("Ann", "ann@x.co", false));
        assertThatThrownBy(() -> service().create(tenant, customerId, req("Ben", "ANN@x.co", false))).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("the main contact cannot be un-set directly, but can be handed to someone else")
    void cannotUnsetMain() {
        var ann = service().create(tenant, customerId, req("Ann", "ann@x.co", false));
        var ben = service().create(tenant, customerId, req("Ben", "ben@x.co", false));
        assertThatThrownBy(() -> service().update(tenant, customerId, ann.id(), req("Ann", "ann@x.co", false))).isInstanceOf(IllegalStateException.class);
        service().makePrimary(tenant, customerId, ben.id());
        assertThat(stored.stream().filter(Contact::isPrimary).findFirst().orElseThrow().getFirstName()).isEqualTo("Ben");
    }

    @Test @DisplayName("removing the main contact promotes the earliest-added one")
    void deletePromotes() {
        var ann = service().create(tenant, customerId, req("Ann", "ann@x.co", false));
        service().create(tenant, customerId, req("Ben", "ben@x.co", false));
        service().delete(tenant, customerId, ann.id());
        assertThat(service().list(tenant, customerId)).hasSize(1);
        assertThat(service().list(tenant, customerId).get(0).primary()).isTrue();
    }
}
