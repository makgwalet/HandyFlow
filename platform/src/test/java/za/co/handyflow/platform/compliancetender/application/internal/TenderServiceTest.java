package za.co.handyflow.platform.compliancetender.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.compliancetender.domain.model.Tender;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.TenderRequirementRepository;
import za.co.handyflow.platform.compliancetender.dto.CreateTenderRequest;
import za.co.handyflow.platform.compliancetender.dto.CreateTenderRequirementRequest;
import za.co.handyflow.platform.compliancetender.dto.TransitionTenderRequest;
import za.co.handyflow.platform.identity.TenantNumberingFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenderServiceTest {

    @Mock private TenderRepository tenderRepository;
    @Mock private TenderRequirementRepository requirementRepository;
    @Mock private TenantNumberingFacade numberingFacade;
    @Mock private TenderSnapshotService snapshotService;

    private TenderService service() {
        return new TenderService(tenderRepository, requirementRepository, numberingFacade, snapshotService);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    @Test
    @DisplayName("create() gets its tender number from TenantNumberingFacade, not hand-rolled")
    void create_usesNumberingFacade() {
        when(numberingFacade.next(TENANT, "TENDER", "TND")).thenReturn("ZETA-TND-00001");

        var req = new CreateTenderRequest("Municipal Road Upgrade", "City of Cape Town", "CCT-2026-045",
                null, null, null, null, "Construction", "cidb Grade 6GB");

        var response = service().create(TENANT, req, USER);

        assertThat(response.tenderNumber()).isEqualTo("ZETA-TND-00001");
        assertThat(response.status()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("addRequirement() throws if the tender doesn't belong to this tenant")
    void addRequirement_unknownTender_throws() {
        UUID tenderId = UUID.randomUUID();
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.empty());

        var req = new CreateTenderRequirementRequest(null, "Valid CSD registration", "COMPLIANCE");

        assertThatThrownBy(() -> service().addRequirement(TENANT, tenderId, req, USER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("transition() delegates to the entity's own transitionTo, surfacing its validation")
    void transition_delegatesToEntity() {
        UUID tenderId = UUID.randomUUID();
        Tender tender = Tender.create(TENANT, "TND-00001", "Test Tender", null, null,
                null, null, null, null, null, null, USER);
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));

        var req = new TransitionTenderRequest("SUBMITTED");

        // DRAFT -> SUBMITTED is not a valid jump; the entity's own rule should surface here unchanged
        assertThatThrownBy(() -> service().transition(TENANT, tenderId, req, USER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a successful transition to SUBMITTED automatically captures a snapshot")
    void transition_toSubmitted_capturesSnapshotAutomatically() {
        UUID tenderId = UUID.randomUUID();
        Tender tender = Tender.create(TENANT, "TND-00001", "Test Tender", null, null,
                null, null, null, null, null, null, USER);
        tender.transitionTo("IN_PREPARATION", USER);
        tender.transitionTo("INTERNAL_REVIEW", USER);
        tender.transitionTo("READY_TO_SUBMIT", USER);
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));

        service().transition(TENANT, tenderId, new TransitionTenderRequest("SUBMITTED"), USER);

        org.mockito.Mockito.verify(snapshotService).captureSnapshot(TENANT, tenderId, USER);
    }

    @Test
    @DisplayName("a transition that is NOT to SUBMITTED never triggers a snapshot")
    void transition_notToSubmitted_doesNotCaptureSnapshot() {
        UUID tenderId = UUID.randomUUID();
        Tender tender = Tender.create(TENANT, "TND-00001", "Test Tender", null, null,
                null, null, null, null, null, null, USER);
        when(tenderRepository.findByIdForTenant(TENANT, tenderId)).thenReturn(Optional.of(tender));

        service().transition(TENANT, tenderId, new TransitionTenderRequest("IN_PREPARATION"), USER);

        org.mockito.Mockito.verifyNoInteractions(snapshotService);
    }
}
