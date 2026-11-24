package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceRegistration;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceRegistrationRepository;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceClient;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceDocumentRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Regression test for ClientComplianceDocumentService. Confirms the
 * EvidenceFacade call is tagged with sourceModule "complianceservices"
 * (not "compliancetender" — a genuinely different module attaching the
 * evidence) and the document's own pre-generated id as relatedEntityId,
 * same pattern compliancetender's own ComplianceDocumentService
 * established.
 */
@ExtendWith(MockitoExtension.class)
class ClientComplianceDocumentServiceTest {

    @Mock private ClientComplianceDocumentRepository documentRepository;
    @Mock private ComplianceClientRepository clientRepository;
    @Mock private ClientComplianceRegistrationRepository registrationRepository;
    @Mock private EvidenceFacade evidenceFacade;
    @Mock private za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceDocumentEventRepository events;

    private ClientComplianceDocumentService service() {
        return new ClientComplianceDocumentService(documentRepository, clientRepository, registrationRepository, evidenceFacade, events);
    }

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    @Test
    @DisplayName("upload() rejects a clientId that doesn't belong to this tenant")
    void upload_unknownClient_rejects() {
        UUID clientId = UUID.randomUUID();
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "cert.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service().upload(TENANT, clientId, null, "Tax Clearance Certificate",
                null, null, file, USER, "Jane Doe"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("upload() rejects a registration that belongs to a different client, and attaches nothing")
    void upload_registrationOfAnotherClient_rejects() {
        UUID clientId = UUID.randomUUID();
        UUID otherClient = UUID.randomUUID();
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);
        ClientComplianceRegistration foreign = ClientComplianceRegistration.create(TENANT, otherClient, "CIPC", "Business Registration", "1", null, null, null, USER);
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(client));
        when(registrationRepository.findByIdForTenant(TENANT, foreign.getId())).thenReturn(Optional.of(foreign));

        MockMultipartFile file = new MockMultipartFile("file", "cert.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service().upload(TENANT, clientId, foreign.getId(), "Tax Clearance Certificate", null, null, file, USER, "Jane Doe"))
                .isInstanceOf(ResourceNotFoundException.class);
        org.mockito.Mockito.verifyNoInteractions(evidenceFacade);
    }

