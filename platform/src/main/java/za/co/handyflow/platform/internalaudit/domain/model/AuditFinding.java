package za.co.handyflow.platform.internalaudit.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Deliberately richer than AuditException — see this class's own
 * migration comment (V276) for the fuller reasoning on why
 * ControlException's shape was confirmed insufficient for a real
 * finding on its own, back in the original design proposal. severity
 * is its own field, same hard constraint as AuditException — never
 * derived from the engagement's risk level or the sampling plan's
 * materiality.
 */
@Entity
@Table(name = "audit_findings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditFinding {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "engagement_id", nullable = false) private UUID engagementId;
    @Column(name = "source_exception_id") private UUID sourceExceptionId;
    @Column(name = "title", nullable = false) private String title;
    @Column(name = "description", nullable = false) private String description;
    @Column(name = "root_cause") private String rootCause;
    @Column(name = "recommendation") private String recommendation;
    @Column(name = "management_response") private String managementResponse;
    @Column(name = "severity", nullable = false) private String severity;
    @Column(name = "owner") private UUID owner;
    @Column(name = "due_date") private LocalDate dueDate;
    @Column(name = "status", nullable = false) private String status = "OPEN"; // OPEN | IN_PROGRESS | RESOLVED | CLOSED
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "resolved_at") private Instant resolvedAt;

    // FIX: closes a real gap found while building external-sharing
    // governance — this entity had no dedicated closedAt at all
    // (close() only ever changed status, reusing resolve()'s own
    // resolvedAt as if it meant the same thing). Needed once reopen()
    // has to preserve "this was closed once, here's when" rather than
    // silently discarding it.
    @Column(name = "closed_at") private Instant closedAt;

    // FIX: closes a real gap in reopen() — it previously just cleared
    // resolvedAt to null with no trace a closure ever happened.
    // previouslyClosedAt is set once, from closedAt, the first time a
    // CLOSED finding is reopened, and is never overwritten again after
    // that — it answers "was this ever closed before, and when," not
    // "when was it most recently closed," which reopenedAt already
    // covers.
    @Column(name = "previously_closed_at") private Instant previouslyClosedAt;
    @Column(name = "reopened_at") private Instant reopenedAt;
    @Column(name = "reopen_reason") private String reopenReason;

    // FIX: closes the confirmed "no internal-audit/audit-analytics
    // engine reachable by an external auditor" gap — per the agreed
    // design: finding status (this class's own status field above) and
    // external visibility are deliberately two separate concepts, never
    // conflated. CLOSED is a precondition for sharing, checked in
    // share() below, but is not itself the security mechanism — a
    // CLOSED finding with externalVisibility still INTERNAL_ONLY (the
    // default) stays exactly as invisible to the auditor portal as an
    // OPEN one. WITHDRAWN is deliberately distinct from reverting to
    // INTERNAL_ONLY — see withdraw()'s own comment.
    @Column(name = "external_visibility", nullable = false) private String externalVisibility = "INTERNAL_ONLY"; // INTERNAL_ONLY | SHARED | WITHDRAWN
    @Column(name = "shared_at") private Instant sharedAt;
    @Column(name = "shared_by") private UUID sharedBy;
    @Column(name = "sharing_reason") private String sharingReason;

    public static AuditFinding create(UUID tenantId, UUID engagementId, UUID sourceExceptionId,
                                      String title, String description, String rootCause, String recommendation,
                                      String severity, UUID owner, LocalDate dueDate, UUID createdBy) {
        AuditFinding f = new AuditFinding();
        f.tenantId = tenantId;
        f.engagementId = engagementId;
        f.sourceExceptionId = sourceExceptionId;
        f.title = title;
        f.description = description;
        f.rootCause = rootCause;
        f.recommendation = recommendation;
        f.severity = severity;
        f.owner = owner;
        f.dueDate = dueDate;
        f.createdBy = createdBy;
        f.createdAt = Instant.now();
        return f;
    }

    public void recordManagementResponse(String response) {
        this.managementResponse = response;
        if ("OPEN".equals(this.status)) {
            this.status = "IN_PROGRESS";
        }
    }

    public void resolve() {
        this.status = "RESOLVED";
        this.resolvedAt = Instant.now();
    }

    public void close() {
        if (!"RESOLVED".equals(status)) {
            throw new IllegalStateException("Only a RESOLVED finding can be closed. Current status: " + status);
        }
        this.status = "CLOSED";
        this.closedAt = Instant.now();
    }

    // FIX: previously discarded closedAt with no trace. Now: the first
    // time a CLOSED finding is reopened, its closedAt is preserved into
    // previouslyClosedAt (never overwritten on any later reopen — it's
    // "was this ever closed," not "most recently"); reopenedAt/
    // reopenReason record THIS reopening specifically. Also auto-
    // withdraws external sharing — a reopened finding is, by
    // definition, no longer the finalized thing an auditor was shown;
    // matching share()'s own precondition, it has to be explicitly
    // re-shared once re-closed, not left visible on the strength of a
    // decision made about a version of the finding that no longer
    // reflects where things stand.
    public void reopen(String reason) {
        if (this.closedAt != null && this.previouslyClosedAt == null) {
            this.previouslyClosedAt = this.closedAt;
        }
        this.status = "IN_PROGRESS";
        this.resolvedAt = null;
        this.closedAt = null;
        this.reopenedAt = Instant.now();
        this.reopenReason = reason;
        if ("SHARED".equals(this.externalVisibility)) {
            this.externalVisibility = "WITHDRAWN";
        }
    }

    // ── External sharing governance ─────────────────────────────────────────
    // FIX: closes the confirmed "no internal-audit engine reachable by
    // an external auditor" gap. CLOSED is a necessary precondition
    // (checked here, at the domain level, not just in the service —
    // this invariant should hold no matter what calls it), but sharing
    // itself is always a separate, explicit act by whoever holds
    // authority to decide it (checked one level up, in
    // InternalAuditService, against EngagementAssignment — this entity
    // has no notion of "who is allowed," only "what state is this in").

    public void share(UUID sharedBy, String reason) {
        if (!"CLOSED".equals(status)) {
            throw new IllegalStateException(
                    "Only a CLOSED finding can be shared externally. Current status: " + status);
        }
        this.externalVisibility = "SHARED";
        this.sharedAt = Instant.now();
        this.sharedBy = sharedBy;
        this.sharingReason = reason;
    }

    // Deliberately a distinct state from reverting to INTERNAL_ONLY —
    // WITHDRAWN preserves the fact that this finding WAS shown to the
    // external auditor at some point (sharedAt/sharedBy/sharingReason
    // are left untouched, not cleared), which matters if the auditor
    // later asks "wasn't this shared with us before?" INTERNAL_ONLY is
    // reserved for a finding that was never shared at all.
    public void withdraw() {
        if (!"SHARED".equals(externalVisibility)) {
            throw new IllegalStateException(
                    "Only a currently-SHARED finding can be withdrawn. Current visibility: " + externalVisibility);
        }
        this.externalVisibility = "WITHDRAWN";
    }
}
