package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Something monitoring found that a person should look at. The system opens alerts; only a person can close one (dismiss or escalate), must say
 * why, and cannot be someone the alert is about. A closed alert stays closed: the same pattern is not filed again.
 */
@Entity(name = "AuditMonitorAlert")
@Table(name = "audit_monitor_alerts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditMonitorAlert {
    public static final int MIN_NOTE = 10;

    @Id private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "rule", nullable = false) private String rule;
    @Column(name = "severity", nullable = false) private String severity;
    @Column(name = "subject_type", nullable = false) private String subjectType;
    @Column(name = "subject_ids", columnDefinition = "TEXT", nullable = false) private String subjectIds;
    @Column(name = "involved_users", columnDefinition = "TEXT") private String involvedUsers;
    @Column(name = "fingerprint", nullable = false) private String fingerprint;
    @Column(name = "summary", nullable = false) private String summary;
    @Column(name = "detail", columnDefinition = "TEXT") private String detail;
    @Column(name = "amount") private BigDecimal amount;
    @Column(name = "occurred_on") private LocalDate occurredOn;
    @Column(name = "status", nullable = false) private String status = "NEW";
    @Column(name = "first_seen", nullable = false) private Instant firstSeen = Instant.now();
    @Column(name = "last_seen", nullable = false) private Instant lastSeen = Instant.now();
    @Column(name = "reviewed_by") private UUID reviewedBy;
    @Column(name = "reviewed_by_name") private String reviewedByName;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "disposition", columnDefinition = "TEXT") private String disposition;
    @Column(name = "finding_id") private UUID findingId;

    public static AuditMonitorAlert open(UUID tenantId, String rule, String severity, String subjectType, String subjectIds, String involvedUsers,
                                         String fingerprint, String summary, String detail, BigDecimal amount, LocalDate occurredOn) {
        AuditMonitorAlert a = new AuditMonitorAlert();
        a.tenantId = tenantId; a.rule = rule; a.severity = severity; a.subjectType = subjectType; a.subjectIds = subjectIds;
        a.involvedUsers = involvedUsers == null || involvedUsers.isBlank() ? null : involvedUsers; a.fingerprint = fingerprint;
        a.summary = summary.length() > 500 ? summary.substring(0, 500) : summary; a.detail = detail; a.amount = amount; a.occurredOn = occurredOn;
        return a;
    }

    public boolean isClosed() { return "DISMISSED".equals(status) || "ESCALATED".equals(status); }
    public void seenAgain() { this.lastSeen = Instant.now(); }

    /** Is this person one the alert is about? They may not review it. */
    public boolean involves(UUID userId) {
        return userId != null && involvedUsers != null && java.util.Arrays.asList(involvedUsers.split(",")).contains(userId.toString());
    }

    /** IN_REVIEW: take it up. DISMISSED: say why it is acceptable. ESCALATED: say why it needs action; a finding may be linked. */
    public void review(String newStatus, String note, UUID findingId, UUID by, String byName) {
        String st = newStatus == null ? "" : newStatus.toUpperCase();
        if (!java.util.Set.of("IN_REVIEW", "DISMISSED", "ESCALATED").contains(st)) throw new IllegalArgumentException("Status must be IN_REVIEW, DISMISSED or ESCALATED");
        if (isClosed()) throw new IllegalStateException("This alert is already closed");
        if (involves(by)) throw new IllegalStateException("You are one of the people this alert is about, so someone else must review it");
        String n = note == null ? null : note.trim();
        if (!"IN_REVIEW".equals(st) && (n == null || n.length() < MIN_NOTE)) throw new IllegalArgumentException("Say why (at least " + MIN_NOTE + " characters)");
        if (findingId != null && !"ESCALATED".equals(st)) throw new IllegalArgumentException("Only an escalated alert can be linked to a finding");
        this.status = st; this.reviewedBy = by; this.reviewedByName = byName; this.reviewedAt = Instant.now();
        this.disposition = n == null || n.isEmpty() ? this.disposition : n;
        if (findingId != null) this.findingId = findingId;
    }
}
