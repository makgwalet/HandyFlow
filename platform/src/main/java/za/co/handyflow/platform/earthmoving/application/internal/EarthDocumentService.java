package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.documents.application.DocumentsFacade;
import za.co.handyflow.platform.documents.application.DocumentsFacade.DocumentInfo;
import za.co.handyflow.platform.documents.application.DocumentsFacade.FileUpload;
import za.co.handyflow.platform.documents.application.DocumentsFacade.VersionInfo;
import za.co.handyflow.platform.earthmoving.domain.model.DocumentPolicy;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAssetRepository;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthDisposalRequestRepository;
import za.co.handyflow.platform.notifications.application.UserRecipientResolver;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Earthmoving's documents: photos, manuals, insurance, warranties and operator certificates, kept in the Document Service. This service decides who may see
 * and change them: reading needs READ and writing needs WRITE, except insurance, warranties and operator certificates, which need administrator authority both
 * ways. Archiving always needs administrator authority. A renewed expiry date settles the expiry alerts raised for the old date.
 */
@Service
@RequiredArgsConstructor
public class EarthDocumentService {

    private final DocumentsFacade documents;
    private final EarthAssetRepository assets;
    private final EarthDisposalRequestRepository disposals;
    private final UserRecipientResolver users;
    private final EarthAlertService alerts;
    private final EarthmovingAccess access;

    @Transactional(readOnly = true)
    public List<DocumentInfo> list(TenantId tenantId, String kind, UUID ownerId, boolean includeArchived) {
        access.require(za.co.handyflow.platform.earthmoving.domain.model.EarthmovingRules.READ, "view documents");
        String owner = DocumentPolicy.owner(kind);
        checkOwner(tenantId, owner, ownerId);
        return documents.list(tenantId, owner, ownerId, includeArchived).stream().filter(this::canSee).toList();
    }

    @Transactional
    public DocumentInfo upload(TenantId tenantId, String kind, UUID ownerId, String category, String title, String description, LocalDate expiresOn,
                               FileUpload file, UUID actor) {
        String owner = DocumentPolicy.owner(kind);
        String cat = DocumentPolicy.category(owner, category);
        access.require(DocumentPolicy.authority(cat, true), "add this document");
        checkOwner(tenantId, owner, ownerId);
        return documents.upload(tenantId, owner, ownerId, cat, title, description, DocumentPolicy.requireExpiry(cat, expiresOn), file, actor);
    }

    @Transactional
    public DocumentInfo addVersion(TenantId tenantId, UUID documentId, FileUpload file, String note, LocalDate renewedExpiry, UUID actor) {
        DocumentInfo before = mine(tenantId, documentId, true);
        DocumentInfo after = documents.addVersion(tenantId, documentId, file, note, renewedExpiry, actor);
        settleAlerts(tenantId, before, after.expiresOn(), actor);
        return after;
    }

    @Transactional
    public DocumentInfo updateDetails(TenantId tenantId, UUID documentId, String title, String description, LocalDate expiresOn, UUID actor) {
        DocumentInfo before = mine(tenantId, documentId, true);
        DocumentInfo after = documents.updateDetails(tenantId, documentId, title, description, DocumentPolicy.requireExpiry(before.category(), expiresOn), actor);
        settleAlerts(tenantId, before, after.expiresOn(), actor);
        return after;
    }

    @Transactional
    public DocumentInfo archive(TenantId tenantId, UUID documentId, String reason, UUID actor) {
        access.require(za.co.handyflow.platform.earthmoving.domain.model.EarthmovingRules.ADMIN, "archive a document");
        DocumentInfo before = mine(tenantId, documentId, true);
        DocumentInfo after = documents.archive(tenantId, documentId, reason, actor);
        settleAlerts(tenantId, before, null, actor);
        return after;
    }

    @Transactional(readOnly = true)
    public List<VersionInfo> versions(TenantId tenantId, UUID documentId) {
        mine(tenantId, documentId, false);
        return documents.versions(tenantId, documentId);
    }

    @Transactional
    public DocumentsFacade.Download download(TenantId tenantId, UUID documentId, Integer versionNo, UUID actor) {
        mine(tenantId, documentId, false);
        return documents.download(tenantId, documentId, versionNo, actor);
    }

    /** Documents that have expired or expire within the days given, limited to the ones this person may see. */
    @Transactional(readOnly = true)
    public List<DocumentInfo> expiring(TenantId tenantId, int withinDays) {
        access.require(za.co.handyflow.platform.earthmoving.domain.model.EarthmovingRules.READ, "view documents");
        int days = Math.max(0, Math.min(withinDays, 365));
        return documents.expiring(tenantId, DocumentPolicy.ALERT_OWNERS, EarthHireService.today().plusDays(days)).stream().filter(this::canSee).toList();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean canSee(DocumentInfo d) { return access.has(DocumentPolicy.authority(d.category(), false)); }

    /** The document, if it is one of ours in this tenant and the caller holds the authority its category needs. Anything else is "not found". */
    private DocumentInfo mine(TenantId tenantId, UUID documentId, boolean write) {
        DocumentInfo d = documents.find(tenantId, documentId).filter(x -> DocumentPolicy.OWNERS.contains(x.ownerType()))
                .orElseThrow(() -> new ResourceNotFoundException("Document", String.valueOf(documentId)));
        access.require(DocumentPolicy.authority(d.category(), write), write ? "change this document" : "view this document");
        return d;
    }

    private void checkOwner(TenantId tenantId, String owner, UUID ownerId) {
        if (ownerId == null) throw new IllegalArgumentException("Say which machine, operator or disposal request the document belongs to.");
        if (DocumentPolicy.OWNER_ASSET.equals(owner)) assets.findActiveById(tenantId, ownerId).orElseThrow(() -> new ResourceNotFoundException("Asset", ownerId.toString()));
        else if (DocumentPolicy.OWNER_DISPOSAL.equals(owner)) disposals.findByTenantAndId(tenantId, ownerId).orElseThrow(() -> new ResourceNotFoundException("Disposal request", ownerId.toString()));
        else if (users.resolveUser(tenantId, ownerId).isEmpty()) throw new ResourceNotFoundException("User", ownerId.toString());
    }

    /** When the expiry date moves or the document is archived, the alerts raised for the old date no longer apply. */
    private void settleAlerts(TenantId tenantId, DocumentInfo before, LocalDate newExpiry, UUID actor) {
        if (before.expiresOn() == null || Objects.equals(before.expiresOn(), newExpiry)) return;
        List<String> keys = new ArrayList<>();
        for (DocumentPolicy.Stage s : DocumentPolicy.Stage.values()) if (s != DocumentPolicy.Stage.NONE) keys.add(DocumentPolicy.alertKey(before.id(), before.expiresOn(), s));
        keys.forEach(k -> alerts.resolveByKey(tenantId, k, actor, newExpiry == null ? "Document archived or expiry removed" : "Document renewed"));
    }
}
