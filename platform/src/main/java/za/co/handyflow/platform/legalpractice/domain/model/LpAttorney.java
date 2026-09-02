package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The firm's own staff who work on matters — attorneys, candidate
 * attorneys, and consultants. Deliberately free-standing (own name/
 * email/phone fields) with an OPTIONAL, unvalidated {@code employeeId}
 * reference, following {@code bookings.BookingStaff}'s shape rather than
 * {@code training}/{@code agriculture}'s deliberately-chosen real,
 * validated {@code HrFacade} reference.
 * <p>
 * WHY: a firm's admitted attorneys are routinely principals, directors,
 * or independent consultants — exactly the people who are NOT payroll
 * "employees" in the tenant's HR module. The entity that carries the
 * Legal Practice Act compliance obligation (an admitted attorney's own
 * admission number) has to work for those people first; a hard HR
 * dependency would fail for them. See the module's own scope-decision
 * note for the full reasoning — this was a design call made without
 * asking, not a guessed revenue/compliance rule.
 */
@Entity
@Table(name = "lp_attorneys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpAttorney {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false)
    private String name;

    private String email;

    private String phone;

    @Column(nullable = false, length = 30)
    private String role; // PRINCIPAL | PARTNER | ASSOCIATE | CANDIDATE_ATTORNEY | CONSULTANT

    @Column(name = "admission_number")
    private String admissionNumber; // Legal Practice Council enrollment number; null for not-yet-admitted candidate attorneys

    @Column(name = "hourly_rate", precision = 12, scale = 2)
    private BigDecimal hourlyRate; // default billing rate; a matter/time entry may override

    @Column(name = "employee_id")
    private UUID employeeId; // optional HR link — see class Javadoc

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static LpAttorney create(TenantId tenantId, String name, String email, String phone,
                                     String role, String admissionNumber, BigDecimal hourlyRate,
                                     UUID employeeId) {
        LpAttorney a = new LpAttorney();
        a.tenantId = tenantId;
        a.name = name;
        a.email = email;
        a.phone = phone;
        a.role = role;
        a.admissionNumber = admissionNumber;
        a.hourlyRate = hourlyRate;
        a.employeeId = employeeId;
        a.active = true;
        a.createdAt = Instant.now();
        a.updatedAt = Instant.now();
        return a;
    }

    public void update(String name, String email, String phone, String role,
                        String admissionNumber, BigDecimal hourlyRate, UUID employeeId) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.admissionNumber = admissionNumber;
        this.hourlyRate = hourlyRate;
        this.employeeId = employeeId;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void reactivate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}
