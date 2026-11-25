package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.CatalogueRules;

import java.time.Instant;
import java.util.UUID;

/** What the provider offers. The definition lives in versions; where it is offered lives in offerings. Retired, never deleted. */
@Entity(name = "TrainProvProgramme")
@Table(name = "trainprov_programmes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvProgramme {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private String code;
    @Column(nullable = false)
    private String title;
    @Column(name = "programme_type", nullable = false)
    private String programmeType;
    @Column(name = "training_activity", nullable = false)
    private String trainingActivity;
    @Column(name = "delivery_mode", nullable = false)
    private String deliveryMode;
    @Column(name = "qualification_id")
    private UUID qualificationId;
    @Column(name = "course_id")
    private UUID courseId;
    private String description;
    @Column(nullable = false)
    private String status = "DRAFT";
    @Column(name = "retired_reason")
    private String retiredReason;
    @Column(name = "retired_by")
    private UUID retiredBy;
    @Column(name = "retired_at")
    private Instant retiredAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvProgramme create(TenantId t, String code, String title, String type, String activity,
                                            String mode, UUID qualificationId, UUID courseId, String description, UUID by) {
        TrainProvProgramme p = new TrainProvProgramme();
        p.tenantId = t.getValue();
        p.createdBy = by;
        p.apply(code, title, type, activity, mode, qualificationId, courseId, description);
        p.createdAt = Instant.now();
        return p;
    }

    /** The training activity and qualification cannot change once a programme is live; retire it and create another. */
    public void update(String code, String title, String type, String activity, String mode, UUID qualificationId,
                       UUID courseId, String description) {
        if ("RETIRED".equals(status)) {
            throw new IllegalStateException("A retired programme cannot be changed");
        }
        if ("ACTIVE".equals(status) && (!activity.equals(trainingActivity) || !java.util.Objects.equals(qualificationId, this.qualificationId))) {
            throw new IllegalStateException("A live programme cannot change its training activity or qualification. Retire it and create a new one");
        }
        apply(code, title, type, activity, mode, qualificationId, courseId, description);
    }

    private void apply(String code, String title, String type, String activity, String mode, UUID qualificationId,
                       UUID courseId, String description) {
        if (code == null || code.isBlank() || title == null || title.isBlank()) {
            throw new IllegalArgumentException("Code and title are required");
        }
        String problem = CatalogueRules.programmeProblem(activity, type, mode, qualificationId);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        this.code = code.trim();
        this.title = title.trim();
        this.programmeType = type;
        this.trainingActivity = activity;
        this.deliveryMode = mode;
        this.qualificationId = qualificationId;
        this.courseId = courseId;
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.updatedAt = Instant.now();
    }

    public void activate() {
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("Only a draft programme can be activated");
        }
        this.status = "ACTIVE";
        this.updatedAt = Instant.now();
    }

    public void retire(String reason, UUID by) {
        if ("RETIRED".equals(status)) {
            throw new IllegalStateException("This programme is already retired");
        }
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("Say why, in at least 10 characters");
        }
        this.status = "RETIRED";
        this.retiredReason = reason.trim();
        this.retiredBy = by;
        this.retiredAt = Instant.now();
        this.updatedAt = this.retiredAt;
    }
}
