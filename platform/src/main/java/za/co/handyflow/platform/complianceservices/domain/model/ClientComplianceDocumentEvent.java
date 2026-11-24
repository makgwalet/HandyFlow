package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One step in a document's life, kept for good. */
@Entity
@Table(name = "client_compliance_document_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientComplianceDocumentEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private String event;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private UUID actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    public static ClientComplianceDocumentEvent of(TenantId tenantId, UUID documentId, String event, String from, String to, String reason, UUID actor) {
        ClientComplianceDocumentEvent e = new ClientComplianceDocumentEvent();
        e.tenantId = tenantId;
        e.documentId = documentId;
        e.event = event;
        e.fromStatus = from;
        e.toStatus = to;
        e.reason = reason;
        e.actor = actor;
        return e;
    }
}
