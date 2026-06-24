package za.co.handyflow.platform.projects.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.projects.domain.model.ProjectDocument;
import za.co.handyflow.platform.projects.domain.repository.ProjectDocumentRepository;
import za.co.handyflow.platform.projects.domain.repository.ProjectRepository;
import za.co.handyflow.platform.projects.dto.UploadDocumentRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final ProjectDocumentRepository documentRepo;
    private final ProjectRepository         projectRepo;

    @Transactional(readOnly = true)
    public List<ProjectDocument> getDocuments(TenantId tenantId, UUID projectId, String type) {
        verifyProject(tenantId, projectId);
        return type != null && !type.isBlank()
                ? documentRepo.findByProjectAndType(projectId, type)
                : documentRepo.findByProject(projectId);
    }

    @Transactional
    public ProjectDocument uploadDocument(TenantId tenantId, UUID projectId,
                                          UploadDocumentRequest req,
                                          UUID uploadedBy, String uploadedByName) {
        verifyProject(tenantId, projectId);

        // If this is a new revision of an existing drawing, supersede the old one
        if (req.revision() != null && req.documentType() != null) {
            documentRepo.findByProjectAndType(projectId, req.documentType()).stream()
                    .filter(d -> d.getTitle().equalsIgnoreCase(req.title())
                            && "CURRENT".equals(d.getStatus()))
                    .forEach(d -> { d.supersede(); documentRepo.save(d); });
        }

        ProjectDocument doc = ProjectDocument.create(
                tenantId.getValue(), projectId, req.documentType(),
                req.title(), req.revision(), req.fileUrl(),
                req.fileName(), req.fileSizeKb(), uploadedBy, uploadedByName);
        if (req.description() != null) doc.setDescription(req.description());
        return documentRepo.save(doc);
    }

    @Transactional
    public ProjectDocument updateDocumentStatus(TenantId tenantId, UUID docId, String action) {
        ProjectDocument doc = documentRepo.findByTenantAndId(tenantId.getValue(), docId)
                .orElseThrow(() -> notFound("Document"));
        switch (action.toUpperCase()) {
            case "APPROVE"         -> doc.approve();
            case "SUBMIT_REVIEW"   -> doc.submitForReview();
            case "SUPERSEDE"       -> doc.supersede();
            default -> throw new HandyFlowException("Unknown action: " + action,
                    HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        }
        return documentRepo.save(doc);
    }

    private void verifyProject(TenantId tenantId, UUID projectId) {
        projectRepo.findByTenantAndId(tenantId.getValue(), projectId)
                .orElseThrow(() -> notFound("Project"));
    }

    private HandyFlowException notFound(String entity) {
        return new HandyFlowException(entity + " not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }
}
