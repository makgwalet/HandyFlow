package za.co.handyflow.platform.hr.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Payroll choices a tenant makes for itself. No row means every default: nobody approves their own work. */
@Entity
@Table(name = "hr_payroll_settings")
@Getter
@NoArgsConstructor
public class HrPayrollSettings {

    @Id @Column(name = "tenant_id") UUID tenantId;
    @Column(name = "allow_self_approval") boolean allowSelfApproval;
    @Column(name = "bank_nominated_branch") String bankNominatedBranch;
    @Column(name = "bank_nominated_account") String bankNominatedAccount;
    @Column(name = "bank_file_second_check") boolean bankFileSecondCheck;
    @Column(name = "tax_method") String taxMethod = "PERIODIC";
    @Column(name = "eti_enabled") boolean etiEnabled;
    @Column(name = "updated_by") UUID updatedBy;
    @Column(name = "updated_at") Instant updatedAt;

    public static HrPayrollSettings create(UUID tenantId) {
        HrPayrollSettings s = new HrPayrollSettings();
        s.tenantId = tenantId;
        return s;
    }

    public void setBankFile(boolean secondCheck, String branch, String account, UUID actor) {
        this.bankFileSecondCheck = secondCheck;
        this.bankNominatedBranch = branch == null || branch.isBlank() ? null : branch;
        this.bankNominatedAccount = account == null || account.isBlank() ? null : account;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }

    public void setEtiEnabled(boolean enabled, UUID actor) {
        this.etiEnabled = enabled;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }

    public boolean averaging() { return "AVERAGING".equals(taxMethod); }

    public void setTaxMethod(String method, UUID actor) {
        this.taxMethod = method;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }

    public void setAllowSelfApproval(boolean allow, UUID actor) {
        this.allowSelfApproval = allow;
        this.updatedBy = actor;
        this.updatedAt = Instant.now();
    }
}
