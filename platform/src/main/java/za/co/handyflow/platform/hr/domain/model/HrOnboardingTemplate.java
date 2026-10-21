package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** One line of a tenant's standard onboarding list. */
@Entity
@Table(name = "hr_onboarding_templates")
@Getter
@NoArgsConstructor
public class HrOnboardingTemplate {

    @Id UUID id;
    @Column(name = "tenant_id")       UUID tenantId;
    String title;
    String description;
    String category;
    boolean required = true;
    @Column(name = "due_offset_days") int dueOffsetDays;
    @Column(name = "due_basis")       String dueBasis = OnboardingRules.START;
    @Column(name = "check_key")       String checkKey;
    @Column(name = "sort_order")      int sortOrder;
    boolean active = true;
    @Column(name = "created_at")      Instant createdAt;
    @Column(name = "updated_at")      Instant updatedAt;

    public static HrOnboardingTemplate of(UUID tenantId, OnboardingRules.Spec s, int sortOrder) {
        HrOnboardingTemplate t = new HrOnboardingTemplate();
        t.id = UUID.randomUUID();
        t.tenantId = tenantId;
        t.createdAt = Instant.now();
        t.sortOrder = sortOrder;
        t.apply(s);
        return t;
    }

    public void apply(OnboardingRules.Spec s) {
        this.title = s.title().trim();
        this.description = s.description() == null || s.description().isBlank() ? null : s.description().trim();
        this.category = s.category();
        this.required = s.required();
        this.dueOffsetDays = s.dueOffsetDays();
        this.dueBasis = s.dueBasis();
        this.checkKey = s.check() == null || s.check().isBlank() ? null : s.check();
        this.updatedAt = Instant.now();
    }

    public void setActive(boolean active) { this.active = active; this.updatedAt = Instant.now(); }

    public OnboardingRules.Spec toSpec() { return new OnboardingRules.Spec(title, description, category, required, dueOffsetDays, dueBasis, checkKey); }
}
