package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One line of the case record. Each line carries the hash of the line before it, so an edit or deletion anywhere breaks every hash after it. */
@Entity(name = "AuditInvestigationLogEntry")
@Table(name = "audit_investigation_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditInvestigationLogEntry {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "investigation_id", nullable = false) private UUID investigationId;
    @Column(name = "seq", nullable = false) private int seq;
    @Column(name = "at", nullable = false) private Instant at;
    @Column(name = "actor_id", nullable = false) private UUID actorId;
    @Column(name = "actor_name", nullable = false) private String actorName;
    @Column(name = "action", nullable = false) private String action;
    @Column(name = "detail", columnDefinition = "TEXT") private String detail;
    @Column(name = "prev_hash", nullable = false) private String prevHash;
    @Column(name = "hash", nullable = false) private String hash;

    public static AuditInvestigationLogEntry next(UUID tenantId, UUID investigationId, AuditInvestigationLogEntry previous, Instant at,
                                                  UUID actorId, String actorName, String action, String detail) {
        AuditInvestigationLogEntry e = new AuditInvestigationLogEntry();
        e.tenantId = tenantId; e.investigationId = investigationId;
        e.seq = previous == null ? 1 : previous.seq + 1;
        e.prevHash = previous == null ? za.co.handyflow.platform.internalaudit.application.internal.InvestigationChain.GENESIS : previous.hash;
        e.at = at; e.actorId = actorId; e.actorName = actorName; e.action = action; e.detail = detail;
        e.hash = za.co.handyflow.platform.internalaudit.application.internal.InvestigationChain.hash(e.seq, at, actorId, action, detail, e.prevHash);
        return e;
    }
}
