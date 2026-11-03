package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittal;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittalItem;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectTransmittalItemRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectTransmittalRepository;
import za.co.handyflow.platform.projects.dto.TransmittalAccessLogResponse;
import za.co.handyflow.platform.projects.dto.TransmittalRecipientRequest;
import za.co.handyflow.platform.projects.dto.TransmittalRecipientResponse;
import za.co.handyflow.platform.secureaccess.application.SecureAccessDtos.*;
import za.co.handyflow.platform.secureaccess.application.SecureShareService;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

import static za.co.handyflow.platform.projects.application.internal.ProjectSecureShareProvider.*;

/**
 * Projects' thin facade over the platform Secure Document Access Engine (secureaccess). It owns only the
 * PROJECT rules: the project and transmittal must exist in the tenant, no new access on a CLOSED project,
 * and only the sender or a document controller may revoke. Everything about links, codes, sessions,
 * expiry, revocation and the access log is the engine's. Recipients open a transmittal at
 * /secure/share/{token}; the engine asks ProjectSecureShareProvider before releasing anything.
 */
@Service
@RequiredArgsConstructor
public class TransmittalAccessService {

    private final ProjectRepository                projectRepo;
    private final ProjectTransmittalRepository     transmittalRepo;
    private final ProjectTransmittalItemRepository itemRepo;
    private final DocumentService                  documentService;
    private final SecureShareService               engine;

    /** Called by TransmittalService.create in the same transaction. Always opens the share, even with no recipients yet. */
    @Transactional
    public List<TransmittalRecipientResponse> issue(TenantId tenantId, Project project, ProjectTransmittal t,
                                                    List<ProjectTransmittalItem> items,
                                                    List<TransmittalRecipientRequest> specs,
                                                    Integer expiryDays, boolean attach, UUID by, String byName) {
        ShareOpened opened = engine.openShare(new OpenShare(tenantId.getValue(), MODULE, SHARE_TYPE, t.getId(),
                project.getId(), itemSpecs(items), toSpecs(specs, items), expiryDays, attach, by, byName));
        Map<UUID, UUID> itemToDoc = invert(engine.itemIdsByDocument(tenantId.getValue(), opened.shareId()));
        return opened.recipients().stream().map(v -> TransmittalRecipientResponse.of(v, itemToDoc)).toList();
    }

    /** Adds one more recipient to an existing transmittal. */
    @Transactional
    public TransmittalRecipientResponse addRecipient(TenantId tenantId, UUID projectId, UUID transmittalId,
                                                     TransmittalRecipientRequest spec, Integer expiryDays,
                                                     boolean attach, UUID by, String byName) {
        Project project = project(tenantId, projectId);
        if (project.getStatus() == ProjectStatus.CLOSED) {
            throw new HandyFlowException("Project is CLOSED — no further access can be granted",
                    HttpStatus.CONFLICT, "PROJECT_CLOSED");
        }
        ProjectTransmittal t = transmittal(tenantId, projectId, transmittalId);
        List<ProjectTransmittalItem> items = itemRepo.findByTransmittal(t.getId());
        UUID shareId = ensureShare(tenantId, project, t, items, by, byName);
        RecipientView v = engine.addRecipient(tenantId.getValue(), shareId, toSpec(spec, items), expiryDays, attach, by, byName);
        return TransmittalRecipientResponse.of(v, invert(engine.itemIdsByDocument(tenantId.getValue(), shareId)));
    }

    /** For a transmittal recorded before secure links existed: sends its recorded recipient a link now (all documents). */
    @Transactional
    public TransmittalRecipientResponse issueLegacyRecipient(TenantId tenantId, UUID projectId, UUID transmittalId,
                                                             Integer expiryDays, UUID by, String byName) {
        ProjectTransmittal t = transmittal(tenantId, projectId, transmittalId);
        if (t.getRecipientEmail() == null || t.getRecipientEmail().isBlank()) {
            throw new HandyFlowException("This transmittal has no email address on record. Add the recipient with their address.",
                    HttpStatus.BAD_REQUEST, "NO_EMAIL_ON_RECORD");
        }
        TransmittalRecipientRequest spec = new TransmittalRecipientRequest(t.getRecipientName(),
                t.getRecipientOrganisation(), t.getRecipientEmail().trim(), true, true, null);
        return addRecipient(tenantId, projectId, transmittalId, spec, expiryDays, false, by, byName);
    }

    /** A new link for the recipient. The old link, any unused code and any open session stop working. */
    @Transactional
    public TransmittalRecipientResponse reissue(TenantId tenantId, UUID projectId, UUID transmittalId, UUID recipientId,
                                                Integer expiryDays, UUID by, String byName) {
        project(tenantId, projectId);
        transmittal(tenantId, projectId, transmittalId);
        UUID shareId = shareOf(tenantId, transmittalId);
        RecipientView v = engine.reissue(tenantId.getValue(), shareId, recipientId, expiryDays, byName);
        return TransmittalRecipientResponse.of(v, invert(engine.itemIdsByDocument(tenantId.getValue(), shareId)));
    }

