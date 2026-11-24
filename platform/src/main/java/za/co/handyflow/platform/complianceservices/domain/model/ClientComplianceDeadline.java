package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The client-scoped counterpart to compliancetender.ComplianceDeadline — same reasoning as ClientComplianceRegistration's own Javadoc for the parallel-table design. */
@Entity
@Table(name = "client_compliance_deadlines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientComplianceDeadline {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "registration_id")
    private UUID registrationId; // nullable

    @Column(name = "deadline_type", nullable = false)
    private String deadlineType;

    @Column(length = 500)
    private String description;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, DONE, MISSED

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "obligation_id")
    private UUID obligationId;

    @Column(name = "obligation_version_number")
    private Integer obligationVersionNumber;

    @Column(nullable = false)
    private boolean generated;

    @Column(name = "responsible_role")
    private String responsibleRole;

    @Column(name = "cancelled_reason", columnDefinition = "TEXT")
    private String cancelledReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public static ClientComplianceDeadline create(TenantId tenantId, UUID clientId, UUID registrationId,
                                                   String deadlineType, String description, LocalDate dueDate,
                                                   UUID createdBy) {
        ClientComplianceDeadline d = new ClientComplianceDeadline();
        d.tenantId = tenantId;
        d.clientId = clientId;
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

    /** A deadline worked out from an obligation's due rule. */
    public static ClientComplianceDeadline generated(TenantId tenantId, UUID clientId, UUID obligationId, int versionNumber, String responsibleRole,
                                                      String description, LocalDate dueDate, UUID createdBy) {
        ClientComplianceDeadline d = create(tenantId, clientId, null, "OBLIGATION", description, dueDate, createdBy);
        d.obligationId = obligationId;
        d.obligationVersionNumber = versionNumber;
        d.responsibleRole = responsibleRole;
        d.generated = true;
        return d;
    }

    /** A generated date that is no longer wanted. It stays on record with the reason. Only a pending one can be cancelled. */
    public void cancel(String reason, UUID by) {
        if (!"PENDING".equals(status)) throw new IllegalStateException("Only a pending deadline can be cancelled");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required");
        this.status = "CANCELLED";
        this.cancelledReason = reason.trim();
        this.updatedBy = by;
        this.updatedAt = Instant.now();
    }

    public void markDone(UUID completedBy) {
        if ("CANCELLED".equals(status)) throw new IllegalStateException("Deadline was cancelled");
        if ("DONE".equals(status)) throw new IllegalStateException("Deadline is already marked done");
        this.status = "DONE";
        this.completedAt = Instant.now();
        this.updatedBy = completedBy;
        this.updatedAt = Instant.now();
    }

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
