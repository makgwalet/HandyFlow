package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.projects.domain.enums.DocumentLinkType;

import java.time.Instant;
import java.util.UUID;

/**
 * "This document revision is evidence for that RFI / change order / task / snag / risk / site diary."
 *
 * The link points at one REVISION (a project_documents row), so the record shows exactly which
 * version was relied on. Linking and unlinking are both written to the document's trail
 * (ProjectDocumentReview), so removing a link never erases the fact that it existed.
 */
@Entity
@Table(name = "project_document_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectDocumentLink {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    /** A DocumentLinkType name. */
    @Column(name = "link_type", nullable = false, length = 20)
    private String linkType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "linked_by")
    private UUID linkedBy;

    @Column(name = "linked_by_name")
    private String linkedByName;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    public static ProjectDocumentLink create(UUID tenantId, UUID projectId, UUID documentId,
                                             DocumentLinkType type, UUID targetId, String note,
                                             UUID linkedBy, String linkedByName) {
        ProjectDocumentLink l = new ProjectDocumentLink();
        l.id           = UUID.randomUUID();
        l.tenantId     = tenantId;
        l.projectId    = projectId;
        l.documentId   = documentId;
        l.linkType     = type.name();
        l.targetId     = targetId;
        l.note         = note;
        l.linkedBy     = linkedBy;
        l.linkedByName = linkedByName;
        l.linkedAt     = Instant.now();
        return l;
    }
}