    /** Immediate. Sender or document controller only: that is a PROJECT rule, so it is checked here, not in the engine. */
    @Transactional
    public TransmittalRecipientResponse revoke(TenantId tenantId, UUID projectId, UUID transmittalId, UUID recipientId,
                                               String reason, UUID by, String byName) {
        project(tenantId, projectId);
        ProjectTransmittal t = transmittal(tenantId, projectId, transmittalId);
        boolean sender = by != null && by.equals(t.getSentBy());
        if (!sender && !documentService.isDocumentController(tenantId, projectId, by)) {
            throw new HandyFlowException("Only the person who sent the transmittal, or a document controller, can revoke access",
                    HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        UUID shareId = shareOf(tenantId, transmittalId);
        RecipientView v = engine.revoke(tenantId.getValue(), shareId, recipientId, reason, byName);
        return TransmittalRecipientResponse.of(v, invert(engine.itemIdsByDocument(tenantId.getValue(), shareId)));
    }

    @Transactional(readOnly = true)
    public List<TransmittalRecipientResponse> recipients(TenantId tenantId, UUID projectId, UUID transmittalId) {
        project(tenantId, projectId);
        transmittal(tenantId, projectId, transmittalId);
        return recipientViews(tenantId, transmittalId);
    }

    @Transactional(readOnly = true)
    public List<TransmittalAccessLogResponse> accessLog(TenantId tenantId, UUID projectId, UUID transmittalId) {
        project(tenantId, projectId);
        transmittal(tenantId, projectId, transmittalId);
        return engine.findShareId(tenantId.getValue(), MODULE, SHARE_TYPE, transmittalId)
                .map(id -> engine.accessLog(tenantId.getValue(), id).stream().map(TransmittalAccessLogResponse::of).toList())
                .orElse(List.of());
    }

    /** Used by TransmittalService for the register views. A transmittal with no share yet has no recipients. */
    @Transactional(readOnly = true)
    public List<TransmittalRecipientResponse> recipientViews(TenantId tenantId, UUID transmittalId) {
        return engine.findShareId(tenantId.getValue(), MODULE, SHARE_TYPE, transmittalId).map(shareId -> {
            Map<UUID, UUID> itemToDoc = invert(engine.itemIdsByDocument(tenantId.getValue(), shareId));
            return engine.recipientViews(tenantId.getValue(), shareId).stream()
                    .map(v -> TransmittalRecipientResponse.of(v, itemToDoc)).toList();
        }).orElse(List.of());
    }

    // ── internals ────────────────────────────────────────────────────────────

    private UUID ensureShare(TenantId tenantId, Project project, ProjectTransmittal t,
                             List<ProjectTransmittalItem> items, UUID by, String byName) {
        return engine.findShareId(tenantId.getValue(), MODULE, SHARE_TYPE, t.getId()).orElseGet(() ->
                engine.openShare(new OpenShare(tenantId.getValue(), MODULE, SHARE_TYPE, t.getId(), project.getId(),
                        itemSpecs(items), List.of(), null, false, by, byName)).shareId());
    }

    private UUID shareOf(TenantId tenantId, UUID transmittalId) {
        return engine.findShareId(tenantId.getValue(), MODULE, SHARE_TYPE, transmittalId)
                .orElseThrow(() -> new HandyFlowException("Recipient not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private static List<ItemSpec> itemSpecs(List<ProjectTransmittalItem> items) {
        return items.stream().map(i -> new ItemSpec(MODULE, DOCUMENT_TYPE, i.getDocumentId(), i.getRevision(),
                i.getDocumentNumber(), i.getTitle(), i.getStatusAtSend(), i.getFileName(), i.getFileSha256())).toList();
    }

    private static List<RecipientSpec> toSpecs(List<TransmittalRecipientRequest> specs, List<ProjectTransmittalItem> items) {
        return specs.stream().map(s -> toSpec(s, items)).toList();
    }

    /** The request names documents; the engine wants positions in the share's item list (same order as the transmittal's items). */
    private static RecipientSpec toSpec(TransmittalRecipientRequest s, List<ProjectTransmittalItem> items) {
        List<Integer> idx = new ArrayList<>();
        if (s.documentIds() != null) {
            for (UUID docId : new LinkedHashSet<>(s.documentIds())) {
                int pos = -1;
                for (int i = 0; i < items.size(); i++) if (items.get(i).getDocumentId().equals(docId)) { pos = i; break; }
                if (pos < 0) {
                    throw new HandyFlowException("A document assigned to " + s.name() + " is not on this transmittal",
                            HttpStatus.BAD_REQUEST, "DOCUMENT_NOT_ON_TRANSMITTAL");
                }
                idx.add(pos);
            }
        }
        return new RecipientSpec(s.name(), s.organisation(), s.email(), s.canDownload(), s.canAcknowledge(), idx);
    }

    private static Map<UUID, UUID> invert(Map<UUID, UUID> docToItem) {
        Map<UUID, UUID> m = new HashMap<>();
        docToItem.forEach((doc, item) -> m.put(item, doc));
        return m;
    }

    private Project project(TenantId tenantId, UUID projectId) {
        return projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> new HandyFlowException("Project not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    private ProjectTransmittal transmittal(TenantId tenantId, UUID projectId, UUID transmittalId) {
        return transmittalRepo.findByProjectAndId(tenantId.getValue(), projectId, transmittalId)
                .orElseThrow(() -> new HandyFlowException("Transmittal not found", HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }
}
