package za.co.handyflow.platform.auditor.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Something an external auditor wants explained (a QUERY) or wants on record (a REVIEW_NOTE). The business answers; the auditor accepts
 * the answer (RESOLVED) or follows up (back to OPEN). The auditor can withdraw it at any time. It is not an internal finding; a
 * business can link one to a finding, which records the connection and nothing more.
 */
@Entity(name = "AuditQuery")
@Table(name = "audit_queries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditQuery {
    public static final int MIN_TEXT = 10;
    public static final Set<String> SUBJECTS = Set.of("JOURNAL_ENTRY", "INVOICE", "BILL", "PAYMENT", "EVIDENCE", "EVIDENCE_REQUEST", "ACCOUNT", "OTHER");

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id") private UUID engagementId;
    @Column(name = "kind", nullable = false) private String kind;
    @Column(name = "reference", nullable = false) private String reference;
    @Column(name = "title", nullable = false) private String title;
    @Column(name = "subject_type") private String subjectType;
    @Column(name = "subject_id") private UUID subjectId;
    @Column(name = "subject_label") private String subjectLabel;
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(name = "raised_by", nullable = false) private UUID raisedBy;
    @Column(name = "raised_by_label") private String raisedByLabel;
    @Column(name = "status", nullable = false) private String status = "OPEN";
    @Column(name = "times_reopened", nullable = false) private int timesReopened = 0;
    @Column(name = "linked_finding_id") private UUID linkedFindingId;
    @Column(name = "linked_by") private UUID linkedBy;
    @Column(name = "linked_at") private Instant linkedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    @Column(name = "closed_at") private Instant closedAt;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "query_id", insertable = false, updatable = false)
    @OrderBy("createdAt ASC")
    private List<AuditQueryMessage> messages = new ArrayList<>();

    private static String text(String t, String what) {
        if (t == null || t.trim().length() < MIN_TEXT) throw new IllegalArgumentException(what + " needs at least " + MIN_TEXT + " characters");
        return t.trim();
    }

    /** Raised by an auditor. The first message is the question itself. */
    public static AuditQuery raise(UUID tenantId, UUID engagementId, String kind, String reference, String title, String text,
                                   String subjectType, UUID subjectId, String subjectLabel, LocalDate dueDate, UUID by, String byLabel) {
        if (!Set.of("QUERY", "REVIEW_NOTE").contains(kind)) throw new IllegalArgumentException("Unknown kind " + kind);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        String st = subjectType == null || subjectType.isBlank() ? null : subjectType.toUpperCase();
        if (st != null && !SUBJECTS.contains(st)) throw new IllegalArgumentException("Unknown subject type " + subjectType);
        AuditQuery q = new AuditQuery();
        q.tenantId = tenantId; q.engagementId = engagementId; q.kind = kind; q.reference = reference; q.title = title.trim();
        q.subjectType = st; q.subjectId = subjectId; q.subjectLabel = subjectLabel == null || subjectLabel.isBlank() ? null : subjectLabel.trim();
        q.dueDate = dueDate; q.raisedBy = by; q.raisedByLabel = byLabel;
        q.messages.add(AuditQueryMessage.of(tenantId, q.id, "AUDITOR", by, byLabel, "QUESTION", text(text, "The question")));
        return q;
    }

    private void touch() { this.updatedAt = Instant.now(); }

    public boolean isFinished() { return "RESOLVED".equals(status) || "WITHDRAWN".equals(status); }

    private void requireNotFinished() {
        if (isFinished()) throw new IllegalStateException("This " + label() + " is finished: " + status);
    }

    private String label() { return "QUERY".equals(kind) ? "query" : "review note"; }

    private void add(String authorKind, UUID by, String byLabel, String messageKind, String body) {
        messages.add(AuditQueryMessage.of(tenantId, id, authorKind, by, byLabel, messageKind, body));
        touch();
    }

    /** The business answers. */
    public void answer(UUID by, String byLabel, String text) {
        if (!"OPEN".equals(status)) throw new IllegalStateException("Only an open " + label() + " can be answered. Current status: " + status);
        add("STAFF", by, byLabel, "ANSWER", text(text, "An answer"));
        this.status = "ANSWERED";
    }

    /** The auditor is not satisfied and asks more. */
    public void followUp(UUID by, String byLabel, String text) {
        if (!"ANSWERED".equals(status)) throw new IllegalStateException("There is no answer to follow up. Current status: " + status);
        add("AUDITOR", by, byLabel, "FOLLOW_UP", text(text, "A follow-up"));
        this.status = "OPEN"; this.timesReopened++;
    }

    /** The auditor accepts the answer. */
    public void resolve(UUID by, String byLabel, String note) {
        if (!"ANSWERED".equals(status)) throw new IllegalStateException("Only an answered " + label() + " can be resolved. Current status: " + status);
        add("AUDITOR", by, byLabel, "RESOLUTION", note == null || note.isBlank() ? "Accepted" : note.trim());
        this.status = "RESOLVED"; this.closedAt = Instant.now();
    }

    /** The auditor drops it. */
    public void withdraw(UUID by, String byLabel, String why) {
        requireNotFinished();
        add("AUDITOR", by, byLabel, "WITHDRAWAL", text(why, "The reason"));
        this.status = "WITHDRAWN"; this.closedAt = Instant.now();
    }

    /** Record that the business connects this to an internal finding. Nothing else changes. */
    public void linkToFinding(UUID findingId, UUID by) {
        if (findingId == null) throw new IllegalArgumentException("Choose a finding to link to");
        this.linkedFindingId = findingId; this.linkedBy = by; this.linkedAt = Instant.now(); touch();
    }

    public void unlinkFinding() { this.linkedFindingId = null; this.linkedBy = null; this.linkedAt = null; touch(); }

    public boolean overdueOn(LocalDate today) { return "OPEN".equals(status) && dueDate != null && dueDate.isBefore(today); }
}
