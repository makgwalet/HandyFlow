package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One employee's salary payment in a bank file. The bank details are a copy taken when the payment was made up. */
@Entity
@Table(name = "hr_pay_run_payments")
@Getter
@NoArgsConstructor
public class HrPayRunPayment {

    @Id UUID id;
    @Column(name = "tenant_id")        UUID tenantId;
    @Column(name = "pay_run_id")       UUID payRunId;
    @Column(name = "payslip_id")       UUID payslipId;
    @Column(name = "employee_id")      UUID employeeId;
    BigDecimal amount;
    @Column(name = "beneficiary_name") String beneficiaryName;
    @Column(name = "bank_name")        String bankName;
    @Column(name = "branch_code")      String branchCode;
    @Column(name = "account_number")   String accountNumber;
    String reference;
    String status;
    @Column(name = "exported_at")      Instant exportedAt;
    @Column(name = "paid_at")          Instant paidAt;
    @Column(name = "paid_by")          UUID paidBy;
    @Column(name = "returned_at")      Instant returnedAt;
    @Column(name = "returned_by")      UUID returnedBy;
    @Column(name = "return_reason")    String returnReason;
    @Column(name = "created_by")       UUID createdBy;
    @Column(name = "created_at")       Instant createdAt;

    public static HrPayRunPayment pending(UUID tenantId, UUID payRunId, UUID payslipId, UUID employeeId, BigDecimal amount,
                                          String name, String bank, String branch, String account, String reference, UUID by) {
        HrPayRunPayment p = new HrPayRunPayment();
        p.id = UUID.randomUUID();
        p.tenantId = tenantId;
        p.payRunId = payRunId;
        p.payslipId = payslipId;
        p.employeeId = employeeId;
        p.amount = PayrollPaymentRules.money(amount);
        p.beneficiaryName = name.trim();
        p.bankName = bank == null || bank.isBlank() ? null : bank.trim();
        p.branchCode = PayrollPaymentRules.digits(branch);
        p.accountNumber = PayrollPaymentRules.digits(account);
        p.reference = reference;
        p.status = PayrollPaymentRules.PENDING;
        p.createdBy = by;
        p.createdAt = Instant.now();
        return p;
    }

    public void markExported() { if (exportedAt == null) exportedAt = Instant.now(); }

    public void markPaid(UUID by) {
        this.status = PayrollPaymentRules.PAID;
        this.paidAt = Instant.now();
        this.paidBy = by;
    }

    public void markReturned(UUID by, String reason) {
        this.status = PayrollPaymentRules.RETURNED;
        this.returnedAt = Instant.now();
        this.returnedBy = by;
        this.returnReason = reason.trim();
    }
}
