package za.co.handyflow.platform.payrollbureau.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A held change to an employee's pay or bank details, with the values before and after (audit D4). */
@Entity
@Table(name = "pay_employee_change_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayEmployeeChangeRequest {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "pay_client_id", nullable = false)
    private UUID payClientId;

    @Column(name = "pay_employee_id", nullable = false)
    private UUID payEmployeeId;

    @Column(name = "status", nullable = false)
    private String status = "PENDING"; // PENDING | APPROVED | REJECTED | CANCELLED

    @Column(name = "prev_gross_salary") private BigDecimal prevGrossSalary;
    @Column(name = "prev_travel_allowance") private BigDecimal prevTravelAllowance;
    @Column(name = "prev_pension") private BigDecimal prevPension;
    @Column(name = "prev_medical_aid") private BigDecimal prevMedicalAid;
    @Column(name = "prev_bank_name") private String prevBankName;
    @Convert(converter = za.co.handyflow.platform.shared.EncryptedStringConverter.class)
    @Column(name = "prev_bank_account") private String prevBankAccount;
    @Column(name = "prev_bank_branch") private String prevBankBranch;

    @Column(name = "new_gross_salary") private BigDecimal newGrossSalary;
    @Column(name = "new_travel_allowance") private BigDecimal newTravelAllowance;
    @Column(name = "new_pension") private BigDecimal newPension;
    @Column(name = "new_medical_aid") private BigDecimal newMedicalAid;
    @Column(name = "new_bank_name") private String newBankName;
    @Convert(converter = za.co.handyflow.platform.shared.EncryptedStringConverter.class)
    @Column(name = "new_bank_account") private String newBankAccount;
    @Column(name = "new_bank_branch") private String newBankBranch;

    @Column(name = "requested_by") private UUID requestedBy;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt = Instant.now();
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decision_note") private String decisionNote;

    @Version
    private Long version;

    public static PayEmployeeChangeRequest create(PayEmployee emp, UUID requestedBy,
            BigDecimal gross, BigDecimal travel, BigDecimal pension, BigDecimal medical,
            String bankName, String bankAccount, String bankBranch) {
        PayEmployeeChangeRequest r = new PayEmployeeChangeRequest();
        r.tenantId = emp.getTenantId();
        r.payClientId = emp.getPayClientId();
        r.payEmployeeId = emp.getId();
        r.prevGrossSalary = emp.getGrossSalary();
        r.prevTravelAllowance = emp.getTravelAllowance();
        r.prevPension = emp.getPensionContribution();
        r.prevMedicalAid = emp.getMedicalAidContribution();
        r.prevBankName = emp.getBankName();
        r.prevBankAccount = emp.getBankAccountNumber();
        r.prevBankBranch = emp.getBankBranchCode();
        r.newGrossSalary = gross;
        r.newTravelAllowance = travel;
        r.newPension = pension;
        r.newMedicalAid = medical;
        r.newBankName = bankName;
        r.newBankAccount = bankAccount;
        r.newBankBranch = bankBranch;
        r.requestedBy = requestedBy;
        return r;
    }

    public void decide(String newStatus, UUID by, String note) {
        this.status = newStatus;
        this.decidedBy = by;
        this.decidedAt = Instant.now();
        this.decisionNote = note;
    }
}
