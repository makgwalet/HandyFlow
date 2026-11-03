package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * One line in a document revision's trail: uploaded, submitted, approved, rejected, superseded,
 * voided, visibility changed, downloaded, linked, and so on. Append-only: the entity is
 * {@link Immutable}, has no setters, and nothing in the module updates or deletes these rows.
 *
 * This is the record of WHO did WHAT to a controlled document and WHEN, so an approval stays
 * traceable even after a newer revision replaces the document.
 */
@Entity
@Immutable
@Table(name = "project_document_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectDocumentReview {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    /** UPLOADED | SUBMITTED | APPROVED | REJECTED | SUPERSEDED | VOIDED | VISIBILITY | DOWNLOADED | LINKED | UNLINKED | LEGAL_HOLD */
    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "comment")
    private String comment;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public static ProjectDocumentReview record(UUID tenantId, UUID projectId, UUID documentId,
                                               String action, String comment,
                                               UUID actorId, String actorName) {
        ProjectDocumentReview r = new ProjectDocumentReview();
        r.id         = UUID.randomUUID();
        r.tenantId   = tenantId;
        r.projectId  = projectId;
        r.documentId = documentId;
        r.action     = action;
        r.comment    = comment;
        r.actorId    = actorId;
        r.actorName  = actorName;
        r.occurredAt = Instant.now();
        return r;
    }
}
