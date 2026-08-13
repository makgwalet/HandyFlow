package za.co.handyflow.platform.recruitmentagency.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A client's open role the agency is trying to fill. Mirrors the shape
 * of recruiter's own Job entity (title/description/salary range) but
 * scoped to an agency client rather than the agency's own tenant —
 * the agency isn't hiring for itself here, it's filling a role on
 * someone else's behalf.
 */
@Entity
@Table(name = "reca_requisitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecAgencyRequisition {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId; // the AGENCY's tenant

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "requisition_number", nullable = false)
    private String requisitionNumber;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "salary_min", precision = 15, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 15, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "location")
    private String location;

    @Column(name = "employment_type")
    private String employmentType; // PERMANENT | CONTRACT | TEMP

    @Column(name = "status", nullable = false)
    private String status = "OPEN"; // OPEN | FILLED | CANCELLED | ON_HOLD

    @Column(name = "target_start_date")
    private java.time.LocalDate targetStartDate;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "filled_at")
    private Instant filledAt;

    public static RecAgencyRequisition create(UUID tenantId, UUID clientId, String requisitionNumber,
                                              String title, String description,
                                              BigDecimal salaryMin, BigDecimal salaryMax,
                                              String location, String employmentType,
                                              java.time.LocalDate targetStartDate) {
        RecAgencyRequisition r = new RecAgencyRequisition();
        r.tenantId = tenantId;
        r.clientId = clientId;
        r.requisitionNumber = requisitionNumber;
        r.title = title;
        r.description = description;
        r.salaryMin = salaryMin;
        r.salaryMax = salaryMax;
        r.location = location;
        r.employmentType = employmentType != null ? employmentType : "PERMANENT";
        r.targetStartDate = targetStartDate;
        r.status = "OPEN";
        r.createdAt = Instant.now();
        r.updatedAt = Instant.now();
        return r;
    }

    public void markFilled() {
        if (!"OPEN".equals(status) && !"ON_HOLD".equals(status)) {
            throw new IllegalStateException("Only an OPEN or ON_HOLD requisition can be marked filled");
        }
        this.status = "FILLED";
        this.filledAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        this.status = "CANCELLED";
        this.updatedAt = Instant.now();
    }

    public void putOnHold() {
        this.status = "ON_HOLD";
        this.updatedAt = Instant.now();
    }

    public void reopen() {
        this.status = "OPEN";
        this.updatedAt = Instant.now();
    }

    public void update(String title, String description, BigDecimal salaryMin, BigDecimal salaryMax,
                       String location, String employmentType, java.time.LocalDate targetStartDate, String notes) {
        this.title = title;
        this.description = description;
        this.salaryMin = salaryMin;
        this.salaryMax = salaryMax;
        this.location = location;
        this.employmentType = employmentType;
        this.targetStartDate = targetStartDate;
        this.notes = notes;
        this.updatedAt = Instant.now();
    }
}