    @Test
    @DisplayName("upload() rejects a registration id that does not exist in this tenant")
    void upload_unknownRegistration_rejects() {
        UUID clientId = UUID.randomUUID();
        UUID regId = UUID.randomUUID();
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(client));
        when(registrationRepository.findByIdForTenant(TENANT, regId)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "cert.pdf", "application/pdf", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service().upload(TENANT, clientId, regId, "Tax Clearance Certificate", null, null, file, USER, "Jane Doe"))
                .isInstanceOf(ResourceNotFoundException.class);
        org.mockito.Mockito.verifyNoInteractions(evidenceFacade);
    }

    @Test
    @DisplayName("upload() tags EvidenceFacade with sourceModule=complianceservices, not compliancetender")
    void upload_taggedWithCorrectSourceModule() {
        UUID clientId = UUID.randomUUID();
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme Construction", null, null, null, null, USER);
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(client));
        when(evidenceFacade.attach(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EvidenceResponse(UUID.randomUUID(), "cert.pdf", "application/pdf", 3L,
                        "Tax Clearance Certificate", "ACTIVE", "Jane Doe", Instant.now()));

        MockMultipartFile file = new MockMultipartFile("file", "cert.pdf", "application/pdf", new byte[]{1, 2, 3});
        var response = service().upload(TENANT, clientId, null, "Tax Clearance Certificate",
                null, null, file, USER, "Jane Doe");

        ArgumentCaptor<String> sourceModuleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> relatedEntityTypeCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(evidenceFacade).attach(eq(TENANT), eq(file), eq("Tax Clearance Certificate"),
                sourceModuleCaptor.capture(), relatedEntityTypeCaptor.capture(), any(), isNull(), eq(USER), eq("Jane Doe"));

        assertThat(sourceModuleCaptor.getValue()).isEqualTo("complianceservices");
        assertThat(relatedEntityTypeCaptor.getValue()).isEqualTo("ClientComplianceDocument");
        assertThat(response.clientId()).isEqualTo(clientId);
    }

    private za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceDocument existing(UUID clientId, String type) {
        return za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceDocument.create(UUID.randomUUID(), TENANT, clientId, null, type, UUID.randomUUID(), null, null, USER);
    }

    @Test
    @DisplayName("a document that has been through review is not deleted, and its file is not detached")
    void delete_reviewedDocument_refused() {
        var d = existing(UUID.randomUUID(), "Tax Clearance Certificate");
        d.verify(UUID.randomUUID(), null);
        when(documentRepository.findByIdForTenant(TENANT, d.getId())).thenReturn(Optional.of(d));
        assertThatThrownBy(() -> service().delete(TENANT, d.getId())).isInstanceOf(za.co.handyflow.platform.shared.HandyFlowException.class).hasMessageContaining("Archive it instead");
        org.mockito.Mockito.verifyNoInteractions(evidenceFacade);
        org.mockito.Mockito.verify(documentRepository, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    @DisplayName("a mistaken upload nobody has looked at can still be deleted")
    void delete_untouchedUpload_allowed() {
        var d = existing(UUID.randomUUID(), "Tax Clearance Certificate");
        when(documentRepository.findByIdForTenant(TENANT, d.getId())).thenReturn(Optional.of(d));
        service().delete(TENANT, d.getId());
        org.mockito.Mockito.verify(documentRepository).delete(d);
    }

    @Test
    @DisplayName("archiving keeps the record, needs a reason and writes an event")
    void archive_recordsEvent() {
        var d = existing(UUID.randomUUID(), "Tax Clearance Certificate");
        when(documentRepository.findByIdForTenant(TENANT, d.getId())).thenReturn(Optional.of(d));
        assertThatThrownBy(() -> service().archive(TENANT, d.getId(), USER, "short")).isInstanceOf(za.co.handyflow.platform.shared.HandyFlowException.class);
        org.mockito.Mockito.verifyNoInteractions(events);
        var r = service().archive(TENANT, d.getId(), USER, "Client left the practice in October");
        assertThat(r.status()).isEqualTo("ARCHIVED");
        org.mockito.Mockito.verify(events).save(any());
    }

    @Test
    @DisplayName("a replacement for a different kind of document is refused before anything is attached")
    void replace_differentType_refused() {
        UUID clientId = UUID.randomUUID();
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme", null, null, null, null, USER);
        var old = existing(clientId, "BBBEE Certificate");
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(client));
        when(documentRepository.findByIdForTenant(TENANT, old.getId())).thenReturn(Optional.of(old));
        MockMultipartFile file = new MockMultipartFile("file", "c.pdf", "application/pdf", new byte[]{1});
        assertThatThrownBy(() -> service().upload(TENANT, clientId, null, "Tax Clearance Certificate", null, null, file, USER, "Jane", old.getId()))
                .isInstanceOf(za.co.handyflow.platform.shared.HandyFlowException.class).hasMessageContaining("same kind");
        org.mockito.Mockito.verifyNoInteractions(evidenceFacade);
    }

    @Test
    @DisplayName("a replacement supersedes the old document and records both events")
    void replace_supersedesOld() {
        UUID clientId = UUID.randomUUID();
        ComplianceClient client = ComplianceClient.create(TENANT, "Acme", null, null, null, null, USER);
        var old = existing(clientId, "Tax Clearance Certificate");
        old.verify(UUID.randomUUID(), null);
        when(clientRepository.findByIdForTenant(TENANT, clientId)).thenReturn(Optional.of(client));
        when(documentRepository.findByIdForTenant(TENANT, old.getId())).thenReturn(Optional.of(old));
        when(evidenceFacade.attach(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EvidenceResponse(UUID.randomUUID(), "c.pdf", "application/pdf", 1L, "Tax Clearance Certificate", "ACTIVE", "Jane", Instant.now()));
        MockMultipartFile file = new MockMultipartFile("file", "c.pdf", "application/pdf", new byte[]{1});
        var r = service().upload(TENANT, clientId, null, "tax clearance certificate", null, null, file, USER, "Jane", old.getId());
        assertThat(old.getStatus()).isEqualTo("SUPERSEDED");
        assertThat(old.getSupersededById()).isEqualTo(r.id());
        assertThat(r.status()).isEqualTo("UPLOADED");
        org.mockito.Mockito.verify(events, org.mockito.Mockito.times(2)).save(any());
    }
}
