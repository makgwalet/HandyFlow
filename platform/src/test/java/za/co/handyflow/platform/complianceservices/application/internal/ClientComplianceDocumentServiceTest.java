package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
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
    @Mock private EvidenceFacade evidenceFacade;

    private ClientComplianceDocumentService service() {
        return new ClientComplianceDocumentService(documentRepository, clientRepository, evidenceFacade);
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
}
