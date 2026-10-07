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
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceRegistration;
import za.co.handyflow.platform.complianceservices.dto.UpdateClientComplianceRegistrationRequest;
import za.co.handyflow.platform.shared.BusinessException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
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

    @Test
    @DisplayName("create() records a registration whose expiry has passed as Expired, not Active")
    void create_pastExpiry_isExpired() {
        UUID clientId = UUID.randomUUID();
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(client));

        var req = new CreateClientComplianceRegistrationRequest("NHBRC", "Home Builder Registration", "N1", null, LocalDate.now().minusDays(5), null);
        assertThat(service().create(TENANT, clientId, req, USER).status()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("create() keeps a registration that expires today Active")
    void create_expiresToday_staysActive() {
        UUID clientId = UUID.randomUUID();
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(client));

        var req = new CreateClientComplianceRegistrationRequest("CSD", "Supplier Registration", "S1", null,
                LocalDate.now(java.time.ZoneId.of("Africa/Johannesburg")), null);
        assertThat(service().create(TENANT, clientId, req, USER).status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("update() refuses Active with a past expiry, and allows Expired")
    void update_activeWithPastExpiry_refused() {
        UUID id = UUID.randomUUID();
        ClientComplianceRegistration existing = ClientComplianceRegistration.create(TENANT, UUID.randomUUID(), "CIPC", "Business Registration", "1", null, null, null, USER);
        when(registrationRepository.findByIdForTenant(TENANT, id)).thenReturn(Optional.of(existing));
        LocalDate past = LocalDate.now().minusDays(3);

        assertThatThrownBy(() -> service().update(TENANT, id, new UpdateClientComplianceRegistrationRequest("1", "ACTIVE", null, past, null), USER))
                .isInstanceOf(BusinessException.class).hasMessageContaining("cannot be Active");
        assertThat(service().update(TENANT, id, new UpdateClientComplianceRegistrationRequest("1", "EXPIRED", null, past, null), USER).status()).isEqualTo("EXPIRED");
    }
}
