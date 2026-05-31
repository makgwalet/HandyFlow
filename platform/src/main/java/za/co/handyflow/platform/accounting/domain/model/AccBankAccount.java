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
}