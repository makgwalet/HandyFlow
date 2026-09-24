package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClient;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.dto.CreateComplianceClientRequest;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.crm.CustomerSummary;
import za.co.handyflow.platform.crm.domain.model.CustomerStatus;
import za.co.handyflow.platform.crm.domain.model.CustomerType;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Regression test for ComplianceClientService — Phase 1 of
 * complianceservices. Confirms the CRM-reference validation shape:
 * strict at write time (can't link a nonexistent or already-linked CRM
 * customer), tolerant at read time (a since-deleted CRM customer doesn't
 * make an existing client record unreadable).
 */
@ExtendWith(MockitoExtension.class)
class ComplianceClientServiceTest {

    @Mock private ComplianceClientRepository clientRepository;
    @Mock private CrmFacade crmFacade;

    private ComplianceClientService service() {
        return new ComplianceClientService(clientRepository, crmFacade);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    private static CustomerSummary customer(UUID id, String name) {
        return new CustomerSummary(id, name, "customer@example.com", null, null, null, CustomerType.LEAD, CustomerStatus.ACTIVE);
    }

    @Test
    @DisplayName("create() with no CRM link succeeds — a client company doesn't need to already be a CRM customer")
    void create_withNoCrmLink_succeeds() {
        var req = new CreateComplianceClientRequest("Acme Construction", null, "ops@acme.com", null, "Signed mandate 2026-01-15");

        var response = service().create(TENANT, req, USER);

        assertThat(response.name()).isEqualTo("Acme Construction");
        assertThat(response.crmCustomerId()).isNull();
        assertThat(response.crmCustomerFound()).isFalse();
    }

    @Test
    @DisplayName("create() rejects a crmCustomerId that doesn't exist in CRM")
    void create_unknownCrmCustomer_rejects() {
        UUID crmCustomerId = UUID.randomUUID();
        when(crmFacade.customerExists(TENANT, crmCustomerId)).thenReturn(false);

        var req = new CreateComplianceClientRequest("Acme Construction", crmCustomerId, null, null, null);

        assertThatThrownBy(() -> service().create(TENANT, req, USER))
                .isInstanceOf(HandyFlowException.class);
    }

    @Test
    @DisplayName("create() rejects a CRM customer already linked to another client")
    void create_alreadyLinkedCrmCustomer_rejects() {
        UUID crmCustomerId = UUID.randomUUID();
        ComplianceClient existing = ComplianceClient.create(TENANT, "Existing Client", crmCustomerId, null, null, null, USER);
        when(crmFacade.customerExists(TENANT, crmCustomerId)).thenReturn(true);
        when(clientRepository.findByCrmCustomerId(TENANT, crmCustomerId)).thenReturn(Optional.of(existing));

        var req = new CreateComplianceClientRequest("Duplicate Attempt", crmCustomerId, null, null, null);

        assertThatThrownBy(() -> service().create(TENANT, req, USER))
                .isInstanceOf(HandyFlowException.class)
                .hasMessageContaining("Existing Client");
    }

    @Test
    @DisplayName("create() with a valid, unlinked CRM customer succeeds and the response reflects live CRM data")
    void create_validCrmCustomer_succeeds() {
        UUID crmCustomerId = UUID.randomUUID();
        when(crmFacade.customerExists(TENANT, crmCustomerId)).thenReturn(true);
        when(clientRepository.findByCrmCustomerId(TENANT, crmCustomerId)).thenReturn(Optional.empty());
        when(crmFacade.findCustomerById(TENANT, crmCustomerId)).thenReturn(Optional.of(customer(crmCustomerId, "Acme (Pty) Ltd")));

        var req = new CreateComplianceClientRequest("Acme Construction", crmCustomerId, null, null, null);
        var response = service().create(TENANT, req, USER);

        assertThat(response.crmCustomerFound()).isTrue();
        assertThat(response.crmCustomerName()).isEqualTo("Acme (Pty) Ltd");
    }

    @Test
    @DisplayName("getClient tolerates a since-deleted CRM customer -- reports crmCustomerFound=false, doesn't throw")
    void getClient_deletedCrmCustomer_toleratesGracefully() {
        UUID id = UUID.randomUUID();
        UUID crmCustomerId = UUID.randomUUID();
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme Construction", crmCustomerId, null, null, null, USER);
        when(clientRepository.findByIdForTenant(TENANT, id)).thenReturn(Optional.of(client));
        when(crmFacade.findCustomerById(TENANT, crmCustomerId)).thenReturn(Optional.empty()); // since removed from CRM

        var response = service().getClient(TENANT, id);

        assertThat(response.crmCustomerFound()).isFalse();
        assertThat(response.crmCustomerName()).isNull();
        assertThat(response.name()).isEqualTo("Acme Construction"); // the client's own name is unaffected
    }

    @Test
    @DisplayName("getClient throws for an unknown id rather than returning null")
    void getClient_unknownId_throws() {
        UUID id = UUID.randomUUID();
        when(clientRepository.findByIdForTenant(TENANT, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getClient(TENANT, id))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
