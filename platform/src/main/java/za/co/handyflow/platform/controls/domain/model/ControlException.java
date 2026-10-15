package za.co.handyflow.platform.controls.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A flagged problem from ANY module, on the shared "needs attention"
 * board — Stage 1 of the Financial Control & Assurance plan, Option C.
 * <p>
 * Deliberately generic, same shape decision as Evidence: sourceModule +
 * controlType + relatedEntityType + relatedEntityId identify what
 * raised this and what it's about, without a foreign key into any
 * specific module's schema — a check in Accounting or Payroll should be
 * able to raise one of these without this module ever depending on them.
 * <p>
 * Confirmed real precedent this generalizes: SCM's own three-way match
 * (ScSupplierInvoice.matchStatus = DISPUTE) — that mechanism is
 * UNTOUCHED by this class. SCM keeps its own matchStatus as the real
 * source of truth for its own screens; this table is an ADDITIONAL,
 * parallel record for the cross-module view, written at the same
 * moment SCM already notifies on a dispute, not a replacement for it.
 */
@Entity
@Table(name = "control_exceptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ControlException {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;

    @Column(name = "source_module", nullable = false, length = 50) private String sourceModule;
    @Column(name = "control_type", nullable = false, length = 100) private String controlType;
    @Column(name = "related_entity_type", nullable = false, length = 100) private String relatedEntityType;
    @Column(name = "related_entity_id", nullable = false) private UUID relatedEntityId;

    @Column(name = "severity", nullable = false, length = 20) private String severity = "WARNING";
    @Column(name = "description", nullable = false, columnDefinition = "TEXT") private String description;
    @Column(name = "status", nullable = false, length = 20) private String status = "OPEN";

    @Column(name = "detected_at", nullable = false) private Instant detectedAt = Instant.now();
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "resolved_by") private UUID resolvedBy;
    @Column(name = "resolved_by_name") private String resolvedByName;
    @Column(name = "resolution_notes", columnDefinition = "TEXT") private String resolutionNotes;

    // A15: a person's assessment of the risk, who owns the fix, what management said, and the check after closing.
    @Column(name = "risk_rating", length = 10) private String riskRating;
    @Column(name = "risk_rationale", columnDefinition = "TEXT") private String riskRationale;
    @Column(name = "rated_by") private UUID ratedBy;
    @Column(name = "rated_by_name") private String ratedByName;
    @Column(name = "rated_at") private Instant ratedAt;
    @Column(name = "owner_id") private UUID ownerId;
    @Column(name = "owner_name") private String ownerName;
    @Column(name = "target_date") private java.time.LocalDate targetDate;
    @Column(name = "response_text", columnDefinition = "TEXT") private String responseText;
    @Column(name = "response_by") private UUID responseBy;
    @Column(name = "response_by_name") private String responseByName;
    @Column(name = "response_at") private Instant responseAt;
    @Column(name = "follow_up_date") private java.time.LocalDate followUpDate;
    @Column(name = "follow_up_at") private Instant followUpAt;
    @Column(name = "follow_up_by") private UUID followUpBy;
    @Column(name = "follow_up_by_name") private String followUpByName;
    @Column(name = "follow_up_outcome", length = 10) private String followUpOutcome;
    @Column(name = "follow_up_note", columnDefinition = "TEXT") private String followUpNote;
    @Column(name = "times_reopened", nullable = false) private int timesReopened = 0;

    public static final int MIN_TEXT = 10;
    public static final java.util.Set<String> RATINGS = java.util.Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

    public static ControlException raise(UUID tenantId, String sourceModule, String controlType,
                                         String relatedEntityType, UUID relatedEntityId,
                                         String severity, String description) {
        ControlException e = new ControlException();
        e.tenantId = tenantId;
        e.sourceModule = sourceModule;
        e.controlType = controlType;
        e.relatedEntityType = relatedEntityType;
        e.relatedEntityId = relatedEntityId;
        e.severity = severity != null ? severity : "WARNING";
        e.description = description;
        return e;
    }

    private static String text(String t, String what) {
        if (t == null || t.trim().length() < MIN_TEXT) throw new IllegalArgumentException(what + " needs at least " + MIN_TEXT + " characters");
        return t.trim();
    }

    private void requireOpen() {
        if (!"OPEN".equals(status)) throw new IllegalStateException("This exception is " + status.toLowerCase() + "; only an open one can be changed");
    }

    /** Serious exceptions get stricter handling when they are closed. */
    public boolean isSerious() { return "HIGH".equals(riskRating) || "CRITICAL".equals(riskRating); }

    /** A person's judgement of the risk. This is separate from {@code severity}, which the raising module sets. */
    public void rate(UUID by, String byName, String rating, String rationale) {
        requireOpen();
        String r = rating == null ? "" : rating.toUpperCase();
        if (!RATINGS.contains(r)) throw new IllegalArgumentException("Risk rating must be LOW, MEDIUM, HIGH or CRITICAL");
        this.riskRating = r; this.riskRationale = text(rationale, "The reason for the rating");
        this.ratedBy = by; this.ratedByName = byName; this.ratedAt = Instant.now(); touch();
    }

    public void assign(UUID ownerId, String ownerName, java.time.LocalDate targetDate) {
        requireOpen();
        if (ownerId == null) throw new IllegalArgumentException("Choose who owns this");
        if (targetDate == null) throw new IllegalArgumentException("Set a date to have it dealt with by");
        this.ownerId = ownerId; this.ownerName = ownerName; this.targetDate = targetDate; touch();
    }

    /** What management says it is doing about it. May be updated while the exception is open. */
    public void respond(UUID by, String byName, String response) {
        requireOpen();
        this.responseText = text(response, "The management response");
        this.responseBy = by; this.responseByName = byName; this.responseAt = Instant.now(); touch();
    }

    /**
     * Closing it by hand. It must be rated and have notes. A HIGH or CRITICAL one also needs a management response, someone other than the
     * person who gave that response to close it, and a follow-up date; the caller checks that evidence exists.
     */
    public void resolve(UUID resolvedBy, String resolvedByName, String resolutionNotes, java.time.LocalDate followUpOn, java.time.LocalDate today) {
        requireOpen();
        if (riskRating == null) throw new IllegalStateException("Rate the risk before resolving this");
        String notes = text(resolutionNotes, "The resolution notes");
        if (isSerious()) {
            if (responseText == null) throw new IllegalStateException("A " + riskRating.toLowerCase() + "-risk exception needs a management response before it can be resolved");
            if (resolvedBy != null && resolvedBy.equals(responseBy)) throw new IllegalStateException("The person who gave the management response cannot also resolve it");
            if (followUpOn == null) throw new IllegalStateException("Set a follow-up date to check that the fix has held");
        }
        if (followUpOn != null && !followUpOn.isAfter(today)) throw new IllegalArgumentException("The follow-up date must be in the future");
        this.status = "RESOLVED";
        this.resolvedBy = resolvedBy; this.resolvedByName = resolvedByName; this.resolutionNotes = notes; this.resolvedAt = Instant.now();
        this.followUpDate = followUpOn; this.followUpAt = null; this.followUpOutcome = null; this.followUpNote = null; this.followUpBy = null; this.followUpByName = null;
        touch();
    }

    /** Closing it because the underlying thing was put right elsewhere (another module resolved the cause). No rules apply: nobody decided anything. */
    public void resolveBySystem(UUID resolvedBy, String resolvedByName, String resolutionNotes) {
        if ("RESOLVED".equals(this.status)) throw new IllegalStateException("This exception is already resolved");
        this.status = "RESOLVED";
        this.resolvedBy = resolvedBy; this.resolvedByName = resolvedByName; this.resolutionNotes = resolutionNotes; this.resolvedAt = Instant.now();
        touch();
    }

    /** The check after closing. HOLDING confirms the fix; RECURRED reopens the exception. */
    public void completeFollowUp(UUID by, String byName, String outcome, String note) {
        if (!"RESOLVED".equals(status) || followUpDate == null || followUpAt != null) throw new IllegalStateException("There is no follow-up waiting on this exception");
        String o = outcome == null ? "" : outcome.toUpperCase();
        if (!java.util.Set.of("HOLDING", "RECURRED").contains(o)) throw new IllegalArgumentException("Outcome must be HOLDING or RECURRED");
        this.followUpOutcome = o; this.followUpNote = text(note, "The follow-up note");
        this.followUpBy = by; this.followUpByName = byName; this.followUpAt = Instant.now();
        if ("RECURRED".equals(o)) {
            this.status = "OPEN"; this.timesReopened++;
            this.resolvedAt = null; this.resolvedBy = null; this.resolvedByName = null;
        }
        touch();
    }

    public boolean followUpDueOn(java.time.LocalDate today) {
        return "RESOLVED".equals(status) && followUpDate != null && followUpAt == null && !followUpDate.isAfter(today);
    }

    public boolean overdueOn(java.time.LocalDate today) { return "OPEN".equals(status) && targetDate != null && targetDate.isBefore(today); }

    private void touch() { this.updatedAt = Instant.now(); }

    public void dismiss(UUID dismissedBy, String dismissedByName, String reason) {
        this.status = "DISMISSED";
        this.resolvedBy = dismissedBy;
        this.resolvedByName = dismissedByName;
        this.resolutionNotes = reason;
        this.resolvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}