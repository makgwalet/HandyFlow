package za.co.handyflow.platform.accountant.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Closes the accountant module audit's "larger workpaper system" gap.
 * Append-only — no update/delete methods, deliberately: an audit log
 * that can be edited after the fact isn't an audit log.
 * <p>
 * The real schema also has a metadata JSONB column, deliberately not
 * mapped in this pass — the core fields (fileId, eventType,
 * performedBy, performedAt) already capture what a "who did what when"
 * audit trail needs, and mapping JSONB correctly under Hibernate's
 * schema validation would need confirming the exact type-handling
 * convention this project uses for it, which hasn't come up anywhere
 * else this session. Not guessed at here — deferred as a clearly
 * separate, smaller follow-up if richer per-event context is needed
 * later.
 */
@Entity(name = "AccountantWorkpaperAudit")
@Table(name = "acc_workpaper_audit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccWorkpaperAudit {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "file_id", nullable = false) private UUID fileId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(name = "performed_by") private UUID performedBy;
    @Column(name = "performed_at", nullable = false, updatable = false) private Instant performedAt;

    public static AccWorkpaperAudit record(UUID tenantId, UUID fileId, String eventType, UUID performedBy) {
        AccWorkpaperAudit a = new AccWorkpaperAudit();
        a.tenantId    = tenantId;
        a.fileId      = fileId;
        a.eventType   = eventType;
        a.performedBy = performedBy;
        a.performedAt = Instant.now();
        return a;
    }
}