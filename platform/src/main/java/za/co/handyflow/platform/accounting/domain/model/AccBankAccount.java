package za.co.handyflow.platform.accounting.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "acc_bank_accounts")
@Getter
@NoArgsConstructor
public class AccBankAccount {

    @Id UUID id;
    @Column(name = "tenant_id")      UUID tenantId;
    @Column(name = "account_id")     UUID accountId;
    @Column(name = "bank_name")      String bankName;
    @Column(name = "account_name")   String accountName;
    @Column(name = "account_number") String accountNumber;
    @Column(name = "branch_code")    String branchCode;
    @Column(name = "account_type")   String accountType;
    String currency = "ZAR";
    @Column(name = "current_balance") BigDecimal currentBalance = BigDecimal.ZERO;
    @Column(name = "low_balance_threshold") BigDecimal lowBalanceThreshold;
    boolean active = true;
    @Column(name = "created_at") Instant createdAt;
    @Column(name = "updated_at") Instant updatedAt;
    @Column(name = "deleted_at") Instant deletedAt;
    @Version long version;

    public static AccBankAccount create(TenantId tenantId, String bankName,
                                        String accountName, String accountNumber,
                                        String branchCode, String accountType) {
        AccBankAccount b = new AccBankAccount();
        b.id            = UUID.randomUUID();
        b.tenantId      = tenantId.getValue();
        b.bankName      = bankName;
        b.accountName   = accountName;
        b.accountNumber = accountNumber;
        b.branchCode    = branchCode;
        b.accountType   = accountType != null ? accountType : "CURRENT";
        b.currency      = "ZAR";
        b.currentBalance = BigDecimal.ZERO;
        b.active        = true;
        b.createdAt     = Instant.now();
        b.updatedAt     = Instant.now();
        return b;
    }

    public void updateBalance(BigDecimal newBalance) {
        this.currentBalance = newBalance;
        this.updatedAt      = Instant.now();
    }

    // WHY THIS EXISTS: create() never took an accountId param at all — a
    // bank account created through the normal flow has no linked Chart of
    // Accounts entry, full stop, with no way to set one afterward either.
    // That's not a hypothetical gap — it's exactly what surfaced as a live
    // 409 when reconciliation's match-candidates search tried to use a
    // null accountId. This lets an already-existing bank account (like
    // one created before this fix existed) get linked retroactively,
    // without needing to delete and recreate it.
    public void linkAccount(UUID accountId) {
        this.accountId = accountId;
        this.updatedAt = Instant.now();
    }

    // threshold == null clears it — disables low-balance alerting for
    // this account entirely, not an error state. A savings account
    // sitting "low" on purpose shouldn't need a threshold at all.
    public void setLowBalanceThreshold(BigDecimal threshold) {
        this.lowBalanceThreshold = threshold;
        this.updatedAt = Instant.now();
    }
}