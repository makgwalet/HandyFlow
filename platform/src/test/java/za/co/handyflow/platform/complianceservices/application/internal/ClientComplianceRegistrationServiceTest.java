package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClient;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceRegistrationRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.dto.CreateClientComplianceRegistrationRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Regression test for ClientComplianceRegistrationService. The behaviour
 * that's new relative to compliancetender's own
 * ComplianceRegistrationService: every operation is additionally scoped
 * to a client, and creating (or listing) a registration under a client
 * that doesn't belong to this tenant fails clearly rather than silently
 * operating on the wrong data.
 */
@ExtendWith(MockitoExtension.class)
class ClientComplianceRegistrationServiceTest {

    @Mock private ClientComplianceRegistrationRepository registrationRepository;
    @Mock private ComplianceClientRepository clientRepository;

    private ClientComplianceRegistrationService service() {
        return new ClientComplianceRegistrationService(registrationRepository, clientRepository);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    @Test
    @DisplayName("create() rejects a clientId that doesn't belong to this tenant")
    void create_unknownClient_rejects() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.empty());

        var req = new CreateClientComplianceRegistrationRequest("CIPC", "Business Registration", null, null, null, null);

        assertThatThrownBy(() -> service().create(TENANT, clientId, req, USER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("create() succeeds for a real client and the response reflects the request")
    void create_realClient_succeeds() {
        UUID clientId = UUID.randomUUID();
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(client));

        var req = new CreateClientComplianceRegistrationRequest("PSIRA", "Business Registration",
                "1234567", null, null, null);
        var response = service().create(TENANT, clientId, req, USER);

        assertThat(response.clientId()).isEqualTo(clientId);
        assertThat(response.authority()).isEqualTo("PSIRA");
    }

    @Test
    @DisplayName("getRegistrations rejects a clientId that doesn't belong to this tenant, rather than returning an empty list")
    void getRegistrations_unknownClient_rejects() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getRegistrations(TENANT, clientId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
