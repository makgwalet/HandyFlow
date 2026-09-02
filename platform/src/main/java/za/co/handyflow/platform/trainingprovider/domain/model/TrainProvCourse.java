package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The provider's own accredited course catalogue — offered to every
 * client, not owned by one. Carries accreditation detail (unit
 * standard / NQF level) that Module 4a's own {@code TrainingCourse}
 * has no equivalent for, since 4a is not itself an accredited training
 * body.
 */
@Entity
@Table(name = "trainprov_courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvCourse {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "course_code", nullable = false)
    private String courseCode;

    private String title;
    private String description;

    /** e.g. a SAQA unit standard number. Null if this course isn't itself accredited (a short/skills course can still be offered). */
    @Column(name = "unit_standard_number")
    private String unitStandardNumber;

    @Column(name = "nqf_level")
    private Integer nqfLevel;

    @Column(name = "credits")
    private Integer credits;

    @Column(name = "duration_days")
    private BigDecimal durationDays;

    @Column(name = "price_per_delegate", nullable = false)
    private BigDecimal pricePerDelegate;

    @Column(name = "certification_offered")
    private boolean certificationOffered;

    @Column(name = "certificate_validity_months")
    private Integer certificateValidityMonths;

    /** ACTIVE | ARCHIVED */
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    public static TrainProvCourse create(TenantId tenantId, String courseCode, String title, String description,
                                          String unitStandardNumber, Integer nqfLevel, Integer credits,
                                          BigDecimal durationDays, BigDecimal pricePerDelegate,
                                          boolean certificationOffered, Integer certificateValidityMonths) {
        if (pricePerDelegate == null || pricePerDelegate.signum() < 0) {
            throw new IllegalArgumentException("pricePerDelegate must be zero or positive");
        }
        TrainProvCourse c = new TrainProvCourse();
        c.tenantId = tenantId.getValue();
        c.courseCode = courseCode;
        c.title = title;
        c.description = description;
        c.unitStandardNumber = unitStandardNumber;
        c.nqfLevel = nqfLevel;
        c.credits = credits;
        c.durationDays = durationDays;
        c.pricePerDelegate = pricePerDelegate;
        c.certificationOffered = certificationOffered;
        c.certificateValidityMonths = certificateValidityMonths;
        c.status = "ACTIVE";
        c.createdAt = Instant.now();
        c.updatedAt = Instant.now();
        return c;
    }

    public void update(String title, String description, String unitStandardNumber, Integer nqfLevel,
                        Integer credits, BigDecimal durationDays, BigDecimal pricePerDelegate,
                        boolean certificationOffered, Integer certificateValidityMonths) {
        if (pricePerDelegate == null || pricePerDelegate.signum() < 0) {
            throw new IllegalArgumentException("pricePerDelegate must be zero or positive");
        }
        this.title = title;
        this.description = description;
        this.unitStandardNumber = unitStandardNumber;
        this.nqfLevel = nqfLevel;
        this.credits = credits;
        this.durationDays = durationDays;
        this.pricePerDelegate = pricePerDelegate;
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
