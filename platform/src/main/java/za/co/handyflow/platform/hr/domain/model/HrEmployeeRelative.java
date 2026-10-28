package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A next of kin or a dependant of an employee. Removed ones stay on file with who removed them and when. */
@Entity
@Table(name = "hr_employee_relatives")
@Getter
@NoArgsConstructor
public class HrEmployeeRelative {

    @Id UUID id;
    @Column(name = "tenant_id")           UUID tenantId;
    @Column(name = "employee_id")         UUID employeeId;
    String kind;
    @Column(name = "full_name")           String fullName;
    String relationship;
    String phone;
    String email;
    @Column(name = "date_of_birth")       LocalDate dateOfBirth;
    String address;
    boolean beneficiary;
    @Column(name = "beneficiary_percent") BigDecimal beneficiaryPercent;
    String notes;
    @Column(name = "created_by")          UUID createdBy;
    @Column(name = "created_at")          Instant createdAt;
    @Column(name = "updated_at")          Instant updatedAt;
    @Column(name = "removed_at")           Instant removedAt;
    @Column(name = "removed_by")           UUID removedBy;

    public static HrEmployeeRelative of(UUID tenantId, UUID employeeId, RelativeRules.Details d, UUID by) {
        HrEmployeeRelative r = new HrEmployeeRelative();
        r.id = UUID.randomUUID();
        r.tenantId = tenantId;
        r.employeeId = employeeId;
        r.createdBy = by;
        r.createdAt = Instant.now();
        r.change(d);
        return r;
    }

    /** Replaces everything the person can edit, with values already cleaned by {@link RelativeRules#clean}. */
    public void change(RelativeRules.Details d) {
        this.kind = d.kind();
        this.fullName = d.fullName();
        this.relationship = d.relationship();
        this.phone = d.phone();
        this.email = d.email();
        this.dateOfBirth = d.dateOfBirth();
        this.address = d.address();
        this.beneficiary = d.beneficiary();
        this.beneficiaryPercent = d.beneficiary() ? d.beneficiaryPercent() : null;
        this.notes = d.notes();
        this.updatedAt = Instant.now();
    }

    public void remove(UUID by) {
        this.removedAt = Instant.now();
        this.removedBy = by;
    }
}
