package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One thing to get done for a new employee. */
@Entity
@Table(name = "hr_onboarding_tasks")
@Getter
@NoArgsConstructor
public class HrOnboardingTask {

    @Id UUID id;
    @Column(name = "tenant_id")     UUID tenantId;
    @Column(name = "onboarding_id") UUID onboardingId;
    String title;
    String description;
    String category;
    boolean required = true;
    @Column(name = "due_date")      LocalDate dueDate;
    @Column(name = "check_key")     String checkKey;
    @Column(name = "assignee_id")   UUID assigneeId;
    String status = OnboardingRules.OPEN;
    String note;
    @Column(name = "resolved_by")   UUID resolvedBy;
    @Column(name = "resolved_at")   Instant resolvedAt;
    @Column(name = "sort_order")    int sortOrder;
    @Column(name = "created_at")    Instant createdAt;
    @Version long version;

    public static HrOnboardingTask of(UUID tenantId, UUID onboardingId, OnboardingRules.Spec s, LocalDate due, int sortOrder) {
        HrOnboardingTask t = new HrOnboardingTask();
        t.id = UUID.randomUUID();
        t.tenantId = tenantId;
        t.onboardingId = onboardingId;
        t.title = s.title();
        t.description = s.description();
        t.category = s.category();
        t.required = s.required();
        t.dueDate = due;
        t.checkKey = s.check() == null || s.check().isBlank() ? null : s.check();
        t.sortOrder = sortOrder;
        t.createdAt = Instant.now();
        return t;
    }

    public void complete(UUID actor, String note) { resolve(OnboardingRules.DONE, actor, note); }
    public void waive(UUID actor, String reason)  { resolve(OnboardingRules.WAIVED, actor, reason); }

    public void reopen() {
        this.status = OnboardingRules.OPEN;
        this.note = null;
        this.resolvedBy = null;
        this.resolvedAt = null;
    }

    public void assignTo(UUID userId) { this.assigneeId = userId; }
    public void dueOn(LocalDate d)    { this.dueDate = d; }

    private void resolve(String status, UUID actor, String note) {
        this.status = status;
        this.note = note == null || note.isBlank() ? null : note.trim();
        this.resolvedBy = actor;
        this.resolvedAt = Instant.now();
    }
}
