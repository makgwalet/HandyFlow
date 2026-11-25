package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Work given to an outside provider for a client. Never deleted: completed with a rating, or cancelled with a reason. */
@Entity(name = "ComplianceProviderAssignment")
@Table(name = "compliance_provider_assignments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceProviderAssignment {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "provider_id", nullable = false)
    private UUID providerId;
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    @Column(nullable = false)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "service_code")
    private String serviceCode;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(nullable = false)
    private String status = ProviderWorkRules.PROPOSED;
    @Column(name = "accreditation_required", nullable = false)
    private boolean accreditationRequired = true;
    @Column(name = "waiver_note", columnDefinition = "TEXT")
    private String waiverNote;
    @Column(name = "conflict_status", nullable = false)
    private String conflictStatus = ProviderWorkRules.PENDING;
    @Column(name = "conflict_note", columnDefinition = "TEXT")
    private String conflictNote;
    @Column(name = "conflict_by")
    private UUID conflictBy;
    @Column(name = "conflict_at")
    private Instant conflictAt;
    @Column(name = "conflict_approved_by")
    private UUID conflictApprovedBy;
    @Column(name = "conflict_approved_at")
    private Instant conflictApprovedAt;
    @Column(name = "conflict_approval_note", columnDefinition = "TEXT")
    private String conflictApprovalNote;
    @Column(name = "activated_by")
    private UUID activatedBy;
    @Column(name = "activated_at")
    private Instant activatedAt;
    @Column(name = "completed_on")
    private LocalDate completedOn;
    @Column(name = "completed_by")
    private UUID completedBy;
    private Integer rating;
    @Column(name = "completion_note", columnDefinition = "TEXT")
    private String completionNote;
    @Column(name = "on_time")
    private Boolean onTime;
    @Column(name = "cancelled_by")
    private UUID cancelledBy;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    @Column(name = "cancel_note", columnDefinition = "TEXT")
    private String cancelNote;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static ComplianceProviderAssignment propose(TenantId tenantId, UUID providerId, UUID clientId, String title, String description, String serviceCode, LocalDate dueDate,
                                                       boolean accreditationRequired, String waiverNote, LocalDate today, UUID by) {
        if (by == null) throw new IllegalArgumentException("Someone must record this");
        ComplianceProviderAssignment a = new ComplianceProviderAssignment();
        a.tenantId = tenantId;
        a.providerId = providerId;
        a.clientId = clientId;
        a.title = ProviderRules.required(title, "What the work is", 200);
        a.description = ProviderRules.optional(description, "The description", 4000);
        a.serviceCode = ProviderRules.optional(serviceCode, "The service code", 60);
        a.dueDate = ProviderWorkRules.dueDate(dueDate, today);
        a.accreditationRequired = accreditationRequired;
        a.waiverNote = accreditationRequired ? null : ProviderRules.note(waiverNote);
        a.createdBy = by;
        a.createdAt = Instant.now();
        return a;
    }

    /** The provider's declaration, recorded by staff: no conflict, or a conflict that someone else must approve. Can be re-recorded until work starts. */
    public void declare(boolean conflict, String note, UUID by) {
        if (!ProviderWorkRules.PROPOSED.equals(status)) throw new IllegalStateException("The declaration can only be recorded before work starts");
        if (by == null) throw new IllegalArgumentException("Someone must record this");
        this.conflictNote = ProviderRules.note(note);
        this.conflictStatus = conflict ? ProviderWorkRules.DECLARED : ProviderWorkRules.NONE;
        this.conflictBy = by;
        this.conflictAt = Instant.now();
        this.conflictApprovedBy = null;
        this.conflictApprovedAt = null;
        this.conflictApprovalNote = null;
    }

    public void approveConflict(UUID by, String note) {
        if (!ProviderWorkRules.PROPOSED.equals(status)) throw new IllegalStateException("The conflict can only be approved before work starts");
        if (!ProviderWorkRules.DECLARED.equals(conflictStatus)) throw new IllegalStateException("No conflict has been declared, so there is nothing to approve");
        if (by == null) throw new IllegalArgumentException("Someone must record this");
        if (by.equals(conflictBy)) throw new IllegalStateException("Someone other than the person who recorded the declaration must approve it");
        this.conflictApprovalNote = ProviderRules.note(note);
        this.conflictApprovedBy = by;
        this.conflictApprovedAt = Instant.now();
    }

    public boolean isConflictApproved() { return conflictApprovedBy != null; }

    public void activate(UUID by, boolean providerActive, boolean hasUsableAccreditation) {
        String problem = ProviderWorkRules.activationProblem(status, conflictStatus, isConflictApproved(), providerActive, accreditationRequired, hasUsableAccreditation);
        if (problem != null) throw new IllegalStateException(problem);
        if (by == null) throw new IllegalArgumentException("Someone must record this");
        this.status = ProviderWorkRules.ACTIVE;
        this.activatedBy = by;
        this.activatedAt = Instant.now();
    }

    public void complete(UUID by, int rating, String note, boolean deliverablesDone, LocalDate today) {
        if (!ProviderWorkRules.ACTIVE.equals(status)) throw new IllegalStateException("Only work that has started can be completed");
        if (!deliverablesDone) throw new IllegalStateException("Every deliverable must be accepted or waived first");
        if (by == null) throw new IllegalArgumentException("Someone must record this");
        ProviderWorkRules.checkRating(rating);
        this.completionNote = ProviderRules.note(note);
        this.rating = rating;
        this.completedOn = today;
        this.completedBy = by;
        this.onTime = ProviderWorkRules.onTime(dueDate, today);
        this.status = ProviderWorkRules.COMPLETED;
    }

    public void cancel(UUID by, String note) {
        if (!ProviderWorkRules.PROPOSED.equals(status) && !ProviderWorkRules.ACTIVE.equals(status)) throw new IllegalStateException("This assignment is already " + status.toLowerCase());
        if (by == null) throw new IllegalArgumentException("Someone must record this");
        this.cancelNote = ProviderRules.note(note);
        this.cancelledBy = by;
        this.cancelledAt = Instant.now();
        this.status = ProviderWorkRules.CANCELLED;
    }

    public boolean isOpen() { return ProviderWorkRules.PROPOSED.equals(status) || ProviderWorkRules.ACTIVE.equals(status); }
}
