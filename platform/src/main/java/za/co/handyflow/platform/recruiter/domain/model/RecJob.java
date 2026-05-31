package za.co.handyflow.platform.recruiter.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "rec_jobs")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RecJob {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false) private String title;
    private String department;
    private String location;
    @Column(name = "job_type",        nullable = false) private String jobType        = "FULL_TIME";
    @Column(name = "experience_level",nullable = false) private String experienceLevel = "MID";
    @Column(nullable = false) private String description;
    private String requirements;
    private String benefits;
    @Column(name = "salary_min",      precision = 12, scale = 2) private BigDecimal salaryMin;
    @Column(name = "salary_max",      precision = 12, scale = 2) private BigDecimal salaryMax;
    @Column(name = "salary_currency", nullable = false)           private String     salaryCurrency = "ZAR";
    @Column(name = "show_salary",     nullable = false)           private boolean    showSalary     = false;
    @Column(nullable = false) private String  status = "DRAFT";
    private String    slug;
    @Column(name = "closes_at")        private LocalDate closesAt;
    @Column(name = "application_count",nullable = false) private int applicationCount = 0;
    @Column(name = "created_by")       private UUID    createdBy;
    @Column(name = "created_at")       private Instant createdAt;
    @Column(name = "updated_at")       private Instant updatedAt;
    @Column(name = "deleted_at")       private Instant deletedAt;

    public static RecJob create(TenantId tenantId, String title, String department,
                                 String location, String jobType, String experienceLevel,
                                 String description, String requirements, String benefits,
                                 BigDecimal salaryMin, BigDecimal salaryMax, boolean showSalary,
                                 LocalDate closesAt, UUID createdBy) {
        RecJob j           = new RecJob();
        j.tenantId         = tenantId;
        j.title            = title;
        j.department       = department;
        j.location         = location;
        j.jobType          = jobType != null ? jobType : "FULL_TIME";
        j.experienceLevel  = experienceLevel != null ? experienceLevel : "MID";
        j.description      = description;
        j.requirements     = requirements;
        j.benefits         = benefits;
        j.salaryMin        = salaryMin;
        j.salaryMax        = salaryMax;
        j.showSalary       = showSalary;
        j.closesAt         = closesAt;
        j.createdBy        = createdBy;
        j.status           = "DRAFT";
        j.slug             = generateSlug(title);
        j.createdAt        = Instant.now();
        j.updatedAt        = Instant.now();
        return j;
    }

    public void publish()  { this.status = "OPEN";   touch(); }
    public void pause()    { this.status = "PAUSED"; touch(); }
    public void close()    { this.status = "CLOSED"; touch(); }
    public void markFilled() { this.status = "FILLED"; touch(); }

    public void incrementApplicationCount() { this.applicationCount++; touch(); }

    public void update(String title, String department, String location,
                        String jobType, String experienceLevel, String description,
                        String requirements, String benefits,
                        BigDecimal salaryMin, BigDecimal salaryMax,
                        boolean showSalary, LocalDate closesAt) {
        if (title           != null) { this.title = title; this.slug = generateSlug(title); }
        if (department      != null) this.department      = department;
        if (location        != null) this.location        = location;
        if (jobType         != null) this.jobType         = jobType;
        if (experienceLevel != null) this.experienceLevel = experienceLevel;
        if (description     != null) this.description     = description;
        if (requirements    != null) this.requirements    = requirements;
        if (benefits        != null) this.benefits        = benefits;
        if (salaryMin       != null) this.salaryMin       = salaryMin;
        if (salaryMax       != null) this.salaryMax       = salaryMax;
        this.showSalary = showSalary;
        if (closesAt        != null) this.closesAt        = closesAt;
        touch();
    }

    private void touch() { this.updatedAt = Instant.now(); }

    private static String generateSlug(String title) {
        return title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .substring(0, Math.min(title.length(), 80));
    }
}
