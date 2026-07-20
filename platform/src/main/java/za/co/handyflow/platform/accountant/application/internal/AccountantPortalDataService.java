package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.accountant.domain.model.AccClient;
import za.co.handyflow.platform.accountant.domain.model.AccFicaDocument;
import za.co.handyflow.platform.accountant.domain.model.AccPortalAccessGrant;
import za.co.handyflow.platform.accountant.domain.model.AccountantProfile;
import za.co.handyflow.platform.accountant.domain.model.FeeNote;
import za.co.handyflow.platform.accountant.domain.repository.*;
import za.co.handyflow.platform.accountant.dto.*;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Closes the "client portal" gap's actual data layer — read-only for
 * now (fee notes, FICA documents). Deliberately does NOT depend on
 * AccountantService: that service's methods assume TenantContext is
 * populated, but PortalJwtFilter never sets it — a portal user isn't
 * tied to one tenant the way staff is. Every method here resolves the
 * tenant from the ACCESS GRANT itself instead, which is the only
 * correct source of truth for "which tenant/client is this specific
 * request actually about" in the portal.
 * <p>
 * Also deliberately does not reuse AccountantService's private response
 * mappers — those are staff-facing internals. Keeping this service's
 * own small, independent copies avoids either weakening that
 * encapsulation or creating an implicit dependency where a future
 * change to staff-facing mapping logic could accidentally affect what
 * a portal user sees, without anyone intending that.
 * <p>
 * Upload and payment are deliberately NOT built here — upload raises a
 * real open question (acc_fica_documents.uploaded_by has no
 * discriminator distinguishing a staff UUID from a portal_user UUID;
 * a staff member reviewing "who uploaded this" couldn't tell which
 * table to check), and payment needs a real gateway integration, a
 * separate, large decision. This is scoped to what's genuinely safe to
 * ship now.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountantPortalDataService {

    private final AccPortalAccessGrantRepository grantRepo;
    private final AccClientRepository clientRepo;
    private final FeeNoteRepository feeNoteRepo;
    private final AccPaymentReceivedRepository paymentRepo;
    private final AccFicaDocumentRepository ficaDocRepo;
    // NEW: backs uploadMyFicaDocument() — resolves the portal user's
    // real name for the uploaded_by_name field.
    private final za.co.handyflow.platform.shared.PortalUserRepository portalUserRepo;
    // NEW: closes the "portal fee note PDF download" gap.
    private final AccountantProfileRepository profileRepo;
    private final AccFeeNotePdfGenerator feeNotePdfGenerator;

    /**
     * The one check every method below starts with — matches
     * AccPortalAccessGrant's own class Javadoc on why this is a single,
     * shared check rather than reimplemented per-method.
     */
    private AccPortalAccessGrant requireAccess(UUID portalUserId, UUID clientId) {
        return grantRepo.findActiveGrant(portalUserId, clientId)
                .orElseThrow(() -> new HandyFlowException(
                        "You don't have access to this client", HttpStatus.FORBIDDEN, "NO_PORTAL_ACCESS"));
    }

    @Transactional(readOnly = true)
    public List<PortalClientSummaryResponse> getMyClients(UUID portalUserId) {
        return grantRepo.findActiveGrantsForUser(portalUserId).stream()
                .map(g -> clientRepo.findById(g.getClientId())
                        .map(c -> new PortalClientSummaryResponse(c.getId(), c.getTradingName()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeeNoteResponse> getMyFeeNotes(UUID portalUserId, UUID clientId) {
        requireAccess(portalUserId, clientId);
        String clientName = clientRepo.findById(clientId).map(AccClient::getTradingName).orElse("Unknown");
        return feeNoteRepo.findByClient(clientId, Pageable.unpaged()).stream()
                .map(f -> toFeeNoteResponse(f, clientName))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FicaDocumentResponse> getMyFicaDocuments(UUID portalUserId, UUID clientId) {
        AccPortalAccessGrant grant = requireAccess(portalUserId, clientId);
        return ficaDocRepo.findSummariesByClient(grant.getTenantId(), clientId).stream()
                .map(p -> new FicaDocumentResponse(p.getId(), p.getDocType(), p.getFileName(),
                        p.getContentType(), p.getFileSizeBytes(), p.isVerified(), p.getVerifiedAt(),
                        p.getExpiryDate(), p.getUploadedByName(), p.getUploadedByType(), p.getCreatedAt()))
                .toList();
    }

    // NEW: closes the "document upload" gap flagged when this service
    // was first built — resolved by adding a real discriminator column
    // (AccFicaDocument.uploadedByType) rather than reusing the staff
    // upload path as-is, which would have made "who uploaded this"
    // ambiguous. Same size cap and content-type allowlist as the staff
    // upload path (AccountantService.ALLOWED_FICA_DOC_TYPES /
    // MAX_FICA_DOC_BYTES) — duplicated here rather than shared, for the
    // same reason the response mappers are duplicated: this service is
    // deliberately independent of AccountantService.
    private static final long MAX_PORTAL_UPLOAD_BYTES = 10L * 1024 * 1024;
    private static final java.util.Set<String> ALLOWED_PORTAL_DOC_TYPES = java.util.Set.of(
            "application/pdf", "image/jpeg", "image/jpg", "image/png",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    @Transactional
    public FicaDocumentResponse uploadMyFicaDocument(UUID portalUserId, UUID clientId,
                                                     UploadFicaDocumentRequest req) {
        AccPortalAccessGrant grant = requireAccess(portalUserId, clientId);

        String contentType = req.contentType() != null ? req.contentType() : "application/octet-stream";
        if (!ALLOWED_PORTAL_DOC_TYPES.contains(contentType)) {
            throw new HandyFlowException(
                    "Unsupported file type — please upload a PDF, JPG, PNG, or Word document",
                    HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE");
        }

        long approxDecodedBytes = (req.fileContentBase64().length() * 3L) / 4;
        if (approxDecodedBytes > MAX_PORTAL_UPLOAD_BYTES) {
            throw new HandyFlowException(
                    "File is too large — maximum attachment size is "
                            + (MAX_PORTAL_UPLOAD_BYTES / (1024 * 1024)) + "MB",
                    HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE");
        }

        String uploaderName = portalUserRepo.findById(portalUserId)
                .map(za.co.handyflow.platform.shared.PortalUser::getFullName)
                .orElse("Portal user");

        AccFicaDocument doc = AccFicaDocument.create(grant.getTenantId(), clientId, req.docType(),
                req.fileName(), contentType, req.fileSizeBytes(), req.fileContentBase64(),
                req.expiryDate(), portalUserId, uploaderName, "PORTAL_USER");
        ficaDocRepo.save(doc);
        log.info("FICA document '{}' ({}) uploaded via portal for client={}", req.fileName(), req.docType(), clientId);

        return new FicaDocumentResponse(doc.getId(), doc.getDocType(), doc.getFileName(), doc.getContentType(),
                doc.getFileSizeBytes(), doc.isVerified(), doc.getVerifiedAt(), doc.getExpiryDate(),
                doc.getUploadedByName(), doc.getUploadedByType(), doc.getCreatedAt());
    }

    /**
     * NEW: closes the "portal fee note PDF download" gap — flagged
     * earlier as a real gap: the staff PDF endpoint
     * (AccountantController.downloadFeeNotePdf) is @PreAuthorize'd on
     * staff-only authorities, and its underlying service method relies
     * on TenantContext, which PortalJwtFilter never populates. This is
     * a genuinely separate implementation, not a reused one — resolves
     * the tenant from the grant, and the client/profile via raw-UUID
     * lookups (AccClientRepository's inherited findById(), and the new
     * AccountantProfileRepository.findByTenantIdRaw() added specifically
     * to avoid needing to construct a TenantId object).
     */
    @Transactional(readOnly = true)
    public byte[] downloadMyFeeNotePdf(UUID portalUserId, UUID clientId, UUID feeNoteId) {
        AccPortalAccessGrant grant = requireAccess(portalUserId, clientId);

        FeeNote feeNote = feeNoteRepo.findByTenantIdAndId(grant.getTenantId(), feeNoteId)
                .orElseThrow(() -> new HandyFlowException("Fee note not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        if (!feeNote.getClientId().equals(clientId)) {
            throw new HandyFlowException("Fee note not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        AccClient client = clientRepo.findById(clientId)
                .orElseThrow(() -> new HandyFlowException("Client not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        AccountantProfile profile = profileRepo.findByTenantIdRaw(grant.getTenantId())
                .orElseThrow(() -> new HandyFlowException(
                        "This firm hasn't completed its practice profile yet", HttpStatus.NOT_FOUND, "NO_PROFILE"));

        return feeNotePdfGenerator.generate(feeNote, client, profile);
    }

    public record FicaDocFile(byte[] content, String contentType, String fileName) {}

    @Transactional(readOnly = true)
    public FicaDocFile downloadMyFicaDocument(UUID portalUserId, UUID clientId, UUID docId) {
        AccPortalAccessGrant grant = requireAccess(portalUserId, clientId);
        AccFicaDocument doc = ficaDocRepo.findByTenantIdAndId(grant.getTenantId(), docId)
                .orElseThrow(() -> new HandyFlowException("Document not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
        // Belt-and-braces: even though findByTenantIdAndId already
        // scoped by the grant's own tenant, this confirms the document
        // actually belongs to THIS specific client too, not just any
        // client under the same tenant.
        if (!doc.getClientId().equals(clientId)) {
            throw new HandyFlowException("Document not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        }
        byte[] content = Base64.getDecoder().decode(doc.getFileContentBase64());
        String contentType = doc.getContentType() != null && !doc.getContentType().isBlank()
                ? doc.getContentType() : "application/octet-stream";
        return new FicaDocFile(content, contentType, doc.getFileName());
    }

    private FeeNoteResponse toFeeNoteResponse(FeeNote f, String clientName) {
        BigDecimal paid = paymentRepo.sumByFeeNoteId(f.getId());
        BigDecimal balance = f.getTotal().subtract(paid);
        int daysOverdue = f.getDueDate().isBefore(LocalDate.now()) && !"PAID".equals(f.getStatus())
                ? (int) ChronoUnit.DAYS.between(f.getDueDate(), LocalDate.now()) : 0;
        List<FeeNoteLineResponse> lines = f.getLines().stream()
                .map(l -> new FeeNoteLineResponse(l.getId(), l.getDescription(),
                        l.getQuantity(), l.getUnitPrice(), l.getVatRate(), l.getAmount()))
                .toList();
        return new FeeNoteResponse(f.getId(), f.getClientId(), clientName, f.getInvoiceNumber(),
                f.getInvoiceDate(), f.getDueDate(), f.getSubtotal(), f.getVatAmount(),
                f.getTotal(), paid, balance, f.getStatus(), daysOverdue, lines, f.getCreatedAt());
    }
}