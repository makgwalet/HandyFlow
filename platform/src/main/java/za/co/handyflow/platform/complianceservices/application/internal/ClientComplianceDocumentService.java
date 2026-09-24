package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceDocument;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceDocumentRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ComplianceClientRepository;
import za.co.handyflow.platform.complianceservices.dto.ClientComplianceDocumentResponse;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Client-scoped counterpart to compliancetender.ComplianceDocumentService
 * — same EvidenceFacade integration, same pre-generated-id-before-attach
 * pattern. sourceModule passed to EvidenceFacade.attach is
 * "complianceservices", not "compliancetender" — a genuinely different
 * module attaching the evidence, so it should be tagged as such for
 * anyone later listing evidence by source module.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientComplianceDocumentService {

    private final ClientComplianceDocumentRepository documentRepository;
    private final ComplianceClientRepository clientRepository;
    private final EvidenceFacade evidenceFacade;

    @Transactional
    public ClientComplianceDocumentResponse upload(TenantId tenantId, UUID clientId, UUID registrationId,
                                                    String documentType, LocalDate issueDate, LocalDate expiryDate,
                                                    MultipartFile file, UUID uploadedBy, String uploadedByName) {
        requireClient(tenantId, clientId);

        UUID documentId = UUID.randomUUID();
        var evidence = evidenceFacade.attach(tenantId, file, documentType, "complianceservices",
                "ClientComplianceDocument", documentId, null, uploadedBy, uploadedByName);

        ClientComplianceDocument document = ClientComplianceDocument.create(documentId, tenantId, clientId,
                registrationId, documentType, evidence.id(), issueDate, expiryDate, uploadedBy);
        documentRepository.save(document);

        log.info("Client compliance document uploaded id={} client={} type={} tenant={}",
                documentId, clientId, documentType, tenantId);
        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public List<ClientComplianceDocumentResponse> getDocuments(TenantId tenantId, UUID clientId) {
        requireClient(tenantId, clientId);
        return documentRepository.findByClient(tenantId, clientId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ClientComplianceDocumentResponse verify(TenantId tenantId, UUID id, UUID verifiedBy) {
        ClientComplianceDocument document = find(tenantId, id);
        document.verify(verifiedBy);
        documentRepository.save(document);
        return toResponse(document);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        ClientComplianceDocument document = find(tenantId, id);
        evidenceFacade.detach(tenantId, document.getEvidenceId());
        documentRepository.delete(document);
        log.info("Client compliance document deleted id={} tenant={}", id, tenantId);
    }

    private void requireClient(TenantId tenantId, UUID clientId) {
        if (clientRepository.findByIdForTenant(tenantId, clientId).isEmpty())
            throw new ResourceNotFoundException("ComplianceClient", clientId.toString());
    }

    private ClientComplianceDocument find(TenantId tenantId, UUID id) {
        return documentRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ClientComplianceDocument", id.toString()));
    }

    private ClientComplianceDocumentResponse toResponse(ClientComplianceDocument d) {
        return new ClientComplianceDocumentResponse(d.getId(), d.getClientId(), d.getRegistrationId(),
                d.getDocumentType(), d.getEvidenceId(), d.getIssueDate(), d.getExpiryDate(),
                d.isVerified(), d.getVerifiedAt(), d.getCreatedAt());
    }
}
