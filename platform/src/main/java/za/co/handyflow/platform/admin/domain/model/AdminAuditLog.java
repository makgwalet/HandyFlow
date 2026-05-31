package za.co.handyflow.platform.admin.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// ── AdminAuditLog ─────────────────────────────────────────────────────────────
@Entity
@Table(name = "admin_audit_log")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AdminAuditLog {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "admin_user_id", nullable = false) private UUID   adminUserId;
    @Column(name = "admin_email",   nullable = false) private String adminEmail;
    @Column(nullable = false)                          private String action;
    @Column(name = "target_type")  private String targetType;
    @Column(name = "target_id")    private String targetId;
    @Column(name = "target_name")  private String targetName;
    @Column(columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON) private String details;
    @Column(name = "ip_address")   private String ipAddress;
    @Column(name = "created_at")   private Instant createdAt;

    public static AdminAuditLog create(UUID adminUserId, String adminEmail,
                                        String action, String targetType,
                                        String targetId, String targetName,
                                        String details, String ipAddress) {
        AdminAuditLog l  = new AdminAuditLog();
        l.adminUserId    = adminUserId;
        l.adminEmail     = adminEmail;
        l.action         = action;
        l.targetType     = targetType;
        l.targetId       = targetId;
        l.targetName     = targetName;
        l.details        = details;
        l.ipAddress      = ipAddress;
        l.createdAt      = Instant.now();
        return l;
    }
}
