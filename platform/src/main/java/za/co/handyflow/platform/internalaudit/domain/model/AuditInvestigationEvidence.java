package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** An item of evidence on a case and who holds it now. The file itself is not stored here; this is the register of who had what, when (the movements are in the case record). */
@Entity(name = "AuditInvestigationEvidence")
@Table(name = "audit_investigation_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditInvestigationEvidence {
    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "investigation_id", nullable = false) private UUID investigationId;
    @Column(name = "ref", nullable = false) private String ref;
    @Column(name = "description", columnDefinition = "TEXT", nullable = false) private String description;
    @Column(name = "source") private String source;
    @Column(name = "collected_by", nullable = false) private UUID collectedBy;
    @Column(name = "collected_at", nullable = false) private Instant collectedAt = Instant.now();
    @Column(name = "holder_id", nullable = false) private UUID holderId;
    @Column(name = "holder_name", nullable = false) private String holderName;

    public static String refFor(int sequence) { return String.format("EV-%03d", sequence); }

    public static AuditInvestigationEvidence collect(UUID tenantId, UUID investigationId, String ref, String description, String source, UUID by, String byName) {
        if (description == null || description.trim().length() < 10) throw new IllegalArgumentException("Describe the evidence (at least 10 characters)");
        AuditInvestigationEvidence e = new AuditInvestigationEvidence();
        e.tenantId = tenantId; e.investigationId = investigationId; e.ref = ref; e.description = description.trim();
        e.source = source == null || source.isBlank() ? null : source.trim(); e.collectedBy = by; e.holderId = by; e.holderName = byName;
        return e;
    }

    /** Only the person holding it, or a lead, can hand it on. */
    public void transfer(UUID actorId, boolean actorIsLead, UUID toId, String toName) {
        if (!actorIsLead && !holderId.equals(actorId)) throw new IllegalStateException("Only the current holder or a lead can transfer this item");
        if (holderId.equals(toId)) throw new IllegalArgumentException("That person already holds this item");
        this.holderId = toId; this.holderName = toName;
    }
}
