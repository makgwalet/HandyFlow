package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The onboarding of one employee. */
@Entity
@Table(name = "hr_onboardings")
@Getter
@NoArgsConstructor
public class HrOnboarding {

    @Id UUID id;
    @Column(name = "tenant_id")          UUID tenantId;
    @Column(name = "employee_id")        UUID employeeId;
    @Column(name = "start_date")         LocalDate startDate;
    @Column(name = "probation_end_date") LocalDate probationEndDate;
    String status = OnboardingRules.IN_PROGRESS;
    @Column(name = "started_by")         UUID startedBy;
    @Column(name = "completed_by")       UUID completedBy;
    @Column(name = "completed_at")       Instant completedAt;
    @Column(name = "cancel_reason")      String cancelReason;
    @Column(name = "created_at")         Instant createdAt;
    @Column(name = "updated_at")         Instant updatedAt;
    @Version long version;

    public static HrOnboarding start(UUID tenantId, UUID employeeId, LocalDate start, LocalDate probationEnd, UUID actor) {
        HrOnboarding o = new HrOnboarding();
        o.id = UUID.randomUUID();
        o.tenantId = tenantId;
        o.employeeId = employeeId;
        o.startDate = start;
        o.probationEndDate = probationEnd;
        o.startedBy = actor;
        o.createdAt = Instant.now();
        o.updatedAt = o.createdAt;
        return o;
    }

    public void finish(UUID actor) {
        this.status = OnboardingRules.COMPLETE;
        this.completedBy = actor;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        this.status = OnboardingRules.CANCELLED;
        this.cancelReason = reason;
        this.updatedAt = Instant.now();
    }

    public void setProbationEndDate(LocalDate d) { this.probationEndDate = d; this.updatedAt = Instant.now(); }
}
