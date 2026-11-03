package za.co.handyflow.platform.secureaccess.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * One line in the audit trail of a share's external access: link sent, code requested, code wrong,
 * verified, viewed, downloaded, acknowledged, refused, revoked. Append-only: immutable entity, no setters,
 * and the database refuses UPDATE and DELETE (trigger in V577).
 */
@Entity
@Immutable
@Table(name = "sda_access_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecureAccessLog {

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false)      private UUID tenantId;
    @Column(name = "share_id", nullable = false)       private UUID shareId;
    @Column(name = "recipient_id")                     private UUID recipientId;
    @Column(name = "doc_source_id")                    private UUID docSourceId;
    @Column(name = "revision", length = 50)            private String revision;
    @Column(name = "action", nullable = false, length = 30) private String action;
    @Column(name = "detail", length = 500)             private String detail;
    @Column(name = "ip_address", length = 64)          private String ipAddress;
    @Column(name = "user_agent", length = 255)         private String userAgent;
    @Column(name = "actor_name")                       private String actorName;
    @Column(name = "occurred_at", nullable = false)    private Instant occurredAt;

    public static SecureAccessLog of(UUID tenantId, UUID shareId, UUID recipientId, UUID docSourceId, String revision,
                                     String action, String detail, String ip, String userAgent, String actorName) {
        SecureAccessLog l = new SecureAccessLog();
        l.id          = UUID.randomUUID();
        l.tenantId    = tenantId;
        l.shareId     = shareId;
        l.recipientId = recipientId;
        l.docSourceId = docSourceId;
        l.revision    = cut(revision, 50);
        l.action      = action;
        l.detail      = cut(detail, 500);
        l.ipAddress   = cut(ip, 64);
        l.userAgent   = cut(userAgent, 255);
        l.actorName   = cut(actorName, 255);
        l.occurredAt  = Instant.now();
        return l;
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
