package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * One line in the audit trail of a transmittal's external access: link sent, code requested, code
 * wrong, verified, viewed, downloaded, acknowledged, refused, revoked. Append-only (immutable entity,
 * no setters, nothing updates or deletes these rows). Names the recipient, the document and revision,
 * the time and where the request came from.
 */
@Entity
@Immutable
@Table(name = "project_transmittal_access_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectTransmittalAccessLog {

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false)      private UUID tenantId;
    @Column(name = "project_id", nullable = false)     private UUID projectId;
    @Column(name = "transmittal_id", nullable = false) private UUID transmittalId;
    @Column(name = "recipient_id")                     private UUID recipientId;
    @Column(name = "document_id")                      private UUID documentId;
    @Column(name = "revision", length = 50)            private String revision;
    @Column(name = "action", nullable = false, length = 30) private String action;
    @Column(name = "detail", length = 500)             private String detail;
    @Column(name = "ip_address", length = 64)          private String ipAddress;
    @Column(name = "user_agent", length = 255)         private String userAgent;
    @Column(name = "actor_name")                       private String actorName;
    @Column(name = "occurred_at", nullable = false)    private Instant occurredAt;

    public static ProjectTransmittalAccessLog of(UUID tenantId, UUID projectId, UUID transmittalId, UUID recipientId,
                                                 UUID documentId, String revision, String action, String detail,
                                                 String ip, String userAgent, String actorName) {
        ProjectTransmittalAccessLog l = new ProjectTransmittalAccessLog();
        l.id            = UUID.randomUUID();
        l.tenantId      = tenantId;
        l.projectId     = projectId;
        l.transmittalId = transmittalId;
        l.recipientId   = recipientId;
        l.documentId    = documentId;
        l.revision      = cut(revision, 50);
        l.action        = action;
        l.detail        = cut(detail, 500);
        l.ipAddress     = cut(ip, 64);
        l.userAgent     = cut(userAgent, 255);
        l.actorName     = cut(actorName, 255);
        l.occurredAt    = Instant.now();
        return l;
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
