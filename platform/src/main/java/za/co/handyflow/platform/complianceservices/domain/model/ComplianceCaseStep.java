package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One thing to do for a case. */
@Entity
@Table(name = "compliance_case_steps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceCaseStep {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String status = "TODO";

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "done_by")
    private UUID doneBy;

    @Column(name = "done_at")
    private Instant doneAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    public static ComplianceCaseStep of(TenantId tenantId, UUID caseId, String description, LocalDate due, int order, LocalDate today) {
        CaseRules.dueDate(due, today);
        ComplianceCaseStep s = new ComplianceCaseStep();
        s.tenantId = tenantId;
        s.caseId = caseId;
        s.description = CaseRules.step(description);
        s.dueDate = due;
        s.sortOrder = order;
        return s;
    }

    public void complete(UUID by) {
        if (!"TODO".equals(status)) throw new IllegalStateException("This step is already " + status.toLowerCase());
        if (by == null) throw new IllegalArgumentException("Who finished this step?");
        this.status = "DONE";
        this.doneBy = by;
        this.doneAt = Instant.now();
    }

    public void cancel(String reason) {
        if (!"TODO".equals(status)) throw new IllegalStateException("This step is already " + status.toLowerCase());
        this.cancelReason = CaseRules.reason(reason, "why the step is not needed");
        this.status = "CANCELLED";
    }

    public boolean isOpen() { return "TODO".equals(status); }
}
