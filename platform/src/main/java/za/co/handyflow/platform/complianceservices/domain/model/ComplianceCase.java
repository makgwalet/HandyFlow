package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A problem being worked on for a client. Every move goes through the rules in {@link CaseRules}. */
@Entity
@Table(name = "compliance_cases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceCase {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String status = "OPEN";

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "assessment_id")
    private UUID assessmentId;

    @Column(name = "obligation_id")
    private UUID obligationId;

    @Column(name = "obligation_code")
    private String obligationCode;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "closed_by")
    private UUID closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "close_exception_reason", columnDefinition = "TEXT")
    private String closeExceptionReason;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private Long version;

    public static ComplianceCase open(TenantId tenantId, UUID clientId, String title, String description, String severity, UUID ownerId, LocalDate dueDate,
                                      UUID assessmentId, UUID obligationId, String obligationCode, UUID by, LocalDate today) {
        CaseRules.dueDate(dueDate, today);
        ComplianceCase c = new ComplianceCase();
        c.tenantId = tenantId;
        c.clientId = clientId;
        c.title = CaseRules.title(title);
        c.description = description == null || description.isBlank() ? null : ClientProfileRules.clean(description, 4000);
        c.severity = CaseRules.severity(severity, "MEDIUM");
        c.ownerId = ownerId;
        c.dueDate = dueDate;
        c.assessmentId = assessmentId;
        c.obligationId = obligationId;
        c.obligationCode = obligationCode;
        c.createdBy = by;
        return c;
    }

    private void move(String to) {
        if (!CaseRules.canMove(status, to)) throw new IllegalStateException("A case that is " + status.toLowerCase().replace('_', ' ') + " cannot become " + to.toLowerCase().replace('_', ' '));
        this.status = to;
        this.updatedAt = Instant.now();
    }

    public void start() {
        if (ownerId == null) throw new IllegalArgumentException("Give the case an owner before starting work on it");
        move("IN_PROGRESS");
    }

    public void awaitClient() { move("AWAITING_CLIENT"); }

    public void resume() {
        if (!"AWAITING_CLIENT".equals(status)) throw new IllegalStateException("Only a case waiting on the client can be resumed");
        move("IN_PROGRESS");
    }

    public void resolve(UUID by, String note, int openSteps) {
        if (by == null) throw new IllegalArgumentException("Who is resolving this case?");
        var blockers = CaseRules.resolveBlockers(openSteps, note);
        if (!blockers.isEmpty()) throw new IllegalArgumentException(String.join(". ", blockers));
        move("RESOLVED");
        this.resolutionNote = ClientProfileRules.clean(note, 4000);
        this.resolvedBy = by;
        this.resolvedAt = Instant.now();
    }

    public void close(UUID by, String exceptionReason) {
        String exception = CaseRules.closeException(resolvedBy, by, exceptionReason);
        move("CLOSED");
        this.closedBy = by;
        this.closedAt = Instant.now();
        this.closeExceptionReason = exception;
    }

    /** The resolution was not right. Back to work, with the reason kept in the trail by the caller. */
    public void reopen(String reason) {
        CaseRules.reason(reason, "why it is reopened");
        move("IN_PROGRESS");
        this.resolutionNote = null;
        this.resolvedBy = null;
        this.resolvedAt = null;
    }

    public void cancel(String reason) {
        String r = CaseRules.reason(reason, "why it is cancelled");
        move("CANCELLED");
        this.cancelReason = r;
    }

    public void assign(UUID owner) {
        if (!CaseRules.isLive(status)) throw new IllegalStateException("A case that is " + status.toLowerCase().replace('_', ' ') + " cannot be reassigned");
        if (owner == null) throw new IllegalArgumentException("An owner is required");
        this.ownerId = owner;
        this.updatedAt = Instant.now();
    }

    public void reschedule(LocalDate due, LocalDate today) {
        if (!CaseRules.isLive(status)) throw new IllegalStateException("A case that is " + status.toLowerCase().replace('_', ' ') + " cannot be rescheduled");
        CaseRules.dueDate(due, today);
        this.dueDate = due;
        this.updatedAt = Instant.now();
    }

    public boolean isLive() { return CaseRules.isLive(status); }
}
