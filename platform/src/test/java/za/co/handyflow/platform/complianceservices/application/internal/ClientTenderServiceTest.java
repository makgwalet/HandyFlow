package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTender;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClient;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientTenderRequirementRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.dto.CreateClientTenderRequirementRequest;
import za.co.handyflow.platform.complianceservices.dto.CreateClientTenderRequest;
import za.co.handyflow.platform.complianceservices.dto.TransitionClientTenderRequest;
import za.co.handyflow.platform.identity.TenantNumberingFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceRequirement;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceRequirementRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Regression test for ClientTenderService — the numbering integration
 * (a CLIENT_TENDER/"CTND" documentType/code, distinct from
 * compliancetender's own "TND") and the client-scoping check that's new
 * relative to the tenant-scoped TenderService.
 */
@ExtendWith(MockitoExtension.class)
class ClientTenderServiceTest {

    @Mock private ClientTenderRepository tenderRepository;
    @Mock private ClientTenderRequirementRepository requirementRepository;
    @Mock private ComplianceClientRepository clientRepository;
    @Mock private TenantNumberingFacade numberingFacade;
    @Mock private ClientTenderSnapshotService snapshotService;
    @Mock private ClientComplianceRequirementRepository catalogueRepository;

    private ClientTenderService service() {
        return new ClientTenderService(tenderRepository, requirementRepository, clientRepository, numberingFacade, snapshotService, catalogueRepository);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    @Test
    @DisplayName("create() rejects a clientId that doesn't belong to this tenant")
    void create_unknownClient_rejects() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.empty());

        var req = new CreateClientTenderRequest("Municipal Road Upgrade", "City of Cape Town", null,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service().create(TENANT, clientId, req, USER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("create() gets its tender number from TenantNumberingFacade with CLIENT_TENDER/CTND, not TND")
    void create_usesCorrectNumberingCode() {
        UUID clientId = UUID.randomUUID();
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(client));
        when(numberingFacade.next(TENANT, "CLIENT_TENDER", "CTND")).thenReturn("ZETA-CTND-00001");

        var req = new CreateClientTenderRequest("Municipal Road Upgrade", "City of Cape Town", null,
                null, null, null, null, null, null);
        var response = service().create(TENANT, clientId, req, USER);

        assertThat(response.tenderNumber()).isEqualTo("ZETA-CTND-00001");
        assertThat(response.clientId()).isEqualTo(clientId);
        assertThat(response.status()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("transition() delegates to the entity's own transitionTo, surfacing its validation unchanged")
    void transition_delegatesToEntity() {
        UUID tenderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        ClientTender tender = ClientTender.create(TENANT, clientId, "CTND-00001", "Test Tender", null, null,
                null, null, null, null, null, null, USER);
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));

        var req = new TransitionClientTenderRequest("SUBMITTED");

        assertThatThrownBy(() -> service().transition(TENANT, tenderId, req, USER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a successful transition to SUBMITTED automatically captures a snapshot")
    void transition_toSubmitted_capturesSnapshotAutomatically() {
        UUID tenderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        ClientTender tender = ClientTender.create(TENANT, clientId, "CTND-00001", "Test Tender", null, null,
                null, null, null, null, null, null, USER);
        tender.transitionTo("IN_PREPARATION", USER);
        tender.transitionTo("INTERNAL_REVIEW", USER);
        tender.transitionTo("READY_TO_SUBMIT", USER);
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));

        service().transition(TENANT, tenderId, new TransitionClientTenderRequest("SUBMITTED"), USER);

        org.mockito.Mockito.verify(snapshotService).captureSnapshot(TENANT, tenderId, USER);
    }

    @Test
    @DisplayName("a transition that is NOT to SUBMITTED never triggers a snapshot")
    void transition_notToSubmitted_doesNotCaptureSnapshot() {
        UUID tenderId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        ClientTender tender = ClientTender.create(TENANT, clientId, "CTND-00001", "Test Tender", null, null,
                null, null, null, null, null, null, USER);
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));

        service().transition(TENANT, tenderId, new TransitionClientTenderRequest("IN_PREPARATION"), USER);

        org.mockito.Mockito.verifyNoInteractions(snapshotService);
    }

    // ---- the requirement reference must be one of THIS client's own tracked requirements -------------------------------------

    private UUID existingTender(UUID clientId) {
        UUID tenderId = UUID.randomUUID();
        ClientTender tender = ClientTender.create(TENANT, clientId, "CTND-00001", "Test Tender", null, null, null, null, null, null, null, null, USER);
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));
        return tenderId;
    }

    @Test
    @DisplayName("addRequirement() attaches a requirement that is one of THIS client's own tracked requirements")
    void addRequirement_thisClientsRequirement_isAttached() {
        UUID clientId = UUID.randomUUID(), tenderId = existingTender(clientId), trackedId = UUID.randomUUID();
        when(catalogueRepository.findByIdForTenant(TENANT, trackedId)).thenReturn(Optional.of(ClientComplianceRequirement.create(TENANT, clientId, "CSD_ACTIVE", "CSD", null, null, true, USER)));

        var response = service().addRequirement(TENANT, tenderId, new CreateClientTenderRequirementRequest(trackedId, "Valid CSD registration", "COMPLIANCE"), USER);

        assertThat(response.clientRequirementId()).isEqualTo(trackedId);
    }

    @Test
    @DisplayName("addRequirement() refuses a tracked requirement that belongs to a DIFFERENT client of the same tenant, and saves nothing")
    void addRequirement_anotherClientsRequirement_isRefused() {
        UUID clientId = UUID.randomUUID(), otherClientId = UUID.randomUUID(), tenderId = existingTender(clientId), trackedId = UUID.randomUUID();
        when(catalogueRepository.findByIdForTenant(TENANT, trackedId)).thenReturn(Optional.of(ClientComplianceRequirement.create(TENANT, otherClientId, "CSD_ACTIVE", "CSD", null, null, true, USER)));

        assertThatThrownBy(() -> service().addRequirement(TENANT, tenderId, new CreateClientTenderRequirementRequest(trackedId, "Valid CSD registration", "COMPLIANCE"), USER))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not one of this client's tracked requirements");
        verify(requirementRepository, never()).save(any());
    }

    @Test
    @DisplayName("addRequirement() refuses an unknown (or another tenant's) tracked-requirement id")
    void addRequirement_unknownRequirement_isRefused() {
        UUID clientId = UUID.randomUUID(), tenderId = existingTender(clientId), foreignId = UUID.randomUUID();
        when(catalogueRepository.findByIdForTenant(TENANT, foreignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().addRequirement(TENANT, tenderId, new CreateClientTenderRequirementRequest(foreignId, "x", "COMPLIANCE"), USER))
                .isInstanceOf(IllegalArgumentException.class);
        verify(requirementRepository, never()).save(any());
    }

    @Test
    @DisplayName("addRequirement() without a link does not look anything up in the catalogue")
    void addRequirement_withoutLink_doesNotTouchTheCatalogue() {
        UUID clientId = UUID.randomUUID(), tenderId = existingTender(clientId);

        var response = service().addRequirement(TENANT, tenderId, new CreateClientTenderRequirementRequest(null, "Typed by hand", "MANUAL"), USER);

        assertThat(response.clientRequirementId()).isNull();
        verifyNoInteractions(catalogueRepository);
    }
}
