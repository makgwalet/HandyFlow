package za.co.handyflow.platform.training.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** "People with this job title (and/or in this department) must hold a valid certificate for this course." Both left empty: everyone. */
@Entity
@Table(name = "training_requirements")
@Getter
@NoArgsConstructor
public class TrainingRequirement {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "course_id") UUID courseId;
    @Column(name = "job_title") String jobTitle;
    String department;
    @Column(name = "created_by") UUID createdBy;
    @Column(name = "created_at") Instant createdAt;

    public static TrainingRequirement create(TenantId tenantId, UUID courseId, String jobTitle, String department, UUID createdBy) {
        TrainingRequirement r = new TrainingRequirement();
        r.id = UUID.randomUUID();
        r.tenantId = tenantId.getValue();
        r.courseId = courseId;
        r.jobTitle = TrainingRequirementRules.clean(jobTitle);
        r.department = TrainingRequirementRules.clean(department);
        r.createdBy = createdBy;
        r.createdAt = Instant.now();
        return r;
    }
}
