package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An employee of one of the bureau's clients. Deliberately its own
 * entity, not a link into hr.HrEmployee — this employee doesn't work
 * for the bureau's tenant, they work for payClientId, a business that
 * may not be a HandyFlow tenant at all. Same "separate client-scoped
 * data" reasoning as accountant's own client-scoped records.
 * <p>
 * Field set is deliberately narrower than HrEmployee — only what's
 * needed to run payroll (PayrollBureauEngine's inputs), not the full HR
 * lifecycle (leave, disciplinary, job title history) a bureau isn't
 * managing for its clients' staff.
 */
@Entity
@Table(name = "pay_employees")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayEmployee {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId; // the BUREAU's tenant

    @Column(name = "pay_client_id", nullable = false)
    private UUID payClientId;

    @Column(name = "employee_number", nullable = false)
    private String employeeNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "id_number")
    private String idNumber;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gross_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossSalary;

    @Column(name = "travel_allowance", precision = 15, scale = 2)
    private BigDecimal travelAllowance = BigDecimal.ZERO;

    @Column(name = "pension_contribution", precision = 15, scale = 2)
    private BigDecimal pensionContribution = BigDecimal.ZERO;

    @Column(name = "medical_aid_contribution", precision = 15, scale = 2)
    private BigDecimal medicalAidContribution = BigDecimal.ZERO;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_account_number")
    private String bankAccountNumber;

    @Column(name = "bank_branch_code")
    private String bankBranchCode;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE"; // ACTIVE | TERMINATED

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static PayEmployee create(UUID tenantId, UUID payClientId, String employeeNumber,
                                     String firstName, String lastName, LocalDate startDate,
                                     BigDecimal grossSalary) {
        PayEmployee e = new PayEmployee();
        e.tenantId = tenantId;
        e.payClientId = payClientId;
        e.employeeNumber = employeeNumber;
        e.firstName = firstName;
        e.lastName = lastName;
        e.startDate = startDate;
        e.grossSalary = grossSalary;
        e.status = "ACTIVE";
        e.createdAt = Instant.now();
        e.updatedAt = Instant.now();
        return e;
    }

    public String getFullName() { return firstName + " " + lastName; }

    public void setIdNumber(String v) { this.idNumber = v; this.updatedAt = Instant.now(); }
    public void setDateOfBirth(LocalDate v) { this.dateOfBirth = v; this.updatedAt = Instant.now(); }
    public void setTravelAllowance(BigDecimal v) { this.travelAllowance = v; this.updatedAt = Instant.now(); }
    public void setPensionContribution(BigDecimal v) { this.pensionContribution = v; this.updatedAt = Instant.now(); }
    public void setMedicalAidContribution(BigDecimal v) { this.medicalAidContribution = v; this.updatedAt = Instant.now(); }
    public void setBankDetails(String bankName, String accNum, String branchCode) {
        this.bankName = bankName; this.bankAccountNumber = accNum; this.bankBranchCode = branchCode;
        this.updatedAt = Instant.now();
    }
    public void setGrossSalary(BigDecimal v) { this.grossSalary = v; this.updatedAt = Instant.now(); }

    public void terminate(LocalDate endDate) {
        this.status = "TERMINATED";
        this.endDate = endDate;
        this.updatedAt = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}