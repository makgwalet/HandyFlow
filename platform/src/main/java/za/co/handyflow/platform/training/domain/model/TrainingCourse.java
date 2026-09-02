package za.co.handyflow.platform.training.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A catalogue entry: "First Aid Level 1", "Forklift Operation",
 * "POPIA Awareness". Not a scheduled event by itself — {@link
 * TrainingSession} is a scheduled running of one of these.
 * <p>
 * Deliberately a plain {@code @Entity}, matching {@code HrEmployee}'s
 * own convention (id assigned in a static factory, manual audit
 * columns, {@code @Version long} primitive, soft-delete) rather than
 * this codebase's AggregateRoot convention — see this module's
 * package-info.java for the full reasoning.
 */
@Entity
@Table(name = "training_courses")
@Getter
@NoArgsConstructor
public class TrainingCourse {

    @Id UUID id;
    @Column(name = "tenant_id") UUID tenantId;

    @Column(name = "course_code") String courseCode;
    String title;
    String description;

    /** Free-text category, e.g. "Compliance", "Technical", "Soft Skills", "Safety". Not an enum — the catalogue of categories is tenant-specific and open-ended. */
    String category;

    /** IN_PERSON | ONLINE | HYBRID */
    @Column(name = "delivery_mode") String deliveryMode;

    @Column(name = "duration_hours") BigDecimal durationHours;

    @Column(name = "default_trainer_name") String defaultTrainerName;

    /**
     * Informational cost per employee (venue/trainer/materials) — NOT
     * wired to AccountingFacade or any invoicing in this first pass.
     * This module tracks who was trained on what, not a training
     * expense ledger; posting training spend to the GL was flagged as
     * out of scope for this delivery (see status doc), not silently
     * built.
     */
    BigDecimal cost;

    @Column(name = "certification_offered") boolean certificationOffered;

    /** Null = certificate (if offered) never expires. Non-null = a TrainingCertificate issued from a completion of this course gets expiryDate = issueDate + this many months. */
    @Column(name = "certificate_validity_months") Integer certificateValidityMonths;

    /** ACTIVE | ARCHIVED */
    String status;

    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;
    @Version long version;

    public static TrainingCourse create(TenantId tenantId, String courseCode, String title, String description,
                                         String category, String deliveryMode, BigDecimal durationHours,
                                         String defaultTrainerName, BigDecimal cost, boolean certificationOffered,
                                         Integer certificateValidityMonths) {
        TrainingCourse c = new TrainingCourse();
        c.id = UUID.randomUUID();
        c.tenantId = tenantId.getValue();
        c.courseCode = courseCode;
        c.title = title;
        c.description = description;
        c.category = category;
        c.deliveryMode = deliveryMode != null ? deliveryMode : "IN_PERSON";
        c.durationHours = durationHours;
        c.defaultTrainerName = defaultTrainerName;
        c.cost = cost;
        c.certificationOffered = certificationOffered;
        c.certificateValidityMonths = certificateValidityMonths;
        c.status = "ACTIVE";
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void update(String title, String description, String category, String deliveryMode,
                        BigDecimal durationHours, String defaultTrainerName, BigDecimal cost,
                        boolean certificationOffered, Integer certificateValidityMonths) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.deliveryMode = deliveryMode != null ? deliveryMode : this.deliveryMode;
        this.durationHours = durationHours;
        this.defaultTrainerName = defaultTrainerName;
        this.cost = cost;
        this.certificationOffered = certificationOffered;
        this.certificateValidityMonths = certificateValidityMonths;
        this.updatedAt = Instant.now();
    }

    public void archive() {
        if ("ARCHIVED".equals(this.status)) throw new IllegalStateException("Course is already archived");
        this.status = "ARCHIVED";
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        this.status = "ACTIVE";
        this.updatedAt = Instant.now();
    }

    public boolean isActive() {
        return "ACTIVE".equals(this.status) && this.deletedAt == null;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
