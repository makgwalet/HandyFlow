package za.co.handyflow.platform.compliancetender.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Feeds the expiry engine / compliance calendar. Deliberately separate
 * from {@link ComplianceRegistration}'s own {@code expiryDate}: a
 * registration has at most one expiry, but a tenant can have other
 * compliance deadlines that aren't tied to any one registration (e.g. an
 * annual return filing date, a recurring declaration) — folding both
 * concepts into one field on the registration would force a choice
 * between under-modelling recurring deadlines or over-modelling a simple
 * registration with a single expiry date.
 */
@Entity
@Table(name = "compliance_deadlines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceDeadline {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "registration_id")
    private UUID registrationId; // nullable

    @Column(name = "deadline_type", nullable = false)
    private String deadlineType; // e.g. "RENEWAL", "ANNUAL_RETURN", "DECLARATION"

    @Column(length = 500)
    private String description;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, DONE, MISSED

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public static ComplianceDeadline create(TenantId tenantId, UUID registrationId, String deadlineType,
                                            String description, LocalDate dueDate, UUID createdBy) {
        ComplianceDeadline d = new ComplianceDeadline();
        d.tenantId = tenantId;
        d.registrationId = registrationId;
        d.deadlineType = deadlineType;
        d.description = description;
        d.dueDate = dueDate;
        d.createdAt = Instant.now();
        d.createdBy = createdBy;
        d.updatedAt = Instant.now();
        d.updatedBy = createdBy;
        return d;
    }

    public void markDone(UUID completedBy) {
        if ("DONE".equals(status)) throw new IllegalStateException("Deadline is already marked done");
        this.status = "DONE";
        this.completedAt = Instant.now();
        this.updatedBy = completedBy;
        this.updatedAt = Instant.now();
    }

    /** Called by the daily notification sweep once a PENDING deadline's due date has passed. */
    public void markMissed() {
        if ("PENDING".equals(status)) {
            this.status = "MISSED";
            this.updatedAt = Instant.now();
        }
    }

    public boolean isOverdue() { return "PENDING".equals(status) && dueDate.isBefore(LocalDate.now()); }

    public boolean isDueWithin(int days) {
        if (!"PENDING".equals(status)) return false;
        LocalDate cutoff = LocalDate.now().plusDays(days);
        return !dueDate.isBefore(LocalDate.now()) && !dueDate.isAfter(cutoff);
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
