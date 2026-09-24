package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceRequirement;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClient;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceRequirementRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.dto.CreateClientComplianceRequirementRequest;
import za.co.handyflow.platform.complianceservices.dto.UpdateClientComplianceRequirementRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Regression test for ClientComplianceRequirementService — the same
 * versioning behaviour compliancetender.ComplianceRequirementServiceTest
 * already covers (duplicate-code rejection, dedup-to-latest-version,
 * stale-version rejection), plus the new client-scoping check: an
 * unknown client is rejected on both create and list.
 */
@ExtendWith(MockitoExtension.class)
class ClientComplianceRequirementServiceTest {

    @Mock private ClientComplianceRequirementRepository requirementRepository;
    @Mock private ComplianceClientRepository clientRepository;

    private ClientComplianceRequirementService service() {
        return new ClientComplianceRequirementService(requirementRepository, clientRepository);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    private ComplianceClient realClient(UUID clientId) {
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(client));
        return client;
    }

    @Test
    @DisplayName("create() rejects a clientId that doesn't belong to this tenant")
    void create_unknownClient_rejects() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.empty());

        var req = new CreateClientComplianceRequirementRequest("CSD_ACTIVE", "Valid CSD Registration", null, null, true);

        assertThatThrownBy(() -> service().create(TENANT, clientId, req, USER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("create() rejects a code that already exists for this client")
    void create_duplicateCode_rejects() {
        UUID clientId = UUID.randomUUID();
        realClient(clientId);
        ClientComplianceRequirement existing = ClientComplianceRequirement.create(TENANT, clientId, "CSD_ACTIVE",
                "Valid CSD Registration", null, null, true, USER);
        when(requirementRepository.findLatestByCode(TENANT, clientId, "CSD_ACTIVE")).thenReturn(Optional.of(existing));

        var req = new CreateClientComplianceRequirementRequest("CSD_ACTIVE", "Duplicate attempt", null, null, true);

        assertThatThrownBy(() -> service().create(TENANT, clientId, req, USER))
                .isInstanceOf(HandyFlowException.class)
                .hasMessageContaining("new version");
    }

    @Test
    @DisplayName("getRequirements dedupes to the latest version per code, scoped to this client")
    void getRequirements_dedupesToLatestVersion() {
        UUID clientId = UUID.randomUUID();
        realClient(clientId);
        ClientComplianceRequirement v1 = ClientComplianceRequirement.create(TENANT, clientId, "CSD_ACTIVE",
                "Valid CSD Registration (old)", null, null, true, USER);
        ClientComplianceRequirement v2 = v1.newVersion("Valid CSD Registration (updated)", null, null, true, USER);
        when(requirementRepository.findAllForClient(TENANT, clientId)).thenReturn(List.of(v1, v2));

        var results = service().getRequirements(TENANT, clientId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).requirementVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("createNewVersion rejects a stale view -- someone else already created a newer version")
    void createNewVersion_staleView_rejects() {
        UUID clientId = UUID.randomUUID();
        ClientComplianceRequirement v1 = ClientComplianceRequirement.create(TENANT, clientId, "CSD_ACTIVE",
                "Valid CSD Registration", null, null, true, USER);
        ClientComplianceRequirement v2 = v1.newVersion("Someone else's update", null, null, true, USER);

        when(requirementRepository.findByIdForTenant(TENANT, v1.getId())).thenReturn(Optional.of(v1));
        when(requirementRepository.findLatestByCode(TENANT, clientId, "CSD_ACTIVE")).thenReturn(Optional.of(v2));

        var req = new UpdateClientComplianceRequirementRequest("My update", null, null, true);

        assertThatThrownBy(() -> service().createNewVersion(TENANT, v1.getId(), req, USER))
                .isInstanceOf(HandyFlowException.class)
                .hasMessageContaining("latest");
    }
}
