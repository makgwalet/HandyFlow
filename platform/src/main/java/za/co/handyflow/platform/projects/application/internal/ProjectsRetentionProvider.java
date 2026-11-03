package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.documents.application.RetentionProvider;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.projects.domain.enums.ProjectStatus;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.domain.model.ProjectDocument;
import za.co.handyflow.platform.projects.domain.model.ProjectDocumentReview;
import za.co.handyflow.platform.projects.domain.repository.ProjectDocumentRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectDocumentReviewRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * Projects register entries with a stored file (module PROJECTS, source type DOCUMENT) for the records retention engine. The clock
 * starts when the project is closed (DocumentService.stampRetention). Disposal deletes the stored file through Evidence (and so the
 * document store), marks the register entry disposed and writes it to the document's own history; the entry, its number, revision
 * and hash stay. A document under legal hold, or of a project that is no longer closed, is refused.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ProjectsRetentionProvider implements RetentionProvider {

    static final String MODULE = "PROJECTS";
    static final String SOURCE = "DOCUMENT";

    private final ProjectDocumentRepository docRepo;
    private final ProjectDocumentReviewRepository reviewRepo;
    private final ProjectRepository projectRepo;
    private final EvidenceFacade evidenceFacade;

    /** What the retention screen shows as the reference: number and revision. */
    static String reference(ProjectDocument d) {
        String n = d.getDocumentNumber() == null ? "" : d.getDocumentNumber();
        return d.getRevision() == null ? n : (n + " rev " + d.getRevision()).trim();
    }

    @Override
    public String moduleCode() { return MODULE; }

    @Override
    public Disposed dispose(TenantId tenantId, String sourceType, UUID sourceId, UUID batchId, UUID approvedBy) {
        if (!SOURCE.equals(sourceType)) throw new IllegalArgumentException("Unknown Projects record type " + sourceType);
        ProjectDocument doc = docRepo.findByTenantAndIdForUpdate(tenantId.getValue(), sourceId)
                .orElseThrow(() -> new IllegalStateException("The register entry no longer exists"));
        if (doc.isDisposed()) throw new IllegalStateException("The document was already disposed of");
        if (doc.isLegalHold()) throw new IllegalStateException("The document is under legal hold in the project register");
        if (!doc.hasStoredFile()) throw new IllegalStateException("The register entry has no stored file");
        Project project = projectRepo.findByTenantAndId(tenantId.getValue(), doc.getProjectId())
                .orElseThrow(() -> new IllegalStateException("The project no longer exists"));
        if (project.getStatus() != ProjectStatus.CLOSED) throw new IllegalStateException("The project is no longer closed");

        String sha = evidenceFacade.dispose(tenantId, "projects", "ProjectDocument", doc.getId(), doc.getEvidenceId(),
                "Disposed under the retention policy (request " + batchId + ")");
        doc.markDisposed(batchId);
        docRepo.save(doc);
        reviewRepo.save(ProjectDocumentReview.record(doc.getTenantId(), doc.getProjectId(), doc.getId(), "DISPOSED",
                "File disposed of under the retention policy (request " + batchId + "); the register entry is kept", approvedBy, null));
        log.info("Project document={} disposed (request {}) project={}", doc.getId(), batchId, doc.getProjectId());
        String hash = sha == null ? doc.getFileSha256() : sha;
        return new Disposed(1, "1 file removed" + (hash == null ? "" : "; sha256 " + hash));
    }

    @Override
    public void legalHoldChanged(TenantId tenantId, String sourceType, UUID sourceId, boolean hold, String reason, UUID by, String byName) {
        if (!SOURCE.equals(sourceType)) return;
        ProjectDocument doc = docRepo.findByTenantAndIdForUpdate(tenantId.getValue(), sourceId).orElse(null);
        if (doc == null || doc.isLegalHold() == hold) return;
        doc.changeLegalHold(hold);
        docRepo.save(doc);
        reviewRepo.save(ProjectDocumentReview.record(doc.getTenantId(), doc.getProjectId(), doc.getId(), "LEGAL_HOLD",
                (hold ? "Placed on the records retention screen: " : "Lifted on the records retention screen: ") + reason, by, byName));
    }
}
