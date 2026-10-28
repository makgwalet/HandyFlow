package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "hr_employees")
@Getter
@NoArgsConstructor
public class HrEmployee {

    @Id UUID id;
    @Column(name = "tenant_id")       UUID tenantId;
    @Column(name = "employee_number") String employeeNumber;
    @Column(name = "first_name")      String firstName;
    @Column(name = "last_name")       String lastName;
    @Column(name = "id_number")       String idNumber;
    @Column(name = "tax_number")      String taxNumber;
    @Column(name = "date_of_birth")   LocalDate dateOfBirth;
    String gender;
    String race;
    boolean disability;
    String nationality = "South African";
    String email;
    String phone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    Map<String, String> address;

    @Column(name = "employment_type") String employmentType;
    @Column(name = "job_title")       String jobTitle;
    String department;
    @Column(name = "manager_id")      UUID managerId;
    @Column(name = "start_date")      LocalDate startDate;
    @Column(name = "end_date")        LocalDate endDate;
    String status = "ACTIVE";

    @Column(name = "salary_type")     String salaryType;
    @Column(name = "gross_salary")    BigDecimal grossSalary;
    @Column(name = "pay_frequency")   String payFrequency;

    @Column(name = "bank_name")           String bankName;
    @Column(name = "bank_account_number") String bankAccountNumber;
    @Column(name = "bank_branch_code")    String bankBranchCode;

    @Column(name = "medical_aid_contribution") BigDecimal medicalAidContribution = BigDecimal.ZERO;
    @Column(name = "pension_contribution")     BigDecimal pensionContribution = BigDecimal.ZERO;
    @Column(name = "travel_allowance")         BigDecimal travelAllowance = BigDecimal.ZERO;

    @Column(name = "emergency_contact_name")     String emergencyContactName;
    @Column(name = "emergency_contact_phone")    String emergencyContactPhone;
    @Column(name = "emergency_contact_relation") String emergencyContactRelation;

    @Column(name = "user_id")            UUID userId;
    @Column(name = "termination_reason") String terminationReason;
    @Column(name = "terminated_by")      UUID terminatedBy;
    @Column(name = "terminated_at")      Instant terminatedAt;
    @Column(name = "end_type")           String endType;
    @Column(name = "notice_given_on")    LocalDate noticeGivenOn;

    String notes;
    @Column(name = "eti_prior_months")   int etiPriorMonths;
    @Column(name = "eti_excluded_reason") String etiExcludedReason;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;
    @Version long version;

    public static HrEmployee create(TenantId tenantId, String employeeNumber,
                                    String firstName, String lastName,
                                    LocalDate startDate, String employmentType,
                                    BigDecimal grossSalary, String payFrequency) {
        HrEmployee e = new HrEmployee();
        e.id             = UUID.randomUUID();
        e.tenantId       = tenantId.getValue();
        e.employeeNumber = employeeNumber;
        e.firstName      = firstName;
        e.lastName       = lastName;
        e.startDate      = startDate;
        e.employmentType = employmentType != null ? employmentType : "PERMANENT";
        e.grossSalary    = grossSalary;
        e.payFrequency   = payFrequency != null ? payFrequency : "MONTHLY";
        e.salaryType     = "MONTHLY";
        e.status         = "ACTIVE";
        e.nationality    = "South African";
        e.disability     = false;
        e.medicalAidContribution = BigDecimal.ZERO;
        e.pensionContribution    = BigDecimal.ZERO;
        e.travelAllowance        = BigDecimal.ZERO;
        e.createdAt = Instant.now();
        e.updatedAt = Instant.now();
        return e;
    }

    public void terminate(LocalDate endDate) {
        this.status    = "TERMINATED";
        this.endDate   = endDate;
        this.updatedAt = Instant.now();
    }

    /** Terminate, keeping why and who recorded it. */
    public void terminate(LocalDate endDate, String reason, UUID actor) {
        terminate(endDate);
        this.terminationReason = reason == null ? null : reason.trim();
        this.terminatedBy = actor;
        this.terminatedAt = Instant.now();
    }

