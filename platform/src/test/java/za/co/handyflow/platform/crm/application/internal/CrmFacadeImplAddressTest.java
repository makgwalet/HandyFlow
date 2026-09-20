package za.co.handyflow.platform.crm.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.crm.domain.model.Customer;
import za.co.handyflow.platform.crm.domain.model.CustomerType;
import za.co.handyflow.platform.crm.domain.repository.CustomerCommunicationRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerConsentRepository;
import za.co.handyflow.platform.crm.domain.repository.CustomerRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Regression test for a real gap found via a real customer-facing PDF:
 * CustomerSummary (the DTO every other module, including invoicing's PDF
 * generators, actually receives) didn't carry Customer.address at all,
 * even though the entity itself had captured it — so a tax invoice's
 * BILL TO block could never show a recipient's address, no matter what
 * was on file. Confirms the address now flows through both
 * CrmFacadeImpl methods that build CustomerSummary.
 */
@ExtendWith(MockitoExtension.class)
class CrmFacadeImplAddressTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerConsentRepository customerConsentRepository;
    @Mock
    private CustomerCommunicationRepository communicationRepository;

    private CrmFacadeImpl facade() {
        return new CrmFacadeImpl(customerRepository, customerConsentRepository, communicationRepository);
    }

    private static final Map<String, String> ADDRESS = Map.of(
            "street", "12 Mine Road", "suburb", "Carletonville", "city", "Merafong", "postalCode", "2499");

    @Test
    @DisplayName("findCustomerById carries the customer's address into CustomerSummary")
    void findCustomerById_carriesAddress() {
        TenantId tenantId = TenantId.generate();
        Customer customer = Customer.create(tenantId, "Black Flamingo", "makgwalet@gmail.com",
                "0787973308", ADDRESS, "987456212", null, CustomerType.CUSTOMER, UUID.randomUUID());

        when(customerRepository.findActiveById(tenantId, customer.getId()))
                .thenReturn(Optional.of(customer));

        Optional<CustomerSummary> result = facade().findCustomerById(tenantId, customer.getId());

        assertThat(result).isPresent();
        assertThat(result.get().address()).isEqualTo(ADDRESS);
    }

    @Test
    @DisplayName("findCustomerById returns a null address, not a crash, when the customer has none on file")
    void findCustomerById_noAddressOnFile_returnsNull() {
        TenantId tenantId = TenantId.generate();
        Customer customer = Customer.create(tenantId, "Walk-in Client", "walkin@example.com",
                null, null, null, null, CustomerType.CUSTOMER, UUID.randomUUID());

        when(customerRepository.findActiveById(tenantId, customer.getId()))
                .thenReturn(Optional.of(customer));

        Optional<CustomerSummary> result = facade().findCustomerById(tenantId, customer.getId());

        assertThat(result).isPresent();
        assertThat(result.get().address()).isNull();
    }

    @Test
    @DisplayName("findActiveCustomersWithEmail also carries address through")
    void findActiveCustomersWithEmail_carriesAddress() {
        TenantId tenantId = TenantId.generate();
        Customer customer = Customer.create(tenantId, "Black Flamingo", "makgwalet@gmail.com",
                "0787973308", ADDRESS, "987456212", null, CustomerType.CUSTOMER, UUID.randomUUID());

        when(customerRepository.findAllActiveWithEmail(tenantId)).thenReturn(List.of(customer));

        List<CustomerSummary> result = facade().findActiveCustomersWithEmail(tenantId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).address()).isEqualTo(ADDRESS);
    }
}
