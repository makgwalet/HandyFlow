package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceRegistration;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceRegistrationRepository;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClient;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceDeadlineRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.dto.CreateClientComplianceDeadlineRequest;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientComplianceDeadlineServiceTest {

    @Mock private ClientComplianceDeadlineRepository deadlineRepository;
    @Mock private ComplianceClientRepository clientRepository;
    @Mock private ClientComplianceRegistrationRepository registrationRepository;

    private ClientComplianceDeadlineService service() {
        return new ClientComplianceDeadlineService(deadlineRepository, clientRepository, registrationRepository);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    @Test
    @DisplayName("create() rejects a clientId that doesn't belong to this tenant")
    void create_unknownClient_rejects() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.empty());

        var req = new CreateClientComplianceDeadlineRequest(null, "ANNUAL_RETURN", null, LocalDate.now().plusDays(10));

        assertThatThrownBy(() -> service().create(TENANT, clientId, req, USER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("create() succeeds for a real client and the response reflects the request")
    void create_realClient_succeeds() {
        UUID clientId = UUID.randomUUID();
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(client));

        var req = new CreateClientComplianceDeadlineRequest(null, "ANNUAL_RETURN", "CIPC annual return", LocalDate.now().plusDays(10));
        var response = service().create(TENANT, clientId, req, USER);

        assertThat(response.clientId()).isEqualTo(clientId);
        assertThat(response.deadlineType()).isEqualTo("ANNUAL_RETURN");
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("create() rejects a registration that belongs to a different client, and saves nothing")
    void create_registrationOfAnotherClient_rejects() {
        UUID clientId = UUID.randomUUID();
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);
        ClientComplianceRegistration foreign = ClientComplianceRegistration.create(TENANT, UUID.randomUUID(), "CIPC", "Business Registration", "1", null, null, null, USER);
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(client));
        when(registrationRepository.findByIdForTenant(TENANT, foreign.getId())).thenReturn(Optional.of(foreign));

        var req = new CreateClientComplianceDeadlineRequest(foreign.getId(), "ANNUAL_RETURN", null, LocalDate.now().plusDays(10));

        assertThatThrownBy(() -> service().create(TENANT, clientId, req, USER)).isInstanceOf(ResourceNotFoundException.class);
        org.mockito.Mockito.verify(deadlineRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("create() accepts a registration that belongs to this client")
    void create_registrationOfThisClient_succeeds() {
        UUID clientId = UUID.randomUUID();
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);
        ClientComplianceRegistration own = ClientComplianceRegistration.create(TENANT, clientId, "CIPC", "Business Registration", "1", null, null, null, USER);
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(client));
        when(registrationRepository.findByIdForTenant(TENANT, own.getId())).thenReturn(Optional.of(own));

        var response = service().create(TENANT, clientId, new CreateClientComplianceDeadlineRequest(own.getId(), "ANNUAL_RETURN", null, LocalDate.now().plusDays(10)), USER);

        assertThat(response.registrationId()).isEqualTo(own.getId());
    }

    @Test
    @DisplayName("getPendingDeadlines rejects a clientId that doesn't belong to this tenant")
    void getPendingDeadlines_unknownClient_rejects() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getPendingDeadlines(TENANT, clientId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
