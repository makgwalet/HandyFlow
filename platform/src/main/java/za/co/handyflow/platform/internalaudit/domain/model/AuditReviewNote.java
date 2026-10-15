package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A reviewer's note on a workpaper. The preparer answers it; someone other than the person who answered clears it, or sends it back.
 * A workpaper with notes that are not cleared cannot be signed off.
 */
@Entity(name = "AuditReviewNote")
@Table(name = "audit_review_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditReviewNote {
    public static final int MIN_TEXT = 5;

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id", nullable = false) private UUID engagementId;
    @Column(name = "workpaper_id", nullable = false) private UUID workpaperId;
    @Column(name = "note", nullable = false, columnDefinition = "TEXT") private String note;
    @Column(name = "raised_by", nullable = false) private UUID raisedBy;
    @Column(name = "raised_at", nullable = false) private Instant raisedAt = Instant.now();
    @Column(name = "status", nullable = false) private String status = "OPEN"; // OPEN | RESPONDED | CLEARED
    @Column(name = "response", columnDefinition = "TEXT") private String response;
    @Column(name = "responded_by") private UUID respondedBy;
    @Column(name = "responded_at") private Instant respondedAt;
    @Column(name = "cleared_by") private UUID clearedBy;
    @Column(name = "cleared_at") private Instant clearedAt;
    @Column(name = "times_returned", nullable = false) private int timesReturned = 0;

    private static String text(String t, String what) {
        if (t == null || t.trim().length() < MIN_TEXT) throw new IllegalArgumentException(what + " needs at least " + MIN_TEXT + " characters");
        return t.trim();
    }

    public static AuditReviewNote raise(UUID tenantId, UUID engagementId, UUID workpaperId, String note, UUID by) {
        AuditReviewNote n = new AuditReviewNote();
        n.tenantId = tenantId; n.engagementId = engagementId; n.workpaperId = workpaperId; n.raisedBy = by;
        n.note = text(note, "A review note");
        return n;
    }

    public void respond(UUID by, String reply) {
        if (!"OPEN".equals(status)) throw new IllegalStateException("Only an open note can be answered. Current status: " + status);
        this.response = text(reply, "A response");
        this.respondedBy = by; this.respondedAt = Instant.now(); this.status = "RESPONDED";
    }

    private void requireIndependent(UUID by) {
        if (!"RESPONDED".equals(status)) throw new IllegalStateException("Only an answered note can be cleared or returned. Current status: " + status);
        if (by.equals(respondedBy)) throw new IllegalStateException("The person who answered a note cannot clear it");
    }

    public void clear(UUID by) {
        requireIndependent(by);
        this.clearedBy = by; this.clearedAt = Instant.now(); this.status = "CLEARED";
    }

    /** The answer was not enough: back to open, the reply stays on record, the reviewer adds why. */
    public void returnWithComment(UUID by, String why) {
        requireIndependent(by);
        this.note = this.note + "\n\nReturned: " + text(why, "A reason");
        this.timesReturned++; this.status = "OPEN";
        this.respondedBy = null; this.respondedAt = null;
    }

    public boolean blocksSignOff() { return !"CLEARED".equals(status); }
}
