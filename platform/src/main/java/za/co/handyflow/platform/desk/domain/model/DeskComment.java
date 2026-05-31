package za.co.handyflow.platform.desk.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

// ── DeskComment ───────────────────────────────────────────────────────────────
@Entity
@Table(name = "desk_comments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class DeskComment {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "ticket_id",   nullable = false) private UUID    ticketId;
    @Column(name = "tenant_id",   nullable = false) private UUID    tenantId;
    @Column(name = "author_name", nullable = false) private String  authorName;
    @Column(name = "author_type", nullable = false) private String  authorType; // TEAM|CUSTOMER|SYSTEM
    @Column(name = "is_internal", nullable = false) private boolean isInternal = false;
    @Column(nullable = false)                        private String  body;
    @Column(name = "created_at")                     private Instant createdAt;

    public static DeskComment create(UUID ticketId, UUID tenantId,
                                      String authorName, String authorType,
                                      boolean isInternal, String body) {
        DeskComment c  = new DeskComment();
        c.ticketId     = ticketId;
        c.tenantId     = tenantId;
        c.authorName   = authorName;
        c.authorType   = authorType;
        c.isInternal   = isInternal;
        c.body         = body;
        c.createdAt    = Instant.now();
        return c;
    }
}
