package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_documents")
@Getter
@NoArgsConstructor
public class ProjectDocument {

    @Id UUID id;
    @Column(name = "tenant_id",  nullable = false) UUID    tenantId;
    @Column(name = "project_id", nullable = false) UUID    projectId;
    // document_type: DRAWING | RFI | SUBMITTAL | CONTRACT | REPORT | PHOTO | GENERAL
    @Column(name = "document_type", nullable = false, length = 30) String documentType = "GENERAL";
    @Column(nullable = false, length = 300) String title;
    @Column(length = 20) String revision;
    @Column(name = "file_url")     String fileUrl;
    @Column(name = "file_name")    String fileName;
    @Column(name = "file_size_kb") Integer fileSizeKb;
    // status: DRAFT | FOR_REVIEW | APPROVED | SUPERSEDED | CURRENT
    @Column(nullable = false, length = 20) String status = "CURRENT";
    String description;
    @Column(name = "uploaded_by")      UUID   uploadedBy;
    @Column(name = "uploaded_by_name") String uploadedByName;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    public static ProjectDocument create(UUID tenantId, UUID projectId, String documentType,
                                         String title, String revision, String fileUrl,
                                         String fileName, Integer fileSizeKb,
                                         UUID uploadedBy, String uploadedByName) {
        ProjectDocument d  = new ProjectDocument();
        d.id               = UUID.randomUUID();
        d.tenantId         = tenantId;
        d.projectId        = projectId;
        d.documentType     = documentType != null ? documentType : "GENERAL";
        d.title            = title;
        d.revision         = revision;
        d.fileUrl          = fileUrl;
        d.fileName         = fileName;
        d.fileSizeKb       = fileSizeKb;
        d.status           = "CURRENT";
        d.uploadedBy       = uploadedBy;
        d.uploadedByName   = uploadedByName;
        d.createdAt        = Instant.now();
        return d;
    }

    public void approve()    { this.status = "APPROVED"; }
    public void supersede()  { this.status = "SUPERSEDED"; }
    public void submitForReview() { this.status = "FOR_REVIEW"; }

    public void setTitle(String v)       { this.title       = v; }
    public void setRevision(String v)    { this.revision    = v; }
    public void setDescription(String v) { this.description = v; }
    public void setStatus(String v)      { this.status      = v; }
}
