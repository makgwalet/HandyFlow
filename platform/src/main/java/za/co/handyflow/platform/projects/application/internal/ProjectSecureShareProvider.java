package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.projects.domain.enums.DocumentStatus;
import za.co.handyflow.platform.projects.domain.model.ProjectTransmittal;
import za.co.handyflow.platform.projects.domain.repository.ProjectDocumentRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectTransmittalRepository;
import za.co.handyflow.platform.secureaccess.application.SecureShareProvider;

import java.util.UUID;

/**
 * Projects' side of the platform Secure Document Access Engine. The engine owns links, codes, sessions,
 * expiry, revocation and the access log; PROJECTS owns the transmittal and the documents, and answers here:
 *
 *   share  = module PROJECTS, type TRANSMITTAL, id = the transmittal, context = the project
 *   items  = module PROJECTS, type DOCUMENT, id = the project document
 *
 * authorise() is asked on EVERY view and download (and for collaborators, every workspace load), so a link
 * can never release a document the project would no longer release: the project must still exist in the
 * tenant, the document must still belong to it, and a voided document is refused.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectSecureShareProvider implements SecureShareProvider {

    public static final String MODULE        = "PROJECTS";
    public static final String SHARE_TYPE    = "TRANSMITTAL";
    public static final String DOCUMENT_TYPE = "DOCUMENT";

    private final ProjectTransmittalRepository transmittalRepo;
    private final ProjectRepository            projectRepo;
    private final ProjectDocumentRepository    docRepo;
    private final DocumentService              documentService;

    @Override public String moduleCode() { return MODULE; }

    @Override
    public ShareHeader describe(UUID tenantId, ShareRef share) {
        ProjectTransmittal t = transmittalRepo.findByProjectAndId(tenantId, share.contextId(), share.sourceId()).orElse(null);
        if (t == null) return null;
        String projectName = projectRepo.findByTenantAndId(tenantId, share.contextId())
                .map(p -> p.getName()).orElse("");
        return new ShareHeader(t.getTransmittalNumber(), t.getSubject(), projectName, t.getPurpose(), t.getNotes(),
                t.getSentByName(), t.getSentAt());
    }

    @Override
    public Decision authorise(UUID tenantId, ShareRef share, DocumentRef doc, Who who) {
        if (!MODULE.equals(doc.moduleCode()) || !DOCUMENT_TYPE.equals(doc.sourceType())) {
            return Decision.deny("This document is not available");
        }
        if (projectRepo.findByTenantAndId(tenantId, share.contextId()).isEmpty()) {
            return Decision.deny("This project is no longer available");
        }
        return docRepo.findByTenantAndId(tenantId, doc.sourceId())
                .filter(d -> share.contextId().equals(d.getProjectId()))
                .map(d -> d.statusEnum() == DocumentStatus.VOIDED
                        ? Decision.deny("This document has been withdrawn") : Decision.allow())
                .orElseGet(() -> Decision.deny("This document is no longer available"));
    }

    @Override
    public ReleasedFile release(UUID tenantId, ShareRef share, DocumentRef doc, String issuedSha256, String recipientLabel) {
        DocumentService.FileDownload f = documentService.downloadIssuedRevision(
                tenantId, share.contextId(), doc.sourceId(), issuedSha256, recipientLabel);
        return new ReleasedFile(f.fileName(), f.contentType(), f.content());
    }

    /** Runs inside the engine's acknowledgement transaction. When everyone has acknowledged, the transmittal itself is marked. */
    @Override
    public void onEvent(UUID tenantId, ShareRef share, ShareEvent event) {
        if (!ShareEvent.ACKNOWLEDGED.equals(event.type()) || !event.everyRecipientAcknowledged()) return;
        transmittalRepo.findByProjectAndIdForUpdate(tenantId, share.contextId(), share.sourceId()).ifPresent(t -> {
            if (t.getAcknowledgedAt() == null) {
                t.acknowledge(event.activeRecipients() == 1 ? event.recipientName() : "All " + event.activeRecipients() + " recipients");
                transmittalRepo.save(t);
            }
        });
    }
}
