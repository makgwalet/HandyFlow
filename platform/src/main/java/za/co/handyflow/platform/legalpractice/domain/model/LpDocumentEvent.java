package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Who did what to a document and when: uploads, downloads, check-outs, holds, archiving. Append-only. */
@Entity
@Table(name = "lp_document_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpDocumentEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "document_id", nullable = false, updatable = false) private UUID documentId;
    @Column(name = "version_no", updatable = false) private Integer versionNo;
    @Column(name = "event_type", nullable = false, updatable = false, length = 20) private String eventType;
    @Column(name = "actor_id", nullable = false, updatable = false) private UUID actorId;
    @Column(name = "actor_name", updatable = false) private String actorName;
    @Column(updatable = false) private String detail;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;

    public static LpDocumentEvent record(TenantId tenantId, UUID documentId, Integer versionNo, String eventType, UUID actor, String actorName, String detail, Instant now) {
        LpDocumentEvent e = new LpDocumentEvent();
        e.tenantId = tenantId; e.documentId = documentId; e.versionNo = versionNo; e.eventType = eventType;
        e.actorId = actor; e.actorName = actorName; e.detail = detail; e.occurredAt = now;
        return e;
    }
}
