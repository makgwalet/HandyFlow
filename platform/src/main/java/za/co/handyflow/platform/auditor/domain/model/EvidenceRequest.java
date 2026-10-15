package za.co.handyflow.platform.auditor.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * A request for evidence between the business and an external auditor. The business answers (files, or "not available"); the auditor
 * approves or sends it back. Each answer is a round and stays on record. Status: OPEN -> SUBMITTED | NOT_AVAILABLE -> APPROVED |
 * CLOSED_NOT_AVAILABLE, or back to OPEN when the auditor sends it back. CANCELLED ends it.
 */
@Entity(name = "EvidenceRequest")
@Table(name = "audit_evidence_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvidenceRequest {
    public static final int MIN_REASON = 10;
    public static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH");

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id") private UUID engagementId;
    @Column(name = "reference", nullable = false) private String reference;
    @Column(name = "title", nullable = false) private String title;
    @Column(name = "description", columnDefinition = "TEXT") private String description;
    @Column(name = "priority", nullable = false) private String priority = "MEDIUM";
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(name = "assigned_to") private UUID assignedTo;
    @Column(name = "requested_by_kind", nullable = false) private String requestedByKind;
    @Column(name = "requested_by", nullable = false) private UUID requestedBy;
    @Column(name = "requested_by_label") private String requestedByLabel;
    @Column(name = "status", nullable = false) private String status = "OPEN";
    @Column(name = "times_rejected", nullable = false) private int timesRejected = 0;
    @Column(name = "cancel_reason", columnDefinition = "TEXT") private String cancelReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    @Column(name = "closed_at") private Instant closedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "request_id", insertable = false, updatable = false)
    @OrderBy("roundNo ASC")
    private List<EvidenceRequestRound> rounds = new ArrayList<>();

    private static String reason(String t, String what) {
        if (t == null || t.trim().length() < MIN_REASON) throw new IllegalArgumentException(what + " needs at least " + MIN_REASON + " characters");
        return t.trim();
    }

    public static EvidenceRequest create(UUID tenantId, UUID engagementId, String reference, String title, String description, String priority,
                                         LocalDate dueDate, UUID assignedTo, String byKind, UUID by, String byLabel) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        String p = priority == null ? "MEDIUM" : priority.toUpperCase();
        if (!PRIORITIES.contains(p)) throw new IllegalArgumentException("Priority must be LOW, MEDIUM or HIGH");
        if (!Set.of("STAFF", "AUDITOR").contains(byKind)) throw new IllegalArgumentException("Unknown requester");
        EvidenceRequest r = new EvidenceRequest();
        r.tenantId = tenantId; r.engagementId = engagementId; r.reference = reference; r.title = title.trim();
        r.description = description == null || description.isBlank() ? null : description.trim();
        r.priority = p; r.dueDate = dueDate; r.assignedTo = assignedTo;
        r.requestedByKind = byKind; r.requestedBy = by; r.requestedByLabel = byLabel;
        return r;
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public boolean isFinished() { return Set.of("APPROVED", "CLOSED_NOT_AVAILABLE", "CANCELLED").contains(status); }

    public void assign(UUID userId) {
        if (isFinished()) throw new IllegalStateException("This request is finished: " + status);
        this.assignedTo = userId; touch();
    }

    public void changeDueDate(LocalDate d) {
        if (isFinished()) throw new IllegalStateException("This request is finished: " + status);
        this.dueDate = d; touch();
    }

    private int nextRound() { return rounds.size() + 1; }

    /** The business sends files. Only files not already sent in an earlier round count. */
    public EvidenceRequestRound submit(UUID by, String note, Set<UUID> newEvidenceIds) {
        if (!"OPEN".equals(status)) throw new IllegalStateException("Files can be sent only while the request is open. Current status: " + status);
        if (newEvidenceIds == null || newEvidenceIds.isEmpty()) throw new IllegalStateException("Attach at least one new file before sending");
        EvidenceRequestRound r = EvidenceRequestRound.of(tenantId, id, nextRound(), EvidenceRequestRound.SUBMISSION,
                note == null || note.isBlank() ? "Sent" : note.trim(), by, newEvidenceIds);
        rounds.add(r); this.status = "SUBMITTED"; touch();
        return r;
    }

    /** The business says it cannot provide the item, and why. */
    public EvidenceRequestRound markNotAvailable(UUID by, String why) {
        if (!"OPEN".equals(status)) throw new IllegalStateException("Only an open request can be marked not available. Current status: " + status);
        EvidenceRequestRound r = EvidenceRequestRound.of(tenantId, id, nextRound(), EvidenceRequestRound.NOT_AVAILABLE,
                reason(why, "The reason"), by, Set.of());
        rounds.add(r); this.status = "NOT_AVAILABLE"; touch();
        return r;
    }

    private EvidenceRequestRound pending() {
        if (!"SUBMITTED".equals(status) && !"NOT_AVAILABLE".equals(status)) {
            throw new IllegalStateException("Nothing is waiting for a decision. Current status: " + status);
        }
        return rounds.get(rounds.size() - 1);
    }

    /** The auditor accepts the files, or accepts that the item is not available. */
    public void approve(UUID auditor, String note) {
        EvidenceRequestRound r = pending();
        boolean na = "NOT_AVAILABLE".equals(status);
        r.decide("APPROVED", note == null || note.isBlank() ? null : note.trim(), auditor);
        this.status = na ? "CLOSED_NOT_AVAILABLE" : "APPROVED"; this.closedAt = Instant.now(); touch();
    }

    /** The auditor sends it back, saying what is wrong. The request is open again; earlier rounds stay on record. */
    public void reject(UUID auditor, String why) {
        EvidenceRequestRound r = pending();
        r.decide("REJECTED", reason(why, "The reason for sending it back"), auditor);
        this.status = "OPEN"; this.timesRejected++; touch();
    }

    public void cancel(String why) {
        if (isFinished()) throw new IllegalStateException("This request is already finished: " + status);
        this.cancelReason = reason(why, "The reason for cancelling");
        this.status = "CANCELLED"; this.closedAt = Instant.now(); touch();
    }

    public boolean overdueOn(LocalDate today) { return "OPEN".equals(status) && dueDate != null && dueDate.isBefore(today); }
    public boolean dueSoonOn(LocalDate today) { return "OPEN".equals(status) && dueDate != null && !dueDate.isBefore(today) && !dueDate.isAfter(today.plusDays(3)); }

    /** Every file sent in any round so far. */
    public Set<UUID> allSentEvidence() {
        Set<UUID> all = new LinkedHashSet<>();
        for (EvidenceRequestRound r : rounds) all.addAll(r.getEvidenceIds());
        return all;
    }
}
