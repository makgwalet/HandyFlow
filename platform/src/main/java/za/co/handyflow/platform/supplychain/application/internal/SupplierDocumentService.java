package za.co.handyflow.platform.supplychain.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.supplychain.domain.enums.SupplierDocType;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierDocument;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierDocumentRepository;
import za.co.handyflow.platform.supplychain.domain.repository.ScSupplierRepository;
import za.co.handyflow.platform.supplychain.dto.SupplierDocumentResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Compliance documents. Files go through the shared EvidenceFacade (hashing, size cap, storage
 * port) — nothing is stored in this module's tables. A new document supersedes the previous
 * ACTIVE one of the same type; removal is soft and detaches (never deletes) the evidence.
 * Verification marks that someone checked the document is genuine (e.g. confirmed on SARS eFiling);
 * with app.scm.sod.enforce=true the uploader cannot verify their own upload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierDocumentService {

    private static final String EVIDENCE_TYPE = "SUPPLIER_DOCUMENT";
    private static final String SOURCE_MODULE = "supplychain";
    private static final String ENTITY_TYPE = "ScSupplierDocument";

    private final ScSupplierDocumentRepository docRepo;
    private final ScSupplierRepository supplierRepo;
    private final EvidenceFacade evidenceFacade;
    private final SupplierAuditService audit;

    @Value("${app.scm.sod.enforce:false}")
    private boolean enforceSegregationOfDuties;

    @Transactional
    public SupplierDocumentResponse add(TenantId tenantId, UUID userId, String userName, UUID supplierId,
                                        String docType, String referenceNumber, LocalDate issued, LocalDate expiry,
                                        MultipartFile file) {
        UUID tid = tenantId.getValue();
        requireSupplier(tid, supplierId);

        SupplierDocType type;
        try { type = SupplierDocType.valueOf(docType == null ? "" : docType.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            throw new HandyFlowException("Unknown document type '" + docType + "'", HttpStatus.BAD_REQUEST, "INVALID_DOC_TYPE");
        }
        if (type.isExpiryRequired() && expiry == null)
            throw new HandyFlowException(type + " needs an expiry date", HttpStatus.BAD_REQUEST, "EXPIRY_REQUIRED");
        if (issued != null && expiry != null && expiry.isBefore(issued))
            throw new HandyFlowException("Expiry date cannot be before the issue date", HttpStatus.BAD_REQUEST, "INVALID_DATES");

        ScSupplierDocument doc = ScSupplierDocument.create(tid, supplierId, type,
                referenceNumber != null && !referenceNumber.isBlank() ? referenceNumber.trim() : null,
                issued, expiry, userId, userName);

        if (file != null && !file.isEmpty()) {
            EvidenceResponse ev = evidenceFacade.attach(tenantId, file, EVIDENCE_TYPE, SOURCE_MODULE, ENTITY_TYPE,
                    doc.getId(), null, userId, userName);
            doc.attachFile(ev.id(), ev.fileName(), ev.contentType());
        }

        docRepo.findActiveOfType(tid, supplierId, type).forEach(ScSupplierDocument::supersede);
        docRepo.save(doc);
        audit.record(tid, supplierId, "DOCUMENT_ADDED", null, null,
                type + (doc.getReferenceNumber() != null ? " " + doc.getReferenceNumber() : "")
                        + (expiry != null ? ", expires " + expiry : "") + (doc.getEvidenceId() != null ? ", file attached" : ", no file"),
                userId, userName);
        return toResponse(doc);
    }

    @Transactional
    public SupplierDocumentResponse verify(TenantId tenantId, UUID supplierId, UUID docId, UUID userId, String userName) {
        ScSupplierDocument d = requireDoc(tenantId, supplierId, docId);
        if (!d.isActive())
            throw new HandyFlowException("Only an ACTIVE document can be verified", HttpStatus.CONFLICT, "DOCUMENT_NOT_ACTIVE");
        if (enforceSegregationOfDuties && d.getUploadedBy() != null && d.getUploadedBy().equals(userId))
            throw new HandyFlowException("Segregation of duties: you cannot verify a document you uploaded",
                    HttpStatus.FORBIDDEN, "SEGREGATION_OF_DUTIES");
        d.markVerified(userId, userName);
        docRepo.save(d);
        audit.record(tenantId.getValue(), supplierId, "DOCUMENT_VERIFIED", null, null, d.getDocType().name(), userId, userName);
        return toResponse(d);
    }

    @Transactional
    public void remove(TenantId tenantId, UUID supplierId, UUID docId, String reason, UUID userId, String userName) {
        if (reason == null || reason.isBlank())
            throw new HandyFlowException("A reason is required to remove a document", HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        ScSupplierDocument d = requireDoc(tenantId, supplierId, docId);
        try { d.remove(reason.trim()); }
        catch (IllegalStateException e) { throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "DOCUMENT_NOT_ACTIVE"); }
        docRepo.save(d);
        if (d.getEvidenceId() != null) evidenceFacade.detach(tenantId, d.getEvidenceId());   // soft — file preserved
        audit.record(tenantId.getValue(), supplierId, "DOCUMENT_REMOVED", null, null,
                d.getDocType() + ": " + reason.trim(), userId, userName);
    }

    @Transactional(readOnly = true)
    public List<SupplierDocumentResponse> list(TenantId tenantId, UUID supplierId, boolean includeHistory) {
        requireSupplier(tenantId.getValue(), supplierId);
        List<ScSupplierDocument> docs = includeHistory
                ? docRepo.findAllForSupplier(tenantId.getValue(), supplierId)
                : docRepo.findActiveForSupplier(tenantId.getValue(), supplierId);
        return docs.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EvidenceFacade.DownloadedEvidence download(TenantId tenantId, UUID supplierId, UUID docId) {
        ScSupplierDocument d = requireDoc(tenantId, supplierId, docId);
        if (d.getEvidenceId() == null)
            throw new HandyFlowException("This document has no file attached", HttpStatus.NOT_FOUND, "NO_FILE");
        return evidenceFacade.download(tenantId, d.getEvidenceId());
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private void requireSupplier(UUID tid, UUID supplierId) {
        supplierRepo.findByTenantIdAndId(tid, supplierId)
                .orElseThrow(() -> new HandyFlowException("Supplier not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private ScSupplierDocument requireDoc(TenantId tenantId, UUID supplierId, UUID docId) {
        requireSupplier(tenantId.getValue(), supplierId);
        ScSupplierDocument d = docRepo.findByTenantAndId(tenantId.getValue(), docId)
                .orElseThrow(() -> new HandyFlowException("Document not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!d.getSupplierId().equals(supplierId))
            throw new HandyFlowException("Document does not belong to this supplier", HttpStatus.BAD_REQUEST, "MISMATCHED_SUPPLIER");
        return d;
    }

    private SupplierDocumentResponse toResponse(ScSupplierDocument d) {
        return new SupplierDocumentResponse(d.getId(), d.getDocType().name(), d.getReferenceNumber(), d.getIssuedDate(),
                d.getExpiryDate(), d.daysToExpiry(LocalDate.now()), d.getStatus(), d.isVerified(), d.getVerifiedByName(),
                d.getVerifiedAt(), d.getEvidenceId() != null, d.getFileName(), d.getUploadedByName(), d.getCreatedAt());
    }
}
