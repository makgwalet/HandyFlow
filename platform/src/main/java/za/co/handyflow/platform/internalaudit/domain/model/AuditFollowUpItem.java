package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** What became of one earlier finding, as found by the follow-up engagement. The outcome can be revised; each revision overwrites, and is on the audit trail. */
@Entity(name = "AuditFollowUpItem")
@Table(name = "audit_followup_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditFollowUpItem {
    public static final int MIN_NOTE = 10;
    public static final Set<String> OUTCOMES = Set.of("IMPLEMENTED", "PARTIALLY_IMPLEMENTED", "NOT_IMPLEMENTED", "NO_LONGER_APPLICABLE");

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id", nullable = false) private UUID engagementId;
    @Column(name = "finding_id", nullable = false) private UUID findingId;
    @Column(name = "outcome") private String outcome;
    @Column(name = "note", columnDefinition = "TEXT") private String note;
    @Column(name = "assessed_by") private UUID assessedBy;
    @Column(name = "assessed_by_name") private String assessedByName;
    @Column(name = "assessed_at") private Instant assessedAt;

    public static AuditFollowUpItem pending(UUID tenantId, UUID engagementId, UUID findingId) {
        AuditFollowUpItem i = new AuditFollowUpItem();
        i.tenantId = tenantId; i.engagementId = engagementId; i.findingId = findingId;
        return i;
    }

    public boolean isAssessed() { return outcome != null; }

    public void assess(String outcome, String note, UUID by, String byName) {
        String o = outcome == null ? "" : outcome.toUpperCase();
        if (!OUTCOMES.contains(o)) throw new IllegalArgumentException("Outcome must be IMPLEMENTED, PARTIALLY_IMPLEMENTED, NOT_IMPLEMENTED or NO_LONGER_APPLICABLE");
        if (note == null || note.trim().length() < MIN_NOTE) throw new IllegalArgumentException("Say what was checked (at least " + MIN_NOTE + " characters)");
        this.outcome = o; this.note = note.trim(); this.assessedBy = by; this.assessedByName = byName; this.assessedAt = Instant.now();
    }

    /** An earlier finding that is still not fully dealt with. */
    public boolean stillOpen() { return "PARTIALLY_IMPLEMENTED".equals(outcome) || "NOT_IMPLEMENTED".equals(outcome); }
}
