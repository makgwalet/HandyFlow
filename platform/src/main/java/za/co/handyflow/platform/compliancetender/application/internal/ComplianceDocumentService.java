package za.co.handyflow.platform.compliancetender.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceDocument;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceDocumentRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceRegistrationRepository;
import za.co.handyflow.platform.compliancetender.dto.ComplianceDocumentResponse;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Deliberately thin around EvidenceFacade — same shape already proven by
 * Expenses' own attachReceipt()/getReceipts(): upload the actual file
 * bytes through EvidenceFacade (which owns storage), then record just the
 * compliance-specific metadata (which registration, document type,
 * issue/expiry dates, verification) here.
 * <p>
 * The document's own id is generated up front, before evidence is
 * attached, so EvidenceFacade.attach(...) can be tagged with
 * relatedEntityType "ComplianceDocument" / relatedEntityId = this
 * document's real id — meaning EvidenceFacade.listFor(tenantId,
 * "compliancetender", "ComplianceDocument", documentId) can find it
 * later, the same lookup shape every other EvidenceFacade consumer uses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceDocumentService {

    private final ComplianceDocumentRepository documentRepository;
    private final ComplianceRegistrationRepository registrationRepository;
    private final EvidenceFacade evidenceFacade;

    @Transactional
    public ComplianceDocumentResponse upload(TenantId tenantId, UUID registrationId, String documentType,
                                             LocalDate issueDate, LocalDate expiryDate, MultipartFile file,
                                             UUID uploadedBy, String uploadedByName) {
        if (registrationId != null) {
            registrationRepository.findByIdForTenant(tenantId, registrationId)
                    .orElseThrow(() -> new ResourceNotFoundException("ComplianceRegistration", registrationId.toString()));
        }

        UUID documentId = UUID.randomUUID();
        EvidenceResponse evidence = evidenceFacade.attach(tenantId, file, documentType, "compliancetender",
                "ComplianceDocument", documentId, null, uploadedBy, uploadedByName);

        ComplianceDocument document = ComplianceDocument.create(documentId, tenantId, registrationId, documentType,
                evidence.id(), issueDate, expiryDate, uploadedBy);
        documentRepository.save(document);

        log.info("Compliance document uploaded id={} type={} registration={} tenant={}",
                documentId, documentType, registrationId, tenantId);
        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public List<ComplianceDocumentResponse> getDocumentsForRegistration(TenantId tenantId, UUID registrationId) {
        return documentRepository.findByRegistration(tenantId, registrationId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ComplianceDocumentResponse> getAllDocuments(TenantId tenantId) {
        return documentRepository.findAllForTenant(tenantId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public ComplianceDocumentResponse verify(TenantId tenantId, UUID id, UUID verifiedBy) {
        ComplianceDocument document = find(tenantId, id);
        document.verify(verifiedBy);
        documentRepository.save(document);
        log.info("Compliance document verified id={} tenant={}", id, tenantId);
        return toResponse(document);
    }

    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        ComplianceDocument document = find(tenantId, id);
        evidenceFacade.detach(tenantId, document.getEvidenceId());
        documentRepository.delete(document);
        log.info("Compliance document deleted id={} tenant={}", id, tenantId);
    }

    private ComplianceDocument find(TenantId tenantId, UUID id) {
        return documentRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ComplianceDocument", id.toString()));
    }

    private ComplianceDocumentResponse toResponse(ComplianceDocument d) {
        return new ComplianceDocumentResponse(d.getId(), d.getRegistrationId(), d.getDocumentType(),
                d.getEvidenceId(), d.getIssueDate(), d.getExpiryDate(), d.isVerified(), d.getVerifiedAt(), d.getCreatedAt());
    }
}
