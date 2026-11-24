package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceDocument;
import za.co.handyflow.platform.complianceservices.domain.model.ClientComplianceDocumentEvent;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceDocumentEventRepository;
import za.co.handyflow.platform.complianceservices.dto.ClientComplianceDocumentLifecycleDtos.DocumentEventView;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceDocumentRepository;
import za.co.handyflow.platform.complianceservices.domain.repository.ClientComplianceRegistrationRepository;
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
    private final ClientComplianceRegistrationRepository registrationRepository;
    private final EvidenceFacade evidenceFacade;
    private final ClientComplianceDocumentEventRepository events;

    @Transactional
    public ClientComplianceDocumentResponse upload(TenantId tenantId, UUID clientId, UUID registrationId,
                                                    String documentType, LocalDate issueDate, LocalDate expiryDate,
                                                    MultipartFile file, UUID uploadedBy, String uploadedByName) {
        return upload(tenantId, clientId, registrationId, documentType, issueDate, expiryDate, file, uploadedBy, uploadedByName, null);
    }

    /** With {@code replacesId}, the new upload takes over from an older document of the same type, which is kept on record. */
    @Transactional
    public ClientComplianceDocumentResponse upload(TenantId tenantId, UUID clientId, UUID registrationId,
                                                    String documentType, LocalDate issueDate, LocalDate expiryDate,
                                                    MultipartFile file, UUID uploadedBy, String uploadedByName, UUID replacesId) {
        requireClient(tenantId, clientId);
        requireRegistrationOf(tenantId, clientId, registrationId);
        ClientComplianceDocument older = null;
        if (replacesId != null) {
            older = find(tenantId, replacesId);
            if (!clientId.equals(older.getClientId()) || older.getDocumentType() == null || !older.getDocumentType().trim().equalsIgnoreCase(documentType == null ? "" : documentType.trim())) {
                throw new HandyFlowException("A replacement must be for the same client and the same kind of document", HttpStatus.BAD_REQUEST, "DOCUMENT_REPLACEMENT_MISMATCH");
            }
            if (!older.isCurrent() && !"REJECTED".equals(older.getStatus())) {
                throw new HandyFlowException("That document has already been replaced or archived", HttpStatus.CONFLICT, "DOCUMENT_NOT_REPLACEABLE");
            }
        }

        UUID documentId = UUID.randomUUID();
        var evidence = evidenceFacade.attach(tenantId, file, documentType, "complianceservices",
                "ClientComplianceDocument", documentId, null, uploadedBy, uploadedByName);

        ClientComplianceDocument document = ClientComplianceDocument.create(documentId, tenantId, clientId,
                registrationId, documentType, evidence.id(), issueDate, expiryDate, uploadedBy);
        documentRepository.save(document);
        events.save(ClientComplianceDocumentEvent.of(tenantId, documentId, "UPLOADED", null, "UPLOADED", older == null ? null : "Replaces an earlier document", uploadedBy));
        if (older != null) {
            String before = older.getStatus();
            older.supersede(documentId);
            documentRepository.save(older);
            events.save(ClientComplianceDocumentEvent.of(tenantId, older.getId(), "SUPERSEDED", before, "SUPERSEDED", "Replaced by a newer upload", uploadedBy));
        }

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
    public ClientComplianceDocumentResponse verify(TenantId tenantId, UUID id, UUID verifiedBy, String exceptionReason) {
        ClientComplianceDocument document = find(tenantId, id);
        String before = document.getStatus();
        move(() -> document.verify(verifiedBy, exceptionReason));
        documentRepository.save(document);
        events.save(ClientComplianceDocumentEvent.of(tenantId, id, "VERIFIED", before, document.getStatus(), document.getVerificationExceptionReason(), verifiedBy));
        return toResponse(document);
    }

    @Transactional
    public ClientComplianceDocumentResponse startReview(TenantId tenantId, UUID id, UUID by) {
        ClientComplianceDocument document = find(tenantId, id);
        String before = document.getStatus();
        move(document::startReview);
        documentRepository.save(document);
        events.save(ClientComplianceDocumentEvent.of(tenantId, id, "REVIEW_STARTED", before, document.getStatus(), null, by));
        return toResponse(document);
    }

    @Transactional
    public ClientComplianceDocumentResponse reject(TenantId tenantId, UUID id, UUID by, String reason) {
        ClientComplianceDocument document = find(tenantId, id);
        String before = document.getStatus();
        move(() -> document.reject(by, reason));
        documentRepository.save(document);
        events.save(ClientComplianceDocumentEvent.of(tenantId, id, "REJECTED", before, document.getStatus(), document.getRejectionReason(), by));
        return toResponse(document);
    }

    /** Replaces deleting. The file and its history stay, but it stops counting as evidence. */
    @Transactional
    public ClientComplianceDocumentResponse archive(TenantId tenantId, UUID id, UUID by, String reason) {
        ClientComplianceDocument document = find(tenantId, id);
        String before = document.getStatus();
        move(() -> document.archive(by, reason));
        documentRepository.save(document);
        events.save(ClientComplianceDocumentEvent.of(tenantId, id, "ARCHIVED", before, document.getStatus(), document.getArchiveReason(), by));
        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentEventView> events(TenantId tenantId, UUID id) {
        find(tenantId, id);
        return events.findForDocument(tenantId, id).stream()
                .map(e -> new DocumentEventView(e.getEvent(), e.getFromStatus(), e.getToStatus(), e.getReason(), e.getActor(), e.getOccurredAt())).toList();
    }

    /** Only a mistaken upload that nobody has looked at can be deleted. Anything else is archived, so the record stays. */
    @Transactional
    public void delete(TenantId tenantId, UUID id) {
        ClientComplianceDocument document = find(tenantId, id);
        if (!document.canDelete()) {
            throw new HandyFlowException("This document has been through review, so it is kept on record. Archive it instead", HttpStatus.CONFLICT, "DOCUMENT_KEPT_ON_RECORD");
        }
        evidenceFacade.detach(tenantId, document.getEvidenceId());
        documentRepository.delete(document);
        log.info("Client compliance document deleted id={} tenant={}", id, tenantId);
    }

    private static void move(Runnable step) {
        try {
            step.run();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "DOCUMENT_STATE_INVALID");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "DOCUMENT_INVALID");
        }
    }

    private void requireClient(TenantId tenantId, UUID clientId) {
        if (clientRepository.findByIdForTenant(tenantId, clientId).isEmpty())
            throw new ResourceNotFoundException("ComplianceClient", clientId.toString());
    }

    /**
     * A registration named on a document or deadline must be one of THIS client's, in this tenant. Without the check, a user could attach a record to another client's
     * registration by passing its id, and the client's own screens would then show (or hide) it wrongly.
     */
    private void requireRegistrationOf(TenantId tenantId, UUID clientId, UUID registrationId) {
        if (registrationId == null) return;
        boolean ok = registrationRepository.findByIdForTenant(tenantId, registrationId)
                .map(r -> clientId.equals(r.getClientId())).orElse(false);
        if (!ok) throw new ResourceNotFoundException("ClientComplianceRegistration", registrationId.toString());
    }

    private ClientComplianceDocument find(TenantId tenantId, UUID id) {
        return documentRepository.findByIdForTenant(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("ClientComplianceDocument", id.toString()));
    }

    private ClientComplianceDocumentResponse toResponse(ClientComplianceDocument d) {
        return new ClientComplianceDocumentResponse(d.getId(), d.getClientId(), d.getRegistrationId(),
                d.getDocumentType(), d.getEvidenceId(), d.getIssueDate(), d.getExpiryDate(),
                d.isVerified(), d.getVerifiedAt(), d.getCreatedAt(), d.getCreatedBy(), d.getVerifiedBy(), d.getVerificationExceptionReason() != null,
                d.getStatus(), d.getRejectionReason(), d.getArchiveReason(), d.getSupersededById(), d.canDelete());
    }
}
