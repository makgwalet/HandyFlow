package za.co.handyflow.platform.admin.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_impersonation_sessions")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AdminImpersonationSession {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "admin_user_id", nullable = false) private UUID   adminUserId;
    @Column(name = "tenant_id",     nullable = false) private UUID   tenantId;
    @Column(name = "admin_email",   nullable = false) private String adminEmail;
    @Column(name = "tenant_slug",   nullable = false) private String tenantSlug;
    private String  reason;
    @Column(name = "started_at")  private Instant startedAt;
    @Column(name = "ended_at")    private Instant endedAt;
    @Column(name = "ip_address")  private String  ipAddress;

    public static AdminImpersonationSession create(UUID adminUserId, UUID tenantId,
                                                    String adminEmail, String tenantSlug,
                                                    String reason, String ipAddress) {
        AdminImpersonationSession s = new AdminImpersonationSession();
        s.adminUserId  = adminUserId;
        s.tenantId     = tenantId;
        s.adminEmail   = adminEmail;
        s.tenantSlug   = tenantSlug;
        s.reason       = reason;
        s.ipAddress    = ipAddress;
        s.startedAt    = Instant.now();
        return s;
    }

    public void end() { this.endedAt = Instant.now(); }
}
