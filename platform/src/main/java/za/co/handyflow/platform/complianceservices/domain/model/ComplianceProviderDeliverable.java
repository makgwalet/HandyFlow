package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Something a provider is to hand over. Received by one person and accepted or rejected by another. A rejected one can be received again. */
@Entity(name = "ComplianceProviderDeliverable")
@Table(name = "compliance_provider_deliverables")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceProviderDeliverable {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;
    @Column(nullable = false)
    private String title;
    @Column(name = "due_date")
    private LocalDate dueDate;
    @Column(nullable = false)
    private String status = ProviderWorkRules.EXPECTED;
    @Column(name = "received_by")
    private UUID receivedBy;
    @Column(name = "received_at")
    private Instant receivedAt;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Version
    private Long version;

    public static ComplianceProviderDeliverable expect(TenantId tenantId, UUID assignmentId, String title, LocalDate dueDate) {
        ComplianceProviderDeliverable d = new ComplianceProviderDeliverable();
        d.tenantId = tenantId;
        d.assignmentId = assignmentId;
        d.title = ProviderRules.required(title, "What is to be handed over", 200);
        d.dueDate = dueDate;
        d.createdAt = Instant.now();
        return d;
    }

    public void received(UUID by) {
        if (!ProviderWorkRules.EXPECTED.equals(status) && !ProviderWorkRules.REJECTED.equals(status)) throw new IllegalStateException("This deliverable is " + status.toLowerCase());
        if (by == null) throw new IllegalArgumentException("Someone must record this");
        this.status = ProviderWorkRules.RECEIVED;
        this.receivedBy = by;
        this.receivedAt = Instant.now();
        this.reviewedBy = null;
        this.reviewedAt = null;
        this.reviewNote = null;
    }

    public void review(boolean accepted, UUID by, String note) {
        if (!ProviderWorkRules.RECEIVED.equals(status)) throw new IllegalStateException("Only a received deliverable can be reviewed");
        ProviderWorkRules.checkReviewer(receivedBy, by);
        this.reviewNote = ProviderRules.note(note);
        this.status = accepted ? ProviderWorkRules.ACCEPTED : ProviderWorkRules.REJECTED;
        this.reviewedBy = by;
        this.reviewedAt = Instant.now();
    }

    public void waive(UUID by, String note) {
        if (ProviderWorkRules.ACCEPTED.equals(status) || ProviderWorkRules.WAIVED.equals(status)) throw new IllegalStateException("This deliverable is " + status.toLowerCase());
        if (by == null) throw new IllegalArgumentException("Someone must record this");
        this.reviewNote = ProviderRules.note(note);
        this.status = ProviderWorkRules.WAIVED;
        this.reviewedBy = by;
        this.reviewedAt = Instant.now();
    }
}
