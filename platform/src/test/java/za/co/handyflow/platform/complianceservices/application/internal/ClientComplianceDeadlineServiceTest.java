package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

    private ClientComplianceDeadlineService service() {
        return new ClientComplianceDeadlineService(deadlineRepository, clientRepository);
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
    @DisplayName("getPendingDeadlines rejects a clientId that doesn't belong to this tenant")
    void getPendingDeadlines_unknownClient_rejects() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getPendingDeadlines(TENANT, clientId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
