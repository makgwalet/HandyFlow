package za.co.handyflow.platform.secureaccess.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Append-only trail of a collaborator ACCOUNT: registered, link sent, code, login, disabled, enabled. */
@Entity
@Immutable
@Table(name = "sda_collaborator_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecureCollaboratorLog {

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false)       private UUID tenantId;
    @Column(name = "collaborator_id", nullable = false) private UUID collaboratorId;
    @Column(name = "action", nullable = false, length = 30) private String action;
    @Column(name = "detail", length = 500)              private String detail;
    @Column(name = "ip_address", length = 64)           private String ipAddress;
    @Column(name = "user_agent", length = 255)          private String userAgent;
    @Column(name = "actor_name")                        private String actorName;
    @Column(name = "occurred_at", nullable = false)     private Instant occurredAt;

    public static SecureCollaboratorLog of(UUID tenantId, UUID collaboratorId, String action, String detail,
                                            String ip, String userAgent, String actorName) {
        SecureCollaboratorLog l = new SecureCollaboratorLog();
        l.id             = UUID.randomUUID();
        l.tenantId       = tenantId;
        l.collaboratorId = collaboratorId;
        l.action         = action;
        l.detail         = cut(detail, 500);
        l.ipAddress      = cut(ip, 64);
        l.userAgent      = cut(userAgent, 255);
        l.actorName      = cut(actorName, 255);
        l.occurredAt     = Instant.now();
        return l;
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