    /** Terminate, also keeping how employment ended and when notice was given. Both may be null. */
    public void terminate(LocalDate endDate, String reason, UUID actor, String endType, LocalDate noticeGivenOn) {
        terminate(endDate, reason, actor);
        this.endType = endType;
        this.noticeGivenOn = endType == null ? null : noticeGivenOn;
    }

    public void suspend() {
        this.status    = "SUSPENDED";
        this.updatedAt = Instant.now();
    }

    public void reinstate() {
        this.status    = "ACTIVE";
        this.updatedAt = Instant.now();
    }

    public void updateSalary(BigDecimal newGrossSalary) {
        this.grossSalary = newGrossSalary;
        this.updatedAt   = Instant.now();
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public void setIdNumber(String idNumber)                { this.idNumber = idNumber; }
    public void setTaxNumber(String taxNumber)              { this.taxNumber = taxNumber; }
    public void setDateOfBirth(java.time.LocalDate dob)     { this.dateOfBirth = dob; }
    public void setGender(String gender)                    { this.gender = gender; }
    public void setRace(String race)                        { this.race = race; }
    public void setEmail(String email)                      { this.email = email; }
    public void setPhone(String phone)                      { this.phone = phone; }
    public void setEmploymentType(String v)                 { this.employmentType = v; }
    public void setJobTitle(String jobTitle)                { this.jobTitle = jobTitle; }
    public void setDepartment(String department)            { this.department = department; }
    public void setBankName(String bankName)                { this.bankName = bankName; }
    public void setBankAccountNumber(String acc)            { this.bankAccountNumber = acc; }
    public void setBankBranchCode(String code)              { this.bankBranchCode = code; }
    public void setMedicalAidContribution(java.math.BigDecimal v) { this.medicalAidContribution = v; }
    public void setPensionContribution(java.math.BigDecimal v)    { this.pensionContribution = v; }
    public void setTravelAllowance(java.math.BigDecimal v)        { this.travelAllowance = v; }
    public void setGrossSalary(java.math.BigDecimal v)            { this.grossSalary = v; }
    public void setPayFrequency(String v)                         { this.payFrequency = v; }
    public void setEmergencyContactName(String v)                 { this.emergencyContactName = v; }
    public void setEmergencyContactPhone(String v)                { this.emergencyContactPhone = v; }
    public void setEti(int priorMonths, String excludedReason) {
        this.etiPriorMonths = priorMonths;
        this.etiExcludedReason = excludedReason == null || excludedReason.isBlank() ? null : excludedReason.trim();
        this.updatedAt = Instant.now();
    }
    public void setNotes(String notes)                            { this.notes = notes; }
    public void setEmergencyContactRelation(String v)             { this.emergencyContactRelation = v; }
    public void setManagerId(UUID managerId)                      { this.managerId = managerId; this.updatedAt = Instant.now(); }
    public void setUserId(UUID userId)                            { this.userId = userId; this.updatedAt = Instant.now(); }

    /** Applies an approved change of pay. Values come from the change request, already checked. */
    public void applyCompensation(BigDecimal gross, BigDecimal travel, BigDecimal medical, BigDecimal pension, String frequency) {
        this.grossSalary = gross;
        this.travelAllowance = travel == null ? BigDecimal.ZERO : travel;
        this.medicalAidContribution = medical == null ? BigDecimal.ZERO : medical;
        this.pensionContribution = pension == null ? BigDecimal.ZERO : pension;
        if (frequency != null && !frequency.isBlank()) this.payFrequency = frequency;
        this.updatedAt = Instant.now();
    }

    /** Applies an approved change of bank details. */
    public void applyBank(String bankName, String account, String branch) {
        this.bankName = bankName;
        this.bankAccountNumber = account;
        this.bankBranchCode = branch;
        this.updatedAt = Instant.now();
    }
}